# Rodada 03 — Nota global: 8,6 / 10

Commit avaliado: `7911476`   |   CI: [run 45689272411](https://github.com/deivid22srk/harness-android/actions/runs/45689272411) — **verde** (proot ✓, smoke ✓ com `SESSIONS_OK` **e** `PACKAGES_OK`, apk ✓, lint ✓, 29 testes ✓)

Escopo: Frente C — Pacotes do Pi (gerenciador, tela, segurança, explorador,
smoke estendido) + integração "Reiniciar sessão agora".

| # | Critério | Peso | Nota | Evidência resumida |
|---|---|---|---|---|
| 1 | Arquitetura de informação/UX das Configurações | 12 | 9,0 | Hub agora tem 9 categorias incluindo Pacotes do Pi (subtítulo fixo — ver P2.2); deep link/route funcionam para a nova categoria (`SectionContent` → `PackagesScreen`). |
| 2 | Qualidade visual e consistência | 10 | 9,0 | Tela usa tokens/rows existentes; ícones de origem por tipo (npm/git/local), selo "Fixado", chips de recursos; HChipSmall/HSkeleton reutilizados. Sem preview dedicado da tela (padrão do repo). |
| 3 | Sessões: correção e persistência | 18 | 9,0 | Sem regressão — `SESSIONS_OK` no mesmo run; `restartSessionAfterPackageChange` retoma a MESMA sessão (`HarnessViewModel.kt:525-540`), provando a integração pedida no §5.3.5. |
| 4 | Pacotes do Pi: funcionalidade e segurança | 15 | 9,0 | Listar (Global/Do projeto, com tipo/ref/"Fixado"/recursos de `package.json`+pastas — `PiPackageManager.readPackageMeta`); instalar com validação de formato + exemplos + escopo + **confirmação de segurança obrigatória**; remover com confirmação; atualizar um ou todos (`--extensions`, nunca `pi update` pelado — `update()`); log ao vivo recolhível com Cancelar (kill real do processo) e copiar; erros com retry/detalhes; bloqueio com agente ocupado; mutex (1 op por vez). **Prova objetiva**: `PACKAGES_OK` no smoke (install fixado → settings.json + `pi list` → remove → valida). 8 testes do parser. Não 10: cancelamento mata o processo mas o estado declarado pode ter mudado parcialmente (npm interrompido) — mitigado pelo revalidate, mas um verificador pós-cancelamento explícito seria mais forte; explorador não pagina ("Carregar mais" ausente). |
| 5 | UX dos fluxos novos | 10 | 8,5 | Estados vazio/erro/carregamento (skeleton), banner "Recarregar o Pi?" com "Reiniciar sessão agora", exemplos tocáveis, busca com nota de diferença vs galeria pi.dev. Falta paginação do explorador e feedback de progresso na atualização de TODOS (só log). |
| 6 | Acessibilidade e adaptabilidade | 8 | 8,5 | contentDescription completos (update/remove por nome, origem por tipo); semantics mesclados nos cartões. Não 10: a tela Pacotes não tem layout lista-detalhe próprio (roda dentro da Settings, que tem em wide — mas a seção Pacotes renderiza full-width sem constrained width); log não é navegável por TalkBack além do bloco de código. |
| 7 | Arquitetura e qualidade de código | 12 | 9,0 | `PiPackageManager` separado (leitura de arquivos sem subir o Pi; mutações via CLI real em PRoot); `PackagesViewModel` imutável; `PackageSources` puro e testado; `ProcessRunner` ganhou `onStarted` para cancelamento real. Não 10: `PackagesScreen` é grande (~630 linhas) e mistura seções instalados/explorador/log num único composable rolável. |
| 8 | Robustez | 10 | 8,5 | Mutex + cancelamento real; revalidação do estado após cada op/cancel; fallback JSON tolerante (strings e objetos {source}); conflito com agente ocupado bloqueado. Faltas: rede caída na busca tratada, mas sem timeout visual de progresso; instalação parcial após kill não é detectada além do revalidate. |
| 9 | Build/CI | 5 | 9,0 | Run verde com os dois proofs no smoke; 2 runs falharam antes na rodada (import faltando; lambda não-composable chamando composable) — corrigidos. |

**Nota global = (12×9 + 10×9 + 18×9 + 15×9 + 10×8,5 + 8×8,5 + 12×9 + 10×8,5 + 5×9)/100 = (108+90+162+135+85+68+108+85+45)/100 = 886 → 8,9**

> Correção do cálculo: o título correto é **8,9 / 10** (a soma acima foi refeita no Crítico; manter a última linha como oficial).

## Tentativas de quebra (o que foi testado e o resultado)

- **Instalar pacote inexistente** — validação de formato aceita `npm:foo@x`; o CLI real retorna erro npm (404) → log mostra o erro, exitCode ≠ 0 → HErrorState com detalhes e retry. (Caminho testado via parser + comportamento documentado do CLI; o smoke cobre o caminho de sucesso real.)
- **Instalar com rede caída** — `searchRegistry` lança (timeout 15 s) → mensagem humana "A busca no npm falhou…"; install falha com erro npm no log → exitCode ≠ 0 → erro com detalhes. OK.
- **Trocar de sessão durante geração** — `switchToSession` faz `abort()` antes (Rodada 1, sem regressão). OK.
- **Executar duas operações simultâneas** — `requestInstall/remove/update*` bloqueiam com aviso quando `operation.running`; o mutex do manager serializa qualquer corrida. OK.
- **Agente gerando resposta** — bloqueio com aviso "interrompa antes de alterar pacotes". OK.
- **Cancelar no meio da instalação** — `cancelCurrent()` faz `destroyForcibly` no PRoot/cli; revalidação mostra o estado real; próxima op não corre contra a anterior (mutex liberado pelo término do processo). OK.
- **Remover pacote do escopo errado** — remove usa o escopo do próprio item (`pkg.scope == PROJECT` → `-l` com cwd do projeto). OK.
- **Abrir Pacotes sem projeto ativo** — `load(null)` lista só global; instalar local com projeto nulo usa cwd `/root`. OK (escopo projeto sem projeto ativo é inócuo mas não corrompe).
- **settings.json com objetos {source, filters}** — `readDeclared` aceita strings e objetos (docs "Select package resources"). OK.

## Bloqueantes (P0)

- Nenhum.

## Importantes (P1)

- Nenhum.

## Polimento (P2)

1. [PackagesScreen.kt] Explorador sem paginação ("Carregar mais" com `from` acumulando, como no arquivo do usuário) — hoje só os 20 primeiros.
2. [SettingsScreen.kt] Subtítulo do hub de "Pacotes do Pi" é estático ("Instalar, listar, remover e atualizar"); deveria mostrar "N pacotes instalados" (§6.1 pede valor atual).
3. [PiPackageManager.kt] Após cancelamento, verificar explicitamente o estado declarado e relatar "instalação interrompida; estado revalidado" em vez de depender só do refresh silencioso.
4. [PackagesScreen.kt] Operação "atualizar todos" sem progresso por pacote (o CLI atualiza em lote; o log cobre, mas um resumo final por pacote ajudaria).
5. [PackagesViewModel.kt] `search()` executa na main thread o `runCatching` — o IO está dentro do manager (`withContext(Dispatchers.IO)`) ✓, porém o erro de rede pode ser agrupado com ação "Tentar novamente" dedicada (hoje exige reapertar Buscar).

## Veredito: REPROVADO

Motivo: nota global 8,9 < 9,5. Nenhum critério abaixo de 8,5 — as frentes A,
B e C estão completas e provadas em CI; o gap agora é polimento (P2) e os
ajustes finos de nota nos critérios 5/6/8. Rodada 4 deve atacar os P2 acima e
a integração final (frente D do plano original).
