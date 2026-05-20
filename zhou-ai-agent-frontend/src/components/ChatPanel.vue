<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  streaming?: boolean
}

const props = defineProps<{
  title: string
  subtitle?: string
  messages: ChatMessage[]
  sending: boolean
  error?: string
}>()

const input = defineModel<string>('input', { default: '' })

const emit = defineEmits<{
  send: []
}>()

const threadEl = ref<HTMLElement | null>(null)

watch(
  () => props.messages,
  async () => {
    await nextTick()
    const el = threadEl.value
    if (!el) return
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  },
  { deep: true },
)

function onSubmit() {
  emit('send')
}
</script>

<template>
  <div class="chat-page">
    <header class="top">
      <RouterLink class="back" to="/">← 返回</RouterLink>
      <div class="titles">
        <h1>{{ title }}</h1>
        <p v-if="subtitle" class="sub">会话 ID：{{ subtitle }}</p>
      </div>
    </header>

    <div class="board">
      <div ref="threadEl" class="thread" role="log" aria-live="polite">
        <p v-if="messages.length === 0" class="empty">发送第一条消息开始对话。</p>
        <div
          v-for="m in messages"
          :key="m.id"
          class="row"
          :class="m.role === 'user' ? 'row-user' : 'row-ai'"
        >
          <div class="bubble" :class="m.role === 'user' ? 'bubble-user' : 'bubble-ai'">
            <span v-if="m.streaming" class="cursor" aria-hidden="true">▍</span>
            {{ m.content }}
          </div>
        </div>
      </div>

      <p v-if="error" class="err" role="alert">{{ error }}</p>

      <form class="composer" @submit.prevent="onSubmit">
        <input
          v-model="input"
          type="text"
          class="field"
          placeholder="输入消息，Enter 发送"
          :disabled="sending"
          autocomplete="off"
        />
        <button type="submit" class="btn" :disabled="sending || !input.trim()">
          {{ sending ? '发送中…' : '发送' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 900px;
  margin: 0 auto;
  padding: 0 1rem 1rem;
}

.top {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1rem 0 0.75rem;
  flex-shrink: 0;
}

.back {
  margin-top: 0.2rem;
  font-size: 0.9rem;
  color: #a5b4fc;
  white-space: nowrap;
}

.back:hover {
  text-decoration: underline;
}

.titles h1 {
  margin: 0;
  font-size: 1.25rem;
  color: #f8fafc;
}

.sub {
  margin: 0.35rem 0 0;
  font-size: 0.8rem;
  color: #94a3b8;
  word-break: break-all;
}

.board {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid rgba(148, 163, 184, 0.2);
  overflow: hidden;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.45);
}

.thread {
  flex: 1;
  overflow-y: auto;
  padding: 1rem 1rem 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.empty {
  margin: 2rem auto;
  color: #64748b;
  font-size: 0.95rem;
}

.row {
  display: flex;
}

.row-user {
  justify-content: flex-end;
}

.row-ai {
  justify-content: flex-start;
}

.bubble {
  max-width: min(78%, 520px);
  padding: 0.65rem 0.85rem;
  border-radius: 14px;
  font-size: 0.95rem;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-user {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #f8fafc;
  border-bottom-right-radius: 4px;
}

.bubble-ai {
  background: rgba(30, 41, 59, 0.95);
  color: #e2e8f0;
  border: 1px solid rgba(100, 116, 139, 0.35);
  border-bottom-left-radius: 4px;
  max-height: min(60vh, 420px);
  overflow-y: auto;
}

.cursor {
  display: inline-block;
  animation: blink 1s step-end infinite;
  margin-right: 2px;
  color: #a5b4fc;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.err {
  margin: 0 1rem;
  padding: 0.5rem 0.75rem;
  border-radius: 8px;
  background: rgba(127, 29, 29, 0.35);
  border: 1px solid rgba(248, 113, 113, 0.35);
  color: #fecaca;
  font-size: 0.85rem;
}

.composer {
  display: flex;
  gap: 0.6rem;
  padding: 0.75rem 1rem 1rem;
  border-top: 1px solid rgba(51, 65, 85, 0.6);
  background: rgba(15, 23, 42, 0.85);
  flex-shrink: 0;
}

.field {
  flex: 1;
  border-radius: 10px;
  border: 1px solid rgba(100, 116, 139, 0.45);
  background: rgba(15, 23, 42, 0.9);
  color: #f1f5f9;
  padding: 0.55rem 0.75rem;
}

.field:focus {
  outline: 2px solid rgba(129, 140, 248, 0.55);
  outline-offset: 0;
}

.field:disabled {
  opacity: 0.65;
}

.btn {
  border: none;
  border-radius: 10px;
  padding: 0.55rem 1.1rem;
  font-weight: 600;
  cursor: pointer;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  color: white;
}

.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
