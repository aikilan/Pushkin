package com.commitai.settings

import com.commitai.i18n.CommitAiBundle
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane

class CommitAiConfigurable(project: Project) : Configurable {
    private val globalSettings = CommitAiSettings.getInstance()
    private val projectSettings = CommitAiProjectSettings.getInstance(project)

    private val baseUrlField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val modelField = JBTextField()
    private val temperatureField = JBTextField()
    private val promptTemplateField = JBTextArea(8, 80)
    private val projectPromptTemplateField = JBTextArea(8, 80)

    private var panel: JPanel? = null

    override fun getDisplayName(): String = CommitAiBundle.message("settings.display.name")

    override fun createComponent(): JComponent {
        if (panel != null) return panel as JPanel

        val result = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = java.awt.Insets(4, 4, 4, 4)
        }

        c.gridx = 0
        c.gridy = 0
        c.weightx = 0.0
        result.add(JBLabel(CommitAiBundle.message("settings.label.baseUrl")), c)

        c.gridx = 1
        c.weightx = 1.0
        result.add(baseUrlField, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        result.add(JBLabel(CommitAiBundle.message("settings.label.apiKey")), c)

        c.gridx = 1
        c.weightx = 1.0
        result.add(apiKeyField, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        result.add(JBLabel(CommitAiBundle.message("settings.label.model")), c)

        c.gridx = 1
        c.weightx = 1.0
        result.add(modelField, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        result.add(JBLabel(CommitAiBundle.message("settings.label.temperature")), c)

        c.gridx = 1
        c.weightx = 1.0
        result.add(temperatureField, c)

        c.gridx = 0
        c.gridy++
        c.anchor = GridBagConstraints.NORTHWEST
        c.weightx = 0.0
        result.add(JBLabel(CommitAiBundle.message("settings.label.promptTemplate")), c)

        c.gridx = 1
        c.weightx = 1.0
        c.weighty = 1.0
        c.fill = GridBagConstraints.BOTH
        result.add(JScrollPane(promptTemplateField), c)

        c.gridx = 0
        c.gridy++
        c.anchor = GridBagConstraints.NORTHWEST
        c.weightx = 0.0
        c.weighty = 0.0
        c.fill = GridBagConstraints.HORIZONTAL
        result.add(JBLabel(CommitAiBundle.message("settings.label.projectPromptTemplate")), c)

        c.gridx = 1
        c.weightx = 1.0
        c.weighty = 1.0
        c.fill = GridBagConstraints.BOTH
        result.add(JScrollPane(projectPromptTemplateField), c)

        c.gridx = 1
        c.gridy++
        c.weighty = 0.0
        c.fill = GridBagConstraints.HORIZONTAL
        result.add(JBLabel(CommitAiBundle.message("settings.project.promptHint")), c)

        panel = result
        reset()
        return result
    }

    override fun isModified(): Boolean {
        val state = globalSettings.state
        return baseUrlField.text != state.baseUrl ||
            String(apiKeyField.password) != state.apiKey ||
            modelField.text != state.model ||
            temperatureField.text != state.temperature.toString() ||
            promptTemplateField.text != state.promptTemplate ||
            projectPromptTemplateField.text != projectSettings.state.promptTemplate
    }

    override fun apply() {
        val state = globalSettings.state
        val temp = temperatureField.text.toDoubleOrNull()
        if (temp == null || temp !in 0.0..1.0) {
            Messages.showErrorDialog(
                CommitAiBundle.message("settings.error.temperatureRange"),
                CommitAiBundle.message("dialog.title"),
            )
            return
        }

        state.baseUrl = baseUrlField.text.trim().trimEnd('/')
        state.apiKey = String(apiKeyField.password).trim()
        state.model = modelField.text.trim()
        state.temperature = temp
        state.promptTemplate = promptTemplateField.text
        projectSettings.state.promptTemplate = projectPromptTemplateField.text
    }

    override fun reset() {
        val state = globalSettings.state
        baseUrlField.text = state.baseUrl
        apiKeyField.text = state.apiKey
        modelField.text = state.model
        temperatureField.text = state.temperature.toString()
        promptTemplateField.text = state.promptTemplate
        projectPromptTemplateField.text = projectSettings.state.promptTemplate
    }

    override fun disposeUIResources() {
        panel = null
    }
}
