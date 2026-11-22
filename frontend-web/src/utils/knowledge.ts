export interface KnowledgeNode { id: string; label: string; type: string }
export interface KnowledgeEdge { source: string; target: string; description: string; weight: number | null }
export interface KnowledgeGraph { nodes: KnowledgeNode[]; edges: KnowledgeEdge[]; truncated: boolean }

function formatRelationshipDescription(description: string | null | undefined): string {
  const descriptions = new Map<string, string>()
  for (const part of (description ?? '').split('<SEP>')) {
    const text = part.trim()
    const key = text.normalize().replace(/\s+/g, ' ').replace(/[。.!！?？]+$/u, '')
    if (key && !descriptions.has(key)) descriptions.set(key, text)
  }
  return [...descriptions.values()].join('\n')
}

/** Deduplicate upstream nodes and discard edges with missing endpoints. */
export function normalizeGraph(graph: KnowledgeGraph): KnowledgeGraph {
  const nodes = [...new Map((graph.nodes || []).filter(node => node.id).map(node => [node.id, node])).values()]
  const ids = new Set(nodes.map(node => node.id))
  const edges = (graph.edges || [])
    .filter(edge => ids.has(edge.source) && ids.has(edge.target))
    .map(edge => ({ ...edge, description: formatRelationshipDescription(edge.description) }))
  return { nodes: nodes.map(node => ({ ...node, type: node.type || '未分类' })), edges, truncated: Boolean(graph.truncated) }
}

/** Preserve neighbors so a search result remains understandable in its context. */
export function filterGraph(graph: KnowledgeGraph, query = '', type = '', focus = ''): KnowledgeGraph {
  let edges = graph.edges
  let nodes = graph.nodes
  if (focus) {
    edges = edges.filter(edge => edge.source === focus || edge.target === focus)
    const ids = new Set([focus, ...edges.flatMap(edge => [edge.source, edge.target])])
    nodes = nodes.filter(node => ids.has(node.id))
  }
  if (query.trim() || type) {
    const matches = new Set(nodes.filter(node => (!type || node.type === type) && node.label.toLowerCase().includes(query.trim().toLowerCase())).map(node => node.id))
    edges = edges.filter(edge => matches.has(edge.source) || matches.has(edge.target))
    const ids = new Set([...matches, ...edges.flatMap(edge => [edge.source, edge.target])])
    nodes = nodes.filter(node => ids.has(node.id))
  }
  return { nodes, edges, truncated: graph.truncated }
}

export const knowledgeFileExtensions = ['.txt', '.md', '.markdown', '.csv', '.json', '.log', '.pdf', '.docx', '.xlsx', '.pptx']
export const knowledgeFileAccept = knowledgeFileExtensions.join(',')
export const knowledgeFileFormats = 'UTF-8 文本、带文字层的 PDF、DOCX、XLSX、PPTX'

export function validateKnowledgeFile(name: string, size: number): string {
  const extension = name.slice(name.lastIndexOf('.')).toLowerCase()
  if (!knowledgeFileExtensions.includes(extension)) return '支持' + knowledgeFileFormats
  if (!size) return '不能上传空文件'
  if (size > 20 * 1024 * 1024) return '单个文件不能超过 20 MiB'
  return ''
}

export const escapeGraphText = (text: string) => String(text ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]!))
