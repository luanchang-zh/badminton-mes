import { get, post, put } from '@/utils/request'

/**
 * 认证接口，对齐后端 AuthController（/api/system/auth）。
 * 登录后 token 以 Authorization: Bearer 传递（request.ts 拦截器统一附加）。
 */

export interface LoginParams {
  userNo: string
  password: string
}

export interface LoginResult {
  token: string
  userId: number
  userNo: string
  userName: string
  roleCodes: string[]
}

export interface RegistrationRole {
  id: number
  roleCode: string
  roleName: string
  remark: string | null
  status: number
}

export interface RegisterParams {
  userNo: string
  userName: string
  password: string
  roleId: number
}

/** 自助注册仅展示后端允许选择的职位。 */
export function getRegistrationRoles(): Promise<RegistrationRole[]> {
  return get('/system/auth/registration_roles')
}

export function registerAccount(data: RegisterParams): Promise<number> {
  return post('/system/auth/register', data)
}

export interface AuthProfile {
  userId: number
  userNo: string
  userName: string
  /** 后端已脱敏 */
  mobile: string | null
  workshopId: number | null
  lineId: number | null
  roleCodes: string[]
  roleNames: string[]
}

export interface ChangePasswordParams {
  oldPassword: string
  newPassword: string
}

export function login(params: LoginParams): Promise<LoginResult> {
  // 登录凭证交给后端校验，返回 token 和角色信息供 Pinia 持久化。
  return post('/system/auth/login', params)
}

/** 幂等；服务端删除会话 */
export function logout(): Promise<void> {
  // 通知后端删除服务端会话；调用方即使请求失败也会清理本地凭证。
  return post('/system/auth/logout')
}

export function getProfile(): Promise<AuthProfile> {
  // 获取服务端最新角色和组织归属，避免长期使用过期的本地用户快照。
  return get('/system/auth/profile')
}

/** 成功后当前会话失效，需重新登录 */
export function changePassword(params: ChangePasswordParams): Promise<void> {
  // 改密成功后服务端使当前 token 失效，调用方随后应清空本地会话。
  return put('/system/auth/password', params)
}
