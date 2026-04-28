package com.pushkin.vcs

import com.pushkin.ai.OpenAiCompatibleClient
import com.pushkin.i18n.PushkinBundle
import com.pushkin.settings.PushkinProjectSettings
import com.pushkin.settings.PushkinSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.ui.Refreshable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import com.intellij.vcs.commit.CommitProjectPanelAdapter
import git4idea.changes.GitChangeUtils
import git4idea.index.GitStageCommitWorkflowHandler
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

        val selection = resolveCommitSelection(panel)
        if (selection.isEmpty()) {
            Messages.showInfoMessage(
                project,
                PushkinBundle.message("action.info.selectChange"),
                PushkinBundle.message("dialog.title"),
            )
            return
        }

        val systemPrompt = resolveSystemPrompt(project)
        loadingProjects.add(project)
        updatePresentation(e.presentation, isVisible = true, isLoading = true)
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, PushkinBundle.message("action.task.generating"), false) {
                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                    indicator.text = PushkinBundle.message("action.progress.callingAi")
                    runCatching {
                        val contextText = buildContext(project, selection)
                        client.generateCommitMessage(contextText, systemPrompt)
                    }.onSuccess { message ->
                        UIUtil.invokeLaterIfNeeded {
                            loadingProjects.remove(project)
                            updatePresentation(e.presentation, isVisible = true, isLoading = false)
                            if (message.isBlank()) {
                                Messages.showWarningDialog(
                                    project,
                                    PushkinBundle.message("action.warning.emptyResult"),
                                    PushkinBundle.message("dialog.title"),
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
                                error.message ?: PushkinBundle.message("action.error.unknown"),
                                PushkinBundle.message("dialog.title"),
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

    private data class CommitSelection(
        val changes: List<Change>,
        val unversionedFiles: List<FilePath>,
        val commitAllRoots: Set<VirtualFile>,
    ) {
        fun isEmpty(): Boolean = changes.isEmpty() && unversionedFiles.isEmpty() && commitAllRoots.isEmpty()
    }

    // 项目提示词非空时覆盖全局提示词，留空则保持现有全局提示词体验。
    private fun resolveSystemPrompt(project: Project): String {
        val projectPrompt = PushkinProjectSettings.getInstance(project).state.promptTemplate.trim()
        return projectPrompt.ifBlank { PushkinSettings.getInstance().state.promptTemplate }
    }

    // 统一刷新提交按钮展示状态，避免 XML 占位符 tooltip 和点击 loading 状态不同步。
    private fun updatePresentation(presentation: Presentation, isVisible: Boolean, isLoading: Boolean) {
        presentation.text = PushkinBundle.message("action.generate.text")
        presentation.description = PushkinBundle.message("action.generate.description")
        presentation.isVisible = isVisible
        presentation.isEnabled = isVisible && !isLoading
        presentation.icon = if (isLoading) AnimatedIcon.Default.INSTANCE else defaultIcon
    }

    // 从提交面板读取用户勾选的内容；普通提交面板包含 changes 和未版本控制文件，Git staging 的 commit-all 需要单独按 root 取工作区 diff。
    private fun resolveCommitSelection(panel: CheckinProjectPanel): CommitSelection {
        val stagingPanel = resolveGitStageCommitPanel(panel)
        if (stagingPanel != null && stagingPanel.isCommitAll) {
            return CommitSelection(
                changes = emptyList(),
                unversionedFiles = emptyList(),
                commitAllRoots = stagingPanel.rootsToCommit,
            )
        }

        return CommitSelection(
            changes = panel.selectedChanges.toList(),
            unversionedFiles = resolveIncludedUnversionedFiles(panel),
            commitAllRoots = emptySet(),
        )
    }

    private fun resolveGitStageCommitPanel(panel: CheckinProjectPanel) =
        ((panel as? CommitProjectPanelAdapter)?.commitWorkflowHandler as? GitStageCommitWorkflowHandler)?.ui

    private fun resolveIncludedUnversionedFiles(panel: CheckinProjectPanel): List<FilePath> {
        val handler = (panel as? CommitProjectPanelAdapter)?.commitWorkflowHandler as? AbstractCommitWorkflowHandler<*, *>
        return handler?.ui?.getIncludedUnversionedFiles().orEmpty()
    }

    private fun buildContext(project: Project, selection: CommitSelection): String {
        val changes = if (selection.commitAllRoots.isNotEmpty()) {
            buildCommitAllChanges(project, selection.commitAllRoots)
        } else {
            selection.changes
        }
        return buildContext(changes, selection.unversionedFiles)
    }

    private fun buildCommitAllChanges(project: Project, roots: Collection<VirtualFile>): List<Change> {
        return roots.flatMap { root ->
            GitChangeUtils.getLocalChangesDiff(project, root, null)
        }
    }

    private fun buildContext(changes: Collection<Change>, unversionedFiles: Collection<FilePath>): String {
        val builder = StringBuilder()
        builder.appendLine(PushkinBundle.message("action.context.header"))
        var index = 1
        changes.forEach { change ->
            builder.appendLine("$index. ${describeChange(change)}")
            val diffSnippet = buildDiffSnippet(change)
            if (diffSnippet.isNotBlank()) {
                builder.appendLine(diffSnippet)
            }
            index++
        }
        unversionedFiles.forEach { filePath ->
            builder.appendLine("$index. ${PushkinBundle.message("action.change.added", filePath.path)}")
            val diffSnippet = buildUnversionedFileSnippet(filePath)
            if (diffSnippet.isNotBlank()) {
                builder.appendLine(diffSnippet)
            }
            index++
        }
        return truncateContext(builder.toString())
    }

    private fun describeChange(change: Change): String {
        val before = change.beforeRevision?.file?.path
        val after = change.afterRevision?.file?.path
        return when {
            before == null && after != null -> PushkinBundle.message("action.change.added", after)
            before != null && after == null -> PushkinBundle.message("action.change.deleted", before)
            before != null && after != null && before != after -> PushkinBundle.message("action.change.renamed", before, after)
            after != null -> PushkinBundle.message("action.change.modified", after)
            else -> PushkinBundle.message("action.change.unknown")
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
            rawSnippet.take(MAX_DIFF_CHARS_PER_FILE) + PushkinBundle.message("action.diff.truncated")
        }
    }

    // 入参是用户勾选但尚未纳入 Git 的文件，输出模拟新增文件 diff，供 AI 与已跟踪变更使用同一种上下文。
    private fun buildUnversionedFileSnippet(filePath: FilePath): String {
        val content = safeFileContent(filePath) ?: return ""
        val rawSnippet = buildAddedSnippet(content)
        return if (rawSnippet.length <= MAX_DIFF_CHARS_PER_FILE) {
            rawSnippet
        } else {
            rawSnippet.take(MAX_DIFF_CHARS_PER_FILE) + PushkinBundle.message("action.diff.truncated")
        }
    }

    private fun buildAddedSnippet(afterContent: String): String {
        val lines = normalizeLines(afterContent)
        val snippetLines = lines.take(MAX_CHANGED_LINES_PER_FILE)
        val builder = StringBuilder("--- /dev/null\n+++ new file\n")
        snippetLines.forEach { builder.appendLine("+ $it") }
        if (lines.size > snippetLines.size) {
            builder.appendLine(PushkinBundle.message("action.diff.addedOmitted", lines.size - snippetLines.size))
        }
        return builder.toString().trimEnd()
    }

    private fun buildDeletedSnippet(beforeContent: String): String {
        val lines = normalizeLines(beforeContent)
        val snippetLines = lines.take(MAX_CHANGED_LINES_PER_FILE)
        val builder = StringBuilder("--- deleted file\n+++ /dev/null\n")
        snippetLines.forEach { builder.appendLine("- $it") }
        if (lines.size > snippetLines.size) {
            builder.appendLine(PushkinBundle.message("action.diff.deletedOmitted", lines.size - snippetLines.size))
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
                PushkinBundle.message("action.diff.modifiedBeforeOmitted", beforeChanged.size - beforeSnippet.size),
            )
        }
        afterSnippet.forEach { builder.appendLine("+ $it") }
        if (afterChanged.size > afterSnippet.size) {
            builder.appendLine(
                PushkinBundle.message("action.diff.modifiedAfterOmitted", afterChanged.size - afterSnippet.size),
            )
        }
        return builder.toString().trimEnd()
    }

    private fun safeContent(revision: ContentRevision?): String? {
        return runCatching { revision?.content }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun safeFileContent(filePath: FilePath): String? {
        if (filePath.isDirectory) {
            return null
        }

        return runCatching {
            val virtualFile = filePath.virtualFile
            if (virtualFile != null && virtualFile.isValid) {
                VfsUtilCore.loadText(virtualFile)
            } else {
                filePath.ioFile.takeIf { it.isFile }?.readText()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
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
        return context.take(MAX_CONTEXT_CHARS) + PushkinBundle.message("action.context.truncated")
    }
}
