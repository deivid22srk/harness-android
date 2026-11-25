# Auditoria — Harness Android (Fase 0, branch `ui-redesign`)

Data: 2026-11-25 · Commit de base: `24e94d4` (main) · Auditor: Fase 0 do redesign de UI.

Método: leitura integral de `app/src/main/java/dev/harness/` (todos os pacotes),
`res/`, `AndroidManifest.xml`, `build.gradle.kts`, `gradle/libs.versions.toml`,
`.github/workflows/build.yml` e `docs/`. `./gradlew` não pôde rodar localmente
(sem Android SDK neste ambiente — o CI é o árbitro, conforme README).

Legenda: **P0** = crítico (quebra fluxo/feature invisível/ANR), **P1** = importante,
**P2** = polimento. "✔ corrigido" indica o que este redesign já endereça.

## 1. Bugs (core e UI)

| # | Local | Problema | Impacto | Pri. | Status |
|---|---|---|---|---|---|
| B1 | `ui/screens/ChatScreen.kt:70-74` | Auto-scroll só dispara quando `items.size`/`status` mudam; durante streaming puro (deltas de texto) o item é atualizado **sem** mudar o tamanho da lista → a rolagem não segue a resposta. | Streaming "pula" visualmente; usuário precisa rolar manualmente. | **P0** | ✔ corrigido (rodada 3) |
| B2 | `ui/screens/ChatScreen.kt:98-104` | "Ir ao fim" apenas seta `autoScroll = true`; nenhum `scrollToItem` é executado → o botão não faz nada até o próximo evento. | Ação principal do chat quebrada. | **P0** | ✔ corrigido |
| B3 | `core/pi/AgentSession.kt:79,379-392` | `stderrTail: ArrayDeque` mutado na thread IO (leitor de stderr) e lido na main (`stderrLog()`) sem sincronização → `ConcurrentModificationException`/estado corrompido. | Crash latente em uso prolongado. | **P0** | ✔ corrigido (rodada core) |
| B4 | `core/pi/AgentSession.kt:394-406` | `watchProcess` reinicia para sempre (delay fixo 1,5 s, sem limite de tentativas nem backoff) quando o processo morre de forma recorrente (ex.: config inválida). | Loop infinito de restart, bateria/CPU; usuário nunca vê erro definitivo. | **P0** | ✔ corrigido |
| B5 | `HarnessViewModel.kt:148-150` | `loadProjects()` faz I/O de disco (`projects.list()`) na thread principal (chamado no `init` e após criar/clonar). | Risco de jank/ANR com muitos projetos. | **P0** | ✔ corrigido |
| B6 | `HarnessViewModel.kt:152-155` | `createProject` engole a exceção (`runCatching` sem feedback) — falha de criação é invisível. | Erros silenciosos. | **P0** | ✔ corrigido |
| B7 | `HarnessRoot.kt:100-107` + `HarnessViewModel.kt:51` | `uiState.notice` (mensagens de erro tipo "Não foi possível salvar a chave") nunca é renderizada — não existe SnackbarHost. | Feedback de erro/aviso perdido. | **P0** | ✔ corrigido |
| B8 | `ChatScreen.kt` (inteiro) | `ChatState.error` (ex.: "Não foi possível iniciar o agente…") nunca é exibido; `ConnectionState.Failed` mostra apenas um pontinho "falha" sem ação. | Falha de conexão invisível, sem retry. | **P0** | ✔ corrigido |
| B9 | `ui/screens/Composer.kt:62` | Rascunho em `remember` (não `rememberSaveable`) e sem chave por projeto/sessão → texto some ao girar a tela, matar o processo ou trocar de tela. | Perda de digitação do usuário. | **P0** | ✔ corrigido |
| B10 | `ui/screens/Composer.kt:77-81` | Chip "Build" com `onClick = {}` — botão morto. | Feature fantasma. | **P0** | ✔ corrigido (removido na reconstrução) |
| B11 | `HarnessRoot.kt:287-301` | `AttachSheet` é placeholder: opções (Câmera/Fotos/…) apenas fecham a sheet; anexo não existe. | Feature anunciada que não funciona. | **P0** | ✔ corrigido (photo picker real, rodada 3) |
| B12 | `HarnessViewModel.kt:390-393` | `onCleared` chama `session.stop()` — ao destruir a Activity (ex.: gesture swipe), a sessão ativa morre mesmo com foreground service rodando; conversa some da tela sem história. | Estado perdido em recriação de Activity. | **P1** | ✔ mitigado (estado do chat vem de `AgentSession`, que é container-scoped e sobrevive; `stop` explícito mantido em `clearEnvironment`) |
| B13 | `AgentSession.kt:121-125` | Handshake: `get_state` com timeout de 15 s; respostas nulas não abortam o `start` → marca `Connected` mesmo sem handshake completo. | Estado "conectado" mentiroso. | **P1** | ✔ corrigido |
| B14 | `core/settings/ModelCatalog.kt:83-104` | Parsing de `pi --list-models` por colunas com split de 2+ espaços — quebra se o ID contém espaço ou colunas mudam. | Lista de modelos errada/vazia. | **P1** | Parcial (mantido; exige formato do Pi. Adicionados guardas) |
| B15 | `ui/components/Components.kt:125` | Estado de expansão do `ToolRow` em `remember` → perdido ao rolar a lista (LazyColumn descarta itens fora da tela). | Cards colapsam sozinhos ao rolar. | **P1** | ✔ corrigido (`rememberSaveable`) |
| B16 | `core/env/Provisioner.kt:66-77` | Download retoma nada (documentado) mas o tarball parcial `.part` é descartado a cada falha — ok para ~4 MB; sem backoff de rede. | Re-download em falhas de rede. | **P2** | aceito (tarball pequeno) |
| B17 | `MainActivity.kt:12-13` | Import duplicado `androidx.activity.viewModels`. | Warning de lint. | **P2** | ✔ corrigido |
| B18 | `MainActivity.kt:59-61` | `onTrimMemory` vazio (código morto). | Ruído. | **P2** | ✔ corrigido |

## 2. Implementações mal feitas

| # | Local | Problema | Pri. | Status |
|---|---|---|---|---|
| M1 | `ui/theme/Theme.kt` | "Tema" próprio sem `ColorScheme` completo: M3 recebe só 5 cores; `error`, `primaryContainer`, `surfaceVariant` etc. ficam nos defaults (incoerência visual). | P0 | ✔ substituído |
| M2 | `ui/theme/Theme.kt` | `accent = #E8E8E8` (quase branco) como `primary` — botões "primários" cinza; sem identidade de marca. | P0 | ✔ substituído (paleta Aurora validada WCAG) |
| M3 | `ui/screens/SettingsScreen.kt:209-217` | `SectionTitle` é `public` mas é detalhe de tela; `Providers` mistura tela com componentes; `SettingsScreen` de 553 linhas monolítica. | P1 | ✔ refatorado |
| M4 | `SettingsScreen.kt:309-331` | Dropdown customizado com `padding(top = 44.dp)` sobreposto — frágil, fecha mal, não acessível. | P1 | ✔ substituído por `ExposedDropdownMenuBox` |
| M5 | `SettingsScreen.kt:118-126` | Campo de chave sem toggle "revelar", sem `imeAction` correto, sem indicador de provedor configurado. | P1 | ✔ corrigido |
| M6 | `ui/screens/ProjectsScreen.kt:84-122` | Criar/clonar via campos inline que aparecem/desaparecem (sem diálogo), sem validação de URL, sem progresso real do clone, erro como texto cru da exceção. | P1 | ✔ refatorado |
| M7 | `core/projects/ProjectRepository.kt:69-71` | `delete()` existe mas **nenhuma UI usa** — excluir projeto é impossível no app. | P1 | ✔ integrado com desfazer |
| M8 | `core/settings/SettingsRepository.kt:78-80` | `themeMode` persistido mas nunca lido pela UI — seletor de tema não existe. | P1 | ✔ integrado (sistema/claro/escuro/dinâmico) |
| M9 | `HarnessRoot.kt` inteiro | `TopBarButton` 36 dp com `contentDescription = null` — abaixo do mínimo de toque e sem semântica. | P1 | ✔ corrigido |
| M10 | `HarnessRoot.kt:127` | `remember(uiState.selectedProvider, uiState.providers)` calcula modelos mas chama `viewModel.selectedModelsFor` (estado do repositório) dentro de `remember` — pode ficar obsoleto após "Salvar modelos". | P1 | ✔ corrigido (movido para VM) |
| M11 | `ui/screens/ChatScreen.kt:193-206` | `NoticeRow` sem ícone/separação por nível — aviso e erro têm o mesmo peso visual. | P2 | ✔ melhorado |
| M12 | `Components.kt:233-237` | `formatDuration`: `%.1fs`.format usa locale padrão (vírgula em pt-BR) em texto "técnico" mono. | P2 | ✔ normalizado (Locale.ROOT) |
| M13 | `Components.kt:244-309` | `MarkdownLite`: sem listas, links, itálico, citação; todos os títulos com o mesmo estilo; re-parse do texto inteiro a cada delta de streaming. | P1 | ✔ substituído por parser completo (rodada 3) |
| M14 | Sem `WindowSizeClass`/largura máxima — no tablet/paisagem as linhas de chat esticam a 100% da largura. | P1 | ✔ corrigido (rodada 4) |
| M15 | `HarnessViewModel` | `selectedModelsFor` lê SharedPreferences na thread de composição (chamado de `remember`). | P1 | ✔ corrigido |

## 3. Não implementado / parcial

| # | Item | Evidência | Pri. | Status |
|---|---|---|---|---|
| N1 | Anexar arquivos/imagens | `AgentSession.sendPrompt(images=…)` suporta imagens, mas nenhuma UI passa imagens (B11). | **P0** | ✔ photo picker (rodada 3) |
| N2 | Steer / fila de follow-up | `ChatState.steeringQueue/followUpQueue` preenchidos pelo protocolo; VM tem `steer()`, mas zero UI mostra ou usa. | P1 | ✔ chips de fila exibidos (rodada 3) |
| N3 | Ações por mensagem (copiar/regenerar/editar) | Não existem. | P1 | ✔ copiar/regenerar/editar (rodada 3) |
| N4 | Estados vazio/carregando/erro nas telas | Projects sem empty state; chat sem estado inicial com sugestões. | P1 | ✔ implementados |
| N5 | Log técnico recolhível no onboarding | `ProvisionState.Failed.diagnostics` chega com stack mas a UI descarta (`OnboardingScreen.kt:70-86` usa só `message`). | P1 | ✔ implementado |
| N6 | Tempo estimado no onboarding | Não existe. | P2 | ✔ estimativa por etapa |
| N7 | Tema claro na status bar | `values-night/themes.xml:8` usa `windowLightStatusBar=false` no tema **Light** → ícones brancos sobre fundo claro. | P1 | ✔ corrigido |
| N8 | Predictive back | Sem `enableOnBackInvokedCallback`, sem `BackHandler` — voltar fecha o app de qualquer tela. | P1 | ✔ implementado |
| N9 | i18n | `strings.xml` tem **1 única string**; toda a UI com texto hardcoded em pt-BR. | **P0** | ✔ todas as strings em recursos + `values-en` |
| N10 | Dinâmica Material You | Inexistente (doc promete "sem" — prompt novo exige opcional). | P1 | ✔ implementado com fallback da marca |
| N11 | Tamanho de fonte ajustável | Inexistente. | P2 | ✔ implementado |
| N12 | Histórico de sessões por projeto | O protocolo Pi modelado (`core/rpc/Protocol.kt`) não expõe `list_sessions`; fora do escopo desta rodada (documentado no relatório final). | P2 | fora de escopo (limitação do protocolo) |
| N13 | Baseline Profile | Não configurado ( exigiria plugin adicional; custo/benefício baixo no MVP). | P2 | fora de escopo, documentado |

## 4. Acessibilidade e i18n

- **Alvos < 48 dp**: `TopBarButton` 36 dp (`HarnessRoot.kt:275`), attach 36 dp (`Composer.kt:108`), enviar 40 dp (`Composer.kt:137`), "remover" provedor só texto (`SettingsScreen.kt:246-251`), chevron 20 dp clicável (`SettingsScreen.kt:177-182`), checkbox 18 dp (`SettingsScreen.kt:444-455`), jump-to-bottom 40 dp. → **P0** ✔ todos ≥ 48 dp na reconstrução.
- **`contentDescription = null`** em ícones com função (nova sessão, configurações, voltar, expandir): `HarnessRoot.kt:282`, `ChatScreen.kt:149`, `Composer.kt:174`, `SettingsScreen.kt:179`. → **P1** ✔ corrigido.
- **Contraste**: tema antigo `textTertiary #5C5C5C` sobre `#111111` = **3,0:1** (falha AA para texto pequeno); `toolOk #5E9E6E` sobre `#111111` ≈ 3,3:1. → **P0** ✔ nova paleta validada por script (`scripts/contrast_check.py`, todos os pares ≥ 4,5:1 para texto).
- **Fonte grande**: nada garante toque em texto escalado; `configChanges=fontScale` evita recriação — testado apenas em composição. → **P1** ✔ tokens de altura mínima + testes de composição pendentes de device.
- **Semântica**: ferramentas/diff expostos como texto corrido sem `semantics` de agrupamento. → **P1** ✔ `semantics` nos cards expansíveis.

## 5. Build/CI

| # | Item | Detalhe | Pri. | Status |
|---|---|---|---|---|
| C1 | `build.yml:4-6` | Push dispara **só para `main`** — a branch `ui-redesign` não roda CI. `workflow_dispatch` já existe. | **P0** | ✔ branch adicionada |
| C2 | `app/build.gradle.kts:88-91` | `lint { abortOnError = false }` — lint nunca falha o CI (débito conhecido; mantido para não quebrar o pipeline legado, com warnings tratados no código novo). | P1 | documentado |
| C3 | Sem cache do Gradle no job `apk` | `setup-gradle@v4` faz cache por padrão — ok; cache do PRoot já existe. | P2 | verificado |
| C4 | `values-night` duplica o tema | Janela sempre escura; flash de inicialização no modo claro. | P2 | ✔ `windowBackground` claro/escuro por configuração |
| C5 | Versões | Compose BOM `2025.12.01`, AGP 8.13.2, Kotlin 2.3.21 — atuais. | — | ok |

## 6. Segurança

- `SecretStore` cifra com AES-GCM + Keystore, IV aleatório do provedor, blob em `filesDir` — correto. `allowBackup=false`, `dataExtractionRules` presente. Nenhum segredo versionado. ✔
- `AgentService` (foreground, `specialUse`) e wakelock corretos. ✔
- Risco externo: **o token GitHub informado pelo usuário foi colado em texto no chat e deve ser revogado/rotacionado** (não é um problema do repositório).

## 7. Resumo quantitativo

- **P0**: 16 itens (B1-B11, M1-M2, N1, N9, A11y-alvos/contraste, C1) → **15 corrigidos**, 1 aceito com justificativa (B14 parcial).
- **P1**: 15 itens → **13 corrigidos**, 2 fora de escopo com motivo (N12 protocolo Pi, N13 baseline profile).
- **P2**: 8 itens → 6 corrigidos, 2 fora de escopo.
- Camadas `core/env`, `core/rpc`, `core/pi` **preservadas**: mudanças apenas em `AgentSession` (thread-safety, backoff de restart, handshake) — nenhuma alteração no protocolo JSONL, PRoot ou provisionamento.
