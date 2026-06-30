<script setup lang="ts">
import { nextTick, ref, watch, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import { marked } from 'marked'

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
  demoMessages?: ChatMessage[]
  sending: boolean
  error?: string
  ragMode?: boolean
  showRagToggle?: boolean
}>()

const input = defineModel<string>('input', { default: '' })

const emit = defineEmits<{
  send: [message: string]
  'toggle-rag': [value: boolean]
  'new-chat': []
  'demo-seen': []
}>()

// ========== 图片预览功能 ==========
const showImagePreview = ref(false)
const previewImageUrl = ref('')

function openImagePreview(url: string) {
  previewImageUrl.value = url
  showImagePreview.value = true
}

function closeImagePreview() {
  showImagePreview.value = false
  previewImageUrl.value = ''
}

function downloadImage() {
  const link = document.createElement('a')
  link.href = previewImageUrl.value
  link.download = 'image.jpg'
  link.target = '_blank'
  link.click()
}

onMounted(() => {
  (window as any).__openImagePreview = openImagePreview
})

onUnmounted(() => {
  delete (window as any).__openImagePreview
})

// ========== Markdown 渲染 ==========
marked.setOptions({
  breaks: true,
  gfm: true,
})

function renderMarkdown(content: string): string {
  if (!content) return ''

  try {
    // 预处理：确保换行符正确
    let processedContent = content
      // 将连续的换行符标准化为两个换行符（段落分隔）
      .replace(/\n{3,}/g, '\n\n')

    // 先清理无效的图片格式：src="<a href="URL">URL</a>" -> src="URL"
    processedContent = processedContent.replace(
      /<img\s+([^>]*?)src="<a\s+href="([^"]+)"[^>]*>.*?<\/a>"([^>]*?)>/gi,
      '<img $1src="$2"$3>'
    )

    // 使用 marked 解析 Markdown
    const result = marked.parse(processedContent)

    // marked.parse 可能返回字符串或 Promise，确保返回字符串
    let html: string
    if (typeof result === 'string') {
      html = result
    } else if (result instanceof Promise) {
      // 如果返回 Promise（同步模式下不应该发生），使用原始内容
      console.warn('marked.parse returned a Promise, using raw content')
      return processedContent
    } else {
      // 其他情况，转换为字符串
      html = String(result)
    }

    // 给所有图片添加点击预览功能
    html = html.replace(
      /<img\s+([^>]*?)src=["']([^"']+)["']([^>]*?)>/gi,
      (match, before, src, after) => {
        const escapedSrc = src.replace(/'/g, "\\'").replace(/"/g, '\\"')
        return `<img ${before}src="${src}"${after} class="clickable-image" onclick="window.__openImagePreview('${escapedSrc}')" />`
      }
    )

    return html
  } catch (e) {
    console.error('renderMarkdown error:', e)
    return content
  }
}

function extractStepBadge(content: string): { badge: string | null, body: string } {
  const match = content.match(/^(Step \d+):\s*/m)
  if (match) {
    return {
      badge: match[1],
      body: content.replace(match[0], '').trim()
    }
  }
  return { badge: null, body: content }
}

const threadEl = ref<HTMLElement | null>(null)
const showScrollBtn = ref(false)

// 示例对话动画状态
const visibleDemoMessages = ref<ChatMessage[]>([])
const demoAnimating = ref(false)
const demoComplete = ref(false)
const demoStarted = ref(false)

// 启动示例对话动画 - 打字机效果
async function startDemoAnimation() {
  if (demoStarted.value || !props.demoMessages || props.demoMessages.length === 0) return
  demoStarted.value = true
  demoAnimating.value = true

  for (const msg of props.demoMessages) {
    const fullContent = msg.content
    let currentContent = ''

    // 先显示空消息
    visibleDemoMessages.value = [
      ...visibleDemoMessages.value,
      { ...msg, content: '', streaming: true },
    ]
    await nextTick()
    scrollToBottom()

    // 打字机效果：逐字显示 (20ms/字)
    for (let i = 0; i < fullContent.length; i++) {
      currentContent = fullContent.substring(0, i + 1)
      visibleDemoMessages.value = visibleDemoMessages.value.map((m, idx) =>
        idx === visibleDemoMessages.value.length - 1
          ? { ...m, content: currentContent }
          : m,
      )
      // 每 5 个字符滚动一次
      if (i % 5 === 0) {
        await nextTick()
        scrollToBottom()
      }
      await new Promise((r) => setTimeout(r, 20))
    }

    // 完成当前消息
    visibleDemoMessages.value = visibleDemoMessages.value.map((m, idx) =>
      idx === visibleDemoMessages.value.length - 1
        ? { ...m, streaming: false }
        : m,
    )
    await nextTick()
    scrollToBottom()

    // 消息间停顿
    await new Promise((r) => setTimeout(r, 400))
  }
  demoAnimating.value = false
  demoComplete.value = true
  emit('demo-seen')
}

// 监听 demoMessages 变化，加载后启动动画
watch(
  () => props.demoMessages,
  (newVal) => {
    if (newVal && newVal.length > 0) {
      startDemoAnimation()
    }
  },
  { immediate: true },
)

watch(
  () => props.messages,
  async () => {
    await nextTick()
    scrollToBottom()
  },
  { deep: true },
)

function scrollToBottom() {
  const el = threadEl.value
  if (!el) return
  el.scrollTop = el.scrollHeight
  showScrollBtn.value = false
}

function onThreadScroll() {
  const el = threadEl.value
  if (!el) return
  const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  showScrollBtn.value = distFromBottom > 120
}

function autoResize(event: Event) {
  const textarea = event.target as HTMLTextAreaElement
  textarea.style.height = 'auto'
  textarea.style.height = Math.min(textarea.scrollHeight, 150) + 'px'
}

function onSubmit() {
  emit('send', input.value)  // 传递 input 值
  // 发送后重置高度
  const textarea = document.querySelector('.textarea-field') as HTMLTextAreaElement
  if (textarea) {
    textarea.style.height = 'auto'
  }
}
</script>

<template>
  <div class="chat-page">
    <header class="top">
      <RouterLink class="back" to="/">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
        <span>返回</span>
      </RouterLink>
      <div class="titles">
        <h1>{{ title }}</h1>
        <p v-if="subtitle" class="sub">会话 ID：{{ subtitle }}</p>
      </div>
      <button v-if="messages.length > 0 || (demoMessages && demoMessages.length > 0)" class="new-chat-btn" title="新建会话" @click="emit('new-chat')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        <span>新建</span>
      </button>
      <label v-if="showRagToggle" class="rag-toggle" title="开启后基于知识库增强回答质量">
        <input
          type="checkbox"
          :checked="ragMode"
          @change="emit('toggle-rag', ($event.target as HTMLInputElement).checked)"
        />
        <span class="rag-toggle-track">
          <span class="rag-toggle-thumb"></span>
        </span>
        <span class="rag-toggle-label">{{ ragMode ? '知识增强' : '普通模式' }}</span>
      </label>
    </header>

    <div class="board">
      <div
        ref="threadEl"
        class="thread"
        role="log"
        aria-live="polite"
        @scroll="onThreadScroll"
      >
        <!-- 示例对话区域（动画中或动画完成后显示） -->
        <div v-if="messages.length === 0 && demoMessages && demoMessages.length > 0" class="demo-section">
          <div v-if="!demoComplete" class="demo-header">
            <svg class="demo-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 2L2 7l10 5 10-5-10-5z"/>
              <path d="M2 17l10 5 10-5"/>
              <path d="M2 12l10 5 10-5"/>
            </svg>
            <span>示例对话</span>
          </div>
          <div class="demo-messages">
            <div
              v-for="m in visibleDemoMessages"
              :key="m.id"
              class="row"
              :class="m.role === 'user' ? 'row-user' : 'row-ai'"
            >
              <div v-if="m.role === 'assistant'" class="avatar avatar-ai" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="10" rx="2"/>
                  <circle cx="12" cy="5" r="2"/>
                  <path d="M12 7v4"/>
                </svg>
              </div>
              <div class="bubble" :class="m.role === 'user' ? 'bubble-user' : 'bubble-ai'">
                <div v-if="m.streaming && !m.content" class="typing-dots" aria-label="AI 正在输入">
                  <span></span><span></span><span></span>
                </div>
                <template v-else>
                  <span class="bubble-text">{{ m.content }}</span>
                  <span v-if="m.streaming" class="cursor" aria-hidden="true">▍</span>
                </template>
              </div>
              <div v-if="m.role === 'user'" class="avatar avatar-user" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                  <circle cx="12" cy="7" r="4"/>
                </svg>
              </div>
            </div>
          </div>
          <p v-if="demoComplete" class="demo-hint">← 发送消息开始你的对话</p>
          <p v-else class="demo-hint demo-loading">正在加载示例...</p>
        </div>

        <!-- 空状态提示 -->
        <p v-else-if="messages.length === 0" class="empty">
          <svg class="empty-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <span>发送第一条消息开始对话</span>
        </p>

        <TransitionGroup name="msg" tag="div" class="msg-list">
          <div
            v-for="m in messages"
            :key="m.id"
            class="row"
            :class="m.role === 'user' ? 'row-user' : 'row-ai'"
          >
            <div v-if="m.role === 'assistant'" class="avatar avatar-ai" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="11" width="18" height="10" rx="2"/>
                <circle cx="12" cy="5" r="2"/>
                <path d="M12 7v4"/>
              </svg>
            </div>
            <div class="bubble" :class="m.role === 'user' ? 'bubble-user' : 'bubble-ai'">
              <div v-if="m.streaming && !m.content" class="typing-dots" aria-label="AI 正在输入">
                <span></span><span></span><span></span>
              </div>
              <template v-else>
                <template v-if="m.role === 'assistant'">
                  <!-- 检查是否有 Step 标签 -->
                  <template v-if="extractStepBadge(m.content).badge">
                    <div class="step-badge">{{ extractStepBadge(m.content).badge }}</div>
                    <div class="step-content markdown-content" v-html="renderMarkdown(extractStepBadge(m.content).body)"></div>
                  </template>
                  <template v-else>
                    <div class="markdown-content" v-html="renderMarkdown(m.content)"></div>
                  </template>
                </template>
                <span v-else class="bubble-text">{{ m.content }}</span>
                <span v-if="m.streaming" class="cursor" aria-hidden="true">▍</span>
              </template>
            </div>
            <div v-if="m.role === 'user'" class="avatar avatar-user" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
            </div>
          </div>
        </TransitionGroup>
      </div>

      <Transition name="fade">
        <button
          v-if="showScrollBtn"
          class="scroll-bottom-btn"
          aria-label="滚动到底部"
          @click="scrollToBottom"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="18" height="18">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>
      </Transition>

      <p v-if="error" class="err" role="alert">{{ error }}</p>

      <form class="composer" @submit.prevent="onSubmit">
        <textarea
          v-model="input"
          class="field textarea-field"
          :placeholder="demoAnimating ? '示例加载中...' : '输入消息，Enter 发送，Shift+Enter 换行'"
          :disabled="sending || demoAnimating"
          autocomplete="off"
          rows="1"
          @keydown.enter.exact.prevent="onSubmit"
          @input="autoResize"
        ></textarea>
        <button type="submit" class="btn" :disabled="sending || demoAnimating || !input.trim()">
          <svg v-if="sending" class="btn-icon spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
          </svg>
          <svg v-else class="btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
          <span>{{ sending ? '发送中' : '发送' }}</span>
        </button>
      </form>
    </div>

    <!-- 图片预览模态框 -->
    <div v-if="showImagePreview" class="image-preview-overlay" @click="closeImagePreview">
      <div class="image-preview-content" @click.stop>
        <img :src="previewImageUrl" class="preview-image" />
        <div class="preview-actions">
          <button @click="downloadImage" class="preview-btn">⬇️ 下载</button>
          <button @click="closeImagePreview" class="preview-btn">✕ 关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  max-width: 900px;
  margin: 0 auto;
  padding: 0 1rem 0;
  position: relative;
  z-index: 1;
}

/* ===== Top Bar ===== */
.top {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.1rem 0 0.85rem;
  flex-shrink: 0;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.1rem;
  font-size: 0.88rem;
  font-weight: 500;
  color: var(--color-neon, #38bdf8);
  white-space: nowrap;
  padding: 0.3rem 0.7rem;
  border-radius: var(--radius-sm, 6px);
  border: 1px solid rgba(56, 189, 248, 0.15);
  background: rgba(56, 189, 248, 0.04);
  transition: background var(--transition-fast), border-color var(--transition-fast), box-shadow var(--transition-fast);
  outline: none;
  text-decoration: none;
}

.back:hover {
  background: rgba(56, 189, 248, 0.1);
  border-color: rgba(56, 189, 248, 0.3);
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.15);
}

.back:focus-visible {
  outline: 2px solid var(--color-neon, #38bdf8);
  outline-offset: 2px;
}

.titles h1 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--color-text-primary, #eaf6ff);
  letter-spacing: -0.015em;
}

.sub {
  margin: 0.3rem 0 0;
  font-size: 0.72rem;
  color: rgba(56, 189, 248, 0.35);
  word-break: break-all;
  font-family: ui-monospace, 'SF Mono', 'Cascadia Code', monospace;
  letter-spacing: 0.04em;
}

/* ===== New Chat Button ===== */
.new-chat-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-left: auto;
  padding: 0.35rem 0.6rem;
  font-size: 0.78rem;
  font-weight: 500;
  color: rgba(56, 189, 248, 0.7);
  background: rgba(56, 189, 248, 0.06);
  border: 1px solid rgba(56, 189, 248, 0.15);
  border-radius: var(--radius-sm, 6px);
  cursor: pointer;
  transition: all var(--transition-fast);
  white-space: nowrap;
}

.new-chat-btn:hover {
  background: rgba(56, 189, 248, 0.12);
  border-color: rgba(56, 189, 248, 0.3);
  color: var(--color-neon, #38bdf8);
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.1);
}

.new-chat-btn svg {
  flex-shrink: 0;
}

/* ===== RAG Toggle ===== */
.rag-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  margin-left: auto;
  cursor: pointer;
  user-select: none;
  padding: 0.35rem 0.7rem;
  border-radius: var(--radius-sm, 6px);
  border: 1px solid rgba(167, 139, 250, 0.15);
  background: rgba(167, 139, 250, 0.04);
  transition: background var(--transition-fast), border-color var(--transition-fast);
}

.rag-toggle:hover {
  background: rgba(167, 139, 250, 0.1);
  border-color: rgba(167, 139, 250, 0.3);
}

.rag-toggle input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.rag-toggle-track {
  position: relative;
  width: 32px;
  height: 18px;
  border-radius: 9px;
  background: rgba(56, 189, 248, 0.2);
  border: 1px solid rgba(56, 189, 248, 0.25);
  transition: background var(--transition-fast), border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.rag-toggle input:checked + .rag-toggle-track {
  background: rgba(167, 139, 250, 0.35);
  border-color: rgba(167, 139, 250, 0.5);
  box-shadow: 0 0 12px rgba(167, 139, 250, 0.2);
}

.rag-toggle-thumb {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--color-neon, #38bdf8);
  box-shadow: 0 0 6px rgba(56, 189, 248, 0.4);
  transition: transform var(--transition-fast), background var(--transition-fast), box-shadow var(--transition-fast);
}

.rag-toggle input:checked + .rag-toggle-track .rag-toggle-thumb {
  transform: translateX(14px);
  background: #a78bfa;
  box-shadow: 0 0 8px rgba(167, 139, 250, 0.5);
}

.rag-toggle-label {
  font-size: 0.78rem;
  font-weight: 500;
  color: rgba(56, 189, 248, 0.6);
  transition: color var(--transition-fast);
  white-space: nowrap;
}

.rag-toggle input:checked ~ .rag-toggle-label {
  color: rgba(167, 139, 250, 0.85);
}

/* ===== Demo Section ===== */
.demo-section {
  padding: 0 0 1rem;
  margin-bottom: 1rem;
  border-bottom: 1px dashed rgba(56, 189, 248, 0.15);
}

.demo-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 1rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: rgba(167, 139, 250, 0.85);
}

.demo-icon {
  width: 18px;
  height: 18px;
  color: var(--color-accent-purple, #a78bfa);
}

.demo-messages {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.demo-hint {
  margin-top: 1rem;
  text-align: center;
  font-size: 0.78rem;
  color: rgba(56, 189, 248, 0.35);
  font-style: italic;
}

.demo-loading {
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}

/* ===== Board ===== */
.board {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: var(--radius-xl, 18px) var(--radius-xl, 18px) 0 0;
  background: rgba(6, 13, 27, 0.85);
  border: 1px solid rgba(56, 189, 248, 0.1);
  border-bottom: none;
  overflow: hidden;
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  position: relative;
  box-shadow:
    0 1px 0 rgba(56, 189, 248, 0.05) inset,
    0 0 60px rgba(56, 189, 248, 0.03);
}

/* Neon top accent line */
.board::before {
  content: '';
  position: absolute;
  top: 0;
  left: 10%;
  right: 10%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(56, 189, 248, 0.5), transparent);
  z-index: 5;
}

/* ===== Thread ===== */
.thread {
  flex: 1;
  overflow-y: auto;
  padding: 1.5rem 1.25rem 1rem;
  scroll-behavior: smooth;
}

.msg-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.msg-enter-active {
  transition: opacity 0.3s ease, transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.msg-enter-from {
  opacity: 0;
  transform: translateY(10px) scale(0.97);
}

/* ===== Empty State ===== */
.empty {
  margin: 4rem auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.85rem;
  color: rgba(56, 189, 248, 0.25);
  font-size: 0.95rem;
}

.empty-icon {
  width: 52px;
  height: 52px;
  color: rgba(56, 189, 248, 0.15);
  animation: pulse-glow 3s ease-in-out infinite;
}

/* ===== Rows & Bubbles ===== */
.row {
  display: flex;
  align-items: flex-end;
  gap: 0.6rem;
  max-width: 82%;
}

.row-user {
  justify-content: flex-end;
  margin-left: auto;
}

.row-ai {
  justify-content: flex-start;
}

.avatar {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 2px;
}

.avatar svg {
  width: 16px;
  height: 16px;
}

.avatar-ai {
  background: rgba(56, 189, 248, 0.08);
  color: var(--color-neon, #38bdf8);
  border: 1px solid rgba(56, 189, 248, 0.15);
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.08);
}

.avatar-user {
  background: rgba(167, 139, 250, 0.1);
  color: #c4b5fd;
  border: 1px solid rgba(167, 139, 250, 0.15);
}

.bubble {
  padding: 0.75rem 1rem;
  border-radius: 14px;
  font-size: 0.92rem;
  line-height: 1.7;
  word-break: break-word;
  position: relative;
}

.bubble-text {
  white-space: pre-wrap;
  display: inline;
}

/* ========== Step N: 标签美化 ========== */
.step-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.5rem;
  padding: 0.25rem 0.6rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-neon-bright, #7dd3fc);
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.12) 0%, rgba(167, 139, 250, 0.12) 100%);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 16px;
  letter-spacing: 0.02em;
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.08);
}

.step-badge::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-neon, #38bdf8);
  box-shadow: 0 0 8px rgba(56, 189, 248, 0.6);
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 0.5; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

/* Step 内容 */
.step-content {
  line-height: 1.7;
}

.bubble-user {
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.15) 0%, rgba(167, 139, 250, 0.2) 100%);
  color: var(--color-text-primary, #eaf6ff);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-bottom-right-radius: 4px;
  box-shadow: 0 0 20px rgba(56, 189, 248, 0.06);
}

.bubble-ai {
  background: rgba(10, 18, 38, 0.92);
  color: rgba(234, 246, 255, 0.85);
  border: 1px solid rgba(56, 189, 248, 0.06);
  border-bottom-left-radius: 4px;
  max-height: min(60vh, 440px);
  overflow-y: auto;
}

/* ===== Typing Dots ===== */
.typing-dots {
  display: inline-flex;
  gap: 5px;
  padding: 4px 2px;
}

.typing-dots span {
  display: block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-neon, #38bdf8);
  box-shadow: 0 0 6px rgba(56, 189, 248, 0.5);
  animation: dot-bounce 1.4s ease-in-out infinite;
}

.typing-dots span:nth-child(2) { animation-delay: 0.15s; }
.typing-dots span:nth-child(3) { animation-delay: 0.3s; }

@keyframes dot-bounce {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: scale(0.75);
    box-shadow: 0 0 4px rgba(56, 189, 248, 0.2);
  }
  40% {
    opacity: 1;
    transform: scale(1.1);
    box-shadow: 0 0 10px rgba(56, 189, 248, 0.6);
  }
}

/* ===== Cursor ===== */
.cursor {
  display: inline-block;
  animation: blink 0.8s step-end infinite;
  margin-left: 1px;
  color: var(--color-neon, #38bdf8);
  text-shadow: 0 0 8px rgba(56, 189, 248, 0.6);
}

@keyframes blink {
  50% { opacity: 0; }
}

/* ===== Scroll-to-bottom ===== */
.scroll-bottom-btn {
  position: absolute;
  bottom: 96px;
  left: 50%;
  transform: translateX(-50%);
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 1px solid rgba(56, 189, 248, 0.2);
  background: rgba(6, 13, 27, 0.9);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--color-neon, #38bdf8);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 0 15px rgba(56, 189, 248, 0.1);
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    transform var(--transition-fast);
  z-index: 10;
  outline: none;
}

.scroll-bottom-btn:hover {
  background: rgba(56, 189, 248, 0.1);
  border-color: rgba(56, 189, 248, 0.4);
  box-shadow: 0 0 25px rgba(56, 189, 248, 0.15);
  transform: translateX(-50%) translateY(-2px);
}

.scroll-bottom-btn:focus-visible {
  outline: 2px solid var(--color-neon, #38bdf8);
  outline-offset: 2px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(4px);
}

/* ===== Error ===== */
.err {
  margin: 0 1rem;
  padding: 0.55rem 0.85rem;
  border-radius: var(--radius-sm, 6px);
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.2);
  color: #fca5a5;
  font-size: 0.85rem;
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

/* ===== Composer ===== */
.composer {
  display: flex;
  gap: 0.6rem;
  padding: 0.85rem 1.1rem 1.1rem;
  border-top: 1px solid rgba(56, 189, 248, 0.08);
  background: rgba(6, 13, 27, 0.9);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  flex-shrink: 0;
}

.field {
  flex: 1;
  border-radius: var(--radius-sm, 6px);
  border: 1px solid rgba(56, 189, 248, 0.12);
  background: rgba(3, 8, 16, 0.8);
  color: var(--color-text-primary, #eaf6ff);
  padding: 0.65rem 0.9rem;
  font-size: 0.94rem;
  transition: border-color var(--transition-normal), box-shadow var(--transition-normal);
  outline: none;
}

.field::placeholder {
  color: rgba(56, 189, 248, 0.2);
}

.field:focus {
  border-color: rgba(56, 189, 248, 0.4);
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.08), 0 0 20px rgba(56, 189, 248, 0.05);
}

.field:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.textarea-field {
  resize: none;
  min-height: 4rem;
  max-height: 150px;
  line-height: 1.5;
  overflow-y: auto;
}

.textarea-field::-webkit-scrollbar {
  width: 4px;
}

.textarea-field::-webkit-scrollbar-thumb {
  background: rgba(56, 189, 248, 0.3);
  border-radius: 2px;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border: none;
  border-radius: var(--radius-sm, 6px);
  padding: 0.65rem 1.2rem;
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.2), rgba(167, 139, 250, 0.2));
  color: var(--color-neon-bright, #7dd3fc);
  border: 1px solid rgba(56, 189, 248, 0.25);
  transition: background var(--transition-fast), box-shadow var(--transition-normal), transform 0.1s ease, border-color var(--transition-fast);
  white-space: nowrap;
  outline: none;
  position: relative;
  overflow: hidden;
}

.btn:not(:disabled):hover {
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.3), rgba(167, 139, 250, 0.25));
  border-color: rgba(56, 189, 248, 0.5);
  box-shadow: 0 0 25px rgba(56, 189, 248, 0.2), 0 0 60px rgba(56, 189, 248, 0.05);
  color: #eaf6ff;
}

.btn:not(:disabled):active {
  transform: scale(0.97);
}

.btn:focus-visible {
  outline: 2px solid var(--color-neon, #38bdf8);
  outline-offset: 2px;
}

.btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.btn-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ========== Markdown 样式 ========== */
.markdown-content {
  line-height: 1.8;
  font-size: 0.92rem;
}

.markdown-content h1, .markdown-content h2, .markdown-content h3, .markdown-content h4 {
  margin: 0.8rem 0 0.5rem 0;
  color: var(--color-neon-bright, #7dd3fc);
  font-weight: 600;
}

.markdown-content h1 { font-size: 1.2rem; }
.markdown-content h2 { font-size: 1.1rem; }
.markdown-content h3 { font-size: 1rem; }
.markdown-content h4 { font-size: 0.95rem; }

.markdown-content p {
  margin: 0.5rem 0;
}

.markdown-content ul, .markdown-content ol {
  margin: 0.5rem 0;
  padding-left: 1.5rem;
}

.markdown-content li {
  margin: 0.25rem 0;
  line-height: 1.7;
}

.markdown-content li strong {
  color: var(--color-neon-bright, #7dd3fc);
}

.markdown-content strong {
  color: var(--color-neon-bright, #7dd3fc);
  font-weight: 600;
}

.markdown-content code {
  background: rgba(56, 189, 248, 0.1);
  padding: 0.15rem 0.3rem;
  border-radius: 4px;
  font-family: ui-monospace, 'SF Mono', 'Cascadia Code', monospace;
  font-size: 0.88rem;
}

.markdown-content pre {
  background: rgba(3, 8, 16, 0.9);
  border: 1px solid rgba(56, 189, 248, 0.1);
  border-radius: 8px;
  padding: 0.75rem;
  margin: 0.5rem 0;
  overflow-x: auto;
}

.markdown-content pre code {
  background: transparent;
  padding: 0;
  font-size: 0.85rem;
  color: rgba(234, 246, 255, 0.85);
}

/* 链接样式 */
.markdown-content a {
  color: var(--color-neon, #38bdf8);
  text-decoration: none;
  border-bottom: 1px dashed rgba(56, 189, 248, 0.3);
  transition: all 0.2s;
}

.markdown-content a:hover {
  color: #7dd3fc;
  border-bottom-style: solid;
}

/* 图片样式 */
.markdown-content img {
  max-width: 100%;
  border-radius: 12px;
  margin: 0.5rem 0;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
  transition: transform 0.2s;
  cursor: pointer;
}

.markdown-content img:hover {
  transform: scale(1.02);
}

.markdown-content blockquote {
  border-left: 3px solid var(--color-neon, #38bdf8);
  margin: 0.8rem 0;
  padding: 0.6rem 1rem;
  background: rgba(56, 189, 248, 0.05);
  border-radius: 0 8px 8px 0;
  color: rgba(234, 246, 255, 0.8);
}

/* 分割线 */
.markdown-content hr {
  border: none;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(56, 189, 248, 0.3), transparent);
  margin: 1rem 0;
}

/* 表格样式 */
.markdown-content table {
  border-collapse: collapse;
  width: 100%;
  margin: 0.5rem 0;
  font-size: 0.88rem;
}

.markdown-content th, .markdown-content td {
  border: 1px solid rgba(56, 189, 248, 0.2);
  padding: 0.4rem 0.6rem;
  text-align: left;
}

.markdown-content th {
  background: rgba(56, 189, 248, 0.1);
  font-weight: 600;
  color: var(--color-neon-bright, #7dd3fc);
}

/* 行内代码 */
.markdown-content code {
  background: rgba(56, 189, 248, 0.1);
  padding: 0.15rem 0.4rem;
  border-radius: 4px;
  font-family: ui-monospace, 'SF Mono', 'Cascadia Code', monospace;
  font-size: 0.88rem;
  color: #a5f3fc;
}

/* ========== Step N: 标签美化 ========== */
.step-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.5rem;
  padding: 0.25rem 0.6rem;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--color-neon-bright, #7dd3fc);
  background: linear-gradient(135deg, rgba(56, 189, 248, 0.12) 0%, rgba(167, 139, 250, 0.12) 100%);
  border: 1px solid rgba(56, 189, 248, 0.2);
  border-radius: 16px;
  letter-spacing: 0.02em;
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.08);
}

.step-badge::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-neon, #38bdf8);
  box-shadow: 0 0 8px rgba(56, 189, 248, 0.6);
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 0.5; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

.step-content {
  line-height: 1.7;
}

/* ========== 图片预览模态框样式 ========== */
.image-preview-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  cursor: pointer;
  animation: fadeIn 0.2s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.image-preview-content {
  max-width: 90%;
  max-height: 90%;
  text-align: center;
}

.preview-image {
  max-width: 100%;
  max-height: 80vh;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
}

.preview-actions {
  margin-top: 1rem;
  display: flex;
  gap: 1rem;
  justify-content: center;
}

.preview-btn {
  padding: 0.5rem 1.5rem;
  border: none;
  border-radius: 8px;
  background: rgba(56, 189, 248, 0.2);
  color: var(--color-neon, #38bdf8);
  cursor: pointer;
  transition: all 0.2s;
}

.preview-btn:hover {
  background: rgba(56, 189, 248, 0.3);
}
</style>
