import { http } from './http'

/** 解析单个 SSE 事件块中的 `data:` 行（可多行） */
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
 * 使用 Axios（XHR）流式读取 GET SSE：通过 `onDownloadProgress` 增量解析 `data:` 事件。
 * 与 Spring `text/event-stream` / `SseEmitter` 常见格式兼容。
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

  if (pending.trim()) {
    const tail = extractDataFromSseEventBlock(pending)
    if (tail && tail !== '[DONE]') onData(tail)
  }
}
