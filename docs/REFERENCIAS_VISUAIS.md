# Referências visuais (fotos.zip) — análise e regras de design

> Fonte: `releases/download/Log/fotos.zip` (15 screenshots do app de referência
> "Opencode", resolução 1080×2400, tema escuro). Cópias otimizadas em
> `docs/design-refs/` (WebP ≤ 300 KB). Este arquivo é a **autoridade visual**
> das rodadas v3: toda decisão visual deve ser coerente com ele; divergências
> precisam de justificativa aqui.

## Análise por imagem

### 1. `chat_expandido_dark.webp` — Chat (visão geral)
- **Top bar**: menu hambúrguer (com ponto-azul de notificação), título do projeto
  ("Executar prompt gow_android") em peso alto + **subtítulo** do runtime
  ("gow-arm64") em cinza; à direita um botão circular escuro (~48 dp) com ícone
  de status/spinner.
- **Mensagem do usuário**: bolha arredondada (~16–20 dp) com fundo `#2e2e2e`,
  largura máx. ~82%, alinhada à direita, sem avatar.
- **Resposta do assistente**: **largura total, sem bolha** — texto corrido sobre
  o fundo `#161616`, cabeçalho discreto inexistente (o texto começa direto).
- **Ferramentas**: linhas colapsadas cinza "Usou 1 ferramenta ›" entre blocos de
  texto (não são cards pesados).
- **Rodapé fixo**: pílula de input com "+ Mensagem", microfone e botão circular
  de enviar; pílula flutuante "2 subagentes" no canto inferior esquerdo e botão
  circular "ir ao fim" à direita.

### 2. `chat_toolcard_shell_dark.webp` — Card de ferramenta (colapsado/expandido)
- Card de ferramenta tem fundo levemente elevado com borda sutil; ícone
  (terminal) + rótulo "Shell" em peso médio + comando em mono truncado com
  reticências.
- Expandido: bloco preto com o comando completo em mono, depois área de
  resultado com `(no output)` e `Exit code: 143 (SIGTERM)` em cinza.

### 3. `chat_shell_detalhe_dark.webp` — Mesmo card em detalhe
- Confirma hierarquia: **o card da ferramenta é escuro mas distinto do texto**;
  o bloco de código interno é mais escuro ainda (quase preto); resultado em
  cinza médio; sem barra de linguagem/copy dentro do card de ferramenta.

### 4. `chat_divisor_tempo_dark.webp` — Divisores temporais
- Divisor horizontal com **rótulo centralizado** ("Novo", "Hoje 16:55"): linhas
  finas dos dois lados, texto pequeno cinza. Marca recomeços de conversa.

### 5. `chat_streaming_stop_dark.webp` — Estado de streaming
- Enquanto gera, o botão de enviar vira **círculo branco com quadrado preto
  (stop)** — contraste máximo, sempre visível.
- Texto parcial do assistente renderizado já com Markdown (negrito, chips de
  código inline) durante o stream.

### 6. `chat_edit_card_dark.webp` — Tool call "Edit"
- Card com ícone de lápis + "Edit dispatch.rs"; expandido mostra **newString /
  oldString** como seções rotuladas com código mono (Rust); rótulos em peso
  médio, código sobre fundo preto.

### 7. `chat_edit_expandido_dark.webp` — Edit completo + abrir arquivo
- Depois do código: linha de **path** em mono cinza (`/data/data/.../dispatch.rs`),
  rodapé "Edited runtime/src/dispatch.rs (1 replacement)" em cinza e ação
  **"<> Abrir dispatch.rs ›"** — abre o visualizador de código.

### 8. `visor_codigo_sintaxe_dark.webp` — Visualizador de arquivo
- Top bar: status à esquerda ("Concluído"), **nome do arquivo centrado** com
  subtítulo de caminho, ações de filtro e compartilhar à direita.
- Código com **syntax highlighting real** (keywords azul/lilás, tipos verde-água,
  strings verde, números âmbar, comentários cinza itálico) sobre fundo
  `#161616`; mono legível, sem numeração de linha visível.

### 9. `drawer_sessoes_dark.webp` — Drawer de sessões
- Painel sobre o chat: título grande "Opencode", busca no topo direito;
  resumo "Fique em dia" com ícone de camadas e **badge de contagem (8)**;
  seção "Recentes" com itens: **badge de letra** (G/H/T), nome da sessão,
  ponto-azul de não lida, **spinner no item da sessão ativa**; rodapé com botão
  "Nova sessão" (ícone editar) e engrenagem de configurações.

### 10. `configuracoes_hub_dark.webp` — Hub de configurações
- Título grande "Configurações" (~28–32 sp bold) com seta de voltar.
- **Card de conexão no topo**: mini-gráfico de pulso verde pontilhado, nome
  ("Opencode"), **latência "53 ms"** com ícone, subtítulo "127.0.0.1:4096 ·
  Loopback".
- Linhas de categoria agrupadas em cards: ícone à esquerda + rótulo
  (Aparência, Idioma, Sessões, Voz, Novidades, Experimental, Desenvolvedor);
  rodapé "Versão 0.8.8 (1025)" centrado em cinza.

### 11. `menu_contexto_sessao_dark.webp` — Menu de contexto de sessão
- Painel flutuante com ícones à esquerda: Fixar, Renomear, Copiar ID da sessão,
  Marcar como não lida, Silenciar, Compactar, Bifurcar, Exportar (›),
  Mídia da sessão, Informações da sessão e **Excluir sessão em vermelho**
  (ícone lixeira).

### 12. `chat_input_modelo_chip_dark.webp` — Input expandido + chips
- Pílula "Mensagem"; abaixo dela uma linha com **"+", chip "Build ⌄"** (comandos)
  e **chip do modelo "DeepSeek V4.1 Flash … ⌄"**, microfone e stop branco.
- Pílula "2 subagentes · 1 shell" com spinner + botão "ir ao fim".

### 13. `seletor_modelos_dark.webp` — Seletor de modelos (sheet)
- Seções: "Favoritos" (item selecionado com fundo elevado, ícone de letra,
  nome + provedor), "Recentes" (nome + provedor + rótulo de contexto à direita:
  "Low", "Padrão"), agrupamento por provedor ("OpenCode Zen"); **campo "Buscar
  modelos" fixo no rodapé** com lupa.

### 14. `sheet_subagentes_dark.webp` — Tarefas em background (sheet)
- Cartão flutuante escuro com seções "Subagentes" e "Shells": itens com
  **spinner animado**, nome da tarefa, **X** para encerrar e chevron; shells
  mostram comando mono truncado + duração ("0:42").

### 15. `chat_markdown_rico_dark.webp` — Markdown rico
- Títulos h2/h3 em peso alto; **listas com marcadores** (disc cinza-claro);
  **chips de código inline** frequentes (`.sbp`, `boot-options.json`,
  `docs/ASSETS.md`...) com fundo elevado sutil; negrito; parágrafos com bom
  ritmo (espaçamento generoso); largura de leitura confortável.

## Regras de design extraídas

### Paleta (hex medidos das screenshots)
| Papel | Valor |
|---|---|
| Fundo da tela (escuro) | `#161616` |
| Superfície elevada (cards, menus, drawer) | `#1C1C1E`–`#222223` |
| Bolha do usuário | `#2E2E2E` |
| Pílula de input | `#222222` |
| Botão circular de enviar (idle) | `#3A3A3A` |
| Botão stop (gerando) | círculo **branco** com quadrado preto |
| Bloco de código interno | quase preto `#0D0D0D`–`#101010` |
| Acento (notificação/não lida) | azul saturado ~`#2F80ED`/`#3D8BFF` |
| Status verde (pulso/latência) | `#70D090` (texto `#4ADE80`) |
| Destrutivo | vermelho ~`#E5484D` |
| Texto primário | quase branco `#EDEDED` |
| Texto secundário | cinza `#9E9E9E` |
| Texto terciário/metadata | `#7A7A7A` |
| Syntax: keyword | lilás `#9090F0` |
| Syntax: tipo/função | verde-água `#90E0A0` |
| Syntax: string | verde `#90E0A0` |
| Syntax: número/literal | âmbar `#F0B000` |
| Syntax: comentário | cinza itálico `#7A8399` |

### Tipografia
- Sistema (Roboto-like); títulos de tela 28–32 sp bold; título de top bar 17–18 sp
  bold com subtítulo 12–13 sp regular; corpo 15–16 sp; mono (JetBrains-like) para
  código/paths; labels de seção 12–13 sp cinza.

### Grid e espaçamento
- Padding horizontal da tela ~16 dp; espaçamento vertical entre mensagens
  **generoso** (~20–24 dp); blocos de markdown com ritmo de ~8 dp; cards com
  padding interno 12–14 dp.

### Raios e elevação
- Bolha do usuário ~18–20 dp; cards ~12–14 dp; pílulas de input/chips fully
  rounded (cápsula); **sem sombras** — profundidade por fundo mais claro +
  borda sutil (1 dp, ~`#2E2E30`) quando necessário.

### Ícones
- Outlined/rounded, 20–24 dp, cor cinza-clara; badges de letra (iniciais) em
  quadrados arredondados para sessões/modelos.

### Mensagens do chat
- Assistente full-width sem bolha; usuário em bolha `#2E2E2E` alinhada à direita
  (máx. ~82%); "Usou N ferramentas ›" como linha colapsada cinza entre blocos;
  cards de ferramenta com borda sutil; divisores temporais com rótulo central.

### Cards
- Fundo `#1C1C1E`/`#222223`, borda sutil, raio 12–14 dp, título peso médio,
  conteúdo secundário mono/cinza; ações de rodapé com chevron.

### Navegação e overlays
- Drawer lateral (não bottom nav) com busca + "Recentes"; menus de contexto como
  painel flutuante com ícones e item destrutivo vermelho; sheets para seletores
  (modelos, escopo) com busca fixa no rodapé.

### Estados
- Streaming: stop branco visível + texto renderizando em tempo real; carregamento:
  spinners discretos (nas linhas, não em overlays); vazio: mensagens curtas em
  cinza, sem ilustrações pesadas; latência/health expostos na UI (card de
  conexão).

## Divergências aceitas do app atual (a justificar em DESIGN.md)
1. O app usa drawer? Hoje as sessões são uma tela; manteremos **tela de sessões**
   (não drawer) na Frente D para não quebrar navegação existente, mas com a
   hierarquia visual das referências (badge, spinner do item ativo, contagem).
2. Brand roxo "Aurora" é **substituído** pela paleta neutra + azul discreto das
   referências (tokens atualizados na Fase 0).
3. Sem gráfico de pulso real na v1 do card de conexão — latência numérica +
   ícone de status; o gráfico fica como polimento (P2).
