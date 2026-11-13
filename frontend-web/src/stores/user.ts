import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '@/api/auth'
import * as faceApi from '@/api/face'
import type { ChangePasswordParams, LoginParams } from '@/api/auth'
import { MOCK_TOKEN, TOKEN_STORAGE_KEY, USER_STORAGE_KEY } from '@/utils/request'
import { cancelActiveRagTasks } from '@/utils/rag-cancellation'

/** 超级管理员角色码：拥有全部权限 */
export const ADMIN_ROLE = 'ADMIN'

/** 演示账号：不请求后端，本地直接建会话（request.ts 切换离线适配器） */
export const MOCK_ACCOUNT = { userNo: 'demo', password: 'demo' } as const

interface StoredUser {
  userId: number | null
  userNo: string
  userName: string
  roleCodes: string[]
}

function readStoredUser(): StoredUser | null {
  // 应用刷新后先从浏览器恢复用户快照；解析失败时按未登录处理，避免坏数据阻塞启动。
  const raw = localStorage.getItem(USER_STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StoredUser
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', () => {
  // token 和用户档案分别恢复，档案缺失不影响 token 校验，后续可重新拉取 profile。
  const stored = readStoredUser()
  const token = ref(localStorage.getItem(TOKEN_STORAGE_KEY) ?? '')
  const userId = ref<number | null>(stored?.userId ?? null)
  const userNo = ref(stored?.userNo ?? '')
  const userName = ref(stored?.userName ?? '')
  const roleCodes = ref<string[]>(stored?.roleCodes ?? [])

  const isLoggedIn = computed(() => token.value !== '')

  function persist() {
    // 登录、刷新档案后同时保存 token 和角色，保证刷新页面时路由守卫能立即完成鉴权判断。
    localStorage.setItem(TOKEN_STORAGE_KEY, token.value)
    localStorage.setItem(
      USER_STORAGE_KEY,
      JSON.stringify({
        userId: userId.value,
        userNo: userNo.value,
        userName: userName.value,
        roleCodes: roleCodes.value,
      } satisfies StoredUser),
    )
  }

  function clear() {
    // 内存状态与 localStorage 必须一起清空，否则刷新页面会重新恢复失效会话。
    token.value = ''
    userId.value = null
    userNo.value = ''
    userName.value = ''
    roleCodes.value = []
    localStorage.removeItem(TOKEN_STORAGE_KEY)
    localStorage.removeItem(USER_STORAGE_KEY)
  }

  /** 工号密码登录（POST /api/system/auth/login），token 与角色由后端下发 */
  async function login(params: LoginParams) {
    if (params.userNo === MOCK_ACCOUNT.userNo && params.password === MOCK_ACCOUNT.password) {
      // 演示账号只建立本地管理员会话，不调用后端，供无服务环境浏览页面。
      token.value = MOCK_TOKEN
      userId.value = 0
      userNo.value = MOCK_ACCOUNT.userNo
      userName.value = '演示用户'
      roleCodes.value = [ADMIN_ROLE]
      persist()
      return
    }
    // 正式登录的 token 和权限完全以服务端返回为准，前端不自行推断角色。
    const result = await authApi.login(params)
    token.value = result.token
    userId.value = result.userId
    userNo.value = result.userNo
    userName.value = result.userName
    roleCodes.value = result.roleCodes
    persist()
  }

  /** 仅使用人脸接口返回的服务端会话和权限。 */
  async function loginWithFace(image: Blob, options: faceApi.FaceLoginRequestOptions = {}) {
    const result = await faceApi.loginWithFace(image, options)
    // 超时或离开页面后的响应不得恢复会话。
    options.signal?.throwIfAborted()
    token.value = result.token
    userId.value = result.userId
    userNo.value = result.userNo
    userName.value = result.userName
    roleCodes.value = result.roleCodes
    persist()
  }

  /** 服务端删会话失败也照常清本地（登出必须总能完成） */
  async function logout() {
    await cancelActiveRagTasks()
    if (token.value === MOCK_TOKEN) {
      clear()
      return
    }
    try {
      // 尽力通知服务端销毁会话；网络失败时仍进入 finally 清理本地状态。
      await authApi.logout()
    } catch {
      // 网络异常/会话已失效均忽略
    } finally {
      clear()
    }
  }

  /** 改密成功后后端使当前会话失效，调用方应引导重新登录 */
  async function changePassword(params: ChangePasswordParams) {
    await cancelActiveRagTasks()
    // 改密后服务端会使旧 token 失效，因此清理本地会话，避免继续携带旧凭证请求。
    await authApi.changePassword(params)
    clear()
  }

  /** 会话有效时拉取最新档案（角色变更后刷新用）；演示会话直接跳过 */
  async function fetchProfile() {
    if (token.value === MOCK_TOKEN) return null
    // 以服务端最新角色覆盖本地快照，防止管理员撤权后前端仍显示旧菜单。
    const profile = await authApi.getProfile()
    userId.value = profile.userId
    userNo.value = profile.userNo
    userName.value = profile.userName
    roleCodes.value = profile.roleCodes
    persist()
    return profile
  }

  return {
    token,
    userId,
    userNo,
    userName,
    roleCodes,
    isLoggedIn,
    login,
    loginWithFace,
    logout,
    changePassword,
    fetchProfile,
  }
})
