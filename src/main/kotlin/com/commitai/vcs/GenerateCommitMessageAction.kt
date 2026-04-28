package com.commitai.vcs

import com.commitai.ai.OpenAiCompatibleClient
import com.commitai.i18n.CommitAiBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.UIUtil
import java.util.Collections

class GenerateCommitMessageAction : AnAction() {
    private val client = OpenAiCompatibleClient()
    private val defaultIcon = IconLoader.getIcon("/icons/icon.svg", GenerateCommitMessageAction::class.java)
    private val loadingProjects = Collections.synchronizedSet(mutableSetOf<Project>())

    companion object {
        private const val MAX_CONTEXT_CHARS = 12_000
        private const val MAX_DIFF_CHARS_PER_FILE = 2_000
        private const val MAX_CHANGED_LINES_PER_FILE = 40
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val panel = e.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel
        val isLoading = project != null && loadingProjects.contains(project)
        updatePresentation(e.presentation, panel != null, isLoading)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = e.getData(Refreshable.PANEL_KEY) as? CheckinProjectPanel ?: return

        val selectedChanges = panel.selectedChanges
        if (selectedChanges.isEmpty()) {
            Messages.showInfoMessage(
                project,
                CommitAiBundle.message("action.info.selectChange"),
                CommitAiBundle.message("dialog.title"),
            )
            return
        }

        val contextText = buildContext(selectedChanges)
        loadingProjects.add(project)
        updatePresentation(e.presentation, isVisible = true, isLoading = true)
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, CommitAiBundle.message("action.task.generating"), false) {
                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                    indicator.text = CommitAiBundle.message("action.progress.callingAi")
                    runCatching {
                        client.generateCommitMessage(contextText)
                    }.onSuccess { message ->
                        UIUtil.invokeLaterIfNeeded {
                            loadingProjects.remove(project)
                            updatePresentation(e.presentation, isVisible = true, isLoading = false)
                            if (message.isBlank()) {
                                Messages.showWarningDialog(
                                    project,
                                    CommitAiBundle.message("action.warning.emptyResult"),
                                    CommitAiBundle.message("dialog.title"),
                                )
                                return@invokeLaterIfNeeded
                            }
                            panel.commitMessage = message
                        }
                    }.onFailure { error ->
                        UIUtil.invokeLaterIfNeeded {
                            loadingProjects.remove(project)
                            updatePresentation(e.presentation, isVisible = true, isLoading = false)
                            Messages.showErrorDialog(
                                project,
                                error.message ?: CommitAiBundle.message("action.error.unknown"),
                                CommitAiBundle.message("dialog.title"),
                            )
                        }
                    }
                }

                override fun onCancel() {
                    loadingProjects.remove(project)
                    UIUtil.invokeLaterIfNeeded {
                        updatePresentation(e.presentation, isVisible = true, isLoading = false)
                    }
                }
            },
        )
    }

    // 统一刷新提交按钮展示状态，避免 XML 占位符 tooltip 和点击 loading 状态不同步。
    private fun updatePresentation(presentation: Presentation, isVisible: Boolean, isLoading: Boolean) {
        presentation.text = CommitAiBundle.message("action.generate.text")
        presentation.description = CommitAiBundle.message("action.generate.description")
        presentation.isVisible = isVisible
        presentation.isEnabled = isVisible && !isLoading
        presentation.icon = if (isLoading) AnimatedIcon.Default.INSTANCE else defaultIcon
    }

    private fun buildContext(changes: Collection<Change>): String {
        val builder = StringBuilder()
        builder.appendLine(CommitAiBundle.message("action.context.header"))
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
            before == null && after != null -> CommitAiBundle.message("action.change.added", after)
            before != null && after == null -> CommitAiBundle.message("action.change.deleted", before)
            before != null && after != null && before != after -> CommitAiBundle.message("action.change.renamed", before, after)
            after != null -> CommitAiBundle.message("action.change.modified", after)
            else -> CommitAiBundle.message("action.change.unknown")
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
            rawSnippet.take(MAX_DIFF_CHARS_PER_FILE) + CommitAiBundle.message("action.diff.truncated")
        }
    }

    private fun buildAddedSnippet(afterContent: String): String {
        val lines = normalizeLines(afterContent)
        val snippetLines = lines.take(MAX_CHANGED_LINES_PER_FILE)
        val builder = StringBuilder("--- /dev/null\n+++ new file\n")
        snippetLines.forEach { builder.appendLine("+ $it") }
        if (lines.size > snippetLines.size) {
            builder.appendLine(CommitAiBundle.message("action.diff.addedOmitted", lines.size - snippetLines.size))
        }
        return builder.toString().trimEnd()
    }

    private fun buildDeletedSnippet(beforeContent: String): String {
        val lines = normalizeLines(beforeContent)
        val snippetLines = lines.take(MAX_CHANGED_LINES_PER_FILE)
        val builder = StringBuilder("--- deleted file\n+++ /dev/null\n")
        snippetLines.forEach { builder.appendLine("- $it") }
        if (lines.size > snippetLines.size) {
            builder.appendLine(CommitAiBundle.message("action.diff.deletedOmitted", lines.size - snippetLines.size))
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
            builder.appendLine(
                CommitAiBundle.message("action.diff.modifiedBeforeOmitted", beforeChanged.size - beforeSnippet.size),
            )
        }
        afterSnippet.forEach { builder.appendLine("+ $it") }
        if (afterChanged.size > afterSnippet.size) {
            builder.appendLine(
                CommitAiBundle.message("action.diff.modifiedAfterOmitted", afterChanged.size - afterSnippet.size),
            )
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
        return context.take(MAX_CONTEXT_CHARS) + CommitAiBundle.message("action.context.truncated")
    }
}
