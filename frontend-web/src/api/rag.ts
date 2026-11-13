import service, { ApiError, isMockSession, redirectToLogin, TOKEN_STORAGE_KEY } from '@/utils/request'
import type { ApiResult, PageResult, RequestOptions } from '@/utils/request'
import { readSse } from '@/utils/sse'
import type { SseEvent } from '@/utils/sse'

export type RagMode = 'naive' | 'mix' | 'local' | 'global' | 'hybrid'
export type MessageStatus = 'GENERATING' | 'COMPLETED' | 'CANCELLED' | 'FAILED'
export interface RagProfile { bound: boolean; enabled: boolean; canManageDocuments: boolean }
export interface RagReference { referenceId: string; sourceName: string; chunks: string[] }
export interface RagConversation {
  id: number; title: string; activeMessageId: number | null; createdAt: string; updatedAt: string
}
export interface RagMessage {
  id: number; conversationId: number; role: 'user' | 'assistant'; content: string; mode: RagMode
  status: MessageStatus; replyToId: number | null; references: RagReference[]
  errorTip: string | null; createdAt: string; updatedAt: string
}
export interface RagDocument { docId: string; fileName: string; status: string; statusText: string }
export interface RagMeta { conversationId: number; userMessageId: number; assistantMessageId: number }
export interface RagQuestion { conversationId: number; requestId: string; question: string; mode: RagMode }

export function newRagRequestId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  // 工厂内网以 HTTP 访问时 randomUUID 可能不可用；仍使用随机字节生成去重编号。
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 15) | 64; bytes[8] = (bytes[8]! & 63) | 128
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

const options = (signal?: AbortSignal): RequestOptions => ({ silent: true, signal })
export const ragApi = {
  profile: (signal?: AbortSignal) => service.get<never, RagProfile>('/rag/profile', options(signal)),
  documents: (signal?: AbortSignal) => service.get<never, RagDocument[]>('/rag/documents', { ...options(signal), timeout: 8000 }),
  conversations: (pageNo = 1, signal?: AbortSignal) => service.get<never, PageResult<RagConversation>>('/rag/conversations', { ...options(signal), params: { pageNo, pageSize: 100 } }),
  create: () => service.post<never, RagConversation>('/rag/conversations', {}, options()),
  rename: (id: number, title: string) => service.patch<never, RagConversation>(`/rag/conversations/${id}`, { title }, options()),
  remove: (id: number) => service.delete<never, null>(`/rag/conversations/${id}`, options()),
  messages: (id: number, pageNo = 2_147_483_647, signal?: AbortSignal) => service.get<never, PageResult<RagMessage>>(`/rag/conversations/${id}/messages`, { ...options(signal), params: { pageNo, pageSize: 50 } }),
  cancel: (id: number) => service.post<never, RagMessage>(`/rag/messages/${id}/cancel`, undefined, { ...options(), timeout: 2000 }),
}

export async function streamRag(
  path: '/rag/query/stream' | `/rag/messages/${number}/retry`,
  payload: RagQuestion | { requestId: string },
  signal: AbortSignal,
  onEvent: (event: SseEvent) => void,
) {
  if (isMockSession()) throw new Error('演示账号仅支持浏览，请使用正式账号登录后提问')
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  const response = await fetch(`/api${path}`, {
    method: 'POST', signal,
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', Authorization: `Bearer ${token ?? ''}` },
    body: JSON.stringify(payload),
  })
  if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream')) {
    const result = await response.json().catch(() => null) as ApiResult | null
    if (response.status === 401 || result?.code === 'A0230') redirectToLogin()
    if (result?.code && result.code !== '00000') throw new ApiError(result)
    throw new Error(response.ok ? '问答服务响应格式异常，请重试' : '问答服务暂时无法连接，请稍后重试')
  }
  if (!response.body) throw new Error('当前浏览器无法读取流式回答')
  await readSse(response.body, onEvent, signal)
}

export function ragError(error: unknown): string {
  if (error instanceof ApiError) return error.userTip || error.message
  if (error instanceof TypeError) return '网络连接中断，请检查连接后重试'
  return error instanceof Error ? error.message : '操作失败，请重试'
}
