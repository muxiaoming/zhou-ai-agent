import { ref, shallowRef } from 'vue'
import { decideInvestmentStream, type DecisionRequest } from '../api/decisionEngine'

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

  let abortController: AbortController | null = null

  function handleEvent(event: StepEvent) {
    switch (event.type) {
      case 'step_start':
        currentStep.value = event.name || null
        steps.value = [...steps.value, {
          step: event.step || steps.value.length + 1,
          name: event.name || '',
          skill: event.skill || '',
          status: 'completed',
          result: ''
        }]
        break

      case 'step_complete':
        currentStep.value = null
        const lastStep = steps.value[steps.value.length - 1]
        if (lastStep) {
          lastStep.result = event.result || ''
          lastStep.status = 'completed'
        }
        steps.value = [...steps.value]
        break

      case 'step_error':
        currentStep.value = null
        const errorStep = steps.value[steps.value.length - 1]
        if (errorStep) {
          errorStep.status = 'failed'
          errorStep.result = event.error || ''
        }
        steps.value = [...steps.value]
        break

      case 'decision_complete':
        finalAdvice.value = event.finalAdvice || ''
        riskWarning.value = event.riskWarning || ''
        durationMs.value = event.durationMs || 0
        tokenUsage.value = event.tokenUsage || null
        model.value = event.model || ''
        break

      case 'error':
        error.value = event.error || '未知错误'
        break
    }
  }

  function reset() {
    steps.value = []
    finalAdvice.value = ''
    riskWarning.value = ''
    durationMs.value = 0
    tokenUsage.value = null
    model.value = ''
    currentStep.value = null
    error.value = null

    if (abortController) {
      abortController.abort()
      abortController = null
    }
  }

  function cancel() {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    processing.value = false
    currentStep.value = null
  }

  async function startDecision(params: DecisionRequest): Promise<void> {
    reset()
    processing.value = true

    try {
      console.log('Starting decision stream with POST:', params)

      // 使用 POST 请求 + ReadableStream
      for await (const event of decideInvestmentStream(params)) {
        handleEvent(event)

        if (event.type === 'decision_complete' || event.type === 'error') {
          processing.value = false
          return
        }
      }

      processing.value = false
    } catch (err: any) {
      console.error('Decision stream error:', err)
      error.value = err.message || '连接失败'
      processing.value = false
      throw err
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
