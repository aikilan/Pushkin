package com.commitai.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "CommitAiProjectSettings", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class CommitAiProjectSettings : PersistentStateComponent<CommitAiProjectSettings.State> {
    // 当前项目专属提示词写入 workspace 文件，仅对当前用户当前项目生效。
    data class State(
        var promptTemplate: String = "",
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): CommitAiProjectSettings {
            return project.getService(CommitAiProjectSettings::class.java)
        }
    }
}
