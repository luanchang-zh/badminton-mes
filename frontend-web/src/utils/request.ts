import axios, { AxiosError } from 'axios'
import type { AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

/** 自动采样的中间错误由取景区展示，避免每一帧弹出通知。 */
export type RequestOptions = AxiosRequestConfig & { silent?: boolean }

/**
 * 与后端 CommonResult<T> 对齐的统一响应结构。
 * code 为 5 位字符串错误码，成功固定 '00000'。
 */
export interface ApiResult<T = unknown> {
  code: string
  message: string
  userTip: string | null
  data: T
}

/** 与后端 PageResult<T> 对齐 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNo: number
  pageSize: number
}

/** 与后端 PageParam 对齐 */
export interface PageParam {
  pageNo: number
  pageSize: number
}

export const SUCCESS_CODE = '00000'
export const TOKEN_STORAGE_KEY = 'mes_token'
export const USER_STORAGE_KEY = 'mes_user'

// ---------- 演示模式（不连后端浏览页面） ----------

/** 演示会话的本地 token 标记，由 stores/user.ts 用演示账号登录时写入 */
export const MOCK_TOKEN = 'mock-demo-session'

export function isMockSession(): boolean {
  // 演示 token 只存在浏览器本地，用于让页面在没有后端服务时保持可浏览状态。
  return localStorage.getItem(TOKEN_STORAGE_KEY) === MOCK_TOKEN
}

/**
 * 演示会话离线适配器：请求不出网。
 * GET 返回空数据（分页给空 PageResult，其余给空数组——数组方法可用、
 * 字段访问得 undefined，列表/详情页均可安全渲染）；写操作返回统一业务错误。
 */
function mockAdapter(config: InternalAxiosRequestConfig): Promise<AxiosResponse> {
  // 演示模式导出返回空文件，避免下载逻辑报错
  if (config.responseType === 'blob') {
    return Promise.resolve({
      data: new Blob([''], { type: 'application/octet-stream' }),
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    })
  }
  const method = (config.method ?? 'get').toLowerCase()
  let body: ApiResult
  if (method === 'get') {
    // GET 请求在演示模式下返回空数据；页面可以正常渲染，但不会误显示伪造的业务记录。
    const params = (config.params ?? {}) as Record<string, unknown>
    const isPage = (config.url ?? '').includes('/page') || params.pageNo !== undefined
    body = {
      code: SUCCESS_CODE,
      message: 'ok',
      userTip: null,
      data: isPage
        ? { list: [], total: 0, pageNo: params.pageNo ?? 1, pageSize: params.pageSize ?? 10 }
        : [],
    }
  } else {
    // 写操作不修改本地演示数据，统一返回业务错误，避免用户误以为数据已经保存到后端。
    body = {
      code: 'B0001',
      message: 'demo mode',
      userTip: '演示模式：未连接后端，仅支持浏览页面',
      data: null,
    }
  }
  return Promise.resolve({ data: body, status: 200, statusText: 'OK', headers: {}, config })
}

/** 登录失效（A0230，HTTP 401）；权限不足（A0301，HTTP 403） */
export const UNAUTHORIZED_CODE = 'A0230'
export const FORBIDDEN_CODE = 'A0301'

/** 业务错误：携带后端错误码，便于调用方按码分支处理 */
export class ApiError extends Error {
  readonly code: string
  readonly userTip: string | null

  constructor(result: ApiResult) {
    super(result.message)
    this.name = 'ApiError'
    this.code = result.code
    this.userTip = result.userTip
  }
}

const service = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

service.interceptors.request.use((config) => {
  if (isMockSession()) {
    // 将 adapter 替换为本地适配器，axios 仍保持相同调用方式，页面无需分支判断演示环境。
    config.adapter = mockAdapter
    return config
  }
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  if (token) {
    // 仅在存在 token 时添加 Authorization，未登录请求仍可访问公开接口或由后端返回 401。
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 并发请求同时 401 时只跳一次登录 */
let redirectingToLogin = false

/**
 * 会话失效处理：清本地凭证后整页跳登录。
 * 用 location 而非 router，避免 request → router → store → request 循环依赖，
 * 且整页刷新可一并重置 Pinia 内存态。
 */
export function redirectToLogin() {
  // 并发请求可能同时收到 401；标志位确保只执行一次清理和跳转，避免重复导航。
  if (redirectingToLogin) return
  redirectingToLogin = true
  localStorage.removeItem(TOKEN_STORAGE_KEY)
  localStorage.removeItem(USER_STORAGE_KEY)
  const { pathname, search } = window.location
  const redirect = pathname === '/login' ? '' : `?redirect=${encodeURIComponent(pathname + search)}`
  // 保留原始访问地址，登录成功后可由登录页恢复用户之前打开的业务页面。
  window.location.href = `/login${redirect}`
}

function isApiResult(data: unknown): data is ApiResult {
  return typeof data === 'object' && data !== null && 'code' in data && 'message' in data
}

service.interceptors.response.use(
  (response) => {
    // 文件导出等二进制流：返回完整响应，供 download 读取响应头与文件名
    if (response.config.responseType === 'blob') {
      return response as never
    }
    const body = response.data as ApiResult
    // 后端即使返回 HTTP 200，也可能通过业务 code 表示失败，因此不能只判断 HTTP 状态。
    if (body.code !== SUCCESS_CODE) {
      if (!(response.config as RequestOptions).silent) ElMessage.error(body.userTip || body.message || '请求失败')
      return Promise.reject(new ApiError(body))
    }
    // 拦截器直接解包 data，调用方拿到的即业务数据
    return body.data as never
  },
  (error: AxiosError) => {
    if (axios.isCancel(error)) return Promise.reject(error)
    const notifyError = (message: string) => {
      if (!(error.config as RequestOptions | undefined)?.silent) ElMessage.error(message)
    }
    const status = error.response?.status
    const body = error.response?.data

    // 后端 GlobalExceptionHandler 对 4xx/5xx 也返回 CommonResult 四要素
    if (isApiResult(body)) {
      // 优先使用后端统一错误结构，保证用户提示、错误码和异常对象来自同一份响应。
      if (status === 401 || body.code === UNAUTHORIZED_CODE) {
        notifyError(body.userTip || '登录状态已失效，请重新登录')
        redirectToLogin()
      } else {
        notifyError(body.userTip || body.message || `请求失败（HTTP ${status}）`)
      }
      return Promise.reject(new ApiError(body))
    }

    if (status === 401) {
      notifyError('登录状态已失效，请重新登录')
      redirectToLogin()
    } else {
      notifyError(status ? `请求失败（HTTP ${status}）` : '网络异常，请检查连接')
    }
    return Promise.reject(error)
  },
)

/** params 接受任意对象（接口层的具名参数类型无索引签名，交给 axios 序列化） */
export function get<T>(url: string, params?: object, config?: AxiosRequestConfig): Promise<T> {
  // GET 参数交给 axios 序列化，接口文件只负责提供具名参数类型和资源路径。
  return service.get(url, { params, ...config }) as Promise<T>
}

export function post<T>(url: string, data?: unknown, config?: RequestOptions): Promise<T> {
  // POST 请求保留统一响应解包和错误拦截链，业务 API 不直接处理 AxiosResponse。
  return service.post(url, data, config) as Promise<T>
}

export function put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
  // PUT 用于状态或资源更新，仍由响应拦截器统一转换业务错误。
  return service.put(url, data, config) as Promise<T>
}

export function del<T>(url: string, params?: object): Promise<T> {
  // DELETE 参数通过 query string 传递，适配后端锁版本等请求参数。
  return service.delete(url, { params }) as Promise<T>
}

/** 从 Content-Disposition 解析下载文件名，解析失败回退 fallback */
function parseFileName(disposition: string | undefined, fallback: string): string {
  // 优先解析 RFC 5987 的 filename*，再兼容传统 filename，两个格式都失败时使用默认名。
  if (!disposition) return fallback
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star?.[1]) return decodeURIComponent(star[1])
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain?.[1] ?? fallback
}

/**
 * 文件导出下载：以 blob 接收二进制流并触发浏览器下载。
 * 后端导出异常时仍以 JSON CommonResult 返回，此处解析后走统一错误提示。
 */
export async function download(
  url: string,
  params?: object,
  fallbackFileName = 'export',
): Promise<void> {
  const response = await service.get(url, { params, responseType: 'blob' })
  const blob = response.data as Blob
  // 导出接口成功时是文件流，失败时仍可能返回 JSON；先检查 MIME 类型再决定解析方式。
  // 业务异常（鉴权/校验失败）以 JSON 返回，需解析为 ApiError
  if (blob.type.includes('application/json')) {
    const result = JSON.parse(await blob.text()) as ApiResult
    ElMessage.error(result.userTip || result.message || '导出失败')
    throw new ApiError(result)
  }
  const fileName = parseFileName(
    response.headers['content-disposition'] as string | undefined,
    fallbackFileName,
  )
  const href = URL.createObjectURL(blob)
  // 使用临时 a 元素触发浏览器下载，完成后立即释放 DOM 节点和对象 URL。
  const link = document.createElement('a')
  link.href = href
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(href)
}

export default service
