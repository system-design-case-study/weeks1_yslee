import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { seedBusinesses, randomLandmark, randomCategory } from './helpers/seed-data.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const searchCount = new Counter('crud_search_total');
const getCount = new Counter('crud_get_total');
const createCount = new Counter('crud_create_total');
const updateCount = new Counter('crud_update_total');
const deleteCount = new Counter('crud_delete_total');

export const options = {
  stages: [
    { duration: '15s', target: 50 },
    { duration: '15s', target: 100 },
    { duration: '15s', target: 200 },
    { duration: '30s', target: 200 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

const createdIds = [];

export function setup() {
  seedBusinesses(BASE_URL, 100000);
}

function doSearch() {
  const landmark = randomLandmark();
  const res = http.get(
    `${BASE_URL}/v1/search/nearby?latitude=${landmark.latitude}&longitude=${landmark.longitude}&radius=5000`
  );
  check(res, { 'search: status 200': (r) => r.status === 200 });
  searchCount.add(1);

  try {
    const body = JSON.parse(res.body);
    if (body.businesses && body.businesses.length > 0) {
      return body.businesses[0].id;
    }
    if (Array.isArray(body) && body.length > 0) {
      return body[0].id;
    }
  } catch { /* ignore */ }
  return null;
}

function doGet(id) {
  if (!id) return;
  const res = http.get(`${BASE_URL}/v1/businesses/${id}`);
  check(res, { 'get: status 200': (r) => r.status === 200 });
  getCount.add(1);
}

function doCreate() {
  const landmark = randomLandmark();
  const payload = JSON.stringify({
    name: `k6-test-${Date.now()}`,
    address: `Test Address ${Math.floor(Math.random() * 10000)}`,
    latitude: landmark.latitude + (Math.random() - 0.5) * 0.01,
    longitude: landmark.longitude + (Math.random() - 0.5) * 0.01,
    category: randomCategory(),
  });

  const res = http.post(`${BASE_URL}/v1/businesses`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'create: status 2xx': (r) => r.status >= 200 && r.status < 300 });
  createCount.add(1);

  try {
    const body = JSON.parse(res.body);
    if (body.id) createdIds.push(body.id);
  } catch { /* ignore */ }
}

function doUpdate(id) {
  if (!id) return;
  const payload = JSON.stringify({
    name: `k6-updated-${Date.now()}`,
    address: `Updated Address ${Math.floor(Math.random() * 10000)}`,
    latitude: 37.4979 + (Math.random() - 0.5) * 0.01,
    longitude: 127.0276 + (Math.random() - 0.5) * 0.01,
    category: randomCategory(),
  });

  const res = http.put(`${BASE_URL}/v1/businesses/${id}`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(res, { 'update: status 2xx': (r) => r.status >= 200 && r.status < 300 });
  updateCount.add(1);
}

function doDelete(id) {
  if (!id) return;
  const res = http.del(`${BASE_URL}/v1/businesses/${id}`);
  check(res, { 'delete: status 2xx': (r) => r.status >= 200 && r.status < 300 });
  deleteCount.add(1);
}

export default function () {
  const roll = Math.random() * 100;
  let lastId = null;

  if (roll < 60) {
    lastId = doSearch();
  } else if (roll < 80) {
    lastId = doSearch();
    doGet(lastId);
  } else if (roll < 90) {
    doCreate();
  } else if (roll < 95) {
    lastId = doSearch();
    doUpdate(lastId);
  } else {
    if (createdIds.length > 0) {
      doDelete(createdIds.pop());
    } else {
      doSearch();
    }
  }

  sleep(0.1);
}
