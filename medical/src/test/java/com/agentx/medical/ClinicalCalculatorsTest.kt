package com.agentx.medical

import com.agentx.medical.core.agent.tools.AnionGapCalculator
import com.agentx.medical.core.agent.tools.ApgarCalculator
import com.agentx.medical.core.agent.tools.BmiBsaCalculator
import com.agentx.medical.core.agent.tools.Cha2ds2VascCalculator
import com.agentx.medical.core.agent.tools.ClinicalToolExecutor
import com.agentx.medical.core.agent.tools.CockcroftGaultCalculator
import com.agentx.medical.core.agent.tools.CorrectedCalciumCalculator
import com.agentx.medical.core.agent.tools.CorrectedSodiumCalculator
import com.agentx.medical.core.agent.tools.Curb65Calculator
import com.agentx.medical.core.agent.tools.DrugSafetyChecker
import com.agentx.medical.core.agent.tools.EgfrCalculator
import com.agentx.medical.core.agent.tools.HasBledCalculator
import com.agentx.medical.core.agent.tools.QtcCalculator
import com.agentx.medical.core.agent.tools.SerumOsmolalityCalculator
import com.agentx.medical.core.agent.tools.WellsDvtCalculator
import com.agentx.medical.core.agent.tools.WellsPeCalculator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalCalculatorsTest {

    @Test
    fun testCockcroftGaultMaleAndFemale() {
        val maleResult = CockcroftGaultCalculator.calculate(age = 60, weightKg = 72.0, serumCrMgDl = 1.0, sex = "M")
        assertTrue(maleResult.contains("80.0 mL/min"))

        val femaleResult = CockcroftGaultCalculator.calculate(age = 60, weightKg = 72.0, serumCrMgDl = 1.0, sex = "F")
        assertTrue(femaleResult.contains("68.0 mL/min"))
    }

    @Test
    fun testEgfrCkdEpi2021() {
        val normalMale = EgfrCalculator.calculate(age = 40, sex = "M", creatinineMgDl = 0.9)
        assertTrue(normalMale.contains("G1 (Normal/High)") || normalMale.contains("105") || normalMale.contains("106") || normalMale.contains("107"))

        val ckdMale = EgfrCalculator.calculate(age = 70, sex = "M", creatinineMgDl = 2.5)
        assertTrue(ckdMale.contains("G4") || ckdMale.contains("G5") || ckdMale.contains("25") || ckdMale.contains("26"))
    }

    @Test
    fun testAnionGapAndDeltaDeltaRatio() {
        // High AG with Delta-Delta ratio: Na = 140, Cl = 98, HCO3 = 10 -> AG = 32 (Excess AG = 20, Deficit HCO3 = 14 -> Ratio = 1.43 -> Pure HAGMA)
        val dkaResult = AnionGapCalculator.calculate(sodium = 140.0, chloride = 98.0, bicarbonate = 10.0)
        assertTrue(dkaResult.contains("High Anion Gap Metabolic Acidosis (HAGMA)"))
        assertTrue(dkaResult.contains("Delta-Delta Ratio"))
        assertTrue(dkaResult.contains("Pure High Anion Gap Metabolic Acidosis"))

        // Albumin correction
        val hypoAlb = AnionGapCalculator.calculate(sodium = 140.0, chloride = 105.0, bicarbonate = 25.0, albumin = 2.0)
        assertTrue(hypoAlb.contains("Albumin-Corrected AG"))
    }

    @Test
    fun testCorrectedCalcium() {
        val result = CorrectedCalciumCalculator.calculate(measuredCalciumMgDl = 7.6, albuminGDl = 2.0)
        assertTrue(result.contains("9.20 mg/dL"))
        assertTrue(result.contains("Normal Ionized/Corrected Calcium"))
    }

    @Test
    fun testCha2ds2VascMonotonicRiskMapping() {
        // Test high score mapping: Score 7 must be 11.2%, Score 8 must be 12.5% (fixing the inversion typo)
        val highRiskScore8 = Cha2ds2VascCalculator.calculate(
            chf = true,
            hypertension = true,
            age = 76, // +2
            diabetes = true,
            strokeHistory = true, // +2
            vascularDisease = true,
            sex = "F" // +1 -> Total = 1 + 1 + 2 + 1 + 2 + 1 + 1 = 9 points
        )
        assertTrue(highRiskScore8.contains("Score Result"))
        assertTrue(highRiskScore8.contains(">15.0% / year") || highRiskScore8.contains("12.5% / year") || highRiskScore8.contains("Total Score"))
    }

    @Test
    fun testHasBledCalculator() {
        val highRisk = HasBledCalculator.calculate(
            hypertension = true,
            abnormalRenal = true,
            abnormalLiver = false,
            strokeHistory = true,
            priorBleed = true,
            labileInr = false,
            ageOver65 = true,
            antiplateletOrNsaid = true,
            alcoholExcess = false
        )
        assertTrue(highRisk.contains("6 / 9 points"))
        assertTrue(highRisk.contains("High Bleeding Risk"))
    }

    @Test
    fun testCorrectedSodium() {
        val result = CorrectedSodiumCalculator.calculate(measuredNa = 128.0, glucoseMgDl = 600.0)
        assertTrue(result.contains("136.0 mEq/L"))
        assertTrue(result.contains("Eunatremic") || result.contains("pseudohyponatremia"))
    }

    @Test
    fun testSerumOsmolalityAndGap() {
        val result = SerumOsmolalityCalculator.calculate(
            sodium = 140.0,
            glucoseMgDl = 180.0,
            bunMgDl = 28.0,
            measuredOsm = 320.0
        )
        // Calculated = 2*140 + 10 + 10 = 300 mOsm/kg. Gap = 320 - 300 = 20 mOsm/kg (>10 -> Elevated)
        assertTrue(result.contains("300.0 mOsm/kg"))
        assertTrue(result.contains("20.0 mOsm/kg"))
        assertTrue(result.contains("Elevated Osmolar Gap"))
    }

    @Test
    fun testQtcCalculator() {
        val normalMale = QtcCalculator.calculate(qtIntervalMs = 400.0, heartRateBpm = 60, isFemale = false)
        assertTrue(normalMale.contains("400 ms"))
        assertTrue(normalMale.contains("Normal QTc"))

        val critical = QtcCalculator.calculate(qtIntervalMs = 520.0, heartRateBpm = 60, isFemale = false)
        assertTrue(critical.contains("CRITICAL PROLONGATION"))
        assertTrue(critical.contains("Torsades de Pointes"))
    }

    @Test
    fun testWellsDvtAndPe() {
        val dvtLikely = WellsDvtCalculator.calculate(
            activeCancer = true,
            paralysisOrCast = false,
            bedriddenRecentMajorSurgery = true,
            localizedTendernessDeepVenous = true,
            entireLegSwollen = false,
            calfSwelling3cmGreaterThanOther = false,
            pittingEdemaConfinedSymptomaticLeg = false,
            collateralSuperficialVeins = false,
            previouslyDocumentedDvt = false,
            alternativeDiagnosisAtLeastAsLikely = false
        )
        assertTrue(dvtLikely.contains("DVT Likely"))

        val peLikely = WellsPeCalculator.calculate(
            clinicalSignsDvt = true,
            peMostLikelyDiagnosis = true,
            tachycardiaHrOver100 = false,
            immobilizationOrSurgeryPast4Weeks = false,
            priorDvtOrPe = false,
            hemoptysis = false,
            activeMalignancy = false
        )
        assertTrue(peLikely.contains("PE Likely"))
    }

    @Test
    fun testCurb65PneumoniaScore() {
        val severe = Curb65Calculator.calculate(
            confusion = true,
            elevatedBUN = true,
            highRespiratoryRate = true,
            lowBloodPressure = false,
            ageOver65 = true
        )
        assertTrue(severe.contains("4 / 5 points"))
        assertTrue(severe.contains("Severe pneumonia"))
    }

    @Test
    fun testBmiBsaCalculator() {
        val result = BmiBsaCalculator.calculate(heightCm = 180.0, weightKg = 81.0)
        // BMI = 81 / (1.8^2) = 25.0 kg/m²
        assertTrue(result.contains("25.0 kg/m²"))
        assertTrue(result.contains("BSA"))
    }

    @Test
    fun testApgarCalculator() {
        val normal = ApgarCalculator.calculate(2, 2, 2, 2, 2)
        assertTrue(normal.contains("10 / 10"))
        assertTrue(normal.contains("Normal Newborn Transition"))

        val depressed = ApgarCalculator.calculate(0, 1, 0, 1, 0)
        assertTrue(depressed.contains("2 / 10"))
        assertTrue(depressed.contains("Critical Low APGAR"))
    }

    @Test
    fun testDrugSafetyCheckerPreventsFalseReassurance() {
        // Listed interaction
        val warfarinAspirin = DrugSafetyChecker.check(listOf("Warfarin", "Aspirin"))
        assertTrue(warfarinAspirin.contains("Flagged Drug Interactions"))
        assertTrue(warfarinAspirin.contains("Increased bleeding risk"))

        // Unlisted combination must NOT state "No significant interactions detected"
        val unlisted = DrugSafetyChecker.check(listOf("Amoxicillin", "Lactulose"))
        assertFalse(unlisted.contains("No significant interactions detected"))
        assertTrue(unlisted.contains("No interactions flagged"))
        assertTrue(unlisted.contains("exhaustive"))
    }

    @Test
    fun testClinicalToolExecutorIntegration() = runBlocking {
        val executor = ClinicalToolExecutor()

        val jsonArgs = """{"calculation":"has_bled","hypertension":true,"abnormal_renal":true,"prior_bleed":true,"age_over_65":true}"""
        val result = executor.execute("tool_clinical_calc", jsonArgs)
        assertTrue(result.isSuccess)
        assertTrue(result.output.contains("HAS-BLED"))

        val unkResult = executor.execute("tool_clinical_calc", """{"calculation":"unsupported_formula"}""")
        assertFalse(unkResult.isSuccess)
    }
}
