<script setup lang="ts">
import axios from 'axios'
import { onMounted, ref, shallowRef } from 'vue'
import ChatPanel, { type ChatMessage } from '../components/ChatPanel.vue'
import { streamSseGet } from '../api/sseStream'

const chatId = ref('')
const messages = shallowRef<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const error = ref('')

let abort: AbortController | null = null

onMounted(() => {
  chatId.value = crypto.randomUUID()
})

function pushMessage(m: ChatMessage) {
  messages.value = [...messages.value, m]
}

function patchAssistant(id: string, fn: (m: ChatMessage) => void) {
  const next = messages.value.map((m) => {
    if (m.id !== id) return m
    const copy = { ...m }
    fn(copy)
    return copy
  })
  messages.value = next
}

async function onSend() {
  const text = input.value.trim()
  if (!text || sending.value || !chatId.value) return

  error.value = ''
  abort?.abort()
  abort = new AbortController()

  pushMessage({ id: crypto.randomUUID(), role: 'user', content: text })
  input.value = ''

  const assistantId = crypto.randomUUID()
  pushMessage({ id: assistantId, role: 'assistant', content: '', streaming: true })
  sending.value = true

  try {
    await streamSseGet(
      '/ai/love_app/chat/sse',
      { message: text, chatId: chatId.value },
      (chunk) => {
        patchAssistant(assistantId, (m) => {
          m.content += chunk
        })
      },
      { signal: abort.signal },
    )
  } catch (e: unknown) {
    if (axios.isCancel(e)) {
      patchAssistant(assistantId, (m) => {
        if (!m.content) m.content = '（已取消）'
      })
    } else {
      const msg = e instanceof Error ? e.message : String(e)
      error.value = msg || '请求失败，请确认后端已启动且允许跨域。'
    }
  } finally {
    patchAssistant(assistantId, (m) => {
      m.streaming = false
    })
    sending.value = false
  }
}
</script>

<template>
  <ChatPanel
    v-model:input="input"
    title="AI 恋爱大师"
    :subtitle="chatId"
    :messages="messages"
    :sending="sending"
    :error="error"
    @send="onSend"
  />
</template>
