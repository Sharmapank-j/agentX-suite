package com.agentx.admin.server

/**
 * Embedded, self-contained single-page web client served directly by AgentXAdminServer on port 8080.
 * Allows any device on the local network (laptop, iPad, desktop, smart TV) to interact with the
 * phone's hosted model without installing any application.
 *
 * Contains 0 external CDN dependencies so it operates 100% offline on local Wi-Fi.
 */
object EmbeddedWebChatClient {

    val HTML: String = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>AgentX Serve • Edge AI Gateway</title>
<style>
:root {
  --bg-dark: #07090E;
  --surface: #0E131F;
  --surface-elevated: #161D2E;
  --border: #232D42;
  --cyan: #00F0FF;
  --cyan-dim: rgba(0, 240, 255, 0.15);
  --indigo: #6366F1;
  --mint: #10B981;
  --text-primary: #F8FAFC;
  --text-muted: #94A3B8;
  --user-bubble: #0E2A47;
  --assistant-bubble: #161D2E;
}
* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
body { background: var(--bg-dark); color: var(--text-primary); height: 100vh; display: flex; flex-direction: column; }
header { background: var(--surface); border-bottom: 1px solid var(--border); padding: 12px 20px; display: flex; align-items: center; justify-content: space-between; }
.logo-group { display: flex; align-items: center; gap: 12px; }
.logo-badge { width: 34px; height: 34px; border-radius: 50%; background: linear-gradient(135deg, var(--cyan), var(--indigo)); display: flex; align-items: center; justify-content: center; font-weight: 900; color: #07090E; font-size: 16px; }
.title-group h1 { font-size: 15px; font-weight: 700; color: var(--text-primary); }
.title-group p { font-size: 11px; color: var(--cyan); }
.status-pill { display: flex; align-items: center; gap: 6px; background: var(--cyan-dim); border: 1px solid var(--cyan); padding: 4px 10px; border-radius: 20px; font-size: 11px; color: var(--cyan); font-weight: 600; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--mint); animation: pulse 2s infinite; }
@keyframes pulse { 0% { opacity: 0.4; } 50% { opacity: 1; } 100% { opacity: 0.4; } }
.controls-bar { background: var(--surface-elevated); border-bottom: 1px solid var(--border); padding: 8px 20px; display: flex; gap: 12px; align-items: center; font-size: 12px; flex-wrap: wrap; }
select, input[type="text"] { background: var(--surface); border: 1px solid var(--border); color: var(--text-primary); padding: 6px 10px; border-radius: 6px; font-size: 12px; outline: none; }
select:focus, input[type="text"]:focus { border-color: var(--cyan); }
.hud-metrics { margin-left: auto; display: flex; gap: 12px; color: var(--text-muted); font-size: 11px; font-family: monospace; }
main { flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 16px; }
.msg { max-width: 80%; padding: 12px 16px; border-radius: 12px; font-size: 14px; line-height: 1.5; word-break: break-word; }
.msg.user { align-self: flex-end; background: var(--user-bubble); border: 1px solid rgba(0,240,255,0.3); border-bottom-right-radius: 2px; }
.msg.assistant { align-self: flex-start; background: var(--assistant-bubble); border: 1px solid var(--border); border-bottom-left-radius: 2px; }
.msg-header { font-size: 10px; font-weight: 700; margin-bottom: 4px; text-transform: uppercase; letter-spacing: 0.5px; }
.user .msg-header { color: var(--cyan); }
.assistant .msg-header { color: var(--indigo); }
pre { background: #07090E; padding: 10px; border-radius: 6px; overflow-x: auto; margin: 8px 0; border: 1px solid var(--border); font-family: monospace; font-size: 12px; color: #E2E8F0; }
footer { background: var(--surface); border-top: 1px solid var(--border); padding: 12px 20px; }
.input-row { display: flex; gap: 10px; }
textarea { flex: 1; background: var(--surface-elevated); border: 1px solid var(--border); color: var(--text-primary); padding: 10px 14px; border-radius: 8px; font-size: 14px; resize: none; height: 46px; outline: none; }
textarea:focus { border-color: var(--cyan); }
button { background: linear-gradient(135deg, var(--cyan), var(--indigo)); color: #07090E; border: none; padding: 0 20px; border-radius: 8px; font-weight: 700; font-size: 13px; cursor: pointer; transition: opacity 0.2s; }
button:hover { opacity: 0.9; }
button:disabled { opacity: 0.4; cursor: not-allowed; }
.empty-hero { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; text-align: center; color: var(--text-muted); gap: 10px; }
.empty-hero h2 { color: var(--text-primary); font-size: 18px; }
.badge { background: var(--cyan-dim); color: var(--cyan); padding: 2px 8px; border-radius: 4px; font-size: 11px; }
</style>
</head>
<body>
<header>
  <div class="logo-group">
    <div class="logo-badge">X</div>
    <div class="title-group">
      <h1>AgentX Serve • Host Gateway</h1>
      <p>On-Device OpenAI API Server</p>
    </div>
  </div>
  <div class="status-pill">
    <div class="status-dot"></div>
    <span id="hostStatus">ONLINE • 8080</span>
  </div>
</header>

<div class="controls-bar">
  <label>Model:
    <select id="modelSelect">
      <option value="default">Active Host Model</option>
    </select>
  </label>
  <label>API Key:
    <input type="text" id="apiKeyInput" placeholder="Optional client key..." style="width: 170px;" />
  </label>
  <button id="clearBtn" style="padding: 4px 10px; font-size: 11px; background: var(--surface); color: var(--text-muted); border: 1px solid var(--border);">Clear</button>
  <div class="hud-metrics">
    <span>Ping: <strong id="pingVal">--</strong></span>
    <span>TTFT: <strong id="ttftVal">--</strong></span>
    <span>Speed: <strong id="speedVal">--</strong></span>
  </div>
</div>

<main id="chatFeed">
  <div class="empty-hero" id="emptyHero">
    <div class="badge">HOSTED ON ANDROID PHONE</div>
    <h2>Chat with On-Device AI</h2>
    <p style="font-size: 13px; max-width: 420px;">This interface is served directly from the host phone's local HTTP server. Messages stream in real-time via Server-Sent Events.</p>
  </div>
</main>

<footer>
  <form id="chatForm" class="input-row">
    <textarea id="promptInput" placeholder="Ask your hosted model anything... (Enter to send)"></textarea>
    <button type="submit" id="sendBtn">Send</button>
  </form>
</footer>

<script>
const chatFeed = document.getElementById('chatFeed');
const emptyHero = document.getElementById('emptyHero');
const promptInput = document.getElementById('promptInput');
const sendBtn = document.getElementById('sendBtn');
const modelSelect = document.getElementById('modelSelect');
const apiKeyInput = document.getElementById('apiKeyInput');
const pingVal = document.getElementById('pingVal');
const ttftVal = document.getElementById('ttftVal');
const speedVal = document.getElementById('speedVal');

const savedKey = localStorage.getItem('agentx_api_key');
if (savedKey) apiKeyInput.value = savedKey;
apiKeyInput.addEventListener('change', () => localStorage.setItem('agentx_api_key', apiKeyInput.value.trim()));

// Ping Health
async function checkHealth() {
  const start = performance.now();
  try {
    const res = await fetch('/health');
    const data = await res.json();
    const lat = Math.round(performance.now() - start);
    pingVal.innerText = lat + 'ms';
    if (data.activeLocalModel) {
      document.getElementById('hostStatus').innerText = 'HOSTING: ' + data.activeLocalModel.substring(0, 18);
    }
  } catch (e) {
    pingVal.innerText = 'ERR';
  }
}
checkHealth();
setInterval(checkHealth, 8000);

// Fetch Models
async function fetchModels() {
  try {
    const res = await fetch('/v1/models');
    const data = await res.json();
    if (data && data.data && data.data.length > 0) {
      modelSelect.innerHTML = '';
      data.data.forEach(m => {
        const opt = document.createElement('option');
        opt.value = m.id;
        opt.innerText = m.id;
        modelSelect.appendChild(opt);
      });
    }
  } catch (e) {}
}
fetchModels();

// Chat Submission
const history = [];

document.getElementById('chatForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const text = promptInput.value.trim();
  if (!text) return;

  if (emptyHero) emptyHero.style.display = 'none';

  // Append user message
  appendMsg('You', text, 'user');
  promptInput.value = '';
  sendBtn.disabled = true;

  history.push({ role: 'user', content: text });

  // Assistant placeholder
  const assistantBubble = appendMsg('AgentX', '', 'assistant');
  const contentSpan = assistantBubble.querySelector('.msg-content');

  const startTime = performance.now();
  let firstTokenTime = null;
  let tokenCount = 0;

  try {
    const headers = { 'Content-Type': 'application/json' };
    const key = apiKeyInput.value.trim();
    if (key) {
      headers['Authorization'] = 'Bearer ' + key;
      headers['X-API-Key'] = key;
    }

    const res = await fetch('/v1/chat/completions', {
      method: 'POST',
      headers,
      body: JSON.stringify({
        model: modelSelect.value || 'default',
        messages: history,
        stream: true
      })
    });

    if (!res.ok) {
      const err = await res.text();
      contentSpan.innerText = 'Error ' + res.status + ': ' + err;
      sendBtn.disabled = false;
      return;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let partial = '';
    let fullResponse = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      partial += decoder.decode(value, { stream: true });
      const lines = partial.split('\n');
      partial = lines.pop();

      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('data: ')) {
          const payload = trimmed.substring(6).trim();
          if (payload === '[DONE]') break;
          try {
            const parsed = JSON.parse(payload);
            const delta = parsed.choices?.[0]?.delta?.content;
            if (delta) {
              if (firstTokenTime === null) {
                firstTokenTime = performance.now();
                ttftVal.innerText = Math.round(firstTokenTime - startTime) + 'ms';
              }
              tokenCount++;
              fullResponse += delta;
              contentSpan.innerText = fullResponse;
              chatFeed.scrollTop = chatFeed.scrollHeight;

              const elapsedSec = (performance.now() - firstTokenTime) / 1000;
              if (elapsedSec > 0) {
                speedVal.innerText = (tokenCount / elapsedSec).toFixed(1) + ' t/s';
              }
            }
          } catch (_) {}
        }
      }
    }
    history.push({ role: 'assistant', content: fullResponse });
  } catch (err) {
    contentSpan.innerText = 'Network error: ' + err.message;
  } finally {
    sendBtn.disabled = false;
  }
});

promptInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    document.getElementById('chatForm').dispatchEvent(new Event('submit'));
  }
});

document.getElementById('clearBtn').addEventListener('click', () => {
  chatFeed.innerHTML = '';
  history.length = 0;
  chatFeed.appendChild(emptyHero);
  emptyHero.style.display = 'flex';
  ttftVal.innerText = '--';
  speedVal.innerText = '--';
});

function appendMsg(sender, text, type) {
  const div = document.createElement('div');
  div.className = 'msg ' + type;
  div.innerHTML = '<div class="msg-header">' + sender + '</div><div class="msg-content">' + escapeHtml(text) + '</div>';
  chatFeed.appendChild(div);
  chatFeed.scrollTop = chatFeed.scrollHeight;
  return div;
}

function escapeHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
</script>
</body>
</html>
""".trimIndent()
}
