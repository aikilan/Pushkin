package com.commitai.vcs

import com.commitai.ai.OpenAiCompatibleClient
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.util.ui.UIUtil

class GenerateCommitMessageAction : AnAction() {
    private val client = OpenAiCompatibleClient()

    companion object {
        private const val MAX_CONTEXT_CHARS = 12_000
        private const val MAX_DIFF_CHARS_PER_FILE = 2_000
        private const val MAX_CHANGED_LINES_PER_FILE = 40
    }

    override fun update(e: AnActionEvent) {
        val panel = e.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel
        e.presentation.isEnabledAndVisible = panel != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = e.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel ?: return

        val selectedChanges = panel.selectedChanges
        if (selectedChanges.isEmpty()) {
            Messages.showInfoMessage(project, "请先选择至少一个变更文件", "Commit AI")
            return
        }

        val contextText = buildContext(selectedChanges)
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Commit AI 生成提交消息", false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                indicator.text = "正在调用 AI 服务..."
                runCatching {
                    client.generateCommitMessage(contextText)
                }.onSuccess { message ->
                    UIUtil.invokeLaterIfNeeded {
                        if (message.isBlank()) {
                            Messages.showWarningDialog(project, "AI 未返回可用提交消息", "Commit AI")
                            return@invokeLaterIfNeeded
                        }
                        panel.commitMessage = message
                    }
                }.onFailure { error ->
                    UIUtil.invokeLaterIfNeeded {
                        Messages.showErrorDialog(project, error.message ?: "未知错误", "Commit AI")
                    }
                }
            }
        })
    }

    private fun buildContext(changes: Collection<Change>): String {
        val builder = StringBuilder()
        builder.appendLine("以下是本次提交涉及的变更，请基于信息生成 1 条提交消息：")
        changes.forEachIndexed { index, change ->
            builder.appendLine("${index + 1}. ${describeChange(change)}")
            val diffSnippet = buildDiffSnippet(change)
            if (diffSnippet.isNotBlank()) {
                builder.appendLine(diffSnippet)
            }
        }
        return truncateContext(builder.toString())
    }

    private fun describeChange(change: Change): String {
        val before = change.beforeRevision?.file?.path
        val after = change.afterRevision?.file?.path
        return when {
            before == null && after != null -> "新增文件: $after"
            before != null && after == null -> "删除文件: $before"
            before != null && after != null && before != after -> "重命名文件: $before -> $after"
            after != null -> "修改文件: $after"
            else -> "未知变更"
        }
    }

    private fun buildDiffSnippet(change: Change): String {
        val beforeContent = safeContent(change.beforeRevision)
        val afterContent = safeContent(change.afterRevision)

        if (beforeContent == null && afterContent == null) {
            return ""
        }

        val rawSnippet = when {
            beforeContent == null && afterContent != null -> buildAddedSnippet(afterContent)
            beforeContent != null && afterContent == null -> buildDeletedSnippet(beforeContent)
            beforeContent != null && afterContent != null -> buildModifiedSnippet(beforeContent, afterContent)
            else -> ""
        }

        if (rawSnippet.isBlank()) {
            return ""
        }

        return if (rawSnippet.length <= MAX_DIFF_CHARS_PER_FILE) {
            rawSnippet
        } else {
            rawSnippet.take(MAX_DIFF_CHARS_PER_FILE) + "\n...（该文件 diff 已截断）"
        }
    }

    private fun buildAddedSnippet(afterContent: String): String {
        val lines = normalizeLines(afterContent)
        val snippetLines = lines.take(MAX_CHANGED_LINES_PER_FILE)
        val builder = StringBuilder("--- /dev/null\n+++ new file\n")
        snippetLines.forEach { builder.appendLine("+ $it") }
        if (lines.size > snippetLines.size) {
            builder.appendLine("+ ...（新增内容省略 ${lines.size - snippetLines.size} 行）")
        }
        return builder.toString().trimEnd()
    }

    private fun buildDeletedSnippet(beforeContent: String): String {
        val lines = normalizeLines(beforeContent)
        val snippetLines = lines.take(MAX_CHANGED_LINES_PER_FILE)
        val builder = StringBuilder("--- deleted file\n+++ /dev/null\n")
        snippetLines.forEach { builder.appendLine("- $it") }
        if (lines.size > snippetLines.size) {
            builder.appendLine("- ...（删除内容省略 ${lines.size - snippetLines.size} 行）")
        }
        return builder.toString().trimEnd()
    }

    private fun buildModifiedSnippet(beforeContent: String, afterContent: String): String {
        val beforeLines = normalizeLines(beforeContent)
        val afterLines = normalizeLines(afterContent)

        var prefix = 0
        while (prefix < beforeLines.size && prefix < afterLines.size && beforeLines[prefix] == afterLines[prefix]) {
            prefix++
        }

        var suffix = 0
        while (
            suffix < beforeLines.size - prefix &&
            suffix < afterLines.size - prefix &&
            beforeLines[beforeLines.size - 1 - suffix] == afterLines[afterLines.size - 1 - suffix]
        ) {
            suffix++
        }

        val beforeChanged = beforeLines.subList(prefix, beforeLines.size - suffix)
        val afterChanged = afterLines.subList(prefix, afterLines.size - suffix)

        if (beforeChanged.isEmpty() && afterChanged.isEmpty()) {
            return ""
        }

        val beforeSnippet = beforeChanged.take(MAX_CHANGED_LINES_PER_FILE)
        val afterSnippet = afterChanged.take(MAX_CHANGED_LINES_PER_FILE)

        val builder = StringBuilder()
        builder.appendLine("--- before")
        builder.appendLine("+++ after")
        beforeSnippet.forEach { builder.appendLine("- $it") }
        if (beforeChanged.size > beforeSnippet.size) {
            builder.appendLine("- ...（修改前内容省略 ${beforeChanged.size - beforeSnippet.size} 行）")
        }
        afterSnippet.forEach { builder.appendLine("+ $it") }
        if (afterChanged.size > afterSnippet.size) {
            builder.appendLine("+ ...（修改后内容省略 ${afterChanged.size - afterSnippet.size} 行）")
        }
        return builder.toString().trimEnd()
    }

    private fun safeContent(revision: ContentRevision?): String? {
        return runCatching { revision?.content }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizeLines(content: String): List<String> {
        return content.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
    }

    private fun truncateContext(context: String): String {
        if (context.length <= MAX_CONTEXT_CHARS) {
            return context
        }
        return context.take(MAX_CONTEXT_CHARS) + "\n\n[提示] 其余上下文已截断。"
    }
}
