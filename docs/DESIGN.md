# Design — Harness (sistema "Aurora")

Redesign completo de UI/UX (branch `ui-redesign`). Referências: apps de chat
com IA de alto nível (largura de leitura limitada, ações por mensagem, cards de
tool call), Linear (neutralidade tipográfica + um acento de marca) e Material 3
Guidelines (alvos de 48 dp, estados, edge-to-edge, predictive back).

Implementação: `ui/theme/` (Color, Type, Tokens, Theme) e `ui/components/` (H*).

## 1. Cor

Dark-first. A paleta foi validada por script (`scripts/contrast_check.py`):
todos os pares texto/fundo ≥ 4.5:1 (WCAG AA). `primary` de marca (#7C5CFF) é
usado em contêineres/foco; o botão preenchido usa `accentButton` (#6841E0,
6.14:1 com rótulo branco) e texto pequeno em tom de marca usa `accentText`
(#A18BFF, 7.04:1) — os três casos ficam acima de AA. Na rodada 02 do Crítico, os pares de **texto em difs/código** e do **snackbar de aviso** no tema claro também foram auditados: novos slots `toolOkText` (#067647), `toolWarningText` (#93370D) e reuso de `toolErrorText` (#B42318) passam em AA sobre `codeBackground` e contêineres (5,1–6,5:1), verificados por `scripts/contrast_check.py` (37 pares PASS AA, 0 FAIL).

| Token | Escuro | Claro | Uso |
|---|---|---|---|
| `background` | `#0B0E14` | `#F7F8FB` | fundo das telas |
| `surface` | `#12161F` | `#FFFFFF` | barras, cartões planos |
| `surfaceElevated` | `#1A1F2B` | `#EEF0F6` | cards, chips, campos |
| `surfaceHigh` | `#222838` | `#E4E7F0` | skeletons, tracks |
| `border` / `borderStrong` | `#2A3040` / `#3A4156` | `#D5D9E4` / `#B8BECF` | hairlines (decorativas; estados não dependem só de borda) |
| `textPrimary` | `#E8EBF3` | `#0F1320` | corpo (16,2:1 / 17,4:1) |
| `textSecondary` | `#9AA3B8` | `#5A6378` | apoio (7,6:1 / 5,7:1) |
| `textTertiary` | `#818CA3` | `#646D82` | metadados discretos (5,7:1 / 4,9:1 — AA após revisão do Crítico) |
| `accentButton` | `#6841E0` | `#5B3FE0` | botão primário |
| `accent` | `#7C5CFF` | `#5B3FE0` | foco, barras de progresso |
| `accentText` | `#A18BFF` | `#5B3FE0` | acento como texto |
| `accentContainer` / `onAccentContainer` | `#3B2E80` / `#EDE9FF` | `#E4DCFF` / `#2A2050` | chips selecionados, tonal |
| `secondary` | `#22D3EE` | `#0891B2` | detalhes de marca (ícones/grande) |
| `toolOk` / `toolRunning` / `toolError` | `#34D399` / `#FBBF24` / `#F87171` | `#059669` / `#B45309` / `#DC2626` | estados de ferramentas/conexão |
| `toolErrorText` | `#FCA5A5` | `#B42318` | erro como texto pequeno |
| `codeBackground` | `#0E1219` | `#F1F3F9` | blocos de código |
| `userBubble` | `#232945` | `#E4DCFF` | bolha do usuário |

**Material You opcional**: `dynamicDark/LightColorScheme` (API 31+) remapeado
para os mesmos slots semânticos; abaixo de 31 ou desativado, cai na paleta da
marca. Preferência em Configurações → Aparência.

**Tema**: sistema / claro / escuro (Configurações → Aparência), resolvido no
`MainActivity` e aplicado em `HarnessThemeProvider`. Tamanho de fonte: P/M/G/GG
(multiplicador 0,9–1,3 aplicado sobre o `fontScale` do sistema via
`LocalDensity`).

## 2. Tipografia

- UI: sans do sistema (Roboto) — nenhuma dependência de font provider em
  runtime; a legibilidade do chat vem da escala, não de trocar a família.
- Código/diff/metadados: **JetBrains Mono** embarcada (`res/font`, OFL, ver
  `docs/OFL-JetBrainsMono.txt`): `MonoStyle` 12.5/18 e `CodeStyle` 13/20.
- Escala: `displaySmall` 34 → `headlineSmall` 22 → `titleMedium` 16 →
  `bodyLarge` 15/22 → `bodySmall` 12.5 → `labelLarge` 14. Entrelinha central
  (`LineHeightStyle`) para texto corrido.

## 3. Tokens

- Espaçamento `Space`: xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32 (múltiplos de 4).
- Raios `Radii`: sm 8 · md 12 · lg 16 · xl 24 · pill 100 + shapes prontos.
- Elevação `Elev`: flat/low/mid/high/sheet (sombras discretas; hierarquia por superfície+borda).
- Movimento: durações 120/200/300 ms com easing padrão; respeita "remover
  animações" do sistema (escala de animador = 0 já zera as animações do Compose).
- **Largura máxima de leitura**: mensagens ≤ 640 dp, conteúdo ≤ 720 dp
  centralizados — adaptativo sem biblioteca extra (telefone → tablet/paisagem).
- Alvo de toque mínimo: 48 dp (`MinTouchTarget`, centralizado em
  `touchTargetClickable`, com ripple + `Role`).

## 4. Componentes (`ui/components/`)

`HButton` (primary/secondary/ghost/danger, loading) · `HIconButton` (48 dp,
contentDescription obrigatório) · `HTextField`/`HPasswordField` (com toggle
mostrar/ocultar) · `HCard` · `HChip` · `HBadge` · `HDivider` · `HSkeleton`
(shimmer) · `HEmptyState` · `HErrorState` (retry + copiar diagnóstico + log
recolhível) · `HDialog` · `HBottomSheet` · `HNoticeSnackbarHost` (níveis) ·
`HProgressSteps`/`HProgressBar` (onboarding) · `CollapsibleSection` ·
`HCodeBlock` (realce de sintaxe leve, copiar, seleção, rolagem horizontal,
nome da linguagem) · `HOutputBlock` · `HMarkdown` (títulos, listas, citações,
negrito/itálico/riscado, `código`, links clicáveis, blocos cercados; fence não
fechado renderiza como código durante o streaming) · `ToolCallCard` (status,
duração, `+N/−N`, diff legível, expansão sobrevive à reciclagem da lista via
`rememberSaveable`).

Cada componente consome apenas tokens — nenhum valor mágico de cor/espaço nas
telas. Previews (`@Preview`, claro/escuro, fonte grande) serão adicionados na
rodada de polimento guiada pelo Crítico.

## 5. Telas e fluxos

- **Onboarding** ("Preparando o ambiente"): checklist real das 5 etapas,
  progresso determinado com % e ETA por fase, log técnico recolhível, erro com
  mensagem humana + "Tentar novamente" + "Copiar diagnóstico". O provisionamento
  roda no container do app: fechar o app no meio e reabrir retoma de onde parou.
- **Chat**: barra superior com conexão + modelo; **banner de erro com retry**
  quando `ConnectionState.Failed`/`chat.error`; streaming com auto-scroll
  inteligente (para de seguir ao rolar para cima, botão "ir ao fim" que
  realmente rola); ações por mensagem (copiar / editar e reenviar / regenerar);
  fila de steering exibida; **Parar** sempre acessível (botão vira vermelho);
  anexo de imagem real (photo picker → base64 → Pi); rascunho por projeto
  persistente (`rememberSaveable`); estado vazio com sugestões clicáveis.
- **Projetos**: busca, criar (diálogo), clonar (diálogo com validação de URL,
  progresso e nota de duração), excluir com **desfazer** (janela de 6 s, o
  snackbar cancela a remoção), estados vazios.
- **Configurações**: provedores (personalizados com `ExposedDropdownMenuBox`),
  chave de API mascarada com mostrar/ocultar, modelos, **aparência** (tema,
  dinâmico, tamanho de fonte), raciocínio padrão, reinstalar ambiente (com
  confirmação), diagnóstico (recolhível + copiar), sobre (versão + licenças).
- **Navegação**: sem biblioteca de navegação — `Screen` enum + `when` (keeps APK
  pequeno); `BackHandler` por tela; `enableOnBackInvokedCallback` no manifest
  para predictive back; snackbar global para notificações one-shot.

## 6. Acessibilidade

- Alvos ≥ 48 dp em todos os elementos interativos (enforced centralmente).
- `contentDescription` em todo ícone com significado; ícones decorativos com
  `null`.
- Cards de ferramenta: `semantics(mergeDescendants)` com descrição
  ("Edição. concluído. caminho/arquivo.kt. 1,2s").
- Contraste AA validado por script; estados comunicados por cor **e** rótulo
  (nunca só cor).
- Fonte grande: tokens sem alturas fixas em sp-only contexts; escala do usuário
  multiplica `fontScale`.

## 7. Estados de ferramenta

| Estado | Cor | Rótulo |
|---|---|---|
| Executando | `toolRunning` (pulsa) | "executando" |
| Ok | `toolOk` | "concluído" |
| Erro | `toolError` | "erro" |
| Cancelado | `textTertiary` | "cancelado" |

## 8. Sessões por projeto (frente A)

O Pi 1.6.0 grava cada conversa em `~/.pi/agent/sessions/--<cwd>--/<timestamp>_<id>.jsonl`
(docs `session-format.md`; convenção verificada empiricamente — pontos e hífens
preservados, apenas `/`, `\`, `:` viram `-`). Como o app sempre abre o Pi no
`cwd` do projeto (`/root/projects/<nome>`), o diretório de sessões de cada
projeto é **determinístico e permanente** — é essa propriedade que sustenta o
recurso, não uma cópia local do histórico.

Padrões novos:

- **`SessionRepository` (`core/sessions/`)** — lista/resume/renomeia/exclui
  sessões lendo os JSONL **sem subir o Pi**. Sumário via gate regex +
  dispatch pelo tipo *parseado* (`session`/`message`/`session_info`/
  `model_change`); arquivo truncado/corrompido degrada (`corrupt`/`truncated`)
  sem quebrar. Fonte da verdade segue sendo o arquivo do Pi; o app só guarda
  o marcador "última sessão ativa por projeto" (`files/state/last-session.properties`).
- **Retomada** — abrir um projeto inicia o Pi e envia `switch_session` →
  `get_messages` → `ChatState` reconstruído por `SessionHydrator` (inclui
  cards de tool call com resultado/erro/cancelado, diffs de `edit`, execuções
  `bash` como avisos). Configurável ("Retomar última sessão", padrão: ligado);
  desligado, abrir o projeto mostra a lista de sessões sem subir o Pi.
- **Tela "Sessões do projeto"** — busca, itens com título (nome, senão
  primeira mensagem), data relativa, contagem, modelo, selo "Ativa"; menu por
  item (renomear/duplicar/excluir) com confirmação + snackbar de desfazer (6 s);
  botão "Nova sessão" em destaque; estados esqueleto/vazio/erro com retry.
  Ação destructiva na sessão **ativa** é bloqueada com mensagem clara
  (trocar antes de excluir) — nunca corromper estado (aceite §4.1.7).
- **Troca segura** — trocar/duplicar com o agente ocupando CPU faz `abort`
  antes do `switch_session`; a sessão abortada permanece no disco.
- **Top bar do chat** — nome da sessão tocável (renomear via RPC
  `set_session_name`) + ícone de acesso rápido à lista.
- **Prova objetiva em CI** — `scripts/session-persistence.js` (chamado pelo
  job `smoke`): fixture em formato real → `switch_session` → `get_messages` →
  `kill -9` → processo novo → mesma sessão → histórico idêntico (`SESSIONS_OK`).

## 9. Navegação (atualização)

`Screen` ganhou `Sessions`. Fluxos: Projects → Chat (retoma) ou Projects →
Sessions (pref. desligada); Chat ↔ Sessions; back handles por tela
(`backFromSessions` volta ao Chat quando há sessão viva, senão aos Projetos).

## 10. Configurações por categorias (frente B)

- **Hub + busca**: `SettingsScreen` vira um hub com `SettingsCategoryRow`
  (ícone, título, subtítulo com o valor atual — "Tema: Escuro", "Chave: ✓").
  A busca filtra as categorias pelo título e leva direto à tela certa.
- **Categorias**: cada uma abre sua tela (`SettingsSection` enum em
  `HarnessUiState.settingsSection`, predictive back fecha categoria → hub →
  sai). Deep link `harness://settings/{categoria}` (ex.: `harness://settings/appearance`).
- **Lista-detalhe** em telas largas (`WindowWidthSizeClass ≥ Medium`): hub à
  esquerda (360 dp), categoria à direita — tablet e dobrável.
- **Componentes** (`ui/components/settings/SettingsRows.kt`, com previews
  escuro/claro/fonte grande): `SettingsCategoryRow`, `SettingsSwitchRow`,
  `SettingsChoiceRow` (+ `HBottomSheetChoice` com radio),
  `SettingsSliderRow` (discreto, para a fonte), `SettingsInfoRow`,
  `SettingsDangerRow` (ações destrutivas separadas com confirmação).
- **Persistência tipada**: `SettingsRepository` migrado para **DataStore**
  (`AppSettings` imutável com defaults; snapshot em memória para leituras
  síncronas sem refazer os ViewModels; migração one-shot do SharedPreferences
  legado, que é limpo ao final; chaves de API permanecem no `SecretStore`
  intocadas).
- **Preferências de chat reais**: rolagem automática (`ChatList`), markdown
  on/off, enviar com Enter (IME action do `Composer`) e haptics ao enviar —
  todas ligadas ao comportamento, nada de opção morta.
- **Robustez**: `Apagar dados` (Privacidade) e `Reinstalar ambiente`
  (Ambiente) com confirmação; diagnósticos com cópia e teste real do ambiente
  (node/pi versões via PRoot).

## 11. Pacotes do Pi (frente C)

- **Fonte da verdade**: `~/.pi/agent/settings.json` (global) e
  `.pi/settings.json` (projeto, `-l`) — lidos direto do host (o rootfs é
  acessível), nunca parse da saída frágil de `pi list`. O CLI roda **dentro do
  PRoot** com `HOME=/root` e o `cwd` do projeto, então os arquivos são os
  mesmos que o Pi usa.
- **`PiPackageManager` (`core/pi/`)**: `listInstalled` (JSON declarado +
  recursos do `package.json`/pastas convencionais), `install/remove` com
  `Flow<String>` de saída ao vivo, `update(--extensions | <source>)` — **nunca
  `pi update` pelado** (isso atualiza o próprio Pi). Um processo por vez
  (mutex) com cancelamento real (`destroyForcibly`).
- **Explorador**: busca no npm (`registry.npmjs.org/-/v1/search`,
  `keywords:pi-package`, paginada) conforme o arquivo do usuário; cartões com
  nome/versão/descrição/autor/downloads/data/tipo por keyword; toque preenche
  a origem. Nota explícita de que os resultados vêm do npm e podem diferir da
  galeria pi.dev.
- **Segurança**: diálogo de confirmação OBRIGATÓRIO antes de instalar
  ("executa código com as permissões do Pi"); bloqueio quando o agente está
  gerando resposta; uma operação por vez.
- **Tela** (`PackagesScreen`, dentro de Configurações → Pacotes do Pi):
  seções Global/Do projeto, itens com ícone de origem (npm/git/local), versão
  ou ref, selo "Fixado" e recursos; instalar com validação de formato +
  exemplos tocáveis + escolha de escopo; log recolhível com "Cancelar" e
  "Copiar diagnóstico"; erros com retry; pós-operação o banner oferece
  **"Reiniciar sessão agora"** (`restartSessionAfterPackageChange` reinicia o
  Pi e retoma a MESMA sessão — Frente A).
- **Prova em CI**: smoke instala `npm:cc-safety-net@2.8.3` (fixado,
  determinístico), valida o `settings.json` + `pi list`, remove e valida de
  novo (`PACKAGES_OK`).

## 12. Rodada 4 — integração e polimento

- **Modo de steering/follow-up** (§6.1): duas escolhas em Configurações →
  Chat ("Uma por vez" / "Todas juntas") mapeadas para `set_steering_mode`/
  `set_follow_up_mode`; aplicadas a cada handshake (`applyQueueModes`) e ao
  vivo quando o agente está conectado.
- **Atualizar o Pi** (Ambiente): `pi update` real, com confirmação, bloqueio
  com agente ocupado e log exibido na própria categoria. NUNCA usado para
  pacotes (aqueles usam `--extensions`/fonte específica).
- **Limpar sessões antigas** (Projetos e sessões): mantém as 10 mais recentes,
  nunca apaga a sessão ativa (Frente A), com confirmação e notice de resultado.
- **Hub com valor real**: "Pacotes do Pi" mostra "N pacote(s) instalado(s)";
  a busca do hub indexa palavras-chave por categoria (ex.: "enter" acha Chat).
- **Explorador paginado**: "Carregar mais" acumula páginas (`from`), retry
  dedicado no erro da busca.
- **Swipe para excluir** na lista de sessões (M3 `SwipeToDismissBox`, com
  desfazer); renomear/duplicar permanecem no menu ⋮ (melhor para TalkBack).
- **Migração versionada**: `migrated_version` no DataStore (v1) — futuras
  migrações podem ser encadeadas sem re-importar.
- **Limitação documentada**: deep links (`harness://settings/{categoria}`)
  funcionam em cold start e `onNewIntent` (setIntent + LaunchedEffect);
  entregas fora desses caminhos dependem de recomposição — aceitável na
  arquitetura sem biblioteca de navegação.

## 13. Rodada 5 — refinamentos finais

- `PackageDeclaredCodec` (`core/pi/`): codec puro do estado declarado (strings
  e objetos {source}) + recursos por `package.json`/pastas — testável sem
  Android; `PiPackageManager` delega nele.
- `PackagesScreen` decomposta em `InstalledPackagesSection` /
  `ExplorerSection` / `OperationLogCard` (composables internos reutilizáveis).
- Resumo pós-operação: "Estado declarado: N pacote(s)" entra no log ao final
  de cada operação e após cancelamento (estado revalidado explícito).
- Previews (escuro/claro/fonte grande) para `PackageRow` e `SessionRow`.
- A11y: fundo do swipe com description dedicada ("Excluir <título>").

## 14. Rodada 6 — a11y fino

- Contagens de resultados com `LiveRegionMode.Polite` na busca de sessões e no
  explorador de pacotes — TalkBack anuncia mudanças sem roubar o foco.
