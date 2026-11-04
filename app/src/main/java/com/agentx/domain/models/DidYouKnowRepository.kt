package com.agentx.domain.models

import kotlin.random.Random

data class DidYouKnowInsight(
    val id: String,
    val category: String,
    val emoji: String,
    val headline: String,
    val fact: String,
    val suggestedPrompt: String,
    val relatedChips: List<String>
)

object DidYouKnowRepository {

    private val insights = listOf(
        DidYouKnowInsight(
            id = "airllm_streaming",
            category = "Edge AI & Architecture",
            emoji = "⚡",
            headline = "Running 70B Models in 200MB RAM",
            fact = "While full 16-bit 70B LLMs require ~140 GB of VRAM, progressive layer-streaming (AirLLM) partitions weights into quantized block buffers on flash storage, streaming active transformer layers into memory one-by-one so even modest mobile hardware never crashes with Out-Of-Memory errors.",
            suggestedPrompt = "Explain how AirLLM layer-streaming executes multi-billion parameter inference in 200MB RAM without native crashes.",
            relatedChips = listOf("AirLLM Mechanics", "Quantization Q4_K_M", "RAM Optimization")
        ),
        DidYouKnowInsight(
            id = "transformers_attention",
            category = "Neural Networks",
            emoji = "🧠",
            headline = "Attention Operates Without Recurrence",
            fact = "The Transformer architecture ('Attention Is All You Need', 2017) discarded recurrent loops (RNN/LSTM) entirely. Instead, its self-attention mechanism computes pairwise token correlations in parallel (O(N²) dot-products), allowing massive scale across language, vision, and biology.",
            suggestedPrompt = "Walk me through the mathematical formulation of scaled dot-product self-attention: Attention(Q, K, V).",
            relatedChips = listOf("Scaled Dot-Product", "Multi-Head Attention", "KV Caching")
        ),
        DidYouKnowInsight(
            id = "quantum_superposition",
            category = "Quantum Computing",
            emoji = "🌌",
            headline = "Exponential State Spaces in Single Operations",
            fact = "A classical register of N bits can store exactly one integer out of 2^N possibilities at any given time. A quantum register of N entangled qubits exists in a coherent superposition of all 2^N states simultaneously until measurement.",
            suggestedPrompt = "How do quantum algorithms like Shor's algorithm use interference to eliminate incorrect states?",
            relatedChips = listOf("Quantum Superposition", "Shor's Algorithm", "Qubits vs Bits")
        ),
        DidYouKnowInsight(
            id = "apollo11_agc",
            category = "Computing History",
            emoji = "🚀",
            headline = "Landing on the Moon with 2KB of RAM",
            fact = "The 1969 Apollo Guidance Computer (AGC) landed Neil Armstrong and Buzz Aldrin on the Moon using only 2,048 words of RAM and 36,864 words of core-rope ROM clocked at 1.024 MHz. Its priority preemption scheduler safely discarded radar overload errors (1202 alarms) during descent.",
            suggestedPrompt = "How did Margaret Hamilton's priority-based asynchronous executive prevent the Apollo 11 computer overload from aborting the landing?",
            relatedChips = listOf("Apollo 11 1202 Alarm", "Core-Rope Memory", "Priority Scheduling")
        ),
        DidYouKnowInsight(
            id = "shannon_capacity",
            category = "Information Theory",
            emoji = "📡",
            headline = "Shannon's Universal Limit on Communication",
            fact = "In 1948, Claude Shannon proved that any communication channel has an absolute theoretical data capacity: C = B × log₂(1 + S/N). No modulation scheme or compression algorithm can ever transmit error-free data faster than this mathematical limit.",
            suggestedPrompt = "Derive the Shannon-Hartley theorem and explain how 5G and Wi-Fi 7 approach its channel capacity limit.",
            relatedChips = listOf("Shannon Limit", "Channel Capacity", "Signal-to-Noise Ratio")
        ),
        DidYouKnowInsight(
            id = "linux_eevdf",
            category = "Operating Systems",
            emoji = "🐧",
            headline = "The Linux Kernel's EEVDF Scheduler",
            fact = "Linux 6.6 replaced the Complete Fair Scheduler (CFS) with the Earliest Eligible Virtual Deadline First (EEVDF) algorithm. Designed by Peter Zijlstra based on 1995 queuing theory, EEVDF reduces latency for interactive desktop and mobile tasks by tracking virtual runtime eligibility before deadline selection.",
            suggestedPrompt = "Compare the Linux EEVDF scheduler against the classical CFS scheduler for thread latency.",
            relatedChips = listOf("Linux EEVDF", "Scheduler Internals", "vruntime vs Deadline")
        ),
        DidYouKnowInsight(
            id = "deepseek_moe",
            category = "AI Architecture",
            emoji = "🔀",
            headline = "Sparse Mixture-of-Experts (MoE)",
            fact = "Mixture-of-Experts architectures decouple total model parameter count from computational cost. A 671B parameter MoE model activates only ~37B parameters per token via learned gating routers, reducing latency by over 80% while preserving colossal knowledge capacity.",
            suggestedPrompt = "How does expert routing work in Mixture-of-Experts (MoE) architectures and what is auxiliary load balancing loss?",
            relatedChips = listOf("MoE Gating Router", "Sparse Inference", "Load Balancing")
        ),
        DidYouKnowInsight(
            id = "cap_theorem",
            category = "Distributed Systems",
            emoji = "🌐",
            headline = "The Inevitable Distributed Trade-off",
            fact = "Eric Brewer's CAP Theorem proves that a distributed data store can guarantee at most two out of three properties simultaneously: Consistency (all nodes see the same data), Availability (every non-failing node returns a response), and Partition Tolerance (system functions despite network drops).",
            suggestedPrompt = "Explain why network partitions make choosing between CP and AP mandatory in distributed databases.",
            relatedChips = listOf("CAP Theorem", "Eventual Consistency", "Raft Consensus")
        ),
        DidYouKnowInsight(
            id = "floating_point_ieee754",
            category = "Computer Arithmetic",
            emoji = "🔢",
            headline = "Why 0.1 + 0.2 Is Not 0.3",
            fact = "In standard IEEE 754 binary floating-point representation, numbers like 0.1 and 0.2 cannot be represented with finite binary fractions (1/10 produces an infinite repeating binary sequence), leading to the famous 0.1 + 0.2 = 0.30000000000000004 quirk in modern software.",
            suggestedPrompt = "Why does binary representation fail to represent decimal fractions exactly, and how do financial systems avoid rounding errors?",
            relatedChips = listOf("IEEE 754 Standard", "Floating-Point Drift", "BigDecimal Math")
        ),
        DidYouKnowInsight(
            id = "bpf_kernel_sandbox",
            category = "Systems Security",
            emoji = "🛡️",
            headline = "eBPF: A Safe In-Kernel Virtual Machine",
            fact = "Extended Berkeley Packet Filter (eBPF) allows developers to execute sandboxed bytecode directly inside the Linux kernel address space without recompiling kernel modules or rebooting, revolutionizing networking observability, tracing, and security monitoring.",
            suggestedPrompt = "Explain how the eBPF kernel verifier proves program safety (preventing infinite loops and memory corruption) at load time.",
            relatedChips = listOf("eBPF Verifier", "Kernel Tracing", "Cilium & Observability")
        ),
        DidYouKnowInsight(
            id = "flash_attention",
            category = "Hardware Acceleration",
            emoji = "🔥",
            headline = "IO-Aware Attention with SRAM Tiling",
            fact = "Tri Dao's FlashAttention sped up transformer inference by 3x-4x without altering math by recognizing that the bottleneck was memory bandwidth (HBM to GPU SRAM), not compute. By computing softmax incrementally in SRAM tiles, it avoided writing intermediate N × N attention matrices to HBM.",
            suggestedPrompt = "How does FlashAttention's online softmax tiling avoid writing the full attention matrix to GPU HBM?",
            relatedChips = listOf("FlashAttention", "SRAM vs HBM", "IO Complexity")
        ),
        DidYouKnowInsight(
            id = "turing_completeness",
            category = "Theory of Computation",
            emoji = "⚙️",
            headline = "Accidental Turing Machines",
            fact = "Turing completeness has been accidentally achieved in surprising places, including the game of Magic: The Gathering, CSS3 with HTML checkboxes, PowerPoint animations, the x86 mov instruction alone (movfuscator), and Minecraft redstone circuitry.",
            suggestedPrompt = "How can a system be proven Turing-complete, and what is the Halting Problem?",
            relatedChips = listOf("Turing Completeness", "Halting Problem", "Cellular Automata")
        )
    )

    fun getAllInsights(): List<DidYouKnowInsight> = insights

    fun getRandomInsight(excludeId: String? = null): DidYouKnowInsight {
        val pool = if (excludeId != null && insights.size > 1) {
            insights.filter { it.id != excludeId }
        } else {
            insights
        }
        return pool[Random.nextInt(pool.size)]
    }
}
