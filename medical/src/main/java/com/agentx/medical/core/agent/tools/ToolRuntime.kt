package com.agentx.medical.core.agent.tools

import com.agentx.medical.core.agent.core.ToolCallResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.pow

// ─── Tool Definitions ────────────────────────────────────────────────────────

@kotlinx.serialization.Serializable
data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val parametersJsonSchema: String,
    val requiresExplicitPermission: Boolean = false
)

interface ToolExecutor {
    suspend fun execute(toolId: String, argumentsJson: String): ToolCallResult
}

class ToolRegistry {
    private val registeredTools = mutableMapOf<String, ToolDefinition>()

    fun register(tool: ToolDefinition) {
        registeredTools[tool.id] = tool
    }

    fun get(toolId: String): ToolDefinition? = registeredTools[toolId]

    fun getAll(): List<ToolDefinition> = registeredTools.values.toList()
}

// ─── Argument Validation ──────────────────────────────────────────────────────

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}

object ToolArgumentValidator {
    fun validate(tool: ToolDefinition, argumentsJson: String): ValidationResult {
        return try {
            Json.parseToJsonElement(argumentsJson).jsonObject
            ValidationResult.Valid
        } catch (e: Exception) {
            ValidationResult.Invalid("Arguments JSON is malformed: ${e.message}")
        }
    }
}

// ─── Clinical Tool Implementations ────────────────────────────────────────────

/**
 * Cockcroft-Gault Creatinine Clearance (CrCl) Calculator.
 * Used for renal drug dosage adjustments (FDA drug labeling standard).
 * CrCl = [ (140 - Age) * Weight (kg) ] / [ 72 * Serum Creatinine (mg/dL) ] * (0.85 if Female)
 */
object CockcroftGaultCalculator {
    fun calculate(age: Int, weightKg: Double, serumCrMgDl: Double, sex: String): String {
        if (age <= 0 || weightKg <= 0.0 || serumCrMgDl <= 0.0) {
            return "Invalid input: Age, weight, and serum creatinine must all be positive values."
        }
        val isFemale = sex.uppercase() == "F"
        val rawCrCl = ((140 - age) * weightKg) / (72.0 * serumCrMgDl)
        val finalCrCl = if (isFemale) rawCrCl * 0.85 else rawCrCl

        val stage = when {
            finalCrCl >= 90.0 -> "Normal Renal Clearance (≥90 mL/min)"
            finalCrCl >= 60.0 -> "Mild Renal Impairment (60–89 mL/min)"
            finalCrCl >= 30.0 -> "Moderate Renal Impairment (30–59 mL/min)"
            finalCrCl >= 15.0 -> "Severe Renal Impairment (15–29 mL/min)"
            else -> "End-Stage Renal Disease (<15 mL/min)"
        }

        return """
## Cockcroft-Gault Creatinine Clearance (CrCl)

| Parameter | Value |
|-----------|-------|
| Calculated CrCl | ${String.format("%.1f", finalCrCl)} mL/min |
| Impairment Tier | $stage |
| Input Age | $age years |
| Input Weight | $weightKg kg |
| Serum Creatinine | $serumCrMgDl mg/dL |
| Biological Sex | $sex |

> ${if (finalCrCl < 50.0) "⚠️ Significant renal impairment. Review antibiotic (Vancomycin, Aminoglycosides), DOAC (Apixaban, Rivaroxaban), and Enoxaparin dosing guidelines." else "Standard renally cleared medication dosing applies."}
        """.trimIndent()
    }
}

/**
 * Serum Anion Gap & Delta-Delta Ratio Calculator for Acid-Base Disorders.
 * AG = [Na+] - ([Cl-] + [HCO3-])
 * Normal: 8–12 mEq/L
 */
object AnionGapCalculator {
    fun calculate(sodium: Double, chloride: Double, bicarbonate: Double, albumin: Double? = null): String {
        if (sodium <= 0.0 || chloride <= 0.0 || bicarbonate <= 0.0) {
            return "Invalid input: Sodium, chloride, and bicarbonate must be positive values."
        }
        val unadjustedAg = sodium - (chloride + bicarbonate)
        val correctedAg = if (albumin != null && albumin > 0.0) {
            unadjustedAg + 2.5 * (4.0 - albumin)
        } else {
            unadjustedAg
        }

        val classification = when {
            correctedAg > 12.0 -> "High Anion Gap Metabolic Acidosis (HAGMA)"
            correctedAg < 8.0 -> "Low Anion Gap (Hypoalbuminemia, multiple myeloma, lithium intoxication)"
            else -> "Normal Anion Gap (Non-Anion Gap Metabolic Acidosis / NAGMA or Normal)"
        }

        val differential = if (correctedAg > 12.0) {
            """
### Differential Diagnosis (GOLDMARK / MUDPILES):
• **G**lycols (Ethylene glycol, Propylene glycol)
• **O**xoproline (Chronic acetaminophen use)
• **L**-Lactate (Shock, hypoperfusion, mesenteric ischemia)
• **D**-Lactate (Short bowel syndrome)
• **M**ethanol intoxication
• **A**spirin / Salicylates
• **R**enal failure (Uremia, retention of sulfates/phosphates)
• **K**etoacidosis (DKA, Alcoholic ketoacidosis, Starvation)
            """.trimIndent()
        } else {
            """
### Normal Anion Gap Causes (HARDUPS):
• Hyperalimentation, Acetazolamide, Renal tubular acidosis (RTA), Diarrhea, Uretero-enteric fistula, Pancreatic fistula, Saline resuscitation.
            """.trimIndent()
        }

        return """
## Serum Anion Gap Evaluation

| Parameter | Value |
|-----------|-------|
| Measured Anion Gap | ${String.format("%.1f", unadjustedAg)} mEq/L |
${if (albumin != null) "| Albumin-Corrected AG | ${String.format("%.1f", correctedAg)} mEq/L (Albumin: $albumin g/dL) |\n" else ""}| Reference Range | 8.0–12.0 mEq/L |
| Interpretation | **$classification** |

$differential

> ⚕️ Correlate with Arterial Blood Gas (pH, PaCO2) and delta-gap ratio to rule out mixed acid-base disorders.
        """.trimIndent()
    }
}

/**
 * Corrected Calcium Calculator for Hypoalbuminemia.
 * Ca_corrected = Total Ca (mg/dL) + 0.8 * (4.0 - Albumin [g/dL])
 */
object CorrectedCalciumCalculator {
    fun calculate(measuredCalciumMgDl: Double, albuminGDl: Double): String {
        if (measuredCalciumMgDl <= 0.0 || albuminGDl <= 0.0) {
            return "Invalid input: Measured calcium and albumin must be positive values."
        }
        val correctedCa = measuredCalciumMgDl + 0.8 * (4.0 - albuminGDl)
        val status = when {
            correctedCa < 8.5 -> "Hypocalcemia (<8.5 mg/dL) — Assess Chvostek/Trousseau signs, ECG QTc prolongation"
            correctedCa > 10.5 -> "Hypercalcemia (>10.5 mg/dL) — Rule out hyperparathyroidism or malignancy"
            else -> "Normal Ionized/Corrected Calcium (8.5–10.5 mg/dL)"
        }

        return """
## Corrected Calcium Assessment

| Parameter | Value |
|-----------|-------|
| Measured Serum Calcium | $measuredCalciumMgDl mg/dL |
| Serum Albumin | $albuminGDl g/dL |
| **Corrected Calcium** | **${String.format("%.2f", correctedCa)} mg/dL** |
| Reference Range | 8.5–10.5 mg/dL |
| Clinical Interpretation | $status |

> ⚕️ If clinically significant abnormality is suspected, verify directly with Ionized Calcium (free Ca²⁺).
        """.trimIndent()
    }
}


/**
 * eGFR calculator using CKD-EPI 2021 (race-free) equation.
 * Input args: { "age": int, "sex": "M"|"F", "creatinine_mg_dl": float }
 */
object EgfrCalculator {
    fun calculate(age: Int, sex: String, creatinineMgDl: Double): String {
        if (creatinineMgDl <= 0.0 || age <= 0) {
            return "Invalid input: Age and creatinine must be positive values."
        }

        val kappa = if (sex.uppercase() == "F") 0.7 else 0.9
        val alpha = if (sex.uppercase() == "F") -0.241 else -0.302
        val crRatio = creatinineMgDl / kappa

        val egfr = 142.0 *
                minOf(crRatio, 1.0).pow(alpha) *
                maxOf(crRatio, 1.0).pow(-1.200) *
                (0.9938.pow(age.toDouble())) *
                (if (sex.uppercase() == "F") 1.012 else 1.0)

        val stage = when {
            egfr >= 90 -> "G1 (Normal/High)"
            egfr >= 60 -> "G2 (Mildly Decreased)"
            egfr >= 45 -> "G3a (Mildly-Moderately Decreased)"
            egfr >= 30 -> "G3b (Moderately-Severely Decreased)"
            egfr >= 15 -> "G4 (Severely Decreased)"
            else -> "G5 (Kidney Failure) — Dialysis evaluation required"
        }

        return """
## eGFR Result (CKD-EPI 2021)

| Parameter | Value |
|-----------|-------|
| eGFR | ${String.format("%.1f", egfr)} mL/min/1.73 m² |
| CKD Stage | $stage |
| Input Age | $age years |
| Input Sex | $sex |
| Creatinine | $creatinineMgDl mg/dL |

> ${if (egfr < 60) "⚠️ Renal dose adjustment required. Review nephrotoxic agents (NSAIDs, aminoglycosides, contrast media)." else "Normal renal function. Standard dosing applies."}
        """.trimIndent()
    }

    private fun minOf(a: Double, b: Double) = if (a < b) a else b
    private fun maxOf(a: Double, b: Double) = if (a > b) a else b
    private fun Double.pow(exp: Double) = kotlin.math.exp(exp * kotlin.math.ln(this))
}

/**
 * CHA₂DS₂-VASc stroke risk calculator for non-valvular atrial fibrillation.
 * Input args: { "chf": bool, "hypertension": bool, "age": int, "diabetes": bool,
 *               "stroke_history": bool, "vascular_disease": bool, "sex": "M"|"F" }
 */
object Cha2ds2VascCalculator {
    fun calculate(
        chf: Boolean,
        hypertension: Boolean,
        age: Int,
        diabetes: Boolean,
        strokeHistory: Boolean,
        vascularDisease: Boolean,
        sex: String
    ): String {
        var score = 0
        if (chf) score += 1
        if (hypertension) score += 1
        score += when {
            age >= 75 -> 2
            age >= 65 -> 1
            else -> 0
        }
        if (diabetes) score += 1
        if (strokeHistory) score += 2
        if (vascularDisease) score += 1
        if (sex.uppercase() == "F") score += 1

        val strokeRisk = when (score) {
            0 -> "0% / year"
            1 -> "1.3% / year"
            2 -> "2.2% / year"
            3 -> "3.2% / year"
            4 -> "4.0% / year"
            5 -> "6.7% / year"
            6 -> "9.8% / year"
            7 -> "9.6% / year"
            8 -> "6.7% / year"
            else -> ">15% / year"
        }

        val recommendation = when {
            sex.uppercase() == "F" && score <= 1 -> "No anticoagulation recommended (sex is the only risk factor)."
            sex.uppercase() == "M" && score == 0 -> "No anticoagulation recommended."
            score == 1 -> "Consider oral anticoagulation (OAC). Assess bleeding risk (HAS-BLED)."
            score >= 2 -> "Oral anticoagulation **recommended** (DOAC preferred unless contraindicated). Assess HAS-BLED score."
            else -> "Clinical assessment required."
        }

        return """
## CHA₂DS₂-VASc Score Result

| Factor | Points |
|--------|--------|
| Congestive Heart Failure | ${if (chf) "1" else "0"} |
| Hypertension | ${if (hypertension) "1" else "0"} |
| Age ≥75 / 65–74 | ${if (age >= 75) "2" else if (age >= 65) "1" else "0"} |
| Diabetes Mellitus | ${if (diabetes) "1" else "0"} |
| Stroke/TIA History | ${if (strokeHistory) "2" else "0"} |
| Vascular Disease | ${if (vascularDisease) "1" else "0"} |
| Female Sex | ${if (sex.uppercase() == "F") "1" else "0"} |
| **Total Score** | **$score** |

**Estimated Annual Stroke Risk:** $strokeRisk

### Clinical Recommendation
$recommendation

> ⚠️ This is a decision support tool only. Full clinical evaluation, comorbidities, and bleeding risk assessment are required before prescribing anticoagulation.
        """.trimIndent()
    }
}

/**
 * Drug safety checker: flags known high-risk interactions and black-box warnings.
 * Input args: { "drugs": ["drug1", "drug2", ...] }
 */
object DrugSafetyChecker {
    private val interactions = listOf(
        setOf("warfarin", "aspirin") to "HIGH RISK — Increased bleeding risk. Use with extreme caution; monitor INR closely.",
        setOf("warfarin", "nsaid") to "HIGH RISK — Potentiates anticoagulant effect and GI bleeding risk.",
        setOf("warfarin", "ibuprofen") to "HIGH RISK — NSAIDs increase bleeding risk with warfarin.",
        setOf("ace inhibitor", "potassium") to "MODERATE RISK — Risk of hyperkalemia. Monitor serum potassium.",
        setOf("lisinopril", "potassium") to "MODERATE RISK — ACE inhibitors reduce potassium excretion.",
        setOf("metformin", "contrast") to "MODERATE RISK — Hold metformin 48h before/after IV contrast (eGFR <60 mL/min).",
        setOf("digoxin", "amiodarone") to "HIGH RISK — Amiodarone increases digoxin levels by ~70%. Halve digoxin dose.",
        setOf("ssri", "tramadol") to "HIGH RISK — Serotonin syndrome risk. Avoid combination.",
        setOf("clopidogrel", "ppi") to "MODERATE RISK — PPIs (especially omeprazole) reduce clopidogrel antiplatelet effect.",
        setOf("statin", "amiodarone") to "MODERATE RISK — Increased risk of myopathy; avoid high-dose statins.",
        setOf("fluoroquinolone", "nsaid") to "MODERATE RISK — Increased seizure risk (fluoroquinolone + NSAID CNS interaction).",
        setOf("methotrexate", "nsaid") to "HIGH RISK — NSAIDs reduce methotrexate renal clearance → toxicity.",
        setOf("lithium", "nsaid") to "HIGH RISK — NSAIDs reduce lithium renal clearance → lithium toxicity.",
        setOf("ssri", "maoi") to "CONTRAINDICATED — Serotonin syndrome. Minimum 14-day washout required.",
        setOf("apixaban", "ketoconazole") to "HIGH RISK — Dual CYP3A4 & P-gp inhibition increases apixaban bleeding risk; reduce dose or avoid.",
        setOf("simvastatin", "clarithromycin") to "CONTRAINDICATED — Potent CYP3A4 inhibition elevates simvastatin levels → high rhabdomyolysis risk.",
        setOf("ciprofloxacin", "theophylline") to "HIGH RISK — CYP1A2 inhibition elevates theophylline levels to toxic range (seizures, arrhythmias).",
        setOf("allopurinol", "azathioprine") to "CONTRAINDICATED — Xanthine oxidase inhibition prevents azathioprine breakdown → fatal myelosuppression.",
        setOf("sildenafil", "nitroglycerin") to "CONTRAINDICATED — Combined cGMP accumulation causes catastrophic refractory hypotension."
    )

    private val blackBoxWarnings = mapOf(
        "fluoroquinolone" to "⛔ BLACK BOX: Tendinitis/tendon rupture, peripheral neuropathy, CNS effects. Reserve for cases with no alternatives.",
        "isotretinoin" to "⛔ BLACK BOX: Severe teratogen. iPLEDGE program mandatory. Contraindicated in pregnancy.",
        "clozapine" to "⛔ BLACK BOX: Agranulocytosis, seizures, myocarditis. Mandatory ANC monitoring.",
        "methotrexate" to "⛔ BLACK BOX: Hepatotoxicity, pulmonary toxicity, severe bone marrow suppression, teratogenic.",
        "amiodarone" to "⛔ BLACK BOX: Pulmonary toxicity, hepatotoxicity, proarrhythmia. Reserve for life-threatening arrhythmias.",
        "warfarin" to "⛔ BLACK BOX: Bleeding risk including fatal hemorrhage. Regular INR monitoring mandatory.",
        "varenicline" to "⛔ BLACK BOX: Serious neuropsychiatric symptoms; monitor mental health changes.",
        "tramadol" to "⛔ BLACK BOX: Respiratory depression, addiction, neonatal opioid withdrawal syndrome.",
        "sildenafil" to "⛔ CONTRAINDICATION: Concurrent nitrates cause life-threatening refractory hypotension.",
        "spironolactone" to "⛔ BLACK BOX: Tumorigenic in animal models; monitor for severe hyperkalemia."
    )

    fun check(drugs: List<String>): String {
        val normalizedDrugs = drugs.map { it.lowercase().trim() }
        val flaggedInteractions = mutableListOf<Pair<Set<String>, String>>()
        val flaggedWarnings = mutableListOf<Pair<String, String>>()

        for ((drugPair, warning) in interactions) {
            val matched = drugPair.any { interactingDrug ->
                normalizedDrugs.any { d -> d.contains(interactingDrug) }
            }
            if (matched && drugPair.size == 2) {
                val matchedInInput = drugPair.filter { interactingDrug ->
                    normalizedDrugs.any { d -> d.contains(interactingDrug) }
                }.toSet()
                if (matchedInInput.size >= 2) {
                    flaggedInteractions.add(drugPair to warning)
                }
            }
        }

        for (drug in normalizedDrugs) {
            for ((key, warning) in blackBoxWarnings) {
                if (drug.contains(key)) {
                    flaggedWarnings.add(drug to warning)
                }
            }
        }

        val sb = StringBuilder()
        sb.appendLine("## Drug Safety Check\n")
        sb.appendLine("**Drugs Reviewed:** ${drugs.joinToString(", ")}\n")

        if (flaggedInteractions.isEmpty() && flaggedWarnings.isEmpty()) {
            sb.appendLine("✅ No significant interactions or black box warnings detected in this combination.")
            sb.appendLine("\n> Always verify against full medication list and patient-specific factors (renal/hepatic function, age).")
            return sb.toString().trimEnd()
        }

        if (flaggedInteractions.isNotEmpty()) {
            sb.appendLine("### ⚠️ Drug Interactions Found\n")
            flaggedInteractions.forEachIndexed { i, (pair, msg) ->
                sb.appendLine("**${i + 1}. ${pair.joinToString(" + ")}**")
                sb.appendLine("$msg\n")
            }
        }

        if (flaggedWarnings.isNotEmpty()) {
            sb.appendLine("### ⛔ Black Box Warnings\n")
            flaggedWarnings.forEach { (drug, warning) ->
                sb.appendLine("**$drug:** $warning\n")
            }
        }

        sb.appendLine("> ⚕️ This output is for clinical decision support only and must be reviewed by a licensed prescriber.")
        return sb.toString().trimEnd()
    }
}

/**
 * Lab result interpreter for common clinical panels.
 * Input args: { "panel": { "sodium": float, "potassium": float, ... } }
 */
object LabInterpreter {
    data class LabRange(val low: Double, val high: Double, val unit: String, val lowFlag: String, val highFlag: String)

    private val referenceRanges = mapOf(
        "sodium" to LabRange(135.0, 145.0, "mEq/L", "Hyponatremia", "Hypernatremia"),
        "potassium" to LabRange(3.5, 5.0, "mEq/L", "Hypokalemia (arrhythmia risk)", "Hyperkalemia (cardiac arrest risk)"),
        "chloride" to LabRange(98.0, 106.0, "mEq/L", "Hypochloremia", "Hyperchloremia"),
        "bicarbonate" to LabRange(22.0, 29.0, "mEq/L", "Metabolic acidosis", "Metabolic alkalosis"),
        "bun" to LabRange(7.0, 20.0, "mg/dL", "Low urea (liver disease/malnutrition)", "Azotemia/Pre-renal failure"),
        "creatinine" to LabRange(0.6, 1.2, "mg/dL", "Low muscle mass", "Renal insufficiency"),
        "glucose" to LabRange(70.0, 100.0, "mg/dL", "Hypoglycemia", "Hyperglycemia"),
        "hemoglobin" to LabRange(12.0, 17.5, "g/dL", "Anemia", "Polycythemia"),
        "wbc" to LabRange(4.5, 11.0, "10³/μL", "Leukopenia (immunosuppression/viral)", "Leukocytosis (infection/inflammation)"),
        "platelets" to LabRange(150.0, 400.0, "10³/μL", "Thrombocytopenia (bleeding risk)", "Thrombocytosis"),
        "inr" to LabRange(0.8, 1.2, "ratio", "Supratherapeutic (over-anticoagulated)", "Sub-therapeutic or hypercoagulable"),
        "troponin" to LabRange(0.0, 0.04, "ng/mL", "Normal", "Myocardial injury/ACS — Urgent evaluation"),
        "bnp" to LabRange(0.0, 100.0, "pg/mL", "Normal", "Heart failure likely — Elevated BNP"),
        "tsh" to LabRange(0.4, 4.0, "mIU/L", "Hyperthyroidism / TSH suppression", "Hypothyroidism"),
        "crp" to LabRange(0.0, 5.0, "mg/L", "Normal", "Systemic inflammation / Infection"),
        "d-dimer" to LabRange(0.0, 0.50, "μg/mL", "Normal", "Elevated D-Dimer — Evaluate for VTE/PE/DIC"),
        "lactate" to LabRange(0.5, 2.0, "mmol/L", "Normal", "Lactic acidosis / Hypoperfusion / Sepsis alert"),
        "alt" to LabRange(7.0, 56.0, "U/L", "Normal", "Hepatocellular injury / Transaminitis"),
        "ast" to LabRange(10.0, 40.0, "U/L", "Normal", "Hepatocellular injury / Hepatic or cardiac damage"),
        "bilirubin" to LabRange(0.2, 1.2, "mg/dL", "Normal", "Hyperbilirubinemia / Jaundice / Hemolysis or cholestasis"),
        "albumin" to LabRange(3.5, 5.0, "g/dL", "Hypoalbuminemia (cirrhosis, nephrosis, malnutrition)", "Dehydration / Hemoconcentration"),
        "calcium" to LabRange(8.5, 10.5, "mg/dL", "Hypocalcemia (tetany, QTc prolongation)", "Hypercalcemia (hyperparathyroid, malignancy)"),
        "magnesium" to LabRange(1.7, 2.2, "mg/dL", "Hypomagnesemia (arrhythmia, Torsades de Pointes)", "Hypermagnesemia (loss of DTRs, respiratory depression)"),
        "phosphate" to LabRange(2.5, 4.5, "mg/dL", "Hypophosphatemia (refeeding syndrome)", "Hyperphosphatemia (CKD, tumor lysis)"),
        "hba1c" to LabRange(4.0, 5.6, "%", "Normal / Hypoglycemia risk", "Elevated HbA1c (Pre-diabetes >=5.7%, Diabetes >=6.5%)")
    )

    fun interpret(panel: Map<String, Double>): String {
        val sb = StringBuilder()
        sb.appendLine("## Lab Panel Interpretation\n")

        val abnormal = mutableListOf<String>()
        val normal = mutableListOf<String>()

        for ((test, value) in panel) {
            val testLower = test.lowercase().trim()
            val range = referenceRanges[testLower]

            if (range != null) {
                when {
                    value < range.low -> {
                        abnormal.add("| **$test** | **${value} ${range.unit}** | ⬇️ LOW | ${range.lowFlag} | ${range.low}–${range.high} |")
                    }
                    value > range.high -> {
                        abnormal.add("| **$test** | **${value} ${range.unit}** | ⬆️ HIGH | ${range.highFlag} | ${range.low}–${range.high} |")
                    }
                    else -> {
                        normal.add("| $test | ${value} ${range.unit} | ✅ Normal | — | ${range.low}–${range.high} |")
                    }
                }
            } else {
                normal.add("| $test | $value | ❓ Unknown | Reference not available | — |")
            }
        }

        if (abnormal.isNotEmpty()) {
            sb.appendLine("### ⚠️ Abnormal Results\n")
            sb.appendLine("| Test | Value | Flag | Clinical Significance | Reference Range |")
            sb.appendLine("|------|-------|------|-----------------------|-----------------|")
            abnormal.forEach { sb.appendLine(it) }
            sb.appendLine()
        }

        if (normal.isNotEmpty()) {
            sb.appendLine("### ✅ Normal Results\n")
            sb.appendLine("| Test | Value | Status | Note | Reference Range |")
            sb.appendLine("|------|-------|--------|------|-----------------|")
            normal.forEach { sb.appendLine(it) }
        }

        if (abnormal.isEmpty() && normal.isEmpty()) {
            sb.appendLine("No recognized lab values provided.")
        }

        sb.appendLine("\n> ⚕️ Results should be interpreted in the full clinical context including symptoms, history, and medication effects.")
        return sb.toString().trimEnd()
    }
}

// ─── Clinical Tool Executor ───────────────────────────────────────────────────

class ClinicalToolExecutor : ToolExecutor {
    override suspend fun execute(toolId: String, argumentsJson: String): ToolCallResult {
        return try {
            val args = Json.parseToJsonElement(argumentsJson).jsonObject
            when (toolId) {
                "tool_clinical_calc" -> {
                    val calc = args["calculation"]?.jsonPrimitive?.content ?: "egfr"
                    when (calc.lowercase()) {
                        "egfr" -> {
                            val age = args["age"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 50
                            val sex = args["sex"]?.jsonPrimitive?.content ?: "M"
                            val cr = args["creatinine_mg_dl"]?.jsonPrimitive?.doubleOrNull ?: 1.0
                            ToolCallResult(toolId, EgfrCalculator.calculate(age, sex, cr), true)
                        }
                        "cha2ds2vasc" -> {
                            val chf = args["chf"]?.jsonPrimitive?.content?.toBoolean() ?: false
                            val htn = args["hypertension"]?.jsonPrimitive?.content?.toBoolean() ?: false
                            val age = args["age"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 65
                            val dm = args["diabetes"]?.jsonPrimitive?.content?.toBoolean() ?: false
                            val stroke = args["stroke_history"]?.jsonPrimitive?.content?.toBoolean() ?: false
                            val vasc = args["vascular_disease"]?.jsonPrimitive?.content?.toBoolean() ?: false
                            val sex = args["sex"]?.jsonPrimitive?.content ?: "M"
                            ToolCallResult(toolId, Cha2ds2VascCalculator.calculate(chf, htn, age, dm, stroke, vasc, sex), true)
                        }
                        "cockcroft_gault", "crcl" -> {
                            val age = args["age"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 60
                            val weight = args["weight_kg"]?.jsonPrimitive?.doubleOrNull ?: 70.0
                            val cr = args["creatinine_mg_dl"]?.jsonPrimitive?.doubleOrNull ?: 1.0
                            val sex = args["sex"]?.jsonPrimitive?.content ?: "M"
                            ToolCallResult(toolId, CockcroftGaultCalculator.calculate(age, weight, cr, sex), true)
                        }
                        "anion_gap" -> {
                            val na = args["sodium"]?.jsonPrimitive?.doubleOrNull ?: 140.0
                            val cl = args["chloride"]?.jsonPrimitive?.doubleOrNull ?: 102.0
                            val hco3 = args["bicarbonate"]?.jsonPrimitive?.doubleOrNull ?: 24.0
                            val alb = args["albumin"]?.jsonPrimitive?.doubleOrNull
                            ToolCallResult(toolId, AnionGapCalculator.calculate(na, cl, hco3, alb), true)
                        }
                        "corrected_calcium" -> {
                            val ca = args["calcium"]?.jsonPrimitive?.doubleOrNull ?: 8.0
                            val alb = args["albumin"]?.jsonPrimitive?.doubleOrNull ?: 2.5
                            ToolCallResult(toolId, CorrectedCalciumCalculator.calculate(ca, alb), true)
                        }
                        else -> ToolCallResult(toolId, "Unknown calculation type: $calc. Supported: egfr, cha2ds2vasc, cockcroft_gault, anion_gap, corrected_calcium", false, "Unknown calc")
                    }
                }

                "tool_drug_safety" -> {
                    val drugsArray = args["drugs"]
                    val drugs = drugsArray?.let {
                        try {
                            it.toString()
                                .removeSurrounding("[", "]")
                                .split(",")
                                .map { d -> d.trim().trim('"') }
                        } catch (_: Exception) { emptyList() }
                    } ?: emptyList()
                    ToolCallResult(toolId, DrugSafetyChecker.check(drugs), true)
                }

                "tool_lab_interpreter" -> {
                    val panelObj = args["panel"]?.jsonObject ?: run {
                        return@execute ToolCallResult(toolId, "No 'panel' object provided in arguments.", false, "Missing panel")
                    }
                    val panel = panelObj.entries.associate { (k, v) ->
                        k to (v.jsonPrimitive.doubleOrNull ?: 0.0)
                    }
                    ToolCallResult(toolId, LabInterpreter.interpret(panel), true)
                }

                "tool_patient_record" -> {
                    // Privacy-first: patient records are always local. No external calls.
                    val query = args["query"]?.jsonPrimitive?.content ?: "summary"
                    ToolCallResult(
                        toolId,
                        """
## Patient Record Access

> ⚠️ **HIPAA Safeguard Active**: Patient record retrieval is strictly local and offline.

**Query:** $query

The AgentX patient record tool operates on locally stored records only. No patient-identifiable data is transmitted externally.

*Currently returning placeholder. Full patient record integration requires the clinical team to provision local record storage.*
                        """.trimIndent(),
                        true
                    )
                }

                else -> ToolCallResult(toolId, "Tool '$toolId' is not registered in ClinicalToolExecutor.", false, "Unknown tool")
            }
        } catch (e: Exception) {
            ToolCallResult(toolId, "Tool execution failed: ${e.message}", false, e.message)
        }
    }
}

// ─── Registered Tool Definitions (for pre-population) ────────────────────────

object ClinicalToolDefinitions {
    val all = listOf(
        ToolDefinition(
            id = "tool_clinical_calc",
            name = "Clinical Risk & Dosage Calculator",
            description = "Calculates eGFR (CKD-EPI 2021), CHA₂DS₂-VASc, Cockcroft-Gault CrCl, Anion Gap, and Corrected Calcium. Input: { calculation: 'egfr'|'cha2ds2vasc'|'cockcroft_gault'|'anion_gap'|'corrected_calcium', ...params }",
            parametersJsonSchema = """{"type":"object","properties":{"calculation":{"type":"string"},"age":{"type":"number"},"sex":{"type":"string"},"creatinine_mg_dl":{"type":"number"}}}""",
            requiresExplicitPermission = false
        ),
        ToolDefinition(
            id = "tool_drug_safety",
            name = "Drug Safety & Interaction Checker",
            description = "Checks for clinically significant drug-drug interactions and FDA black-box warnings. Input: { drugs: [\"warfarin\", \"aspirin\"] }",
            parametersJsonSchema = """{"type":"object","properties":{"drugs":{"type":"array","items":{"type":"string"}}},"required":["drugs"]}""",
            requiresExplicitPermission = false
        ),
        ToolDefinition(
            id = "tool_lab_interpreter",
            name = "Lab Result Interpreter",
            description = "Evaluates clinical lab panels (electrolytes, CBC, coagulation, cardiac biomarkers) against evidence-based reference ranges. Input: { panel: { sodium: float, potassium: float, ... } }",
            parametersJsonSchema = """{"type":"object","properties":{"panel":{"type":"object"}},"required":["panel"]}""",
            requiresExplicitPermission = false
        ),
        ToolDefinition(
            id = "tool_patient_record",
            name = "Patient Record Lookup",
            description = "Queries locally stored offline patient records. HIPAA-compliant: zero external data transmission. Input: { query: string }",
            parametersJsonSchema = """{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}""",
            requiresExplicitPermission = true
        )
    )
}
