import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:9080';
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || 'admin@samt.local';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'Str0ng@Pass!';
const PROFILE = __ENV.K6_PROFILE || 'full';
const JIRA_HOST = __ENV.JIRA_BASE_URL || __ENV.JIRA_HOST || 'https://example.atlassian.net';
const JIRA_EMAIL = __ENV.JIRA_EMAIL || 'qa@example.com';
const JIRA_TOKEN = __ENV.JIRA_API_TOKEN || __ENV.JIRA_TOKEN || 'ATATTAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';
const GITHUB_REPO = __ENV.GITHUB_REPO_URL
  || (__ENV.GITHUB_REPO ? `https://github.com/${String(__ENV.GITHUB_REPO).replace(/^https?:\/\/github\.com\//, '')}` : 'https://github.com/example-org/example-repo');
const GITHUB_TOKEN = __ENV.GITHUB_TOKEN || __ENV.GITHUB_PAT || 'ghp_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA';

function scenariosForProfile(profile) {
  if (profile === 'smoke') {
    return {
      login_flow: { exec: 'loginFlow', executor: 'constant-vus', vus: 2, duration: '20s' },
      profile_retrieval: { exec: 'profileRetrieval', executor: 'constant-vus', vus: 2, duration: '20s' },
      group_creation: { exec: 'groupCreation', executor: 'constant-vus', vus: 1, duration: '20s' },
      project_config_verification: { exec: 'projectConfigVerification', executor: 'constant-vus', vus: 1, duration: '20s' }
    };
  }

  if (profile === 'steady') {
    return {
      login_flow: { exec: 'loginFlow', executor: 'ramping-vus', stages: [{ duration: '20s', target: 6 }, { duration: '60s', target: 12 }, { duration: '20s', target: 0 }] },
      profile_retrieval: { exec: 'profileRetrieval', executor: 'ramping-vus', stages: [{ duration: '20s', target: 8 }, { duration: '60s', target: 18 }, { duration: '20s', target: 0 }] },
      group_creation: { exec: 'groupCreation', executor: 'ramping-vus', stages: [{ duration: '20s', target: 2 }, { duration: '40s', target: 4 }, { duration: '20s', target: 0 }] },
      project_config_verification: { exec: 'projectConfigVerification', executor: 'ramping-vus', stages: [{ duration: '20s', target: 2 }, { duration: '40s', target: 4 }, { duration: '20s', target: 0 }] }
    };
  }

  return {
    login_flow: { exec: 'loginFlow', executor: 'ramping-vus', stages: [{ duration: '20s', target: 6 }, { duration: '40s', target: 12 }, { duration: '20s', target: 0 }] },
    profile_retrieval: { exec: 'profileRetrieval', executor: 'ramping-vus', stages: [{ duration: '20s', target: 12 }, { duration: '40s', target: 24 }, { duration: '20s', target: 0 }] },
    group_creation: { exec: 'groupCreation', executor: 'ramping-vus', startTime: '20s', stages: [{ duration: '15s', target: 3 }, { duration: '30s', target: 6 }, { duration: '15s', target: 0 }] },
    project_config_verification: { exec: 'projectConfigVerification', executor: 'ramping-vus', startTime: '35s', stages: [{ duration: '15s', target: 2 }, { duration: '30s', target: 5 }, { duration: '15s', target: 0 }] }
  };
}

function parseJson(response) {
  try {
    return JSON.parse(response.body);
  } catch (_error) {
    return null;
  }
}

function unwrapData(response) {
  const body = parseJson(response);
  return body?.data || body || null;
}

function authHeaders(token) {
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function uniqueSuffix() {
  return `${Date.now()}${__VU || 0}${__ITER || 0}`;
}

function createLecturer(token, suffix) {
  const response = http.post(
    `${BASE_URL}/api/admin/users`,
    JSON.stringify({
      email: `qa.load.lecturer.${suffix}@samt.local`,
      password: 'Str0ng@Pass!',
      fullName: 'QA Load Lecturer',
      role: 'LECTURER'
    }),
    {
      headers: {
        ...authHeaders(token),
        'Content-Type': 'application/json'
      },
      tags: { endpoint: 'admin_create_user', flow: 'load_setup' }
    }
  );

  return unwrapData(response)?.user?.id || null;
}

function createSemester(token, suffix) {
  const response = http.post(
    `${BASE_URL}/api/semesters`,
    JSON.stringify({
      semesterCode: `QA${suffix.slice(-4)}-S1`,
      semesterName: `QA Load ${suffix}`,
      startDate: '2026-01-15',
      endDate: '2026-05-30'
    }),
    {
      headers: {
        ...authHeaders(token),
        'Content-Type': 'application/json'
      },
      tags: { endpoint: 'create_semester', flow: 'load_setup' }
    }
  );

  const semesterId = unwrapData(response)?.id || null;
  if (semesterId) {
    http.patch(`${BASE_URL}/api/semesters/${semesterId}/activate`, null, {
      headers: authHeaders(token),
      tags: { endpoint: 'activate_semester', flow: 'load_setup' }
    });
  }
  return semesterId;
}

function createGroup(token, semesterId, lecturerId, suffix, tags = { endpoint: 'create_group', flow: 'group_creation' }) {
  const groupCode = suffix.slice(-4).padStart(4, '0');
  const response = http.post(
    `${BASE_URL}/api/groups`,
    JSON.stringify({
      groupName: `SE${groupCode}-G${(__VU || 1) % 9 || 1}`,
      semesterId,
      lecturerId
    }),
    {
      headers: {
        ...authHeaders(token),
        'Content-Type': 'application/json'
      },
      tags
    }
  );

  return {
    response,
    groupId: unwrapData(response)?.id || null
  };
}

function createProjectConfig(token, groupId, tags = { endpoint: 'create_project_config', flow: 'load_setup' }) {
  const response = http.post(
    `${BASE_URL}/api/project-configs`,
    JSON.stringify({
      groupId,
      jiraHostUrl: JIRA_HOST,
      jiraEmail: JIRA_EMAIL,
      jiraApiToken: JIRA_TOKEN,
      githubRepoUrl: GITHUB_REPO,
      githubToken: GITHUB_TOKEN
    }),
    {
      headers: {
        ...authHeaders(token),
        'Content-Type': 'application/json'
      },
      tags
    }
  );

  return {
    response,
    configId: unwrapData(response)?.id || null
  };
}

function verifyProjectConfig(token, configId, tags = { endpoint: 'verify_project_config', flow: 'project_verification' }) {
  return http.post(`${BASE_URL}/api/project-configs/${configId}/verify`, null, {
    headers: authHeaders(token),
    tags
  });
}

function waitForReadiness(token, url, acceptedStatuses) {
  for (let attempt = 0; attempt < 10; attempt += 1) {
    const response = http.get(url, {
      headers: authHeaders(token),
      tags: { endpoint: 'readiness', flow: 'load_setup' }
    });
    if (acceptedStatuses.includes(response.status)) {
      return true;
    }
    sleep(1);
  }
  return false;
}

export const options = {
  scenarios: scenariosForProfile(PROFILE),
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500'],
    'http_req_duration{flow:login}': ['p(95)<900'],
    'http_req_duration{flow:profile}': ['p(95)<900'],
    'http_req_duration{flow:group_creation}': ['p(95)<1800'],
    'http_req_duration{flow:project_verification}': ['p(95)<2500'],
    checks: ['rate>0.95']
  }
};

function parseLoginToken(response) {
  const data = unwrapData(response);
  return data?.accessToken || parseJson(response)?.accessToken || '';
}

export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login', flow: 'login' } }
  );

  check(loginRes, {
    'setup login succeeded': (response) => response.status === 200
  });

  const token = parseLoginToken(loginRes);
  const suffix = `${Date.now()}`;
  const lecturerId = createLecturer(token, suffix);
  const semesterId = createSemester(token, suffix);
  if (lecturerId) {
    waitForReadiness(token, `${BASE_URL}/api/users/${lecturerId}`, [200]);
  }
  if (semesterId) {
    waitForReadiness(token, `${BASE_URL}/api/semesters/${semesterId}`, [200]);
  }
  const group = createGroup(token, semesterId, lecturerId, suffix, { endpoint: 'create_group', flow: 'load_setup' });
  const config = group.groupId
    ? createProjectConfig(token, group.groupId, { endpoint: 'create_project_config', flow: 'load_setup' })
    : { configId: null };

  return {
    token,
    lecturerId,
    semesterId,
    groupId: group.groupId,
    projectConfigId: config.configId
  };
}

export function loginFlow() {
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'login', flow: 'login' } }
  );

  check(loginRes, {
    'login remains healthy under load': (response) => response.status === 200
  });

  sleep(1);
}

export function profileRetrieval(data) {
  const headers = authHeaders(data?.token);

  group('profile-retrieval', () => {
    const profileRes = http.get(`${BASE_URL}/api/users/${data?.lecturerId}`, { headers, tags: { endpoint: 'user_detail', flow: 'profile' } });
    check(profileRes, {
      'profile retrieval is healthy': (response) => response.status === 200
    });

    const groupsRes = http.get(`${BASE_URL}/api/users?page=0&size=10`, { headers, tags: { endpoint: 'users', flow: 'profile' } });
    check(groupsRes, {
      'groups retrieval is healthy': (response) => response.status === 200
    });

    const semestersRes = http.get(`${BASE_URL}/api/semesters`, { headers, tags: { endpoint: 'semesters', flow: 'profile' } });
    check(semestersRes, {
      'semesters retrieval is healthy': (response) => response.status === 200
    });
  });

  sleep(1);
}

export function groupCreation(data) {
  const suffix = uniqueSuffix();
  const lecturerId = createLecturer(data?.token, suffix);
  if (lecturerId) {
    waitForReadiness(data?.token, `${BASE_URL}/api/users/${lecturerId}`, [200]);
  }
  const { response, groupId } = createGroup(data?.token, data?.semesterId, lecturerId || data?.lecturerId, suffix);

  check(response, {
    'group creation is healthy': (result) => [200, 201, 409].includes(result.status)
  });

  if (groupId) {
    const readBack = http.get(`${BASE_URL}/api/groups/${groupId}`, {
      headers: authHeaders(data?.token),
      tags: { endpoint: 'group_read', flow: 'group_creation' }
    });
    check(readBack, {
      'created group is readable': (result) => result.status === 200
    });
  }

  sleep(1);
}

export function projectConfigVerification(data) {
  if (!data?.projectConfigId) {
    sleep(1);
    return;
  }

  group('project-config-verification', () => {
    const verifyRes = verifyProjectConfig(data?.token, data.projectConfigId);
    check(verifyRes, {
      'project config verification is healthy': (result) => [200, 503].includes(result.status)
    });

    const fetchConfig = http.get(`${BASE_URL}/api/project-configs/${data.projectConfigId}`, {
      headers: authHeaders(data?.token),
      tags: { endpoint: 'project_config_read', flow: 'project_verification' }
    });
    check(fetchConfig, {
      'project config remains readable': (result) => result.status === 200
    });
  });

  sleep(1);
}

export default function (data) {
  profileRetrieval(data);
}
