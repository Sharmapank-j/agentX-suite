package com.agentx.medical.feature.agents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.medical.data.repository.AgentRepository
import com.agentx.medical.domain.models.Agent
import com.agentx.medical.domain.models.AgentRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class AgentsUiState(
    val agents: List<Agent> = emptyList(),
    val selectedRoleFilter: AgentRole? = null
)

class AgentsViewModel(
    private val agentRepository: AgentRepository
) : ViewModel() {

    private val _roleFilter = MutableStateFlow<AgentRole?>(null)

    val uiState: StateFlow<AgentsUiState> = combine(
        agentRepository.getAllAgents(),
        _roleFilter
    ) { agents, filter ->
        val filtered = if (filter == null) agents else agents.filter { it.role == filter }
        AgentsUiState(agents = filtered, selectedRoleFilter = filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AgentsUiState()
    )

    fun setFilter(role: AgentRole?) {
        _roleFilter.value = role
    }

    fun saveAgent(
        id: String?,
        name: String,
        role: AgentRole,
        systemPrompt: String,
        temperature: Float,
        topP: Float,
        maxTokens: Int
    ) {
        viewModelScope.launch {
            val agent = Agent(
                id = id ?: UUID.randomUUID().toString(),
                name = name,
                role = role,
                systemPrompt = systemPrompt,
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens,
                isDefault = false
            )
            agentRepository.saveAgent(agent)
        }
    }

    fun deleteAgent(id: String) {
        viewModelScope.launch {
            agentRepository.deleteAgent(id)
        }
    }
}
