import { http as defaultHttp } from './http'

// 使用命名导入的 http 实例（指向 localhost:8123/api）
const http = defaultHttp

/** 解析单个 SSE 事件块中的 data: 行（可多行） */
function extractDataFromSseEventBlock(block: string): string {
  const lines = block.split(/\r?\n/)
  const parts: string[] = []
  for (const line of lines) {
    if (line.startsWith('data:')) {
      parts.push(line.slice(5).trimStart())
    }
  }
  return parts.join('\n')
}

function consumeCompleteSseEvents(
  buffer: string,
  rawChunk: string,
): { pending: string; payloads: string[] } {
  let buf = buffer + rawChunk
  const payloads: string[] = []
  while (true) {
    let idx = buf.indexOf('\n\n')
    let sep = 2
    if (idx === -1) {
      idx = buf.indexOf('\r\n\r\n')
      sep = 4
    }
    if (idx === -1) break
    const block = buf.slice(0, idx)
    buf = buf.slice(idx + sep)
    payloads.push(extractDataFromSseEventBlock(block))
  }
  return { pending: buf, payloads }
}

/**
 * 使用 Axios（XHR）流式读取 GET SSE：通过 onDownloadProgress 增量解析 data: 事件。
 * 适用于 Spring text/event-stream / SseEmitter 格式。
 * 注意：XHR 方式浏览器可能缓冲，对实时性敏感的场景请使用 streamSsePost。
 */
export async function streamSseGet(
  path: string,
  params: Record<string, string>,
  onData: (text: string) => void,
  opts?: { signal?: AbortSignal },
): Promise<void> {
  let receivedLen = 0
  let pending = ''

  await http.get(path, {
    params,
    responseType: 'text',
    signal: opts?.signal,
    onDownloadProgress: (e) => {
      const xhr = e.event?.target as XMLHttpRequest | undefined
      if (!xhr) return
      const full = xhr.responseText ?? ''
      const addition = full.slice(receivedLen)
      receivedLen = full.length
      const { pending: next, payloads } = consumeCompleteSseEvents(pending, addition)
      pending = next
      for (const p of payloads) {
        if (!p || p === '[DONE]') continue
        onData(p)
      }
    },
  })

  // 处理尾部残余数据
  if (pending.trim()) {
    const tail = extractDataFromSseEventBlock(pending)
    if (tail && tail !== '[DONE]') onData(tail)
  }
}

/**
 * 使用 fetch + ReadableStream 流式读取 POST SSE。
 * 比 XHR onDownloadProgress 方式更可靠，浏览器不会缓冲，事件实时到达。
 * 用于投资决策引擎的流式接口（/engine/investment/decide/stream）。
 */
export async function streamSsePost(
  path: string,
  body: Record<string, unknown>,
  onData: (text: string) => void,
  opts?: { signal?: AbortSignal },
): Promise<void> {
  const url = new URL(path, window.location.origin)

  const response = await fetch(url.toString(), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(body),
    signal: opts?.signal,
  })

  if (!response.ok) {
    const errText = await response.text().catch(() => '')
    throw new Error(`SSE 连接失败: HTTP ${response.status}${errText ? ' - ' + errText : ''}`)
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let pending = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      const { pending: next, payloads } = consumeCompleteSseEvents(pending, chunk)
      pending = next
      for (const p of payloads) {
        if (!p || p === '[DONE]') continue
        onData(p)
      }
    }
  } finally {
    reader.releaseLock()
  }

  // 处理尾部残余数据
  if (pending.trim()) {
    const tail = extractDataFromSseEventBlock(pending)
    if (tail && tail !== '[DONE]') onData(tail)
  }
}
