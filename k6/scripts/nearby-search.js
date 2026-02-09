import http from 'k6/http';
import { check, sleep } from 'k6';
import { seedBusinesses, randomLandmark } from './helpers/seed-data.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '15s', target: 100 },
    { duration: '15s', target: 200 },
    { duration: '15s', target: 300 },
    { duration: '15s', target: 500 },
    { duration: '30s', target: 500 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<100'],
    http_req_failed: ['rate<0.01'],
  },
};

export function setup() {
  seedBusinesses(BASE_URL, 100000);
}

export default function () {
  const landmark = randomLandmark();
  const radius = 5000;

  const res = http.get(
    `${BASE_URL}/v1/search/nearby?latitude=${landmark.latitude}&longitude=${landmark.longitude}&radius=${radius}`
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response has results': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.total > 0 || (Array.isArray(body) && body.length > 0);
      } catch {
        return false;
      }
    },
  });

  sleep(0.1);
}
