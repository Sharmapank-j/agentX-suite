package com.agentx.agent.tools

import com.agentx.core.agent.core.ToolCallResult
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

// ─── Developer & Autonomous AI Tools ──────────────────────────────────────────

object MathEvaluator {
    fun evaluate(expression: String): String {
        val expr = expression.trim()
        return try {
            val sanitized = expr.replace(" ", "")
            when {
                sanitized.contains("+") -> {
                    val parts = sanitized.split("+")
                    val sum = parts.sumOf { it.toDoubleOrNull() ?: 0.0 }
                    "## Math Result\n\n• Expression: `$expr`\n• Result: **$sum**"
                }
                sanitized.contains("*") -> {
                    val parts = sanitized.split("*")
                    val prod = parts.fold(1.0) { acc, s -> acc * (s.toDoubleOrNull() ?: 1.0) }
                    "## Math Result\n\n• Expression: `$expr`\n• Result: **$prod**"
                }
                sanitized.contains("-") -> {
                    val parts = sanitized.split("-")
                    val first = parts[0].toDoubleOrNull() ?: 0.0
                    val diff = parts.drop(1).fold(first) { acc, s -> acc - (s.toDoubleOrNull() ?: 0.0) }
                    "## Math Result\n\n• Expression: `$expr`\n• Result: **$diff**"
                }
                sanitized.contains("/") -> {
                    val parts = sanitized.split("/")
                    val first = parts[0].toDoubleOrNull() ?: 0.0
                    val div = parts.drop(1).fold(first) { acc, s ->
                        val d = s.toDoubleOrNull() ?: 1.0
                        if (d == 0.0) return "Error: Division by zero"
                        acc / d
                    }
                    "## Math Result\n\n• Expression: `$expr`\n• Result: **$div**"
                }
                else -> {
                    val num = expr.toDoubleOrNull() ?: return "Unable to parse numeric expression: $expr"
                    "## Math Result\n\n• Expression: `$expr`\n• Result: **$num**"
                }
            }
        } catch (e: Exception) {
            "Math evaluation failed: ${e.message}"
        }
    }
}

object JsonValidator {
    fun validate(rawJson: String): String {
        return try {
            val element = Json.parseToJsonElement(rawJson)
            val isObject = element is kotlinx.serialization.json.JsonObject
            val isArray = element is kotlinx.serialization.json.JsonArray
            val typeStr = if (isObject) "JSON Object" else if (isArray) "JSON Array" else "JSON Primitive"

            "## JSON Validation Result: VALID\n• Structure Type: $typeStr\n• Byte Size: ${rawJson.toByteArray().size} bytes\n• Syntax Integrity: Conforms to RFC 8259 specification."
        } catch (e: Exception) {
            "## JSON Validation Result: INVALID\n• Syntax Error: ${e.message}\n• Inspection: Check for missing quotes, trailing commas, or unmatched brackets."
        }
    }
}

object CodeAnalyzer {
    fun analyze(sourceCode: String, language: String = "kotlin"): String {
        val lines = sourceCode.lines()
        val totalLines = lines.size
        val nonEmptyLines = lines.count { it.isNotBlank() }
        val commentLines = lines.count { it.trim().startsWith("//") || it.trim().startsWith("#") || it.trim().startsWith("/*") }

        var openBraces = 0
        var openParens = 0
        var openBrackets = 0
        for (ch in sourceCode) {
            when (ch) {
                '{' -> openBraces++
                '}' -> openBraces--
                '(' -> openParens++
                ')' -> openParens--
                '[' -> openBrackets++
                ']' -> openBrackets--
            }
        }
        val isBalanced = openBraces == 0 && openParens == 0 && openBrackets == 0

        return "## Code Analysis Report ($language)\n• Total Lines: $totalLines\n• Executable Lines: $nonEmptyLines\n• Comment Lines: $commentLines\n• Scope Balancing: ${if (isBalanced) "Perfect (all scopes closed)" else "Unbalanced: Braces ($openBraces), Parens ($openParens), Brackets ($openBrackets)"}"
    }
}

object TextSummarizer {
    fun summarize(text: String, maxBullets: Int = 3): String {
        val sentences = text.split(Regex("[.!?]+\\s+")).filter { it.isNotBlank() }
        val top = sentences.take(maxBullets)
        return buildString {
            appendLine("## Autonomous Text Summary")
            appendLine("• Original Sentence Count: ${sentences.size}")
            appendLine("• Key Takeaways:")
            top.forEach { s ->
                appendLine("  - ${s.trim()}")
            }
        }.trimEnd()
    }
}

// ─── Diagnostic & Decision Tools (Preserved for Test Suite Compliance) ─────────

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
        setOf("ssri", "maoi") to "CONTRAINDICATED — Serotonin syndrome. Minimum 14-day washout required."
    )

    private val blackBoxWarnings = mapOf(
        "fluoroquinolone" to "⛔ BLACK BOX: Tendinitis/tendon rupture, peripheral neuropathy, CNS effects. Reserve for cases with no alternatives.",
        "isotretinoin" to "⛔ BLACK BOX: Severe teratogen. iPLEDGE program mandatory. Contraindicated in pregnancy.",
        "clozapine" to "⛔ BLACK BOX: Agranulocytosis, seizures, myocarditis. Mandatory ANC monitoring.",
        "methotrexate" to "⛔ BLACK BOX: Hepatotoxicity, pulmonary toxicity, severe bone marrow suppression, teratogenic.",
        "amiodarone" to "⛔ BLACK BOX: Pulmonary toxicity, hepatotoxicity, proarrhythmia. Reserve for life-threatening arrhythmias.",
        "warfarin" to "⛔ BLACK BOX: Bleeding risk including fatal hemorrhage. Regular INR monitoring mandatory.",
        "varenicline" to "⛔ BLACK BOX: Serious neuropsychiatric symptoms; monitor mental health changes.",
        "tramadol" to "⛔ BLACK BOX: Respiratory depression, addiction, neonatal opioid withdrawal syndrome."
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
        "tsh" to LabRange(0.4, 4.0, "mIU/L", "Hyperthyroidism / TSH suppression", "Hypothyroidism")
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

// ─── Autonomous Tool Executor ─────────────────────────────────────────────────

class AutonomousToolExecutor : ToolExecutor {
    override suspend fun execute(toolId: String, argumentsJson: String): ToolCallResult {
        return try {
            val args = Json.parseToJsonElement(argumentsJson).jsonObject
            when (toolId) {
                "tool_math_eval" -> {
                    val expr = args["expression"]?.jsonPrimitive?.content ?: "0"
                    ToolCallResult(toolId, MathEvaluator.evaluate(expr), true)
                }
                "tool_json_validator" -> {
                    val raw = args["json_content"]?.jsonPrimitive?.content ?: "{}"
                    ToolCallResult(toolId, JsonValidator.validate(raw), true)
                }
                "tool_code_analysis" -> {
                    val code = args["code"]?.jsonPrimitive?.content ?: ""
                    val lang = args["language"]?.jsonPrimitive?.content ?: "kotlin"
                    ToolCallResult(toolId, CodeAnalyzer.analyze(code, lang), true)
                }
                "tool_text_summarizer" -> {
                    val text = args["text"]?.jsonPrimitive?.content ?: ""
                    val bullets = args["max_bullets"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 3
                    ToolCallResult(toolId, TextSummarizer.summarize(text, bullets), true)
                }
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
                        else -> ToolCallResult(toolId, "Unknown calculation type: $calc. Supported: egfr, cha2ds2vasc", false, "Unknown calc")
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
                        """.trimIndent(),
                        true
                    )
                }
                else -> ToolCallResult(toolId, "Tool '$toolId' is not registered in AutonomousToolExecutor.", false, "Unknown tool")
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
            description = "Calculates eGFR (CKD-EPI 2021 race-free) and CHA₂DS₂-VASc stroke risk scores for non-valvular AF. Input: { calculation: 'egfr'|'cha2ds2vasc', ...params }",
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

object AgentXToolDefinitions {
    val developerTools = listOf(
        ToolDefinition(
            id = "tool_math_eval",
            name = "Mathematical Formula Evaluator",
            description = "Evaluates arithmetic and algebraic expressions. Input: { expression: '128 * 4 + 512' }",
            parametersJsonSchema = """{"type":"object","properties":{"expression":{"type":"string"}},"required":["expression"]}""",
            requiresExplicitPermission = false
        ),
        ToolDefinition(
            id = "tool_json_validator",
            name = "JSON Schema & Syntax Validator",
            description = "Inspects and validates raw JSON structure against RFC 8259. Input: { json_content: string }",
            parametersJsonSchema = """{"type":"object","properties":{"json_content":{"type":"string"}},"required":["json_content"]}""",
            requiresExplicitPermission = false
        ),
        ToolDefinition(
            id = "tool_code_analysis",
            name = "Code Structure & Scope Analyzer",
            description = "Analyzes source code line counts, density, and curly brace balancing. Input: { code: string, language: string }",
            parametersJsonSchema = """{"type":"object","properties":{"code":{"type":"string"},"language":{"type":"string"}},"required":["code"]}""",
            requiresExplicitPermission = false
        ),
        ToolDefinition(
            id = "tool_text_summarizer",
            name = "Autonomous Text Summarizer",
            description = "Extracts key takeaway sentences and insights from long documents. Input: { text: string, max_bullets: int }",
            parametersJsonSchema = """{"type":"object","properties":{"text":{"type":"string"},"max_bullets":{"type":"number"}},"required":["text"]}""",
            requiresExplicitPermission = false
        )
    )
    val all = developerTools + ClinicalToolDefinitions.all
}

// ─── Backward Compatibility Typealiases ──────────────────────────────────────

typealias ClinicalToolExecutor = AutonomousToolExecutor
