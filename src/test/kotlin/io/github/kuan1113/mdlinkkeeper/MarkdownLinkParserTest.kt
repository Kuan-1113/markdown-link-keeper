package io.github.kuan1113.mdlinkkeeper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 這些測試不需要開 IDE、不需要有人點選單。
 * 跑法：gradle test
 */
class MarkdownLinkParserTest {

    private fun targetsOf(md: String) =
        MarkdownLinkParser.findLocalLinks(md).map { it.path }

    // ---------- 應該被找出來的 ----------

    @Test
    @DisplayName("基本的相對路徑連結會被找到")
    fun findsBasicRelativeLink() {
        val links = MarkdownLinkParser.findLocalLinks("看 [安裝說明](./setup.md) 這份。")
        assertEquals(1, links.size)
        assertEquals("./setup.md", links[0].path)
        assertEquals("安裝說明", links[0].label)
        assertEquals(1, links[0].line)
    }

    @Test
    @DisplayName("同一行有多個連結，全部都要找到")
    fun findsMultipleLinksOnSameLine() {
        assertEquals(
            listOf("a.md", "b.md"),
            targetsOf("[一](a.md) 和 [二](b.md)")
        )
    }

    @Test
    @DisplayName("行號要正確")
    fun reportsCorrectLineNumber() {
        val md = "第一行\n\n第三行有 [連結](x.md)"
        assertEquals(3, MarkdownLinkParser.findLocalLinks(md).single().line)
    }

    @Test
    @DisplayName("錨點要被去掉，只留檔案路徑")
    fun stripsAnchor() {
        val link = MarkdownLinkParser.findLocalLinks("[章節](guide/intro.md#section-2)").single()
        assertEquals("guide/intro.md", link.path)
        assertEquals("guide/intro.md#section-2", link.rawTarget)
    }

    @Test
    @DisplayName("%20 要還原成空格")
    fun decodesPercentTwenty() {
        assertEquals(listOf("my notes.md"), targetsOf("[筆記](my%20notes.md)"))
    }

    @Test
    @DisplayName("連結後面帶標題也要正確解析")
    fun handlesLinkTitle() {
        assertEquals(listOf("a.md"), targetsOf("""[文字](a.md "這是標題")"""))
    }

    @Test
    @DisplayName("角括號包起來的路徑（含空格）也要處理")
    fun handlesAngleBracketPath() {
        assertEquals(listOf("my file.md"), targetsOf("[文字](<my file.md>)"))
    }

    @Test
    @DisplayName("上層路徑要被找到")
    fun findsParentPath() {
        assertEquals(listOf("../index.md"), targetsOf("[回首頁](../index.md)"))
    }

    // ---------- 不該被找出來的 ----------

    @Test
    @DisplayName("http / https 外部網址要跳過")
    fun skipsHttpLinks() {
        assertTrue(targetsOf("[官網](https://jetbrains.com) [另一個](http://a.com/x.md)").isEmpty())
    }

    @Test
    @DisplayName("mailto 要跳過")
    fun skipsMailto() {
        assertTrue(targetsOf("[寄信](mailto:a@b.com)").isEmpty())
    }

    @Test
    @DisplayName("純錨點要跳過")
    fun skipsPureAnchor() {
        assertTrue(targetsOf("[回到頂端](#top)").isEmpty())
    }

    @Test
    @DisplayName("絕對路徑要跳過")
    fun skipsAbsolutePath() {
        assertTrue(targetsOf("[絕對](/etc/hosts)").isEmpty())
    }

    @Test
    @DisplayName("程式碼區塊裡的連結不算數")
    fun skipsFencedCodeBlock() {
        val md = """
            正常的 [連結](real.md)

            ```markdown
            這是範例 [假連結](fake.md)
            ```

            結束
        """.trimIndent()
        assertEquals(listOf("real.md"), targetsOf(md))
    }

    @Test
    @DisplayName("~~~ 圍起來的區塊也要跳過")
    fun skipsTildeFence() {
        val md = "~~~\n[假的](fake.md)\n~~~\n[真的](real.md)"
        assertEquals(listOf("real.md"), targetsOf(md))
    }

    @Test
    @DisplayName("沒有連結時回傳空清單，不要爆炸")
    fun handlesNoLinks() {
        assertTrue(MarkdownLinkParser.findLocalLinks("# 標題\n\n只是普通文字。").isEmpty())
    }

    @Test
    @DisplayName("空字串不要爆炸")
    fun handlesEmptyInput() {
        assertTrue(MarkdownLinkParser.findLocalLinks("").isEmpty())
    }

    // ---------- 對照真實測試檔的預期結果 ----------

    @Test
    @DisplayName("test-docs/guide/cases.md 的內容應該產出 5 個本機連結候選")
    fun matchesRealFixtureExpectation() {
        val md = """
            # 完整測試案例

            1. [搬走的安裝說明](./setup.md)
            2. [不存在的資料夾](./missing/page.md)
            3. [上一層不存在的檔](../outside.md)
            4. [帶錨點的壞連結](./gone.md#section)
            5. [帶空格的檔名](./my%20notes.md)

            - [同資料夾的 intro](./intro.md)
            - [上一層的 index](../index.md)
            - [外部網址](https://www.jetbrains.com)
            - [另一個外部網址](http://example.com/a.md)
            - [信箱](mailto:someone@example.com)
            - [純錨點](#完整測試案例)
        """.trimIndent()

        // 7 個本機連結候選：5 個壞的 + intro.md + index.md（後兩個實際存在，由呼叫端判斷）
        val local = MarkdownLinkParser.findLocalLinks(md)
        assertEquals(7, local.size)

        // 4 個外部/錨點連結全部被排除
        assertTrue(local.none { it.path.startsWith("http") })
        assertTrue(local.none { it.path.contains("mailto") })
    }
}
