export interface SseEvent { event: string; data: string }

/** POST SSE：支持跨字节 UTF-8、CR/LF/CRLF、注释与多行 data。 */
export async function readSse(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: SseEvent) => void,
  signal: AbortSignal,
) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = '', name = '', data: string[] = [], size = 0
  const dispatch = () => {
    if (data.length) onEvent({ event: name || 'message', data: data.join('\n') })
    name = ''; data = []; size = 0
  }
  const line = (value: string) => {
    if (!value) { dispatch(); return }
    if (value.startsWith(':')) return
    const colon = value.indexOf(':')
    const field = colon < 0 ? value : value.slice(0, colon)
    let content = colon < 0 ? '' : value.slice(colon + 1)
    if (content.startsWith(' ')) content = content.slice(1)
    if (field === 'event') name = content
    if (field === 'data') {
      size += content.length
      if (size > 2_000_000) throw new Error('回答片段过大，请重试')
      data.push(content)
    }
  }
  const consume = (final = false) => {
    let start = 0
    for (let i = 0; i < buffer.length; i++) {
      const char = buffer[i]
      if (char !== '\r' && char !== '\n') continue
      if (char === '\r' && i === buffer.length - 1 && !final) break
      line(buffer.slice(start, i))
      if (char === '\r' && buffer[i + 1] === '\n') i++
      start = i + 1
    }
    buffer = buffer.slice(start)
    if (buffer.length > 2_000_000) throw new Error('回答片段过大，请重试')
    // SSE 只有空行才提交事件，断线时的半条消息不视为完成。
  }
  const abort = () => { void reader.cancel().catch(() => {}) }
  signal.addEventListener('abort', abort, { once: true })
  try {
    signal.throwIfAborted()
    while (true) {
      const chunk = await reader.read()
      signal.throwIfAborted()
      if (chunk.done) { buffer += decoder.decode(); consume(true); break }
      buffer += decoder.decode(chunk.value, { stream: true })
      consume()
    }
  } finally {
    signal.removeEventListener('abort', abort)
    await reader.cancel().catch(() => {})
    reader.releaseLock()
  }
}
