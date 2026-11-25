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

echo "==> RPC handshake SEM NENHUMA CHAVE (get_state + get_available_models)"
# Prova objetiva (PROMPT §4.4): o Pi responde ao RPC com o ambiente limpo,
# sem nenhuma credencial de provedor — conexão não depende da chave.
env -i PATH="$PATH" HOME="$HOME" node - <<'JS'
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
proc.stdin.write(JSON.stringify({ id: 'smoke-3', type: 'get_available_models' }) + '\n');

const deadline = Date.now() + 30000;
const timer = setInterval(() => {
  const done = seen.has('get_state') && seen.has('get_available_thinking_levels') && seen.has('get_available_models');
  if (done || Date.now() > deadline) {
    clearInterval(timer);
    const state = seen.get('get_state');
    const models = seen.get('get_available_models');
    const modelCount = models && models.data && Array.isArray(models.data.models)
      ? models.data.models.length : -1;
    console.log(`  get_state: success=${state ? state.success : 'timeout'}`);
    console.log(`  get_available_models: success=${models ? models.success : 'timeout'} models=${modelCount}`);
    proc.stdin.end();
    proc.kill();
    // The objective proof: the RPC answers get_state with NO credentials in
    // the environment. The models command must ANSWER (its success flag and
    // shape are logged for diagnostics, not asserted — Pi's model catalog
    // behavior without keys is version-specific).
    const stateOk = state && state.success === true;
    const answered = seen.size >= 3;
    console.log(`  respondeu todos os comandos: ${answered ? 'sim' : 'nao'}`);
    if (models && models.success === false) {
      console.log(`  get_available_models error: ${models.error || '(sem mensagem)'}`);
    }
    const ok = stateOk && answered;
    console.log(ok ? 'RPC_OK_SEM_CHAVE' : 'RPC_FAILED');
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

# ---------------------------------------------------------------------------
# Packages proof (Frente C): install a small pinned package, verify the
# declaration lands in ~/.pi/agent/settings.json (the app's source of truth),
# verify `pi list`, remove it and verify the declaration is gone.
# Uses a PINNED version so the test is deterministic (PROMPT §5.4).
# ---------------------------------------------------------------------------
echo "==> Packages (pi install/list/remove)"
PKG="npm:cc-safety-net@2.8.3"
SETTINGS="$HOME/.pi/agent/settings.json"

pi install "$PKG" >/dev/null 2>&1 || { echo "PACKAGES_FAILED: install"; exit 1; }
node -e '
const fs = require("fs");
const s = JSON.parse(fs.readFileSync(process.env.HOME + "/.pi/agent/settings.json", "utf8"));
const ok = Array.isArray(s.packages) && s.packages.some(p => String(p).startsWith("npm:cc-safety-net@2.8.3"));
console.log("  install declared:", ok);
process.exit(ok ? 0 : 1);
' || { echo "PACKAGES_FAILED: settings.json"; exit 1; }
pi list | grep -q "cc-safety-net" || { echo "PACKAGES_FAILED: pi list"; exit 1; }

pi remove "npm:cc-safety-net" >/dev/null 2>&1 || { echo "PACKAGES_FAILED: remove"; exit 1; }
node -e '
const fs = require("fs");
const s = JSON.parse(fs.readFileSync(process.env.HOME + "/.pi/agent/settings.json", "utf8"));
const ok = Array.isArray(s.packages) && !s.packages.some(p => String(p).includes("cc-safety-net"));
console.log("  remove declared:", ok);
process.exit(ok ? 0 : 1);
' || { echo "PACKAGES_FAILED: settings after remove"; exit 1; }

echo "PACKAGES_OK"

echo "==> Chain OK"
