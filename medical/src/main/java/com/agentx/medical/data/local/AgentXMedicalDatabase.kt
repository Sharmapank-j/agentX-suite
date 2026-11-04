package com.agentx.medical.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.agentx.medical.data.local.dao.AgentDao
import com.agentx.medical.data.local.dao.ConversationDao
import com.agentx.medical.data.local.dao.ExecutionDao
import com.agentx.medical.data.local.dao.MemoryDao
import com.agentx.medical.data.local.dao.MessageDao
import com.agentx.medical.data.local.dao.ModelDao
import com.agentx.medical.data.local.dao.TeamDao
import com.agentx.medical.data.local.dao.ToolDao
import com.agentx.medical.data.local.entity.AgentEntity
import com.agentx.medical.data.local.entity.AgentTeamEntity
import com.agentx.medical.data.local.entity.ConversationEntity
import com.agentx.medical.data.local.entity.ExecutionEntity
import com.agentx.medical.data.local.entity.MemoryEntity
import com.agentx.medical.data.local.entity.MessageEntity
import com.agentx.medical.data.local.entity.ModelEntity
import com.agentx.medical.data.local.entity.TeamMemberEntity
import com.agentx.medical.data.local.entity.ToolEntity
import com.agentx.medical.data.local.entity.ToolPermissionEntity
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
    version = 1,
    exportSchema = false
)
abstract class AgentXMedicalDatabase : RoomDatabase() {

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
        private var INSTANCE: AgentXMedicalDatabase? = null

        fun getInstance(context: Context): AgentXMedicalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AgentXMedicalDatabase::class.java,
                    "agentx_medical_database.db"
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
                        name = "Chief Clinical Supervisor",
                        role = "SUPERVISOR",
                        systemPrompt = "You are the Chief Clinical Supervisor. You govern clinical consultations, medical curriculum inquiries, and multi-agent workflows. Decompose clinical questions, coordinate specialized medical agents, and synthesize verifiable, physician-grade reports adhering to evidence-based medicine.",
                        isDefault = true,
                        icon = "medical_services"
                    ),
                    AgentEntity(
                        id = "agent_planner",
                        name = "Clinical Task Planner",
                        role = "PLANNER",
                        systemPrompt = "You are the Clinical Task Planner. Decompose patient presentations, study goals (e.g. cardiovascular physiology), and clinical inquiries into structured, chronological subtasks with clear objectives and dependencies.",
                        isDefault = true,
                        icon = "checklist"
                    ),
                    AgentEntity(
                        id = "agent_researcher",
                        name = "Medical Literature Researcher",
                        role = "RESEARCHER",
                        systemPrompt = "You are the Medical Literature Researcher. Query clinical guidelines (AHA/ACC, ESC, NICE, WHO), pharmacological monographs, and verified biomedical knowledge. Emphasize peer-reviewed evidence and standard-of-care recommendations.",
                        isDefault = true,
                        icon = "menu_book"
                    ),
                    AgentEntity(
                        id = "agent_coder",
                        name = "Biomedical & Clinical Coder",
                        role = "CODER",
                        systemPrompt = "You are the Biomedical & Clinical Coder. Calculate clinical scores (eGFR, CHA2DS2-VASc, Wells, MELD, APACHE II, Corrected QT) and execute biomedical algorithms with absolute mathematical precision.",
                        isDefault = true,
                        icon = "calculate"
                    ),
                    AgentEntity(
                        id = "agent_analyst",
                        name = "Diagnostic & Pathophysiology Analyst",
                        role = "ANALYST",
                        systemPrompt = "You are the Diagnostic & Pathophysiology Analyst. Formulate clinical differential diagnoses, correlate lab abnormalities with symptomatology, and perform structured pathophysiological reasoning.",
                        isDefault = true,
                        icon = "analytics"
                    ),
                    AgentEntity(
                        id = "agent_reviewer",
                        name = "Clinical Safety & Reviewer",
                        role = "REVIEWER",
                        systemPrompt = "You are the Clinical Safety Reviewer. Act as the clinical safety barrier: rigorously audit all recommendations for contraindications, drug interactions, black-box warnings, dosage safety, and prevent medical hallucinations.",
                        isDefault = true,
                        icon = "verified_user"
                    ),
                    AgentEntity(
                        id = "agent_synthesizer",
                        name = "Medical Synthesizer",
                        role = "SYNTHESIZER",
                        systemPrompt = "You are the Medical Synthesizer. Merge multi-agent clinical findings into an elegant, structured summary with clinical indications, key takeaways, citations, and standard medical disclaimers.",
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
                        id = "tool_clinical_calc",
                        name = "Clinical Risk & Dosage Calculator",
                        description = "Calculates eGFR (CKD-EPI), CHA2DS2-VASc score, Wells score for DVT/PE, Mean Arterial Pressure (MAP), and drug dose adjustments",
                        category = "Clinical Tools",
                        requiresPermission = false,
                        schemaJson = "{\"calculation\": \"string\", \"parameters\": \"object\"}"
                    ),
                    ToolEntity(
                        id = "tool_drug_safety",
                        name = "Drug Safety & Contraindication Checker",
                        description = "Evaluates drug-drug interactions, contraindications, pregnancy categories, and black-box safety warnings",
                        category = "Pharmacology",
                        requiresPermission = false,
                        schemaJson = "{\"medications\": \"array\", \"patientConditions\": \"array\"}"
                    ),
                    ToolEntity(
                        id = "tool_lab_interpreter",
                        name = "Lab Reference Range Analyzer",
                        description = "Interprets CBC, CMP, ABG, and cardiac biomarker values against clinical standard reference intervals",
                        category = "Diagnostics",
                        requiresPermission = false,
                        schemaJson = "{\"panelType\": \"string\", \"values\": \"object\"}"
                    ),
                    ToolEntity(
                        id = "tool_patient_record",
                        name = "Secure Patient Document Reader",
                        description = "Confidential, offline Storage Access Framework reader for local clinical notes, lab PDFs, and medical summaries",
                        category = "HIPAA Privacy Storage",
                        requiresPermission = true,
                        schemaJson = "{\"documentUri\": \"string\"}"
                    )
                )
                toolDao.insertAll(defaultTools)
            }

            private suspend fun populateDefaultTeams(teamDao: TeamDao) {
                // Team 0: Clinical Multidisciplinary Team (General Assistant, Coder, Researcher, Analyst, Reviewer, Synthesizer)
                val generalTeam = AgentTeamEntity(
                    id = "team_clinical_core",
                    name = "Clinical Multidisciplinary Team",
                    description = "Comprehensive hospital and clinical care team integrating clinical guidance, pharmacological safety, biomedical calculations, and diagnostic review.",
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

                // Team 1: Cardiovascular & Physiology Team (from prompt's cardiovascular physiology example)
                val cardioTeam = AgentTeamEntity(
                    id = "team_cardio_phys",
                    name = "Cardiovascular & Physiology Team",
                    description = "Specialized clinical and study pipeline for cardiovascular medicine, hemodynamics, and electrophysiology (Supervisor -> Planner -> Researcher -> Analyst -> Reviewer -> Synthesizer)",
                    supervisorAgentId = "agent_supervisor"
                )
                teamDao.insertTeam(cardioTeam)
                val cardioMembers = listOf(
                    TeamMemberEntity("tm_c1", cardioTeam.id, "agent_supervisor", 0, "SUPERVISOR"),
                    TeamMemberEntity("tm_c2", cardioTeam.id, "agent_planner", 1, "PLANNER"),
                    TeamMemberEntity("tm_c3", cardioTeam.id, "agent_researcher", 2, "RESEARCHER"),
                    TeamMemberEntity("tm_c4", cardioTeam.id, "agent_analyst", 3, "ANALYST"),
                    TeamMemberEntity("tm_c5", cardioTeam.id, "agent_reviewer", 4, "REVIEWER"),
                    TeamMemberEntity("tm_c6", cardioTeam.id, "agent_synthesizer", 5, "SYNTHESIZER")
                )
                teamDao.insertTeamMembers(cardioMembers)

                // Team 2: Diagnostic & Differential Clinical Team
                val diagTeam = AgentTeamEntity(
                    id = "team_differential",
                    name = "Diagnostic & Differential Reasoning Team",
                    description = "Clinical team focused on symptom correlation, lab panel evaluation, differential diagnosis, and safety review",
                    supervisorAgentId = "agent_supervisor"
                )
                teamDao.insertTeam(diagTeam)
                val diagMembers = listOf(
                    TeamMemberEntity("tm_d1", diagTeam.id, "agent_supervisor", 0, "SUPERVISOR"),
                    TeamMemberEntity("tm_d2", diagTeam.id, "agent_analyst", 1, "ANALYST"),
                    TeamMemberEntity("tm_d3", diagTeam.id, "agent_coder", 2, "CODER"),
                    TeamMemberEntity("tm_d4", diagTeam.id, "agent_reviewer", 3, "REVIEWER"),
                    TeamMemberEntity("tm_d5", diagTeam.id, "agent_synthesizer", 4, "SYNTHESIZER")
                )
                teamDao.insertTeamMembers(diagMembers)
            }
        }
    }
}
