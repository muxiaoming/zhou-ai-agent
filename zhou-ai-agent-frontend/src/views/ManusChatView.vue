<script setup lang="ts">
import axios from 'axios'
import { onMounted, ref, shallowRef } from 'vue'
import ChatPanel, { type ChatMessage } from '../components/ChatPanel.vue'
import { streamSseGet } from '../api/sseStream'
import { useStepStreamChat } from '../composables/useStepStreamChat'
import { http } from '../api/http'

const CHAT_ID_KEY = 'manus-chat-id'
const DEMO_SEEN_KEY = 'manus-chat-demo-seen'
const DEMO_TIME_KEY = 'manus-demo-time'

// 判断是否为本地开发环境
const isLocalDev = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'

// 检查示例是否应该显示（本地每次都显示，生产环境2小时过期）
function shouldShowDemo(): boolean {
  if (isLocalDev) {
    // 本地开发环境：每次都显示
    return true
  }

  // 生产环境：检查是否已看且未过期
  const demoSeen = localStorage.getItem(DEMO_SEEN_KEY)
  const demoTime = localStorage.getItem(DEMO_TIME_KEY)

  if (!demoSeen || !demoTime) {
    return true
  }

  // 检查是否超过2小时（2 * 60 * 60 * 1000 毫秒）
  const elapsed = Date.now() - parseInt(demoTime, 10)
  const twoHours = 2 * 60 * 60 * 1000

  if (elapsed > twoHours) {
    // 已过期，清除标记
    localStorage.removeItem(DEMO_SEEN_KEY)
    localStorage.removeItem(DEMO_TIME_KEY)
    return true
  }

  return false
}

const chatId = ref('')
const { messages, pushMessage, beginTurn, appendChunk, endTurn, cancelTurn } =
  useStepStreamChat()
const demoMessages = shallowRef<ChatMessage[]>([])

const input = ref('')
const sending = ref(false)
const error = ref('')

let abort: AbortController | null = null

onMounted(async () => {
  // 从 localStorage 读取 chatId
  const savedChatId = localStorage.getItem(CHAT_ID_KEY)

  if (savedChatId) {
    // 有保存的 chatId，尝试加载历史对话
    chatId.value = savedChatId
    try {
      const { data } = await http.get('/ai/love_app/history', { params: { chatId: savedChatId } })
      if (data && data.length > 0) {
        data.forEach((m: { role: string; content: string }) => {
          pushMessage({
            id: crypto.randomUUID(),
            role: m.role as 'user' | 'assistant',
            content: m.content,
          })
        })
        // 有历史对话，标记为已看
        return
      }
    } catch (e) {
      console.warn('加载历史对话失败', e)
    }
  }

  // 没有历史对话，生成新的 chatId 并显示示例
  if (!savedChatId) {
    chatId.value = crypto.randomUUID()
    localStorage.setItem(CHAT_ID_KEY, chatId.value)
  }

  // 检查是否应该显示示例（本地每次显示，生产2小时过期）
  if (shouldShowDemo()) {
    try {
      // 从后端加载 Manus 示例对话
      const { data } = await http.get('/ai/manus/demo')
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
  // 本地开发环境不保存标记（每次都显示）
  if (isLocalDev) {
    return
  }
  // 生产环境：保存标记和时间戳
  localStorage.setItem(DEMO_SEEN_KEY, 'true')
  localStorage.setItem(DEMO_TIME_KEY, Date.now().toString())
}

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
    :demo-messages="demoMessages"
    :sending="sending"
    :error="error"
    @send="onSend"
    @new-chat="startNewChat"
    @demo-seen="onDemoSeen"
  />
</template>
