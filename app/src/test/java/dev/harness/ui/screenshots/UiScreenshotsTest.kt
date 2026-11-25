package dev.harness.ui.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.harness.PackagesUiState
import dev.harness.core.pi.ConnectionState
import dev.harness.core.state.DiffLine
import dev.harness.core.state.DiffSummary
import dev.harness.core.state.ToolItem
import dev.harness.core.state.ToolStatus
import dev.harness.ui.components.ConnectionSheet
import dev.harness.ui.components.HMarkdown
import dev.harness.ui.components.ToolCallCard
import dev.harness.ui.components.DiffView
import dev.harness.ui.theme.HarnessThemeProvider
import dev.harness.ui.theme.Space
import dev.harness.ui.screens.InstalledPackagesSection
import dev.harness.ui.screens.NoProviderBanner
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Evidência visual no CI (PROMPT §8): renders production composables with
 * fixture data and saves PNGs to app/build/screenshots as
 * `<tela>_<estado>_<tema>_<fonte>.png`. Runs only when HARNESS_SCREENSHOTS=1
 * (the dedicated CI job); in the regular unit-test run it no-ops to stay fast.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "+w412dp-h892dp", sdk = [34])
class UiScreenshotsTest {

    // Robolectric sandboxes System.getenv — the Gradle test config forwards the
    // toggle as system properties (see app/build.gradle.kts testOptions).
    private val enabled: Boolean = System.getProperty("harness.screenshots") == "1"
    private val outDir = System.getProperty("harness.screenshots.dir") ?: "build/screenshots"

    private fun capture(name: String, dark: Boolean, fontScale: Float, content: @Composable () -> Unit) {
        if (!enabled) return
        val path = File(outDir).apply { mkdirs() }
            .resolve("$name${if (dark) "_dark" else "_light"}_${if (fontScale == 1f) "1.0" else "1.3"}.png")
            .absolutePath
        captureRoboImage(path) {
            HarnessThemeProvider(darkTheme = dark, dynamicColor = false, fontScale = fontScale) {
                Box(Modifier.fillMaxSize().background(dev.harness.ui.theme.HarnessTheme.background)) {
                    content()
                }
            }
        }
    }

    private fun allVariants(name: String, content: @Composable () -> Unit) {
        capture(name, dark = true, fontScale = 1f, content = content)
        capture(name, dark = false, fontScale = 1f, content = content)
        capture(name, dark = true, fontScale = 1.3f, content = content)
        capture(name, dark = false, fontScale = 1.3f, content = content)
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private val richMarkdown = """
        ## Plano de correção

        Vou **cancelar a compilação local** e mandar pro GitHub. Aqui está o diagnóstico:

        - Todos os 5 jobs falham em ~2s com `runner_id: 0`
        - Nenhum runner foi alocado — problema de cota da conta
        - Os shards estão `safely in place` para quando o CI voltar

        ### Checklist

        1. Cancelar processos locais
        2. Confirmar estado do GitHub
        3. Fazer commit e push

        > Detalhe crítico: o build #167 passou uma hora antes — não é código.

        | Job | Duração | Causa |
        |---|---|---|
        | build | ~2s | runner ausente |
        | game_so | ~2s | runner ausente |

        ```kotlin
        fun main() {
            val shards = listOf("a", "b")
            println("uploaded ${"$"}shards")
        }
        ```

        ```diff
        - val retries = 0
        + val retries = 3
        ```

        Veja a [documentação](https://example.com/docs) e `runtime/src/dispatch.rs` para detalhes.
    """.trimIndent()

    private val streamingOpenFence = """
        Agora vou corrigir o epílogo da função. O código fica assim:

        ```rust
        pub unsafe extern "C" fn gow_store(cpu: *mut GowCpu, addr: u64, v: u64, size: u32) {
            if !plausible_mem(addr, size as u64) {
                let c = &*cpu;
    """.trimIndent()

    private val toolOk = ToolItem(
        id = "t1",
        toolCallId = "c1",
        name = "edit",
        summary = "runtime/src/dispatch.rs",
        status = ToolStatus.Ok,
        output = "Edited runtime/src/dispatch.rs (1 replacement)",
        diff = DiffSummary(
            path = "runtime/src/dispatch.rs",
            added = 3,
            removed = 1,
            previewLines = listOf(
                DiffLine(DiffLine.Kind.Header, "@@ gow_store"),
                DiffLine(DiffLine.Kind.Context, "let c = &*cpu;"),
                DiffLine(DiffLine.Kind.Removed, "let r = cpu.read(0x1000);"),
                DiffLine(DiffLine.Kind.Added, "let r = cpu.read(c.rip);"),
                DiffLine(DiffLine.Kind.Added, "c.rip.wrapping_sub(c.image_base);"),
            ),
        ),
        durationMs = 820,
    )

    private val toolError = ToolItem(
        id = "t2",
        toolCallId = "c2",
        name = "bash",
        summary = "pkill -f build_game_local.sh 2>/dev/null",
        status = ToolStatus.Error,
        output = "(no output)\nExit code: 143 (SIGTERM)",
        durationMs = 412,
    )

    private val toolRunning = ToolItem(
        id = "t3",
        toolCallId = "c3",
        name = "webfetch",
        summary = "https://www.githubstatus.com/api/v2/summary.json",
        status = ToolStatus.Running,
        durationMs = null,
    )

    // ------------------------------------------------------------------
    // Scenarios (§8)
    // ------------------------------------------------------------------

    @Test
    fun chat_markdown_rico() {
        allVariants("chat_markdown-rico") {
            Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                HMarkdown(richMarkdown)
            }
        }
    }

    @Test
    fun chat_streaming_cerca_aberta() {
        allVariants("chat_streaming-cerca-aberta") {
            Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                HMarkdown(streamingOpenFence)
            }
        }
    }

    @Test
    fun chat_toolcall_sucesso_erro() {
        allVariants("chat_toolcall-sucesso-erro") {
            Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                ToolCallCard(toolOk)
                ToolCallCard(toolError)
                ToolCallCard(toolRunning)
                DiffView(toolOk.diff!!)
            }
        }
    }

    @Test
    fun chat_sem_provedor() {
        allVariants("chat_sem-provedor") {
            Column(Modifier.fillMaxWidth()) {
                NoProviderBanner(onConfigure = {})
                Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                    HMarkdown("O agente está **conectado**. Configure um provedor para conversar com o modelo.")
                }
            }
        }
    }

    @Test
    fun conexao_diagnostico() {
        allVariants("conexao-diagnostico") {
            Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                ConnectionSheet(
                    state = ConnectionState.Connected,
                    piVersion = "1.6.0 (node 22.19.0)",
                    uptime = "4m 12s",
                    lastError = null,
                    stderrTail = "[pi] rpc mode iniciado\n[pi] session loaded: --root--",
                    providerTest = "O agente está em execução e respondeu ao RPC.\nChave presente para \"anthropic\".",
                    onTestProvider = {},
                    onRestart = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun pacotes_instalados() {
        val state = PackagesUiState(
            loading = false,
            global = listOf(
                dev.harness.core.pi.InstalledPackage(
                    source = "npm:cc-safety-net@2.8.3",
                    scope = dev.harness.core.pi.PackageScope.GLOBAL,
                    kind = dev.harness.core.pi.PackageKind.NPM,
                    identity = "cc-safety-net",
                    ref = "2.8.3",
                    pinned = true,
                    features = setOf(dev.harness.core.pi.PackageFeature.EXTENSIONS),
                    description = "Safety net extension",
                ),
            ),
            projectPackages = emptyList(),
        )
        allVariants("pacotes-instalados") {
            Column(Modifier.fillMaxWidth().padding(Space.lg)) {
                InstalledPackagesSection(
                    packagesState = state,
                    onRemove = {},
                    onUpdateOne = {},
                )
            }
        }
    }
}
