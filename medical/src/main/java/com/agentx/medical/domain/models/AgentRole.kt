package com.agentx.medical.domain.models

import kotlinx.serialization.Serializable

@Serializable
enum class AgentRole(val title: String, val description: String) {
    SUPERVISOR(
        "Chief Medical Officer & Clinical Supervisor",
        "Orchestrates complex clinical consultations, assigns diagnostic, pharmacology, and triage subtasks, and compiles the final clinical assessment."
    ),
    PLANNER(
        "Clinical Strategy & Triage Planner",
        "Decomposes complex patient presentations, multi-system comorbidities, and acute scenarios into structured, prioritized diagnostic workup steps."
    ),
    RESEARCHER(
        "Evidence-Based Medicine Specialist",
        "Investigates peer-reviewed clinical guidelines, randomized controlled trials (RCTs), PubMed literature, and disease pathology mechanisms."
    ),
    CODER(
        "Clinical Informatics & EHR Specialist",
        "Specializes in clinical decision algorithms, ICD-10/CPT coding structures, deterministic calculators, and FHIR/HL7 clinical data workflows."
    ),
    ANALYST(
        "Biostatistics & Clinical Trial Analyst",
        "Conducts epidemiology risk calculations, statistical significance analysis, odds ratios, number needed to treat (NNT), and laboratory reference interval interpretations."
    ),
    REVIEWER(
        "Clinical Safety & Peer Reviewer",
        "Audits proposed diagnostic plans and therapeutic regimens for contraindicated drug-drug interactions, black box warnings, renal dosing adjustments, and clinical safety."
    ),
    SYNTHESIZER(
        "Clinical Scribe & Case Synthesizer",
        "Synthesizes multi-specialist findings into a standardized SOAP note, discharge summary, or bedside consult report with prioritized action items."
    ),
    ASSISTANT(
        "Clinical Decision Support Assistant",
        "Rapid, intuitive bedside medical assistant for healthcare professionals, interns, and students addressing acute medical questions."
    ),
    CUSTOM(
        "Custom Medical Specialist",
        "User-defined clinical sub-specialist (e.g. Cardiology, Nephrology, Oncology, Neurology, Critical Care) with customizable clinical instructions."
    )
}
