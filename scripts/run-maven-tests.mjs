import { spawnSync } from 'node:child_process';

const argsFromCli = process.argv.slice(2);
const goalArg = argsFromCli.find((arg) => arg.startsWith('--goal='));
const goal = goalArg ? goalArg.split('=')[1] : 'test';
const includeITs = argsFromCli.includes('--includeITs');

const mvnw = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';
const args = ['-T', '1C', '-B', '-DtrimStackTrace=false'];

if (!includeITs) {
  args.push('-DskipITs=true');
}

args.push(goal);

const result = spawnSync(mvnw, args, {
  cwd: process.cwd(),
  shell: process.platform === 'win32',
  stdio: 'inherit',
  env: process.env,
});

if (result.error) {
  process.stderr.write(`${result.error.message}\n`);
  process.exit(1);
}

process.exit(result.status ?? 1);
