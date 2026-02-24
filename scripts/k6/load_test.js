import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ============================================
// 설정
// ============================================
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 커스텀 메트릭
const errorRate = new Rate('errors');
const loginDuration = new Trend('login_duration');
const postListDuration = new Trend('post_list_duration');
const postDetailDuration = new Trend('post_detail_duration');
const presignedUrlDuration = new Trend('presigned_url_duration');
const s3UploadDuration = new Trend('s3_upload_duration');

// 테스트 이미지 파일 로드
const testImages = [
  { data: open('./testdata/test_image1.jpg', 'b'), name: 'test_image1.jpg', type: 'image/jpeg' },
  { data: open('./testdata/test_image2.jpg', 'b'), name: 'test_image2.jpg', type: 'image/jpeg' },
  { data: open('./testdata/test_image3.jpeg', 'b'), name: 'test_image3.jpeg', type: 'image/jpeg' },
  { data: open('./testdata/test_image4.jpeg', 'b'), name: 'test_image4.jpeg', type: 'image/jpeg' },
  { data: open('./testdata/test_image5.jpeg', 'b'), name: 'test_image5.jpeg', type: 'image/jpeg' },
  { data: open('./testdata/test_image6.jpeg', 'b'), name: 'test_image6.jpeg', type: 'image/jpeg' },
];

function getRandomImage() {
  return testImages[Math.floor(Math.random() * testImages.length)];
}

// ============================================
// 테스트 시나리오 옵션
// ============================================
export const options = {
  scenarios: {
    // 시나리오 1: 읽기 위주 (70% 비율)
    read_heavy: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },   // 웜업
        { duration: '1m', target: 50 },    // 램프업
        { duration: '2m', target: 50 },    // 유지
        { duration: '30s', target: 0 },    // 램프다운
      ],
      exec: 'readHeavyScenario',
    },
    // 시나리오 2: 쓰기 위주 (30% 비율)
    write_heavy: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },
        { duration: '1m', target: 20 },
        { duration: '2m', target: 20 },
        { duration: '30s', target: 0 },
      ],
      exec: 'writeHeavyScenario',
      startTime: '10s',  // 읽기 시나리오 시작 후 10초 뒤 시작
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],  // 95%가 500ms 이내
    errors: ['rate<0.05'],  // 에러율 5% 미만
    login_duration: ['p(95)<300'],
    post_list_duration: ['p(95)<200'],
    post_detail_duration: ['p(95)<150'],
  },
};

// ============================================
// 테스트 데이터
// ============================================
const TEST_USERS = [];
for (let i = 1; i <= 100; i++) {
  TEST_USERS.push({
    email: `user${i}@test.com`,
    password: 'Test1234!',  // dummy_data.sql의 비밀번호 해시와 일치
  });
}

// ============================================
// 헬퍼 함수
// ============================================
function getRandomUser() {
  return TEST_USERS[Math.floor(Math.random() * TEST_USERS.length)];
}

function getRandomPostId(max = 1500) {
  return Math.floor(Math.random() * max) + 1;
}

function login(email, password) {
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email, password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'login' },
    }
  );

  loginDuration.add(res.timings.duration);

  const success = check(res, {
    'login status 200': (r) => r.status === 200,
    'login has accessToken': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.accessToken;
      } catch (e) {
        return false;
      }
    },
  });

  errorRate.add(!success);

  if (success) {
    const body = JSON.parse(res.body);
    return {
      accessToken: body.data.accessToken,
      userId: body.data.userId,
    };
  }
  return null;
}

function authHeaders(accessToken) {
  return {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${accessToken}`,
  };
}

// ============================================
// 시나리오 1: 읽기 위주 (비로그인 + 로그인 사용자)
// ============================================
export function readHeavyScenario() {
  group('게시글 목록 조회', () => {
    const res = http.get(`${BASE_URL}/posts?limit=10`, {
      tags: { name: 'posts_list' },
    });

    postListDuration.add(res.timings.duration);

    const success = check(res, {
      'posts list status 200': (r) => r.status === 200,
      'posts list has data': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.data && body.data.posts;
        } catch (e) {
          return false;
        }
      },
    });

    errorRate.add(!success);
  });

  sleep(1);

  group('게시글 상세 조회', () => {
    const postId = getRandomPostId();
    const res = http.get(`${BASE_URL}/posts/${postId}`, {
      tags: { name: 'post_detail' },
    });

    postDetailDuration.add(res.timings.duration);

    const success = check(res, {
      'post detail status 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    errorRate.add(!success && res.status !== 404);
  });

  sleep(1);

  group('댓글 목록 조회', () => {
    const postId = getRandomPostId();
    const res = http.get(`${BASE_URL}/posts/${postId}/comments?limit=10`, {
      tags: { name: 'comments_list' },
    });

    const success = check(res, {
      'comments list status 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    errorRate.add(!success && res.status !== 404);
  });

  sleep(2);
}

// ============================================
// 시나리오 2: 쓰기 위주 (로그인 사용자)
// ============================================
export function writeHeavyScenario() {
  const user = getRandomUser();
  const auth = login(user.email, user.password);

  if (!auth) {
    console.log(`Login failed for ${user.email}`);
    sleep(5);
    return;
  }

  sleep(1);

  group('게시글 작성', () => {
    const res = http.post(
      `${BASE_URL}/posts`,
      JSON.stringify({
        title: `k6 테스트 게시글 ${Date.now()}`,
        content: '이것은 k6 부하테스트에서 생성된 게시글입니다.',
      }),
      {
        headers: authHeaders(auth.accessToken),
        tags: { name: 'create_post' },
      }
    );

    const success = check(res, {
      'create post status 201': (r) => r.status === 201,
    });

    errorRate.add(!success);
  });

  sleep(1);

  group('게시글 좋아요', () => {
    const postId = getRandomPostId();
    const res = http.post(
      `${BASE_URL}/posts/${postId}/like`,
      null,
      {
        headers: authHeaders(auth.accessToken),
        tags: { name: 'like_post' },
      }
    );

    // 409 (이미 좋아요) 또는 404 (게시글 없음)도 정상 처리
    const success = check(res, {
      'like post status ok': (r) => [200, 404, 409].includes(r.status),
    });

    errorRate.add(!success);
  });

  sleep(1);

  group('댓글 작성', () => {
    const postId = getRandomPostId();
    const res = http.post(
      `${BASE_URL}/posts/${postId}/comments`,
      JSON.stringify({
        comment: `k6 테스트 댓글 ${Date.now()}`,
      }),
      {
        headers: authHeaders(auth.accessToken),
        tags: { name: 'create_comment' },
      }
    );

    const success = check(res, {
      'create comment status 201 or 404': (r) => r.status === 201 || r.status === 404,
    });

    errorRate.add(!success && res.status !== 404);
  });

  sleep(1);

  // 50% 확률로 이미지 업로드 (Presigned URL 방식)
  if (Math.random() < 0.5) {
    group('이미지 업로드 (Presigned URL)', () => {
      const img = getRandomImage();
      const presignedRes = http.get(
        `${BASE_URL}/images/presigned-url?filename=${img.name}&content_type=${encodeURIComponent(img.type)}`,
        {
          headers: { Authorization: `Bearer ${auth.accessToken}` },
          tags: { name: 'presigned_url' },
        }
      );

      presignedUrlDuration.add(presignedRes.timings.duration);

      const presignedOk = check(presignedRes, {
        'presigned url status 201': (r) => r.status === 201,
      });

      if (presignedOk) {
        const uploadUrl = JSON.parse(presignedRes.body).data.upload_url;
        const s3Res = http.put(uploadUrl, img.data, {
          headers: { 'Content-Type': img.type, 'x-amz-acl': 'public-read' },
          tags: { name: 's3_upload' },
        });

        s3UploadDuration.add(s3Res.timings.duration);

        check(s3Res, {
          's3 upload status 200': (r) => r.status === 200,
        });
      }

      errorRate.add(!presignedOk);
    });
  }

  sleep(2);
}

// ============================================
// 기본 실행 (단일 시나리오 테스트용)
// ============================================
export default function () {
  readHeavyScenario();
}
