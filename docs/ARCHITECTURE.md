# Arquitetura — Harness

> Documento de decisões técnicas. Tudo aqui foi **validado empiricamente** no
> Termux/Android (aarch64) antes de ser fixado no código, salvo indicação em
> contrário.

## 1. Visão geral

```
┌──────────────────────────────────────────────────────────┐
│ App Android (Kotlin + Jetpack Compose)                   │
│  UI minimalista ── ViewModel/StateFlow ── RpcClient       │
│                                   │ JSONL (stdin/stdout) │
│  Foreground Service  ◄────────────┘                       │
│     └─ spawn: libproot.so  (binário nativo, jniLibs)      │
│            └─ Alpine Linux (rootfs aarch64, sem root)     │
│                  └─ node + pi  (--mode rpc)               │
└──────────────────────────────────────────────────────────┘
```

Camadas:

| Camada | Implementação |
|---|---|
| UI/orquestração | Kotlin + Jetpack Compose (Material 3, sem Material You) |
| Concorrência | Coroutines + Flow |
| JSON | kotlinx.serialization (sem reflexão, R8-friendly) |
| Transporte | `RpcClient` (JSONL sobre stdin/stdout) |
| Redução de estado | `ChatReducer` puro |
| Virtualização | PRoot estático (aarch64, Bionic) empacotado como `libproot.so` |
| Artefatos de VM | Alpine minirootfs aarch64 (baixado em runtime, SHA-256 verificado) |
| Agente | Pi `@earendil-works/pi-coding-agent` 1.6.0, modo RPC |

## 2. Versões fixadas (verificadas em 2026-11-25)

| Item | Versão | Origem |
|---|---|---|
| Pi | 1.6.0 | npm dist-tag `latest`; exige Node ≥ 22.19.0 |
| Alpine | 3.24.4 (`v3.24`) | `latest-releases.yaml`; SHA-256 `4b14c18f…18a8de` |
| Node/Alpine | 24.22.3-r0 | `main` do Alpine v3.24 (satisfaz o Pi) |
| PRoot | termux fork `v5.1.107.102` | ver §4 |
| Gradle | 8.14.3 | wrapper |
| AGP | 8.13.2 | estável |
| Kotlin | 2.3.21 | estável |
| Compose BOM | 2025.12.01 | estável |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 | |
| ABI | arm64-v8a | apenas |

## 3. Camada Linux: por que PRoot e onde ele roda

Android 10+ proíbe `exec` em arquivos gravados no diretório de dados do app
(W^X). Por isso:

- O binário do PRoot é empacotado como `libproot.so` em
  `app/src/main/jniLibs/arm64-v8a/`. O instalador do Android o extrai para
  `applicationInfo.nativeLibraryDir`, o único lugar onde o app pode `exec`.
- O **loader** do PRoot é empacotado como `libproot-loader.so` e passado via
  `PROOT_LOADER`, pela mesma razão (o loader nunca pode ser extraído para
  `filesDir` e executado).
- `packaging.jniLibs.useLegacyPackaging = true` + `android:extractNativeLibs="true"`
  garantem a extração.
- `PROOT_TMP_DIR` aponta para `filesDir/tmp`; `LD_LIBRARY_PATH` inclui
  `nativeLibraryDir`.

### 3.1 Descoberta crítica: o PRoot precisa ser compilado para Bionic, não musl

Testamos três candidatos executando `node --version` **dentro** do rootfs Alpine
a partir do Android real:

| PRoot | Toolchain | Entra no rootfs | Executa `busybox echo` | Executa `node` |
|---|---|---|---|---|
| build próprio | musl (Alpine) | ✅ | ✅ | ❌ SIGSYS (sinal 31) |
| `proot-static` (Alpine community) | musl | ✅ | ✅ | ❌ exit 255 |
| **termux/proot** | **Android/NDK (Bionic)** | ✅ | ✅ | ✅ `v24.22.3` |

Conclusão: um PRoot musl estático consegue traduzir syscalls simples, mas
quebra no momento em que um processo dinâmico real (Node) arranca — o filtro
seccomp que ele instala não convive com o tratamento de sinais do Bionic.
Por isso compilamos o **fork `termux/proot`** (que carrega os patches Android:
`libandroid-shmem`, `ARG_MAX`, etc.) com o **NDK**, exatamente como o Termux faz.
O script está em `native/proot/build-proot.sh`.

Estado da validação local (Termux, using `clang` do Termux cujo alvo é
`aarch64-unknown-linux-android24`, equivalente ao NDK):

- `termux/proot` compilado localmente → `node -e 'console.log(…)'` = `NODE_OK v24.22.3`
- o mesmo PRoot → `pi --version` = `1.6.0`

O único passo não reproduzido no dispositivo foi o **link estático** —
o Termux não possui libc estática — e é justamente o que o runner do CI
(NDK completo) fará. Ver §8 (riscos).

## 4. Rootfs, provisionamento e persistência

- Baixado na primeira execução (mantém o APK pequeno), com `HttpURLConnection`
  e verificação de SHA-256 antes da extração (`RootfsDownloader`).
- Extração com um leitor de tar próprio (`TarExtractor`) — sem depender do
  `tar` do sistema, que não existe antes do rootfs. Suporta arquivos,
  diretórios, symlinks, hardlinks e entradas PAX/GNU de nome longo.
- `HOME` (`/root`) fica em `files/home`, bind-mountado via `-b …:/root`, então
  sessões e config do Pi (`~/.pi`) **persistem** entre execuções.
- Provisionamento idempotente, reportado à UI por etapas calmas
  (`ProvisionState`): baixar → extrair → configurar → instalar ferramentas →
  preparar o Pi. Um marcador (`files/env/version`) evita reinstalações.

### 4.1 Ajustes de ambiente aplicados no rootfs

- `etc/resolv.conf` → 1.1.1.1 / 8.8.8.8; `etc/hosts`.
- **Fake `/proc`**: Android restringe `/proc`. Escrevemos `stat`, `version`,
  `loadavg`, `uptime`, `cpuinfo` dentro do rootfs para `os.cpus()` etc. não
  quebrarem.
- `--link2symlink` (sem hardlinks no `/data`), `--kill-on-exit`, `-0` (uid fake).
- Fallback documentado: `PROOT_NO_SECCOMP=1` (já não foi necessário com o PRoot
  Bionic, mas fica disponível como válvula de escape).

### 4.2 Phantom Process Killer (Android 12+)

Tudo roda sob um **Foreground Service** `specialUse`
(`FOREGROUND_SERVICE_SPECIAL_USE`), com notificação discreta e ação "Parar", e
um `PARTIAL_WAKE_LOCK`. O app oferece pedido **opcional** de isenção de
otimização de bateria.

## 5. Integração com o Pi (RPC)

Protocolo implementado conforme a documentação oficial da versão instalada
(`rpc.md`, `rpc-commands.md`, `json.md`, `message-types.md`,
`rpc-extension-ui.md`):

- **Framing**: JSONL delimitado por LF. O `JsonlFramer` divide em bytes (0x0A),
  o que é intrinsecamente seguro contra `U+2028`/`U+2029` dentro de strings,
  aceita `\r\n` e trata chunks que cortam uma sequência UTF-8. Há testes
  unitários para todos esses casos.
- **Comandos**: `prompt` (com `streamingBehavior: "followUp"` durante
  streaming), `steer`, `follow_up`, `abort`, `new_session`, `get_state`,
  `get_messages`, `get_available_models`, `get_available_thinking_levels`,
  `set_model`, `set_thinking_level`, `set_session_name`, `get_session_stats`.
- **Eventos**: `agent_start/end/settled`, `turn_*`, `message_*`,
  `tool_execution_*`, `queue_update`, `session_info_changed`,
  `thinking_level_changed`, `compaction_*`, `auto_retry_*`. Eventos
  desconhecidos são preservados como `PiEvent.Unknown` (nunca descartados).
- **Streaming**: reconstruído exatamente como `json.md` prescreve — os deltas
  alimentam a visão ao vivo e são substituídos pelo conteúdo autoritativo de
  `text_end`/`thinking_end`/`toolcall_end` e, por fim, pela mensagem completa de
  `message_end`.
- **Extensão UI**: `select`/`confirm`/`input`/`editor` viram diálogos nativos;
  `notify` vira aviso; `setStatus`/`setWidget`/`setTitle` são ignorados (são
  conceitos de TUI).
- **Throttle de render**: publicações de estado são coalescidas a ~30 fps
  (`AgentSession`), preservando frames durante streaming.

## 6. Segurança de credenciais

- Chaves de API cifradas com AES-256-GCM usando uma chave do **Android
  Keystore** (`SecretStore`). Nada em texto puro; `allowBackup=false` e as
  regras de extração excluem tudo.
- As chaves são injetadas como variáveis de ambiente no processo do Pi
  (`ANTHROPIC_API_KEY`, …), nunca persistidas em `~/.pi` pelo app.
- Nenhum segredo no repositório; o `.gitignore` exclui keystores.
- O token `GITHUBK` é usado apenas localmente via `gh`, com `gh auth setup-git`
  (nunca em URL de remote).

## 7. Desempenho

- Sem Hilt/Dagger, Retrofit, Room, Coil, Glide, WebView.
- `R8` full mode + `shrinkResources`.
- Listas em `LazyColumn` com chaves estáveis; estado imutável; recomposição
  localizada; texto em streaming com throttle.
- PRoot empacotado apenas em `arm64-v8a`.

## 8. Riscos conhecidos e o que depende de teste em aparelho real

| Item | Status |
|---|---|
| Cadeia Alpine → Node → Pi → RPC dentro do PRoot | ✅ validado no dispositivo (Termux) |
| PRoot Bionic executando Node/Pi | ✅ validado no dispositivo |
| Compilação **estática** do PRoot com NDK | ⚠️ feita no CI (o Termux não tem libc estática) |
| Extração do rootfs com `TarExtractor` | ✅ testes unitários (arquivos, dirs, symlinks, nomes longos) |
| Instalação de pacotes `apk` / `npm -g` sob PRoot no aparelho | ✅ validado no dispositivo |
| `apk`/DNS no aparelho | ✅ validado |
| Comportamento em aparelhos com seccomp agressivo | ⚠️ fallback `PROOT_NO_SECCOMP=1` disponível |
| Phantom Process Killer em sessões longas | ⚠️ mitigado por FGS + wake lock; testar em aparelho |
