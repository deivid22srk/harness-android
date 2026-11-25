package dev.harness.core.rpc

/**
 * Translates raw provider errors coming from the Pi RPC (`response.error`,
 * model `error` events, notices) into human pt-BR messages plus a suggested
 * action. Pure and JVM-testable (AUDIT_V3 C3).
 */
object ProviderErrors {

    /** Suggested user action for a translated error. */
    enum class Action { NONE, REVIEW_KEY, RETRY, CHECK_NETWORK, CONFIGURE_PROVIDER }

    data class Translated(val message: String, val action: Action)

    fun translate(raw: String?): Translated? {
        if (raw.isNullOrBlank()) return null
        val low = raw.lowercase()

        // API key problems -------------------------------------------------
        if ("401" in low || "unauthorized" in low || "invalid api key" in low ||
            "invalid_api_key" in low || "incorrect api key" in low ||
            "x-api-key" in low || "authentication_error" in low ||
            "authentication" in low && "fail" in low
        ) {
            return Translated(
                "A chave do provedor foi recusada (401). Revise a chave em Configurações → Provedores.",
                Action.REVIEW_KEY,
            )
        }
        if ("403" in low || "forbidden" in low || "permission" in low) {
            return Translated(
                "Acesso negado pelo provedor (403). Verifique permissões e se a chave tem acesso ao modelo.",
                Action.REVIEW_KEY,
            )
        }
        if ("quota" in low || "billing" in low || "credit" in low || "crédito" in low ||
            "payment" in low || "insufficient" in low && "balance" in low
        ) {
            return Translated(
                "O provedor recusou por cota/crédito. Verifique o saldo ou o plano da conta.",
                Action.REVIEW_KEY,
            )
        }

        // Rate limits --------------------------------------------------------
        if ("429" in low || "rate limit" in low || "too many requests" in low || "overloaded" in low) {
            return Translated(
                "O provedor está sobrecarregado ou o limite de uso foi atingido (429). Aguarde alguns segundos e tente de novo.",
                Action.RETRY,
            )
        }

        // Network ------------------------------------------------------------
        if ("timeout" in low || "timed out" in low || "etimedout" in low) {
            return Translated(
                "A conexão com o provedor expirou. Verifique a internet e tente de novo.",
                Action.RETRY,
            )
        }
        if ("econnrefused" in low || "enotfound" in low || "econnreset" in low ||
            "connection refused" in low || "connection error" in low ||
            "network" in low || "fetch failed" in low || "socket hang up" in low ||
            "dns" in low
        ) {
            return Translated(
                "Não foi possível alcançar o provedor. Verifique a conexão de internet e tente de novo.",
                Action.CHECK_NETWORK,
            )
        }

        // Missing provider/model configuration --------------------------------
        if ("no provider" in low || "no api key" in low || "api key is required" in low ||
            "missing api key" in low || "no available model" in low ||
            "no models available" in low || "provider not found" in low ||
            "unknown provider" in low || "model not found" in low || "unknown model" in low
        ) {
            return Translated(
                "Nenhum provedor utilizável para este modelo. Configure um provedor para conversar com o modelo — o restante do app continua funcionando.",
                Action.CONFIGURE_PROVIDER,
            )
        }

        return null // unknown: surface raw, never invent semantics
    }
}
