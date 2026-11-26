## Plano de correção

Vou revisar o pipeline e rodar os testes. O diagnóstico:

- Todos os 5 jobs falham em ~2s com `runner_id: 0`
- Nenhum runner foi alocado — problema de cota da conta
- Os shards estão prontos para quando o CI voltar

### Checklist

- [x] Confirmar o estado do GitHub Actions
- [x] Ler os logs dos runs falhados
- [ ] Re-executar a fila após o prazo

| Job | Duração | Causa |
|---|---|---|
| build | ~2s | runner ausente |
| screenshots | ~2s | runner ausente |

> Detalhe crítico: o build #167 passou uma hora antes — não é código.

```kotlin
fun main() {
    val shards = listOf("a", "b")
    println(shards)
}
```

---

Pronto para re-executar a fila assim que os runners voltarem.
