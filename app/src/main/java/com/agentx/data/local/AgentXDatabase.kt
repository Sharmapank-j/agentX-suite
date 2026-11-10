package com.agentx.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.agentx.data.local.dao.AgentDao
import com.agentx.data.local.dao.ConversationDao
import com.agentx.data.local.dao.ExecutionDao
import com.agentx.data.local.dao.MemoryDao
import com.agentx.data.local.dao.MessageDao
import com.agentx.data.local.dao.ModelDao
import com.agentx.data.local.dao.TeamDao
import com.agentx.data.local.dao.ToolDao
import com.agentx.data.local.entity.AgentEntity
import com.agentx.data.local.entity.AgentTeamEntity
import com.agentx.data.local.entity.ConversationEntity
import com.agentx.data.local.entity.ExecutionEntity
import com.agentx.data.local.entity.MemoryEntity
import com.agentx.data.local.entity.MessageEntity
import com.agentx.data.local.entity.ModelEntity
import com.agentx.data.local.entity.TeamMemberEntity
import com.agentx.data.local.entity.ToolEntity
import com.agentx.data.local.entity.ToolPermissionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ModelEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AgentEntity::class,
        AgentTeamEntity::class,
        TeamMemberEntity::class,
        ToolEntity::class,
        ToolPermissionEntity::class,
        ExecutionEntity::class,
        MemoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AgentXDatabase : RoomDatabase() {

    abstract fun modelDao(): ModelDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun agentDao(): AgentDao
    abstract fun teamDao(): TeamDao
    abstract fun toolDao(): ToolDao
    abstract fun executionDao(): ExecutionDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AgentXDatabase? = null

        fun getInstance(context: Context): AgentXDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgentXDatabase::class.java,
                    "agentx_database.db"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        populateDefaultAgents(database.agentDao())
                        populateDefaultTools(database.toolDao())
                        populateDefaultTeams(database.teamDao())
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                CoroutineScope(Dispatchers.IO).launch {
                    INSTANCE?.let { database ->
                        populateDefaultAgents(database.agentDao())
                        populateDefaultTools(database.toolDao())
                        populateDefaultTeams(database.teamDao())
                    }
                }
            }

            private suspend fun populateDefaultAgents(agentDao: AgentDao) {
                val defaultAgents = listOf(
                    AgentEntity(
                        id = "agent_supervisor",
                        name = "Chief Orchestrator & Supervisor",
                        role = "SUPERVISOR",
                        systemPrompt = "You are the Chief Orchestrator of AgentX, an advanced multi-agent personal AI assistant. You decompose user requests, coordinate specialized agents across coding, analysis, research, and planning, and synthesize cohesive, comprehensive responses.",
                        isDefault = false,
                        icon = "hub"
                    ),
                    AgentEntity(
                        id = "agent_planner",
                        name = "Strategy & Task Planner",
                        role = "PLANNER",
                        systemPrompt = "You are the Strategy & Task Planner for AgentX. You decompose user requests into structured, actionable execution phases with clear deliverables and dependency chains.",
                        isDefault = true,
                        icon = "checklist"
                    ),
                    AgentEntity(
                        id = "agent_researcher",
                        name = "Knowledge & Tech Researcher",
                        role = "RESEARCHER",
                        systemPrompt = "You are the Knowledge & Tech Researcher for AgentX. You retrieve documentation, explain scientific mechanisms, and synthesize authoritative empirical data across computing, science, and engineering.",
                        isDefault = true,
                        icon = "menu_book"
                    ),
                    AgentEntity(
                        id = "agent_coder",
                        name = "Code & Systems Architect",
                        role = "CODER",
                        systemPrompt = "You are the Code & Systems Architect for AgentX. You design modular software, write clean, idiomatic code, solve algorithms, and debug complex runtime issues.",
                        isDefault = true,
                        icon = "code"
                    ),
                    AgentEntity(
                        id = "agent_analyst",
                        name = "Data & Math Analyst",
                        role = "ANALYST",
                        systemPrompt = "You are the Data & Math Analyst for AgentX. You perform calculations, statistical evaluations, structured data analysis, and comparative trade-off evaluations.",
                        isDefault = true,
                        icon = "analytics"
                    ),
                    AgentEntity(
                        id = "agent_reviewer",
                        name = "Quality & Logic Auditor",
                        role = "REVIEWER",
                        systemPrompt = "You are the Quality & Logic Auditor for AgentX. You review specialist outputs for correctness, edge cases, vulnerabilities, and logical soundness.",
                        isDefault = true,
                        icon = "verified_user"
                    ),
                    AgentEntity(
                        id = "agent_synthesizer",
                        name = "Master Synthesizer",
                        role = "SYNTHESIZER",
                        systemPrompt = "You are the Master Synthesizer for AgentX. You weave together all specialist contributions into a unified, engaging, and polished final response.",
                        isDefault = true,
                        icon = "auto_awesome"
                    ),
                    AgentEntity(
                        id = "agent_assistant",
                        name = "AgentX General Assistant",
                        role = "ASSISTANT",
                        systemPrompt = "You are AgentX, a friendly, intelligent, and versatile personal AI assistant. Assist the user with everyday questions, programming, brainstorming, problem-solving, writing, and creative projects.",
                        isDefault = true,
                        icon = "psychology"
                    )
                )
                agentDao.insertAll(defaultAgents)
            }

            private suspend fun populateDefaultTools(toolDao: ToolDao) {
                val defaultTools = listOf(
                    ToolEntity(
                        id = "tool_calculator",
                        name = "High-Precision Math & Expression Evaluator",
                        description = "Evaluates mathematical expressions, arithmetic, percentages, powers, square roots, and unit conversions",
                        category = "Mathematics",
                        requiresPermission = false,
                        schemaJson = "{\"expression\": \"string\"}"
                    ),
                    ToolEntity(
                        id = "tool_device_specs",
                        name = "Device Telemetry & Performance Inspector",
                        description = "Inspects available RAM, CPU cores, battery level, and free storage to recommend optimal on-device AI models",
                        category = "System & Hardware",
                        requiresPermission = false,
                        schemaJson = "{}"
                    ),
                    ToolEntity(
                        id = "tool_datetime",
                        name = "Date, Time & Timezone Utility",
                        description = "Provides current UTC/local time, ISO-8601 formatting, date differences, and timezone conversions",
                        category = "Utilities",
                        requiresPermission = false,
                        schemaJson = "{\"action\": \"string\", \"timezone\": \"string\"}"
                    ),
                    ToolEntity(
                        id = "tool_text_analyzer",
                        name = "Text Statistics & Readability Analyzer",
                        description = "Analyzes word counts, character counts, reading time estimates, and regex search matches",
                        category = "Text Processing",
                        requiresPermission = false,
                        schemaJson = "{\"text\": \"string\"}"
                    ),
                    ToolEntity(
                        id = "tool_json_validator",
                        name = "JSON Schema & Syntax Validator",
                        description = "Validates JSON structure, syntax correctness, and reports schema errors",
                        category = "Developer Tools",
                        requiresPermission = false,
                        schemaJson = "{\"json\": \"string\"}"
                    )
                )
                toolDao.insertAll(defaultTools)
            }

            private suspend fun populateDefaultTeams(teamDao: TeamDao) {
                // Team 0: AgentX General Collective (General Assistant, Coder, Researcher, Analyst, Reviewer, Synthesizer)
                val generalTeam = AgentTeamEntity(
                    id = "team_agentx_core",
                    name = "AgentX General Collective",
                    description = "All-purpose personal AI assistant team for everyday chat, coding, research, data analysis, and synthesis.",
                    supervisorAgentId = "agent_supervisor"
                )
                teamDao.insertTeam(generalTeam)
                val generalMembers = listOf(
                    TeamMemberEntity("tm_g1", generalTeam.id, "agent_supervisor", 0, "SUPERVISOR"),
                    TeamMemberEntity("tm_g2", generalTeam.id, "agent_planner", 1, "PLANNER"),
                    TeamMemberEntity("tm_g3", generalTeam.id, "agent_coder", 2, "CODER"),
                    TeamMemberEntity("tm_g4", generalTeam.id, "agent_researcher", 3, "RESEARCHER"),
                    TeamMemberEntity("tm_g5", generalTeam.id, "agent_analyst", 4, "ANALYST"),
                    TeamMemberEntity("tm_g6", generalTeam.id, "agent_reviewer", 5, "REVIEWER"),
                    TeamMemberEntity("tm_g7", generalTeam.id, "agent_synthesizer", 6, "SYNTHESIZER")
                )
                teamDao.insertTeamMembers(generalMembers)

                // Team 1: Software Engineering & Architecture Team
                val devTeam = AgentTeamEntity(
                    id = "team_software_eng",
                    name = "Software Engineering & Architecture Squad",
                    description = "Specialized engineering pipeline for Kotlin/Python/Android coding, algorithms, architecture reviews, and bug auditing",
                    supervisorAgentId = "agent_supervisor"
                )
                teamDao.insertTeam(devTeam)
                val devMembers = listOf(
                    TeamMemberEntity("tm_e1", devTeam.id, "agent_supervisor", 0, "SUPERVISOR"),
                    TeamMemberEntity("tm_e2", devTeam.id, "agent_planner", 1, "PLANNER"),
                    TeamMemberEntity("tm_e3", devTeam.id, "agent_coder", 2, "CODER"),
                    TeamMemberEntity("tm_e4", devTeam.id, "agent_reviewer", 3, "REVIEWER"),
                    TeamMemberEntity("tm_e5", devTeam.id, "agent_synthesizer", 4, "SYNTHESIZER")
                )
                teamDao.insertTeamMembers(devMembers)

                // Team 2: Deep Research & Data Analysis Collective
                val researchTeam = AgentTeamEntity(
                    id = "team_research_analytics",
                    name = "Research & Data Analytics Collective",
                    description = "Comprehensive multi-agent pipeline for technical research, empirical calculations, statistics, and structured synthesis",
                    supervisorAgentId = "agent_supervisor"
                )
                teamDao.insertTeam(researchTeam)
                val researchMembers = listOf(
                    TeamMemberEntity("tm_r1", researchTeam.id, "agent_supervisor", 0, "SUPERVISOR"),
                    TeamMemberEntity("tm_r2", researchTeam.id, "agent_researcher", 1, "RESEARCHER"),
                    TeamMemberEntity("tm_r3", researchTeam.id, "agent_analyst", 2, "ANALYST"),
                    TeamMemberEntity("tm_r4", researchTeam.id, "agent_reviewer", 3, "REVIEWER"),
                    TeamMemberEntity("tm_r5", researchTeam.id, "agent_synthesizer", 4, "SYNTHESIZER")
                )
                teamDao.insertTeamMembers(researchMembers)
            }
        }
    }
}
