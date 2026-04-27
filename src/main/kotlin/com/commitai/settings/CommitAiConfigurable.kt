package com.commitai.settings

import com.intellij.openapi.options.Configurable
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

class CommitAiConfigurable : Configurable {
    private val settings = CommitAiSettings.getInstance()

    private val baseUrlField = JBTextField()
    private val apiKeyField = JBPasswordField()
    private val modelField = JBTextField()
    private val temperatureField = JBTextField()
    private val promptTemplateField = JBTextArea(8, 80)

    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Commit AI"

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
        result.add(JBLabel("Base URL"), c)

        c.gridx = 1
        c.weightx = 1.0
        result.add(baseUrlField, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        result.add(JBLabel("API Key"), c)

        c.gridx = 1
        c.weightx = 1.0
        result.add(apiKeyField, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        result.add(JBLabel("Model"), c)

        c.gridx = 1
        c.weightx = 1.0
        result.add(modelField, c)

        c.gridx = 0
        c.gridy++
        c.weightx = 0.0
        result.add(JBLabel("Temperature (0~1)"), c)

        c.gridx = 1
        c.weightx = 1.0
        result.add(temperatureField, c)

        c.gridx = 0
        c.gridy++
        c.anchor = GridBagConstraints.NORTHWEST
        c.weightx = 0.0
        result.add(JBLabel("Prompt Template"), c)

        c.gridx = 1
        c.weightx = 1.0
        c.weighty = 1.0
        c.fill = GridBagConstraints.BOTH
        result.add(JScrollPane(promptTemplateField), c)

        panel = result
        reset()
        return result
    }

    override fun isModified(): Boolean {
        val state = settings.state
        return baseUrlField.text != state.baseUrl ||
            String(apiKeyField.password) != state.apiKey ||
            modelField.text != state.model ||
            temperatureField.text != state.temperature.toString() ||
            promptTemplateField.text != state.promptTemplate
    }

    override fun apply() {
        val state = settings.state
        val temp = temperatureField.text.toDoubleOrNull()
        if (temp == null || temp !in 0.0..1.0) {
            Messages.showErrorDialog("Temperature 必须是 0 到 1 之间的数字", "Commit AI")
            return
        }

        state.baseUrl = baseUrlField.text.trim().trimEnd('/')
        state.apiKey = String(apiKeyField.password).trim()
        state.model = modelField.text.trim()
        state.temperature = temp
        state.promptTemplate = promptTemplateField.text
    }

    override fun reset() {
        val state = settings.state
        baseUrlField.text = state.baseUrl
        apiKeyField.text = state.apiKey
        modelField.text = state.model
        temperatureField.text = state.temperature.toString()
        promptTemplateField.text = state.promptTemplate
    }

    override fun disposeUIResources() {
        panel = null
    }
}
