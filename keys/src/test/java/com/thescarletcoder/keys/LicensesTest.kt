package com.thescarletcoder.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every license in the repo ships in the APK, is listed in Settings, and is named in the README's Credits (Q8). */
class LicensesTest {
    private val built = File("build/generated/assets/copyLicenses")
    private val source = File("src/main/assets")
    private val listed = Licenses.parts.map { it.asset }

    @Test fun everyRepoLicenseIsListedAndBuilt() {
        val repo = File("../licenses").listFiles()!!.filter { it.isFile && it.extension == "txt" }.map { "licenses/${it.name}" } + "licenses/LICENSE"
        repo.forEach { path ->
            assertTrue("$path is not in Licenses.parts", path in listed)
            assertTrue("$path is missing from the APK assets", File(built, path).isFile)
        }
    }

    @Test fun everyListedLicenseShips() {
        listed.forEach { path ->
            assertTrue("$path is listed but not in the APK", File(built, path).isFile || File(source, path).isFile)
        }
    }

    private fun shipped(path: String) = File(built, path).takeIf { it.isFile } ?: File(source, path)

    @Test fun reflowJoinsWrappedLines() {
        assertEquals("One two three.\n\nFour five.", Licenses.reflow("  One two\r\n   three.\r\n\r\n   Four\nfive.\n"))
        assertEquals("PREAMBLE\n\nThe goals are these.", Licenses.reflow("----------\nPREAMBLE\nThe goals\nare these.\n"))
        assertEquals("PERMISSION & CONDITIONS\n\nPermission is granted.", Licenses.reflow("PERMISSION & CONDITIONS\nPermission is\ngranted.\n"))
        // The short last line of an all-caps paragraph stays in it.
        assertEquals("THERE IS NO WARRANTY FOR THE PROGRAM, TO THE EXTENT PERMITTED BY LAW, FOR ANY SUCH DAMAGES.",
            Licenses.reflow("THERE IS NO WARRANTY FOR THE PROGRAM, TO THE EXTENT PERMITTED BY LAW, FOR ANY\nSUCH DAMAGES.\n"))
        // Every text shown in the app keeps its words, in order (divider dashes aside).
        val words = { s: String -> s.split(Regex("\\s+")).filter { w -> w.isNotEmpty() && !w.all { it == '-' } } }
        listed.forEach { path ->
            val text = shipped(path).readText()
            assertEquals(path, words(text), words(Licenses.reflow(text)))
        }
    }

    @Test fun readmeCreditsNameEveryLicense() {
        val readme = File("../README.md").readText()
        listed.forEach { path ->
            val name = path.substringAfterLast('/')
            assertTrue("README doesn't mention $name", name in readme)
        }
        assertTrue("README doesn't name the GPL", "GNU General Public License" in readme)
    }
}
