import { del, download, get, post } from '@/utils/request'
import type { KnowledgeGraph } from '@/utils/knowledge'

export interface KnowledgeDocument {
  docId: string
  fileName: string
  status: 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'UNKNOWN'
  statusText: string
  rawStatus: string
}
export interface DocumentContent { docId: string; fileName: string; content: string }

export const listDocuments = () => get<KnowledgeDocument[]>('/rag/documents')
export const readDocument = (id: string) => get<DocumentContent>(`/rag/documents/${encodeURIComponent(id)}/text`)
export const downloadDocument = (doc: KnowledgeDocument) => download(`/rag/documents/${encodeURIComponent(doc.docId)}/content`, undefined, doc.fileName)
export const deleteDocument = (id: string) => del<void>(`/rag/documents/${encodeURIComponent(id)}`)
export const reprocessFailed = () => post<void>('/rag/documents/reprocess-failed')
export const graphLabels = () => get<string[]>('/rag/graph/labels')
export const loadGraph = (label: string, maxDepth: number) => get<KnowledgeGraph>('/rag/graph', { label, maxDepth }, { timeout: 60000 })
export function uploadDocument(file: File, previousId?: string) {
  const data = new FormData()
  data.append('file', file)
  const url = previousId ? `/rag/documents/${encodeURIComponent(previousId)}/versions` : '/rag/documents'
  return post<string>(url, data, { timeout: 60000 })
}
