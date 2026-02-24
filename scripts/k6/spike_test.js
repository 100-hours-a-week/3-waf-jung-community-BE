import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ============================================
// 스파이크 테스트
// 갑작스러운 트래픽 급증 시 시스템 반응 측정
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('errors');
const responseDuration = new Trend('response_duration');

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10 },   // 정상 상태
        { duration: '20s', target: 10 },   // 유지
        { duration: '10s', target: 200 },  // 급격한 스파이크!
        { duration: '1m', target: 200 },   // 피크 유지
        { duration: '10s', target: 10 },   // 급격한 하락
        { duration: '20s', target: 10 },   // 복구 확인
        { duration: '10s', target: 0 },    // 종료
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<2000'],  // 스파이크 시 2초까지 허용
    errors: ['rate<0.2'],                // 스파이크 시 20%까지 허용
  },
};

function getRandomPostId(max = 1500) {
  return Math.floor(Math.random() * max) + 1;
}

export default function () {
  // 읽기/쓰기 혼합 시나리오
  const isWriter = Math.random() < 0.3;

  // 게시글 목록
  group('게시글 목록', () => {
    const res = http.get(`${BASE_URL}/posts?limit=10`);
    responseDuration.add(res.timings.duration);
    check(res, { 'posts ok': (r) => r.status === 200 });
    errorRate.add(res.status !== 200);
  });

  sleep(0.5);

  // 게시글 상세
  group('게시글 상세', () => {
    const res = http.get(`${BASE_URL}/posts/${getRandomPostId()}`);
    responseDuration.add(res.timings.duration);
    check(res, { 'detail ok': (r) => r.status === 200 || r.status === 404 });
    errorRate.add(res.status !== 200 && res.status !== 404);
  });

  sleep(0.5);

  if (isWriter) {
    const userId = Math.floor(Math.random() * 600) + 1;
    const loginRes = http.post(
      `${BASE_URL}/auth/login`,
      JSON.stringify({ email: `user${userId}@test.com`, password: 'Test1234!' }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    if (loginRes.status === 200) {
      try {
        const token = JSON.parse(loginRes.body).data.accessToken;
        const headers = {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`,
        };

        // 좋아요
        http.post(`${BASE_URL}/posts/${getRandomPostId()}/like`, null, { headers });

        sleep(0.3);

        // 댓글
        http.post(
          `${BASE_URL}/posts/${getRandomPostId()}/comments`,
          JSON.stringify({ comment: `스파이크 테스트 ${Date.now()}` }),
          { headers }
        );
      } catch (e) {}
    }
  }

  sleep(1);
}
