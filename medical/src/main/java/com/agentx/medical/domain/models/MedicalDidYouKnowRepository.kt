package com.agentx.medical.domain.models

import kotlin.random.Random

data class MedicalDidYouKnowInsight(
    val id: String,
    val category: String,
    val emoji: String,
    val headline: String,
    val fact: String,
    val suggestedPrompt: String,
    val relatedChips: List<String>
)

object MedicalDidYouKnowRepository {

    private val insights = listOf(
        MedicalDidYouKnowInsight(
            id = "ckd_epi_2021",
            category = "Nephrology & Renal Function",
            emoji = "🩺",
            headline = "The 2021 Race-Free CKD-EPI Formula",
            fact = "The 2021 CKD-EPI eGFR equation eliminated race as a biological variable in renal staging. By utilizing standardized serum creatinine and cystatin C alone, it provides equitable, unbiased filtration estimations across diverse patient populations without compromising prognostic accuracy.",
            suggestedPrompt = "Explain the physiological rationale behind removing race from the 2021 CKD-EPI eGFR calculation.",
            relatedChips = listOf("eGFR Calculation", "CKD Staging", "Cystatin C vs Creatinine")
        ),
        MedicalDidYouKnowInsight(
            id = "cha2ds2_vasc_stroke",
            category = "Cardiology & Hemodynamics",
            emoji = "🫀",
            headline = "CHA2DS2-VASc vs CHADS2 Precision",
            fact = "The CHA2DS2-VASc score refined stroke risk prediction in non-valvular atrial fibrillation by adding vascular disease history, age 65-74 tiering, and female sex. A score of 0 in males or 1 in females truly identifies patients at exceptionally low risk (<1% per year) who can safely avoid systemic anticoagulation.",
            suggestedPrompt = "Walk me through the CHA2DS2-VASc scoring criteria and guidelines for starting DOAC anticoagulation.",
            relatedChips = listOf("CHA2DS2-VASc", "Atrial Fibrillation", "DOAC vs Warfarin")
        ),
        MedicalDidYouKnowInsight(
            id = "cyp450_pharmacogenomics",
            category = "Pharmacology & Genetics",
            emoji = "💊",
            headline = "The CYP450 Hepatic Engine",
            fact = "Over 75% of clinically prescribed medications are metabolized by hepatic Cytochrome P450 monooxygenases. Genetic polymorphisms in CYP2D6 and CYP2C19 categorize patients into poor, intermediate, extensive, or ultra-rapid metabolizers, profoundly altering therapeutic drug levels of beta-blockers, clopidogrel, and antidepressants.",
            suggestedPrompt = "How do CYP2D6 polymorphisms affect codeine-to-morphine bioactivation and pediatric safety?",
            relatedChips = listOf("CYP2D6 & CYP3A4", "Prodrug Activation", "Therapeutic Monitoring")
        ),
        MedicalDidYouKnowInsight(
            id = "penicillin_beta_lactam",
            category = "Infectious Disease",
            emoji = "🔬",
            headline = "Targeting Transpeptidases (PBPs)",
            fact = "Alexander Fleming's 1928 discovery of penicillin revolutionized medicine because beta-lactam antibiotics selectively inhibit bacterial penicillin-binding proteins (PBPs/transpeptidases) responsible for peptidoglycan cross-linking. Because human cells lack cell walls, beta-lactams achieve high therapeutic selectivity.",
            suggestedPrompt = "Explain the biochemical mechanism of beta-lactam antibiotics and how bacterial beta-lactamases cause resistance.",
            relatedChips = listOf("Beta-Lactam Mechanism", "Bacterial Cell Wall", "Beta-Lactamase Inhibitors")
        ),
        MedicalDidYouKnowInsight(
            id = "frank_starling_law",
            category = "Cardiovascular Physiology",
            emoji = "❤️",
            headline = "The Frank-Starling Mechanism",
            fact = "The Frank-Starling law of the heart states that the stroke volume of the heart increases in response to an increase in the volume of blood in the ventricles before contraction (end-diastolic volume), caused by optimized actin-myosin cross-bridge overlap in stretched cardiomyocytes.",
            suggestedPrompt = "How does the Frank-Starling curve explain compensation and decompensation in congestive heart failure?",
            relatedChips = listOf("Preload & Afterload", "End-Diastolic Volume", "Heart Failure Mechanics")
        ),
        MedicalDidYouKnowInsight(
            id = "anion_gap_metabolic_acidosis",
            category = "Critical Care & Acid-Base",
            emoji = "🧪",
            headline = "Unmasking Unmeasured Plasma Anions",
            fact = "Serum anion gap = [Na+] - ([Cl-] + [HCO3-]). A normal anion gap is 8 to 12 mEq/L, primarily representing circulating serum albumin. An elevated anion gap indicates unmeasured organic acids (lactate, ketoacids, uremic toxins, or toxic alcohols) as summarized by the classic mnemonic GOLDMARK.",
            suggestedPrompt = "How do you calculate the delta-delta (ΔAG / ΔHCO3-) ratio to identify mixed acid-base disorders?",
            relatedChips = listOf("Anion Gap Acidosis", "GOLDMARK & MUDPILES", "Delta-Delta Ratio")
        ),
        MedicalDidYouKnowInsight(
            id = "curb65_pneumonia",
            category = "Pulmonology & Triage",
            emoji = "🫁",
            headline = "CURB-65 Pneumonia Severity Triage",
            fact = "The CURB-65 score stratifies 30-day mortality risk in community-acquired pneumonia using 5 simple objective clinical criteria: Confusion, Urea >7 mmol/L, Respiratory rate ≥30/min, Blood pressure (SBP <90 or DBP ≤60 mmHg), and Age ≥65. Scores 0-1 indicate outpatient safety, while ≥3 warrants inpatient ICU consideration.",
            suggestedPrompt = "Break down the CURB-65 score components and compare it against the Pneumonia Severity Index (PSI).",
            relatedChips = listOf("CURB-65 Triage", "Pneumonia Mortality", "ICU Admission Criteria")
        ),
        MedicalDidYouKnowInsight(
            id = "crispr_cas9_genomics",
            category = "Molecular Genetics",
            emoji = "🧬",
            headline = "Bacterial Adaptive Immunity as Medicine",
            fact = "CRISPR-Cas9 originated as a bacterial adaptive immune system against bacteriophages. Its guide RNA (gRNA) directs the Cas9 endonuclease to introduce a targeted double-strand break at a specific genomic locus adjacent to a protospacer adjacent motif (PAM: 5'-NGG-3'), enabling cure for sickle cell disease.",
            suggestedPrompt = "How does CRISPR-Cas9 gene editing cure sickle cell disease by reactivating fetal hemoglobin (HbF)?",
            relatedChips = listOf("CRISPR-Cas9", "Sickle Cell Disease", "Fetal Hemoglobin Reactivation")
        ),
        MedicalDidYouKnowInsight(
            id = "blood_brain_barrier",
            category = "Neuroscience & Pharmacology",
            emoji = "🧠",
            headline = "The Tight Junction Neurovascular Shield",
            fact = "The blood-brain barrier (BBB) is formed by tightly sealed brain capillary endothelial cells with claudin-5 and occludin tight junctions, ensheathed by pericytes and astrocyte end-feet. It excludes >98% of small-molecule drugs and nearly 100% of large biologics unless specific receptor-mediated transport is harnessed.",
            suggestedPrompt = "How do pharmacologists design CNS-penetrant medications to cross the blood-brain barrier?",
            relatedChips = listOf("Blood-Brain Barrier", "Tight Junctions", "CNS Drug Delivery")
        ),
        MedicalDidYouKnowInsight(
            id = "troponin_kinetics",
            category = "Emergency Medicine",
            emoji = "⏱️",
            headline = "High-Sensitivity Cardiac Troponin Kinetics",
            fact = "High-sensitivity cardiac troponin (hs-cTnI / hs-cTnT) can detect cardiomyocyte micro-necrosis within 1 to 3 hours of ischemic chest pain onset. Because baseline levels exist in healthy individuals, acute myocardial infarction diagnosis requires observing a dynamic rise or fall (delta) above the 99th percentile URL.",
            suggestedPrompt = "Explain the European Society of Cardiology (ESC) 0/1-hour and 0/2-hour hs-cTn acute myocardial infarction rule-in/rule-out algorithms.",
            relatedChips = listOf("High-Sensitivity Troponin", "STEMI vs NSTEMI", "ESC 0/1-Hour Protocol")
        ),
        MedicalDidYouKnowInsight(
            id = "meld_sodium_liver",
            category = "Gastroenterology & Hepatology",
            emoji = "🔬",
            headline = "MELD-Na Score for Liver Allocation",
            fact = "The Model for End-Stage Liver Disease (MELD) uses objective laboratory markers (bilirubin, INR, creatinine) to eliminate subjective assessments like ascites. In 2016, serum sodium was incorporated (MELD-Na), significantly reducing waitlist mortality for cirrhotic patients suffering from dilutional hyponatremia.",
            suggestedPrompt = "How is the MELD-Na score calculated and why is serum sodium predictive of mortality in cirrhosis?",
            relatedChips = listOf("MELD-Na Score", "Cirrhosis Staging", "Liver Transplant Allocation")
        ),
        MedicalDidYouKnowInsight(
            id = "wells_dvt_pe",
            category = "Vascular & Hematology",
            emoji = "🩸",
            headline = "Pre-Test Probability and D-Dimer Triage",
            fact = "The Wells Criteria stratify clinical pre-test probability for deep vein thrombosis (DVT) and pulmonary embolism (PE). In low-risk patients, a high-sensitivity D-dimer test has a >99% negative predictive value, safely ruling out venous thromboembolism without expensive radiation imaging (CTPA).",
            suggestedPrompt = "Explain how age-adjusted D-dimer (age × 10 μg/L for patients >50 years) improves diagnostic specificity for PE.",
            relatedChips = listOf("Wells Criteria for PE", "Age-Adjusted D-Dimer", "CTPA vs V/Q Scan")
        )
    )

    fun getAllInsights(): List<MedicalDidYouKnowInsight> = insights

    fun getRandomInsight(excludeId: String? = null): MedicalDidYouKnowInsight {
        val pool = if (excludeId != null && insights.size > 1) {
            insights.filter { it.id != excludeId }
        } else {
            insights
        }
        return pool[Random.nextInt(pool.size)]
    }
}
