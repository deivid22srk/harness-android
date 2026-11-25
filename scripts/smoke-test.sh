#!/bin/sh
# Smoke test for the runtime chain: Alpine -> Node -> Pi -> RPC.
#
# Runs inside an aarch64 Alpine container (see .github/workflows/build.yml).
# Proves that the pinned Pi version installs on the Alpine Node runtime, that
# the RPC handshake responds, AND — since the sessions redesign (Frente A) —
# that a session survives a process kill and is fully restored afterwards.
# That last part is the objective, CI-enforced proof that the "sessões por
# projeto" bug is fixed (PROMPT §4.4), without needing an Android device.
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

# ---------------------------------------------------------------------------
# Sessions persistence proof (Frente A).
#
# Mirrors exactly what the app does:
#   1. A session file exists in the project's deterministic Pi directory
#      (--<cwd>-- convention of docs/session-format.md).
#   2. The app sends switch_session -> get_messages and renders the history.
#   3. The Pi process dies (kill -9 — process death, PROMPT §4.1.1).
#   4. A NEW Pi process is started in the same cwd and the SAME session file
#      is switched to again; the history must come back identical.
# ---------------------------------------------------------------------------
echo "==> Sessions persistence (switch_session survives process death)"
PROJ_DIR="/root/projects/demo"
SESSIONS_DIR="$HOME/.pi/agent/sessions/--root-projects-demo--"
mkdir -p "$PROJ_DIR" "$SESSIONS_DIR"

# Fixture in the documented format (v3): header, user, assistant, session_info.
FIXTURE="$SESSIONS_DIR/2026-01-01T00-00-00-000Z_11111111-2222-3333-4444-555555555555.jsonl"
cat > "$FIXTURE" <<'JSON'
{"type":"session","version":3,"id":"11111111-2222-3333-4444-555555555555","timestamp":"2026-01-01T00:00:00.000Z","cwd":"/root/projects/demo"}
{"type":"message","id":"e1","parentId":null,"timestamp":"2026-01-01T00:00:01.000Z","message":{"role":"user","content":"Crie um arquivo teste.txt","timestamp":1764000001000}}
{"type":"message","id":"e2","parentId":"e1","timestamp":"2026-01-01T00:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"Feito! Arquivo criado."}],"api":"anthropic-messages","provider":"anthropic","model":"claude-sonnet-4-5","stopReason":"stop","timestamp":1764000002000}}
{"type":"session_info","id":"e3","parentId":"e2","timestamp":"2026-01-01T00:00:03.000Z","name":"smoke-persist"}
JSON

node /scripts/session-persistence.js "$FIXTURE"

echo "==> Chain OK"
