// Sessions persistence proof (see smoke-test.sh).
//
// Phase 1: start pi RPC in /root/projects/demo, switch to the fixture session,
//          read the history back, capture get_state, then kill -9 the process.
// Phase 2: start a NEW pi RPC in the same cwd, switch to the same session file
//          again and verify the history came back identical.
//
// Exits 0 (SESSIONS_OK) or 1 (SESSIONS_FAILED). No API keys required: the
// switch_session/get_messages path does not call a model.
'use strict';

const { spawn } = require('node:child_process');
const fs = require('node:fs');

const SESSION_FILE = process.argv[2];
if (!SESSION_FILE) {
  console.error('usage: session-persistence.js <session-file>');
  process.exit(1);
}

const CWD = process.env.PI_CWD || '/root/projects/demo';
const TIMEOUT_MS = 30000;

function drive() {
  return new Promise((resolve, reject) => {
    const proc = spawn(process.env.PI_BIN || 'pi', ['--mode', 'rpc'], {
      stdio: ['pipe', 'pipe', 'ignore'],
      cwd: CWD,
    });

    let buffer = '';
    const responses = new Map();
    proc.stdout.setEncoding('utf8');
    proc.stdout.on('data', (chunk) => {
      buffer += chunk;
      let idx;
      while ((idx = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 1);
        if (!line.trim()) continue;
        let rec;
        try { rec = JSON.parse(line); } catch { continue; }
        if (rec.type === 'response' && rec.id) responses.set(rec.id, rec);
      }
    });

    const call = (id, payload) =>
      new Promise((res, rej) => {
        const started = Date.now();
        const timer = setInterval(() => {
          const r = responses.get(id);
          if (r) {
            clearInterval(timer);
            res(r);
          } else if (Date.now() - started > TIMEOUT_MS) {
            clearInterval(timer);
            rej(new Error(`timeout waiting for ${payload.type}`));
          }
        }, 50);
        proc.stdin.write(JSON.stringify({ id, ...payload }) + '\n');
      });

    (async () => {
      try {
        const sw = await call('s1', { type: 'switch_session', sessionPath: SESSION_FILE });
        if (!sw.success || sw.data?.cancelled) throw new Error('switch_session rejected');
        const msgs = await call('s2', { type: 'get_messages' });
        if (!msgs.success) throw new Error('get_messages failed');
        const state = await call('s3', { type: 'get_state' });
        if (!state.success) throw new Error('get_state failed');
        resolve({
          messages: msgs.data.messages,
          sessionName: state.data.sessionName,
          sessionFile: state.data.sessionFile,
          sessionId: state.data.sessionId,
          kill: () => proc.kill('SIGKILL'),
          killAll: () => { proc.kill('SIGKILL'); proc.stdin.end(); },
        });
      } catch (err) {
        proc.kill('SIGKILL');
        reject(err);
      }
    })();
  });
}

(async () => {
  try {
    // ---- Phase 1: resume + capture + kill -9 (process death) ----
    const first = await drive();
    const roles = first.messages.map((m) => m.role).join(',');
    const texts = first.messages.map((m) =>
      typeof m.content === 'string' ? m.content : (m.content?.[0]?.text || ''),
    );
    console.log(`  phase1: roles=[${roles}] name=${first.sessionName}`);
    if (roles !== 'user,assistant') throw new Error(`unexpected roles: ${roles}`);
    if (!texts[0].includes('Crie um arquivo')) throw new Error('user text missing');
    if (!texts[1].includes('Feito! Arquivo criado')) throw new Error('assistant text missing');
    if (first.sessionName !== 'smoke-persist') throw new Error('session name not restored');
    if (first.sessionId !== '11111111-2222-3333-4444-555555555555') {
      throw new Error('session id not restored');
    }
    first.kill();
    await new Promise((r) => setTimeout(r, 500));
    console.log('  phase1: process killed (SIGKILL)');

    // ---- Phase 2: NEW process, same session, history must be back ----
    const second = await drive();
    const texts2 = second.messages.map((m) =>
      typeof m.content === 'string' ? m.content : (m.content?.[0]?.text || ''),
    );
    console.log(`  phase2: messages=${second.messages.length} file=${second.sessionFile}`);
    if (second.messages.length !== 2) throw new Error('history not fully restored');
    if (texts2.join('|') !== texts.join('|')) throw new Error('restored text differs');
    second.killAll();
    console.log('SESSIONS_OK');
    process.exit(0);
  } catch (err) {
    console.error(`SESSIONS_FAILED: ${err.message}`);
    process.exit(1);
  }
})();
