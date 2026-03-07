import { spawnSync } from 'node:child_process';

const cwd = process.cwd();

const args = [
  'run',
  '--rm',
  '-v',
  `${cwd}:/work`,
  'schemathesis/schemathesis',
  'run',
  '/work/openapi.yaml',
  '--url=http://host.docker.internal:9080',
  '--checks=status_code_conformance,content_type_conformance,response_schema_conformance,not_a_server_error',
  '--exclude-checks=unsupported_method,ignored_auth,positive_data_acceptance,negative_data_rejection',
  '--exclude-path-regex=^/api/auth/(login|refresh|register)$',
  '--max-examples=1'
];

const result = spawnSync('docker', args, {
  stdio: 'inherit'
});

if (result.error) {
  console.error(result.error.message);
  process.exit(1);
}

process.exit(result.status ?? 1);
