<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { isAxiosError } from 'axios'
import { Camera, VideoCamera, RefreshRight, Check } from '@element-plus/icons-vue'
import FaceScanIcon from './FaceScanIcon.vue'
import { ApiError } from '@/utils/request'
import type { FaceLoginRequestOptions } from '@/api/face'

const props = withDefaults(defineProps<{
  mode: 'login' | 'enroll'
  busy?: boolean
  allowed?: boolean
  verify?: (image: Blob, options: FaceLoginRequestOptions) => Promise<void>
}>(), { busy: false, allowed: true })
const emit = defineEmits<{ submit: [images: Blob[]]; reset: []; progress: [count: number] }>()
const video = ref<HTMLVideoElement>()
const active = ref(false)
const opening = ref(false)
const capturing = ref(false)
const error = ref('')
const samples = ref<{ blob: Blob; url: string }[]>([])
let stream: MediaStream | undefined
let requestId = 0
let autoRound = 0
let autoTimer: ReturnType<typeof setTimeout> | undefined
let sampleTimer: ReturnType<typeof setTimeout> | undefined
let autoController: AbortController | undefined
const autoPhase = ref<'aligning' | 'verifying' | 'done' | 'failed'>('aligning')
const autoStatus = computed(() => autoPhase.value === 'verifying' ? '正在验证身份，请保持面部稳定' : autoPhase.value === 'done' ? '验证通过，正在进入工作台' : '请将面部放入轮廓，系统将自动采样验证')

function cancelAutoLogin() {
  autoRound++
  clearTimeout(autoTimer)
  clearTimeout(sampleTimer)
  autoController?.abort()
  autoController = undefined
}

/** 登录只上传取景轮廓周围的区域，按 object-fit: cover 映射回原始视频坐标。 */
async function readFrame(source: HTMLVideoElement, cropToGuide: boolean): Promise<Blob> {
  if (!source.videoWidth || !source.videoHeight) throw new Error('video not ready')
  let sx = 0, sy = 0, sw = source.videoWidth, sh = source.videoHeight
  if (cropToGuide) {
    const bounds = source.getBoundingClientRect()
    const scale = Math.max(bounds.width / sw, bounds.height / sh)
    sx = (sw - bounds.width / scale) / 2 + bounds.width * 0.18 / scale
    sy = (sh - bounds.height / scale) / 2 + bounds.height * 0.07 / scale
    sw = bounds.width * 0.64 / scale
    sh = bounds.height * 0.84 / scale
  }
  const canvas = document.createElement('canvas')
  const outputScale = Math.min(1, 640 / Math.max(sw, sh))
  canvas.width = Math.max(1, Math.round(sw * outputScale))
  canvas.height = Math.max(1, Math.round(sh * outputScale))
  const context = canvas.getContext('2d')
  if (!context) throw new Error('canvas unavailable')
  context.drawImage(source, sx, sy, sw, sh, 0, 0, canvas.width, canvas.height)
  const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', 0.9))
  if (!blob) throw new Error('capture failed')
  return blob
}

function startAutoLogin() {
  cancelAutoLogin()
  const round = autoRound
  const controller = new AbortController()
  autoController = controller
  // 为页面结果绘制预留 200ms，整轮交互预算为 5 秒。
  const budgetMs = 4800
  const deadline = performance.now() + budgetMs
  autoPhase.value = 'aligning'
  let attempts = 0
  const fail = (message: string) => {
    autoPhase.value = 'failed'
    error.value = message
    stopCamera()
  }
  autoTimer = setTimeout(() => {
    if (round === autoRound) fail('本次验证已超时（5秒），请调整位置后重新验证。')
  }, budgetMs)
  const sample = async () => {
    if (round !== autoRound || controller.signal.aborted) return
    const source = video.value
    if (!source?.videoWidth) {
      sampleTimer = setTimeout(() => void sample(), 100)
      return
    }
    try {
      attempts++
      const image = await readFrame(source, true)
      controller.signal.throwIfAborted()
      const remaining = deadline - performance.now()
      if (remaining <= 0) return fail('本次验证已超时（5秒），请调整位置后重新验证。')
      autoPhase.value = 'verifying'
      if (!props.verify) throw new Error('verification unavailable')
      await props.verify(image, { signal: controller.signal, timeout: Math.max(1, Math.min(3000, remaining)) })
      if (round !== autoRound || controller.signal.aborted) return
      autoPhase.value = 'done'
      stopCamera()
    } catch (cause) {
      if (round !== autoRound || controller.signal.aborted) return
      // 只有画面不合格才重采；身份拒绝、限流及服务故障立即结束，保留后端计数策略。
      if (cause instanceof ApiError && cause.code === 'A0700' && attempts < 3 && deadline - performance.now() > 500) {
        autoPhase.value = 'aligning'
        sampleTimer = setTimeout(() => void sample(), 350)
        return
      }
      if (cause instanceof ApiError) {
        fail(cause.userTip || cause.message)
      } else if (isAxiosError(cause) && ['ECONNABORTED', 'ETIMEDOUT'].includes(cause.code ?? '')) {
        fail('验证响应超时，请重新验证或使用密码登录。')
      } else if (isAxiosError(cause) && (!cause.response || [502, 503, 504].includes(cause.response.status))) {
        fail('暂时无法连接登录服务，请稍后重试或使用密码登录。')
      } else {
        fail('验证失败，请重新验证或使用密码登录。')
      }
    }
  }
  // 给用户短暂对齐时间；此后自动采样，无拍照或确认按钮。
  sampleTimer = setTimeout(() => void sample(), 650)
}

const target = computed(() => props.mode === 'enroll' ? 2 : 1)
const ready = computed(() => samples.value.length === target.value)
const instruction = computed(() => ready.value
  ? (props.mode === 'enroll' ? '照片已采集，请提交并开通人脸登录' : '照片已采集，请点击验证登录')
  : samples.value.length === 1 ? '请轻微向左或向右转头，采集第二张照片' : '请正视摄像头，将面部置于取景框内')

function stopCamera() {
  cancelAutoLogin()
  requestId++
  stream?.getTracks().forEach(track => track.stop())
  stream = undefined
  if (video.value) video.value.srcObject = null
  active.value = false
  opening.value = false
}

async function startCamera() {
  if (opening.value || props.busy || !props.allowed) return
  error.value = ''
  autoPhase.value = 'aligning'
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    error.value = '当前环境无法使用摄像头，请通过 HTTPS 或本机 localhost 访问。'
    return
  }
  opening.value = true
  const id = ++requestId
  try {
    const media = await navigator.mediaDevices.getUserMedia({ audio: false, video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 480 } } })
    if (id !== requestId) {
      media.getTracks().forEach(track => track.stop())
      return
    }
    stream = media
    active.value = true
    await nextTick()
    if (!video.value || id !== requestId) return
    video.value.srcObject = media
    await video.value.play()
    if (props.mode === 'login' && id === requestId) startAutoLogin()
  } catch (cause) {
    if (id !== requestId) return
    stopCamera()
    const name = cause instanceof DOMException ? cause.name : ''
    error.value = name === 'NotAllowedError' ? '摄像头权限未开启，请在浏览器地址栏允许访问后重试。'
      : name === 'NotFoundError' ? '未找到摄像头，请连接摄像头后重试。'
        : name === 'NotReadableError' ? '摄像头正在被占用，请关闭其他使用摄像头的应用后重试。'
          : '摄像头启动失败，请检查设备后重试。'
  } finally {
    if (id === requestId) opening.value = false
  }
}

async function capture() {
  const source = video.value
  if (!source || !source.videoWidth || capturing.value || ready.value || props.busy) return
  capturing.value = true
  const id = requestId
  try {
    const blob = await readFrame(source, false)
    if (id !== requestId) return
    samples.value.push({ blob, url: URL.createObjectURL(blob) })
    if (ready.value) stopCamera()
  } catch {
    error.value = '照片采集失败，请重新拍摄。'
  } finally {
    capturing.value = false
  }
}

function reset() {
  stopCamera()
  samples.value.forEach(sample => URL.revokeObjectURL(sample.url))
  samples.value = []
  error.value = ''
  emit('reset')
}

function visibilityChanged() {
  if (document.hidden) {
    stopCamera()
    if (props.mode === 'login' && autoPhase.value !== 'done') {
      autoPhase.value = 'failed'
      error.value = '验证已暂停，返回后请重新验证。'
    }
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', visibilityChanged)
  if (props.mode === 'login') void startCamera()
})
watch(() => props.allowed, allowed => { if (!allowed) reset() })
watch(() => samples.value.length, count => emit('progress', count))
onBeforeUnmount(() => {
  reset()
  document.removeEventListener('visibilitychange', visibilityChanged)
})
</script>

<template>
  <div class="face-capture" :class="{ 'face-capture--compact': mode === 'login', 'face-capture--live': active, 'face-capture--ready': ready }">
    <div class="face-capture__viewport">
      <div v-if="mode === 'enroll'" class="face-capture__topline"><span><el-icon><VideoCamera /></el-icon>人脸采集</span><span>{{ ready ? '采集已完成' : active ? '摄像头已开启' : '等待开启摄像头' }}</span></div>
      <video v-show="active" ref="video" autoplay muted playsinline aria-label="摄像头实时预览" />
      <img v-if="ready" :src="samples[samples.length - 1]?.url" alt="本次采集的照片预览" />
      <div v-if="mode === 'enroll' && !active && !ready" class="face-capture__lens" aria-hidden="true"><div class="face-capture__lens-inner"><FaceScanIcon /></div></div>
      <svg v-if="mode === 'login'" class="face-capture__face-outline" viewBox="0 0 200 240" fill="none" aria-hidden="true">
        <path class="face-capture__head-contour" d="M100 10C66 10 45 28 42 62L40 91C34 84 24 88 25 105L28 128C30 139 36 142 42 139C47 170 56 192 74 207C84 218 91 222 100 222C109 222 116 218 126 207C144 192 153 170 158 139C164 142 170 139 172 128L175 105C176 88 166 84 160 91L158 62C155 28 134 10 100 10Z" />
        <path class="face-capture__ear-detail" d="M32 100C38 94 39 110 37 123M168 100C162 94 161 110 163 123" />
      </svg>
      <div v-else-if="!ready && active" class="face-capture__guide" aria-hidden="true" />
      <span v-if="mode === 'login' && !error" class="face-capture__auto-status" role="status">{{ opening ? '正在开启摄像头…' : autoStatus }}<small v-if="active">自动验证 · 最长 5 秒</small></span>
      <div v-if="mode === 'login' && error" class="face-capture__error face-capture__login-error" role="alert">
        <span>{{ error }}</span>
        <button class="face-capture__retry" type="button" :disabled="opening" @click="startCamera">重新验证</button>
      </div>
      <span v-if="ready" class="face-capture__captured"><el-icon><Check /></el-icon>已采集 {{ samples.length }} 张</span>
      <span v-else-if="mode === 'enroll'" class="face-capture__camera-label">保持面部清晰 · 镜头中仅您一人</span>
    </div>
    <p v-if="mode === 'enroll'" class="face-capture__instruction" role="status">{{ instruction }}</p>
    <div v-if="mode === 'enroll' && samples.length" class="face-capture__samples" aria-label="已采集照片">
      <div v-for="index in 2" :key="index" class="face-capture__sample" :class="{ 'face-capture__sample--done': samples[index - 1] }">
        <img v-if="samples[index - 1]" :src="samples[index - 1]?.url" :alt="index === 1 ? '正面照片' : '微侧面照片'" />
        <FaceScanIcon v-else />
        <span>{{ index === 1 ? '正面照片' : '微侧面照片' }}<el-icon v-if="samples[index - 1]"><Check /></el-icon></span>
      </div>
    </div>
    <p v-if="mode === 'enroll' && error" class="face-capture__error" role="alert">{{ error }}</p>
    <div v-if="mode === 'enroll'" class="face-capture__actions">
      <el-button v-if="ready" type="primary" :loading="busy" :disabled="!allowed" @click="emit('submit', samples.map(sample => sample.blob))">{{ busy ? '正在验证…' : '提交并开通人脸登录' }}</el-button>
      <el-button v-else-if="active" type="primary" :loading="capturing" :disabled="busy || !allowed || opening" @click="capture"><el-icon><Camera /></el-icon>{{ samples.length ? '采集微侧面照片' : '采集正面照片' }}</el-button>
      <el-button v-else type="primary" :loading="opening" :disabled="busy || !allowed" @click="startCamera"><el-icon><VideoCamera /></el-icon>{{ samples.length ? '继续采集' : '开启摄像头' }}</el-button>
      <button v-if="samples.length" class="face-capture__retake" type="button" :disabled="busy || capturing" @click="reset"><el-icon><RefreshRight /></el-icon>重新采集</button>
    </div>

  </div>
</template>

<style scoped>
.face-capture {
  --el-color-primary: #1b704e;
  --el-color-primary-light-3: #478f70;
  --el-color-primary-light-5: #8fbd9f;
  --el-color-primary-light-7: #c3dccc;
  --el-color-primary-light-9: #f0f6f1;
  --el-color-primary-dark-2: #14563c;
  color: #2f4c3a;
}
.face-capture__viewport { position: relative; display: grid; place-items: center; height: 312px; overflow: hidden; border-radius: 16px; background: #123d30; }
.face-capture__viewport video, .face-capture__viewport > img { width: 100%; height: 100%; position: absolute; inset: 0; object-fit: cover; transform: scaleX(-1); }
.face-capture__topline { position: absolute; z-index: 2; top: 18px; left: 20px; right: 20px; display: flex; align-items: center; justify-content: space-between; font-size: 11px; color: #c3dccb; }
.face-capture__topline > span:first-child { display: flex; align-items: center; gap: 6px; }
.face-capture--live .face-capture__topline, .face-capture--ready .face-capture__topline { padding: 6px 8px; top: 10px; left: 10px; right: 10px; border-radius: 6px; background: #123d30c9; }
.face-capture__lens { position: relative; display: grid; place-items: center; width: 210px; height: 210px; border: 1px solid #6b947e55; border-radius: 50%; }
.face-capture__lens::before { content: ''; position: absolute; inset: 17px; border: 1px solid #81a38e88; border-radius: inherit; }
.face-capture__lens-inner { display: grid; place-items: center; width: 146px; height: 146px; border-radius: 50%; background: #214d39; color: #cee2d4; }
.face-capture__lens-inner svg { width: 82px; height: 82px; }
.face-capture__camera-label { position: absolute; bottom: 20px; color: #a8c4b1; font-size: 12px; letter-spacing: .5px; }
.face-capture__guide { position: absolute; top: 15%; left: 50%; width: 44%; height: 66%; transform: translateX(-50%); border: 1.5px solid #d7eddf; border-radius: 46%; box-shadow: 0 0 0 200px #102e2b38; pointer-events: none; }
.face-capture__captured { display: flex; align-items: center; gap: 5px; position: absolute; bottom: 14px; right: 14px; padding: 6px 10px; border-radius: 6px; background: #fff; color: #1b704e; font-size: 12px; }
.face-capture__instruction { margin: 16px 0; color: #627b68; font-size: 13px; line-height: 1.7; text-align: center; }
.face-capture__error { margin: 12px 0; padding: 10px 12px; border-radius: 8px; background: #fff5f3; color: #a84035; font-size: 13px; line-height: 1.7; }
.face-capture__login-error { position: absolute; left: 16px; right: 16px; bottom: 12px; z-index: 2; margin: 0; padding: 10px 14px 6px; background: #fff5f3f5; font-size: max(11px, calc(13 * var(--u, 1px))); text-align: center; }
.face-capture__login-error .face-capture__retry { margin: 5px auto 0; }
.face-capture__actions > .el-button { width: 100%; height: 46px; border-radius: 10px; font-size: 14px; font-weight: 600; transition: background-color 150ms ease, box-shadow 150ms ease; }
.face-capture__actions > .el-button:not(:disabled) { box-shadow: 0 5px 12px #194d3320; }
.face-capture__actions .el-icon { margin-right: 8px; font-size: 17px; }
.face-capture__retake { display: flex; align-items: center; justify-content: center; gap: 5px; width: 100%; margin-top: 12px; padding: 5px; border: 0; background: none; color: #6e8375; cursor: pointer; }
.face-capture__retake:disabled { opacity: .5; cursor: default; }
.face-capture__retake:focus-visible { outline: 2px solid #1b704e; outline-offset: 2px; }
.face-capture__samples { display: flex; justify-content: center; gap: 12px; margin-bottom: 16px; }
.face-capture__sample { display: flex; align-items: center; gap: 8px; min-width: 134px; padding: 5px; border: 1px dashed #d1dfd4; border-radius: 8px; font-size: 11px; color: #87998b; }
.face-capture__sample > img, .face-capture__sample > svg { width: 34px; height: 34px; border-radius: 4px; object-fit: cover; }
.face-capture__sample > svg { padding: 5px; }
.face-capture__sample > span { display: flex; gap: 10px; align-items: center; }
.face-capture__sample--done { border-style: solid; border-color: #bed6c6; color: #37684a; background: #f7faf7; }
.face-capture__privacy { display: flex; justify-content: center; align-items: center; gap: 5px; margin: 12px 0 0; font-size: 11px; color: #93a296; }
.face-capture--compact .face-capture__viewport { height: calc(404 * var(--u, 1px)); border: 1px solid #ffffffd9; border-radius: calc(36 * var(--u, 1px)); background: radial-gradient(ellipse at 50% 55%, #8beed64f, #d3f7f335 55%, #e8ffff19); box-shadow: inset 0 0 calc(40 * var(--u, 1px)) #ffffff73, 0 calc(10 * var(--u, 1px)) calc(30 * var(--u, 1px)) #48cbb016; }
.face-capture--compact .face-capture__viewport video, .face-capture--compact .face-capture__viewport > img { border-radius: calc(36 * var(--u, 1px)); }
.face-capture__face-outline { position: absolute; top: 7%; left: 18%; width: 64%; height: 84%; pointer-events: none; stroke: #def7ec; stroke-width: 1.6; stroke-dasharray: 5 4; stroke-linecap: round; filter: drop-shadow(0 1px 2px #17372b55); }
.face-capture__face-outline path { vector-effect: non-scaling-stroke; }
.face-capture:not(.face-capture--live) .face-capture__face-outline { stroke: #6da88d; filter: none; }
.face-capture__ear-detail { opacity: .75; }
.face-capture__auto-status { position: absolute; left: 16px; right: 16px; bottom: 12px; padding: 8px 10px; border-radius: 9px; background: #12372cc9; color: #f1fff7; font-size: max(11px, calc(13 * var(--u, 1px))); line-height: 1.6; text-align: center; }
.face-capture__auto-status small { display: block; color: #b6d9c6; font-size: 10px; }
.face-capture__retry { display: block; margin: 8px auto; padding: 4px 10px; border: 0; border-radius: 4px; color: #168c61; background: transparent; font: inherit; font-size: 12px; cursor: pointer; }
.face-capture__retry:focus-visible { outline: 2px solid #00b44d; outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) { .face-capture__actions > .el-button { transition: none; } }
</style>
