import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================
// CCU(동시접속자) 단계별 부하 테스트
// 목표: 10 → 50 → 100 → 200 CCU 성능 측정
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 메트릭
const errorRate = new Rate('errors');
const requestCount = new Counter('requests');
const loginDuration = new Trend('login_duration');
const apiDuration = new Trend('api_duration');

// ============================================
// CCU 단계별 테스트 옵션
// ============================================
export const options = {
  scenarios: {
    ccu_stages: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        // Stage 1: CCU 10
        { duration: '30s', target: 10 },
        { duration: '1m', target: 10 },

        // Stage 2: CCU 50
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },

        // Stage 3: CCU 100
        { duration: '30s', target: 100 },
        { duration: '1m', target: 100 },

        // Stage 4: CCU 200
        { duration: '30s', target: 200 },
        { duration: '1m', target: 200 },

        // 램프다운
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],  // 95%가 1초 이내
    errors: ['rate<0.1'],                // 에러율 10% 미만
  },
};

// ============================================
// 테스트 데이터
// ============================================
function getRandomEmail(max = 600) {
  const id = Math.floor(Math.random() * max) + 1;
  return `user${id}@test.com`;
}

function getRandomPostId(max = 1500) {
  return Math.floor(Math.random() * max) + 1;
}

// ============================================
// 메인 시나리오: 실제 사용자 행동 시뮬레이션
// ============================================
export default function () {
  const userId = Math.floor(Math.random() * 600) + 1;
  const email = `user${userId}@test.com`;

  // 70% 확률로 비로그인 사용자 (읽기만)
  // 30% 확률로 로그인 사용자 (읽기 + 쓰기)
  const isLoggedIn = Math.random() < 0.3;

  let accessToken = null;

  if (isLoggedIn) {
    // 로그인
    group('로그인', () => {
      const loginRes = http.post(
        `${BASE_URL}/auth/login`,
        JSON.stringify({ email, password: 'Test1234!' }),
        { headers: { 'Content-Type': 'application/json' } }
      );

      loginDuration.add(loginRes.timings.duration);
      requestCount.add(1);

      if (loginRes.status === 200) {
        try {
          const body = JSON.parse(loginRes.body);
          accessToken = body.data.accessToken;
        } catch (e) {}
      }

      errorRate.add(loginRes.status !== 200);
    });

    sleep(0.5);
  }

  // 게시글 목록 조회 (모든 사용자)
  group('게시글 목록', () => {
    const res = http.get(`${BASE_URL}/posts?limit=10`);
    apiDuration.add(res.timings.duration);
    requestCount.add(1);

    check(res, { 'posts list ok': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(1);

  // 게시글 상세 조회 (모든 사용자)
  group('게시글 상세', () => {
    const postId = getRandomPostId();
    const res = http.get(`${BASE_URL}/posts/${postId}`);
    apiDuration.add(res.timings.duration);
    requestCount.add(1);

    check(res, { 'post detail ok': (r) => r.status === 200 || r.status === 404 });
    errorRate.add(res.status !== 200 && res.status !== 404);
  });

  sleep(1);

  // 댓글 조회 (모든 사용자)
  group('댓글 목록', () => {
    const postId = getRandomPostId();
    const res = http.get(`${BASE_URL}/posts/${postId}/comments?limit=10`);
    apiDuration.add(res.timings.duration);
    requestCount.add(1);

    check(res, { 'comments ok': (r) => r.status === 200 || r.status === 404 });
    errorRate.add(res.status !== 200 && res.status !== 404);
  });

  sleep(1);

  // 로그인 사용자만 쓰기 작업
  if (isLoggedIn && accessToken) {
    const headers = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${accessToken}`,
    };

    // 좋아요
    group('좋아요', () => {
      const postId = getRandomPostId();
      const res = http.post(`${BASE_URL}/posts/${postId}/like`, null, { headers });
      apiDuration.add(res.timings.duration);
      requestCount.add(1);

      // 200, 404, 409 모두 정상
      check(res, { 'like ok': (r) => [200, 404, 409].includes(r.status) });
      errorRate.add(![200, 404, 409].includes(res.status));
    });

    sleep(1);

    // 10% 확률로 댓글 작성
    if (Math.random() < 0.1) {
      group('댓글 작성', () => {
        const postId = getRandomPostId();
        const res = http.post(
          `${BASE_URL}/posts/${postId}/comments`,
          JSON.stringify({ comment: `테스트 댓글 ${Date.now()}` }),
          { headers }
        );
        apiDuration.add(res.timings.duration);
        requestCount.add(1);

        check(res, { 'comment created': (r) => r.status === 201 || r.status === 404 });
        errorRate.add(res.status !== 201 && res.status !== 404);
      });
    }
  }

  sleep(2);
}
