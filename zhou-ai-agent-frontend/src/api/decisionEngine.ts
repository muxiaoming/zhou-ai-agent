import axios from 'axios'

// 创建 agent-decision-engine 的专用 axios 实例
// 开发环境通过 Vite 代理，生产环境直连后端
const ENGINE_API_BASE = '/engine'

export const http = axios.create({
  baseURL: ENGINE_API_BASE,
  timeout: 0,
})

// 请求/响应类型
export interface DecisionRequest {
  message: string
  modelName?: string
  threadId?: string
  enableRAG?: boolean
  enableTools?: boolean
  enableGraph?: boolean
}

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

export interface WorkflowStep {
  name: string
  status: string
  durationMs: number
}

export interface WorkflowResult {
  status: string
  data: any
  steps: WorkflowStep[]
}

export interface DecisionResponse {
  status: 'success' | 'partial' | 'failed'
  threadId: string
  steps: DecisionStep[]
  finalAdvice: string
  riskWarning: string
  durationMs: number
  tokenUsage: TokenUsage
  model: string
  workflow?: WorkflowResult
  error?: string
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

export interface SkillInfo {
  registryType: string
  skillCount: number
  skills: Record<string, string>
  explanation: string
}

// API 调用函数
export async function decideInvestment(params: DecisionRequest): Promise<DecisionResponse> {
  const response = await http.post<DecisionResponse>('/investment/decide', params)
  return response.data
}

export function* decideInvestmentStream(params: DecisionRequest): AsyncGenerator<StepEvent> {
  const url = new URL('/engine/agent/decide/stream', window.location.origin)
  url.searchParams.append('message', params.message)
  if (params.modelName) url.searchParams.append('modelName', params.modelName)
  if (params.threadId) url.searchParams.append('threadId', params.threadId)

  const eventSource = new EventSource(url.toString())

  return {
    [Symbol.asyncIterator]() {
      const queue: StepEvent[] = []
      let resolve: ((value: IteratorResult<StepEvent>) => void) | null = null
      let done = false

      eventSource.onmessage = (event) => {
        const data: StepEvent = JSON.parse(event.data)
        if (resolve) {
          resolve({ value: data, done: false })
          resolve = null
        } else {
          queue.push(data)
        }

        if (data.type === 'decision_complete' || data.type === 'error') {
          done = true
          eventSource.close()
        }
      }

      eventSource.onerror = () => {
        const errorEvent: StepEvent = { type: 'error', error: '连接失败' }
        if (resolve) {
          resolve({ value: errorEvent, done: false })
          resolve = null
        } else {
          queue.push(errorEvent)
        }
        done = true
        eventSource.close()
      }

      return {
        next(): Promise<IteratorResult<StepEvent>> {
          if (queue.length > 0) {
            return Promise.resolve({ value: queue.shift()!, done: false })
          }
          if (done) {
            return Promise.resolve({ value: undefined as any, done: true })
          }
          return new Promise((r) => {
            resolve = r
          })
        },
        return() {
          eventSource.close()
          return Promise.resolve({ value: undefined, done: true })
        }
      }
    }
  }
}

export async function listSkills(): Promise<SkillInfo> {
  const response = await http.get<SkillInfo>('/skills')
  return response.data
}

export async function chatWithSkill(params: {
  message: string
  threadId?: string
}): Promise<{ reply: string; threadId: string; durationMs: number }> {
  const response = await http.post('/skills/chat', params)
  return response.data
}

export async function askKnowledgeBase(params: {
  message: string
  modelName?: string
}): Promise<{ content: string; model: string; sources: string[] }> {
  const response = await http.post('/rag/ask', params)
  return response.data
}

export async function ingestDocument(file: File): Promise<{
  status: string
  documentId: string
  chunks: number
  message: string
}> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await http.post('/rag/ingest', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data
}
