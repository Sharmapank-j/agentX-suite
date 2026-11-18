package com.agentx.medical.intelligence

/**
 * Recognized clinical inquiry intents for tailoring prompt structure and focus.
 */
enum class MedicalQueryIntent(
    val id: String,
    val title: String,
    val description: String,
    val keywords: List<String>,
    val promptDirectives: String
) {
    DIAGNOSTIC_EVALUATION(
        id = "DIAGNOSTIC_EVALUATION",
        title = "Diagnostic Evaluation & Workup",
        description = "Systematic approach to diagnosing a patient presentation or symptom complex",
        keywords = listOf("diagnose", "diagnosis", "workup", "evaluation", "approach to", "investigate", "diagnostic criteria"),
        promptDirectives = """
            CLINICAL INTENT: DIAGNOSTIC EVALUATION
            - Detail a stepped, tiered investigative pathway (Initial Bedside/POC, Core Laboratory, Targeted Imaging, Specialized Tests).
            - Specify numeric diagnostic cutoffs, normal ranges, and pathognomonic findings.
            - Explain the pre-test and post-test likelihood and sensitivity/specificity where relevant.
        """.trimIndent()
    ),

    DIFFERENTIAL_DIAGNOSIS(
        id = "DIFFERENTIAL_DIAGNOSIS",
        title = "Differential Diagnosis",
        description = "Structured list of potential diagnoses ranked by probability and acuity",
        keywords = listOf("differential", "differentials", "ddx", "rule out", "distinguish between", "discriminate"),
        promptDirectives = """
            CLINICAL INTENT: DIFFERENTIAL DIAGNOSIS
            - Rank differentials into: (1) Most Common / Likely, (2) Critical Must-Not-Miss Life Threats, and (3) Rare / Atypical.
            - For each differential, clearly contrast: Supporting clinical features, refuting features, and the single discriminating investigation.
        """.trimIndent()
    ),

    CLINICAL_MANAGEMENT(
        id = "CLINICAL_MANAGEMENT",
        title = "Clinical Management & Therapeutics",
        description = "Treatment protocols, stepped management, and pharmacotherapy",
        keywords = listOf("management", "treatment", "therapy", "manage", "treat", "protocol", "regimen", "guideline management"),
        promptDirectives = """
            CLINICAL INTENT: CLINICAL MANAGEMENT
            - Structure therapy into: (1) Immediate resuscitation & stabilization, (2) Definitive medical/surgical therapy, (3) Supportive care & symptom control, (4) Long-term secondary prevention.
            - Explicitly specify drug names, exact doses, routes, frequency, duration, renal/hepatic dose adjustments, and monitoring parameters.
        """.trimIndent()
    ),

    EMERGENCY_RESUSCITATION(
        id = "EMERGENCY_RESUSCITATION",
        title = "Emergency Resuscitation & Resus",
        description = "Immediate life support, airway, breathing, circulation stabilization",
        keywords = listOf("resuscitation", "cardiac arrest", "code blue", "acls", "bls", "atls", "shock", "intubation", "cpr", "defibrillation"),
        promptDirectives = """
            CLINICAL INTENT: EMERGENCY RESUSCITATION
            - Follow strict ABCDE protocol with immediate actionable bullet points.
            - Give immediate first-line drug dosages, defibrillation energy levels, and fluid bolus volumes.
            - State reversible causes (4 Hs & 4 Ts) and immediate post-resuscitation bundle care.
        """.trimIndent()
    ),

    PHARMACOTHERAPY(
        id = "PHARMACOTHERAPY",
        title = "Pharmacotherapy & Drug Dosing",
        description = "Specific drug mechanisms, dosing regimens, adverse effects, and contraindications",
        keywords = listOf("drug", "medication", "dose", "dosing", "pharmacology", "pharmacokinetics", "side effect", "adverse effect", "contraindication"),
        promptDirectives = """
            CLINICAL INTENT: PHARMACOTHERAPY
            - State mechanism of action, receptor pharmacology, bioavailability, and half-life.
            - List standard starting dose, titration schedule, maximum daily dose, and renal/hepatic adjustments.
            - Highlight common vs severe black-box adverse effects, key contraindications, and monitoring labs.
        """.trimIndent()
    ),

    INVESTIGATION_INTERPRETATION(
        id = "INVESTIGATION_INTERPRETATION",
        title = "Investigation & Laboratory Interpretation",
        description = "Interpreting lab results, blood gases, CSF analysis, pathology, and biomarkers",
        keywords = listOf("interpret", "lab results", "blood test", "abg", "vbg", "lft", "rft", "kft", "csf analysis", "biomarker", "troponin", "d-dimer"),
        promptDirectives = """
            CLINICAL INTENT: INVESTIGATION INTERPRETATION
            - Interpret each abnormal parameter with its underlying physiological mechanism.
            - State reference intervals and whether the alteration is mild, moderate, or severe.
            - Correlate findings into a unified clinical diagnosis with recommended confirmatory steps.
        """.trimIndent()
    ),

    PATHOPHYSIOLOGY(
        id = "PATHOPHYSIOLOGY",
        title = "Pathophysiology & Disease Mechanisms",
        description = "Biological, cellular, and tissue-level mechanisms of disease",
        keywords = listOf("pathophysiology", "mechanism of disease", "pathogenesis", "etiology", "aetiology", "cellular mechanism"),
        promptDirectives = """
            CLINICAL INTENT: PATHOPHYSIOLOGY
            - Detail step-by-step causal cascades from initial insult or genetic predisposition to tissue destruction.
            - Correlate cellular/molecular events directly with overt macroscopic clinical manifestations.
        """.trimIndent()
    ),

    CLINICAL_GUIDELINES(
        id = "CLINICAL_GUIDELINES",
        title = "Clinical Guidelines & Consensus",
        description = "Guideline recommendations (NICE, ESC, AHA/ACC, KDIGO, GOLD, GINA, Surviving Sepsis)",
        keywords = listOf("guidelines", "nice guidelines", "aha", "acc", "esc", "gold", "gina", "kdigo", "consensus", "recommendations"),
        promptDirectives = """
            CLINICAL INTENT: CLINICAL GUIDELINES
            - Cite current authoritative guideline recommendations with classes of recommendation and levels of evidence.
            - Highlight major updates or changes between older and current versions.
        """.trimIndent()
    ),

    VIVA_EXAM_PREPARATION(
        id = "VIVA_EXAM_PREPARATION",
        title = "Viva & Board Exam Revision",
        description = "High-yield examination topics, classical questions, and examiner pitfalls",
        keywords = listOf("viva", "exam", "board exam", "usmle", "plab", "neet pg", "mrcp", "osce", "high yield", "classical sign", "mnemonic"),
        promptDirectives = """
            CLINICAL INTENT: VIVA & BOARD EXAM REVISION
            - Present high-yield examiner questions and clear, concise model answers.
            - Highlight classical buzzwords, triad/pentads, eponyms, and memory mnemonics.
            - Warn against common student pitfalls and hazardous answers.
        """.trimIndent()
    ),

    ANATOMY_PHYSIOLOGY(
        id = "ANATOMY_PHYSIOLOGY",
        title = "Anatomy & Physiology Correlation",
        description = "Structural and functional correlation in clinical medicine and surgery",
        keywords = listOf("anatomy", "physiology", "nerve supply", "blood supply", "innervation", "structure", "histology"),
        promptDirectives = """
            CLINICAL INTENT: ANATOMY & PHYSIOLOGY CORRELATION
            - Describe relevant anatomical boundaries, vascular supplies, and neuro-innervation.
            - Directly explain clinical symptoms resulting from specific anatomical lesions or physiological derangements.
        """.trimIndent()
    ),

    SURGICAL_PROCEDURES(
        id = "SURGICAL_PROCEDURES",
        title = "Surgical Indications & Procedures",
        description = "Surgical anatomy, operative indications, complications, and pre/post-op care",
        keywords = listOf("surgery", "surgical", "operation", "procedure", "indication for surgery", "laparotomy", "preoperative", "postoperative"),
        promptDirectives = """
            CLINICAL INTENT: SURGICAL PROCEDURES
            - Outline clear absolute and relative indications for surgical intervention.
            - Detail preoperative optimization, key intraoperative landmarks/steps, and postoperative complication surveillance.
        """.trimIndent()
    ),

    PROGNOSIS_RISK_STRATIFICATION(
        id = "PROGNOSIS_RISK_STRATIFICATION",
        title = "Prognosis & Clinical Risk Scores",
        description = "Validated prognostic scoring systems (CURB-65, CHA2DS2-VASc, Wells, Glasgow-Blatchford)",
        keywords = listOf("prognosis", "risk score", "stratification", "curb-65", "cha2ds2", "wells score", "has-bled", "mortality risk"),
        promptDirectives = """
            CLINICAL INTENT: PROGNOSIS & RISK STRATIFICATION
            - Apply validated clinical scoring systems with individual point allocations.
            - State the resulting risk tier, estimated percentage mortality or event rate, and actionable clinical disposition.
        """.trimIndent()
    ),

    PATIENT_COUNSELLING(
        id = "PATIENT_COUNSELLING",
        title = "Patient Counselling & Communication",
        description = "Explaining conditions, lifestyle modifications, and treatment plans in accessible language",
        keywords = listOf("counsel", "counselling", "explain to patient", "patient education", "lifestyle advice", "breaking bad news"),
        promptDirectives = """
            CLINICAL INTENT: PATIENT COUNSELLING
            - Provide clear, empathetic, jargon-free explanations suitable for patient and family discussion.
            - Highlight actionable lifestyle modifications, medication adherence importance, and emergency return precautions.
        """.trimIndent()
    ),

    MEDICAL_ETHICS_LEGAL(
        id = "MEDICAL_ETHICS_LEGAL",
        title = "Medical Ethics & Medicolegal Aspects",
        description = "Consent, capacity, autonomy, confidentiality, DNACPR, and clinical ethics",
        keywords = listOf("ethics", "legal", "medicolegal", "consent", "capacity", "dnar", "dnacpr", "autonomy", "beneficence"),
        promptDirectives = """
            CLINICAL INTENT: MEDICAL ETHICS & MEDICOLEGAL
            - Address the four ethical pillars (Autonomy, Beneficence, Non-maleficence, Justice).
            - Detail structured capacity assessment, valid consent criteria, and documentation standards.
        """.trimIndent()
    ),

    CLINICAL_CALCULATION(
        id = "CLINICAL_CALCULATION",
        title = "Clinical Calculations & Equations",
        description = "Formulas, conversions, anion gap, eGFR, Cockcroft-Gault, Osmolality, A-a gradient",
        keywords = listOf("calculate", "formula", "equation", "anion gap", "cockcroft", "egfr", "corrected calcium", "qtc"),
        promptDirectives = """
            CLINICAL INTENT: CLINICAL CALCULATION
            - State the exact mathematical formula with units.
            - Show clear step-by-step arithmetic substitution and calculation.
            - Interpret the clinical significance of the calculated result.
        """.trimIndent()
    ),

    DRUG_INTERACTION(
        id = "DRUG_INTERACTION",
        title = "Drug-Drug & Drug-Disease Interactions",
        description = "Pharmacokinetic and pharmacodynamic interactions and contraindications",
        keywords = listOf("drug interaction", "interaction", "contraindication", "cyp450", "p-glycoprotein", "serotonin syndrome"),
        promptDirectives = """
            CLINICAL INTENT: DRUG INTERACTIONS
            - Identify the exact mechanism of interaction (e.g., CYP3A4 inhibition, additive QT prolongation).
            - State the severity level (Contraindicated / Severe / Moderate / Minor) and clinical management strategy.
        """.trimIndent()
    ),

    RADIOLOGY_IMAGING(
        id = "RADIOLOGY_IMAGING",
        title = "Radiology & Imaging Findings",
        description = "X-ray, CT, MRI, Ultrasound findings and imaging choice of modality",
        keywords = listOf("radiology", "imaging", "x-ray", "ct scan", "mri", "ultrasound", "cxr", "radiograph"),
        promptDirectives = """
            CLINICAL INTENT: RADIOLOGY & IMAGING
            - Specify the optimal first-line imaging modality of choice with contrast considerations.
            - Describe pathognomonic imaging signs and differential diagnoses for the observed radiologic pattern.
        """.trimIndent()
    ),

    ECG_INTERPRETATION(
        id = "ECG_INTERPRETATION",
        title = "ECG & Cardiac Rhythm Interpretation",
        description = "Rate, rhythm, axis, intervals, ischemia, infarction, and arrhythmias",
        keywords = listOf("ecg", "ekg", "st elevation", "st depression", "t wave", "qt interval", "arrhythmia", "qrs", "bundle branch"),
        promptDirectives = """
            CLINICAL INTENT: ECG INTERPRETATION
            - Follow a systematic review: Rate, Rhythm, Axis, P waves, PR interval, QRS morphology/duration, ST segments, T waves, QTc.
            - Correlate electrophysiologic abnormalities with acute coronary territory or systemic etiologies.
        """.trimIndent()
    ),

    TOXICOLOGY(
        id = "TOXICOLOGY",
        title = "Clinical Toxicology & Toxidromes",
        description = "Poisoning, overdose, antidotes, decontamination, and toxidromes",
        keywords = listOf("poison", "poisoning", "toxicology", "overdose", "antidote", "toxidrome", "paracetamol overdose", "naloxone"),
        promptDirectives = """
            CLINICAL INTENT: TOXICOLOGY
            - Characterize the toxidrome (Sympathomimetic, Anticholinergic, Cholinergic, Opioid, Sedative-Hypnotic).
            - Detail specific antidotes with exact dosing schedules and decontamination guidelines.
        """.trimIndent()
    ),

    GENERAL_MEDICAL_INQUIRY(
        id = "GENERAL_MEDICAL_INQUIRY",
        title = "General Medical Inquiry",
        description = "Broad or multi-topic medical information request",
        keywords = emptyList(),
        promptDirectives = """
            CLINICAL INTENT: GENERAL MEDICAL INQUIRY
            - Provide a comprehensive, evidence-based medical answer adhering strictly to medical science.
        """.trimIndent()
    );

    companion object {
        fun match(query: String): MedicalQueryIntent {
            val lower = query.lowercase()
            for (intent in entries) {
                if (intent == GENERAL_MEDICAL_INQUIRY) continue
                if (intent.keywords.any { lower.contains(it) }) {
                    return intent
                }
            }
            return GENERAL_MEDICAL_INQUIRY
        }
    }
}
