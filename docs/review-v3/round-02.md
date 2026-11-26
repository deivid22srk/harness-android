# Rodada 02 — Frente B (Chat bonito: renderização e streaming)

> Base auditada: `647a08f` + `5ebde74`. CI de referência: **run #73** (verde 4/4;
> o job de screenshots exigiu re-run por falha transiente de resolução de deps,
> `ModuleVersionResolveException` — o job do APK passou com as mesmas dependências).

## 1. Evidência por causa-raiz (AUDIT_V3 §2)

### M1 — Parser markdown completo ✅
- `HMarkdown.kt`: `MdNode.Table` (GFM: cabeçalho + separador `|---|`, células com `\|`
  escapado, linhas ragged normalizadas), `MdNode.HorizontalRule` (`---`/`***`/`___`),
  `ListItem.depth` (aninhamento por indentação de 2 espaços, até 3 níveis),
  `ListItem.taskDone` (`- [x]`/`- [ ]`). Tabela nunca confundida com hr
  (separador exige linha-candidato acima); tabela não fechada no streaming renderiza.
- Testes: 9 novos casos em `MarkdownParserTest.kt` + fixture real
  `app/src/test/resources/markdown/agent-typical.md`. Validação adicional do parser
  com kotlinc standalone (todas as expectativas verdes) antes do push.
- Evidência visual: `chat_markdown-rico_*` mostra tabela, tasks ✓/○, aninhamento, hr
  e cercas — sem nenhum `|---|` cru.

### M2 — Streaming estável ✅
- Blocos com identidade estável: `key(idx)` por nó (`HMarkdown.kt`, renderer) —
  blocos completos não re-executam trabalho; só a cauda muda.
- Realce **debounced** para corpos grandes: `HCodeBlock` calcula o primeiro frame
  imediatamente e re-realça com `delay(150)` para ≥400 caracteres
  (`HCodeBlock.kt:186-199`). O re-parse de blocos é O(linhas) por tick (leve).

### M3 — Rodapé por resposta ✅
- `ChatItem.AssistantMessage` ganha `tsMs` (carimbado no reducer no início do turno)
  e `usage` (registrado no `Done` e preservado no `MessageEnd`)
  (`ChatState.kt:30-45`, `ChatReducer.kt:32-36,133-137,154-164`).
- Rodapé discreto `modelo · tok · HH:mm` (`MessageFooter`, ChatScreen.kt:694-724),
  oculto para histórico sem carimbo.

### M4 — Divisores temporais e ações ✅
- Divisor "Hoje às HH:mm" (hairline—rótulo—hairline) entre turnos com gap > 5 min,
  formato `dd/MM HH:mm` em outro dia (`TimeDivider`, ChatScreen.kt:736-757).
- Ações por mensagem: copiar, **compartilhar** (novo, share sheet) e regenerar
  (última resposta) — `ChatScreen.kt:674-686`.

### M5 — Blocos de código ✅
- Cercas `diff`/`patch` com cores de linha (+ verde / − vermelho / @@ accent)
  (`highlightDiff`, HCodeBlock.kt:148-171).
- Toggle de quebra de linha (⇥/⇤) no cabeçalho do bloco: alterna
  rolagem horizontal ↔ soft wrap (`HCodeBlock.kt:222-239,241-254`).

## 2. Avaliação do Crítico

| Critério | Peso | Nota | Justificativa |
|---|---|---|---|
| Conexão | 18 | 9,4 | Mantém a Rodada 1 (estado "Gerando" ainda não distinto no indicador). |
| Chat | 18 | **9,3** | M1–M5 entregues com teste + screenshot. Perde 0,7: sem numeração de linhas opcional (P2), tool calls/rich errors por menu de contexto substituído por actions row coerente com o app. |
| Pacotes | 14 | 8,2 | Inalterado (P1–P3 pendentes, Rodada 3). |
| Visual | 14 | 9,0 | Tabela/quote/código agora seguem a hierarquia das refs. |
| UX | 8 | 8,8 | Divisores + rodapé aumentam orientação temporal. |
| Performance | 8 | 8,8 | key + debounce eliminam re-realce por tick; re-parse O(linhas). |
| Arquitetura | 8 | 9,3 | Parser puro JVM-testável; reducer com campos novos cobertos por suíte verde. |
| Acessibilidade | 4 | 9,0 | Strings novas nos 2 locales; glyphs de task com cor + forma (não só cor). |
| Build/CI | 8 | 9,4 | Verde; re-run documentado (falha transiente, não de código). |
| **Global ponderado** | | **9,0** | REPROVADO — pacotes (8,2) e conexão (estado Gerando) puxam abaixo de 9,5. Zero P0/P1. |

**Veredito da Frente B: APROVADO.** **Veredito global: REPROVADO (9,0)** — Rodada 3
(Frente C: pacotes) é o caminho de maior ganho pendente.

## 3. Observações (P2)
1. Numeração de linhas opcional nos code blocks — adiar (dúvida de valor x custo).
2. Screenshot fixture continua em EN para strings de app (fixar pt-BR na Rodada 4).
