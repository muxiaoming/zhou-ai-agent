<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <div class="home">
    <header class="hero">
      <p class="eyebrow">Vue 3 · Axios · SSE</p>
      <h1>AI 应用中心</h1>
      <p class="lead">选择一个应用进入独立会话界面。</p>
    </header>

    <section class="cards">
      <RouterLink class="card" to="/love">
        <span class="card-icon" aria-hidden="true">💬</span>
        <div>
          <h2>AI 恋爱大师</h2>
          <p>聊天室风格，自动生成会话 ID，SSE 流式对话。</p>
        </div>
        <span class="chevron">→</span>
      </RouterLink>

      <RouterLink class="card" to="/manus">
        <span class="card-icon" aria-hidden="true">🤖</span>
        <div>
          <h2>AI 超级智能体</h2>
          <p>与恋爱大师相同的交互，对接 Manus 流式接口。</p>
        </div>
        <span class="chevron">→</span>
      </RouterLink>
    </section>
  </div>
</template>

<style scoped>
.home {
  max-width: 880px;
  margin: 0 auto;
  padding: 3rem 1.5rem 4rem;
}

.hero {
  margin-bottom: 2.5rem;
}

.eyebrow {
  margin: 0 0 0.5rem;
  font-size: 0.85rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #a5b4fc;
}

h1 {
  margin: 0 0 0.75rem;
  font-size: clamp(1.75rem, 4vw, 2.25rem);
  font-weight: 700;
  color: #f8fafc;
}

.lead {
  margin: 0;
  font-size: 1.05rem;
  color: #cbd5e1;
  max-width: 36rem;
  line-height: 1.6;
}

.cards {
  display: grid;
  gap: 1rem;
}

@media (min-width: 720px) {
  .cards {
    grid-template-columns: 1fr 1fr;
  }
}

.card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem 1.35rem;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(148, 163, 184, 0.25);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.35);
  transition:
    transform 0.18s ease,
    border-color 0.18s ease,
    background 0.18s ease;
}

.card:hover {
  transform: translateY(-2px);
  border-color: rgba(165, 180, 252, 0.55);
  background: rgba(30, 27, 75, 0.55);
}

.card-icon {
  font-size: 1.75rem;
  line-height: 1;
}

.card h2 {
  margin: 0 0 0.35rem;
  font-size: 1.1rem;
  color: #f1f5f9;
}

.card p {
  margin: 0;
  font-size: 0.9rem;
  color: #94a3b8;
  line-height: 1.5;
}

.chevron {
  font-size: 1.25rem;
  color: #a5b4fc;
}
</style>
