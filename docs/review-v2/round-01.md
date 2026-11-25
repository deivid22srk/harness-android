# Rodada 01 — Nota global: 7,9 / 10

Commit avaliado: `be614f2`   |   CI: [run 45680447725](https://github.com/deivid22srk/harness-android/actions/runs/45680447725) — **verde** (proot ✓, smoke ✓ com `SESSIONS_OK`, apk ✓, lint ✓, 27 testes unitários ✓)

Escopo da rodada: Frente A — sessões por projeto. Avaliação do estado atual do
repositório (diff + código + CI), não da intenção do Criador.

| # | Critério | Peso | Nota | Evidência resumida |
|---|---|---|---|---|
| 1 | Arquitetura de informação/UX das Configurações | 12 | 5,0 | A tela de Configurações não foi tocada nesta rodada (`SettingsScreen.kt` inalterado). Reavaliar na Rodada 2. |
| 2 | Qualidade visual e consistência com o design system | 10 | 9,0 | `SessionsScreen.kt` usa exclusivamente tokens/componentes H* (`HButton`, `HTextField`, `HBadge`, `HEmptyState`, `HErrorState`, `HSkeleton`, `HDialog`); `accentContainer` para a sessão ativa com `onAccentContainer` — sem cor avulsa. Nenhum `@Preview` da nova tela (padrão do repo é preview por componente). |
| 3 | Sessões: correção e persistência | 18 | 9,0 | Causa-raiz documentada (`docs/AUDIT_SETTINGS_SESSIONS.md`, H4) e corrigida: `switch_session`→`get_messages`→`SessionHydrator` (`AgentSession.kt:240-259`, `SessionHydrator.kt`); retomada automática com marcador por projeto (`SessionRepository.kt:137-153`, `HarnessViewModel.kt:151-173`); criar/renomear (RPC + append `session_info`)/excluir com desfazer/duplicar (`clone`)/buscar; UI completa (`SessionsScreen.kt`). **Prova objetiva**: `SESSIONS_OK` no smoke do CI — kill -9 → processo novo → histórico idêntico. 11 testes de parser/hydrator com fixtures reais (válido, truncado, corrompido, header-only, vazio). Não 10: faltam cards de branch (`get_entries`/`get_tree`) e o comportamento de `context_edit` não é renderizado. |
| 4 | Pacotes do Pi | 15 | 5,0 | Não iniciado (Rodada 3). |
| 5 | UX dos fluxos novos | 10 | 8,5 | Vazio/erro/carregamento/desfazer presentes e testados por inspeção; **sem swipe** nos itens (menu ⋮ usado em vez do swipe pedido no §4.3) — funcional mas é um desvio do aceite. |
| 6 | Acessibilidade e adaptabilidade | 8 | 9,0 | `semantics(mergeDescendants)` na linha; `contentDescription` contextuais ("Ações da sessão X"); alvos 48 dp via `touchTargetClickable`; sem layout lista-detalhe para tablet ainda. |
| 7 | Arquitetura e qualidade de código | 12 | 9,0 | UDF mantido; `SessionsViewModel` com `UiState` imutável e eventos one-shot; `SessionRepository` puro (File-based, testável sem Android — demonstrado no compile JVM local); zero lógica nos composables. Acoplamento leve via callbacks (`onActiveRename`) documentado. |
| 8 | Robustez | 10 | 9,0 | Abort antes de trocar sessão ocupada (`AgentSession.kt:243`); exclusão da sessão ativa bloqueada com mensagem; arquivo truncado/corrompido degrada (testes); marcador valida existência do arquivo; `switch_session` rejeitado → aviso + sessão nova (experimento real: cwd inexistente retorna erro tratado). Process death provado no CI. |
| 9 | Build/CI | 5 | 9,5 | Run verde incluindo o smoke estendido (`SESSIONS_OK`) e os testes novos. Um run falhou antes (`45679816472`: ícone inexistente em icons-core + string ausente) — corrigido no mesmo round; teto por não ser primeiro run verde. |

**Nota global = Σ(peso × nota)/100 = (60+90+162+75+85+72+108+90+47.5)/100 = 7,89 → 7,9**

## Tentativas de quebra (o que foi testado e o resultado)

- **Sair e voltar do projeto** — `openProject` com sessão viva não relança o Pi (`HarnessViewModel.kt:331-349`); com `resumeLastSession=false` a lista abre sem subir o Pi e só inicia ao escolher sessão. OK.
- **Process death / kill do app** — provado no CI (`SESSIONS_OK`): kill -9 → novo processo → `switch_session` → histórico idêntico (2 mensagens, nome e id restaurados). OK.
- **Trocar de sessão durante streaming** — `switchToSession` faz `abort()` antes (`AgentSession.kt:243`); o arquivo da sessão abortada persiste (comportamento append-only do Pi). OK. *Observação: o abort é silencioso para o usuário — melhor avisar na UI (P2).*
- **Abrir sessão corrompida/truncada** — parser degrada (`corrupt`/`truncated`), lista renderiza título de fallback "Sessão"; testes cobrem. OK.
- **Excluir a sessão ativa** — bloqueado com mensagem clara (`SessionsViewModel.kt:126-130`). OK.
- **Renomear com nome vazio/200 chars** — rejeitado no repositório (testes). OK.
- **Retomar sessão cujo diretório de trabalho sumiu** — experimento real: Pi retorna `success:false` com mensagem de erro; o app mostra aviso e abre sessão nova (`HarnessViewModel.kt:372-380`). OK.
- **Renomear sessão fechada** — append de `session_info` funciona sem Pi (teste de integração do parser lê o nome novo). OK.
- **Reabrir projeto morto com marcador de arquivo apagado** — `lastSessionFileFor` valida existência → cai para o arquivo mais novo → se não houver, sessão nova. OK.

## Bloqueantes (P0)

- Nenhum.

## Importantes (P1)

- Nenhum nesta frente. (Os P1 da auditoria B1 `newSession` matando o agente e B2 cards perdidos no hydrate foram corrigidos nesta rodada: `HarnessViewModel.kt:551-565` não chama mais `AgentService.stop`; `SessionHydrator` reconstrói tool cards — testado.)

## Polimento (P2)

1. [SessionsScreen.kt:154-190] Itens sem **swipe** para excluir/renomear (menu ⋮ usado). Impacto: desvio do aceite §4.3; TalkBack na verdade se beneficia do menu, mas o gesto pedido não existe. Correção: `SwipeToDismissBox` (M3) com gesto→excluir e menu para o resto.
2. [HarnessViewModel.kt:551-565] `newSession()` durante geração aborta silenciosamente; o usuário não recebe aviso de que a geração foi cancelada. Correção: notice informativo quando `chat.isBusy` era true.
3. [SessionRepository.kt] Excluir um projeto deixa o marcador do projeto em `last-session.properties` (leitura tolera; sem limpeza). Correção: remover a chave em `ProjectRepository.delete`.
4. [SessionsScreen.kt] Lista não reordena ao vivo enquanto o usuário conversa (refresh só ao entrar); aceitável, mas um refresh leve ao voltar do Chat melhoraria a percepção.
5. [docs/AUDIT_SETTINGS_SESSIONS.md] Documentar a limitação de `context_edit`/`get_tree` (histórico restaurado usa `get_messages`; entradas de edição de contexto não são aplicadas na visão restaurada).

## Veredito: REPROVADO

Motivo: nota global 7,9 < 9,5; critérios #1 (5,0) e #4 (5,0) abaixo de 9,0 — as
frentes B (Configurações) e C (Pacotes) ainda não foram implementadas, além dos
P2 acima. A frente A (sessões) está tecnicamente completa e provada em CI, mas o
processo exige todas as frentes.
