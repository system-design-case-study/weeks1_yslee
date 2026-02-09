import http from 'k6/http';
import { check } from 'k6';

export const LANDMARKS = [
  { name: '강남', latitude: 37.4979, longitude: 127.0276 },
  { name: '홍대', latitude: 37.5563, longitude: 126.9236 },
  { name: '잠실', latitude: 37.5133, longitude: 127.1001 },
  { name: '서울역', latitude: 37.5547, longitude: 126.9707 },
  { name: '명동', latitude: 37.5636, longitude: 126.9869 },
];

export const CATEGORIES = [
  'korean_food', 'chinese_food', 'japanese_food', 'western_food',
  'cafe', 'bar', 'convenience', 'pharmacy', 'hair_salon', 'gym',
];

export function seedBusinesses(baseUrl, count) {
  const CENTER = { latitude: 37.5512, longitude: 126.9882 };
  const RADIUS_DEG = 0.13;

  const batchSize = 500;
  for (let offset = 0; offset < count; offset += batchSize) {
    const batch = [];
    const end = Math.min(offset + batchSize, count);
    for (let i = offset; i < end; i++) {
      const lat = CENTER.latitude + (Math.random() - 0.5) * 2 * RADIUS_DEG;
      const lng = CENTER.longitude + (Math.random() - 0.5) * 2 * RADIUS_DEG;
      batch.push({
        name: `seed-biz-${i}`,
        address: `Seoul Address ${i}`,
        latitude: lat,
        longitude: lng,
        category: CATEGORIES[i % CATEGORIES.length],
      });
    }

    const res = http.post(`${baseUrl}/v1/businesses/seed`, JSON.stringify(batch), {
      headers: { 'Content-Type': 'application/json' },
      timeout: '120s',
    });

    check(res, {
      'seed batch status is 201': (r) => r.status === 201,
    });
  }
}

export function randomLandmark() {
  return LANDMARKS[Math.floor(Math.random() * LANDMARKS.length)];
}

export function randomCategory() {
  return CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
}
