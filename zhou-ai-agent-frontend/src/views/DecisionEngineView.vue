<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { RouterLink } from 'vue-router'
import { streamDecideInvestment, type DecisionStep, type DecisionRequest } from '../api/decisionEngine-sync'
import { marked } from 'marked'

// 消息类型
interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  steps?: DecisionStep[]
  riskWarning?: string
  durationMs?: number
  tokenUsage?: {
    promptTokens: number
    completionTokens: number
    totalTokens: number
  }
  model?: string
  currentStep?: string | null  // 当前正在执行的步骤名
  streaming?: boolean
}

const messages = ref<Message[]>([])
const input = ref('')
const error = ref<string | null>(null)
const processing = ref(false)
const showConfig = ref(false)  // 配置面板默认显示

// 配置参数
const modelName = ref('openAiChatModel')
const enableRAG = ref(true)
const enableTools = ref(true)
const enableGraph = ref(true)

// 可用模型列表
const availableModels = [
  { value: 'openAiChatModel', label: 'Agnes 模型 (推荐)' },
  { value: 'deepSeekChatModel', label: 'DeepSeek Chat' },
  { value: 'dashscopeChatModel', label: '通义千问' },
]

// 决策引擎状态
const decisionSteps = ref<DecisionStep[]>([])
const decisionFinalAdvice = ref('')
const decisionRiskWarning = ref('')
const decisionDurationMs = ref(0)
const decisionCurrentStep = ref<string | null>(null)

// Markdown 渲染
const renderer = new marked.Renderer()
marked.setOptions({ breaks: true, gfm: true })

function renderMarkdown(content: string) {
  if (!content) return ''
  return marked.parse(content, { renderer }) as string
}

// 新建对话
onMounted(async () => {
  // TODO: 加载可用技能列表
})

// 发送消息（流式打字机效果）
async function onSubmit() {
  const message = input.value.trim()
  if (!message || processing.value) return

  error.value = null
  const userMsg: Message = {
    id: `user-${Date.now()}`,
    role: 'user',
    content: message,
    timestamp: Date.now(),
  }
  messages.value = [...messages.value, userMsg]
  input.value = ''

  const assistantMsg: Message = {
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
    steps: [],
    currentStep: null,
    streaming: true,
  }
  messages.value = [...messages.value, assistantMsg]

  // 重置决策状态
  decisionSteps.value = []
  decisionFinalAdvice.value = ''
  decisionRiskWarning.value = ''
  decisionDurationMs.value = 0
  decisionCurrentStep.value = null

  const abort = new AbortController()
  processing.value = true

  try {
    const params: DecisionRequest = {
      message,
      modelName: modelName.value,
      enableRAG: enableRAG.value,
      enableTools: enableTools.value,
      enableGraph: enableGraph.value,
    }

    console.log('Decision params:', params)

    await streamDecideInvestment(
      params,
      (type, data) => {
        console.log(`Event [${type}]:`, data)
        const lastIndex = messages.value.length - 1
        const lastMsg = messages.value[lastIndex]
        if (lastMsg.role !== 'assistant') return

        switch (type) {
          case 'step_chunk':
            // 打字机效果：逐块追加到当前步骤的 result
            if (data.step != null && data.name && data.result) {
              decisionCurrentStep.value = data.name
              lastMsg.currentStep = decisionCurrentStep.value
              // 查找是否已有该步骤
              const existingIdx = decisionSteps.value.findIndex(s => s.step === data.step)
              if (existingIdx >= 0) {
                // 追加内容
                const updated = [...decisionSteps.value]
                updated[existingIdx] = { ...updated[existingIdx], result: updated[existingIdx].result + data.result }
                decisionSteps.value = updated
              } else {
                // 首次接收该步骤的 chunk，创建新条目
                decisionSteps.value.push({
                  step: data.step,
                  name: data.name,
                  skill: data.skill || '',
                  status: 'completed',
                  result: data.result,
                })
              }
              // 实时更新气泡内容
              decisionFinalAdvice.value = decisionSteps.value
                .filter(s => s.status === 'completed')
                .map(s => `**步骤 ${s.step}：${s.name}**\n\n${s.result}`)
                .join('\n\n')
              lastMsg.content = decisionFinalAdvice.value
              messages.value = [...messages.value]
            }
            break

          case 'step_start':
            decisionCurrentStep.value = data.name || null
            lastMsg.currentStep = decisionCurrentStep.value
            messages.value = [...messages.value]
            break

          case 'step_complete':
            if (data.step != null && data.name && data.result) {
              // 如果 step_chunk 已经创建了该步骤条目，则替换为完整结果；否则新建
              const existingIdx = decisionSteps.value.findIndex(s => s.step === data.step)
              if (existingIdx >= 0) {
                const updated = [...decisionSteps.value]
                updated[existingIdx] = { ...updated[existingIdx], result: data.result }
                decisionSteps.value = updated
              } else {
                decisionSteps.value.push({
                  step: data.step,
                  name: data.name,
                  skill: data.skill || '',
                  status: 'completed',
                  result: data.result,
                })
              }
              // 累积内容到消息气泡，为每个步骤加上标题便于阅读
              decisionFinalAdvice.value = decisionSteps.value
                .filter(s => s.status === 'completed')
                .map(s => `**步骤 ${s.step}：${s.name}**\n\n${s.result}`)
                .join('\n\n')
              lastMsg.content = decisionFinalAdvice.value
              lastMsg.steps = [...decisionSteps.value]
              lastMsg.currentStep = null
              messages.value = [...messages.value]
            }
            break

          case 'step_error':
            if (data.step != null && data.name) {
              decisionSteps.value.push({
                step: data.step,
                name: data.name,
                skill: data.skill || '',
                status: 'failed',
                result: '',
              })
            }
            if (data.error) {
              console.error(`Step ${data.step} (${data.name}) failed:`, data.error)
            }
            break

          case 'decision_complete':
            decisionRiskWarning.value = data.riskWarning || ''
            if (data.durationMs != null) decisionDurationMs.value = data.durationMs
            // 流式过程中已有步骤内容就用累积的，没有就用 finalAdvice
            if (data.finalAdvice && !lastMsg.content) {
              lastMsg.content = data.finalAdvice
            }
            lastMsg.steps = [...decisionSteps.value]
            lastMsg.riskWarning = decisionRiskWarning.value
            lastMsg.durationMs = decisionDurationMs.value
            lastMsg.streaming = false
            lastMsg.currentStep = null
            // 非投资消息短路时没有 riskWarning，清空
            if (!data.riskWarning) {
              lastMsg.riskWarning = undefined
            }
            messages.value = [...messages.value]
            break
        }

        nextTick(() => {
          const container = document.querySelector('.messages-container')
          if (container) container.scrollTop = container.scrollHeight
        })
      },
      abort.signal,
    )
  } catch (err: any) {
    if (err.name === 'CanceledError' || err.name === 'AbortError') {
      console.log('Decision aborted')
    } else {
      console.error('Decision failed:', err)
      error.value = err.message || '请求失败，请稍后重试'
      const lastIndex = messages.value.length - 1
      const lastMsg = messages.value[lastIndex]
      if (lastMsg.role === 'assistant' && !lastMsg.content) {
        messages.value[lastIndex] = {
          ...lastMsg,
          content: `抱歉，处理请求时出错：${error.value}`,
        }
        messages.value = [...messages.value]
      }
    }
  } finally {
    processing.value = false
    await nextTick()
  }
}

// 新建对话
function handleNewChat() {
  messages.value = []
  decisionSteps.value = []
  decisionFinalAdvice.value = ''
  decisionRiskWarning.value = ''
  decisionDurationMs.value = 0
  decisionCurrentStep.value = null
  error.value = null
}

// 自动调整文本框高度
function autoResize(event: Event) {
  const textarea = event.target as HTMLTextAreaElement
  textarea.style.height = 'auto'
  textarea.style.height = Math.min(textarea.scrollHeight, 120) + 'px'
}
</script>

<template>
  <div class="decision-engine-page">
    <!-- 顶部导航 -->
    <header class="top">
      <RouterLink class="back" to="/">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
          <polyline points="15 18 9 12 15 6"/>
        </svg>
        <span>返回</span>
      </RouterLink>
      <div class="titles">
        <h1>投资决策引擎</h1>
        <p class="sub">基于 Spring AI + Langfuse 的智能投资代理</p>
      </div>
      <button class="new-chat-btn" @click="handleNewChat">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        新建
      </button>
    </header>

    <!-- 消息列表 -->
    <div class="messages-container">
      <TransitionGroup name="msg">
        <div
          v-for="m in messages"
          :key="m.id"
          :class="['msg', m.role === 'user' ? 'msg-user' : 'msg-assistant']"
        >
          <div class="bubble">
            <template v-if="m.role === 'assistant'">
              <!-- 流式阶段：显示当前步骤标签 -->
              <div v-if="m.streaming && m.currentStep" class="step-badge step-badge-streaming">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="step-spinner">
                  <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
                </svg>
                {{ m.currentStep }}
              </div>
              <div class="markdown-content" v-html="renderMarkdown(m.content)"></div>
              <!-- 决策完成后的元数据：风险提示、耗时、Token 用量 -->
              <div v-if="!m.streaming && m.riskWarning" class="decision-meta">
                <div class="risk-warning-box">
                  <div class="risk-warning-title">风险提示</div>
                  <div class="markdown-content" v-html="renderMarkdown(m.riskWarning)"></div>
                </div>
                <div class="meta-stats">
                  <span v-if="m.durationMs" class="meta-tag">耗时: {{ (m.durationMs / 1000).toFixed(1) }}s</span>
                  <span v-if="m.tokenUsage?.totalTokens" class="meta-tag">Token: {{ m.tokenUsage.totalTokens }}</span>
                  <span v-if="m.model" class="meta-tag">模型: {{ m.model }}</span>
                </div>
              </div>
            </template>
            <span v-else class="bubble-text">{{ m.content }}</span>
            <span v-if="m.streaming" class="cursor" aria-hidden="true">▍</span>
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

    <!-- 错误提示 -->
    <p v-if="error" class="error-msg" role="alert">{{ error }}</p>

    <!-- 配置面板（输入框上方） -->
    <div class="config-panel">
      <button class="config-toggle" @click="showConfig = !showConfig">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="14" height="14">
          <circle cx="12" cy="12" r="3"/>
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
        </svg>
        <span>配置</span>
        <svg :class="['arrow', { 'arrow-open': showConfig }]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="12" height="12">
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </button>

      <Transition name="config-slide">
        <div v-if="showConfig" class="config-content">
          <div class="config-row">
            <!-- 模型选择 -->
            <div class="config-item">
              <label class="config-label">AI 模型</label>
              <select v-model="modelName" class="config-select">
                <option v-for="model in availableModels" :key="model.value" :value="model.value">
                  {{ model.label }}
                </option>
              </select>
            </div>

          </div>

          <!-- 配置预览 -->
          <div class="config-preview">
            <span class="preview-tag">模型: {{ modelName }}</span>

          </div>
        </div>
      </Transition>
    </div>

    <!-- 输入框 -->
    <form class="input-area" @submit.prevent="onSubmit">
      <textarea
        v-model="input"
        class="input-field"
        placeholder="输入消息，如：我有十万块想投资A股，Enter 发送，Shift+Enter 换行"
        :disabled="processing"
        autocomplete="off"
        rows="1"
        @keydown.enter.exact.prevent="onSubmit"
        @input="autoResize"
      ></textarea>
      <button type="submit" class="send-btn" :disabled="processing || !input.trim()">
        <svg v-if="processing" class="spinner" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13"/>
          <polygon points="22 2 15 22 11 13 2 9 22 2"/>
        </svg>
      </button>
    </form>
  </div>
</template>

<style scoped>
.decision-engine-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 900px;
  margin: 0 auto;
  background: var(--bg-main);
  position: relative;
}

.top {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--bg-panel);
  border-bottom: 1px solid var(--border);
}

.back {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-dim);
  text-decoration: none;
  font-size: 0.9rem;
  transition: color var(--transition-fast);
}

.back:hover {
  color: var(--neon-blue);
}

.titles {
  flex: 1;
  min-width: 0;
}

.titles h1 {
  margin: 0;
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text);
}

.titles .sub {
  margin: 0;
  font-size: 0.75rem;
  color: var(--text-dim);
}

.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-dim);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.new-chat-btn:hover {
  border-color: var(--neon-blue);
  color: var(--neon-blue);
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.msg {
  display: flex;
  gap: 12px;
  max-width: 85%;
}

.msg-user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.msg-assistant {
  align-self: flex-start;
}

.bubble {
  padding: 12px 16px;
  border-radius: var(--radius-lg);
  line-height: 1.6;
  font-size: 0.95rem;
}

.msg-user .bubble {
  background: linear-gradient(135deg, var(--neon-blue), #818cf8);
  color: white;
  border-bottom-right-radius: 4px;
}

.msg-assistant .bubble {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-bottom-left-radius: 4px;
}

.avatar {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border: 1px solid var(--border);
  color: var(--text-dim);
}

.avatar svg {
  width: 18px;
  height: 18px;
}

.step-badge {
  display: inline-block;
  padding: 4px 10px;
  background: var(--neon-blue);
  color: white;
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
  font-weight: 600;
  margin-bottom: 8px;
}

.step-badge-streaming {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: rgba(56, 189, 248, 0.15);
  color: var(--neon-blue);
  border: 1px solid rgba(56, 189, 248, 0.3);
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
  font-weight: 600;
  margin-bottom: 8px;
}

.step-spinner {
  width: 14px;
  height: 14px;
  animation: spin 1s linear infinite;
}

.error-msg {
  padding: 12px 16px;
  margin: 0 16px;
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(239, 68, 68, 0.3);
  border-radius: var(--radius-md);
  color: #ef4444;
  font-size: 0.9rem;
}

.config-panel {
  border-top: 1px solid var(--border);
  background: var(--bg-panel);
}

.config-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 12px 16px;
  background: transparent;
  border: none;
  color: var(--text-dim);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.config-toggle:hover {
  color: var(--neon-blue);
  background: rgba(56, 189, 248, 0.05);
}

.arrow {
  margin-left: auto;
  transition: transform var(--transition-fast);
}

.arrow-open {
  transform: rotate(180deg);
}

.config-content {
  padding: 0 16px 16px;
  border-top: 1px solid var(--border);
}

.config-row {
  display: flex;
  gap: 24px;
  padding-top: 12px;
}

.config-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-dim);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.config-select {
  padding: 8px 12px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text);
  font-size: 0.9rem;
  cursor: pointer;
  transition: border-color var(--transition-fast);
}

.config-select:hover {
  border-color: var(--neon-blue);
}

.config-select:focus {
  outline: none;
  border-color: var(--neon-blue);
  box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.2);
}

.config-toggles {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.config-toggle-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-dim);
  cursor: pointer;
  font-size: 0.85rem;
}

.config-toggle-item input[type="checkbox"] {
  accent-color: var(--neon-blue);
  width: 14px;
  height: 14px;
}

.config-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  margin-top: 12px;
  background: rgba(56, 189, 248, 0.05);
  border: 1px solid rgba(56, 189, 248, 0.1);
  border-radius: var(--radius-sm);
}

.preview-tag {
  padding: 3px 8px;
  background: rgba(56, 189, 248, 0.1);
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  color: var(--neon-blue);
}

.input-area {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 16px;
  background: var(--bg-panel);
  border-top: 1px solid var(--border);
}

.input-field {
  flex: 1;
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  color: var(--text);
  font-size: 0.95rem;
  resize: none;
  min-height: 48px;
  max-height: 120px;
  transition: border-color var(--transition-fast);
}

.input-field:focus {
  outline: none;
  border-color: var(--neon-blue);
  box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.2);
}

.input-field::placeholder {
  color: var(--text-dim);
}

.send-btn {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--neon-blue), #818cf8);
  border: none;
  border-radius: 50%;
  color: white;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.05);
  box-shadow: 0 0 20px rgba(56, 189, 248, 0.4);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.send-btn svg {
  width: 20px;
  height: 20px;
}

.spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.msg-enter-active {
  animation: msgIn 0.3s ease;
}

@keyframes msgIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.config-slide-enter-active,
.config-slide-leave-active {
  transition: all 0.2s ease;
  overflow: hidden;
}

.config-slide-enter-from,
.config-slide-leave-to {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.config-slide-enter-to,
.config-slide-leave-from {
  max-height: 300px;
  opacity: 1;
}

.markdown-content :deep(p) {
  margin: 0 0 8px;
}

.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.markdown-content :deep(code) {
  background: rgba(56, 189, 248, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.9em;
}

.markdown-content :deep(pre) {
  background: rgba(0, 0, 0, 0.3);
  padding: 12px;
  border-radius: var(--radius-sm);
  overflow-x: auto;
  margin: 8px 0;
}

.markdown-content :deep(pre code) {
  background: transparent;
  padding: 0;
}

.cursor {
  color: var(--neon-blue);
  animation: blink 1s infinite;
}

/* 决策元数据 */
.decision-meta {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border);
}

.risk-warning-box {
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  margin-bottom: 10px;
}

.risk-warning-title {
  font-size: 0.8rem;
  font-weight: 700;
  color: #ef4444;
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.risk-warning-box .markdown-content {
  font-size: 0.85rem;
  color: var(--text-dim);
}

.meta-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-tag {
  padding: 3px 10px;
  background: rgba(56, 189, 248, 0.1);
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  color: var(--neon-blue);
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
