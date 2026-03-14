import { spawn } from 'node:child_process';

function run(command, label, env = {}) {
  return new Promise((resolve) => {
    const child = spawn(command, {
      cwd: process.cwd(),
      shell: process.platform === 'win32' ? 'powershell.exe' : true,
      stdio: 'inherit',
      env: { ...process.env, ...env },
    });

    child.on('exit', (code) => {
      resolve({ label, code: code ?? 1 });
    });
  });
}

const strictEnv = {
  SCHEMATHESIS_SEED: process.env.SCHEMATHESIS_SEED || '20260308',
  SCHEMATHESIS_STRICT: 'true',
};

const [contract, fuzz] = await Promise.all([
  run('npm run tests:contract:strict', 'contract', strictEnv),
  run('npm run tests:fuzz', 'fuzz', strictEnv),
]);

const failures = [contract, fuzz].filter((item) => item.code !== 0);
if (failures.length > 0) {
  process.stderr.write(`Contract stage failed: ${failures.map((item) => `${item.label}=${item.code}`).join(', ')}\n`);
  process.exit(1);
}

process.stdout.write('Contract stage passed: strict + fuzz\n');
