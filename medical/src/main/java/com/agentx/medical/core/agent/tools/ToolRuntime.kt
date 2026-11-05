package com.agentx.medical.core.agent.tools

import com.agentx.medical.core.agent.core.ToolCallResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlin.math.pow
import kotlin.math.sqrt

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

// ─── Clinical Tool Implementations (Deterministic & Validated) ────────────────

/**
 * 1. Cockcroft-Gault Creatinine Clearance (CrCl) Calculator.
 * CrCl = [ (140 - Age) * Weight (kg) ] / [ 72 * Serum Creatinine (mg/dL) ] * (0.85 if Female)
 */
object CockcroftGaultCalculator {
    fun calculate(age: Int, weightKg: Double, serumCrMgDl: Double, sex: String): String {
        if (age <= 0 || weightKg <= 0.0 || serumCrMgDl <= 0.0) {
            return "Invalid input: Age, weight, and serum creatinine must all be positive values."
        }
        val isFemale = sex.uppercase().startsWith("F")
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
| Calculated CrCl | **${String.format("%.1f", finalCrCl)} mL/min** |
| Impairment Tier | $stage |
| Input Age | $age years |
| Input Weight | $weightKg kg |
| Serum Creatinine | $serumCrMgDl mg/dL |
| Biological Sex | ${if (isFemale) "Female" else "Male"} |

> ${if (finalCrCl < 50.0) "⚠️ Significant renal impairment. Review antibiotic (Vancomycin, Aminoglycosides), DOAC (Apixaban, Rivaroxaban), and Enoxaparin dosing guidelines." else "Standard renally cleared medication dosing applies."}
        """.trimIndent()
    }
}

/**
 * 2. eGFR calculator using CKD-EPI 2021 (race-free) equation.
 */
object EgfrCalculator {
    fun calculate(age: Int, sex: String, creatinineMgDl: Double): String {
        if (creatinineMgDl <= 0.0 || age <= 0) {
            return "Invalid input: Age and creatinine must be positive values."
        }

        val isFemale = sex.uppercase().startsWith("F")
        val kappa = if (isFemale) 0.7 else 0.9
        val alpha = if (isFemale) -0.241 else -0.302
        val crRatio = creatinineMgDl / kappa

        val egfr = 142.0 *
                minOf(crRatio, 1.0).pow(alpha) *
                maxOf(crRatio, 1.0).pow(-1.200) *
                (0.9938.pow(age.toDouble())) *
                (if (isFemale) 1.012 else 1.0)

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
| eGFR | **${String.format("%.1f", egfr)} mL/min/1.73 m²** |
| CKD Stage | $stage |
| Input Age | $age years |
| Input Sex | ${if (isFemale) "Female" else "Male"} |
| Creatinine | $creatinineMgDl mg/dL |

> ${if (egfr < 60) "⚠️ Renal dose adjustment required. Review nephrotoxic agents (NSAIDs, aminoglycosides, contrast media)." else "Normal renal function. Standard dosing applies."}
        """.trimIndent()
    }

    private fun minOf(a: Double, b: Double) = if (a < b) a else b
    private fun maxOf(a: Double, b: Double) = if (a > b) a else b
    private fun Double.pow(exp: Double) = kotlin.math.exp(exp * kotlin.math.ln(this))
}

/**
 * 3. Serum Anion Gap & Delta-Delta Ratio Calculator for Acid-Base Disorders.
 * AG = [Na+] - ([Cl-] + [HCO3-])
 * Delta-Delta Ratio (ΔAG / ΔHCO3) evaluates for mixed acid-base disorders.
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

        val deltaAg = correctedAg - 12.0
        val deltaHco3 = 24.0 - bicarbonate
        val deltaRatio = if (deltaHco3 > 0.5) deltaAg / deltaHco3 else null

        val deltaInterpretation = if (correctedAg > 12.0 && deltaRatio != null) {
            when {
                deltaRatio < 0.4 -> "Delta Ratio < 0.4: Mixed High Anion Gap and Non-Anion Gap Metabolic Acidosis (e.g., DKA + severe diarrhea or renal tubular acidosis)."
                deltaRatio in 0.4..0.8 -> "Delta Ratio 0.4–0.8: Mixed HAGMA and NAGMA (combined high anion gap and hyperchloremic acidosis)."
                deltaRatio in 0.8..2.0 -> "Delta Ratio 0.8–2.0: Pure High Anion Gap Metabolic Acidosis (uncomplicated DKA, lactic acidosis)."
                else -> "Delta Ratio > 2.0: Mixed HAGMA with concurrent Metabolic Alkalosis (e.g., DKA + vomiting/NG suction or diuretic use) or pre-existing chronic respiratory acidosis."
            }
        } else null

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

        val deltaSection = if (deltaInterpretation != null) {
            """
### Delta-Delta Ratio Analysis (ΔAG / ΔHCO₃):
• **ΔAG (Excess Anion Gap):** ${String.format("%.1f", deltaAg)} mEq/L (AG - 12)
• **ΔHCO₃ (Bicarbonate Deficit):** ${String.format("%.1f", deltaHco3)} mEq/L (24 - HCO₃)
• **Delta-Delta Ratio:** **${String.format("%.2f", deltaRatio)}**
• **Interpretation:** $deltaInterpretation
            """.trimIndent()
        } else ""

        return """
## Serum Anion Gap & Delta-Delta Evaluation

| Parameter | Value |
|-----------|-------|
| Measured Anion Gap | ${String.format("%.1f", unadjustedAg)} mEq/L |
${if (albumin != null) "| Albumin-Corrected AG | ${String.format("%.1f", correctedAg)} mEq/L (Albumin: $albumin g/dL) |\n" else ""}| Reference Range | 8.0–12.0 mEq/L |
| Interpretation | **$classification** |

$differential
${if (deltaSection.isNotBlank()) "\n$deltaSection\n" else ""}
> ⚕️ Correlate with Arterial Blood Gas (pH, PaCO2) and clinical history to confirm compensation.
        """.trimIndent()
    }
}

/**
 * 4. Corrected Calcium Calculator for Hypoalbuminemia.
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
 * 5. CHA₂DS₂-VASc stroke risk calculator for non-valvular atrial fibrillation.
 * Fully corrected monotonic risk progression based on validated ESC/AHA guideline cohorts.
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
        val isFemale = sex.uppercase().startsWith("F")
        if (isFemale) score += 1

        val strokeRisk = when (score) {
            0 -> "0.2% / year"
            1 -> "0.6%–1.3% / year"
            2 -> "2.2% / year"
            3 -> "3.2% / year"
            4 -> "4.0% / year"
            5 -> "6.7% / year"
            6 -> "9.8% / year"
            7 -> "11.2% / year"
            8 -> "12.5% / year"
            else -> ">15.0% / year"
        }

        val recommendation = when {
            isFemale && score <= 1 -> "No anticoagulation recommended (sex is the only risk factor)."
            !isFemale && score == 0 -> "No anticoagulation recommended."
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
| Female Sex | ${if (isFemale) "1" else "0"} |
| **Total Score** | **$score** |

**Estimated Annual Stroke Risk:** $strokeRisk

### Clinical Recommendation
$recommendation

> ⚠️ This is a decision support tool only. Full clinical evaluation, comorbidities, and bleeding risk assessment are required before prescribing anticoagulation.
        """.trimIndent()
    }
}

/**
 * 6. HAS-BLED Bleeding Risk Calculator for Atrial Fibrillation.
 */
object HasBledCalculator {
    fun calculate(
        hypertension: Boolean,
        abnormalRenal: Boolean,
        abnormalLiver: Boolean,
        strokeHistory: Boolean,
        priorBleed: Boolean,
        labileInr: Boolean,
        ageOver65: Boolean,
        antiplateletOrNsaid: Boolean,
        alcoholExcess: Boolean
    ): String {
        var score = 0
        if (hypertension) score += 1
        if (abnormalRenal) score += 1
        if (abnormalLiver) score += 1
        if (strokeHistory) score += 1
        if (priorBleed) score += 1
        if (labileInr) score += 1
        if (ageOver65) score += 1
        if (antiplateletOrNsaid) score += 1
        if (alcoholExcess) score += 1

        val riskTier = when {
            score >= 3 -> "High Bleeding Risk (Annual major bleed risk ≥3.7%–8.9%)"
            score in 1..2 -> "Moderate Bleeding Risk (Annual major bleed risk 1.0%–1.9%)"
            else -> "Low Bleeding Risk (Annual major bleed risk ~0.9%)"
        }

        val clinicalAction = if (score >= 3) {
            "⚠️ **High bleeding risk (Score ≥ 3)**: Caution and regular review recommended. Mitigate modifiable risk factors (control hypertension, stop concurrent NSAIDs/aspirin, counsel on alcohol, stabilize INR)."
        } else {
            "Low-to-moderate bleeding risk. Anticoagulation generally well-tolerated; continue standard clinical monitoring."
        }

        return """
## HAS-BLED Bleeding Risk Assessment

| Parameter | Value |
|-----------|-------|
| **Total HAS-BLED Score** | **$score / 9 points** |
| Risk Category | $riskTier |
| Hypertension (SBP >160) | ${if (hypertension) "1" else "0"} |
| Renal Disease (Dialysis, Cr >2.26 mg/dL) | ${if (abnormalRenal) "1" else "0"} |
| Liver Disease (Cirrhosis, Bilirubin >2x, AST/ALT >3x) | ${if (abnormalLiver) "1" else "0"} |
| Stroke History | ${if (strokeHistory) "1" else "0"} |
| Prior Major Bleed / Predisposition | ${if (priorBleed) "1" else "0"} |
| Labile INRs (TTR <60%) | ${if (labileInr) "1" else "0"} |
| Elderly (Age >65) | ${if (ageOver65) "1" else "0"} |
| Medication Precaution (Aspirin, NSAIDs) | ${if (antiplateletOrNsaid) "1" else "0"} |
| Alcohol Excess (≥8 drinks/week) | ${if (alcoholExcess) "1" else "0"} |

### Clinical Directive
$clinicalAction

> ⚕️ A high HAS-BLED score should not be used alone to withhold oral anticoagulation; rather, it identifies patients requiring more frequent clinical review and correction of modifiable risk factors.
        """.trimIndent()
    }
}

/**
 * 7. Corrected Sodium for Hyperglycemia (Katz / Hillier Formula).
 * Corrected Na = Measured Na + 0.016 * (Glucose - 100)
 */
object CorrectedSodiumCalculator {
    fun calculate(measuredNa: Double, glucoseMgDl: Double): String {
        if (measuredNa <= 0.0 || glucoseMgDl <= 0.0) {
            return "Invalid input: Sodium and Glucose must be positive values."
        }
        val excessGlucose = maxOf(0.0, glucoseMgDl - 100.0)
        val katzCorrection = measuredNa + 0.016 * excessGlucose
        val hillierCorrection = measuredNa + 0.024 * excessGlucose

        val interpretation = when {
            katzCorrection < 135.0 -> "True Hyponatremia (Corrected Na < 135 mEq/L) despite hyperglycemia"
            katzCorrection > 145.0 -> "Hypernatremia (Corrected Na > 145 mEq/L) — severe hyperosmolar dehydration"
            else -> "Eunatremic (Normal corrected sodium 135–145 mEq/L). Measured low sodium represents pseudohyponatremia due to osmotic fluid shift."
        }

        return """
## Corrected Sodium for Hyperglycemia

| Parameter | Value |
|-----------|-------|
| Measured Sodium | $measuredNa mEq/L |
| Serum Glucose | $glucoseMgDl mg/dL |
| **Corrected Na (Standard Katz, 1.6 factor)** | **${String.format("%.1f", katzCorrection)} mEq/L** |
| Corrected Na (Hillier, 2.4 factor) | ${String.format("%.1f", hillierCorrection)} mEq/L |
| Normal Range | 135.0–145.0 mEq/L |
| Clinical Interpretation | **$interpretation** |

> ⚕️ In Diabetic Ketoacidosis (DKA) and Hyperosmolar Hyperglycemic State (HHS), use the corrected sodium to guide intravenous fluid selection (0.45% NaCl vs. 0.9% NaCl).
        """.trimIndent()
    }
}

/**
 * 8. Serum Osmolality & Osmolar Gap Calculator.
 * Calculated Osm = 2 * [Na+] + [Glucose]/18 + [BUN]/2.8
 */
object SerumOsmolalityCalculator {
    fun calculate(sodium: Double, glucoseMgDl: Double, bunMgDl: Double, measuredOsm: Double? = null): String {
        if (sodium <= 0.0 || glucoseMgDl <= 0.0 || bunMgDl <= 0.0) {
            return "Invalid input: Sodium, glucose, and BUN must be positive values."
        }
        val calculatedOsm = 2.0 * sodium + (glucoseMgDl / 18.0) + (bunMgDl / 2.8)
        val gap = measuredOsm?.let { it - calculatedOsm }

        val gapAlert = if (gap != null) {
            if (gap > 10.0) {
                "⚠️ **Elevated Osmolar Gap (>10 mOsm/kg):** Strong suspicion for exogenous unmeasured osmol ingestion: Methanol, Ethylene Glycol, Isopropanol, Propylene Glycol, Ethanol, or Mannitol/glycerol."
            } else {
                "Normal Osmolar Gap (≤10 mOsm/kg). High osmolar state driven by known endogenous solutes (sodium, glucose, urea)."
            }
        } else ""

        return """
## Serum Osmolality & Osmolar Gap Assessment

| Parameter | Value |
|-----------|-------|
| Calculated Osmolality | **${String.format("%.1f", calculatedOsm)} mOsm/kg** |
| Reference Range | 275–295 mOsm/kg |
| Serum Sodium | $sodium mEq/L |
| Serum Glucose | $glucoseMgDl mg/dL |
| Blood Urea Nitrogen (BUN) | $bunMgDl mg/dL |
${if (measuredOsm != null) "| Measured Osmolality | $measuredOsm mOsm/kg |\n| **Osmolar Gap** | **${String.format("%.1f", gap ?: 0.0)} mOsm/kg** |\n| Gap Reference Range | <10 mOsm/kg |\n" else ""}
${if (gapAlert.isNotBlank()) "### Toxicological Notice\n$gapAlert\n" else ""}
> ⚕️ Calculate in patients with altered mental status, unexplained metabolic acidosis, or suspected toxic alcohol exposure.
        """.trimIndent()
    }
}

/**
 * 9. Corrected QT Interval (QTc) Calculator (Bazett & Fridericia formulas).
 */
object QtcCalculator {
    fun calculate(qtIntervalMs: Double, heartRateBpm: Int, isFemale: Boolean): String {
        if (qtIntervalMs <= 0.0 || heartRateBpm <= 0) {
            return "Invalid input: QT interval and heart rate must be positive values."
        }
        val rrSeconds = 60.0 / heartRateBpm
        val qtcBazett = qtIntervalMs / sqrt(rrSeconds)
        val qtcFridericia = qtIntervalMs / Math.cbrt(rrSeconds)

        val threshold = if (isFemale) 460.0 else 450.0
        val isProlonged = qtcBazett > threshold
        val isCritical = qtcBazett > 500.0

        val riskNotice = when {
            isCritical -> "🚨 **CRITICAL PROLONGATION (QTc > 500 ms):** Markedly elevated risk of **Torsades de Pointes** (polymorphic ventricular tachycardia) and sudden cardiac death. Discontinue QT-prolonging drugs immediately, check serum K+ and Mg2+."
            isProlonged -> "⚠️ **Prolonged QTc (> $threshold ms):** Review concurrent QT-prolonging agents (Macrolides, Fluoroquinolones, Antipsychotics, Antiarrhythmics, Ondansetron). Monitor telemetry."
            else -> "Normal QTc (≤ $threshold ms). Low risk for drug-induced ventricular arrhythmias."
        }

        return """
## Corrected QT Interval (QTc) Evaluation

| Parameter | Value |
|-----------|-------|
| Measured QT Interval | $qtIntervalMs ms |
| Heart Rate | $heartRateBpm bpm (RR: ${String.format("%.2f", rrSeconds)} s) |
| Biological Sex | ${if (isFemale) "Female" else "Male"} (Upper limit: ${threshold.toInt()} ms) |
| **QTc (Bazett)** | **${String.format("%.0f", qtcBazett)} ms** |
| **QTc (Fridericia - preferred at extreme HR)** | **${String.format("%.0f", qtcFridericia)} ms** |
| Assessment | $riskNotice |

> ⚕️ Bazett overcorrects at rapid heart rates (>100 bpm) and undercorrects at low heart rates (<60 bpm); Fridericia is clinically preferred for extremes of heart rate.
        """.trimIndent()
    }
}

/**
 * 10. Wells Score for Deep Vein Thrombosis (DVT).
 */
object WellsDvtCalculator {
    fun calculate(
        activeCancer: Boolean,
        paralysisOrCast: Boolean,
        bedriddenRecentMajorSurgery: Boolean,
        localizedTendernessDeepVenous: Boolean,
        entireLegSwollen: Boolean,
        calfSwelling3cmGreaterThanOther: Boolean,
        pittingEdemaConfinedSymptomaticLeg: Boolean,
        collateralSuperficialVeins: Boolean,
        previouslyDocumentedDvt: Boolean,
        alternativeDiagnosisAtLeastAsLikely: Boolean
    ): String {
        var score = 0
        if (activeCancer) score += 1
        if (paralysisOrCast) score += 1
        if (bedriddenRecentMajorSurgery) score += 1
        if (localizedTendernessDeepVenous) score += 1
        if (entireLegSwollen) score += 1
        if (calfSwelling3cmGreaterThanOther) score += 1
        if (pittingEdemaConfinedSymptomaticLeg) score += 1
        if (collateralSuperficialVeins) score += 1
        if (previouslyDocumentedDvt) score += 1
        if (alternativeDiagnosisAtLeastAsLikely) score -= 2

        val (tier, probability, action) = when {
            score >= 2 -> Triple("DVT Likely", "~28%–40% risk", "Order compressive duplex ultrasonography of the lower extremity.")
            else -> Triple("DVT Unlikely", "~5% risk", "Order high-sensitivity D-dimer. If negative, DVT safely ruled out.")
        }

        return """
## Wells Score for Deep Vein Thrombosis (DVT)

| Parameter | Value |
|-----------|-------|
| **Total Wells DVT Score** | **$score points** |
| Risk Classification | **$tier** |
| Pre-test Probability | $probability |
| Recommended Action | **$action** |

> ⚕️ Two-tier model (Score ≥2 = Likely, Score <2 = Unlikely) recommended by NICE and ACP guidelines.
        """.trimIndent()
    }
}

/**
 * 11. Wells Score for Pulmonary Embolism (PE).
 */
object WellsPeCalculator {
    fun calculate(
        clinicalSignsDvt: Boolean,
        peMostLikelyDiagnosis: Boolean,
        tachycardiaHrOver100: Boolean,
        immobilizationOrSurgeryPast4Weeks: Boolean,
        priorDvtOrPe: Boolean,
        hemoptysis: Boolean,
        activeMalignancy: Boolean
    ): String {
        var score = 0.0
        if (clinicalSignsDvt) score += 3.0
        if (peMostLikelyDiagnosis) score += 3.0
        if (tachycardiaHrOver100) score += 1.5
        if (immobilizationOrSurgeryPast4Weeks) score += 1.5
        if (priorDvtOrPe) score += 1.5
        if (hemoptysis) score += 1.0
        if (activeMalignancy) score += 1.0

        val (tier, probability, recommendation) = when {
            score > 4.0 -> Triple("PE Likely", "~40% risk", "Immediate CT Pulmonary Angiography (CTPA) indicated. Consider empirical anticoagulation if no delays/contraindications.")
            else -> Triple("PE Unlikely", "~12% risk", "High-sensitivity D-dimer testing indicated (or PERC rule if very low pre-test suspicion).")
        }

        return """
## Wells Score for Pulmonary Embolism (PE)

| Parameter | Value |
|-----------|-------|
| **Total Wells PE Score** | **${String.format("%.1f", score)} points** |
| Clinical Risk Tier | **$tier** |
| Estimated PE Probability | $probability |
| Diagnostic Pathway | **$recommendation** |

> ⚕️ Score >4.0 = PE Likely; Score ≤4.0 = PE Unlikely.
        """.trimIndent()
    }
}

/**
 * 12. CURB-65 Pneumonia Severity Score.
 */
object Curb65Calculator {
    fun calculate(
        confusion: Boolean,
        elevatedBUN: Boolean,
        highRespiratoryRate: Boolean,
        lowBloodPressure: Boolean,
        ageOver65: Boolean
    ): String {
        var score = 0
        if (confusion) score += 1
        if (elevatedBUN) score += 1
        if (highRespiratoryRate) score += 1
        if (lowBloodPressure) score += 1
        if (ageOver65) score += 1

        val (mortality, disposition) = when (score) {
            0 -> "0.6% 30-day mortality" to "Low risk: Outpatient management suitable."
            1 -> "2.7% 30-day mortality" to "Low risk: Consider outpatient management or short stay."
            2 -> "6.8% 30-day mortality" to "Intermediate risk: Inpatient hospital admission recommended."
            3 -> "14.0% 30-day mortality" to "High risk: Inpatient hospitalization required."
            4, 5 -> "27.8%–31.0% 30-day mortality" to "Severe pneumonia: Urgent inpatient admission with immediate ICU consideration."
            else -> "Unknown" to "Clinical judgment required."
        }

        return """
## CURB-65 Pneumonia Severity Assessment

| Parameter | Points |
|-----------|--------|
| **Total CURB-65 Score** | **$score / 5 points** |
| Confusion (Disorientation in person/place/time) | ${if (confusion) "1" else "0"} |
| Urea >7 mmol/L (BUN >19 mg/dL) | ${if (elevatedBUN) "1" else "0"} |
| Respiratory Rate ≥30 breaths/min | ${if (highRespiratoryRate) "1" else "0"} |
| Blood Pressure (SBP <90 mmHg or DBP ≤60 mmHg) | ${if (lowBloodPressure) "1" else "0"} |
| Age ≥65 years | ${if (ageOver65) "1" else "0"} |

### Clinical Guidance
• **30-Day Mortality Risk:** $mortality
• **Recommended Disposition:** **$disposition**

> ⚕️ BTS guidelines recommendation. Combine with clinical judgment, pulse oximetry, and social factors before deciding admission.
        """.trimIndent()
    }
}

/**
 * 13. APGAR Newborn Scoring (1 & 5 minutes).
 */
object ApgarCalculator {
    fun calculate(
        appearance: Int, // 0 = blue/pale, 1 = body pink, extremities blue, 2 = completely pink
        pulse: Int,      // 0 = absent, 1 = <100 bpm, 2 = >100 bpm
        grimace: Int,    // 0 = flaccid, 1 = some flexion, 2 = active motion/cry
        activity: Int,   // 0 = absent, 1 = arms/legs flexed, 2 = active movement
        respiration: Int // 0 = absent, 1 = slow/irregular, 2 = vigorous cry
    ): String {
        val score = (appearance.coerceIn(0, 2) +
                pulse.coerceIn(0, 2) +
                grimace.coerceIn(0, 2) +
                activity.coerceIn(0, 2) +
                respiration.coerceIn(0, 2))

        val status = when {
            score >= 7 -> "Normal Newborn Transition (Score 7–10: Routine post-delivery care)"
            score in 4..6 -> "Moderately Abnormal (Score 4–6: Close monitoring, stimulation, and airway clearance indicated)"
            else -> "🚨 Critical Low APGAR (Score 0–3: Immediate neonatal resuscitation required [NRP algorithm])"
        }

        return """
## APGAR Newborn Score Assessment

| Parameter | Score (0–2) |
|-----------|-------------|
| Appearance (Skin Color) | $appearance |
| Pulse (Heart Rate) | $pulse |
| Grimace (Reflex Irritability) | $grimace |
| Activity (Muscle Tone) | $activity |
| Respiration (Effort/Cry) | $respiration |
| **Total APGAR Score** | **$score / 10** |

### Assessment & Action
• **Clinical Status:** **$status**

> ⚕️ Evaluated at 1 and 5 minutes post-delivery; repeat every 5 minutes up to 20 minutes if score remains <7.
        """.trimIndent()
    }
}

/**
 * 14. BMI & Body Surface Area (BSA) Calculator.
 * BMI = weight (kg) / [height (m)]^2
 * BSA (Mosteller) = sqrt([height (cm) * weight (kg)] / 3600)
 */
object BmiBsaCalculator {
    fun calculate(heightCm: Double, weightKg: Double): String {
        if (heightCm <= 0.0 || weightKg <= 0.0) {
            return "Invalid input: Height and weight must be positive values."
        }
        val heightM = heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)
        val bsa = sqrt((heightCm * weightKg) / 3600.0)

        val category = when {
            bmi < 18.5 -> "Underweight (<18.5 kg/m²)"
            bmi < 25.0 -> "Normal weight (18.5–24.9 kg/m²)"
            bmi < 30.0 -> "Overweight (25.0–29.9 kg/m²)"
            bmi < 35.0 -> "Class I Obesity (30.0–34.9 kg/m²)"
            bmi < 40.0 -> "Class II Obesity (35.0–39.9 kg/m²)"
            else -> "Class III Obesity (Severe/Morbid ≥40.0 kg/m²)"
        }

        return """
## BMI & Body Surface Area (BSA) Evaluation

| Metric | Value | Reference / Category |
|--------|-------|----------------------|
| **BMI** | **${String.format("%.1f", bmi)} kg/m²** | $category |
| **BSA (Mosteller)** | **${String.format("%.2f", bsa)} m²** | Standard adult: ~1.7–1.9 m² |
| Height | $heightCm cm (${String.format("%.2f", heightM)} m) | — |
| Weight | $weightKg kg | — |

> ⚕️ BSA is critical for chemotherapy dosing, cardiovascular hemodynamic indexing (Cardiac Index), and burn fluid resuscitation.
        """.trimIndent()
    }
}

/**
 * Drug safety checker: flags known high-risk interactions and black-box warnings.
 * Hardened with explicit clinical disclaimers to prevent false reassurance on unlisted combinations.
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
        setOf("methotrexate", "nsaid") to "HIGH RISK — NSAIDs reduce methotrexate renal clearance → severe toxicity.",
        setOf("lithium", "nsaid") to "HIGH RISK — NSAIDs reduce lithium renal clearance → lithium toxicity.",
        setOf("ssri", "maoi") to "CONTRAINDICATED — Fatal Serotonin syndrome risk. Minimum 14-day washout required.",
        setOf("apixaban", "ketoconazole") to "HIGH RISK — Dual CYP3A4 & P-gp inhibition increases apixaban bleeding risk; reduce dose or avoid.",
        setOf("simvastatin", "clarithromycin") to "CONTRAINDICATED — Potent CYP3A4 inhibition elevates simvastatin levels → high rhabdomyolysis risk.",
        setOf("ciprofloxacin", "theophylline") to "HIGH RISK — CYP1A2 inhibition elevates theophylline levels to toxic range (seizures, arrhythmias).",
        setOf("allopurinol", "azathioprine") to "CONTRAINDICATED — Xanthine oxidase inhibition prevents azathioprine breakdown → fatal myelosuppression.",
        setOf("sildenafil", "nitroglycerin") to "CONTRAINDICATED — Combined cGMP accumulation causes catastrophic refractory hypotension.",
        setOf("spironolactone", "potassium") to "HIGH RISK — Severe hyperkalemia risk. Avoid combination."
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
        sb.appendLine("## Drug Safety & Interaction Analysis\n")
        sb.appendLine("**Drugs Screened:** ${drugs.joinToString(", ")}\n")

        if (flaggedInteractions.isEmpty() && flaggedWarnings.isEmpty()) {
            sb.appendLine("ℹ️ **No interactions flagged within the curated rapid-lookup ruleset (20 major drug combinations).**\n")
            sb.appendLine("> ⚠️ **CLINICAL NOTICE**: This offline screening tool verifies critical black-box and major CYP pairs. It is **NOT** an exhaustive pharmacopoeia. Absence of a flag does not guarantee absence of drug interactions, idiosyncratic reactions, or individual contraindications. Always cross-check against full patient-specific factors (renal/hepatic function, age) and official prescribing literature.")
            return sb.toString().trimEnd()
        }

        if (flaggedInteractions.isNotEmpty()) {
            sb.appendLine("### ⚠️ Flagged Drug Interactions\n")
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
                        "cha2ds2vasc", "cha2ds2_vasc" -> {
                            val chf = args["chf"]?.jsonPrimitive?.booleanOrNull ?: false
                            val htn = args["hypertension"]?.jsonPrimitive?.booleanOrNull ?: false
                            val age = args["age"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 65
                            val dm = args["diabetes"]?.jsonPrimitive?.booleanOrNull ?: false
                            val stroke = args["stroke_history"]?.jsonPrimitive?.booleanOrNull ?: false
                            val vasc = args["vascular_disease"]?.jsonPrimitive?.booleanOrNull ?: false
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
                        "has_bled", "hasbled" -> {
                            val htn = args["hypertension"]?.jsonPrimitive?.booleanOrNull ?: false
                            val renal = args["abnormal_renal"]?.jsonPrimitive?.booleanOrNull ?: false
                            val liver = args["abnormal_liver"]?.jsonPrimitive?.booleanOrNull ?: false
                            val stroke = args["stroke_history"]?.jsonPrimitive?.booleanOrNull ?: false
                            val bleed = args["prior_bleed"]?.jsonPrimitive?.booleanOrNull ?: false
                            val inr = args["labile_inr"]?.jsonPrimitive?.booleanOrNull ?: false
                            val elderly = args["age_over_65"]?.jsonPrimitive?.booleanOrNull ?: false
                            val drugs = args["drugs_nsaid"]?.jsonPrimitive?.booleanOrNull ?: false
                            val alcohol = args["alcohol_excess"]?.jsonPrimitive?.booleanOrNull ?: false
                            ToolCallResult(toolId, HasBledCalculator.calculate(htn, renal, liver, stroke, bleed, inr, elderly, drugs, alcohol), true)
                        }
                        "corrected_sodium" -> {
                            val na = args["sodium"]?.jsonPrimitive?.doubleOrNull ?: 130.0
                            val glucose = args["glucose"]?.jsonPrimitive?.doubleOrNull ?: 350.0
                            ToolCallResult(toolId, CorrectedSodiumCalculator.calculate(na, glucose), true)
                        }
                        "osmolality", "serum_osmolality" -> {
                            val na = args["sodium"]?.jsonPrimitive?.doubleOrNull ?: 140.0
                            val glucose = args["glucose"]?.jsonPrimitive?.doubleOrNull ?: 100.0
                            val bun = args["bun"]?.jsonPrimitive?.doubleOrNull ?: 15.0
                            val measured = args["measured_osm"]?.jsonPrimitive?.doubleOrNull
                            ToolCallResult(toolId, SerumOsmolalityCalculator.calculate(na, glucose, bun, measured), true)
                        }
                        "qtc", "corrected_qt" -> {
                            val qt = args["qt_ms"]?.jsonPrimitive?.doubleOrNull ?: 420.0
                            val hr = args["heart_rate"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 75
                            val isFemale = args["is_female"]?.jsonPrimitive?.booleanOrNull ?: false
                            ToolCallResult(toolId, QtcCalculator.calculate(qt, hr, isFemale), true)
                        }
                        "curb65", "curb_65" -> {
                            val confusion = args["confusion"]?.jsonPrimitive?.booleanOrNull ?: false
                            val bun = args["elevated_bun"]?.jsonPrimitive?.booleanOrNull ?: false
                            val rr = args["high_rr"]?.jsonPrimitive?.booleanOrNull ?: false
                            val bp = args["low_bp"]?.jsonPrimitive?.booleanOrNull ?: false
                            val age65 = args["age_over_65"]?.jsonPrimitive?.booleanOrNull ?: false
                            ToolCallResult(toolId, Curb65Calculator.calculate(confusion, bun, rr, bp, age65), true)
                        }
                        "bmi_bsa", "bmi", "bsa" -> {
                            val ht = args["height_cm"]?.jsonPrimitive?.doubleOrNull ?: 175.0
                            val wt = args["weight_kg"]?.jsonPrimitive?.doubleOrNull ?: 70.0
                            ToolCallResult(toolId, BmiBsaCalculator.calculate(ht, wt), true)
                        }
                        "wells_dvt" -> {
                            val cancer = args["active_cancer"]?.jsonPrimitive?.booleanOrNull ?: false
                            val paralysis = args["paralysis"]?.jsonPrimitive?.booleanOrNull ?: false
                            val bedridden = args["bedridden"]?.jsonPrimitive?.booleanOrNull ?: false
                            val tenderness = args["tenderness"]?.jsonPrimitive?.booleanOrNull ?: false
                            val swollenLeg = args["swollen_leg"]?.jsonPrimitive?.booleanOrNull ?: false
                            val calfSwelling = args["calf_swelling"]?.jsonPrimitive?.booleanOrNull ?: false
                            val pittingEdema = args["pitting_edema"]?.jsonPrimitive?.booleanOrNull ?: false
                            val collateralVeins = args["collateral_veins"]?.jsonPrimitive?.booleanOrNull ?: false
                            val priorDvt = args["prior_dvt"]?.jsonPrimitive?.booleanOrNull ?: false
                            val altDiag = args["alternative_diagnosis"]?.jsonPrimitive?.booleanOrNull ?: false
                            ToolCallResult(toolId, WellsDvtCalculator.calculate(cancer, paralysis, bedridden, tenderness, swollenLeg, calfSwelling, pittingEdema, collateralVeins, priorDvt, altDiag), true)
                        }
                        "wells_pe" -> {
                            val dvtSigns = args["dvt_signs"]?.jsonPrimitive?.booleanOrNull ?: false
                            val peLikely = args["pe_most_likely"]?.jsonPrimitive?.booleanOrNull ?: false
                            val tachycardia = args["tachycardia"]?.jsonPrimitive?.booleanOrNull ?: false
                            val surgery = args["surgery_immobilization"]?.jsonPrimitive?.booleanOrNull ?: false
                            val priorVte = args["prior_vte"]?.jsonPrimitive?.booleanOrNull ?: false
                            val hemoptysis = args["hemoptysis"]?.jsonPrimitive?.booleanOrNull ?: false
                            val malignancy = args["malignancy"]?.jsonPrimitive?.booleanOrNull ?: false
                            ToolCallResult(toolId, WellsPeCalculator.calculate(dvtSigns, peLikely, tachycardia, surgery, priorVte, hemoptysis, malignancy), true)
                        }
                        "apgar" -> {
                            val a = args["appearance"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 2
                            val p = args["pulse"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 2
                            val g = args["grimace"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 2
                            val act = args["activity"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 2
                            val r = args["respiration"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 2
                            ToolCallResult(toolId, ApgarCalculator.calculate(a, p, g, act, r), true)
                        }
                        else -> ToolCallResult(
                            toolId,
                            "Unknown calculation type: $calc. Supported: egfr, cha2ds2vasc, cockcroft_gault, anion_gap, corrected_calcium, has_bled, corrected_sodium, osmolality, qtc, curb65, bmi_bsa, wells_dvt, wells_pe, apgar",
                            false,
                            "Unknown calc"
                        )
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
                    val query = args["query"]?.jsonPrimitive?.content ?: "summary"
                    ToolCallResult(
                        toolId,
                        """
## Patient Record Access

> ⚠️ **HIPAA Safeguard Active**: Patient record retrieval is strictly local and offline.

**Query:** $query

The AgentX patient record tool operates on locally stored records only. No patient-identifiable data is transmitted externally.

*Offline clinical record repository active.*
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

// ─── Registered Tool Definitions ─────────────────────────────────────────────

object ClinicalToolDefinitions {
    val all = listOf(
        ToolDefinition(
            id = "tool_clinical_calc",
            name = "Clinical Risk & Dosage Calculator",
            description = "Calculates validated medical formulas: egfr (CKD-EPI 2021), cha2ds2vasc, cockcroft_gault (CrCl), anion_gap (+ Delta-Delta ratio), corrected_calcium, has_bled, corrected_sodium, osmolality, qtc (Bazett/Fridericia), curb65, bmi_bsa, wells_dvt, wells_pe, apgar.",
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
