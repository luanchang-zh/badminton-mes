export interface KnowledgeNode { id: string; label: string; type: string }
export interface KnowledgeEdge { source: string; target: string; description: string; weight: number | null }
export interface KnowledgeGraph { nodes: KnowledgeNode[]; edges: KnowledgeEdge[]; truncated: boolean }

/** Deduplicate upstream nodes and discard edges with missing endpoints. */
export function normalizeGraph(graph: KnowledgeGraph): KnowledgeGraph {
  const nodes = [...new Map((graph.nodes || []).filter(node => node.id).map(node => [node.id, node])).values()]
  const ids = new Set(nodes.map(node => node.id))
  const edges = (graph.edges || []).filter(edge => ids.has(edge.source) && ids.has(edge.target))
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

export function validateKnowledgeFile(name: string, size: number): string {
  if (!/\.(txt|md|markdown|csv|json|log)$/i.test(name)) return '支持 TXT、Markdown、CSV、JSON 和 LOG 文件'
  if (!size) return '不能上传空文件'
  if (size > 20 * 1024 * 1024) return '单个文件不能超过 20 MiB'
  return ''
}

export const escapeGraphText = (text: string) => String(text ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]!))
