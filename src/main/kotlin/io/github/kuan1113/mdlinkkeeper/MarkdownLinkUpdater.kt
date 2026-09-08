package io.github.kuan1113.mdlinkkeeper

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/**
 * 這是產品的核心功能：**檔案被搬走或改名時，自動修好所有指向它的 Markdown 連結。**
 *
 * 為什麼這件事值得做：
 * 內建的 Markdown 插件只會在你「打開某個檔案」時標出壞連結；
 * 一個 200 檔的文件庫，搬一個檔案會弄壞散落各處的連結，而你不會知道。
 *
 * 所有路徑運算與文字改寫都在 [RelativePathUtil] 和 [MarkdownLinkParser]（有 38 項單元測試涵蓋）。
 * 這個類別只負責 IDE 那一半：監聽事件、走訪檔案、寫回檔案。
 */
class MarkdownLinkUpdater : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        for (event in events) {
            val (file, oldPath) = when {
                event is VFileMoveEvent ->
                    event.file to event.oldPath

                event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME ->
                    event.file to event.oldPath

                else -> continue
            }
            if (file.isDirectory) continue          // 先只處理單一檔案，資料夾整包搬移留待之後
            scheduleUpdate(file, oldPath, file.path)
        }
    }

    private fun scheduleUpdate(movedFile: VirtualFile, oldPath: String, newPath: String) {
        // VFS 事件正在寫入交易中，不能在這裡再改檔案 —— 排到之後執行
        ApplicationManager.getApplication().invokeLater {
            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed) continue
                updateInProject(project, movedFile, oldPath, newPath)
            }
        }
    }

    private fun updateInProject(
        project: Project,
        movedFile: VirtualFile,
        oldPath: String,
        newPath: String,
    ) {
        val oldSegments = RelativePathUtil.split(oldPath)
        val newSegments = RelativePathUtil.split(newPath)

        // 先在唯讀階段算出「哪些檔案要怎麼改」，不動任何東西
        val pending = mutableListOf<Pair<VirtualFile, String>>()

        ProjectFileIndex.getInstance(project).iterateContent { md ->
            if (!md.isDirectory &&
                md.extension.equals("md", ignoreCase = true) &&
                md != movedFile
            ) {
                planUpdate(md, oldSegments, newSegments)?.let { pending += md to it }
            }
            true
        }

        if (pending.isEmpty()) return

        // 用 WriteCommandAction 包起來，使用者可以 Ctrl+Z 復原
        WriteCommandAction.runWriteCommandAction(project, "更新 Markdown 連結", null, {
            var updated = 0
            for ((file, newText) in pending) {
                val doc = FileDocumentManager.getInstance().getDocument(file) ?: continue
                if (doc.text != newText) {
                    doc.setText(newText)
                    updated++
                }
            }
            if (updated > 0) notify(project, movedFile.name, updated)
        })
    }

    /** 算出這個 .md 檔改寫後的內容；沒有任何連結需要改就回傳 null */
    private fun planUpdate(
        md: VirtualFile,
        oldSegments: List<String>,
        newSegments: List<String>,
    ): String? {
        val text = try {
            String(md.contentsToByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }

        val mdDir = RelativePathUtil.split(md.parent?.path ?: return null)

        val rewritten = MarkdownLinkParser.rewriteLocalLinks(text) { link ->
            val resolved = RelativePathUtil.resolve(mdDir, link.path) ?: return@rewriteLocalLinks null
            if (resolved == oldSegments) {
                MarkdownLinkParser.encode(RelativePathUtil.relativize(mdDir, newSegments))
            } else {
                null
            }
        }

        return if (rewritten != text) rewritten else null
    }

    private fun notify(project: Project, movedFileName: String, fileCount: Int) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Markdown Link Updates")
            .createNotification(
                "已更新 Markdown 連結",
                "「$movedFileName」搬移後，$fileCount 個檔案裡指向它的連結已自動修正。可用 Ctrl+Z 復原。",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }
}
