# AgentX Medical — Clinical Multi-Agent Architecture

## 1. Vision & Clinical Principles
AgentX Medical is an offline-first, private clinical multi-agent AI application designed for medical education, clinical study plans, pathophysiology reasoning, and clinical decision support running entirely on-device via local GGUF models.

### Clinical & Privacy Tenets:
- **HIPAA / Zero-Exfiltration Guarantee**: All patient summaries, clinical inquiries, and study notes remain strictly on the local device. No telemetry, third-party cloud services, or network calls are required.
- **Cognitive Clinical Hierarchy**:
  - **Chief Clinical Supervisor**: Governs the clinical workflow, decomposes medical goals, and oversees agent teams.
  - **Clinical Task Planner**: Breaks clinical questions and medical study curriculums (e.g. cardiovascular physiology) into sequential subtasks.
  - **Medical Literature Researcher**: Reviews clinical practice guidelines (ACC/AHA, ESC, NICE, WHO) and peer-reviewed biomedical evidence.
  - **Biomedical & Clinical Coder**: Executes medical algorithms, risk scores (CHA2DS2-VASc, Wells, eGFR, MAP), and biostatistical formulas.
  - **Diagnostic & Pathophysiology Analyst**: Analyzes symptomatology, differential diagnoses, and organ-system pathophysiology.
  - **Clinical Safety & Reviewer**: The critical safety barrier auditing for contraindications, black-box warnings, drug-drug interactions, and clinical hallucinations.
  - **Medical Synthesizer**: Formulates physician-grade structured summaries, study guides, and clinical briefs.
- **Resource Discipline**: Single active GGUF model in RAM by default, preventing out-of-memory crashes on mobile hardware.

---

## 2. Layered Architecture

```
┌────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                   │
│   ConsultScreen • SpecialistsScreen • Models • Settings│
├────────────────────────────────────────────────────────┤
│                       MVVM Layer                       │
│    ChatViewModel • AgentsViewModel • ModelsViewModel   │
├────────────────────────────────────────────────────────┤
│                      Domain Layer                      │
│     Agent • AgentTeam • Conversation • Tool • Memory    │
├────────────────────────────────────────────────────────┤
│             Clinical Multi-Agent Runtime               │
│   Supervisor • TaskPlanner • Dispatcher • SafetyReview │
├────────────────────────────────────────────────────────┤
│               Data Layer & Persistence                 │
│         Room Database (10 Tables) • DataStore          │
├────────────────────────────────────────────────────────┤
│               Inference Abstraction Layer              │
│                InferenceEngine Interface               │
├────────────────────────────────────────────────────────┤
│                     Native (JNI)                       │
│           libagentx.so (llama.cpp C++ Backend)         │
└────────────────────────────────────────────────────────┘
```

---

## 3. Clinical Tools & Safe Execution
1. `tool_clinical_calc`: Medical calculators (CHA2DS2-VASc, eGFR CKD-EPI, Wells, Mean Arterial Pressure).
2. `tool_drug_safety`: Pharmacovigilance screening for contraindications and interactions.
3. `tool_lab_interpreter`: Reference range evaluations (CBC, BMP, LFT, Troponin, BNP).
4. `tool_patient_record`: Storage Access Framework sandboxed reader for local medical documents.

---

## 4. Room Database Schema (10 Entities)
1. `ModelEntity`: Imported medical GGUF models (e.g. BioMistral, Meditron, Llama-3-Med).
2. `ConversationEntity`: Clinical consultations and study sessions.
3. `MessageEntity`: Clinical exchanges with token counts and streaming states.
4. `AgentEntity`: Specialist personas, clinical system prompts, and tool sets.
5. `AgentTeamEntity`: Multi-agent clinical teams (e.g. Cardiovascular Physiology Team).
6. `TeamMemberEntity`: Join table mapping clinical agents to teams.
7. `ToolEntity`: Registered clinical diagnostic and safety tools.
8. `ToolPermissionEntity`: User permissions and audit controls.
9. `ExecutionEntity`: Audit logs of clinical task decomposition and reasoning traces.
10. `MemoryEntity`: User-approved clinical guidelines, medical acronyms, and preferences.
