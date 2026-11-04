package com.agentx.feature.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agentx.data.repository.AgentRepository
import com.agentx.domain.models.Agent
import com.agentx.domain.models.AgentTeam
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class TeamsUiState(
    val teams: List<AgentTeam> = emptyList(),
    val availableAgents: List<Agent> = emptyList()
)

class TeamsViewModel(
    private val agentRepository: AgentRepository
) : ViewModel() {

    val uiState: StateFlow<TeamsUiState> = combine(
        agentRepository.getAllTeams(),
        agentRepository.getAllAgents()
    ) { teams, agents ->
        TeamsUiState(teams = teams, availableAgents = agents)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TeamsUiState()
    )

    fun saveTeam(id: String?, name: String, description: String, supervisorId: String, memberIds: List<String>) {
        viewModelScope.launch {
            val team = AgentTeam(
                id = id ?: UUID.randomUUID().toString(),
                name = name,
                description = description,
                supervisorAgentId = supervisorId,
                memberAgentIds = memberIds
            )
            agentRepository.saveTeam(team)
        }
    }

    fun deleteTeam(id: String) {
        viewModelScope.launch {
            agentRepository.deleteTeam(id)
        }
    }
}
