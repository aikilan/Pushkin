package com.commitai.settings

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
        var promptTemplate: String = DEFAULT_PROMPT_TEMPLATE,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        const val DEFAULT_PROMPT_TEMPLATE: String = """
你是一个专业的软件工程师助手。请根据提交变更内容生成 1 条高质量 Git 提交消息。
要求：
1) 默认使用 Conventional Commits 风格：type(scope): subject
2) subject 使用祈使语气，简洁明确，长度建议不超过 72 字符
3) 若无法判断 scope 可省略 scope
4) 仅输出最终提交消息，不要解释，不要使用 Markdown
"""

        fun getInstance(): CommitAiSettings {
            return ApplicationManager.getApplication().getService(CommitAiSettings::class.java)
        }
    }
}
