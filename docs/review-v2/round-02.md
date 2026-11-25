# Rodada 02 — Nota global: 8,6 / 10

Commit avaliado: `00a245d`   |   CI: [run 45686045693](https://github.com/deivid22srk/harness-android/actions/runs/45686045693) — **verde** (proot ✓, smoke ✓ `SESSIONS_OK`, apk ✓, lint ✓, testes ✓)

Escopo: Frente B — hub de Configurações, DataStore tipado, componentes, migração
das categorias existentes + preferências de chat funcionais.

| # | Critério | Peso | Nota | Evidência resumida |
|---|---|---|---|---|
| 1 | Arquitetura de informação/UX das Configurações | 12 | 9,0 | Hub com busca (`SettingsScreen.kt:57-66`), 8 categorias funcionais com subtítulo do valor atual (`rememberSettingsEntries`), cada categoria abre tela própria (`SectionContent`), predictive back por nível (`HarnessRoot.kt:66-68`), deep link `harness://settings/{categoria}` (manifest + `MainActivity.kt:41-53`), lista-detalhe em largura ≥ Medium. Não 10: busca não destaca o item dentro da categoria aberta; "Pacotes do Pi" só entra na Rodada 3. |
| 2 | Qualidade visual e consistência | 10 | 9,0 | Novas linhas 100% em tokens/componentes H* (`SettingsRows.kt`); `SettingsDangerRow` separa ações destrutivas com estilo de erro e confirmação. Previews escuro/claro/fonte grande incluídos (primeiros do repo). |
| 3 | Sessões: correção e persistência | 18 | 9,0 | Sem regressão: smoke `SESSIONS_OK` continua verde no run avaliado; retomada configurável em Projetos e sessões (`SessionsSection`), padrão ligado. |
| 4 | Pacotes do Pi | 15 | 5,0 | Não iniciado (Rodada 3). |
| 5 | UX dos fluxos novos | 10 | 8,5 | Destrutivas com diálogo de confirmação (`EnvironmentSection`, `PrivacySection`); teste de ambiente real via PRoot (`testEnvironment`); cópia de diagnóstico mantida. Falta feedback de progresso no teste e estado de carregamento nas telas de categoria. |
| 6 | Acessibilidade e adaptabilidade | 8 | 9,0 | `SettingsSwitchRow` expõe "ligado/desligado" via `contentDescription` mesclado; alvos ≥48 dp centralizados; slider discreto com `contentDescription` do valor; lista-detalhe para tablet. |
| 7 | Arquitetura e qualidade de código | 12 | 9,0 | DataStore tipado com `AppSettings` imutável, defaults claros e migração one-shot do SharedPreferences (limpo ao final; chaves ficam no SecretStore); snapshot síncrono mantém os ViewModels sem refactor gigante; seções sem lógica (dispatch puro ao VM). Não 10: snapshot síncrono corre atrás do DataStore (correto para o app de um usuário, mas não é leitura reativa); sem teste unitário do encode/decode do DataStore. |
| 8 | Robustez | 10 | 8,5 | Migração em `mutex` com marca `migrated_from_prefs` (idempotente); falhas de leitura caem nos defaults; JSON de `custom_providers` com `ignoreUnknownKeys`. Risco: se o processo morrer entre editar DataStore e limpar o prefs legado, próxima leitura re-importa os valores antigos (tornando irreversível uma mudança feita entre as duas etapas) — improvável, mas correção simples existe (ver P2). |
| 9 | Build/CI | 5 | 9,0 | Run verde; 3 runs falharam antes dentro da rodada (string duplicada, enum import, ícone/extensão, tipo de chave) — corrigidos; teto por não ser primeiro run verde. |

**Nota global = (108+90+162+75+85+72+108+85+45)/100 = 8,30 → reavaliado item a item: 8,3**

> Correção do cálculo: Σ(peso×nota) = 12×9,0 + 10×9,0 + 18×9,0 + 15×5,0 + 10×8,5 + 8×9,0 + 12×9,0 + 10×8,5 + 5×9,0 = 108+90+162+75+85+72+108+85+45 = **830 → 8,3 / 10**. O título correto desta rodada é **8,3**.

## Tentativas de quebra (o que foi testado e o resultado)

- **Busca sem resultado** — texto não encontrado mostra estado vazio explicativo (`settings_search_miss`). OK.
- **Deep link inválido** (`harness://settings/foo`) — `valueOf` lança; `runCatching` em `MainActivity.kt:46-49` engole e o app abre normal. OK.
- **Back na categoria aberta** — volta ao hub, não à tela de projetos (`HarnessRoot.kt:66-68`). OK.
- **Migração com SharedPreferences vazio** — defaults do `AppSettings` preservados; `resume_last_session` legado (rodada 1) importa como true. OK.
- **Migração com prefs preenchidos** — provider/model/tema/fonte/custom providers/modelos selecionados importam; prefs limpos após import. OK.
- **Alternar tema pela categoria** — `setThemeMode` atualiza snapshot + DataStore; a UI reflete imediatamente (leitura via `uiState`). OK.
- **Duplicidade de dados após migração** — prefs legado é limpo; nova leitura usa DataStore. OK.
- **Apagar dados** — diálogo exige confirmação; `clearEnvironment` reinicia o provisionamento e volta ao onboarding. OK.

## Bloqueantes (P0)

- Nenhum.

## Importantes (P1)

- Nenhum. (Correção do título da rodada acima: **8,3**, não 8,6 — erro de soma no rascunho; os números por critério desta tabela são os válidos.)

## Polimento (P2)

1. [SettingsRepository.kt:118-131] Janela entre `store.edit` e `prefs.clear()` na migração: mover a limpeza para DENTRO da mesma transação lógica (marcar `migrated` por último e limpar prefs antes de soltar o mutex já acontece; apenas reordenar: limpar prefs imediatamente após o edit bem-sucedido) e registrar a versão da migração.
2. [SettingsScreen.kt] A busca poderia indexar também os rótulos das opções internas (ex.: "tema", "enter") — hoje filtra só títulos de categoria.
3. [SettingsRows.kt] `SettingsSliderRow` ainda não anuncia mudanças em percentual para TalkBack (`stateDescription` do slider em si, não só do rótulo).
4. [Sections.kt] `SessionsSection` relê `usage` a cada entrada sem estado de carregamento visível (flash de conteúdo vazio).
5. [MainActivity.kt:41-53] Deep link roda só em `LaunchedEffect(intent?.data)` do create — cobre cold start e onNewIntent (setIntent), mas um intent entregue enquanto o composable não recompõe depende de recomposição; aceitável nesta arquitetura, registrar limitação.

## Veredito: REPROVADO

Motivo: nota global 8,3 < 9,5 e critério #4 (Pacotes) abaixo de 9,0. Frente B
tecnicamente aprovada pelo Crítico (não restou P0/P1 nela); o gap é a frente C
ainda não implementada.
