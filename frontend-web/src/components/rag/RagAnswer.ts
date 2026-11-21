import { defineComponent, h } from 'vue'
import type { PropType, VNodeChild } from 'vue'
import type { RagReference } from '@/api/rag'

type RenderInline = (text: string) => VNodeChild[]
type Block = { node: VNodeChild; next: number }
const heading = /^ {0,3}(#{1,6})[ \t]+(.+)$/
const fence = /^ {0,3}(`{3,}|~{3,})(.*)$/
const listItem = /^([ \t]*)([-+*]|\d+[.)])[ \t]+(.*)$/
const quote = /^ {0,3}>[ \t]?/

function tableCells(line: string): string[] {
  return line.trim().replace(/^\||(?<!\\)\|$/g, '').split(/(?<!\\)\|/).map(cell => cell.trim().replace(/\\\|/g, '|'))
}

function startsTable(lines: string[], index: number): boolean {
  if (!lines[index]?.includes('|') || !lines[index + 1]?.includes('|')) return false
  const header = tableCells(lines[index]!)
  const divider = tableCells(lines[index + 1]!)
  return header.length === divider.length && divider.every(cell => /^:?-{3,}:?$/.test(cell))
}

function renderTable(lines: string[], index: number, inline: RenderInline): Block {
  const header = tableCells(lines[index]!)
  const alignment = tableCells(lines[index + 1]!).map(cell => {
    if (cell.endsWith(':')) return cell.startsWith(':') ? 'center' : 'right'
    return undefined
  })
  const row = (cells: string[], tag: 'th' | 'td') => h('tr', header.map((_, column) =>
    h(tag, { style: { textAlign: alignment[column] } }, inline(cells[column] ?? ''))))
  const rows: VNodeChild[] = []
  let next = index + 2
  while (lines[next]?.includes('|') && !heading.test(lines[next]!) && !fence.test(lines[next]!)
    && !quote.test(lines[next]!) && !listItem.test(lines[next]!)) {
    rows.push(row(tableCells(lines[next++]!), 'td'))
  }
  return { node: h('div', { class: 'rag-table-scroll' }, [
    h('table', [h('thead', [row(header, 'th')]), h('tbody', rows)]),
  ]), next }
}

function renderFence(lines: string[], index: number, marker: string): Block {
  const code: string[] = []
  let next = index + 1
  while (next < lines.length) {
    const closing = /^[ \t]*(`+|~+)[ \t]*$/.exec(lines[next]!)?.[1]
    if (closing?.startsWith(marker)) break
    code.push(lines[next++]!)
  }
  return { node: h('pre', [h('code', code.join('\n'))]), next: next + 1 }
}

function renderList(lines: string[], index: number, first: RegExpExecArray, inline: RenderInline, depth: number): Block {
  const indent = first[1]!.length
  const ordered = /^\d/.test(first[2]!)
  const start = ordered ? Number.parseInt(first[2]!, 10) : undefined
  const items: VNodeChild[] = []
  let next = index
  while (next < lines.length) {
    const item = listItem.exec(lines[next]!)
    if (!item || item[1]!.length !== indent || /^\d/.test(item[2]!) !== ordered) break
    const body = [item[3]!]
    const bodyIndent = item[0].length - item[3]!.length
    next++
    while (next < lines.length) {
      let continuation = next
      while (continuation < lines.length && /^[ \t]*$/.test(lines[continuation]!)) continuation++
      const line = lines[continuation]
      if (!line || !/^[ \t]+\S/.test(line)) break
      const indentation = line.match(/^[ \t]+/)![0].length
      if (indentation <= indent) break
      // 列表内代码的空行属于列表项，后续缩进回落才结束该项。
      while (next < continuation) { body.push(''); next++ }
      body.push(line.slice(Math.min(indentation, bodyIndent)))
      next++
    }
    items.push(h('li', renderBlocks(body, inline, depth + 1)))
  }
  return { node: h(ordered ? 'ol' : 'ul', { start }, items), next }
}

function renderBlock(lines: string[], index: number, inline: RenderInline, depth: number): Block {
  const line = lines[index]!
  const opening = fence.exec(line)
  if (opening) return renderFence(lines, index, opening[1]!)
  const title = heading.exec(line)
  if (title) {
    const tag = 'h' + Math.min(title[1]!.length + 1, 6)
    return { node: h(tag, inline(title[2]!.replace(/\s+#+\s*$/, ''))), next: index + 1 }
  }
  if (quote.test(line)) {
    const quoted: string[] = []
    let next = index
    while (next < lines.length && quote.test(lines[next]!)) quoted.push(lines[next++]!.replace(quote, ''))
    return { node: h('blockquote', renderBlocks(quoted, inline, depth + 1)), next }
  }
  const item = listItem.exec(line)
  if (item) return renderList(lines, index, item, inline, depth)
  if (startsTable(lines, index)) return renderTable(lines, index, inline)

  const paragraph = [line]
  let next = index + 1
  while (next < lines.length) {
    const following = lines[next]!
    if (!following.trim() || heading.test(following) || fence.test(following)
      || quote.test(following) || listItem.test(following) || startsTable(lines, next)) break
    paragraph.push(lines[next++]!)
  }
  return { node: h('p', inline(paragraph.join('\n'))), next }
}

function renderBlocks(lines: string[], inline: RenderInline, depth = 0): VNodeChild[] {
  // 模型正文可能由连续嵌套符号构成，限制递归深度以保持页面可用。
  if (depth >= 12) return [h('p', inline(lines.join('\n')))]
  const nodes: VNodeChild[] = []
  let index = 0
  while (index < lines.length) {
    if (!lines[index]!.trim()) {
      index++
      continue
    }
    const block = renderBlock(lines, index, inline, depth)
    nodes.push(block.node)
    index = block.next
  }
  return nodes
}

/** 通过 Vue 文本节点呈现 Markdown 子集；模型输出不会作为 HTML 执行。 */
export default defineComponent({
  name: 'RagAnswer',
  props: { content: { type: String, required: true }, references: { type: Array as PropType<RagReference[]>, default: () => [] } },
  emits: { cite: (_id: string) => true },
  setup(props, { emit }) {
    const inline = (text: string): VNodeChild[] => {
      const nodes: VNodeChild[] = []
      const pattern = /(`[^`\n]+`|\*\*[^*\n]+\*\*|\*[^*\n]+\*|\[[^\]\n]+\]\([^\s)]+\)|\[\d+\])/g
      let cursor = 0
      for (const match of text.matchAll(pattern)) {
        nodes.push(text.slice(cursor, match.index))
        const token = match[0]
        if (token.startsWith('`')) nodes.push(h('code', token.slice(1, -1)))
        else if (token.startsWith('**')) nodes.push(h('strong', token.slice(2, -2)))
        else if (token.startsWith('*')) nodes.push(h('em', token.slice(1, -1)))
        else if (/^\[\d+\]$/.test(token)) {
          const id = token.slice(1, -1)
          nodes.push(props.references.some(r => r.referenceId === id)
            ? h('button', { type: 'button', class: 'rag-citation', 'aria-label': `查看来源 ${id}`, onClick: () => emit('cite', id) }, token)
            : token)
        } else {
          const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(token)!
          nodes.push(/^https?:\/\//i.test(link[2]!)
            ? h('a', { href: link[2], target: '_blank', rel: 'noopener noreferrer' }, link[1])
            : token)
        }
        cursor = match.index! + token.length
      }
      nodes.push(text.slice(cursor))
      return nodes
    }
    return () => h('div', { class: 'rag-answer' }, renderBlocks(
      props.content.replace(/\r\n?/g, '\n').split('\n'), inline,
    ))
  },
})
