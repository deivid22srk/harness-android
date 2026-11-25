package dev.harness.core.env

/** High-level provisioning state surfaced to the onboarding UI. */
sealed interface ProvisionState {
    data object Idle : ProvisionState

    data class Preparing(val detail: String) : ProvisionState

    data class Downloading(val fraction: Float, val detail: String) : ProvisionState

    data class Extracting(val detail: String) : ProvisionState

    data class Configuring(val detail: String) : ProvisionState

    data class InstallingPackages(val step: Int, val total: Int, val detail: String) : ProvisionState

    data class InstallingAgent(val step: Int, val total: Int, val detail: String) : ProvisionState

    data class Ready(val nodeVersion: String, val piVersion: String) : ProvisionState

    data class Failed(val message: String, val diagnostics: String?) : ProvisionState

    /** Coarse progress 0..1 for the onboarding bar. */
    val progress: Float
        get() = when (this) {
            is Idle -> 0f
            is Preparing -> 0.02f
            is Downloading -> 0.05f + fraction * 0.30f
            is Extracting -> 0.40f
            is Configuring -> 0.48f
            is InstallingPackages -> 0.50f + (step.toFloat() / total) * 0.25f
            is InstallingAgent -> 0.80f + (step.toFloat() / total) * 0.15f
            is Ready -> 1f
            is Failed -> 0f
        }

    val label: String
        get() = when (this) {
            is Idle -> "Aguardando"
            is Preparing -> detail
            is Downloading -> "Baixando ambiente"
            is Extracting -> "Extraindo ambiente"
            is Configuring -> "Configurando"
            is InstallingPackages -> "Instalando ferramentas"
            is InstallingAgent -> "Preparando o Pi"
            is Ready -> "Pronto"
            is Failed -> "Falha na preparação"
        }

    val detailText: String
        get() = when (this) {
            is Downloading -> "${(fraction * 100).toInt()}%"
            is InstallingPackages -> "etapa $step de $total"
            is InstallingAgent -> "etapa $step de $total"
            is Failed -> message
            else -> ""
        }
}
