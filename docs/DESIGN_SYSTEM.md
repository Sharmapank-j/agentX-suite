# AgentX Suite — Unified Design System Specification (v1.0.0)

**Status:** Official Production Specification  
**Framework:** Jetpack Compose + Material Design 3 (M3)  
**Modules:** `:app`, `:medical`, `:admin`, `:core`  

---

## 1. Visual Language & Design Philosophy

The AgentX design system balances technical density with calm, spacious legibility.
Key principles:
1. **Calm & Roomy**: Generous internal padding (16–20dp on container cards, 12dp on bubble margins) prevents visual cognitive fatigue during long debugging or clinical sessions.
2. **Context-Aware Controls**: Multimodal attachment badges, model capabilities chips, and camera triggers appear only when the selected model or hardware profile supports them.
3. **High Contrast & Legibility**: Strict WCAG 2.1 AA compliance (minimum 4.5:1 for body copy, 3:1 for large display elements).
4. **Predictable Interaction**: Subtle haptic clicks on action confirmations, continuous progress indicators on multi-gigabyte GGUF model staging, and smooth animated layout transitions.

---

## 2. Color System & Semantic Tokens

### 2.1 Semantic Token Architecture
All three applications inherit from a unified token taxonomy implemented in their respective `Theme.kt`:

| Semantic Token | Light Mode Value | Dark Mode Value | Semantic Role |
| :--- | :--- | :--- | :--- |
| `colorScheme.primary` | Indigo-600 / Teal-600 / Emerald-600 | Electric Cyan (`#00F2FE`) / Clinical Mint (`#14B8A6`) / Matrix Bright (`#34D399`) | Primary actions, send buttons, active indicators |
| `colorScheme.primaryContainer`| Pastel Wash (`#EEF2FF` / `#CCFBF1` / `#D1FAE5`) | Deep Tint (`#0E2A47` / `#042F2E` / `#064E3B`) | Focused cards, active filter pills, highlighted chips |
| `colorScheme.secondary` | Sky-600 (`#0284C7`) / Cobalt (`#2563EB`) | Neon Indigo (`#4FACFE`) / Sapphire (`#38BDF8`) | Secondary actions, telemetry badges, drawer items |
| `colorScheme.background` | Clean Slate (`#F8FAFC`) | Cyber Void (`#06080F` / `#040A12` / `#050811`) | Root screen background |
| `colorScheme.surface` | Pure White (`#FFFFFF`) | Space Obsidian (`#0B101D` / `#081422` / `#0A101D`) | Content containers, settings cards, top app bars |
| `colorScheme.surfaceVariant`| Light Pearl (`#F1F5F9`) | Glass Slate (`#111827` / `#0D1E33` / `#10192A`) | Chat message bubbles, search input bars, bottom sheets |
| `colorScheme.outline` | Crisp Border (`#CBD5E1`) | Subtle Stroke (`#1E293B` / `#163252` / `#1E2B45`) | Card outlines, dividers, input borders |
| `colorScheme.error` | Crimson-600 (`#DC2626`) | Alert Rose (`#F43F5E`) | Critical warnings, disconnect states, high thermal alert |

---

## 3. Typographic Hierarchy

Text styles scale proportionally across device font size preferences using scalable pixels (`sp`):

```kotlin
// Material 3 Typography Scale
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)
```

---

## 4. Reusable UI Components

### 4.1 Chat Bubbles (`UserBubble` & `AssistantBubble`)
- **Assistant Bubble**:
  - Background: `MaterialTheme.colorScheme.surfaceVariant`.
  - Corner radius: `18.dp` with `4.dp` bottom-left anchor notch.
  - Padding: `14.dp` horizontal, `12.dp` vertical.
  - Streaming footer: Live telemetry pill displaying `⚡ [Tokens] tok • [Speed] t/s • [TTFT]ms` in high-contrast cyan/mint typography.
- **User Bubble**:
  - Background: `MaterialTheme.colorScheme.primaryContainer`.
  - Corner radius: `18.dp` with `4.dp` bottom-right anchor notch.
  - Text: `MaterialTheme.colorScheme.onPrimaryContainer`.
  - Multimodal preview thumbnail attached seamlessly above message text if image was provided.

### 4.2 Message Input Bar (`ChatInputBar`)
- Container: Floating capsule card with `28.dp` corner radius and `1.dp` subtle outline stroke.
- Insets: Automatically accounts for system software keyboard (`WindowInsets.ime.asPaddingValues()`) preventing UI obscuration.
- Multimodal Action:
  - If loaded model lacks vision projector: Camera/Gallery attachment button is cleanly collapsed to maximize typing real estate.
  - If loaded model has vision projector: Camera/Gallery attachment icon renders with a glowing cyan badge.

### 4.3 Model Management Cards (`ModelCard`)
- Displays Model Architecture (`Llama`, `Qwen`, `Gemma`, `Mistral`), Parameter Count (`3B`, `7B`), Quantization (`Q4_K_M`), and Context Window (`4096`).
- Storage Progress Bar: Renders smooth transfer percentage during multi-GB GGUF copy/staging with bytes remaining.
- Action Buttons:
  - **Load / Hot-Swap**: Unloads active model from RAM and loads target model within a single mutex transaction.
  - **Benchmark**: Runs 1-tap on-device speed benchmark reporting prompt eval and token generation rates.
  - **Delete**: Cleans staged `.gguf` and associated `.mmproj` projector files safely without path traversal.

### 4.4 Diagnostic & Telemetry Panels
- Live RAM utilization bar with color-coded safety tiers:
  - `< 60%`: Green (Safe)
  - `60%–80%`: Amber (Moderate Memory Footprint)
  - `> 80%`: Red (Low Memory Warning with 1-tap Unload button)
- Thermal & Battery status:
  - Displays device battery percentage, charging state, and SoC temperature in Celsius with thermal throttling alerts (`THROTTLED` / `SEVERE`).

---

## 5. Responsive Design & Multi-Screen Support

All screens utilize Compose adaptive constraints:
- **Small Screens (< 360dp width)**: Text sizes scale gracefully down; top bar title truncates with ellipsis; chip lists scroll horizontally.
- **Standard Phones (360dp–600dp)**: Standard 1-column conversational and card layouts.
- **Foldables & Tablets (> 600dp width)**:
  - Navigation switches from bottom navigation bar to permanent navigation rail.
  - Chat screen and model management screens utilize multi-pane Master-Detail layout when folded out.
- **Keyboard Handling**: `Modifier.imePadding()` ensures chat bubbles scroll automatically into view as the virtual keyboard appears.

---

## 6. Accessibility (A11y) & WCAG Compliance

1. **Touch Targets**: All interactive elements (icon buttons, toggles, chips) enforce a minimum touch target size of **48dp × 48dp** via `Modifier.minimumInteractiveComponentSize()`.
2. **Screen Reader Support**: Every icon button has an explicit, localized `contentDescription` (e.g. `"Send message"`, `"Attach image"`, `"Stop generation"`, `"Open model drawer"`).
3. **Color Contrast**: All text-to-background combinations meet or exceed the 4.5:1 contrast requirement under both Dark and Light themes.
4. **Haptics**: Tactile feedback is triggered through `HapticFeedbackHelper` only when user-enabled in Settings, respecting system accessibility vibration preferences.
