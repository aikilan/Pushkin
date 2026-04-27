package com.commitai.settings

import com.commitai.i18n.CommitAiBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "CommitAiSettings", storages = [Storage("commit-ai.xml")])
class CommitAiSettings : PersistentStateComponent<CommitAiSettings.State> {
    data class State(
        var baseUrl: String = "https://api.openai.com",
        var apiKey: String = "",
        var model: String = "gpt-4o-mini",
        var temperature: Double = 0.2,
        var promptTemplate: String = defaultPromptTemplate(),
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        private fun defaultPromptTemplate(): String {
            return CommitAiBundle.message("settings.defaultPromptTemplate")
        }

        fun getInstance(): CommitAiSettings {
            return ApplicationManager.getApplication().getService(CommitAiSettings::class.java)
        }
    }
}
