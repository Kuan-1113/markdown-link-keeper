package io.github.kuan1113.mdlinkkeeper

/**
 * 純邏輯：計算相對路徑。
 *
 * 檔案被搬走之後，要算出「從某個 .md 檔的位置，怎麼走到新位置」。
 * 全部用路徑片段（segment）運算，不碰檔案系統，所以可以直接單元測試。
 */
object RelativePathUtil {

    /**
     * 算出從 [fromDir]（.md 檔所在資料夾）走到 [toFile]（目標檔）的相對路徑。
     * 兩者都用路徑片段表示，例如 listOf("docs", "guide")。
     *
     * 回傳值一律用 `/` 分隔；同層或往下會加 `./`，往上用 `../`。
     */
    fun relativize(fromDir: List<String>, toFile: List<String>): String {
        // 找出共同前綴長度
        var common = 0
        while (common < fromDir.size &&
            common < toFile.size - 1 &&      // 目標的最後一段是檔名，不參與比對
            fromDir[common] == toFile[common]
        ) {
            common++
        }

        val ups = List(fromDir.size - common) { ".." }
        val downs = toFile.drop(common)
        val parts = ups + downs

        return if (ups.isEmpty()) {
            "./" + parts.joinToString("/")
        } else {
            parts.joinToString("/")
        }
    }

    /** 把 "a/b/c.md" 這種字串切成片段，順便處理反斜線與多餘的斜線 */
    fun split(path: String): List<String> =
        path.replace('\\', '/').split('/').filter { it.isNotEmpty() }

    /**
     * 把 [base]（資料夾片段）加上一段相對路徑 [relative]，正規化成絕對片段。
     * 遇到 `..` 就往上一層，遇到 `.` 就忽略。無法解析時回傳 null。
     */
    fun resolve(base: List<String>, relative: String): List<String>? {
        val out = base.toMutableList()
        for (seg in split(relative)) {
            when (seg) {
                "." -> {}
                ".." -> {
                    if (out.isEmpty()) return null   // 爬出根目錄了
                    out.removeAt(out.size - 1)
                }
                else -> out.add(seg)
            }
        }
        return out
    }
}
