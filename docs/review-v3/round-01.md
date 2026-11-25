# Rodada 01 — Frente A (Conexão independente da chave) + CI de evidências

> Base auditada: `92d3a4d` + `3fe88af`. CI de referência: **run #70** (verde, 4/4 jobs).
> Escopo: causas C1–C5 do `docs/AUDIT_V3.md` + CI1/CI2.

## 1. Evidência por causa-raiz

### C1 — Chave aplicada em execução, preservando a sessão ✅
- `AgentSession.applyCredentials()` (`app/src/main/java/dev/harness/core/pi/AgentSession.kt:199-209`):
  estado → Restarting → `stopInternal()` → `start(newConfig)` → `switchToSession(resumeGuest)`
  com `resumeGuest = internal.sessionFile ?: lastKnownSessionFile`.
- Teste `applyCredentials preserves the active session`
  (`app/src/test/java/dev/harness/core/pi/AgentSessionTest.kt:300-324`): passa no CI (run #70,
  85 testes, 0 falhas) e prova que `switch_session` é reemitido ao processo NOVO e o
  `sessionFile` não muda.

### C1b — Bug real encontrado pelos testes e corrigido nesta rodada ✅
- **Corrida watchdog × applyCredentials**: o `waitFor()` do processo antigo acordava no
  reinício controlado e o watchdog reiniciava por conta própria (duplo `start()`, sessão
  perdida). Correção: guarda por instância `if (pi !== process) return@launch`
  (`AgentSession.kt:556-565`). Sem isso, salvar chave poderia derrubar a sessão ativa —
  exatamente o sintoma da Frente A.

### C2 — Estado "Sem provedor" na máquina de conexão ✅
- `ConnectionState.NoProvider` decidido no handshake: `AgentSession.kt:187`
  (`apiKeys.isEmpty() → NoProvider`); enum em `AgentSession.kt:41`.
- Banner não bloqueante com CTA "Configurar provedor": `NoProviderBanner`
  (`app/src/main/java/dev/harness/ui/screens/ChatScreen.kt:399-425`), inserido em
  `ChatScreen.kt:152-154`. Evidência visual: `chat_sem-provedor_{dark,light}_{1.0,1.3}.png`.
- Cores por estado incluindo NoProvider: `ChatScreen.kt:386-392` (corrigido nesta rodada:
  referência `warning`→`toolWarning` quebrou o CI #61).

### C3 — Erros de provedor traduzidos ✅
- `ProviderErrors.translate()` (`app/src/main/java/dev/harness/core/rpc/ProviderErrors.kt`):
  401/unauthorized/invalid_api_key/**x-api-key**/authentication_error → REVIEW_KEY; 403;
  quota/billing/credit; 429/rate limit/overloaded; timeout; rede. Testes
  `ProviderErrorsTest` passando (incl. "invalid x-api-key" do Anthropic, que expôs a falta
  do padrão `x-api-key` — corrigido nesta rodada).

### C4 — Diagnóstico de conexão exposto ✅
- Sheet de conexão: `app/src/main/java/dev/harness/ui/components/ConnectionSheet.kt`
  (estado, versão do Pi, uptime, último erro, teste de provedor, reiniciar).
- stderr com tail de 2000 linhas: `AgentSession.kt:605` (`STDERR_TAIL_LINES = 2000`) +
  teste `stderr tail is bounded at 2000 lines` passando.
- Evidência visual: `conexao-diagnostico_{dark,light}_{1.0,1.3}.png`.

### C5 — Smoke prova RPC sem nenhuma chave ✅
- `scripts/smoke-test.sh:24-79`: `get_state` + `get_available_models` sem chave.
- Log do run #70 (job Smoke): `get_state: success=true`,
  `get_available_models: success=true models=0`, **`RPC_OK_SEM_CHAVE`** — trava objetiva
  do PROMPT (conexão > 7,0 exige prova sem chave). `SESSIONS_OK` e `PACKAGES_OK`
  continuam verdes (nada da etapa anterior foi regredido).

### CI1 — Job de screenshots ✅
- `.github/workflows/build.yml:104-137` (job `screenshots`, Roborazzi JVM, sem emulador).
- **24 PNGs** no padrão `<tela>_<estado>_<tema>_<fonte>.png` (6 telas × escuro/claro ×
  fonte 1,0/1,3), artifact `ui-screenshots` do run #70.
- Correções necessárias nesta rodada (registradas por honestidade): `PiProcessHandle.isAlive`
  val→fun (colisão JVM com `java.lang.Process`); `@Config(sdk=[34])` (SDK 36 exige Java 21,
  CI usa 17); `nativeLibraryDir` null sob Robolectric (`HarnessPaths.kt:34-41`);
  `roborazzi.test.record=true` — sem isso `captureRoboImage` grava **nada** silenciosamente
  (taskType None); stack traces completos no log (`build.gradle.kts:104-110`).

### CI2 — Gatilho da branch ✅
- `build.yml:5` inclui `fix/connection-chat-packages-visual` — runs #61–#70 dispararam por push.

## 2. Avaliação do Crítico (rubrica do PROMPT)

| Critério | Peso | Nota | Justificativa |
|---|---|---|---|
| Conexão | 18 | **9,4** | C1–C5 completos com prova objetiva (smoke sem chave + 85 testes + 2 estados novos com screenshot). Perde 0,6: estado "Gerando" não é distinto no indicador de conexão (a geração sinaliza via composer/stop); indicador do chat é clicável mas sem latência. |
| Chat | 18 | 7,8 | Inalterado nesta rodada (M1–M5 pendentes, Rodada 2). |
| Pacotes | 14 | 8,2 | Inalterado (P1–P3 pendentes, Rodada 3). `PACKAGES_OK` mantido. |
| Visual | 14 | 8,8 | Tokens v3 + design-refs da Fase 0; acabamento por tela pendente (L2, Rodada 4). |
| UX | 8 | 8,5 | Banner Sem provedor melhora convite; onboarding igual (L3 pendente). |
| Performance | 8 | 8,0 | Streaming ainda re-parseia (M2 pendente). |
| Arquitetura | 8 | 9,3 | Camadas `core/*` preservadas; correções com testes; `core/` mexido tem cobertura. |
| Acessibilidade | 4 | 9,0 | Mantém; strings novas em ambos os locales (backfill 170 chaves). |
| Build/CI | 8 | **9,6** | Verde 4/4; artifact de 24 PNGs; smoke triplo (`RPC_OK_SEM_CHAVE`, `SESSIONS_OK`, `PACKAGES_OK`); reprovações #61–#69 documentadas e resolvidas com causa-raiz. |
| **Global ponderado** | | **8,6** | Sem P0/P1 abertos. REPROVADO (esperado na Rodada 1) — chat/pacotes/visual puxam a média. |

**Veredito da Frente A: APROVADO** (objetivo da rodada cumprido com prova objetiva).
**Veredito global: REPROVADO (8,6)** — o loop continua; metas por rodada já mapeadas
(M1–M5 → 2; P1–P3 → 3; L1–L3 → 4).

## 3. Observações para as próximas rodadas (P2)
1. Screenshots saem com locale EN (Robolectric default): fixtures pt-BR + strings EN no
   mesmo frame. Na Rodada 4 fixar `@Config(qualifiers = "+pt-BR…")` para evidência fiel.
2. Indicador de conexão no chat: acrescentar estado "gerando" (§4.2) — barato e visível.
3. `docs/review-v3/` passa a ser o diretório de relatórios desta etapa (V3).
