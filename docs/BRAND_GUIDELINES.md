# AgentX Suite — Brand Identity & Design Guidelines (v1.0.0)

**Author:** Principal Brand Identity Designer & Product Architect  
**Version:** 1.0.0 (Production)  
**Date:** 2026-11-10  

---

## 1. Brand Philosophy & Core Values

The **AgentX Suite** identity communicates:
1. **Artificial Intelligence**: Advanced neural cognitive processing running natively on edge silicon.
2. **Precision & Determinism**: Mathematical reliability, medical validation, and zero hallucination where facts matter.
3. **Technical Sophistication**: High-performance C++ JNI architecture, low memory footprints, and low-latency P2P streaming.
4. **Reliability & Privacy**: Zero telemetry leaks, local encrypted storage, and offline sovereign inference.
5. **Modern Engineering**: Minimalist, functional visual language free of generic clipart or cliché robot heads.

---

## 2. Logo System & Construction

### 2.1 The Neural-X Singularity Motif
The primary mark of AgentX is a precision geometric fusion of:
- **Two Interlocking Chamfered Vector Beams**: Representing cross-network routing and bidirectional neural flow.
- **Central Singularity Core**: A diamond aperture and glowing circular emitter representing the local reasoning engine.
- **Four Synaptic Precision Nodes**: Anchored at the terminal boundaries of the X structural grid, representing multi-agent collaboration, memory retention, and tool augmentation.
- **Synaptic Filaments**: Curved arcs subtly binding the nodes into a coherent synaptic mesh.

```
       (Top-Left Node)            (Top-Right Node)
              ● ─────────────---─────────── ●
               \                         /
                \     /\         /\     /
                 \   /  \       /  \   /
                  \ /    \     /    \ /
                   X      \   /      X
                           \ /
                       ◆ Singularity
                           / \
                   X      /   \      X
                  / \    /     \    / \
                 /   \  /       \  /   \
                /     \/         \/     \
               /                         \
              ● ─────────────---─────────── ●
      (Bottom-Left Node)         (Bottom-Right Node)
```

### 2.2 Mathematical Proportions & Grid
- **Bounding Box**: 512 × 512 units (SVG ViewBox).
- **Beam Width**: 44 units (chamfered bevels at 45°).
- **Node Outer Radius**: 24 units.
- **Node Inner Core**: 10 units.
- **Aperture Diamond**: 76 units diagonal.
- **Central Singularity Point**: 24 units diameter.
- **Safe Area (Android Adaptive Icon Grid)**:
  - Total canvas: 108dp × 108dp.
  - Safe inner content circle: 72dp diameter (centered at x=54, y=54, padding 18dp on all sides). All critical nodes and beams reside strictly within `18dp <= x,y <= 90dp`.

---

## 3. Product Family Variations

All three applications in the AgentX Suite share the identical geometric Neural-X silhouette to ensure unmistakable brand cohesion, while carrying application-specific colorways and emblems:

| Application | Role | Core Colors | Distinctive Domain Emblem | Background |
| :--- | :--- | :--- | :--- | :--- |
| **AgentX** (`:app`) | Primary consumer AI assistant | Deep Neural Indigo (`#6366F1`) & Electric Cyan (`#06B6D4`) | Pure Neural Core with floating synaptic filaments | Cosmic Charcoal (`#090D16`) |
| **AgentX Medical** (`:medical`) | Clinical decision-support AI | Clinical Teal (`#0D9488` / `#14B8A6`) & Vital Mint (`#10B981`) | Precision clinical cross datum bar & biometric EKG rhythm pulse | Surgical Slate (`#041C1A`) |
| **AgentX Serve** (`:admin`) | Edge inference server & gateway | Infrastructure Emerald (`#059669`) & Signal Sapphire (`#2563EB`) | Dual broadcast telemetry arcs & server chassis anchor base | Server Carbon (`#0A0E17`) |

---

## 4. Typography & Wordmark

### 4.1 Wordmark Architecture
- **"Agent"**: Clean, humanist geometric sans-serif (Inter / Roboto / System San-Serif), Font Weight 700 (Bold), Letter Spacing `-2px`.
- **"X"**: Ultra-heavy display glyph, Font Weight 900 (Black), styled with the signature directional linear gradient (`#22D3EE` -> `#818CF8`).
- **Tagline**: All-caps tracking `+6px`, Font Weight 600 (Semi-Bold), Color `#94A3B8` (Slate-400).

### 4.2 Typographic Hierarchy
| Role | Size (sp) | Weight | Line Height | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Display** | 32sp | 800 Bold | 40sp | Hero metrics, benchmark scores |
| **Headline** | 24sp | 700 Bold | 32sp | Screen titles, primary cards |
| **Title** | 18sp | 600 Semi-Bold | 24sp | Section headers, dialog headers |
| **Body Large** | 15sp | 400 Regular | 22sp | AI chat messages, markdown content |
| **Body Medium** | 13sp | 400 Regular | 18sp | Metadata, model parameter chips |
| **Label Small** | 11sp | 600 Semi-Bold | 14sp | Badges, throughput tags (`tok/s`) |

---

## 5. Color Palette & Semantic Tokens

### 5.1 Primary Brand Palette
```css
/* AgentX Assistant */
--agentx-indigo-400: #818CF8;
--agentx-indigo-500: #6366F1;
--agentx-indigo-600: #4F46E5;
--agentx-cyan-400:   #38BDF8;
--agentx-cyan-500:   #06B6D4;

/* AgentX Medical */
--med-teal-400:      #2DD4BF;
--med-teal-500:      #14B8A6;
--med-teal-600:      #0D9488;
--med-mint-400:      #34D399;
--med-mint-500:      #10B981;

/* AgentX Serve */
--serve-emerald-500: #10B981;
--serve-emerald-600: #059669;
--serve-sapphire-400:#60A5FA;
--serve-sapphire-500:#3B82F6;
--serve-sapphire-600:#2563EB;
```

### 5.2 Functional UI Tokens (Dark & Light)
| Token | Dark Mode Value | Light Mode Value | Semantic Role |
| :--- | :--- | :--- | :--- |
| `surfaceBackground` | `#090D16` | `#F8FAFC` | Primary window background |
| `surfaceCard` | `#111827` | `#FFFFFF` | Message cards, model tiles |
| `surfaceElevated` | `#1E293B` | `#F1F5F9` | Modals, bottom sheets, dropdowns |
| `borderSubtle` | `#1E293B` | `#E2E8F0` | Dividers, card boundaries |
| `textPrimary` | `#F8FAFC` | `#0F172A` | Primary titles, chat text |
| `textSecondary` | `#94A3B8` | `#64748B` | Subtext, timestamps, token counters |
| `statusSuccess` | `#10B981` | `#059669` | Connected, healthy, optimal thermal |
| `statusWarning` | `#F59E0B` | `#D97706` | Throttling, low battery, cloud fallback |
| `statusError` | `#EF4444` | `#DC2626` | Disconnected, load failure, high thermal |

---

## 6. Iconography & Vector Assets

### 6.1 Generated Asset Manifest
All brand assets are rendered in production formats and stored under `assets/branding/`:

1. `assets/branding/agentx_primary_logo.svg` — Standard horizontal lockup (Symbol + Wordmark on Dark).
2. `assets/branding/agentx_secondary_logo.svg` — Stacked vertical lockup (Symbol over Wordmark on Dark).
3. `assets/branding/agentx_symbol.svg` — Standalone geometric Neural-X symbol.
4. `assets/branding/agentx_wordmark.svg` — Standalone typographic wordmark.
5. `assets/branding/agentx_light_logo.svg` — Horizontal lockup optimized for light themes (`#F8FAFC`).
6. `assets/branding/agentx_dark_logo.svg` — Horizontal lockup optimized for dark themes (`#090D16`).
7. `assets/branding/agentx_monochrome_logo.svg` — Pure high-contrast monochrome version (`#000000` / `#FFFFFF`).
8. `assets/branding/favicon.svg` — Compact 64x64 favicon for documentation and WebChat.
9. `assets/branding/store/agentx_512.png` & `.svg` — 512x512 Google Play store icon for AgentX.
10. `assets/branding/store/agentx_medical_512.png` & `.svg` — 512x512 Google Play store icon for AgentX Medical.
11. `assets/branding/store/agentx_serve_512.png` & `.svg` — 512x512 Google Play store icon for AgentX Serve.

---

## 7. Rules of Correct Usage

### 7.1 Minimum Sizes & Clear Space
- **Minimum Digital Size**: 24dp (standalone symbol); 120dp wide (horizontal logo with wordmark).
- **Clear Space**: Always maintain a clear margin around the mark equal to the width of the central singularity diamond (approx. 20% of symbol height).
- **Favicon / Tiny Renders**: Use `favicon.svg` or the symbol without synaptic arcs to maintain crisp edge definition below 32px.

### 7.2 Prohibited Usage
- ❌ **Do not warp or skew**: Never stretch, squeeze, or alter the 1:1 aspect ratio of the Neural-X.
- ❌ **Do not substitute colors**: Never apply arbitrary rainbow or non-brand gradients to the primary beams.
- ❌ **Do not crowd the mark**: Never place text or extraneous icons inside the 72dp adaptive safe area.
- ❌ **Do not use raster scaling**: Never upscale low-resolution PNGs; always use vector SVGs or Android Vector Drawables (`.xml`).
- ❌ **Do not combine domain emblems**: Never place the medical EKG line on AgentX Serve or the antenna broadcast arcs on AgentX Medical.
