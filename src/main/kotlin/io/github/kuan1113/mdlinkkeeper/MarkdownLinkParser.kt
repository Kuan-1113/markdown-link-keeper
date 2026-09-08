package io.github.kuan1113.mdlinkkeeper

/**
 * 純邏輯：Markdown 連結的「找出來」與「改寫」。
 *
 * 刻意不依賴任何 IntelliJ API —— 這樣就能用普通單元測試驗證，
 * 不必開 IDE、不必有人點選單。
 * 「這個檔案存不存在」「檔案被搬到哪」由呼叫端負責（那部分才需要 IDE）。
 */
object MarkdownLinkParser {

    /** 一個指向本機檔案的連結 */
    data class LocalLink(
        val line: Int,         // 行號，從 1 開始
        val label: String,     // [] 裡面顯示的文字
        val rawTarget: String, // () 裡面的原始內容，可能含 #錨點
        val path: String,      // 去掉錨點、解過編碼的實際路徑
    ) {
        /** 原本帶的 #錨點（含 #），沒有就是空字串 */
        val anchor: String
            get() = rawTarget.indexOf('#').let { if (it >= 0) rawTarget.substring(it) else "" }
    }

    // [文字](路徑) / [文字](路徑 "標題") / [文字](<有空格的 路徑>)
    // 群組：1=文字  2=角括號路徑  3=一般路徑
    private val LINK = Regex("""\[([^\]]*)]\(\s*(?:<([^>]+)>|([^)\s]+))(?:\s+"[^"]*")?\s*\)""")

    private val SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*:""")

    /** 找出所有指向本機檔案的連結（會略過外部網址、錨點、程式碼區塊） */
    fun findLocalLinks(text: String): List<LocalLink> {
        val out = mutableListOf<LocalLink>()
        forEachLocalLink(text) { link, _, _ -> out += link; null }
        return out
    }

    /**
     * 改寫連結。[newPathFor] 回傳新路徑就替換，回傳 null 就原封不動。
     * 只動路徑本身，標題、錨點、周圍文字全部保留。
     */
    fun rewriteLocalLinks(text: String, newPathFor: (LocalLink) -> String?): String {
        val lines = text.split("\n").toMutableList()
        val edits = mutableMapOf<Int, MutableList<Triple<Int, Int, String>>>()

        forEachLocalLink(text) { link, start, end ->
            newPathFor(link)?.let { newPath ->
                edits.getOrPut(link.line - 1) { mutableListOf() }
                    .add(Triple(start, end, newPath + link.anchor))
            }
            null
        }

        edits.forEach { (lineIdx, replacements) ->
            var line = lines[lineIdx]
            // 由後往前替換，才不會弄亂前面的位置
            replacements.sortedByDescending { it.first }.forEach { (s, e, text) ->
                line = line.substring(0, s) + text + line.substring(e)
            }
            lines[lineIdx] = line
        }
        return lines.joinToString("\n")
    }

    /** 共用的走訪邏輯：處理程式碼區塊、過濾外部連結，回報每個本機連結與它在該行的位置 */
    private fun forEachLocalLink(
        text: String,
        visit: (link: LocalLink, targetStart: Int, targetEnd: Int) -> Unit?,
    ) {
        var inFence = false
        text.split("\n").forEachIndexed { idx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence
                return@forEachIndexed
            }
            if (inFence) return@forEachIndexed

            LINK.findAll(line).forEach { m ->
                val angle = m.groups[2]
                val plain = m.groups[3]
                val group = angle ?: plain ?: return@forEach
                val target = group.value
                if (target.isBlank() || isExternalOrAnchor(target)) return@forEach

                val path = decode(target.substringBefore('#'))
                if (path.isBlank()) return@forEach

                visit(
                    LocalLink(idx + 1, m.groupValues[1], target, path),
                    group.range.first,
                    group.range.last + 1,
                )
            }
        }
    }

    /** 外部網址、mailto、純錨點、絕對路徑 —— 這些不是「本機相對檔案」 */
    fun isExternalOrAnchor(target: String): Boolean =
        target.startsWith("#") ||
            target.startsWith("/") ||
            target.startsWith("\\") ||
            SCHEME.containsMatchIn(target)

    /** 處理路徑裡的百分號編碼（目前只處理最常見的空格） */
    fun decode(path: String): String = path.replace("%20", " ")

    /** 反向：把空格編碼回去，寫進 Markdown 時用 */
    fun encode(path: String): String = path.replace(" ", "%20")
}
