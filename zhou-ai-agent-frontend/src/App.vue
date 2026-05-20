<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="app-root">
    <RouterView />
  </div>
</template>

<style scoped>
.app-root {
  min-height: 100vh;
  background: linear-gradient(160deg, #0f172a 0%, #1e1b4b 45%, #312e81 100%);
  color: #e2e8f0;
}
</style>
