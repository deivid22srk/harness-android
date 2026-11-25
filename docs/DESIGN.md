# Design — Harness

Referência visual: **OpenCode** (dark, minimalista, "ferramenta de dev", não
"app de chatbot") + as imagens anexadas ao prompt (paleta escura quase preta,
chamadas de ferramenta compactas e expansíveis, composer fixo, chips de modelo
e raciocínio, sem terminal visível).

## 1. Tokens

Implementados em `ui/theme/Theme.kt` (`HarnessColors` + `LocalHarnessColors`).

### Cor

| Token | Escuro | Claro |
|---|---|---|
| `background` | `#0A0A0A` | `#FAFAFA` |
| `surface` | `#111111` | `#FFFFFF` |
| `surfaceElevated` | `#171717` | `#F2F2F2` |
| `border` | `#1F1F1F` | `#E4E4E4` |
| `textPrimary` | `#EDEDED` | `#141414` |
| `textSecondary` | `#8A8A8A` | `#6A6A6A` |
| `textTertiary` | `#5C5C5C` | `#9A9A9A` |
| `accent` | `#E8E8E8` | `#1A1A1A` |
| `accentMuted` | `#2A2A2A` | `#ECECEC` |
| `codeBackground` | `#161616` | `#F1F1F1` |
| `toolRunning` | `#C79A4A` | `#9A6B1E` |
| `toolOk` | `#5E9E6E` | `#2F7A45` |
| `toolError` | `#C4665E` | `#B23B33` |

**Um único acento** (neutro). Os três estados de ferramenta são a única cor
semântica. Sem Material You dinâmico.

### Espaçamento (`Space`)

`xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32` (dp)

### Raios (`Radii`)

`sm 8 · md 12 · lg 16 · pill 100` (dp)

### Tipografia

Sans do sistema para UI; `FontFamily.Monospace` para código, diffs, saída e
metadados. Corpo a 15 sp / 22 sp de entrelinha. Escala enxuta: `bodyLarge`,
`bodyMedium`, `bodySmall`, `labelLarge`, `titleMedium`, `headlineSmall`.

## 2. Componentes

| Componente | Regra |
|---|---|
| Mensagem do usuário | Bloco sutil, alinhado à direita, borda + raio `lg`. Nunca balão chamativo. |
| Resposta do assistente | Texto limpo, **sem balão**, largura total. Markdown leve. |
| Raciocínio | Linha "Raciocínio" colapsável (mono, itálico). |
| Chamada de ferramenta | Linha compacta: chevron + ponto de estado + rótulo + resumo mono + `+N/−N` + duração. Toca para expandir (diff/saída). |
| Grupo de ferramentas | Cartão `surface` com borda `md`; cabeçalho "Usou N ferramenta(s)". |
| Bloco de código | Painel `codeBackground`, botão "Copiar", rolagem horizontal, sem quebra. |
| Composer | Fixo embaixo, multilinha, chips (Build/Modelo/Raciocínio) acima, botão enviar que **vira parar**. |
| Barra superior | Projeto + sessão + modelo + indicador de conexão; nova sessão, configurações, voltar. |
| Diálogos de extensão | Bottom sheets / diálogos nativos. |

## 3. Movimento

Transições curtas (150–200 ms), pulso sutil no indicador de "pensando",
`AnimatedVisibility` para expandir. Respeita "reduzir animações" do sistema.

## 4. Acessibilidade

Áreas de toque ≥ 48 dp nos alvos principais, contraste alto no tema escuro,
conteúdo descritivo em ícones quando há significado (`contentDescription`).

## 5. Layout

Edge-to-edge com `WindowInsets.safeDrawing`/`statusBars` e `imePadding`.
Coluna única (chat), adaptável a retrato/paisagem/tablet. Idiomas: pt-BR e en
(strings em pt-BR; estrutura pronta para tradução).

## 6. Estados de ferramenta

| Estado | Cor | Uso |
|---|---|---|
| Executando | `toolRunning` | durante o `tool_execution_start → end` |
| Ok | `toolOk` | `tool_execution_end` sem erro |
| Erro | `toolError` | `isError: true` |
| Cancelado | `textTertiary` | `agent_settled` com ferramenta pendente |
