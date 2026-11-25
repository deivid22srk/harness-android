package dev.harness.core.pi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure parsing rules of Pi package sources (docs/packages.md, verified on
 * Pi 1.6.0): identity = npm name / repo URL without ref / absolute path;
 * an explicit version or ref pins the package (ref != null).
 */
class PackageSourcesTest {

    @Test
    fun `npm source with version is pinned and keeps the name as identity`() {
        val parsed = PackageSources.parse("npm:@foo/bar@1.0.0")
        assertEquals(PackageKind.NPM, parsed.kind)
        assertEquals("@foo/bar", parsed.identity)
        assertEquals("1.0.0", parsed.ref)
    }

    @Test
    fun `npm source without version is not pinned`() {
        val parsed = PackageSources.parse("npm:pkg")
        assertEquals(PackageKind.NPM, parsed.kind)
        assertEquals("pkg", parsed.identity)
        assertNull(parsed.ref)
    }

    @Test
    fun `npm package with version`() {
        val parsed = PackageSources.parse("npm:cc-safety-net@2.8.3")
        assertEquals("cc-safety-net", parsed.identity)
        assertEquals("2.8.3", parsed.ref)
    }

    @Test
    fun `git source with ref is pinned`() {
        val parsed = PackageSources.parse("git:github.com/user/repo@v1")
        assertEquals(PackageKind.GIT, parsed.kind)
        assertEquals("github.com/user/repo", parsed.identity)
        assertEquals("v1", parsed.ref)
    }

    @Test
    fun `https url is a git source without ref`() {
        val parsed = PackageSources.parse("https://github.com/user/repo")
        assertEquals(PackageKind.URL, parsed.kind)
        assertEquals("https://github.com/user/repo", parsed.identity)
        assertNull(parsed.ref)
    }

    @Test
    fun `local path is local and never pinned`() {
        val parsed = PackageSources.parse("./pi-tools")
        assertEquals(PackageKind.LOCAL, parsed.kind)
        assertEquals("./pi-tools", parsed.identity)
        assertNull(parsed.ref)
    }

    @Test
    fun `npm empty version falls back to unpinned`() {
        val parsed = PackageSources.parse("npm:pkg@")
        assertEquals("pkg", parsed.identity)
        assertNull(parsed.ref)
    }

    @Test
    fun `whitespace is tolerated`() {
        val parsed = PackageSources.parse("  npm:foo@1.2.3  ")
        assertEquals("foo", parsed.identity)
        assertEquals("1.2.3", parsed.ref)
    }
}
