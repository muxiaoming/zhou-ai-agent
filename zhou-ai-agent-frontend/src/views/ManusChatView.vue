<script setup lang="ts">
import axios from 'axios'
import { onMounted, ref } from 'vue'
import ChatPanel from '../components/ChatPanel.vue'
import { streamSseGet } from '../api/sseStream'
import { useStepStreamChat } from '../composables/useStepStreamChat'

const chatId = ref('')
const { messages, pushMessage, beginTurn, appendChunk, endTurn, cancelTurn } =
  useStepStreamChat()

onMounted(() => {
  chatId.value = crypto.randomUUID()
})
const input = ref('')
const sending = ref(false)
const error = ref('')

let abort: AbortController | null = null

async function onSend() {
  const text = input.value.trim()
  if (!text || sending.value || !chatId.value) return

  error.value = ''
  abort?.abort()
  abort = new AbortController()

  pushMessage({ id: crypto.randomUUID(), role: 'user', content: text })
  input.value = ''

  beginTurn()
  sending.value = true

  try {
    await streamSseGet(
      '/ai/manus/chat',
      { message: text },
      (chunk) => appendChunk(chunk),
      { signal: abort.signal },
    )
  } catch (e: unknown) {
    if (axios.isCancel(e)) {
      cancelTurn()
    } else {
      const msg = e instanceof Error ? e.message : String(e)
      error.value = msg || '请求失败，请确认后端已启动且允许跨域。'
    }
  } finally {
    endTurn()
    sending.value = false
  }
}
</script>

<template>
  <ChatPanel
    v-model:input="input"
    title="AI 超级智能体"
    :subtitle="chatId"
    :messages="messages"
    :sending="sending"
    :error="error"
    @send="onSend"
  />
</template>
