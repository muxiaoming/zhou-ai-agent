<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="app-root">
    <div class="cyber-bg" aria-hidden="true">
      <!-- Animated grid -->
      <div class="grid-plane"></div>
      <!-- Scanning line -->
      <div class="scan-line"></div>
      <!-- Floating orbs -->
      <div class="orb orb-cyan"></div>
      <div class="orb orb-purple"></div>
      <div class="orb orb-pink"></div>
      <!-- Corner HUD decorations -->
      <div class="hud-corner hud-tl"></div>
      <div class="hud-corner hud-tr"></div>
      <div class="hud-corner hud-bl"></div>
      <div class="hud-corner hud-br"></div>
      <!-- Noise -->
      <div class="noise"></div>
    </div>
    <RouterView />
  </div>
</template>

<style scoped>
.app-root {
  position: relative;
  min-height: 100vh;
  background: #030810;
  color: var(--color-text-primary, #eaf6ff);
  overflow: hidden;
}

/* ===== Cyber Background ===== */
.cyber-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

/* Grid plane */
.grid-plane {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(56, 189, 248, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(56, 189, 248, 0.04) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse 80% 70% at 50% 40%, rgba(0,0,0,0.6), transparent);
  -webkit-mask-image: radial-gradient(ellipse 80% 70% at 50% 40%, rgba(0,0,0,0.6), transparent);
}

/* Scanning line */
.scan-line {
  position: absolute;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(56, 189, 248, 0.3), rgba(56, 189, 248, 0.6), rgba(56, 189, 248, 0.3), transparent);
  box-shadow: 0 0 30px rgba(56, 189, 248, 0.3), 0 0 80px rgba(56, 189, 248, 0.1);
  animation: scan-line 8s linear infinite;
}

/* Orbs */
.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  animation: float 20s ease-in-out infinite;
}

.orb-cyan {
  width: 600px;
  height: 600px;
  top: -15%;
  right: -10%;
  background: radial-gradient(circle, rgba(56, 189, 248, 0.2), transparent 70%);
  animation-duration: 25s;
}

.orb-purple {
  width: 500px;
  height: 500px;
  bottom: -20%;
  left: -15%;
  background: radial-gradient(circle, rgba(167, 139, 250, 0.18), transparent 70%);
  animation-duration: 20s;
  animation-delay: -7s;
}

.orb-pink {
  width: 350px;
  height: 350px;
  top: 50%;
  left: 45%;
  background: radial-gradient(circle, rgba(244, 114, 182, 0.1), transparent 70%);
  animation-duration: 28s;
  animation-delay: -12s;
}

/* HUD Corners */
.hud-corner {
  position: absolute;
  width: 60px;
  height: 60px;
  opacity: 0.25;
}

.hud-tl {
  top: 20px;
  left: 20px;
  border-top: 2px solid var(--color-neon, #38bdf8);
  border-left: 2px solid var(--color-neon, #38bdf8);
}

.hud-tr {
  top: 20px;
  right: 20px;
  border-top: 2px solid var(--color-neon, #38bdf8);
  border-right: 2px solid var(--color-neon, #38bdf8);
}

.hud-bl {
  bottom: 20px;
  left: 20px;
  border-bottom: 2px solid var(--color-neon, #38bdf8);
  border-left: 2px solid var(--color-neon, #38bdf8);
}

.hud-br {
  bottom: 20px;
  right: 20px;
  border-bottom: 2px solid var(--color-neon, #38bdf8);
  border-right: 2px solid var(--color-neon, #38bdf8);
}

/* Noise */
.noise {
  position: absolute;
  inset: 0;
  opacity: 0.04;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  background-size: 200px 200px;
}

/* Content above background */
.app-root > :deep(.home),
.app-root > :deep(.chat-page) {
  position: relative;
  z-index: 1;
}

@media (max-width: 768px) {
  .hud-corner { display: none; }
  .orb-cyan { width: 350px; height: 350px; }
  .orb-purple { width: 300px; height: 300px; }
  .orb-pink { width: 200px; height: 200px; }
}
</style>
