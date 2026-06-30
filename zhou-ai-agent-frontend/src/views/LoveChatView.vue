<script setup lang="ts">
import axios from 'axios'
import { onMounted, ref, shallowRef } from 'vue'
import ChatPanel, { type ChatMessage } from '../components/ChatPanel.vue'
import { streamSseGet } from '../api/sseStream'
import { http } from '../api/http'

const CHAT_ID_KEY = 'love-chat-id'
const DEMO_SEEN_KEY = 'love-chat-demo-seen'
const DEMO_TIME_KEY = 'love-demo-time'

// 判断是否为本地开发环境
const isLocalDev = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'

// 检查示例是否应该显示（本地每次都显示，生产环境2小时过期）
function shouldShowDemo(): boolean {
  if (isLocalDev) {
    return true
  }
  const demoSeen = localStorage.getItem(DEMO_SEEN_KEY)
  const demoTime = localStorage.getItem(DEMO_TIME_KEY)
  if (!demoSeen || !demoTime) return true
  const elapsed = Date.now() - parseInt(demoTime, 10)
  const twoHours = 2 * 60 * 60 * 1000
  if (elapsed > twoHours) {
    localStorage.removeItem(DEMO_SEEN_KEY)
    localStorage.removeItem(DEMO_TIME_KEY)
    return true
  }
  return false
}

const chatId = ref('')
const messages = shallowRef<ChatMessage[]>([])
const demoMessages = shallowRef<ChatMessage[]>([])
const input = ref('')
const sending = ref(false)
const error = ref('')
const ragMode = ref(false)

let abort: AbortController | null = null

onMounted(async () => {
  const savedChatId = localStorage.getItem(CHAT_ID_KEY)

  // 本地开发环境：清除旧缓存，每次都显示新示例
  if (isLocalDev && savedChatId) {
    localStorage.removeItem(CHAT_ID_KEY)
    localStorage.removeItem(DEMO_SEEN_KEY)
    localStorage.removeItem(DEMO_TIME_KEY)
  }

  const currentChatId = localStorage.getItem(CHAT_ID_KEY)

  if (currentChatId) {
    chatId.value = currentChatId
    try {
      const { data } = await http.get('/ai/love_app/history', { params: { chatId: currentChatId } })
      if (data && data.length > 0) {
        messages.value = data.map((m: { role: string; content: string }, i: number) => ({
          id: `history-${i}`,
          role: m.role as 'user' | 'assistant',
          content: m.content,
        }))
        return
      }
    } catch (e) {
      console.warn('加载历史对话失败', e)
    }
  }

  if (!currentChatId) {
    chatId.value = crypto.randomUUID()
    localStorage.setItem(CHAT_ID_KEY, chatId.value)
  }

  if (shouldShowDemo()) {
    try {
      const { data } = await http.get('/ai/love_app/demo')
      demoMessages.value = data.map((m: { role: string; content: string }, i: number) => ({
        id: `demo-${i}`,
        role: m.role as 'user' | 'assistant',
        content: m.content,
      }))
    } catch (e) {
      console.warn('加载示例对话失败', e)
    }
  }
})

function startNewChat() {
  chatId.value = crypto.randomUUID()
  localStorage.setItem(CHAT_ID_KEY, chatId.value)
  messages.value = []
}

function onDemoSeen() {
  if (isLocalDev) return
  localStorage.setItem(DEMO_SEEN_KEY, 'true')
  localStorage.setItem(DEMO_TIME_KEY, Date.now().toString())
}

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

  const endpoint = ragMode.value
    ? '/ai/love_app/rag'
    : '/ai/love_app/chat/sse'

  try {
    await streamSseGet(
      endpoint,
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
    :demo-messages="demoMessages"
    :sending="sending"
    :error="error"
    :rag-mode="ragMode"
    :show-rag-toggle="true"
    @send="onSend"
    @toggle-rag="ragMode = $event"
    @new-chat="startNewChat"
    @demo-seen="onDemoSeen"
  />
</template>
