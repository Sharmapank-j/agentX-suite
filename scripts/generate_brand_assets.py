#!/usr/bin/env python3
"""
AgentX Suite Brand Asset Generator
Generates full vector SVG branding suite, high-resolution store icons (512x512),
and Android Adaptive Vector Drawables for AgentX, AgentX Medical, and AgentX Serve.
"""

import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT_DIR = Path(__file__).resolve().parent.parent
BRANDING_DIR = ROOT_DIR / "assets" / "branding"
STORE_DIR = BRANDING_DIR / "store"
PNG_DIR = BRANDING_DIR / "png"

BRANDING_DIR.mkdir(parents=True, exist_ok=True)
STORE_DIR.mkdir(parents=True, exist_ok=True)
PNG_DIR.mkdir(parents=True, exist_ok=True)

# ----------------------------------------------------------------------
# 1. SVG Definitions
# ----------------------------------------------------------------------

# Pure Symbol SVG (AgentX Neural Core)
SYMBOL_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <linearGradient id="beamPrimary" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#818CF8" />
      <stop offset="50%" stop-color="#6366F1" />
      <stop offset="100%" stop-color="#4F46E5" />
    </linearGradient>
    <linearGradient id="beamSecondary" x1="100%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#38BDF8" />
      <stop offset="50%" stop-color="#22D3EE" />
      <stop offset="100%" stop-color="#06B6D4" />
    </linearGradient>
    <radialGradient id="coreGlow" cx="50%" cy="50%" r="50%">
      <stop offset="0%" stop-color="#FFFFFF" />
      <stop offset="45%" stop-color="#E0F2FE" />
      <stop offset="80%" stop-color="#38BDF8" stop-opacity="0.8" />
      <stop offset="100%" stop-color="#0284C7" stop-opacity="0" />
    </radialGradient>
    <filter id="subtleDrop" x="-10%" y="-10%" width="120%" height="120%">
      <feDropShadow dx="0" dy="8" stdDeviation="12" flood-color="#000000" flood-opacity="0.45" />
    </filter>
  </defs>

  <!-- Neural Synaptic Arch Filaments -->
  <path d="M 160,138 Q 256,92 352,138" fill="none" stroke="#6366F1" stroke-width="4" stroke-dasharray="6,6" opacity="0.6" />
  <path d="M 160,374 Q 256,420 352,374" fill="none" stroke="#06B6D4" stroke-width="4" stroke-dasharray="6,6" opacity="0.6" />
  <path d="M 160,138 Q 110,256 160,374" fill="none" stroke="#4F46E5" stroke-width="3" stroke-dasharray="4,4" opacity="0.4" />
  <path d="M 352,138 Q 402,256 352,374" fill="none" stroke="#22D3EE" stroke-width="3" stroke-dasharray="4,4" opacity="0.4" />

  <!-- Diagonal Primary Beam (Indigo) -->
  <polygon points="172,126 216,126 352,386 308,386" fill="url(#beamPrimary)" filter="url(#subtleDrop)" />

  <!-- Diagonal Secondary Beam (Electric Cyan) -->
  <polygon points="340,126 296,126 160,386 204,386" fill="url(#beamSecondary)" filter="url(#subtleDrop)" />

  <!-- Center Core Singularity Diamond -->
  <polygon points="256,218 294,256 256,294 218,256" fill="url(#coreGlow)" filter="url(#subtleDrop)" />
  <circle cx="256" cy="256" r="10" fill="#FFFFFF" />

  <!-- Four Precision Synaptic Nodes -->
  <!-- Top-Left -->
  <circle cx="160" cy="138" r="22" fill="#1E1B4B" stroke="#818CF8" stroke-width="5" />
  <circle cx="160" cy="138" r="9" fill="#38BDF8" />
  <!-- Top-Right -->
  <circle cx="352" cy="138" r="22" fill="#082F49" stroke="#38BDF8" stroke-width="5" />
  <circle cx="352" cy="138" r="9" fill="#818CF8" />
  <!-- Bottom-Left -->
  <circle cx="160" cy="374" r="22" fill="#082F49" stroke="#22D3EE" stroke-width="5" />
  <circle cx="160" cy="374" r="9" fill="#6366F1" />
  <!-- Bottom-Right -->
  <circle cx="352" cy="374" r="22" fill="#1E1B4B" stroke="#6366F1" stroke-width="5" />
  <circle cx="352" cy="374" r="9" fill="#22D3EE" />
</svg>"""

# Wordmark SVG
WORDMARK_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 420 120" width="420" height="120">
  <defs>
    <linearGradient id="wmGradient" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#22D3EE" />
      <stop offset="50%" stop-color="#818CF8" />
      <stop offset="100%" stop-color="#C084FC" />
    </linearGradient>
  </defs>
  <style>
    .font-agent { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; font-size: 76px; font-weight: 700; fill: #F8FAFC; letter-spacing: -2px; }
    .font-x { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; font-size: 82px; font-weight: 900; fill: url(#wmGradient); }
    .font-tagline { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; font-size: 14px; font-weight: 600; fill: #94A3B8; letter-spacing: 5px; }
  </style>
  <text x="10" y="82" class="font-agent">Agent<tspan class="font-x">X</tspan></text>
  <text x="14" y="108" class="font-tagline">AUTONOMOUS SUITE</text>
</svg>"""

# Primary Logo (Horizontal on Dark)
PRIMARY_LOGO_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 760 220" width="760" height="220">
  <defs>
    <linearGradient id="bgDark" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#090D16" />
      <stop offset="100%" stop-color="#111827" />
    </linearGradient>
    <linearGradient id="beamPrimary" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#818CF8" />
      <stop offset="50%" stop-color="#6366F1" />
      <stop offset="100%" stop-color="#4F46E5" />
    </linearGradient>
    <linearGradient id="beamSecondary" x1="100%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#38BDF8" />
      <stop offset="50%" stop-color="#22D3EE" />
      <stop offset="100%" stop-color="#06B6D4" />
    </linearGradient>
    <linearGradient id="wmGradient" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#22D3EE" />
      <stop offset="50%" stop-color="#818CF8" />
      <stop offset="100%" stop-color="#C084FC" />
    </linearGradient>
  </defs>
  <rect width="100%" height="100%" rx="24" fill="url(#bgDark)" />
  <g transform="translate(20, 10) scale(0.39)">
    <polygon points="172,126 216,126 352,386 308,386" fill="url(#beamPrimary)" />
    <polygon points="340,126 296,126 160,386 204,386" fill="url(#beamSecondary)" />
    <polygon points="256,218 294,256 256,294 218,256" fill="#FFFFFF" />
    <circle cx="256" cy="256" r="10" fill="#38BDF8" />
    <circle cx="160" cy="138" r="22" fill="#1E1B4B" stroke="#818CF8" stroke-width="5" />
    <circle cx="160" cy="138" r="9" fill="#38BDF8" />
    <circle cx="352" cy="138" r="22" fill="#082F49" stroke="#38BDF8" stroke-width="5" />
    <circle cx="352" cy="138" r="9" fill="#818CF8" />
    <circle cx="160" cy="374" r="22" fill="#082F49" stroke="#22D3EE" stroke-width="5" />
    <circle cx="160" cy="374" r="9" fill="#6366F1" />
    <circle cx="352" cy="374" r="22" fill="#1E1B4B" stroke="#6366F1" stroke-width="5" />
    <circle cx="352" cy="374" r="9" fill="#22D3EE" />
  </g>
  <g transform="translate(240, 50)">
    <text x="0" y="70" font-family="-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif" font-size="78" font-weight="700" fill="#F8FAFC" letter-spacing="-2px">Agent<tspan font-weight="900" fill="url(#wmGradient)">X</tspan></text>
    <text x="4" y="100" font-family="-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif" font-size="15" font-weight="600" fill="#94A3B8" letter-spacing="6px">ENTERPRISE ON-DEVICE AI</text>
  </g>
</svg>"""

# Secondary Logo (Vertical on Dark)
SECONDARY_LOGO_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 420 540" width="420" height="540">
  <defs>
    <linearGradient id="bgDark" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#090D16" />
      <stop offset="100%" stop-color="#111827" />
    </linearGradient>
    <linearGradient id="beamPrimary" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#818CF8" />
      <stop offset="50%" stop-color="#6366F1" />
      <stop offset="100%" stop-color="#4F46E5" />
    </linearGradient>
    <linearGradient id="beamSecondary" x1="100%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#38BDF8" />
      <stop offset="50%" stop-color="#22D3EE" />
      <stop offset="100%" stop-color="#06B6D4" />
    </linearGradient>
    <linearGradient id="wmGradient" x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stop-color="#22D3EE" />
      <stop offset="50%" stop-color="#818CF8" />
      <stop offset="100%" stop-color="#C084FC" />
    </linearGradient>
  </defs>
  <rect width="100%" height="100%" rx="32" fill="url(#bgDark)" />
  <g transform="translate(45, 20) scale(0.64)">
    <polygon points="172,126 216,126 352,386 308,386" fill="url(#beamPrimary)" />
    <polygon points="340,126 296,126 160,386 204,386" fill="url(#beamSecondary)" />
    <polygon points="256,218 294,256 256,294 218,256" fill="#FFFFFF" />
    <circle cx="256" cy="256" r="10" fill="#38BDF8" />
    <circle cx="160" cy="138" r="22" fill="#1E1B4B" stroke="#818CF8" stroke-width="5" />
    <circle cx="160" cy="138" r="9" fill="#38BDF8" />
    <circle cx="352" cy="138" r="22" fill="#082F49" stroke="#38BDF8" stroke-width="5" />
    <circle cx="352" cy="138" r="9" fill="#818CF8" />
    <circle cx="160" cy="374" r="22" fill="#082F49" stroke="#22D3EE" stroke-width="5" />
    <circle cx="160" cy="374" r="9" fill="#6366F1" />
    <circle cx="352" cy="374" r="22" fill="#1E1B4B" stroke="#6366F1" stroke-width="5" />
    <circle cx="352" cy="374" r="9" fill="#22D3EE" />
  </g>
  <g transform="translate(0, 370)">
    <text x="210" y="60" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif" font-size="72" font-weight="700" fill="#F8FAFC" letter-spacing="-2px">Agent<tspan font-weight="900" fill="url(#wmGradient)">X</tspan></text>
    <text x="210" y="96" text-anchor="middle" font-family="-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif" font-size="14" font-weight="600" fill="#94A3B8" letter-spacing="6px">ON-DEVICE AI SUITE</text>
  </g>
</svg>"""

# Light Background Logo
LIGHT_LOGO_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 760 220" width="760" height="220">
  <defs>
    <linearGradient id="beamPrimaryL" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#4F46E5" />
      <stop offset="100%" stop-color="#3730A3" />
    </linearGradient>
    <linearGradient id="beamSecondaryL" x1="100%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#0891B2" />
      <stop offset="100%" stop-color="#0E7490" />
    </linearGradient>
  </defs>
  <rect width="100%" height="100%" rx="24" fill="#F8FAFC" stroke="#E2E8F0" stroke-width="2" />
  <g transform="translate(20, 10) scale(0.39)">
    <polygon points="172,126 216,126 352,386 308,386" fill="url(#beamPrimaryL)" />
    <polygon points="340,126 296,126 160,386 204,386" fill="url(#beamSecondaryL)" />
    <polygon points="256,218 294,256 256,294 218,256" fill="#0F172A" />
    <circle cx="256" cy="256" r="10" fill="#0284C7" />
    <circle cx="160" cy="138" r="22" fill="#EEF2FF" stroke="#4F46E5" stroke-width="5" />
    <circle cx="160" cy="138" r="9" fill="#0284C7" />
    <circle cx="352" cy="138" r="22" fill="#ECFEFF" stroke="#0891B2" stroke-width="5" />
    <circle cx="352" cy="138" r="9" fill="#4F46E5" />
    <circle cx="160" cy="374" r="22" fill="#ECFEFF" stroke="#0891B2" stroke-width="5" />
    <circle cx="160" cy="374" r="9" fill="#4F46E5" />
    <circle cx="352" cy="374" r="22" fill="#EEF2FF" stroke="#4F46E5" stroke-width="5" />
    <circle cx="352" cy="374" r="9" fill="#0891B2" />
  </g>
  <g transform="translate(240, 50)">
    <text x="0" y="70" font-family="-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif" font-size="78" font-weight="700" fill="#0F172A" letter-spacing="-2px">Agent<tspan font-weight="900" fill="#4F46E5">X</tspan></text>
    <text x="4" y="100" font-family="-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif" font-size="15" font-weight="600" fill="#64748B" letter-spacing="6px">ENTERPRISE ON-DEVICE AI</text>
  </g>
</svg>"""

# Monochrome Logo
MONOCHROME_LOGO_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 760 220" width="760" height="220">
  <rect width="100%" height="100%" rx="24" fill="#000000" />
  <g transform="translate(20, 10) scale(0.39)">
    <polygon points="172,126 216,126 352,386 308,386" fill="#FFFFFF" />
    <polygon points="340,126 296,126 160,386 204,386" fill="#A3A3A3" />
    <polygon points="256,218 294,256 256,294 218,256" fill="#FFFFFF" />
    <circle cx="256" cy="256" r="10" fill="#000000" />
    <circle cx="160" cy="138" r="22" fill="#000000" stroke="#FFFFFF" stroke-width="5" />
    <circle cx="160" cy="138" r="9" fill="#FFFFFF" />
    <circle cx="352" cy="138" r="22" fill="#000000" stroke="#FFFFFF" stroke-width="5" />
    <circle cx="352" cy="138" r="9" fill="#FFFFFF" />
    <circle cx="160" cy="374" r="22" fill="#000000" stroke="#FFFFFF" stroke-width="5" />
    <circle cx="160" cy="374" r="9" fill="#FFFFFF" />
    <circle cx="352" cy="374" r="22" fill="#000000" stroke="#FFFFFF" stroke-width="5" />
    <circle cx="352" cy="374" r="9" fill="#FFFFFF" />
  </g>
  <g transform="translate(240, 50)">
    <text x="0" y="70" font-family="-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif" font-size="78" font-weight="700" fill="#FFFFFF" letter-spacing="-2px">Agent<tspan font-weight="900" fill="#FFFFFF">X</tspan></text>
    <text x="4" y="100" font-family="-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif" font-size="15" font-weight="600" fill="#A3A3A3" letter-spacing="6px">AUTONOMOUS SUITE</text>
  </g>
</svg>"""

# Favicon SVG
FAVICON_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">
  <rect width="64" height="64" rx="14" fill="#090D16" />
  <g transform="translate(4, 4) scale(0.109375)">
    <polygon points="172,126 216,126 352,386 308,386" fill="#6366F1" />
    <polygon points="340,126 296,126 160,386 204,386" fill="#06B6D4" />
    <polygon points="256,218 294,256 256,294 218,256" fill="#FFFFFF" />
    <circle cx="160" cy="138" r="20" fill="#38BDF8" />
    <circle cx="352" cy="138" r="20" fill="#818CF8" />
    <circle cx="160" cy="374" r="20" fill="#22D3EE" />
    <circle cx="352" cy="374" r="20" fill="#6366F1" />
  </g>
</svg>"""

# ----------------------------------------------------------------------
# 2. Store 512x512 Master SVGs
# ----------------------------------------------------------------------

# AgentX 512x512 Store Icon
AGENTX_STORE_512 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <radialGradient id="appBg" cx="50%" cy="40%" r="65%">
      <stop offset="0%" stop-color="#182138" />
      <stop offset="70%" stop-color="#090D16" />
      <stop offset="100%" stop-color="#04060A" />
    </radialGradient>
    <linearGradient id="beamPrimary" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#818CF8" />
      <stop offset="50%" stop-color="#6366F1" />
      <stop offset="100%" stop-color="#4F46E5" />
    </linearGradient>
    <linearGradient id="beamSecondary" x1="100%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#38BDF8" />
      <stop offset="50%" stop-color="#22D3EE" />
      <stop offset="100%" stop-color="#06B6D4" />
    </linearGradient>
    <filter id="glow" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="16" result="blur" />
      <feComposite in="SourceGraphic" in2="blur" operator="over" />
    </filter>
  </defs>

  <rect width="512" height="512" rx="112" fill="url(#appBg)" />

  <!-- Subtle neural field rings -->
  <circle cx="256" cy="256" r="190" fill="none" stroke="#6366F1" stroke-width="1.5" stroke-dasharray="8,8" opacity="0.3" />
  <circle cx="256" cy="256" r="140" fill="none" stroke="#06B6D4" stroke-width="1.5" stroke-dasharray="6,6" opacity="0.4" />

  <!-- Primary Beams -->
  <polygon points="172,126 216,126 352,386 308,386" fill="url(#beamPrimary)" />
  <polygon points="340,126 296,126 160,386 204,386" fill="url(#beamSecondary)" />

  <!-- Center Singularity Core -->
  <polygon points="256,218 294,256 256,294 218,256" fill="#FFFFFF" filter="url(#glow)" />
  <circle cx="256" cy="256" r="12" fill="#38BDF8" />

  <!-- Four Precision Synaptic Nodes -->
  <circle cx="160" cy="138" r="24" fill="#1E1B4B" stroke="#818CF8" stroke-width="6" />
  <circle cx="160" cy="138" r="10" fill="#38BDF8" />

  <circle cx="352" cy="138" r="24" fill="#082F49" stroke="#38BDF8" stroke-width="6" />
  <circle cx="352" cy="138" r="10" fill="#818CF8" />

  <circle cx="160" cy="374" r="24" fill="#082F49" stroke="#22D3EE" stroke-width="6" />
  <circle cx="160" cy="374" r="10" fill="#6366F1" />

  <circle cx="352" cy="374" r="24" fill="#1E1B4B" stroke="#6366F1" stroke-width="6" />
  <circle cx="352" cy="374" r="10" fill="#22D3EE" />
</svg>"""

# AgentX Medical 512x512 Store Icon
AGENTX_MEDICAL_STORE_512 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <radialGradient id="medBg" cx="50%" cy="40%" r="65%">
      <stop offset="0%" stop-color="#0E3530" />
      <stop offset="70%" stop-color="#041C1A" />
      <stop offset="100%" stop-color="#020E0D" />
    </radialGradient>
    <linearGradient id="medPrimary" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#2DD4BF" />
      <stop offset="50%" stop-color="#14B8A6" />
      <stop offset="100%" stop-color="#0D9488" />
    </linearGradient>
    <linearGradient id="medSecondary" x1="100%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#6EE7B7" />
      <stop offset="50%" stop-color="#34D399" />
      <stop offset="100%" stop-color="#10B981" />
    </linearGradient>
    <filter id="medGlow" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="16" result="blur" />
      <feComposite in="SourceGraphic" in2="blur" operator="over" />
    </filter>
  </defs>

  <rect width="512" height="512" rx="112" fill="url(#medBg)" />

  <!-- Subtle vital pulse telemetry ring -->
  <circle cx="256" cy="256" r="190" fill="none" stroke="#14B8A6" stroke-width="1.5" stroke-dasharray="8,8" opacity="0.3" />

  <!-- Clinical Cross Datum Bar (linking medical identity into X geometry) -->
  <line x1="130" y1="256" x2="382" y2="256" stroke="#14B8A6" stroke-width="12" stroke-linecap="round" opacity="0.8" />
  <line x1="256" y1="130" x2="256" y2="382" stroke="#10B981" stroke-width="12" stroke-linecap="round" opacity="0.8" />

  <!-- Primary Beams (Shared Neural-X) -->
  <polygon points="172,126 216,126 352,386 308,386" fill="url(#medPrimary)" />
  <polygon points="340,126 296,126 160,386 204,386" fill="url(#medSecondary)" />

  <!-- Center Singularity Clinical Core -->
  <polygon points="256,218 294,256 256,294 218,256" fill="#FFFFFF" filter="url(#medGlow)" />
  <circle cx="256" cy="256" r="12" fill="#2DD4BF" />

  <!-- Integrated Vital Telemetry EKG Rhythm Motif -->
  <polyline points="206,256 230,256 242,236 256,276 270,244 282,256 306,256" fill="none" stroke="#041C1A" stroke-width="4.5" stroke-linecap="round" stroke-linejoin="round" />

  <!-- Four Precision Clinical Nodes -->
  <circle cx="160" cy="138" r="24" fill="#042F2C" stroke="#2DD4BF" stroke-width="6" />
  <circle cx="160" cy="138" r="10" fill="#34D399" />

  <circle cx="352" cy="138" r="24" fill="#022C22" stroke="#34D399" stroke-width="6" />
  <circle cx="352" cy="138" r="10" fill="#2DD4BF" />

  <circle cx="160" cy="374" r="24" fill="#022C22" stroke="#10B981" stroke-width="6" />
  <circle cx="160" cy="374" r="10" fill="#14B8A6" />

  <circle cx="352" cy="374" r="24" fill="#042F2C" stroke="#14B8A6" stroke-width="6" />
  <circle cx="352" cy="374" r="10" fill="#10B981" />
</svg>"""

# AgentX Serve 512x512 Store Icon
AGENTX_SERVE_STORE_512 = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <defs>
    <radialGradient id="srvBg" cx="50%" cy="40%" r="65%">
      <stop offset="0%" stop-color="#11221D" />
      <stop offset="70%" stop-color="#081410" />
      <stop offset="100%" stop-color="#020806" />
    </radialGradient>
    <linearGradient id="srvPrimary" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#34D399" />
      <stop offset="50%" stop-color="#10B981" />
      <stop offset="100%" stop-color="#059669" />
    </linearGradient>
    <linearGradient id="srvSecondary" x1="100%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#60A5FA" />
      <stop offset="50%" stop-color="#3B82F6" />
      <stop offset="100%" stop-color="#2563EB" />
    </linearGradient>
    <filter id="srvGlow" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="16" result="blur" />
      <feComposite in="SourceGraphic" in2="blur" operator="over" />
    </filter>
  </defs>

  <rect width="512" height="512" rx="112" fill="url(#srvBg)" />

  <!-- Broadcast Telemetry Arcs (Signifying Server Antenna / Edge Gateway) -->
  <path d="M 180,88 A 96,96 0 0,1 332,88" fill="none" stroke="#10B981" stroke-width="8" stroke-linecap="round" opacity="0.85" />
  <path d="M 212,112 A 54,54 0 0,1 300,112" fill="none" stroke="#3B82F6" stroke-width="6" stroke-linecap="round" opacity="0.9" />

  <!-- Infrastructure Server Chassis Anchor Base -->
  <rect x="130" y="394" width="252" height="20" rx="10" fill="#1E293B" stroke="#334155" stroke-width="3" />
  <circle cx="160" cy="404" r="4" fill="#10B981" />
  <circle cx="178" cy="404" r="4" fill="#3B82F6" />
  <circle cx="196" cy="404" r="4" fill="#F59E0B" />

  <!-- Primary Beams (Shared Neural-X) -->
  <polygon points="172,126 216,126 352,386 308,386" fill="url(#srvPrimary)" />
  <polygon points="340,126 296,126 160,386 204,386" fill="url(#srvSecondary)" />

  <!-- Center Singularity Server Gateway Core -->
  <polygon points="256,218 294,256 256,294 218,256" fill="#FFFFFF" filter="url(#srvGlow)" />
  <circle cx="256" cy="256" r="12" fill="#10B981" />

  <!-- Four Precision Infrastructure Nodes -->
  <circle cx="160" cy="138" r="24" fill="#064E3B" stroke="#34D399" stroke-width="6" />
  <circle cx="160" cy="138" r="10" fill="#60A5FA" />

  <circle cx="352" cy="138" r="24" fill="#172554" stroke="#60A5FA" stroke-width="6" />
  <circle cx="352" cy="138" r="10" fill="#34D399" />

  <circle cx="160" cy="374" r="24" fill="#172554" stroke="#3B82F6" stroke-width="6" />
  <circle cx="160" cy="374" r="10" fill="#10B981" />

  <circle cx="352" cy="374" r="24" fill="#064E3B" stroke="#10B981" stroke-width="6" />
  <circle cx="352" cy="374" r="10" fill="#3B82F6" />
</svg>"""

# ----------------------------------------------------------------------
# 3. Android Adaptive Vector Drawables (108dp viewport, safe area 72dp)
# ----------------------------------------------------------------------

# AgentX Foreground
AGENTX_VD_FOREGROUND = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Diagonal Primary Beam (Indigo) -->
    <path
        android:pathData="M36,27 L45,27 L74,81 L65,81 Z"
        android:fillColor="#6366F1" />
    <!-- Diagonal Secondary Beam (Electric Cyan) -->
    <path
        android:pathData="M72,27 L63,27 L34,81 L43,81 Z"
        android:fillColor="#06B6D4" />
    <!-- Center Core Diamond -->
    <path
        android:pathData="M54,46 L62,54 L54,62 L46,54 Z"
        android:fillColor="#FFFFFF" />
    <path
        android:pathData="M54,51 A3,3 0 1,0 54,57 A3,3 0 1,0 54,51 Z"
        android:fillColor="#38BDF8" />
    <!-- Synaptic Nodes (Safe within 18..90dp area) -->
    <path
        android:pathData="M34,29 A5,5 0 1,0 34,19 A5,5 0 1,0 34,29 Z"
        android:fillColor="#818CF8" />
    <path
        android:pathData="M74,29 A5,5 0 1,0 74,19 A5,5 0 1,0 74,29 Z"
        android:fillColor="#38BDF8" />
    <path
        android:pathData="M34,89 A5,5 0 1,0 34,79 A5,5 0 1,0 34,89 Z"
        android:fillColor="#22D3EE" />
    <path
        android:pathData="M74,89 A5,5 0 1,0 74,79 A5,5 0 1,0 74,89 Z"
        android:fillColor="#6366F1" />
</vector>
"""

# AgentX Background
AGENTX_VD_BACKGROUND = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M0,0h108v108h-108z"
        android:fillColor="#090D16" />
    <!-- Subtle radial aura backing -->
    <path
        android:pathData="M54,18 A36,36 0 1,0 54,90 A36,36 0 1,0 54,18 Z"
        android:fillColor="#151D33" />
</vector>
"""

# AgentX Medical Foreground
MEDICAL_VD_FOREGROUND = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Clinical Cross Datum Bar -->
    <path
        android:pathData="M27,54 L81,54"
        android:strokeColor="#14B8A6"
        android:strokeWidth="3"
        android:strokeLineCap="round" />
    <path
        android:pathData="M54,27 L54,81"
        android:strokeColor="#10B981"
        android:strokeWidth="3"
        android:strokeLineCap="round" />
    <!-- Shared Diagonal Primary Beam (Teal) -->
    <path
        android:pathData="M36,27 L45,27 L74,81 L65,81 Z"
        android:fillColor="#0D9488" />
    <!-- Shared Diagonal Secondary Beam (Emerald) -->
    <path
        android:pathData="M72,27 L63,27 L34,81 L43,81 Z"
        android:fillColor="#14B8A6" />
    <!-- Center Core Clinical Diamond -->
    <path
        android:pathData="M54,46 L62,54 L54,62 L46,54 Z"
        android:fillColor="#FFFFFF" />
    <path
        android:pathData="M54,51 A3,3 0 1,0 54,57 A3,3 0 1,0 54,51 Z"
        android:fillColor="#2DD4BF" />
    <!-- Clinical Synaptic Nodes -->
    <path
        android:pathData="M34,29 A5,5 0 1,0 34,19 A5,5 0 1,0 34,29 Z"
        android:fillColor="#2DD4BF" />
    <path
        android:pathData="M74,29 A5,5 0 1,0 74,19 A5,5 0 1,0 74,29 Z"
        android:fillColor="#34D399" />
    <path
        android:pathData="M34,89 A5,5 0 1,0 34,79 A5,5 0 1,0 34,89 Z"
        android:fillColor="#10B981" />
    <path
        android:pathData="M74,89 A5,5 0 1,0 74,79 A5,5 0 1,0 74,89 Z"
        android:fillColor="#14B8A6" />
</vector>
"""

# AgentX Medical Background
MEDICAL_VD_BACKGROUND = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M0,0h108v108h-108z"
        android:fillColor="#041C1A" />
    <!-- Subtle clinical teal radial aura -->
    <path
        android:pathData="M54,18 A36,36 0 1,0 54,90 A36,36 0 1,0 54,18 Z"
        android:fillColor="#0B302C" />
</vector>
"""

# AgentX Serve Foreground
SERVE_VD_FOREGROUND = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Broadcast Waves (Telemetry Edge Server) -->
    <path
        android:pathData="M44,18 A14,14 0 0,1 64,18"
        android:strokeColor="#10B981"
        android:strokeWidth="2.5"
        android:strokeLineCap="round" />
    <path
        android:pathData="M38,24 A22,22 0 0,1 70,24"
        android:strokeColor="#3B82F6"
        android:strokeWidth="2"
        android:strokeLineCap="round" />
    <!-- Infrastructure Base Anchor Bar -->
    <path
        android:pathData="M32,84 L76,84"
        android:strokeColor="#334155"
        android:strokeWidth="3.5"
        android:strokeLineCap="round" />
    <!-- Shared Diagonal Primary Beam (Signal Emerald) -->
    <path
        android:pathData="M36,27 L45,27 L74,81 L65,81 Z"
        android:fillColor="#059669" />
    <!-- Shared Diagonal Secondary Beam (Cobalt) -->
    <path
        android:pathData="M72,27 L63,27 L34,81 L43,81 Z"
        android:fillColor="#2563EB" />
    <!-- Center Core Server Gateway Diamond -->
    <path
        android:pathData="M54,46 L62,54 L54,62 L46,54 Z"
        android:fillColor="#FFFFFF" />
    <path
        android:pathData="M54,51 A3,3 0 1,0 54,57 A3,3 0 1,0 54,51 Z"
        android:fillColor="#10B981" />
    <!-- Infrastructure Nodes -->
    <path
        android:pathData="M34,29 A5,5 0 1,0 34,19 A5,5 0 1,0 34,29 Z"
        android:fillColor="#34D399" />
    <path
        android:pathData="M74,29 A5,5 0 1,0 74,19 A5,5 0 1,0 74,29 Z"
        android:fillColor="#60A5FA" />
    <path
        android:pathData="M34,89 A5,5 0 1,0 34,79 A5,5 0 1,0 34,89 Z"
        android:fillColor="#3B82F6" />
    <path
        android:pathData="M74,89 A5,5 0 1,0 74,79 A5,5 0 1,0 74,89 Z"
        android:fillColor="#10B981" />
</vector>
"""

# AgentX Serve Background
SERVE_VD_BACKGROUND = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="M0,0h108v108h-108z"
        android:fillColor="#0A0E17" />
    <!-- Subtle server carbon aura -->
    <path
        android:pathData="M54,18 A36,36 0 1,0 54,90 A36,36 0 1,0 54,18 Z"
        android:fillColor="#12201C" />
</vector>
"""

def write_text(filepath: Path, content: str):
    filepath.parent.mkdir(parents=True, exist_ok=True)
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Generated: {filepath.relative_to(ROOT_DIR)}")

def generate_png_icon(output_path: Path, theme="agentx", size=512):
    """Generates crisp 512x512 raster icon using PIL matching vector geometry"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Background rounded rect
    if theme == "agentx":
        bg_color = (9, 13, 22, 255)
        core_ring = (56, 189, 248, 255)
        beam_p = (99, 102, 241, 255)
        beam_s = (6, 182, 212, 255)
        node1 = (129, 140, 248, 255)
        node2 = (56, 189, 248, 255)
    elif theme == "medical":
        bg_color = (4, 28, 26, 255)
        core_ring = (45, 212, 191, 255)
        beam_p = (13, 148, 136, 255)
        beam_s = (20, 184, 166, 255)
        node1 = (45, 212, 191, 255)
        node2 = (52, 211, 153, 255)
    else: # serve
        bg_color = (10, 14, 23, 255)
        core_ring = (16, 185, 129, 255)
        beam_p = (5, 150, 105, 255)
        beam_s = (37, 99, 235, 255)
        node1 = (52, 211, 153, 255)
        node2 = (96, 165, 250, 255)

    # Draw rounded background
    draw.rounded_rectangle([0, 0, size, size], radius=112, fill=bg_color)

    # If medical, draw cross datum bar
    if theme == "medical":
        draw.line([(130, 256), (382, 256)], fill=(20, 184, 166, 200), width=12)
        draw.line([(256, 130), (256, 382)], fill=(16, 185, 129, 200), width=12)
    elif theme == "serve":
        # Draw antenna arcs
        draw.arc([180, 50, 332, 150], start=200, end=340, fill=(16, 185, 129, 220), width=8)
        # Server base
        draw.rounded_rectangle([130, 394, 382, 414], radius=10, fill=(30, 41, 59, 255))
        draw.ellipse([156, 400, 164, 408], fill=(16, 185, 129, 255))
        draw.ellipse([174, 400, 182, 408], fill=(59, 130, 246, 255))

    # Diagonal Primary Beam
    draw.polygon([(172, 126), (216, 126), (352, 386), (308, 386)], fill=beam_p)
    # Diagonal Secondary Beam
    draw.polygon([(340, 126), (296, 126), (160, 386), (204, 386)], fill=beam_s)

    # Singularity diamond
    draw.polygon([(256, 218), (294, 256), (256, 294), (218, 256)], fill=(255, 255, 255, 255))
    draw.ellipse([244, 244, 268, 268], fill=core_ring)

    # Nodes
    draw.ellipse([136, 114, 184, 162], fill=(20, 25, 40, 255), outline=node1, width=6)
    draw.ellipse([150, 128, 170, 148], fill=node2)

    draw.ellipse([328, 114, 376, 162], fill=(20, 25, 40, 255), outline=node2, width=6)
    draw.ellipse([342, 128, 362, 148], fill=node1)

    draw.ellipse([136, 350, 184, 398], fill=(20, 25, 40, 255), outline=node2, width=6)
    draw.ellipse([150, 364, 170, 384], fill=node1)

    draw.ellipse([328, 350, 376, 398], fill=(20, 25, 40, 255), outline=node1, width=6)
    draw.ellipse([342, 364, 362, 384], fill=node2)

    img.save(output_path, "PNG")
    print(f"Generated PNG: {output_path.relative_to(ROOT_DIR)}")

def main():
    print("=== AgentX Suite Brand Asset Generation ===")

    # 1. Branding Assets
    write_text(BRANDING_DIR / "agentx_symbol.svg", SYMBOL_SVG)
    write_text(BRANDING_DIR / "agentx_wordmark.svg", WORDMARK_SVG)
    write_text(BRANDING_DIR / "agentx_primary_logo.svg", PRIMARY_LOGO_SVG)
    write_text(BRANDING_DIR / "agentx_secondary_logo.svg", SECONDARY_LOGO_SVG)
    write_text(BRANDING_DIR / "agentx_horizontal_logo.svg", PRIMARY_LOGO_SVG)
    write_text(BRANDING_DIR / "agentx_vertical_logo.svg", SECONDARY_LOGO_SVG)
    write_text(BRANDING_DIR / "agentx_light_logo.svg", LIGHT_LOGO_SVG)
    write_text(BRANDING_DIR / "agentx_dark_logo.svg", PRIMARY_LOGO_SVG)
    write_text(BRANDING_DIR / "agentx_monochrome_logo.svg", MONOCHROME_LOGO_SVG)
    write_text(BRANDING_DIR / "favicon.svg", FAVICON_SVG)

    # 2. Store 512x512 Master SVGs
    write_text(STORE_DIR / "agentx_512.svg", AGENTX_STORE_512)
    write_text(STORE_DIR / "agentx_medical_512.svg", AGENTX_MEDICAL_STORE_512)
    write_text(STORE_DIR / "agentx_serve_512.svg", AGENTX_SERVE_STORE_512)

    # 3. High-res Store PNGs
    generate_png_icon(STORE_DIR / "agentx_512.png", theme="agentx")
    generate_png_icon(STORE_DIR / "agentx_medical_512.png", theme="medical")
    generate_png_icon(STORE_DIR / "agentx_serve_512.png", theme="serve")

    # 4. Android Adaptive Vector Drawables
    # App
    write_text(ROOT_DIR / "app/src/main/res/drawable/ic_launcher_foreground.xml", AGENTX_VD_FOREGROUND)
    write_text(ROOT_DIR / "app/src/main/res/drawable/ic_launcher_background.xml", AGENTX_VD_BACKGROUND)
    write_text(ROOT_DIR / "assets/agentx/ic_launcher_foreground.xml", AGENTX_VD_FOREGROUND)
    write_text(ROOT_DIR / "assets/agentx/ic_launcher_background.xml", AGENTX_VD_BACKGROUND)

    # Medical
    write_text(ROOT_DIR / "medical/src/main/res/drawable/ic_launcher_foreground.xml", MEDICAL_VD_FOREGROUND)
    write_text(ROOT_DIR / "medical/src/main/res/drawable/ic_launcher_background.xml", MEDICAL_VD_BACKGROUND)
    write_text(ROOT_DIR / "assets/agentx_medical/ic_launcher_foreground.xml", MEDICAL_VD_FOREGROUND)
    write_text(ROOT_DIR / "assets/agentx_medical/ic_launcher_background.xml", MEDICAL_VD_BACKGROUND)

    # Serve (Admin)
    write_text(ROOT_DIR / "admin/src/main/res/drawable/ic_launcher_foreground.xml", SERVE_VD_FOREGROUND)
    write_text(ROOT_DIR / "admin/src/main/res/drawable/ic_launcher_background.xml", SERVE_VD_BACKGROUND)
    write_text(ROOT_DIR / "assets/agentx_serve/ic_launcher_foreground.xml", SERVE_VD_FOREGROUND)
    write_text(ROOT_DIR / "assets/agentx_serve/ic_launcher_background.xml", SERVE_VD_BACKGROUND)

    print("Brand assets successfully generated.")

if __name__ == "__main__":
    main()
