import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9080';
const LOGIN_EMAIL = __ENV.PERF_LOGIN_EMAIL || 'admin@samt.local';
const LOGIN_PASSWORD = __ENV.PERF_LOGIN_PASSWORD || 'Str0ng@Pass!';

export const options = {
  scenarios: {
    login_warmup: {
      executor: 'constant-vus',
      exec: 'loginScenario',
      vus: 10,
      duration: '45s',
      tags: { scenario: 'warmup-login' }
    },
    main_api_steady: {
      executor: 'constant-vus',
      exec: 'mainApiScenario',
      vus: 50,
      duration: '90s',
      startTime: '45s',
      tags: { scenario: 'main-api-50-users' }
    },
    report_burst: {
      executor: 'shared-iterations',
      exec: 'reportScenario',
      vus: 50,
      iterations: 200,
      maxDuration: '60s',
      startTime: '135s',
      tags: { scenario: 'report-burst-200' }
    }
  },
  thresholds: {
    http_req_duration: ['avg<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99']
  }
};

function jsonOrNull(response) {
  try {
    return response.json();
  } catch (_error) {
    return null;
  }
}

function parseAccessToken(response) {
  const payload = jsonOrNull(response);
  if (!payload) return '';
  return payload?.data?.accessToken || payload?.accessToken || '';
}

function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json'
  };
}

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: LOGIN_EMAIL, password: LOGIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login-setup' } }
  );

  check(loginRes, {
    'setup login status is 200': (r) => r.status === 200
  });

  const token = parseAccessToken(loginRes);
  if (!token) {
    throw new Error('Failed to acquire access token in setup');
  }

  return { token };
}

export function loginScenario() {
  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: LOGIN_EMAIL, password: LOGIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login' } }
  );

  check(response, {
    'login status is 200': (r) => r.status === 200,
    'login returns access token': (r) => Boolean(parseAccessToken(r))
  });

  sleep(1);
}

export function mainApiScenario(data) {
  const response = http.get(`${BASE_URL}/api/users/me`, {
    headers: authHeaders(data.token),
    tags: { endpoint: 'main-api-users-me' }
  });

  check(response, {
    'main API status is 200': (r) => r.status === 200
  });

  sleep(0.3);
}

export function reportScenario(data) {
  const response = http.get(`${BASE_URL}/api/reports/lecturer/overview`, {
    headers: authHeaders(data.token),
    tags: { endpoint: 'report-overview' }
  });

  check(response, {
    'report endpoint status is 200': (r) => r.status === 200
  });
}
