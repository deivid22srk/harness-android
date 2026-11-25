#!/bin/sh
# Smoke test for the runtime chain: Alpine -> Node -> Pi -> RPC.
#
# Runs inside an aarch64 Alpine container (see .github/workflows/build.yml).
# Proves that the pinned Pi version installs on the Alpine Node runtime and that
# the RPC handshake responds — without needing an Android device.
set -eu

PI_VERSION="${1:-1.6.0}"

echo "==> Alpine $(cat /etc/alpine-release)"
apk add --no-cache nodejs npm >/dev/null
echo "==> node $(node --version) / npm $(npm --version)"

echo "==> Installing Pi $PI_VERSION"
npm install -g "@earendil-works/pi-coding-agent@${PI_VERSION}" >/dev/null

echo "==> pi --version"
pi --version

echo "==> RPC handshake (get_state)"
node - <<'JS'
const { spawn } = require('node:child_process');

const proc = spawn('pi', ['--mode', 'rpc', '--no-session'], {
  stdio: ['pipe', 'pipe', 'ignore'],
});

let buffer = '';
const seen = new Map();

proc.stdout.setEncoding('utf8');
proc.stdout.on('data', (chunk) => {
  buffer += chunk;
  let idx;
  while ((idx = buffer.indexOf('\n')) >= 0) {
    const line = buffer.slice(0, idx).replace(/\r$/, '');
    buffer = buffer.slice(idx + 1);
    if (!line.trim()) continue;
    let rec;
    try { rec = JSON.parse(line); } catch { continue; }
    if (rec.type === 'response') seen.set(rec.command, rec);
  }
});

proc.stdin.write(JSON.stringify({ id: 'smoke-1', type: 'get_state' }) + '\n');
proc.stdin.write(JSON.stringify({ id: 'smoke-2', type: 'get_available_thinking_levels' }) + '\n');

const deadline = Date.now() + 30000;
const timer = setInterval(() => {
  const done = seen.has('get_state') && seen.has('get_available_thinking_levels');
  if (done || Date.now() > deadline) {
    clearInterval(timer);
    proc.stdin.end();
    proc.kill();
    let ok = true;
    for (const [cmd, rec] of seen) {
      console.log(`  ${cmd}: success=${rec.success}`);
      if (rec.success !== true) ok = false;
    }
    if (seen.size < 2) ok = false;
    console.log(ok ? 'RPC_OK' : 'RPC_FAILED');
    process.exit(ok ? 0 : 1);
  }
}, 250);
JS

echo "==> Chain OK"
