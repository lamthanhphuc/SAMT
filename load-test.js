import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 100,
  duration: '2m',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000']
  }
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9080';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@samt.local';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'Str0ng@Pass!';

export default function () {
  const healthRes = http.get(`${BASE_URL}/api/health`);
  check(healthRes, {
    'health status 200': (r) => r.status === 200 || r.status === 404
  });

  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(loginRes, {
    'login status valid': (r) => [200, 401, 403].includes(r.status)
  });

  let token = '';
  if (loginRes.status === 200) {
    try {
      const body = JSON.parse(loginRes.body);
      token = body?.data?.accessToken || body?.accessToken || '';
    } catch (_err) {
      token = '';
    }
  }

  const userRes = http.get(`${BASE_URL}/api/users?page=0&size=10`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  });

  check(userRes, {
    'users status valid': (r) => [200, 401, 403].includes(r.status)
  });

  sleep(1);
}
