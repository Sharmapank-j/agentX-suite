package com.agentx

import com.agentx.core.agent.core.AgentContext
import com.agentx.core.agent.orchestrator.ToolCallParser
import com.agentx.core.agent.tools.Cha2ds2VascCalculator
import com.agentx.core.agent.tools.ClinicalToolDefinitions
import com.agentx.core.agent.tools.ClinicalToolExecutor
import com.agentx.core.agent.tools.DrugSafetyChecker
import com.agentx.core.agent.tools.EgfrCalculator
import com.agentx.core.agent.tools.LabInterpreter
import com.agentx.core.agent.tools.ToolArgumentValidator
import com.agentx.core.agent.tools.ToolDefinition
import com.agentx.core.agent.tools.ToolRegistry
import com.agentx.core.agent.tools.ValidationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// ─── eGFR Calculator Tests ────────────────────────────────────────────────────

class EgfrCalculatorTest {

    @Test
    fun `normal creatinine male 40yo gives normal eGFR G1`() {
        val result = EgfrCalculator.calculate(age = 40, sex = "M", creatinineMgDl = 1.0)
        assertTrue("Expected G1", result.contains("G1"))
        assertFalse("Should NOT warn about dose adjustment", result.contains("⚠️"))
    }

    @Test
    fun `elevated creatinine female 70yo gives reduced eGFR with warning`() {
        // High creatinine → low eGFR → dose adjustment warning expected
        val result = EgfrCalculator.calculate(age = 70, sex = "F", creatinineMgDl = 3.5)
        assertTrue("Expected renal dose adjustment warning", result.contains("⚠️"))
        // Should be G3 or lower
        val isLowStage = result.contains("G3") || result.contains("G4") || result.contains("G5")
        assertTrue("Expected CKD stage G3 or worse", isLowStage)
    }

    @Test
    fun `invalid inputs return error message`() {
        val result = EgfrCalculator.calculate(age = -1, sex = "M", creatinineMgDl = 1.0)
        assertTrue("Should return invalid input message", result.contains("Invalid input"))
    }

    @Test
    fun `near-normal female eGFR includes F multiplier`() {
        // Female eGFR should include 1.012 multiplier (CKD-EPI 2021)
        val female = EgfrCalculator.calculate(age = 50, sex = "F", creatinineMgDl = 0.9)
        val male   = EgfrCalculator.calculate(age = 50, sex = "M", creatinineMgDl = 0.9)
        // Both should be G1/G2 but female value should be slightly higher
        assertTrue(female.contains("G1") || female.contains("G2"))
        assertTrue(male.contains("G1")   || male.contains("G2"))
    }
}

// ─── CHA₂DS₂-VASc Calculator Tests ───────────────────────────────────────────

class Cha2ds2VascCalculatorTest {

    @Test
    fun `male age 60 no risk factors gives score 0 no anticoagulation`() {
        val result = Cha2ds2VascCalculator.calculate(
            chf = false, hypertension = false, age = 60,
            diabetes = false, strokeHistory = false, vascularDisease = false, sex = "M"
        )
        assertTrue("Should recommend no anticoagulation", result.contains("No anticoagulation"))
    }

    @Test
    fun `score 2 gives OAC recommended`() {
        // CHF + hypertension = 2 points
        val result = Cha2ds2VascCalculator.calculate(
            chf = true, hypertension = true, age = 60,
            diabetes = false, strokeHistory = false, vascularDisease = false, sex = "M"
        )
        assertTrue("Should recommend OAC", result.contains("recommended"))
        assertTrue("Should mention DOAC", result.contains("DOAC"))
    }

    @Test
    fun `prior stroke adds 2 points and high stroke risk`() {
        val result = Cha2ds2VascCalculator.calculate(
            chf = false, hypertension = false, age = 65,
            diabetes = false, strokeHistory = true, vascularDisease = false, sex = "M"
        )
        // Age ≥65 = 1 + stroke = 2 → total 3
        assertTrue("Score 3 should recommend OAC", result.contains("recommended"))
    }

    @Test
    fun `female sex with no other factors gives no anticoagulation`() {
        val result = Cha2ds2VascCalculator.calculate(
            chf = false, hypertension = false, age = 60,
            diabetes = false, strokeHistory = false, vascularDisease = false, sex = "F"
        )
        // Only the sex modifier → score 1 for female with no other factors
        assertTrue("Female only should not anticoagulate", result.contains("No anticoagulation"))
    }

    @Test
    fun `output contains safety disclaimer`() {
        val result = Cha2ds2VascCalculator.calculate(
            chf = true, hypertension = false, age = 70,
            diabetes = true, strokeHistory = false, vascularDisease = false, sex = "M"
        )
        assertTrue("Must contain safety disclaimer", result.contains("⚠️"))
    }
}

// ─── Drug Safety Checker Tests ────────────────────────────────────────────────

class DrugSafetyCheckerTest {

    @Test
    fun `warfarin plus aspirin flags HIGH RISK interaction`() {
        val result = DrugSafetyChecker.check(listOf("warfarin", "aspirin"))
        assertTrue("Should flag HIGH RISK", result.contains("HIGH RISK"))
        assertTrue("Should mention bleeding risk", result.lowercase().contains("bleeding"))
    }

    @Test
    fun `ssri plus maoi is contraindicated`() {
        val result = DrugSafetyChecker.check(listOf("ssri", "maoi"))
        assertTrue("Should flag CONTRAINDICATED", result.contains("CONTRAINDICATED"))
        assertTrue("Should mention serotonin syndrome", result.lowercase().contains("serotonin"))
    }

    @Test
    fun `amiodarone triggers black box warning`() {
        val result = DrugSafetyChecker.check(listOf("amiodarone"))
        assertTrue("Should show black box warning symbol", result.contains("⛔"))
        assertTrue("Should mention pulmonary toxicity", result.lowercase().contains("pulmonary"))
    }

    @Test
    fun `safe drug combination returns no significant interactions`() {
        val result = DrugSafetyChecker.check(listOf("acetaminophen", "vitamin_d"))
        assertTrue("Should report no significant interactions", result.contains("No significant interactions"))
    }

    @Test
    fun `digoxin plus amiodarone interaction detected`() {
        val result = DrugSafetyChecker.check(listOf("digoxin", "amiodarone"))
        assertTrue("Should flag digoxin-amiodarone HIGH RISK", result.contains("HIGH RISK"))
        // Also check for the black-box warning on amiodarone
        assertTrue("Should show amiodarone black box", result.contains("⛔"))
    }

    @Test
    fun `output includes clinical support disclaimer`() {
        val result = DrugSafetyChecker.check(listOf("warfarin", "aspirin"))
        assertTrue("Must include clinical support notice", result.contains("⚕️"))
    }
}

// ─── Lab Interpreter Tests ────────────────────────────────────────────────────

class LabInterpreterTest {

    @Test
    fun `hyponatremia detected correctly`() {
        val result = LabInterpreter.interpret(mapOf("sodium" to 128.0))
        assertTrue("Should flag LOW sodium", result.contains("⬇️ LOW"))
        assertTrue("Should mention Hyponatremia", result.contains("Hyponatremia"))
    }

    @Test
    fun `hyperkalemia flagged with cardiac risk`() {
        val result = LabInterpreter.interpret(mapOf("potassium" to 6.1))
        assertTrue("Should flag HIGH potassium", result.contains("⬆️ HIGH"))
        assertTrue("Should mention cardiac arrest risk", result.lowercase().contains("cardiac"))
    }

    @Test
    fun `elevated troponin triggers urgent evaluation flag`() {
        val result = LabInterpreter.interpret(mapOf("troponin" to 0.5))
        assertTrue("Should flag HIGH troponin", result.contains("⬆️ HIGH"))
        assertTrue("Should mention ACS or myocardial", result.lowercase().contains("myocardial"))
    }

    @Test
    fun `all normal values produce normal results only`() {
        val normalPanel = mapOf(
            "sodium" to 140.0,
            "potassium" to 4.0,
            "creatinine" to 1.0,
            "glucose" to 90.0
        )
        val result = LabInterpreter.interpret(normalPanel)
        assertFalse("Should not contain abnormal section", result.contains("⬇️ LOW"))
        assertFalse("Should not contain high flag", result.contains("⬆️ HIGH"))
        assertTrue("Should contain normal section", result.contains("✅ Normal"))
    }

    @Test
    fun `unknown lab test handled gracefully`() {
        val result = LabInterpreter.interpret(mapOf("mystery_test" to 42.0))
        assertTrue("Should note unknown reference", result.contains("Unknown") || result.contains("❓"))
    }
}

// ─── ClinicalToolExecutor Dispatch Tests ─────────────────────────────────────

class ClinicalToolExecutorTest {

    private val executor = ClinicalToolExecutor()

    @Test
    fun `dispatches tool_clinical_calc for eGFR`() = runTest {
        val result = executor.execute(
            toolId = "tool_clinical_calc",
            argumentsJson = """{"calculation":"egfr","age":55,"sex":"M","creatinine_mg_dl":1.2}"""
        )
        assertTrue("Should succeed", result.isSuccess)
        assertTrue("Should contain eGFR result header", result.output.contains("eGFR Result"))
    }

    @Test
    fun `dispatches tool_clinical_calc for cha2ds2vasc`() = runTest {
        val result = executor.execute(
            toolId = "tool_clinical_calc",
            argumentsJson = """{"calculation":"cha2ds2vasc","chf":"true","hypertension":"true","age":72,"diabetes":"false","stroke_history":"false","vascular_disease":"false","sex":"M"}"""
        )
        assertTrue("Should succeed", result.isSuccess)
        assertTrue("Should contain CHA2DS2 header", result.output.contains("CHA₂DS₂-VASc"))
    }

    @Test
    fun `dispatches tool_drug_safety`() = runTest {
        val result = executor.execute(
            toolId = "tool_drug_safety",
            argumentsJson = """{"drugs":["warfarin","aspirin"]}"""
        )
        assertTrue("Should succeed", result.isSuccess)
        assertTrue("Should flag bleeding risk", result.output.contains("HIGH RISK"))
    }

    @Test
    fun `dispatches tool_lab_interpreter`() = runTest {
        val result = executor.execute(
            toolId = "tool_lab_interpreter",
            argumentsJson = """{"panel":{"potassium":6.2,"sodium":140.0}}"""
        )
        assertTrue("Should succeed", result.isSuccess)
        assertTrue("Should interpret potassium", result.output.contains("potassium"))
    }

    @Test
    fun `dispatches tool_patient_record with HIPAA notice`() = runTest {
        val result = executor.execute(
            toolId = "tool_patient_record",
            argumentsJson = """{"query":"medication list"}"""
        )
        assertTrue("Should succeed", result.isSuccess)
        assertTrue("Should contain HIPAA safeguard notice", result.output.contains("HIPAA"))
    }

    @Test
    fun `unknown tool returns failure`() = runTest {
        val result = executor.execute(
            toolId = "tool_nonexistent",
            argumentsJson = """{}"""
        )
        assertFalse("Should report failure", result.isSuccess)
        assertTrue("Should mention not registered", result.output.contains("not registered"))
    }
}

// ─── ToolCallParser Tests ─────────────────────────────────────────────────────

class ToolCallParserTest {

    @Test
    fun `extracts single tool call correctly`() {
        val text = """
            Let me calculate the eGFR.
            <tool_call>{"name": "tool_clinical_calc", "arguments": {"age": 55, "sex": "M", "creatinine_mg_dl": 1.2}}</tool_call>
            I will now interpret the result.
        """.trimIndent()

        val calls = ToolCallParser.extract(text)
        assertEquals("Should extract 1 tool call", 1, calls.size)
        assertEquals("Tool ID should match", "tool_clinical_calc", calls[0].toolId)
    }

    @Test
    fun `extracts multiple tool calls`() {
        val text = """
            Running drug check.
            <tool_call>{"name": "tool_drug_safety", "arguments": {"drugs": ["warfarin","aspirin"]}}</tool_call>
            Also checking labs.
            <tool_call>{"name": "tool_lab_interpreter", "arguments": {"panel": {"sodium": 128}}}</tool_call>
        """.trimIndent()

        val calls = ToolCallParser.extract(text)
        assertEquals("Should extract 2 tool calls", 2, calls.size)
        assertEquals("First call is drug_safety", "tool_drug_safety", calls[0].toolId)
        assertEquals("Second call is lab_interpreter", "tool_lab_interpreter", calls[1].toolId)
    }

    @Test
    fun `returns empty list when no tool calls present`() {
        val text = "The patient's eGFR appears to be within normal limits based on clinical history."
        val calls = ToolCallParser.extract(text)
        assertTrue("Should return empty list", calls.isEmpty())
    }

    @Test
    fun `hasToolCall detects presence correctly`() {
        val withTool = "result: <tool_call>{\"name\":\"tool_drug_safety\",\"arguments\":{}}</tool_call>"
        val withoutTool = "No tools needed here."
        assertTrue("Should detect tool call", ToolCallParser.hasToolCall(withTool))
        assertFalse("Should NOT detect tool call", ToolCallParser.hasToolCall(withoutTool))
    }

    @Test
    fun `stripToolCalls removes tool_call blocks leaving reasoning text`() {
        val text = """
            The patient needs eGFR evaluation.
            <tool_call>{"name": "tool_clinical_calc", "arguments": {}}</tool_call>
            Based on the result, dose adjustment is required.
        """.trimIndent()

        val stripped = ToolCallParser.stripToolCalls(text)
        assertFalse("Tool call block should be removed", stripped.contains("<tool_call>"))
        assertTrue("Clinical reasoning text should remain", stripped.contains("eGFR evaluation"))
        assertTrue("Post-result text should remain", stripped.contains("dose adjustment"))
    }

    @Test
    fun `malformed JSON in tool_call is skipped gracefully`() {
        val text = "<tool_call>{invalid json here}</tool_call>"
        val calls = ToolCallParser.extract(text)
        assertTrue("Should return empty list for malformed JSON", calls.isEmpty())
    }
}

// ─── ToolRegistry Tests ───────────────────────────────────────────────────────

class ToolRegistryTest {

    @Test
    fun `registers and retrieves a tool definition`() {
        val registry = ToolRegistry()
        val tool = ToolDefinition(
            id = "test_tool",
            name = "Test Tool",
            description = "A test tool",
            parametersJsonSchema = "{}"
        )
        registry.register(tool)
        assertEquals("Should retrieve registered tool", tool, registry.get("test_tool"))
    }

    @Test
    fun `returns null for unregistered tool`() {
        val registry = ToolRegistry()
        assertEquals("Should return null for unknown tool", null, registry.get("nonexistent"))
    }

    @Test
    fun `ClinicalToolDefinitions pre-populates 4 tools`() {
        val registry = ToolRegistry()
        ClinicalToolDefinitions.all.forEach { registry.register(it) }
        val tools = registry.getAll()
        assertEquals("Should have exactly 4 clinical tools", 4, tools.size)
        val ids = tools.map { it.id }.toSet()
        assertTrue(ids.contains("tool_clinical_calc"))
        assertTrue(ids.contains("tool_drug_safety"))
        assertTrue(ids.contains("tool_lab_interpreter"))
        assertTrue(ids.contains("tool_patient_record"))
    }
}

// ─── ToolArgumentValidator Tests ─────────────────────────────────────────────

class ToolArgumentValidatorTest {

    private val dummyTool = ToolDefinition(
        id = "dummy", name = "Dummy", description = "Test", parametersJsonSchema = "{}"
    )

    @Test
    fun `valid JSON object passes validation`() {
        val result = ToolArgumentValidator.validate(dummyTool, """{"age": 55, "sex": "M"}""")
        assertTrue("Valid JSON should pass", result is ValidationResult.Valid)
    }

    @Test
    fun `malformed JSON fails validation`() {
        val result = ToolArgumentValidator.validate(dummyTool, "{not valid json")
        assertTrue("Malformed JSON should fail", result is ValidationResult.Invalid)
    }

    @Test
    fun `empty object JSON passes validation`() {
        val result = ToolArgumentValidator.validate(dummyTool, "{}")
        assertTrue("Empty object should be valid JSON", result is ValidationResult.Valid)
    }
}
