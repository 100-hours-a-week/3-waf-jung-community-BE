import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================
// DB 커넥션 풀 스트레스 테스트
// 동시 다발적 쓰기 요청으로 커넥션 풀 한계 테스트
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('errors');
const dbOpDuration = new Trend('db_op_duration');
const dbOps = new Counter('db_operations');

export const options = {
  scenarios: {
    db_stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 30 },
        { duration: '30s', target: 30 },
        { duration: '20s', target: 60 },
        { duration: '30s', target: 60 },
        { duration: '20s', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    db_op_duration: ['p(95)<2000'],
    errors: ['rate<0.2'],
  },
};

function getRandomPostId(max = 1500) {
  return Math.floor(Math.random() * max) + 1;
}

export default function () {
  const userId = Math.floor(Math.random() * 600) + 1;
  const email = `user${userId}@test.com`;

  // 로그인
  const loginRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password: 'Test1234!' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  dbOpDuration.add(loginRes.timings.duration);
  dbOps.add(1);

  if (loginRes.status !== 200) {
    errorRate.add(true);
    sleep(1);
    return;
  }

  let token;
  try {
    token = JSON.parse(loginRes.body).data.accessToken;
  } catch (e) {
    errorRate.add(true);
    sleep(1);
    return;
  }

  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${token}`,
  };

  // 동시 다발적 DB 쓰기 작업
  // 1. 게시글 조회 (조회수 UPDATE)
  group('조회수 증가', () => {
    const res = http.get(`${BASE_URL}/posts/${getRandomPostId()}`);
    dbOpDuration.add(res.timings.duration);
    dbOps.add(1);
    errorRate.add(res.status !== 200 && res.status !== 404);
  });

  // 2. 좋아요 (INSERT + UPDATE)
  group('좋아요', () => {
    const res = http.post(`${BASE_URL}/posts/${getRandomPostId()}/like`, null, { headers });
    dbOpDuration.add(res.timings.duration);
    dbOps.add(1);
    errorRate.add(![200, 404, 409].includes(res.status));
  });

  // 3. 댓글 작성 (INSERT + UPDATE)
  group('댓글', () => {
    const res = http.post(
      `${BASE_URL}/posts/${getRandomPostId()}/comments`,
      JSON.stringify({ comment: `DB 스트레스 ${Date.now()}` }),
      { headers }
    );
    dbOpDuration.add(res.timings.duration);
    dbOps.add(1);
    errorRate.add(res.status !== 201 && res.status !== 404);
  });

  // 4. 게시글 작성 (INSERT posts + INSERT post_stats)
  group('게시글 작성', () => {
    const res = http.post(
      `${BASE_URL}/posts`,
      JSON.stringify({
        title: `DB테스트 ${Date.now()}`,
        content: `커넥션 풀 스트레스 테스트 ${__VU}-${__ITER}`,
      }),
      { headers }
    );
    dbOpDuration.add(res.timings.duration);
    dbOps.add(1);
    errorRate.add(res.status !== 201);
  });

  // 5. 좋아요 취소 (DELETE + UPDATE)
  group('좋아요 취소', () => {
    const res = http.del(`${BASE_URL}/posts/${getRandomPostId()}/like`, null, { headers });
    dbOpDuration.add(res.timings.duration);
    dbOps.add(1);
    errorRate.add(![200, 404].includes(res.status));
  });

  sleep(2);  // Rate Limit 회피를 위해 대기 시간 증가
}
