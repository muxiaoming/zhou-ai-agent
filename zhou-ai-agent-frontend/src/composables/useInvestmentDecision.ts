import { ref, shallowRef } from 'vue'
import axios from 'axios'

// 创建 agent-decision-engine 的专用 axios 实例
const ENGINE_API_BASE = '/engine'
const http = axios.create({
  baseURL: ENGINE_API_BASE,
  timeout: 0,
})

export interface DecisionStep {
  step: number
  name: string
  skill: string
  status: 'completed' | 'failed' | 'skipped'
  result: string
}

export interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

export interface StepEvent {
  type: 'step_start' | 'step_complete' | 'step_error' | 'decision_complete' | 'error'
  step?: number
  name?: string
  skill?: string
  result?: string
  error?: string
  finalAdvice?: string
  riskWarning?: string
  durationMs?: number
  tokenUsage?: TokenUsage
  model?: string
}

export interface DecisionRequest {
  message: string
  modelName?: string
  threadId?: string
  enableRAG?: boolean
  enableTools?: boolean
  enableGraph?: boolean
}

export function useInvestmentDecision() {
  const steps = ref<DecisionStep[]>([])
  const finalAdvice = ref('')
  const riskWarning = ref('')
  const durationMs = ref(0)
  const tokenUsage = ref<TokenUsage | null>(null)
  const model = ref('')
  const currentStep = ref<string | null>(null)
  const processing = ref(false)
  const error = ref<string | null>(null)

  let eventSource: EventSource | null = null

  function reset() {
    steps.value = []
    finalAdvice.value = ''
    riskWarning.value = ''
    durationMs.value = 0
    tokenUsage.value = null
    model.value = ''
    currentStep.value = null
    error.value = null
  }

  function cancel() {
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
    processing.value = false
    currentStep.value = null
  }

  async function startDecision(params: DecisionRequest): Promise<void> {
    reset()
    processing.value = true

    // 使用流式接口 /engine/investment/decide/stream (GET 请求)
    const url = new URL('/engine/investment/decide/stream', window.location.origin)
    url.searchParams.append('message', params.message)

    // 可选参数：有值就传递，没有就使用后端默认值
    if (params.modelName) url.searchParams.append('modelName', params.modelName)
    if (params.threadId) url.searchParams.append('threadId', params.threadId)

    // 配置开关参数
    url.searchParams.append('enableRAG', String(params.enableRAG ?? true))
    url.searchParams.append('enableTools', String(params.enableTools ?? true))
    url.searchParams.append('enableGraph', String(params.enableGraph ?? true))

    console.log('Starting decision stream:', url.toString())
    console.log('Params:', {
      message: params.message,
      modelName: params.modelName,
      enableRAG: params.enableRAG,
      enableTools: params.enableTools,
      enableGraph: params.enableGraph,
    })

    return new Promise((resolve, reject) => {
      eventSource = new EventSource(url.toString())

      eventSource.onopen = () => {
        console.log('SSE connection opened')
      }

      eventSource.onmessage = (event) => {
        try {
          console.log('SSE event received:', event.data)
          const data: StepEvent = JSON.parse(event.data)
          handleEvent(data)

          if (data.type === 'decision_complete' || data.type === 'error') {
            console.log('Decision completed:', data.type)
            eventSource?.close()
            eventSource = null
            processing.value = false
            resolve()
          }
        } catch (err) {
          console.error('Failed to parse event:', err)
        }
      }

      eventSource.onerror = (err) => {
        console.error('SSE connection error:', err)
        error.value = '连接失败，请稍后重试'
        eventSource?.close()
        eventSource = null
        processing.value = false
        reject(new Error('连接失败'))
      }
    })
  }

  function handleEvent(data: StepEvent) {
    switch (data.type) {
      case 'step_start':
        currentStep.value = data.name || `Step ${data.step}`
        steps.value = [
          ...steps.value,
          {
            step: data.step || steps.value.length + 1,
            name: data.name || `步骤 ${steps.value.length + 1}`,
            skill: data.skill || '',
            status: 'completed',
            result: '',
          },
        ]
        break

      case 'step_complete':
        if (data.step && data.step > 0) {
          const idx = steps.value.findIndex((s) => s.step === data.step)
          if (idx >= 0) {
            steps.value = [
              ...steps.value.slice(0, idx),
              {
                ...steps.value[idx],
                status: 'completed',
                result: data.result || '',
              },
              ...steps.value.slice(idx + 1),
            ]
          }
        }
        currentStep.value = null
        break

      case 'step_error':
        if (data.step && data.step > 0) {
          const idx = steps.value.findIndex((s) => s.step === data.step)
          if (idx >= 0) {
            steps.value = [
              ...steps.value.slice(0, idx),
              {
                ...steps.value[idx],
                status: 'failed',
                result: data.error || '步骤执行失败',
              },
              ...steps.value.slice(idx + 1),
            ]
          }
        }
        currentStep.value = null
        break

      case 'decision_complete':
        finalAdvice.value = data.finalAdvice || ''
        riskWarning.value = data.riskWarning || ''
        durationMs.value = data.durationMs || 0
        tokenUsage.value = data.tokenUsage || null
        model.value = data.model || ''
        break

      case 'error':
        error.value = data.error || '未知错误'
        break
    }
  }

  return {
    steps,
    finalAdvice,
    riskWarning,
    durationMs,
    tokenUsage,
    model,
    currentStep,
    processing,
    error,
    startDecision,
    cancel,
    reset,
  }
}
