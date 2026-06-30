import { ref } from 'vue'
import { decideInvestment, type DecisionRequest, type DecisionResponse } from '../api/decisionEngine-sync'

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

  async function startDecision(params: DecisionRequest): Promise<void> {
    reset()
    processing.value = true

    try {
      console.log('Starting decision (sync):', params)

      // 使用同步接口，一次性获取完整结果
      const response: DecisionResponse = await decideInvestment(params)

      console.log('Decision response:', response)

      // 更新所有步骤
      steps.value = response.steps || []
      finalAdvice.value = response.finalAdvice || ''
      riskWarning.value = response.riskWarning || ''
      durationMs.value = response.durationMs || 0
      tokenUsage.value = response.tokenUsage || null
      model.value = response.model || ''

      console.log('Decision completed successfully')
      processing.value = false
    } catch (err: any) {
      console.error('Decision error:', err)
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
    reset,
  }
}
