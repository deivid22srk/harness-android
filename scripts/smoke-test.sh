#!/bin/sh
# Smoke test for the runtime chain: Alpine -> Node -> Pi -> RPC.
#
# Runs inside an aarch64 Alpine container (see .github/workflows/build.yml).
# This proves that the pinned Pi version installs on the Alpine Node runtime
# and that the RPC handshake responds, without needing an Android device.
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
python3 - <<'PY'
import json, subprocess, sys, time

proc = subprocess.Popen(
    ["pi", "--mode", "rpc", "--no-session"],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
)

def send(obj):
    proc.stdin.write((json.dumps(obj) + "\n").encode())
    proc.stdin.flush()

send({"id": "smoke-1", "type": "get_state"})
send({"id": "smoke-2", "type": "get_available_thinking_levels"})

deadline = time.time() + 30
seen = {}
while time.time() < deadline and len(seen) < 2:
    line = proc.stdout.readline()
    if not line:
        break
    try:
        rec = json.loads(line)
    except ValueError:
        continue
    if rec.get("type") == "response":
        seen[rec.get("command")] = rec

proc.stdin.close()
proc.terminate()

ok = "get_state" in seen and "get_available_thinking_levels" in seen
for cmd, rec in seen.items():
    print(f"  {cmd}: success={rec.get('success')}")
    assert rec.get("success") is True, f"{cmd} failed: {rec}"

print("RPC_OK" if ok else "RPC_FAILED")
sys.exit(0 if ok else 1)
PY

echo "==> Chain OK"
