<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowLeft, ArrowRight, Camera, Check, CircleCheck, InfoFilled, RefreshLeft } from '@element-plus/icons-vue'
import FaceScanIcon from './FaceScanIcon.vue'
import examplesUrl from '@/assets/images/face-pose-examples.webp'

const props = defineProps<{ busy: boolean; error: string; replacing?: boolean }>()
const emit = defineEmits<{ submit: [images: Blob[]]; reset: [] }>()
const phase = ref<'intro' | 'capture' | 'review'>('intro')
const consent = ref(false)
const activeStep = ref(0)
const exampleOpen = ref(false)
const samples = ref<({ blob: Blob; url: string } | undefined)[]>([undefined, undefined, undefined])
const video = ref<HTMLVideoElement>()
const opening = ref(false)
const active = ref(false)
const ready = ref(false)
const capturing = ref(false)
const cameraError = ref('')
let stream: MediaStream | undefined
let version = 0
let disposed = false
let removeTrackListeners: (() => void) | undefined
let cancelEncoding: (() => void) | undefined

const poses = [
  { title: '正面', action: '请正对镜头', hint: '将脸部完整放入取景框，目光看向镜头。' },
  { title: '左转头', action: '请微微向左转头', hint: '肩膀保持不动，像示例一样轻轻转头。' },
  { title: '右转头', action: '请微微向右转头', hint: '保持面部清晰，转动角度无需太大。' },
]
const currentPose = computed(() => poses[activeStep.value]!)

function stopCamera() {
  version++
  cancelEncoding?.()
  removeTrackListeners?.()
  removeTrackListeners = undefined
  stream?.getTracks().forEach(track => track.stop())
  stream = undefined
  if (video.value) video.value.srcObject = null
  active.value = false
  opening.value = false
  ready.value = false
  capturing.value = false
}

async function startCamera() {
  if (disposed || opening.value || active.value || props.busy || !consent.value || phase.value !== 'capture') return
  cameraError.value = ''
  if (document.hidden) {
    cameraError.value = '采集已暂停，返回后请重新开启摄像头。'
    return
  }
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    cameraError.value = '当前环境无法使用摄像头，请通过 HTTPS 或本机 localhost 访问。'
    return
  }
  opening.value = true
  const id = ++version
  try {
    const media = await navigator.mediaDevices.getUserMedia({ audio: false, video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 960 } } })
    if (disposed || id !== version) {
      media.getTracks().forEach(track => track.stop())
      return
    }
    stream = media
    const ended = () => {
      stopCamera()
      cameraError.value = '摄像头连接已中断，请检查设备后重试。'
    }
    media.getVideoTracks().forEach(track => track.addEventListener('ended', ended))
    removeTrackListeners = () => media.getVideoTracks().forEach(track => track.removeEventListener('ended', ended))
    active.value = true
    await nextTick()
    if (disposed || id !== version || !video.value) return
    video.value.srcObject = media
    await video.value.play()
    if (id === version) ready.value = video.value.videoWidth > 0
  } catch (cause) {
    if (disposed || id !== version) return
    stopCamera()
    const name = cause instanceof DOMException ? cause.name : ''
    cameraError.value = name === 'NotAllowedError' ? '摄像头权限未开启，请在浏览器地址栏允许访问后重试。'
      : name === 'NotFoundError' ? '未找到摄像头，请连接摄像头后重试。'
        : name === 'NotReadableError' ? '摄像头正在被占用，请关闭其他使用摄像头的应用后重试。'
          : '摄像头启动失败，请检查设备后重试。'
  } finally {
    if (id === version) opening.value = false
  }
}

async function begin() {
  if (!consent.value || props.busy) return
  phase.value = 'capture'
  await nextTick()
  await startCamera()
}

/** Crop the same centered rectangle as the oval's object-fit: cover preview. */
function snapshot(source: HTMLVideoElement): Promise<Blob> {
  const box = source.getBoundingClientRect()
  if (!box.width || !box.height) throw new Error('Camera viewport unavailable')
  const ratio = box.width / box.height
  const width = Math.min(source.videoWidth, source.videoHeight * ratio)
  const height = Math.min(source.videoHeight, source.videoWidth / ratio)
  const scale = Math.min(1, 960 / Math.max(width, height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(width * scale))
  canvas.height = Math.max(1, Math.round(height * scale))
  const context = canvas.getContext('2d')
  if (!context) return Promise.reject(new Error('Canvas unavailable'))
  context.drawImage(source, (source.videoWidth - width) / 2, (source.videoHeight - height) / 2, width, height, 0, 0, canvas.width, canvas.height)
  return new Promise((resolve, reject) => {
    let settled = false
    const finish = (blob?: Blob | null, error?: Error) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      cancelEncoding = undefined
      canvas.width = canvas.height = 0
      if (blob?.size) resolve(blob)
      else reject(error ?? new Error('Empty image'))
    }
    const timer = setTimeout(() => finish(null, new Error('Encoding timeout')), 5000)
    cancelEncoding = () => finish(null, new DOMException('Capture cancelled', 'AbortError'))
    try { canvas.toBlob(blob => finish(blob), 'image/jpeg', 0.9) }
    catch { finish(null, new Error('Image encoding failed')) }
  })
}

async function takePhoto() {
  const source = video.value
  if (!source?.videoWidth || !ready.value || !active.value || capturing.value || props.busy || !consent.value) return
  capturing.value = true
  cameraError.value = ''
  const id = version
  try {
    const blob = await snapshot(source)
    if (disposed || id !== version || phase.value !== 'capture') return
    const url = URL.createObjectURL(blob)
    const previous = samples.value[activeStep.value]
    if (previous) URL.revokeObjectURL(previous.url)
    samples.value[activeStep.value] = { blob, url }
    emit('reset')
    exampleOpen.value = false
    const next = samples.value.findIndex(sample => !sample)
    if (next === -1) {
      stopCamera()
      phase.value = 'review'
    } else activeStep.value = next
  } catch {
    if (!disposed && id === version) {
      stopCamera()
      cameraError.value = '照片采集失败，请重新拍摄。'
    }
  } finally {
    if (id === version) capturing.value = false
  }
}

async function retake(index: number) {
  if (props.busy || capturing.value) return
  const sample = samples.value[index]
  if (sample) URL.revokeObjectURL(sample.url)
  samples.value[index] = undefined
  activeStep.value = index
  exampleOpen.value = false
  emit('reset')
  phase.value = 'capture'
  await nextTick()
  await startCamera()
}

function previous() {
  if (props.busy || capturing.value) return
  if (activeStep.value === 0) {
    stopCamera()
    phase.value = 'intro'
    cameraError.value = ''
  } else void retake(activeStep.value - 1)
  exampleOpen.value = false
}

function confirm() {
  if (!consent.value || props.busy || !samples.value.every(Boolean)) return
  emit('submit', samples.value.map(sample => sample!.blob))
}

function visibilityChanged() {
  if (document.hidden && phase.value === 'capture') {
    stopCamera()
    cameraError.value = '采集已暂停，返回后请重新开启摄像头。'
  }
}

onMounted(() => document.addEventListener('visibilitychange', visibilityChanged))
onBeforeUnmount(() => {
  disposed = true
  stopCamera()
  samples.value.forEach(sample => { if (sample) URL.revokeObjectURL(sample.url) })
  document.removeEventListener('visibilitychange', visibilityChanged)
})
</script>

<template>
  <div v-if="phase === 'intro'" class="intro-view">
    <div class="intro-mark"><FaceScanIcon /></div>
    <h3>{{ replacing ? '重新采集人脸' : '让下一次登录更轻松' }}</h3>
    <p class="intro-description">{{ replacing ? '重新采集三个角度的照片，提交后替换原有人脸信息。' : '采集三个角度的照片，即可使用人脸登录。' }}</p>
    <div class="intro-poses" aria-label="需要采集的三个角度"><figure v-for="(pose, index) in poses" :key="pose.title"><div class="pose-photo"><img :src="examplesUrl" :style="{ left: `${index * -100}%` }" :alt="`${pose.title}示例`" width="2172" height="724" /></div><figcaption>{{ pose.title }}</figcaption></figure></div>
    <p class="preparation-note">保持光线充足，摘下遮挡面部的物品。</p>
    <div class="intro-consent"><el-checkbox v-model="consent">同意采集人脸信息，用于本账号登录</el-checkbox></div>
    <el-button type="primary" class="primary-action start-action" :disabled="!consent" @click="begin">开始采集<el-icon class="button-next"><ArrowRight /></el-icon></el-button>
  </div>
  <div v-else-if="phase === 'capture'" class="capture-view">
    <ol class="pose-progress" aria-label="采集进度"><li v-for="(pose, index) in poses" :key="pose.title" :class="{ 'is-active': activeStep === index, 'is-finished': samples[index] }" :aria-current="activeStep === index ? 'step' : undefined"><span class="pose-progress-label"><el-icon v-if="samples[index]" :size="13"><Check /></el-icon>{{ pose.title }}</span><span class="pose-progress-track" /></li></ol>
    <div class="capture-stage">
      <div v-if="cameraError || !active" class="camera-help" :role="cameraError ? 'alert' : 'status'"><el-icon :size="32"><Camera /></el-icon><strong>{{ cameraError ? '摄像头暂不可用' : opening ? '正在开启摄像头…' : '准备开启摄像头' }}</strong><p v-if="cameraError">{{ cameraError }}</p><el-button v-if="!opening" @click="startCamera">重新开启摄像头</el-button></div>
      <template v-else><div class="camera-oval"><video ref="video" autoplay playsinline muted @loadeddata="ready = active && !!video?.videoWidth" /></div><span class="alignment-guide" aria-hidden="true" /></template>
    </div>
    <div class="capture-direction" aria-live="polite"><h3>{{ currentPose.action }}</h3><p>{{ currentPose.hint }}</p></div>
    <el-popover v-model:visible="exampleOpen" placement="right" :width="180" trigger="click" popper-class="pose-example-popover"><div class="example-popover-content"><div class="pose-photo"><img :src="examplesUrl" :style="{ left: `${activeStep * -100}%` }" :alt="`${currentPose.title}示例`" width="2172" height="724" /></div><span>{{ currentPose.title }}示例</span></div><template #reference><button class="pose-example-link" type="button"><el-icon :size="13"><InfoFilled /></el-icon>查看姿态示例</button></template></el-popover>
    <div class="capture-controls"><el-button class="previous-action" :icon="ArrowLeft" :disabled="capturing || opening" @click="previous">上一步</el-button><el-button type="primary" class="primary-action" :icon="Camera" :loading="capturing" :disabled="!ready || !active || opening" @click="takePhoto">拍摄{{ currentPose.title }}</el-button></div>
  </div>
  <div v-else class="review-view">
    <div class="review-heading"><span class="review-check"><el-icon :size="22"><CircleCheck /></el-icon></span><h3>三个角度，采集完成</h3><p>{{ replacing ? '提交后将替换原有人脸信息，未通过时需重新采集。' : '确认照片清晰、面部无遮挡后，即可开启。' }}</p></div>
    <div class="review-photos"><figure v-for="(pose, index) in poses" :key="pose.title"><div class="captured-photo"><img v-if="samples[index]" :src="samples[index]!.url" :alt="`已采集${pose.title}照片`" /></div><figcaption><span>{{ pose.title }}</span><button type="button" :disabled="busy" :aria-label="`重拍${pose.title}`" @click="retake(index)"><el-icon :size="13"><RefreshLeft /></el-icon>重拍</button></figcaption></figure></div>
    <p v-if="error" class="enroll-error" role="alert">{{ error }}</p>
    <el-button type="primary" class="primary-action review-confirm" :loading="busy" :disabled="busy" @click="confirm">{{ busy ? '正在提交…' : replacing ? '确认并更新' : '确认并开启' }}</el-button>
  </div>
</template>
