package io.github.kuan1113.mdlinkkeeper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 「搬檔案時自動修好連結」的核心邏輯測試。
 * 跑法：gradle test —— 不需要開 IDE。
 */
class LinkRewriteTest {

    // ---------------- 相對路徑計算 ----------------

    private fun rel(from: String, to: String) =
        RelativePathUtil.relativize(RelativePathUtil.split(from), RelativePathUtil.split(to))

    @Test
    @DisplayName("同一層資料夾")
    fun sameDirectory() {
        assertEquals("./a.md", rel("docs", "docs/a.md"))
    }

    @Test
    @DisplayName("往下一層")
    fun intoSubdirectory() {
        assertEquals("./guide/a.md", rel("docs", "docs/guide/a.md"))
    }

    @Test
    @DisplayName("往上一層")
    fun upOneLevel() {
        assertEquals("../a.md", rel("docs/guide", "docs/a.md"))
    }

    @Test
    @DisplayName("往上兩層")
    fun upTwoLevels() {
        assertEquals("../../a.md", rel("docs/guide/deep", "docs/a.md"))
    }

    @Test
    @DisplayName("先上後下（兄弟資料夾）")
    fun siblingDirectory() {
        assertEquals("../api/ref.md", rel("docs/guide", "docs/api/ref.md"))
    }

    @Test
    @DisplayName("完全沒有共同祖先")
    fun noCommonAncestor() {
        assertEquals("../../x/y.md", rel("a/b", "x/y.md"))
    }

    @Test
    @DisplayName("從根目錄出發")
    fun fromRoot() {
        assertEquals("./docs/a.md", rel("", "docs/a.md"))
    }

    // ---------------- 路徑解析 ----------------

    @Test
    @DisplayName("解析 ../ 會往上一層")
    fun resolveParent() {
        assertEquals(
            listOf("docs", "a.md"),
            RelativePathUtil.resolve(listOf("docs", "guide"), "../a.md")
        )
    }

    @Test
    @DisplayName("解析 ./ 會被忽略")
    fun resolveCurrent() {
        assertEquals(
            listOf("docs", "a.md"),
            RelativePathUtil.resolve(listOf("docs"), "./a.md")
        )
    }

    @Test
    @DisplayName("爬出根目錄要回傳 null，不要爆炸")
    fun resolveBeyondRoot() {
        assertNull(RelativePathUtil.resolve(listOf("docs"), "../../../x.md"))
    }

    @Test
    @DisplayName("反斜線也要能處理（Windows 路徑）")
    fun handlesBackslash() {
        assertEquals(listOf("docs", "guide"), RelativePathUtil.split("docs\\guide"))
    }

    // ---------------- 連結改寫 ----------------

    @Test
    @DisplayName("改寫指定的連結，其他原封不動")
    fun rewritesOnlyMatchingLink() {
        val md = "看 [安裝](./setup.md) 和 [教學](./guide.md)。"
        val out = MarkdownLinkParser.rewriteLocalLinks(md) {
            if (it.path == "./setup.md") "../install/setup.md" else null
        }
        assertEquals("看 [安裝](../install/setup.md) 和 [教學](./guide.md)。", out)
    }

    @Test
    @DisplayName("改寫時要保留錨點")
    fun preservesAnchor() {
        val md = "[章節](./a.md#section-2)"
        val out = MarkdownLinkParser.rewriteLocalLinks(md) { "../b/a.md" }
        assertEquals("[章節](../b/a.md#section-2)", out)
    }

    @Test
    @DisplayName("改寫時要保留連結標題")
    fun preservesTitle() {
        val md = """[文字](./a.md "這是標題")"""
        val out = MarkdownLinkParser.rewriteLocalLinks(md) { "./sub/a.md" }
        assertEquals("""[文字](./sub/a.md "這是標題")""", out)
    }

    @Test
    @DisplayName("同一行多個連結都要改對")
    fun rewritesMultipleOnOneLine() {
        val md = "[一](a.md) 和 [二](b.md) 和 [三](c.md)"
        val out = MarkdownLinkParser.rewriteLocalLinks(md) {
            when (it.path) {
                "a.md" -> "x/a.md"
                "c.md" -> "y/c.md"
                else -> null
            }
        }
        assertEquals("[一](x/a.md) 和 [二](b.md) 和 [三](y/c.md)", out)
    }

    @Test
    @DisplayName("多行文件改寫後其他行不受影響")
    fun preservesOtherLines() {
        val md = "# 標題\n\n[連結](./old.md)\n\n結尾文字"
        val out = MarkdownLinkParser.rewriteLocalLinks(md) { "./new.md" }
        assertEquals("# 標題\n\n[連結](./new.md)\n\n結尾文字", out)
    }

    @Test
    @DisplayName("外部網址絕對不能被改寫")
    fun neverRewritesExternal() {
        val md = "[官網](https://jetbrains.com) [信箱](mailto:a@b.com) [錨點](#top)"
        val out = MarkdownLinkParser.rewriteLocalLinks(md) { "SHOULD_NEVER_APPEAR" }
        assertEquals(md, out)
    }

    @Test
    @DisplayName("程式碼區塊裡的連結絕對不能被改寫")
    fun neverRewritesInsideCodeFence() {
        val md = "```\n[範例](./a.md)\n```\n[真的](./a.md)"
        val out = MarkdownLinkParser.rewriteLocalLinks(md) { "./new.md" }
        assertEquals("```\n[範例](./a.md)\n```\n[真的](./new.md)", out)
    }

    @Test
    @DisplayName("沒有任何要改的東西時，內容必須完全相同")
    fun noChangesMeansIdenticalOutput() {
        val md = "# 標題\n\n[連結](./a.md)\n\n結尾"
        assertEquals(md, MarkdownLinkParser.rewriteLocalLinks(md) { null })
    }

    // ---------------- 端對端：搬檔案的完整情境 ----------------

    @Test
    @DisplayName("情境：把 setup.md 從 docs/ 搬到 docs/install/，指向它的連結要被修好")
    fun endToEndFileMove() {
        // docs/guide.md 內容，裡面指向 docs/setup.md
        val guideDir = RelativePathUtil.split("docs")
        val md = "安裝步驟見 [安裝說明](./setup.md)。"

        // setup.md 從 docs/setup.md 搬到 docs/install/setup.md
        val oldPath = RelativePathUtil.split("docs/setup.md")
        val newPath = RelativePathUtil.split("docs/install/setup.md")

        val out = MarkdownLinkParser.rewriteLocalLinks(md) { link ->
            val resolved = RelativePathUtil.resolve(guideDir, link.path)
            if (resolved == oldPath) RelativePathUtil.relativize(guideDir, newPath) else null
        }

        assertEquals("安裝步驟見 [安裝說明](./install/setup.md)。", out)
    }

    @Test
    @DisplayName("情境：從子資料夾指向被搬走的檔案，要算出正確的 ../ 路徑")
    fun endToEndMoveFromSubdirectory() {
        // docs/guide/intro.md 指向 docs/setup.md（寫成 ../setup.md）
        val introDir = RelativePathUtil.split("docs/guide")
        val md = "見 [安裝](../setup.md)"

        val oldPath = RelativePathUtil.split("docs/setup.md")
        val newPath = RelativePathUtil.split("install/setup.md")   // 搬到專案根的 install/

        val out = MarkdownLinkParser.rewriteLocalLinks(md) { link ->
            val resolved = RelativePathUtil.resolve(introDir, link.path)
            if (resolved == oldPath) RelativePathUtil.relativize(introDir, newPath) else null
        }

        assertEquals("見 [安裝](../../install/setup.md)", out)
    }
}
