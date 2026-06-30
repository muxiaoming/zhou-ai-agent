import axios from 'axios'
import { streamSsePost } from './sseStream'

// 创建 agent-decision-engine 的专用 axios 实例
const ENGINE_API_BASE = '/engine'
const http = axios.create({
  baseURL: ENGINE_API_BASE,
  timeout: 0,
})

// 参数对象类型
export interface DecisionRequest {
  message: string
  modelName?: string
  threadId?: string
  enableRAG?: boolean
  enableTools?: boolean
  enableGraph?: boolean
}

// 投资决策响应类型
export interface DecisionResponse {
  status: 'success' | 'partial' | 'failed'
  threadId: string
  steps: DecisionStep[]
  finalAdvice: string
  riskWarning: string
  durationMs: number
  tokenUsage: TokenUsage
  model: string
  error?: string
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

// 使用同步接口（简单可靠）
export async function decideInvestment(params: DecisionRequest): Promise<DecisionResponse> {
  console.log('Calling decideInvestment (sync):', params)
  const response = await http.post<DecisionResponse>('/investment/decide', params)
  console.log('Response received:', response.data)
  return response.data
}

// 流式投资决策（使用 fetch + ReadableStream POST SSE）
export async function streamDecideInvestment(
  params: DecisionRequest,
  onEvent: (type: string, data: any) => void,
  signal?: AbortSignal,
): Promise<void> {
  console.log('Calling decideInvestment (stream POST):', params)

  // 构建请求体，过滤 undefined 字段
  const body: Record<string, unknown> = {
    message: params.message,
    modelName: params.modelName,
    threadId: params.threadId,
    enableRAG: params.enableRAG ?? true,
    enableTools: params.enableTools ?? true,
    enableGraph: params.enableGraph ?? true,
  }

  await streamSsePost(
    '/engine/investment/decide/stream',
    body,
    (chunk) => {
      try {
        const data = JSON.parse(chunk)
        onEvent(data.type, data)
      } catch {
        console.warn('SSE chunk parse error:', chunk)
      }
    },
    { signal },
  )
}

// 其他 API 函数
export async function listSkills() {
  return http.get('/skills')
}

export async function chatWithSkill(params: { message: string; threadId?: string }) {
  return http.post('/skills/chat', params)
}

export async function askKnowledgeBase(params: { message: string; modelName?: string }) {
  return http.post('/rag/ask', params)
}

export async function ingestDocument(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/rag/ingest', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
