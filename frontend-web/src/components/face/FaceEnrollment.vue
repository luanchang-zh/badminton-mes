<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Lock } from '@element-plus/icons-vue'
import { disableFaceLogin, enrollFace, getFaceProfile, reenrollFace } from '@/api/face'
import type { FaceEnrollmentResult, FaceProfile } from '@/api/face'
import { ApiError, MOCK_TOKEN } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import FaceEnrollmentWizard from './FaceEnrollmentWizard.vue'
import FaceScanIcon from './FaceScanIcon.vue'
import './face-enrollment.css'

const userStore = useUserStore()
const profile = ref<FaceProfile>()
const visible = ref(false)
const dismissed = ref(false)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const statusError = ref(false)
const completed = ref(false)
const recapturing = ref(false)
const result = ref<FaceEnrollmentResult>()
let profileVersion = 0
let disposed = false
const demo = computed(() => userStore.token === MOCK_TOKEN)
const showPrompt = computed(() => profile.value?.faceLoginAllowed && !profile.value.enrolled && !dismissed.value && !visible.value)
const promptOffset = ref(0)
let topMessages: HTMLElement[] = []
let messageListObserver: MutationObserver | undefined
let messageStyleObserver: MutationObserver | undefined
let messageResizeObserver: ResizeObserver | undefined

function updatePromptOffset() {
  if (!showPrompt.value || disposed) return
  const bottom = Math.max(0, ...topMessages.map(message => {
    const top = Number.parseFloat(message.style.top)
    return Number.isFinite(top) && message.offsetHeight ? top + message.offsetHeight : 0
  }))
  // 顶部基准 16px，消息下方保留 20px 间距；只移动外层 transform。
  promptOffset.value = bottom ? bottom + 4 : 0
}

function observeTopMessages() {
  messageStyleObserver?.disconnect()
  messageResizeObserver?.disconnect()
  topMessages = Array.from(document.body.querySelectorAll<HTMLElement>(':scope > .el-message.is-center:not(.is-bottom)'))
  topMessages.forEach(message => {
    messageStyleObserver?.observe(message, { attributes: true, attributeFilter: ['style'] })
    messageResizeObserver?.observe(message)
  })
  updatePromptOffset()
}

function stopPromptTracking() {
  messageListObserver?.disconnect()
  messageStyleObserver?.disconnect()
  messageResizeObserver?.disconnect()
  topMessages = []
}

watch(showPrompt, active => {
  stopPromptTracking()
  if (!active) return
  messageStyleObserver = new MutationObserver(updatePromptOffset)
  messageResizeObserver = new ResizeObserver(updatePromptOffset)
  messageListObserver = new MutationObserver(records => {
    if (records.some(record => [...record.addedNodes, ...record.removedNodes]
      .some(node => node instanceof HTMLElement && node.matches('.el-message')))) observeTopMessages()
  })
  messageListObserver.observe(document.body, { childList: true })
  observeTopMessages()
})
const poseNames = ['正面', '左转头', '右转头']
const rejectedMessage = (items: FaceEnrollmentResult['rejectedImages']) => items.map(item => `${poseNames[item.index - 1] ?? `第 ${item.index} 张`}照片：${item.reason}`).join('；')

async function loadProfile(background = false) {
  if (demo.value) return
  const id = ++profileVersion
  if (!background) {
    loading.value = true
    statusError.value = false
  }
  try {
    const data = await getFaceProfile()
    if (!disposed && id === profileVersion) profile.value = data
  } catch {
    if (!disposed && id === profileVersion && !background) statusError.value = true
  } finally {
    if (!disposed && id === profileVersion && !background) loading.value = false
  }
}

async function open() {
  if (busy.value) return
  error.value = ''
  completed.value = false
  recapturing.value = false
  result.value = undefined
  visible.value = true
  await loadProfile()
}

function startRecapture() {
  if (busy.value || !profile.value?.faceLoginAllowed) return
  error.value = ''
  completed.value = false
  result.value = undefined
  recapturing.value = true
}

async function submit(images: Blob[]) {
  if (busy.value || !profile.value?.faceLoginAllowed || images.length !== 3) return
  busy.value = true
  error.value = ''
  try {
    const response = await (recapturing.value ? reenrollFace(images) : enrollFace(images))
    if (disposed) return
    if (response.acceptedCount > 0 && response.faceCount > 0) {
      result.value = response
      profile.value = { ...profile.value, enrolled: true, faceCount: response.faceCount }
      completed.value = true
      recapturing.value = false
    } else {
      profile.value = { ...profile.value, enrolled: false, faceCount: 0, samples: [] }
      error.value = rejectedMessage(response.rejectedImages) || '照片未通过校验，请重拍后再试。'
    }
  } catch (cause) {
    if (disposed) return
    // A previous submission may have succeeded even if its response was lost.
    if (!recapturing.value && cause instanceof ApiError && cause.code === 'A0111') {
      await loadProfile()
      if (!statusError.value && profile.value?.enrolled) {
        completed.value = true
        return
      }
    }
    if (recapturing.value) await loadProfile(true)
    if (disposed) return
    error.value = cause instanceof ApiError ? (cause.userTip || cause.message) : '采集提交失败，请检查网络后重试。'
  } finally {
    if (!disposed) busy.value = false
  }
}

async function disable() {
  try {
    await ElMessageBox.confirm('关闭后将删除已采集的人脸特征，之后请使用密码登录。', '关闭人脸登录', { confirmButtonText: '关闭并删除', cancelButtonText: '保留', type: 'warning' })
  } catch { return }
  busy.value = true
  error.value = ''
  try {
    await disableFaceLogin()
    if (disposed) return
    dismissed.value = true
    completed.value = false
    result.value = undefined
    await loadProfile()
    ElMessage.success('已关闭人脸登录并删除人脸特征')
  } catch (cause) {
    if (!disposed) error.value = cause instanceof ApiError ? (cause.userTip || cause.message) : '关闭失败，请稍后重试。'
  } finally {
    if (!disposed) busy.value = false
  }
}

function beforeClose(done: () => void) {
  if (!busy.value) done()
}

defineExpose({ open })
onMounted(() => { void loadProfile() })
onBeforeUnmount(() => { disposed = true; profileVersion++; stopPromptTracking() })
</script>

<template>
  <div class="face-prompt-position" :style="{ transform: `translate(-50%, ${promptOffset}px)` }">
    <Transition name="face-prompt">
    <aside v-if="showPrompt" class="face-prompt mes-account-notice" aria-labelledby="face-prompt-title" role="status">
      <span class="face-prompt__icon"><FaceScanIcon /></span>
      <div class="face-prompt__copy"><strong id="face-prompt-title">开启人脸登录</strong><p>采集三个角度的照片，下次可使用人脸登录。</p></div>
      <el-button type="primary" @click="open">立即采集</el-button>
      <button class="face-prompt__later" type="button" @click="dismissed = true">稍后</button>
      <button class="face-prompt__close" type="button" aria-label="关闭人脸采集提醒" @click="dismissed = true"><el-icon><Close /></el-icon></button>
    </aside>
    </Transition>
  </div>
  <el-dialog v-model="visible" :title="recapturing ? '重新采集人脸' : completed ? '设置完成' : '设置人脸登录'" width="620px" align-center class="enroll-dialog" modal-class="enroll-overlay" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" :before-close="beforeClose" destroy-on-close append-to-body>
    <template #header><h2 class="dialog-heading">{{ recapturing ? '重新采集人脸' : completed ? '设置完成' : '设置人脸登录' }}</h2></template>
    <template v-if="visible">
      <div v-if="demo" class="enroll-message"><FaceScanIcon /><h3>请使用正式工作账号</h3><p>当前演示账号仅支持浏览，登录正式账号后即可采集人脸。</p><el-button @click="visible = false">我知道了</el-button></div>
      <div v-else-if="loading" class="enroll-message" role="status"><p>正在查询人脸登录状态…</p></div>
      <div v-else-if="statusError" class="enroll-message" role="alert"><h3>人脸登录状态加载失败</h3><p>请检查网络后重试。</p><el-button @click="loadProfile()">重新加载</el-button></div>
      <div v-else-if="!recapturing && (completed || profile?.enrolled)" class="success-view" role="status">
        <span class="success-mark"><el-icon :size="36"><Check /></el-icon></span>
        <h3>{{ !profile?.faceLoginAllowed ? '管理已采集的人脸资料' : '人脸登录已开启' }}</h3>
        <p>{{ profile?.faceLoginAllowed ? '下次登录时，选择「人脸登录」即可。' : '当前账号使用密码登录，您可以关闭并删除此前采集的人脸特征。' }}</p>
        <span class="enroll-sample-count">已录入 {{ profile?.faceCount }} 张有效照片</span>
        <p v-if="result?.rejectedImages.length" class="enroll-partial">部分照片未录入：{{ rejectedMessage(result.rejectedImages) }}</p>
        <p v-if="error" class="enroll-error" role="alert">{{ error }}</p>
        <div class="enroll-result-actions">
          <el-button type="primary" class="primary-action" :disabled="busy" @click="visible = false">完成</el-button>
          <el-button v-if="profile?.faceLoginAllowed" class="primary-action" :disabled="busy" @click="startRecapture">重新采集人脸</el-button>
        </div>
        <button class="quiet-action enroll-disable" type="button" :disabled="busy" @click="disable">{{ busy ? '正在关闭…' : '关闭人脸登录并删除特征' }}</button>
      </div>
      <div v-else-if="profile && !profile.faceLoginAllowed" class="enroll-message"><h3>当前账号使用密码登录</h3><p>无需采集人脸信息。</p><el-button @click="visible = false">我知道了</el-button></div>
      <template v-else-if="profile?.faceLoginAllowed"><FaceEnrollmentWizard :busy="busy" :error="error" :replacing="recapturing" @submit="submit" @reset="error = ''" /><button v-if="!busy" class="quiet-action enroll-cancel" type="button" @click="visible = false">{{ recapturing ? '取消重新采集' : '暂不开启' }}</button></template>
      <div class="privacy-note"><el-icon :size="13"><Lock /></el-icon><span>仅用于本账号登录，可在个人设置中随时关闭</span></div>
    </template>
  </el-dialog>
</template>

<style scoped>
.face-prompt-position { position: fixed; z-index: 100; top: 16px; left: 50%; width: var(--mes-notice-width); pointer-events: none; transition: transform 280ms cubic-bezier(.455, .03, .515, .955); }
.face-prompt { --el-color-primary: #2866b7; --el-color-primary-light-3: #6994cd; --el-color-primary-dark-2: #205292; display: flex; align-items: center; gap: 14px; width: 100%; pointer-events: auto; }
.face-prompt__icon { display: grid; place-items: center; flex: 0 0 32px; color: #2866b7; }
.face-prompt__icon svg { width: 28px; height: 28px; }
.face-prompt__copy { flex: 1; }
.face-prompt__copy strong { font-size: 14px; font-weight: 600; line-height: 1.6; }
.face-prompt__copy p { margin: 5px 0 0; font-size: 12px; color: #687a91; line-height: 1.6; }
.face-prompt > .el-button { height: 36px; border-radius: 6px; font-size: 12px; }
.face-prompt__later, .face-prompt__close { border: 0; background: none; color: #687a91; font: inherit; font-size: 12px; cursor: pointer; white-space: nowrap; }
.face-prompt__close { position: absolute; right: 7px; top: 7px; padding: 5px; }
.face-prompt button:focus-visible { outline: 2px solid #2866b7; outline-offset: 3px; }
.face-prompt-enter-active, .face-prompt-leave-active { transition: opacity 200ms ease-out, transform 200ms ease-out; }
.face-prompt-enter-from, .face-prompt-leave-to { opacity: 0; transform: translateY(-8px); }
@media (prefers-reduced-motion: reduce) { .face-prompt-position, .face-prompt *, .face-prompt-enter-active, .face-prompt-leave-active { transition: none; animation: none; } }
</style>
