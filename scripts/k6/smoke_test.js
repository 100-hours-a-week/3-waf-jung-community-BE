import http from 'k6/http';
import { check, sleep } from 'k6';

// ============================================
// Smoke Test - 기본 동작 확인용 (배포 후 빠른 검증)
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  // 1. Health Check
  let res = http.get(`${BASE_URL}/health`);
  check(res, {
    'health check ok': (r) => r.status === 200,
  });

  sleep(1);

  // 2. 게시글 목록 조회
  res = http.get(`${BASE_URL}/posts?limit=5`);
  check(res, {
    'posts list ok': (r) => r.status === 200,
  });

  sleep(1);

  // 3. 게시글 상세 조회
  res = http.get(`${BASE_URL}/posts/1`);
  check(res, {
    'post detail ok': (r) => r.status === 200 || r.status === 404,
  });

  sleep(1);

  // 4. 로그인
  res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: 'user1@test.com', password: 'Test1234!' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(res, {
    'login ok': (r) => r.status === 200,
  });

  sleep(1);

  // 5. 통계 조회
  res = http.get(`${BASE_URL}/stats`);
  check(res, {
    'stats ok': (r) => r.status === 200,
  });

  sleep(2);
}
