package dev.harness.core.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AUDIT_V3 C3: provider errors must arrive in human pt-BR with an action. */
class ProviderErrorsTest {

    @Test
    fun `401 maps to review key`() {
        val t = ProviderErrors.translate("Error: 401 unauthorized — invalid api key")
        assertNotNull(t)
        assertEquals(ProviderErrors.Action.REVIEW_KEY, t!!.action)
        assertTrue(t.message.contains("chave", ignoreCase = true))
    }

    @Test
    fun `invalid_api_key from anthropic maps to review key`() {
        val t = ProviderErrors.translate("authentication_error: invalid x-api-key")
        assertNotNull(t)
        assertEquals(ProviderErrors.Action.REVIEW_KEY, t!!.action)
    }

    @Test
    fun `403 maps to review key`() {
        val t = ProviderErrors.translate("403 Forbidden — you don't have access to model X")
        assertNotNull(t)
        assertEquals(ProviderErrors.Action.REVIEW_KEY, t!!.action)
    }

    @Test
    fun `billing maps to review key`() {
        val t = ProviderErrors.translate("Your credit balance is too low to access the API")
        assertNotNull(t)
        assertEquals(ProviderErrors.Action.REVIEW_KEY, t!!.action)
    }

    @Test
    fun `429 maps to retry`() {
        val t = ProviderErrors.translate("429 Too Many Requests: rate limit exceeded")
        assertNotNull(t)
        assertEquals(ProviderErrors.Action.RETRY, t!!.action)
    }

    @Test
    fun `overloaded maps to retry`() {
        val t = ProviderErrors.translate("Overloaded (provider is temporarily saturated)")
        assertNotNull(t)
        assertEquals(ProviderErrors.Action.RETRY, t!!.action)
    }

    @Test
    fun `timeout maps to retry`() {
        val t = ProviderErrors.translate("Request timed out after 30000ms")
        assertNotNull(t)
        assertEquals(ProviderErrors.Action.RETRY, t!!.action)
    }

    @Test
    fun `network failures map to check network`() {
        for (raw in listOf(
            "fetch failed: ECONNREFUSED 127.0.0.1:8080",
            "getaddrinfo ENOTFOUND api.example.com",
            "socket hang up",
        )) {
            val t = ProviderErrors.translate(raw)
            assertNotNull("should translate: $raw", t)
            assertEquals(ProviderErrors.Action.CHECK_NETWORK, t!!.action)
        }
    }

    @Test
    fun `missing provider or key maps to configure provider`() {
        for (raw in listOf(
            "No API key provided for provider anthropic",
            "No available models for the selected provider",
            "Provider not found: custom-x",
            "Model not found: gpt-99",
        )) {
            val t = ProviderErrors.translate(raw)
            assertNotNull("should translate: $raw", t)
            assertEquals(ProviderErrors.Action.CONFIGURE_PROVIDER, t!!.action)
        }
    }

    @Test
    fun `unknown errors stay raw`() {
        assertNull(ProviderErrors.translate(null))
        assertNull(ProviderErrors.translate(""))
        assertNull(ProviderErrors.translate("something completely unrelated"))
    }

    @Test
    fun `messages are user friendly`() {
        val t = ProviderErrors.translate("401 unauthorized")!!
        assertTrue(t.message.isNotBlank())
        assertTrue(t.message.length < 220)
    }
}
