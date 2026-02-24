import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================
// 로그인 집중 스트레스 테스트
// JWT 생성 + DB 토큰 저장 부하 측정
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('errors');
const loginDuration = new Trend('login_duration');
const loginCount = new Counter('login_count');

export const options = {
  scenarios: {
    login_stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 30 },
        { duration: '1m', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    login_duration: ['p(95)<500'],
    errors: ['rate<0.1'],
  },
};

export default function () {
  const userId = Math.floor(Math.random() * 600) + 1;
  const email = `user${userId}@test.com`;

  // 로그인
  const loginRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password: 'Test1234!' }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  loginDuration.add(loginRes.timings.duration);
  loginCount.add(1);

  const loginOk = check(loginRes, {
    'login 200': (r) => r.status === 200,
    'has token': (r) => {
      try {
        return JSON.parse(r.body).data.accessToken !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  errorRate.add(!loginOk);

  if (loginRes.status === 200) {
    try {
      const token = JSON.parse(loginRes.body).data.accessToken;

      sleep(0.5);

      // 로그아웃 (토큰 삭제)
      const logoutRes = http.post(
        `${BASE_URL}/auth/logout`,
        null,
        {
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
          },
        }
      );

      check(logoutRes, { 'logout ok': (r) => r.status === 200 });

    } catch (e) {}
  }

  sleep(1);
}
