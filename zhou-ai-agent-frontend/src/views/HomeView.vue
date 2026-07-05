<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="home">
    <!-- Hero -->
    <header class="hero">
      <div class="hero-hud" aria-hidden="true">
        <span class="hud-tag">&lt;system.online /&gt;</span>
      </div>
      <h1 class="hero-title">
        <span class="title-line">AI</span>
        <span class="title-line neon-text">应用中心</span>
      </h1>
      <p class="lead">探索下一代智能对话体验 &mdash; 实时流式响应，多模型接入</p>
      <div class="hero-bar" aria-hidden="true">
        <span class="bar-seg"></span>
        <span class="bar-dot"></span>
        <span class="bar-seg bar-seg-long"></span>
        <span class="bar-dot"></span>
        <span class="bar-seg"></span>
      </div>
    </header>

    <!-- Cards -->
    <section class="cards">
      <RouterLink class="card" to="/love">
        <div class="card-border-glow" aria-hidden="true"></div>
        <div class="card-inner">
          <div class="card-head">
            <span class="card-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" width="28" height="28">
                <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 1 0-7.78 7.78L12 21.23l8.84-8.84a5.5 5.5 0 0 0 0-7.78z"/>
              </svg>
            </span>
            <span class="card-tag">RAG · SSE</span>
          </div>
          <h2>AI 恋爱大师</h2>
          <p>Agentic RAG 对话，LLM 路由 + 混合检索 Hybird Search + 多轮反思，4 种 SSE 流式实现。</p>
          <div class="card-footer">
            <span class="card-status"><span class="status-dot"></span> 在线</span>
            <span class="card-enter">
              进入
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16"><polyline points="9 18 15 12 9 6"/></svg>
            </span>
          </div>
        </div>
      </RouterLink>

      <RouterLink class="card card-purple" to="/manus">
        <div class="card-border-glow" aria-hidden="true"></div>
        <div class="card-inner">
          <div class="card-head">
            <span class="card-icon card-icon-alt" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" width="28" height="28">
                <rect x="3" y="11" width="18" height="10" rx="2"/>
                <circle cx="12" cy="5" r="2"/>
                <path d="M12 7v4"/>
                <line x1="8" y1="16" x2="8" y2="16"/>
                <line x1="16" y1="16" x2="16" y2="16"/>
              </svg>
            </span>
            <span class="card-tag card-tag-alt">ReAct · MCP</span>
          </div>
          <h2>AI 超级智能体</h2>
          <p>自研 ReAct 四层框架，7 种内置工具 + MCP 协议动态扩展，自动终止。</p>
          <div class="card-footer">
            <span class="card-status"><span class="status-dot"></span> 在线</span>
            <span class="card-enter">
              进入
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16"><polyline points="9 18 15 12 9 6"/></svg>
            </span>
          </div>
        </div>
      </RouterLink>

      <RouterLink class="card card-orange" to="/decision-engine">
        <div class="card-border-glow" aria-hidden="true"></div>
        <div class="card-inner">
          <div class="card-head">
            <span class="card-icon card-icon-orange" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" width="28" height="28">
                <path d="M12 2L2 7l10 5 10-5-10-5z"/>
                <path d="M2 17l10 5 10-5"/>
                <path d="M2 12l10 5 10-5"/>
              </svg>
            </span>
            <span class="card-tag card-tag-orange">StateGraph · Multi-Agent</span>
          </div>
          <h2>Multi-Agent 投资决策引擎</h2>
          <p>7 节点 StateGraph 有向图编排，多模型智能路由 + 降级容错，逐节点 SSE 流式推送，且支持多种模型(Deepseek，OpenAI类型等)。</p>
          <div class="card-footer">
            <span class="card-status"><span class="status-dot"></span> 在线</span>
            <span class="card-enter">
              进入
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16"><polyline points="9 18 15 12 9 6"/></svg>
            </span>
          </div>
        </div>
      </RouterLink>
    </section>

    <!-- Footer -->
    <footer class="home-footer">
      <div class="footer-line" aria-hidden="true"></div>
      <p>Spring AI &middot; StateGraph &middot; ReAct Agent &middot; Agentic RAG &middot; MCP &middot; Langfuse &middot; SSE &middot; OpenTelemetry</p>
    </footer>
  </div>
</template>

<style scoped>
.home {
  max-width: 920px;
  margin: 0 auto;
  padding: 5rem 1.5rem 3rem;
  animation: fadeIn 0.6s ease;
}

/* ===== Hero ===== */
.hero {
  text-align: center;
  margin-bottom: 3.5rem;
  position: relative;
}

.hero-hud {
  margin-bottom: 1.25rem;
}

.hud-tag {
  display: inline-block;
  font-family: ui-monospace, 'SF Mono', 'Cascadia Code', monospace;
  font-size: 0.78rem;
  color: var(--color-neon, #38bdf8);
  letter-spacing: 0.12em;
  padding: 0.3rem 1rem;
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 4px;
  background: rgba(56, 189, 248, 0.05);
  text-shadow: 0 0 12px rgba(56, 189, 248, 0.5);
}

.hero-title {
  margin: 0 0 0.75rem;
  font-size: clamp(2.5rem, 7vw, 3.5rem);
  font-weight: 900;
  letter-spacing: -0.04em;
  line-height: 1.1;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.title-line {
  display: block;
  color: var(--color-text-primary, #eaf6ff);
}

.neon-text {
  background: linear-gradient(135deg, #38bdf8 0%, #a78bfa 50%, #f472b6 100%);
  background-size: 200% auto;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: gradient-shift 4s ease infinite;
  filter: drop-shadow(0 0 30px rgba(56, 189, 248, 0.3));
}

.lead {
  margin: 0 auto;
  font-size: 1.05rem;
  color: rgba(125, 211, 252, 0.6);
  max-width: 38rem;
  line-height: 1.7;
  letter-spacing: 0.01em;
}

/* Hero decorative bar */
.hero-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 1.5rem;
}

.bar-seg {
  width: 40px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(56, 189, 248, 0.4), transparent);
}

.bar-seg-long {
  width: 80px;
}

.bar-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--color-neon, #38bdf8);
  box-shadow: 0 0 8px var(--color-neon, #38bdf8), 0 0 20px rgba(56, 189, 248, 0.3);
}

/* ===== Cards ===== */
.cards {
  display: grid;
  gap: 1.25rem;
}

@media (min-width: 720px) {
  .cards {
    grid-template-columns: 1fr 1fr;
  }
}

@media (min-width: 1024px) {
  .cards {
    grid-template-columns: 1fr 1fr 1fr;
  }
}

.card {
  position: relative;
  border-radius: var(--radius-lg, 14px);
  cursor: pointer;
  animation: fadeInUp 0.7s ease backwards;
  outline: none;
  text-decoration: none;
}

.card:nth-child(1) { animation-delay: 0.15s; }
.card:nth-child(2) { animation-delay: 0.3s; }
.card:nth-child(3) { animation-delay: 0.45s; }

.card:focus-visible {
  outline: 2px solid var(--color-neon, #38bdf8);
  outline-offset: 3px;
}

/* Animated border glow */
.card-border-glow {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  padding: 1px;
  background: linear-gradient(
    135deg,
    rgba(56, 189, 248, 0.0),
    rgba(56, 189, 248, 0.0),
    rgba(56, 189, 248, 0.0)
  );
  -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  -webkit-mask-composite: xor;
  mask-composite: exclude;
  transition: background 0.4s ease;
  z-index: 0;
}

.card:hover .card-border-glow {
  background: linear-gradient(
    135deg,
    rgba(56, 189, 248, 0.6),
    rgba(167, 139, 250, 0.4),
    rgba(56, 189, 248, 0.6)
  );
  background-size: 200% 200%;
  animation: border-flow 3s linear infinite;
}

.card-purple:hover .card-border-glow {
  background: linear-gradient(
    135deg,
    rgba(167, 139, 250, 0.6),
    rgba(244, 114, 182, 0.4),
    rgba(167, 139, 250, 0.6)
  );
  background-size: 200% 200%;
  animation: border-flow 3s linear infinite;
}

.card-orange:hover .card-border-glow {
  background: linear-gradient(
    135deg,
    rgba(251, 146, 60, 0.6),
    rgba(239, 68, 68, 0.4),
    rgba(251, 146, 60, 0.6)
  );
  background-size: 200% 200%;
  animation: border-flow 3s linear infinite;
}

.card-inner {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.5rem;
  border-radius: inherit;
  background: rgba(6, 13, 27, 0.8);
  border: 1px solid rgba(56, 189, 248, 0.1);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  z-index: 1;
  transition:
    transform var(--transition-spring, 0.35s cubic-bezier(0.34, 1.56, 0.64, 1)),
    background 0.4s ease,
    box-shadow 0.4s ease;
}

.card:hover .card-inner {
  transform: translateY(-4px);
  background: rgba(6, 13, 27, 0.95);
  box-shadow:
    0 0 40px rgba(56, 189, 248, 0.08),
    0 20px 60px rgba(0, 0, 0, 0.4);
}

.card-purple:hover .card-inner {
  box-shadow:
    0 0 40px rgba(167, 139, 250, 0.08),
    0 20px 60px rgba(0, 0, 0, 0.4);
}

.card-orange:hover .card-inner {
  box-shadow:
    0 0 40px rgba(251, 146, 60, 0.08),
    0 20px 60px rgba(0, 0, 0, 0.4);
}

.card:active .card-inner {
  transform: translateY(-2px) scale(0.998);
}

/* Card head row */
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  border-radius: var(--radius-md, 10px);
  background: rgba(56, 189, 248, 0.08);
  color: var(--color-neon-bright, #7dd3fc);
  border: 1px solid rgba(56, 189, 248, 0.15);
  transition: background 0.3s ease, box-shadow 0.3s ease, border-color 0.3s ease;
}

.card:hover .card-icon {
  background: rgba(56, 189, 248, 0.15);
  border-color: rgba(56, 189, 248, 0.3);
  box-shadow: 0 0 20px rgba(56, 189, 248, 0.15);
}

.card-icon-alt {
  background: rgba(167, 139, 250, 0.08);
  color: #c4b5fd;
  border-color: rgba(167, 139, 250, 0.15);
}

.card:hover .card-icon-alt {
  background: rgba(167, 139, 250, 0.15);
  border-color: rgba(167, 139, 250, 0.3);
  box-shadow: 0 0 20px rgba(167, 139, 250, 0.15);
}

.card-icon-orange {
  background: rgba(251, 146, 60, 0.08);
  color: #fdba74;
  border-color: rgba(251, 146, 60, 0.15);
}

.card:hover .card-icon-orange {
  background: rgba(251, 146, 60, 0.15);
  border-color: rgba(251, 146, 60, 0.3);
  box-shadow: 0 0 20px rgba(251, 146, 60, 0.15);
}

.card-tag {
  font-family: ui-monospace, 'SF Mono', 'Cascadia Code', monospace;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-neon, #38bdf8);
  padding: 0.2rem 0.6rem;
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 4px;
  background: rgba(56, 189, 248, 0.06);
}

.card-tag-alt {
  color: var(--color-accent-purple, #a78bfa);
  border-color: rgba(167, 139, 250, 0.2);
  background: rgba(167, 139, 250, 0.06);
}

.card-tag-orange {
  color: #fb923c;
  border-color: rgba(251, 146, 60, 0.2);
  background: rgba(251, 146, 60, 0.06);
}

.card-inner h2 {
  margin: 0;
  font-size: 1.15rem;
  color: var(--color-text-primary, #eaf6ff);
  font-weight: 700;
  letter-spacing: -0.015em;
}

.card-inner p {
  margin: 0;
  font-size: 0.9rem;
  color: rgba(125, 211, 252, 0.5);
  line-height: 1.6;
}

/* Card footer */
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 0.75rem;
  border-top: 1px solid rgba(56, 189, 248, 0.08);
  margin-top: 0.25rem;
}

.card-status {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.78rem;
  color: rgba(52, 211, 153, 0.8);
  font-weight: 500;
}

.status-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 8px rgba(52, 211, 153, 0.6);
  animation: pulse-glow 2s ease-in-out infinite;
}

.card-enter {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--color-neon, #38bdf8);
  transition: transform var(--transition-fast), gap var(--transition-fast);
}

.card:hover .card-enter {
  gap: 0.5rem;
}

.card-purple .card-enter {
  color: var(--color-accent-purple, #a78bfa);
}

.card-orange .card-enter {
  color: #fb923c;
}

/* ===== Footer ===== */
.home-footer {
  text-align: center;
  margin-top: 3.5rem;
}

.footer-line {
  width: 60px;
  height: 1px;
  margin: 0 auto 1rem;
  background: linear-gradient(90deg, transparent, rgba(56, 189, 248, 0.3), transparent);
}

.home-footer p {
  margin: 0;
  font-size: 0.78rem;
  color: rgba(56, 189, 248, 0.3);
  letter-spacing: 0.08em;
  font-family: ui-monospace, 'SF Mono', 'Cascadia Code', monospace;
}
</style>
