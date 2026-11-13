import { defineComponent, h } from 'vue'
import type { PropType, VNodeChild } from 'vue'
import type { RagReference } from '@/api/rag'

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
    return () => {
      const lines = props.content.replace(/\r\n?/g, '\n').split('\n')
      const nodes: VNodeChild[] = []
      let i = 0
      while (i < lines.length) {
        const line = lines[i]!
        if (!line.trim()) { i++; continue }
        if (/^\s*```/.test(line)) {
          const code: string[] = []; i++
          while (i < lines.length && !/^\s*```/.test(lines[i]!)) code.push(lines[i++]!)
          i++; nodes.push(h('pre', [h('code', code.join('\n'))])); continue
        }
        const heading = /^#{1,6}\s+(.+)$/.exec(line)
        if (heading) { nodes.push(h('h4', inline(heading[1]!))); i++; continue }
        if (/^\s*>\s?/.test(line)) { nodes.push(h('blockquote', inline(line.replace(/^\s*>\s?/, '')))); i++; continue }
        if (/^\s*(?:[-*+] |\d+[.)] )\s*\S/.test(line)) {
          const ordered = /^\s*\d+[.)] /.test(line), items: VNodeChild[] = []
          const expression = ordered ? /^\s*\d+[.)]\s+(.+)$/ : /^\s*[-*+]\s+(.+)$/
          let item: RegExpExecArray | null
          while (i < lines.length && (item = expression.exec(lines[i]!))) { items.push(h('li', inline(item[1]!))); i++ }
          nodes.push(h(ordered ? 'ol' : 'ul', items)); continue
        }
        if (line.includes('|') && /^\s*\|?\s*:?-{3,}/.test(lines[i + 1] ?? '')) {
          const cells = (value: string) => value.replace(/^\s*\||\|\s*$/g, '').split('|').map(s => s.trim())
          const header = cells(line), rows: VNodeChild[] = []; i += 2
          while (i < lines.length && lines[i]!.includes('|') && lines[i]!.trim()) {
            rows.push(h('tr', cells(lines[i++]!).map(cell => h('td', inline(cell)))))
          }
          nodes.push(h('div', { class: 'rag-table-scroll' }, [h('table', [h('thead', [h('tr', header.map(cell => h('th', inline(cell))))]), h('tbody', rows)])])); continue
        }
        const paragraph = [line]; i++
        while (i < lines.length && lines[i]!.trim() && !/^\s*(?:```|#{1,6} |[-*+] |\d+[.)] |>)/.test(lines[i]!)) paragraph.push(lines[i++]!)
        nodes.push(h('p', inline(paragraph.join('\n'))))
      }
      return h('div', { class: 'rag-answer' }, nodes)
    }
  },
})
