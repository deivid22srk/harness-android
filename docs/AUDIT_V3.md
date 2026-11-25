# AUDIT V3 — Diagnóstico das causas-raiz (Fase 0)

> Investigação antes de corrigir (PROMPT §3.4). Cada achado traz `arquivo:linha`,
> impacto e a correção planejada. Base: commit `724c5de`
> (`feat/settings-sessions-packages`, Rodada 06 aprovada da etapa anterior).

## 1. Conexão UI ↔ Pi "depende da chave" (Frente A)

### C1 — Chave salva não é aplicada ao Pi em execução (causa-raiz principal)
- **Onde**: `app/src/main/java/dev/harness/HarnessViewModel.kt:910-921`
  (`saveApiKey` apenas grava no `SecretStore`); `app/src/main/java/dev/harness/HarnessApp.kt:54-64`
  (`launchConfig()` injeta `settings.apiKeyEnv()` **só na criação do processo**);
  `app/src/main/java/dev/harness/core/env/ProotCommand.kt:20-30,60-71` (env
  construído por argv no spawn).
- **Impacto**: o usuário abre o app sem chave → Pi sobe e conecta (RPC funciona —
  provado pelo smoke, `scripts/smoke-test.sh:24-69`), mas **enviar prompt falha**
  com erro de provedor. Ao salvar a chave **nada reinicia o Pi**; só quando o
  projeto é fechado/aberto (`openProject` → `startPiAndResume`) a chave vale.
  Percepção exata do usuário: "só funciona depois que coloco a API key".
- **Correção (Rodada 1)**: ao salvar/trocar chave ou modelo, **reinício
  controlado do Pi preservando a sessão** (mesmo mecanismo de
  `restartSessionAfterPackageChange`, HarnessViewModel.kt:559-574) + estado
  "Sem provedor" reavaliado; animação de reconexão discreta.

### C2 — Não existe estado "Sem provedor" na máquina de conexão
- **Onde**: `app/src/main/java/dev/harness/core/pi/AgentSession.kt:41` (enum
  `ConnectionState { Stopped, Starting, Connected, Restarting, Failed }`).
- **Impacto**: "Pi conectado" e "provedor utilizável" são a mesma coisa para a
  UI. Sem provedor, o chat não comunica o problema nem oferece CTA
  "Configurar provedor"; erros só aparecem como notice cru ao enviar
  (AgentSession.kt:186-189).
- **Correção (Rodada 1)**: máquina de estados única (§4.2 do PROMPT) com
  `NoProvider` derivada de: Pi conectado + nenhum modelo utilizável
  (`get_available_models` vazio **ou** nenhuma chave configurada para o modelo
  selecionado). Banner com CTA direto na tela de chat.

### C3 — Erros de provedor brutos, sem tradução nem ação
- **Onde**: `AgentSession.kt:186-189` e `AgentSession.kt:388-391` (repassa
  `response.error` verbatim); `ChatScreen` renderiza como notice.
- **Impacto**: 401/403/429/timeout/rede aparecem como string técnica; sem
  "Revisar chave"/"Tentar de novo".
- **Correção (Rodada 1)**: tradutor de erros (mapeamento por padrão — `401`,
  `unauthorized`, `credit`, `429`, `rate limit`, `timeout`, `ECONNREFUSED`,
  `ENOTFOUND`…) → mensagem humana + ação (Abrir configurações / Repetir), em
  chat (bloco de erro rico) e no sheet de diagnóstico.

### C4 — Diagnóstico de conexão pouco exposto
- **Onde**: `AgentSession.kt:531` (`STDERR_TAIL_LINES = 200`, PROMPT pede ~2000);
  `ChatScreen.kt:273` (badge de conexão **não clicável**); strings de
  diagnóstico existem (`settings_diag_test_run` "Testar ambiente") mas não há
  **"Testar conexão com o provedor"**.
- **Impacto**: usuário não vê stderr, versão do Pi, último erro; não consegue
  copiar diagnóstico do chat.
- **Correção (Rodada 1)**: sheet de conexão tocando no indicador (versão do Pi
  via `pi --version` capturado no provisionamento, tempo ativo, estado, último
  erro, stderr tail 2000 linhas, botões Reiniciar Pi / Copiar diagnóstico /
  Testar provedor); teste de provedor = chamada mínima ao endpoint quando o Pi
  estiver sem uso (via erro do próprio `get_available_models`/prompt de teste,
  sem gastar tokens quando possível).

### C5 — Handshake não aguarda `get_available_models` (menor)
- **Onde**: `AgentSession.kt:136-138` (os três comandos pós-`get_state` são
  fire-and-forget — aceitável, mas o smoke não loga `get_available_models`).
- **Impacto**: o smoke não prova explicitamente que modelos respondem sem chave.
- **Correção (Rodada 1)**: estender `scripts/smoke-test.sh` para chamar e
  validar `get_available_models` **sem nenhuma chave** no ambiente e logar a
  resposta (prova objetiva exigida pelo Crítico).

### O que já está correto (não mexer)
- Correlação por `id` + timeout por comando + linhas parciais:
  `core/rpc/RpcClient.kt:114-124` e `core/rpc/JsonlFramer.kt`.
- stderr drenado em thread própria com tail: `AgentSession.kt:466-479`.
- Dono do processo em escopo de app: `HarnessApp.kt:21,28` (container) +
  Foreground Service `specialUse` com wakelock: `service/AgentService.kt:36-60`.
- Detecção de morte + backoff exponencial (2s/4s/8s, máx. 3):
  `AgentSession.kt:481-514`.
- Restart preserva sessão ativa via `switchToSession`:
  `HarnessViewModel.kt:559-574`.

## 2. Chat "parece Markdown cru" (Frente B)

### M1 — Parser Markdown incompleto (tabelas, listas aninhadas, tarefas, hr)
- **Onde**: `app/src/main/java/dev/harness/ui/components/HMarkdown.kt:45-136`
  (apenas heading/par/quote/code/list de **1 nível**); sem tabelas, sem task
  lists, sem `---`, sem imagens.
- **Impacto**: respostas com tabelas/aninhamento aparecem com `|---|` cru ou
  achatadas.
- **Correção (Rodada 2)**: parser de blocos expandido (tabelas com rolagem,
  aninhamento recursivo, task lists, hr) mantendo **dependência zero** e
  testável na JVM (fixtures em `app/src/test/resources/markdown/`).

### M2 — Recomposição pesada durante streaming
- **Onde**: `HMarkdown.kt:273` (`remember(text) { parseMarkdown(text) }` —
  re-parse da mensagem inteira no main thread a cada tick de 33 ms do
  publisher `AgentSession.kt:356-364`); `HCodeBlock.kt:153`
  (`remember(code, codeColors) { highlightCode(code, codeColors) }` —
  re-realce inteiro por token).
- **Impacto**: respostas longas com código "piscam" e consomem CPU; contra as
  exigências de streaming (§5.3: blocos estáveis com key, parse fora da main
  thread, debounce de realce).
- **Correção (Rodada 2)**: divisão em **blocos estáveis com `key`** (só o
  último bloco recompõe), parse em dispatcher padrão com throttle, realce
  debounce ~150 ms, cerca aberta/tabela incompleta tratadas sem piscar.

### M3 — Sem rodapé por resposta (modelo/tokens/hora) e sem cabeçalho
- **Onde**: `ChatState.kt` guarda `lastUsage` (`ChatReducer.kt:111-112`) mas
  nada renderiza; `ChatScreen.kt:539-593` (AssistantMessage) sem metadados.
- **Correção (Rodada 2)**: rodapé discreto por resposta (modelo, tokens/custo
  quando disponíveis, hora) e cabeçalho discreto da resposta.

### M4 — Sem divisores temporais e sem ações por menu
- **Onde**: `ChatScreen.kt:456-470` (sem divisores tipo "Novo"/"Hoje 16:55" das
  referências); ações são botões de texto (MessageActions) em vez de menu por
  mensagem.
- **Correção (Rodada 2)**: divisores por data/hora entre itens; menu de
  mensagem (copiar Markdown, copiar texto, compartilhar, regenerar).

### M5 — Blocos de código sem diff fence/quebra de linha/numeração
- **Onde**: `HCodeBlock.kt:143-197` (tem linguagem + copy + rolagem; falta
  toggle de quebra, números de linha opcionais e coloração para cercas
  `diff`).
- **Correção (Rodada 2)**: três melhorias acima, coerentes com a paleta.

### Nota de escopo
O "subagentes/shells" das referências é funcionalidade do app de referência;
o protocolo RPC do Pi instalado (1.6.0) não expõe eventos de subagentes —
**não será inventado** (documentado em DESIGN.md como divergência consciente).

## 3. Pacotes "não mostram a lista" (Frente C)

### P1 — Seção Instalados desaparece quando ambas as listas estão vazias
- **Onde**: `app/src/main/java/dev/harness/ui/screens/PackagesScreen.kt:389`
  (`if (global.isNotEmpty() || projectPackages.isNotEmpty())` — sem else).
- **Impacto**: quem nunca instalou nada vê **nenhuma seção de lista** — a tela
  salta direto para "Instalar"/"Explorar"; leitura do usuário: "a lista não
  aparece". Ainda: estado vazio sem instrução não cumpre §6.3 (área 1 deve
  sempre existir).
- **Correção (Rodada 3)**: seção Instalados **sempre** presente (Global e Do
  projeto separados), com estado vazio explicativo; reorganizar em **3 áreas**
  (Instalados / Explorar / Instalar por origem) com abas.

### P2 — Explorador sem debounce, filtros por tipo, cache, skeleton ou detalhe
- **Onde**: `PackagesViewModel.kt:286-330` (busca só por botão, sem debounce de
  ~400 ms; sem filtros Extensão/Skill/Prompt/Tema; sem cache em memória/disco
  10–15 min; sem estado offline com cache; sem tela de **detalhe do pacote**
  com manifesto `pi`, links, licença e botão Instalar cruzado com instalados).
- **Correção (Rodada 3)**: debounce + filtros por keywords (`pi-extension`,
  `pi-skill`, `pi-prompt`, `pi-theme` — heurística documentada no guia),
  paginação infinita (já existe `from`), cache, skeleton, detalhe.

### P3 — Parser do catálogo sem fixture e validação cruzada
- **Onde**: `PiPackageManager.kt:266-313` (parser já tolerante, mas sem fixture
  JSON real nos testes; `PackageDeclaredCodec` tem testes mas o explorer não).
- **Correção (Rodada 3)**: fixture da resposta real do npm
  (`keywords:pi-package`) + testes de cruzamento instalado × catálogo.

### P4 — `pi list` não é usado como complemento (ok por design, mas smoke não
loga `pi list` detalhado para o escopo do projeto)
- **Correção (Rodada 3)**: smoke já valida install/list/remove
  (`scripts/smoke-test.sh:104-127`); manter e estender log da saída real para
  fixtures.

## 4. Layout geral ≠ referências (Frente D)

### L1 — Paleta "Aurora" (azul-violeta sobre fundo azulado) diverge das refs
- **Onde**: `app/src/main/java/dev/harness/ui/theme/Color.kt:13-44`
  (`DarkBackground #0B0E14`, primária `#7C5CFF`, bolha do usuário `#232945`).
- **Referência**: fundo neutro `#161616`, bolha `#2E2E2E`, acento azul discreto
  (ver docs/REFERENCIAS_VISUAIS.md — hex medidos).
- **Correção (Fase 0, tokens + Frente D)**: trocar tokens para neutro/azul,
  rodar `scripts/contrast_check.py` e manter AA.

### L2 — Top bar do chat, hub de configurações e telas sem o acabamento das refs
- **Onde**: `ChatScreen.kt:224-335` (top bar própria), `SettingsScreen.kt` /
  `settings/Sections.kt` (hub sem card de conexão/latência), `SessionsScreen.kt`
  (sem badge de letra/spinner do item ativo), `ProjectsScreen.kt`.
- **Correção (Rodada 4)**: reconstruir acabamento conforme
  REFERENCIAS_VISUAIS.md (hierarquia, espaçamentos, raios, bordas).

### L3 — Onboarding ainda pode parecer bloqueio
- **Onde**: `MainActivity`/`OnboardingScreen` — iniciar o app exige
  provisionamento (correto), mas **a chave não deve bloquear** uso nenhum; já
  é assim tecnicamente (smoke prova), falta **comunicar**: banner "Sem
  provedor" com convite (não bloqueio) na Frente 1/A.

## 5. CI

### CI1 — Sem job de screenshots
- **Onde**: `.github/workflows/build.yml:21-204` (proot → smoke → apk →
  release).
- **Correção (Rodada 1)**: job `screenshots` com **Roborazzi** (JVM, sem
  emulador) publicando artifact `ui-screenshots` com os cenários do §8
  (escuro/claro × fonte 1,0/1,3).

### CI2 — Branch de trabalho fora do gatilho push
- **Onde**: `build.yml:4-6` (`branches: [main, ui-redesign,
  feat/settings-sessions-packages]`).
- **Correção (Rodada 1)**: adicionar `fix/connection-chat-packages-visual`;
  `workflow_dispatch` já existe.

## Resumo de causas → rodadas
| Causa | Rodada |
|---|---|
| C1 chave não aplicada em execução; C2 Sem provedor; C3 erros traduzidos; C4 sheet diagnóstico; C5 smoke sem chave explícita | 1 |
| CI1 job screenshots; CI2 gatilho push | 1 |
| M1–M5 renderização/streaming do chat + fixtures | 2 |
| P1–P3 pacotes (3 áreas, debounce, cache, detalhe, fixtures) | 3 |
| L1–L3 layout completo nas referências + onboarding convite | 4 |
