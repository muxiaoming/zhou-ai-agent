import { shallowRef } from 'vue'
import type { ChatMessage } from '../components/ChatPanel.vue'

/** Manus 等智能体输出中的步骤标记，如 `Step 1:`、`Step 2:` */
const STEP_HEAD = /^Step \d+:/m
const STEP_SPLIT = /(?=Step \d+:)/g

export function splitBySteps(text: string): string[] {
  if (!text) return []
  const first = text.search(STEP_HEAD)
  if (first === -1) return [text]
  if (first > 0) {
    const preamble = text.slice(0, first).trimEnd()
    const steps = text.slice(first).split(STEP_SPLIT)
      .filter((s) => s.length > 0)
    return preamble ? [preamble, ...steps] : steps
  }
  return text.split(STEP_SPLIT).filter((s) => s.length > 0)
}

/**
 * 将 SSE 流式文本按 Step 拆成多条 assistant 气泡（每个 Step 一条）。
 * Step N: 前缀会保留在内容中，由前端美化为标签样式。
 */
export function useStepStreamChat() {
  const messages = shallowRef<ChatMessage[]>([])
  let streamBuffer = ''
  let turnStepIds: string[] = []

  function pushMessage(m: ChatMessage) {
    messages.value = [...messages.value, m]
  }

  function resetTurn() {
    streamBuffer = ''
    turnStepIds = []
  }

  function syncStepsFromBuffer(streaming: boolean) {
    const parts = splitBySteps(streamBuffer)
    if (parts.length === 0) return

    const next = [...messages.value]
    const isLastStreaming = streaming

    for (let i = 0; i < parts.length; i++) {
      const content = parts[i]
      const isLast = i === parts.length - 1

      if (i < turnStepIds.length) {
        const idx = next.findIndex((m) => m.id === turnStepIds[i])
        if (idx !== -1) {
          next[idx] = {
            ...next[idx],
            content,
            streaming: isLastStreaming && isLast,
          }
        }
      } else {
        const id = crypto.randomUUID()
        turnStepIds.push(id)
        next.push({
          id,
          role: 'assistant',
          content,
          streaming: isLastStreaming && isLast,
        })
      }
    }

    messages.value = next
  }

  function beginTurn() {
    resetTurn()
  }

  function appendChunk(chunk: string) {
    streamBuffer += chunk
    syncStepsFromBuffer(true)
  }

  function endTurn() {
    syncStepsFromBuffer(false)
    resetTurn()
  }

  function cancelTurn() {
    if (turnStepIds.length === 0) {
      pushMessage({
        id: crypto.randomUUID(),
        role: 'assistant',
        content: '（已取消）',
        streaming: false,
      })
    } else {
      messages.value = messages.value.map((m) =>
        turnStepIds.includes(m.id) ? { ...m, streaming: false } : m,
      )
    }
    resetTurn()
  }

  return {
    messages,
    pushMessage,
    beginTurn,
    appendChunk,
    endTurn,
    cancelTurn,
  }
}
