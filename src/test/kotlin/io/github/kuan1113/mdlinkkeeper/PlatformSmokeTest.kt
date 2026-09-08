package io.github.kuan1113.mdlinkkeeper

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** 先確認平台測試框架本身跑得起來，再寫真正的整合測試 */
class PlatformSmokeTest : BasePlatformTestCase() {

    fun testFixtureCanCreateFile() {
        val file = myFixture.addFileToProject("docs/a.md", "# 標題\n\n[連結](./b.md)")
        assertNotNull(file)
        assertEquals("a.md", file.name)
        assertTrue(file.text.contains("[連結](./b.md)"))
    }
}
