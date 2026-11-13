import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
const require = createRequire(new URL('../frontend-web/package.json', import.meta.url))
const ts = require('typescript')
const source = await readFile(new URL('../frontend-web/src/utils/sse.ts', import.meta.url), 'utf8')
const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText
const { readSse } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`)
const bytes = new TextEncoder().encode('\ufeff: heartbeat\r\nevent: delta\r\ndata: 中文🙂\r\ndata: 第二行\r\n\r\nevent: done\ndata: {"status":"COMPLETED"}\n\nevent: ignored\ndata: half')
for (const size of [1, 2, 3, 7, 64, bytes.length]) {
  let index = 0
  const events = [], controller = new AbortController()
  await readSse(new ReadableStream({ pull(stream) { if (index === bytes.length) stream.close(); else { stream.enqueue(bytes.slice(index, index + size)); index = Math.min(index + size, bytes.length) } } }), event => events.push(event), controller.signal)
  assert.deepEqual(events, [{ event: 'delta', data: '中文🙂\n第二行' }, { event: 'done', data: '{"status":"COMPLETED"}' }])
}
const abort = new AbortController()
let cancelled = false
const running = readSse(new ReadableStream({ cancel() { cancelled = true } }), () => {}, abort.signal)
abort.abort()
await assert.rejects(running, { name: 'AbortError' })
assert.equal(cancelled, true)
await assert.rejects(readSse(new ReadableStream({ start(controller) { controller.enqueue(new TextEncoder().encode(`data: ${'a'.repeat(2_000_001)}\n\n`)); controller.close() } }), () => {}, new AbortController().signal), /片段过大/)
console.log('SSE: 8 checks passed (6 chunk sizes, abort, size limit)')
