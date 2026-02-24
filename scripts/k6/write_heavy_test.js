import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================
// 쓰기 집중 부하 테스트
// DB INSERT/UPDATE 부하 극대화
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('errors');
const writeOps = new Counter('write_operations');
const writeDuration = new Trend('write_duration');

export const options = {
  scenarios: {
    write_stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '2m', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    errors: ['rate<0.1'],
  },
};

function getRandomPostId(max = 1500) {
  return Math.floor(Math.random() * max) + 1;
}

function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };
}

export default function () {
  const userId = Math.floor(Math.random() * 600) + 1;
  const email = `user${userId}@test.com`;

  // 1. 로그인 (INSERT user_tokens)
  let accessToken = null;
  group('로그인', () => {
    const res = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ email, password: 'Test1234!' }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    writeOps.add(1);
    writeDuration.add(res.timings.duration);

    if (res.status === 200) {
      try {
        accessToken = JSON.parse(res.body).data.accessToken;
      } catch (e) {}
    }
    errorRate.add(res.status !== 200);
  });

  if (!accessToken) {
    sleep(2);
    return;
  }

  sleep(0.5);

  // 2. 게시글 작성 (INSERT posts + INSERT post_stats)
  let newPostId = null;
  group('게시글 작성', () => {
    const res = http.post(
      `${BASE_URL}/posts`,
      JSON.stringify({
        title: `부하테스트 ${Date.now()}`,
        content: `k6 쓰기 테스트 내용입니다. VU: ${__VU}, Iter: ${__ITER}`,
      }),
      { headers: authHeaders(accessToken) }
    );

    writeOps.add(1);
    writeDuration.add(res.timings.duration);

    if (res.status === 201) {
      try {
        newPostId = JSON.parse(res.body).data.postId;
      } catch (e) {}
    }
    check(res, { 'post created': (r) => r.status === 201 });
    errorRate.add(res.status !== 201);
  });

  sleep(0.5);

  // 3. 좋아요 3회 (INSERT post_likes + UPDATE post_stats)
  group('좋아요 연속', () => {
    for (let i = 0; i < 3; i++) {
      const postId = getRandomPostId();
      const res = http.post(
        `${BASE_URL}/posts/${postId}/like`,
        null,
        { headers: authHeaders(accessToken) }
      );

      writeOps.add(1);
      writeDuration.add(res.timings.duration);

      check(res, { 'like ok': (r) => [200, 404, 409].includes(r.status) });
      errorRate.add(![200, 404, 409].includes(res.status));

      sleep(0.2);
    }
  });

  sleep(0.5);

  // 4. 댓글 작성 5회 (INSERT comments + UPDATE post_stats)
  group('댓글 연속 작성', () => {
    for (let i = 0; i < 5; i++) {
      const postId = getRandomPostId();
      const res = http.post(
        `${BASE_URL}/posts/${postId}/comments`,
        JSON.stringify({ comment: `테스트 댓글 ${Date.now()}-${i}` }),
        { headers: authHeaders(accessToken) }
      );

      writeOps.add(1);
      writeDuration.add(res.timings.duration);

      check(res, { 'comment created': (r) => r.status === 201 || r.status === 404 });
      errorRate.add(res.status !== 201 && res.status !== 404);

      sleep(0.2);
    }
  });

  sleep(0.5);

  // 5. 좋아요 취소 (DELETE post_likes + UPDATE post_stats)
  group('좋아요 취소', () => {
    const postId = getRandomPostId();
    const res = http.del(
      `${BASE_URL}/posts/${postId}/like`,
      null,
      { headers: authHeaders(accessToken) }
    );

    writeOps.add(1);
    writeDuration.add(res.timings.duration);

    check(res, { 'unlike ok': (r) => [200, 404].includes(r.status) });
    errorRate.add(![200, 404].includes(res.status));
  });

  sleep(0.5);

  // 6. 로그아웃 (DELETE user_tokens)
  group('로그아웃', () => {
    const res = http.post(
      `${BASE_URL}/auth/logout`,
      null,
      { headers: authHeaders(accessToken) }
    );

    writeOps.add(1);
    writeDuration.add(res.timings.duration);

    check(res, { 'logout ok': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(1);
}
