package io.github.kuan1113.mdlinkkeeper

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * 整合測試：真的在一個（無畫面的）IDE 專案裡搬檔案，確認連結被自動修好。
 *
 * 這一層測的是純邏輯測不到的東西：
 *   1. BulkFileListener 到底收不收得到搬移事件
 *   2. 事件裡的新舊路徑有沒有取對
 *   3. 改寫後有沒有真的寫回檔案
 *
 * 不需要開 IDE、不需要有人點任何東西。跑法：gradle test
 */
class MoveUpdatesLinksTest : BasePlatformTestCase() {

    /** 測試環境不會自動註冊 plugin.xml 裡的監聽器，這裡手動掛上 */
    private fun subscribeUpdater() {
        com.intellij.openapi.application.ApplicationManager.getApplication()
            .messageBus.connect(testRootDisposable)
            .subscribe(VirtualFileManager.VFS_CHANGES, MarkdownLinkUpdater())
    }

    private fun textOf(path: String): String {
        val vf = myFixture.findFileInTempDir(path)
        return FileDocumentManager.getInstance().getDocument(vf)?.text
            ?: String(vf.contentsToByteArray(), Charsets.UTF_8)
    }

    fun testMovingFileUpdatesLinkInSameFolder() {
        myFixture.addFileToProject("docs/setup.md", "# 安裝說明")
        myFixture.addFileToProject("docs/guide.md", "安裝步驟見 [安裝說明](./setup.md)。")
        subscribeUpdater()

        val setup = myFixture.findFileInTempDir("docs/setup.md")
        val docs = setup.parent

        WriteAction.runAndWait<Exception> {
            val install = VfsUtil.createDirectoryIfMissing(docs, "install")
            setup.move(this, install)
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals("安裝步驟見 [安裝說明](./install/setup.md)。", textOf("docs/guide.md"))
    }

    fun testRenamingFileUpdatesLink() {
        myFixture.addFileToProject("docs/old-name.md", "# 內容")
        myFixture.addFileToProject("docs/index.md", "見 [說明](./old-name.md)")
        subscribeUpdater()

        val target = myFixture.findFileInTempDir("docs/old-name.md")
        WriteAction.runAndWait<Exception> { target.rename(this, "new-name.md") }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals("見 [說明](./new-name.md)", textOf("docs/index.md"))
    }

    fun testExternalLinksAreNeverTouched() {
        myFixture.addFileToProject("docs/setup.md", "# 安裝")
        myFixture.addFileToProject(
            "docs/guide.md",
            "[本機](./setup.md) [官網](https://jetbrains.com) [錨點](#top)"
        )
        subscribeUpdater()

        val setup = myFixture.findFileInTempDir("docs/setup.md")
        WriteAction.runAndWait<Exception> {
            val sub = VfsUtil.createDirectoryIfMissing(setup.parent, "install")
            setup.move(this, sub)
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val result = textOf("docs/guide.md")
        assertTrue("外部網址被動到了：$result", result.contains("[官網](https://jetbrains.com)"))
        assertTrue("錨點被動到了：$result", result.contains("[錨點](#top)"))
        assertTrue("本機連結沒被修好：$result", result.contains("./install/setup.md"))
    }

    fun testUnrelatedFilesAreNotModified() {
        myFixture.addFileToProject("docs/setup.md", "# 安裝")
        val untouched = "這份文件沒有任何連結指向 setup。\n\n[別的](./other.md)"
        myFixture.addFileToProject("docs/unrelated.md", untouched)
        subscribeUpdater()

        val setup = myFixture.findFileInTempDir("docs/setup.md")
        WriteAction.runAndWait<Exception> {
            val sub = VfsUtil.createDirectoryIfMissing(setup.parent, "install")
            setup.move(this, sub)
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(untouched, textOf("docs/unrelated.md"))
    }
}
