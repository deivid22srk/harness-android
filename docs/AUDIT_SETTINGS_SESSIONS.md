# Auditoria — Sessões por projeto e Configurações (Rodada 0)

> Diagnóstico produzido **antes de qualquer correção**, conforme o PROMPT §3.3.
> Cada hipótese do prompt foi verificada com evidência `arquivo:linha`. A validação
> do comportamento do Pi foi feita na versão exata instalada pelo app
> (`@earendil-works/pi-coding-agent@1.6.0`), com experimentos reais de RPC.

## 1. Causa-raiz do bug de sessões

### 1.1 Hipóteses avaliadas

| # | Hipótese | Veredito | Evidência |
|---|---|---|---|
| H1 | Pi iniciado com `--no-session` ou `--session-dir` diferente a cada execução | **Parcialmente verdadeira** | `PiProcess.kt:59-72` — nunca passa `--session-dir`; `--no-session` só quando `useSession=false` (o fluxo de chat usa sessão). O Pi usa então o diretório padrão `~/.pi/agent/sessions/--<cwd>--/`, que **persiste**. O problema não é a flag, é a falta de retomada (H4). |
| H2 | O `cwd` do Pi muda entre execuções | **Falsa** | `HarnessViewModel.kt:320-324` — `workDirGuest = project.guestPath` (`/root/projects/<nome>`), determinístico por projeto. |
| H3 | O app sempre envia `new_session` ao abrir o projeto | **Parcialmente verdadeira** | `AgentSession.start()` (`AgentSession.kt:90-147`) sempre inicia um processo novo; sem `--continue`/`--session`, o Pi cria uma sessão nova a cada abertura. `new_session` explícito só existe no botão de nova sessão. |
| H4 | Nunca é chamado `switch_session`/`get_messages` para reconstruir a conversa | **Verdadeira (causa principal)** | Busca por `switch_session`, `get_entries`, `fork`, `clone` em `app/src/main/java/`: **zero ocorrências**. `get_messages` é chamado só no handshake do processo recém-iniciado (`AgentSession.kt:139`), cuja sessão é nova. |
| H5 | Estado do chat só em memória | **Verdadeira (contribui)** | `AgentSession.internal` (`AgentSession.kt:76`) e `HarnessUiState.chat` vivem no processo; nada persiste `sessionFile` por projeto. Ao matar o app, a posição na sessão se perde (o JSONL do Pi fica no disco, órfão e inacessível pela UI). |
| H6 | Processo morto e recriado sem guardar `sessionFile`/`sessionId` | **Verdadeira (contribui)** | `PiProcess.stop()` (`PiProcess.kt:49-57`) e `AgentSession.stopInternal()` (`AgentSession.kt:465-473`) descartam tudo; `get_state` (`AgentSession.kt:327-335`) lê só `model`/`thinkingLevel`/`sessionName` — ignora `sessionFile`, `sessionId` e `messageCount` que o Pi retorna (verificado no RPC real de 1.6.0). |
| H7 | Falta tela/lista de sessões | **Verdadeira** | Não existe qualquer UI de sessões; só "Nova sessão" destrói o contexto atual. |

### 1.2 Causa-raiz consolidada

**O app nunca retoma a sessão que o Pi gravou.** O Pi (1.6.0) grava cada conversa
em `~/.pi/agent/sessions/--<cwd>--/<timestamp>_<uuid>.jsonl` (verificado
empiricamente; `cwd` do app = `/root/projects/<nome>`, logo o diretório é
determinístico: `--root-projects-<nome>--`). A cada `openProject` o
`HarnessViewModel` sobe um processo novo sem apontar para a sessão anterior, e a
UI reconstrói o estado apenas do que chega por eventos. O histórico "some" para o
usuário, embora permaneça em disco. Pior: `hydrateMessages` (`AgentSession.kt:354-378`)
descarta `toolResult` e blocos `toolCall`, portanto mesmo um `get_messages` de
sucesso não reconstruiria os cards de ferramentas.

Defeitos decorrentes que serão corrigidos junto:

- **B1 (P1)** — `HarnessViewModel.newSession()` (`HarnessViewModel.kt:422-429`)
  chama `AgentService.stop(context)`, e `ACTION_STOP` (`AgentService.kt:41-46`)
  derruba o serviço **e** o processo do Pi logo depois de criar a sessão nova.
- **B2 (P1)** — `hydrateMessages` não reconstrói cards de tool call
  (`AgentSession.kt:354-378`): pula `toolResult` (`:372`) e ignora blocos
  `toolCall` de mensagens assistant.
- **B3 (P2)** — `get_state` ignora `sessionFile`/`sessionId`/`messageCount`
  (`AgentSession.kt:327-335`), impossibilitando saber qual sessão está ativa.

### 1.3 Verificação empírica no Pi 1.6.0 (a versão instalada pelo Provisioner)

Experimentos rodados localmente com `npm install @earendil-works/pi-coding-agent@1.6.0`:

1. `get_state` retorna `sessionFile`, `sessionId`, `sessionName`, `messageCount`,
   `isStreaming` — conforme `rpc-commands.md` da própria versão.
2. Convenção do diretório confirmada: cwd `/tmp/pilab/pkgtest/Meu.Proj-01` →
   `~/.pi/agent/sessions/--tmp-pilab-pkgtest-Meu.Proj-01--/`. Regra (docs
   `session-format.md`): remove a barra inicial e troca `/`, `\`, `:` por `-`.
   Pontos e hífens são preservados.
3. **Importante (fonte: código do Pi, `session-manager.js` `_persist`)**: o arquivo
   `.jsonl` só é escrito quando existe mensagem `user`/`assistant` ("A new session
   file is created only once the session contains a user or assistant message").
   Sessões sem conversa real ficam só em memória — comportamento que a lista de
   sessões do app deve tolerar (arquivos = sessões que tiveram conversa).
4. `switch_session {sessionPath}` → `get_messages` → histórico restaurado,
   `get_state` traz `sessionName` e `messageCount`. **Validado de ponta a ponta**
   com um JSONL de teste (header v3 + user + assistant + session_info).
5. Sem chave de API, `prompt` não gera mensagem gravável; `bash` (RPC) cria
   mensagem de contexto mas não dispara a gravação do arquivo. O `smoke` do CI
   (sem chaves) validará a persistência com um JSONL de fixture + `switch_session`,
   o mesmo caminho que o app usa.

## 2. Inventário da tela de Configurações atual

`ui/screens/SettingsScreen.kt` (658 linhas) — **uma tela única rolável** com
seções planas (`HSectionTitle`): Provedor de IA, Modelos, Aparência, Raciocínio,
Armazenamento, Diagnóstico, Sobre. Problemas:

| Item | Gravidade | Evidência |
|---|---|---|
| Sem categorias nem busca; tudo empilhado numa tela longa | P1 (ux) | `SettingsScreen.kt:113-237` |
| Persistência não tipada (SharedPreferences, chaves soltas, sem defaults centralizados) | P1 (arch) | `SettingsRepository.kt:62-212` |
| Sem layout lista-detalhe para tablet/dobrável | P2 | `SettingsScreen.kt:76-111` |
| "Reinstalar ambiente" destrutivo sem zona separada | P2 | `SettingsScreen.kt:208-222` |
| Sem "Pacotes do Pi", sem preferências de chat/sessões | P1 (feature) | ausência total |

Ponto forte a preservar: strings já centralizadas em `strings.xml`
(zero `Text("...") hardcoded na tela).

## 3. Fonte da verdade para pacotes (verificada)

- Arquivo do usuário (`pastebin A9Y8fNFa`, lido por inteiro): define a **descoberta**
  via `GET https://registry.npmjs.org/-/v1/search?text=keywords:pi-package&size=60&from=N`,
  campos dos cartões (`package.name/version/description`, `publisher.username`,
  `date`, `keywords`, `links.npm`, `downloads.monthly`) e aviso de segurança.
- `pi install --help` / `pi remove --help` (1.6.0 real): `pi install <source> [-l]`,
  `pi remove <source> [-l]`.
- `pi list` (1.6.0 real): `User packages:` / caminho; vazio → `No packages installed.`
  Formato de saída frágil — o estado autoritativo será lido de
  `~/.pi/agent/settings.json` (chave `packages`: array de strings ou objetos com
  `source`), com `pi list` como complemento. Verificado: instalar grava
  `"npm:cc-safety-net@2.8.3"` e remover grava `"packages": []`.
- **`pi update` sem argumento atualiza o próprio Pi** (docs `cli.md`); atualização
  de pacotes é `pi update --extensions` (todos) ou `pi update <source>` (um). O app
  nunca chamará `pi update` pelado.
- Pacote instalado fica em `~/.pi/agent/npm/node_modules/<nome>`; recursos vêm da
  chave `pi` do `package.json` ou das pastas `extensions/ skills/ prompts/ themes/`.

## 4. Plano aprovado para as rodadas

| Rodada | Escopo |
|---|---|
| 1 | `SessionRepository` (parser JSONL preguiçoso + diretório determinístico), `switch_session`/`get_entries`/`fork`/`clone`/`set_session_name` no `AgentSession`, reconstrução completa do `ChatState` (incl. tool cards), tela "Sessões do projeto", retomada automática, persistir última sessão por projeto, smoke de persistência |
| 2 | Hub de Configurações por categorias + `SettingsRepository` tipado (DataStore) com migração + componentes `Settings*` + telas Conta/Aparência/Chat/Sessões |
| 3 | `PiPackageManager` (PRoot, `Flow` de saída), tela Pacotes (instalar/listar/remover/atualizar, escopo, segurança, log), explorador npm, smoke de pacotes |
| 4 | Ambiente/Privacidade/Diagnóstico/Sobre + integrações (reiniciar sessão após pacotes) + polimento adaptativo/a11y |
| 5+ | Ciclos Crítico × Criador até a condição de parada |
