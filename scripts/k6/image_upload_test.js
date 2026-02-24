import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================
// 이미지 업로드 부하 테스트
// Presigned URL 발급 + S3 직접 업로드 + Multipart 업로드
// ============================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const errorRate = new Rate('errors');
const presignedDuration = new Trend('presigned_url_duration');
const s3UploadDuration = new Trend('s3_upload_duration');
const multipartDuration = new Trend('multipart_upload_duration');
const uploadOps = new Counter('upload_operations');

// 테스트 이미지 파일 로드 (init 단계에서 실행)
const testImages = [
  { data: open('./testdata/test_image1.jpg', 'b'), name: 'test_image1.jpg', type: 'image/jpeg' },
  { data: open('./testdata/test_image2.jpg', 'b'), name: 'test_image2.jpg', type: 'image/jpeg' },
  { data: open('./testdata/test_image3.jpeg', 'b'), name: 'test_image3.jpeg', type: 'image/jpeg' },
  { data: open('./testdata/test_image4.jpeg', 'b'), name: 'test_image4.jpeg', type: 'image/jpeg' },
  { data: open('./testdata/test_image5.jpeg', 'b'), name: 'test_image5.jpeg', type: 'image/jpeg' },
  { data: open('./testdata/test_image6.jpeg', 'b'), name: 'test_image6.jpeg', type: 'image/jpeg' },
];

export const options = {
  scenarios: {
    // 시나리오 1: Presigned URL 발급 + S3 직접 업로드 (70%)
    presigned_upload: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '1m30s', target: 20 },
        { duration: '1m', target: 20 },
        { duration: '20s', target: 0 },
      ],
      exec: 'presignedUploadScenario',
    },
    // 시나리오 2: Multipart 직접 업로드 (30%)
    multipart_upload: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 5 },
        { duration: '1m30s', target: 10 },
        { duration: '1m', target: 10 },
        { duration: '20s', target: 0 },
      ],
      exec: 'multipartUploadScenario',
      startTime: '5s',
    },
  },
  thresholds: {
    presigned_url_duration: ['p(95)<500'],
    s3_upload_duration: ['p(95)<3000'],
    multipart_upload_duration: ['p(95)<3000'],
    errors: ['rate<0.1'],
  },
};

// ============================================
// 헬퍼 함수
// ============================================
function getRandomImage() {
  return testImages[Math.floor(Math.random() * testImages.length)];
}

function login() {
  const userId = Math.floor(Math.random() * 600) + 1;
  const res = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ email: `user${userId}@test.com`, password: 'Test1234!' }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
  );

  if (res.status === 200) {
    try {
      return JSON.parse(res.body).data.accessToken;
    } catch (e) {
      return null;
    }
  }
  return null;
}

function authHeaders(token) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  };
}

// ============================================
// 시나리오 1: Presigned URL + S3 직접 업로드
// ============================================
export function presignedUploadScenario() {
  const token = login();
  if (!token) {
    errorRate.add(true);
    sleep(3);
    return;
  }

  const img = getRandomImage();

  sleep(0.5);

  // Step 1: Presigned URL 발급
  let uploadUrl = null;
  let imageId = null;

  group('Presigned URL 발급', () => {
    const res = http.get(
      `${BASE_URL}/images/presigned-url?filename=${img.name}&content_type=${encodeURIComponent(img.type)}`,
      {
        headers: { Authorization: `Bearer ${token}` },
        tags: { name: 'presigned_url' },
      }
    );

    presignedDuration.add(res.timings.duration);
    uploadOps.add(1);

    const success = check(res, {
      'presigned URL status 201': (r) => r.status === 201,
      'presigned URL has uploadUrl': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.data && body.data.upload_url;
        } catch (e) {
          return false;
        }
      },
    });

    if (success) {
      const body = JSON.parse(res.body);
      uploadUrl = body.data.upload_url;
      imageId = body.data.image_id;
    }

    errorRate.add(!success);
  });

  if (!uploadUrl) {
    sleep(2);
    return;
  }

  sleep(0.5);

  // Step 2: S3에 직접 업로드 (PUT)
  group('S3 직접 업로드', () => {
    const res = http.put(uploadUrl, img.data, {
      headers: { 'Content-Type': img.type, 'x-amz-acl': 'public-read' },
      tags: { name: 's3_upload' },
    });

    s3UploadDuration.add(res.timings.duration);
    uploadOps.add(1);

    const success = check(res, {
      's3 upload status 200': (r) => r.status === 200,
    });

    errorRate.add(!success);
  });

  sleep(1);
}

// ============================================
// 시나리오 2: Multipart 직접 업로드
// ============================================
export function multipartUploadScenario() {
  const token = login();
  if (!token) {
    errorRate.add(true);
    sleep(3);
    return;
  }

  const img = getRandomImage();

  sleep(0.5);

  group('Multipart 이미지 업로드', () => {
    const res = http.post(`${BASE_URL}/images`, {
      file: http.file(img.data, img.name, img.type),
    }, {
      headers: { Authorization: `Bearer ${token}` },
      tags: { name: 'multipart_upload' },
    });

    multipartDuration.add(res.timings.duration);
    uploadOps.add(1);

    const success = check(res, {
      'multipart upload status 201': (r) => r.status === 201,
      'multipart upload has imageId': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.data && body.data.imageId;
        } catch (e) {
          return false;
        }
      },
    });

    errorRate.add(!success);
  });

  sleep(2);
}

// ============================================
// 기본 실행
// ============================================
export default function () {
  presignedUploadScenario();
}
