# Missão: elevar o Harness de MVP a qualidade de produção

Este documento é o **contrato** entre dois agentes que trabalham em loop:

- **EXECUTOR** — implementa/corrige/refina o app.
- **CRÍTICO** — audita de forma extremamente rigorosa e dá uma nota de 0 a 10.

O objetivo é **nota ≥ 9.5** ("perto de 10") em cada iteração. Enquanto o CRÍTICO
der nota < 9.5, o EXECUTOR recebe o parecer e corrige. A cada rodada, o trabalho
é commitado e enviado ao GitHub (branch `main`, repo `deivid22srk/harness-android`).

## Estado atual conhecido (não repita erros já corrigidos)

- PRoot compilado com o NDK (fork `termux/proot`), **autocontido** (talloc + shim
  ashmem estáticos). Sem `$ORIGIN` rpath (o make mangleia para "RIGIN").
- Keystore: o IV deve ser gerado pelo provedor (`setRandomizedEncryptionRequired`),
  nunca passado pelo app.
- Provisionamento inicia automaticamente; roda sob Foreground Service.
- Provedor personalizado **precisa** declarar modelos no `models.json`, senão o Pi
  não tem catálogo e não inicializa / `--list-models` volta vazio.
- `models.json` fica em `~/.pi/agent/models.json` (guest `/root/.pi/agent/`).

## O que o usuário relatou e PRECISA estar impecável

1. **Provedor personalizado**: ao adicionar e escolher um provedor personalizado,
   o Pi deve inicializar e enxergar os modelos. "Obter modelos" deve listar os
   modelos desse provedor. Teste o fluxo completo mentalmente/código a código.
2. **Funções que não funcionam** — encontre e corrija. Suspeitas mínimas:
   - `AgentSession.listAvailableModels()` quando não há sessão ativa.
   - `ModelCatalog.listModels()` — parsing da tabela `pi --list-models`, e uso do
     `baseUrl`/`apiKey` corretos.
   - Escopo `--models` ao iniciar o Pi (formato esperado pelo Pi).
   - `get_messages` hidratando corretamente; tool groups; diffs.
   - Seleção de modelos por provedor e o que aparece no composer.
3. **UI/UX** — melhorar de forma perceptível: hierarquia, estados vazios,
   feedback de erro, carregamento, consistência de espaçamento/tipografia,
   acessibilidade (área de toque ≥ 48dp, contraste), e fluidez.

## Restrições inegociáveis (do PROMPT original)

- Sem interface de terminal. UI minimalista estilo OpenCode.
- Dependências mínimas (sem Hilt/Retrofit/Room/Coil/WebView/material-icons-extended).
- Segurança de chaves (Keystore; nada em texto puro/logs/repo).
- APK ≤ 12 MB, apenas arm64-v8a.
- Não desabilitar etapas de build só para passar.
- Tudo compila: `./gradlew lintDebug testDebugUnitTest assembleRelease` (o CI roda isso).

## Como o CRÍTICO deve avaliar (rubrica, 0–10)

Avalie por evidência, citando arquivos e linhas. Use a média ponderada:

| Critério | Peso | O que conta como 10 |
|---|---|---|
| Correção funcional (provedor personalizado, obter modelos, chat, ferramentas) | 30% | Cada fluxo funciona ponta a ponta; erros tratados |
| Robustez/estados de erro | 15% | Sem falhas silenciosas; mensagens úteis; sem crash |
| Qualidade de código (arquitetura, testes, legibilidade) | 15% | Reducer/parser testados; sem código morto; consistente |
| UI/UX e acessibilidade | 20% | Hierarquia clara, feedback, toque ≥48dp, contraste, i18n pt/en |
| Segurança | 10% | Chaves protegidas; sem vazamentos; manifest mínimo |
| Desempenho/tamanho | 10% | APK ≤ 12 MB; start rápido; sem jank no streaming |

O CRÍTICO **deve** listar: nota por critério, nota final, e uma lista priorizada
de defeitos com **arquivo:linha** e uma correção sugerida concreta. Se a nota for
< 9.5, o veredito é "REPROVADO" e o loop continua.

## Definição de pronto (DoD)

- Nota do CRÍTICO ≥ 9.5.
- CI verde (PRoot + smoke + APK).
- Release publicado com APK.
- Relatório final conciso.
