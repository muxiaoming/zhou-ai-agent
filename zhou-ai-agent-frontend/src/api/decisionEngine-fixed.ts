// 修复 405 问题：使用 POST + RequestBody 方式

// 1. 后端保持 @PostMapping（原始方式）
// 2. 前端使用 axios 发送 POST 请求
// 3. 前端使用 fetch 或 ReadableStream 读取 SSE

import axios from 'axios'

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

// 投资决策函数（同步）
export async function decideInvestment(params: DecisionRequest) {
  return http.post('/investment/decide', params)
}

// 流式投资决策（使用 fetch + ReadableStream）
export async function* decideInvestmentStream(params: DecisionRequest) {
  const url = new URL('/engine/agent/decide/stream', window.location.origin)

  const response = await fetch(url.toString(), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
    },
    body: JSON.stringify(params),
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 按 \n\n 分割 SSE 事件
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data === '[DONE]') return

          try {
            const event = JSON.parse(data)
            yield event
          } catch (e) {
            console.error('Failed to parse event:', data, e)
          }
        }
      }
    }
  } finally {
    reader.releaseLock()
  }
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
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
