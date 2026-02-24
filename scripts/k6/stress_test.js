import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================
// 스트레스 테스트
// 시스템 한계점(Breaking Point) 탐색
// CCU를 계속 올려서 어디서 무너지는지 확인
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('errors');
const responseDuration = new Trend('response_duration');
const requestCount = new Counter('requests');

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        // 점진적으로 증가하며 한계점 탐색
        { duration: '30s', target: 50 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '1m', target: 100 },
        { duration: '30s', target: 150 },
        { duration: '1m', target: 150 },
        { duration: '30s', target: 200 },
        { duration: '1m', target: 200 },
        { duration: '30s', target: 250 },
        { duration: '1m', target: 250 },
        { duration: '30s', target: 300 },
        { duration: '1m', target: 300 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<3000'],  // 한계 탐색이므로 3초까지 허용
    errors: ['rate<0.3'],                // 30%까지 허용 (한계점 탐색용)
  },
};

function getRandomPostId(max = 1500) {
  return Math.floor(Math.random() * max) + 1;
}

export default function () {
  const isWriter = Math.random() < 0.2;  // 20%만 쓰기

  // 읽기 작업
  group('읽기', () => {
    const res = http.get(`${BASE_URL}/posts?limit=10`);
    responseDuration.add(res.timings.duration);
    requestCount.add(1);
    check(res, { 'read ok': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(0.3);

  group('상세', () => {
    const res = http.get(`${BASE_URL}/posts/${getRandomPostId()}`);
    responseDuration.add(res.timings.duration);
    requestCount.add(1);
    check(res, { 'detail ok': (r) => r.status === 200 || r.status === 404 });
    errorRate.add(res.status !== 200 && res.status !== 404);
  });

  sleep(0.3);

  if (isWriter) {
    const userId = Math.floor(Math.random() * 600) + 1;
    const loginRes = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ email: `user${userId}@test.com`, password: 'Test1234!' }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    requestCount.add(1);

    if (loginRes.status === 200) {
      try {
        const token = JSON.parse(loginRes.body).data.accessToken;
        const headers = {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        };

        // 댓글 작성 (DB 부하)
        const commentRes = http.post(
          `${BASE_URL}/posts/${getRandomPostId()}/comments`,
          JSON.stringify({ comment: `스트레스 ${Date.now()}` }),
          { headers }
        );
        responseDuration.add(commentRes.timings.duration);
        requestCount.add(1);

      } catch (e) {}
    }
  }

  sleep(0.5);
}
