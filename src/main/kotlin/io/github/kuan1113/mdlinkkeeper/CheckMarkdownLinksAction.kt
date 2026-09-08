package io.github.kuan1113.mdlinkkeeper

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

/**
 * 掃描專案裡所有 .md 檔，找出指向「不存在的本機檔案」的連結。
 *
 * 連結的解析邏輯全部在 [MarkdownLinkParser]（純邏輯、有單元測試涵蓋）。
 * 這個類別只負責兩件 IDE 才做得到的事：走訪專案檔案、判斷目標檔存不存在。
 *
 * 為什麼跑在背景執行緒：同類的 Requirements 插件（297 萬下載）
 * 最常見的負評就是「freezes my UI」，被抱怨三年沒修。不要重蹈覆轍。
 */
class CheckMarkdownLinksAction : AnAction() {

    private data class BrokenLink(
        val sourceFile: String,
        val line: Int,
        val label: String,
        val target: String,
    )

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, "檢查 Markdown 連結", true) {
                override fun run(indicator: ProgressIndicator) {
                    val broken = ReadAction
                        .nonBlocking<List<BrokenLink>> { scan(project, indicator) }
                        .executeSynchronously()
                    ApplicationManager.getApplication().invokeLater {
                        showResult(project, broken)
                    }
                }
            }
        )
    }

    private fun showResult(project: Project, broken: List<BrokenLink>) {
        if (broken.isEmpty()) {
            Messages.showInfoMessage(project, "掃描完成，沒有發現壞掉的連結。", "Markdown 連結檢查")
            return
        }

        val report = buildString {
            append("找到 ${broken.size} 個壞掉的連結：\n\n")
            broken.take(40).forEach {
                append("${it.sourceFile}:${it.line}\n")
                append("    [${it.label}] → ${it.target}\n\n")
            }
            if (broken.size > 40) append("（另有 ${broken.size - 40} 個未列出）")
        }
        Messages.showWarningDialog(project, report, "Markdown 連結檢查")
    }

    private fun scan(project: Project, indicator: ProgressIndicator): List<BrokenLink> {
        val results = mutableListOf<BrokenLink>()
        var scanned = 0

        ProjectFileIndex.getInstance(project).iterateContent { file ->
            indicator.checkCanceled()
            if (!file.isDirectory && file.extension.equals("md", ignoreCase = true)) {
                indicator.text = file.name
                results += checkFile(file)
                scanned++
            }
            true
        }
        indicator.text = "已掃描 $scanned 個檔案"
        return results
    }

    private fun checkFile(file: VirtualFile): List<BrokenLink> {
        val text = try {
            String(file.contentsToByteArray(), Charsets.UTF_8)
        } catch (ex: Exception) {
            return emptyList()   // 讀不到就跳過，不要讓整個掃描中斷
        }
        val parent = file.parent ?: return emptyList()

        // 解析交給有測試涵蓋的純邏輯；這裡只判斷「檔案在不在」
        return MarkdownLinkParser.findLocalLinks(text)
            .filter { parent.findFileByRelativePath(it.path) == null }
            .map {
                BrokenLink(
                    sourceFile = file.name,
                    line = it.line,
                    label = it.label,
                    target = it.rawTarget,
                )
            }
    }
}
