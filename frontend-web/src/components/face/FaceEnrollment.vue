<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Lock, Close } from '@element-plus/icons-vue'
import { disableFaceLogin, enrollFace, getFaceProfile } from '@/api/face'
import type { FaceProfile } from '@/api/face'
import { ApiError, MOCK_TOKEN } from '@/utils/request'
import { useUserStore } from '@/stores/user'
import FaceCapture from './FaceCapture.vue'
import FaceScanIcon from './FaceScanIcon.vue'

const userStore = useUserStore()
const profile = ref<FaceProfile>()
const visible = ref(false)
const dismissed = ref(false)
const loading = ref(false)
const busy = ref(false)
const consent = ref(false)
const error = ref('')
const statusError = ref(false)
const complete = ref(false)
const capturedCount = ref(0)
const showPrompt = computed(() => profile.value?.faceLoginAllowed && !profile.value.enrolled && !dismissed.value && !visible.value)
const demo = computed(() => userStore.token === MOCK_TOKEN)

async function loadProfile() {
  if (demo.value) return
  loading.value = true
  statusError.value = false
  try {
    profile.value = await getFaceProfile()
  } catch {
    statusError.value = true
  } finally {
    loading.value = false
  }
}

async function open() {
  consent.value = false
  capturedCount.value = 0
  error.value = ''
  complete.value = false
  visible.value = true
  await loadProfile()
}

async function submit(images: Blob[]) {
  if (busy.value || !consent.value || !profile.value?.faceLoginAllowed) return
  busy.value = true
  error.value = ''
  try {
    const result = await enrollFace(images)
    if (result.acceptedCount > 0 && result.faceCount > 0) {
      profile.value = { ...profile.value, enrolled: true, faceCount: result.faceCount }
      complete.value = true
      ElMessage.success('人脸采集完成，已开通人脸登录')
    } else {
      error.value = result.rejectedImages?.map(item => `第 ${item.index} 张：${item.reason}`).join('；') || '照片未通过校验，请重新采集，保持面部清晰、光线充足。'
    }
  } catch (cause) {
    error.value = cause instanceof ApiError ? (cause.userTip || cause.message) : '采集提交失败，请检查网络后重试。'
  } finally {
    busy.value = false
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
    dismissed.value = true
    complete.value = false
    await loadProfile()
    ElMessage.success('已关闭人脸登录并删除人脸特征')
  } catch (cause) {
    error.value = cause instanceof ApiError ? (cause.userTip || cause.message) : '关闭失败，请稍后重试。'
  } finally {
    busy.value = false
  }
}

function beforeClose(done: () => void) {
  if (!busy.value) done()
}

defineExpose({ open })
onMounted(() => { void loadProfile() })
</script>

<template>
  <Transition name="face-prompt">
    <aside v-if="showPrompt" class="face-prompt" aria-labelledby="face-prompt-title" role="status">
      <span class="face-prompt__icon"><FaceScanIcon /></span>
      <div class="face-prompt__copy"><strong id="face-prompt-title">开启人脸登录，让每次进入工作台更轻松</strong><p>采集两张人脸照片，即可在登录页选择人脸登录。</p></div>
      <el-button type="primary" @click="open">立即采集</el-button>
      <button class="face-prompt__later" type="button" @click="dismissed = true">稍后</button>
      <button class="face-prompt__close" type="button" aria-label="关闭人脸采集提醒" @click="dismissed = true"><el-icon><Close /></el-icon></button>
    </aside>
  </Transition>
  <el-dialog v-model="visible" title="设置人脸登录" width="920px" top="8vh" class="face-enrollment" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" :before-close="beforeClose" destroy-on-close append-to-body>
    <template v-if="visible">
      <p v-if="demo" class="face-enrollment__intro">当前为演示账号。请使用正式工作账号登录后设置人脸登录。</p>
      <p v-else-if="loading" class="face-enrollment__intro" role="status">正在查询人脸登录状态…</p>
      <div v-else-if="statusError" role="alert"><p>人脸登录状态加载失败，请重试。</p><el-button @click="loadProfile">重新加载</el-button></div>
      <div v-else-if="complete || profile?.enrolled" class="face-enrollment__success">
        <span class="face-enrollment__check"><el-icon><Check /></el-icon></span>
        <h3>{{ !profile?.faceLoginAllowed ? '管理已采集的人脸资料' : complete ? '人脸采集完成' : '已开通人脸登录' }}</h3>
        <p>{{ profile?.faceLoginAllowed ? '下次在登录页选择「人脸登录」，验证后即可进入工作台。' : '当前账号使用密码登录，您可以关闭并删除此前采集的人脸特征。' }}</p>
        <span class="face-enrollment__count">已录入 {{ profile?.faceCount }} 张照片</span>
        <el-button type="primary" @click="visible = false">{{ complete ? '完成，返回工作台' : '返回工作台' }}</el-button>
        <button class="face-enrollment__disable" type="button" :disabled="busy" @click="disable">{{ busy ? '正在关闭…' : '关闭人脸登录并删除特征' }}</button>
      </div>
      <p v-else-if="profile && !profile.faceLoginAllowed" class="face-enrollment__intro">当前账号请使用密码登录，无需采集人脸。</p>
      <template v-else-if="profile?.faceLoginAllowed">
        <p class="face-enrollment__intro">只需两张照片，为您的工作账号开启快捷登录。</p>
        <div class="face-enrollment__grid">
          <div class="face-enrollment__camera">
            <FaceCapture mode="enroll" :allowed="consent" :busy="busy" @submit="submit" @reset="error = ''" @progress="capturedCount = $event" />
          </div>
          <div class="face-enrollment__details">
            <h3>采集步骤<span>{{ capturedCount }} / 2</span></h3>
            <ol class="face-enrollment__steps">
              <li :class="{ 'is-done': capturedCount > 0, 'is-current': capturedCount === 0 }"><span class="face-enrollment__step-number"><el-icon v-if="capturedCount > 0"><Check /></el-icon><template v-else>1</template></span><div><strong>正面照片</strong><p>自然直视镜头，保持面部无遮挡</p></div></li>
              <li :class="{ 'is-done': capturedCount > 1, 'is-current': capturedCount === 1 }"><span class="face-enrollment__step-number"><el-icon v-if="capturedCount > 1"><Check /></el-icon><template v-else>2</template></span><div><strong>微侧面照片</strong><p>轻微转头，让面部特征更加完整</p></div></li>
            </ol>
            <div class="face-enrollment__advice"><strong>拍摄前，请留意</strong><p>选择光线充足的位置，保持镜头清洁，确保画面中只有您一人。</p></div>
            <div class="face-enrollment__consent">
              <p><el-icon><Lock /></el-icon>照片用于提取人脸特征、验证本人登录。您可随时在人脸登录设置中关闭并删除特征。</p>
              <el-checkbox v-model="consent" :disabled="busy">我已阅读并同意上述人脸信息采集与处理说明</el-checkbox>
            </div>
          </div>
        </div>
      </template>
      <p v-if="error" class="face-enrollment__error" role="alert">{{ error }}</p>
    </template>
    <template v-if="!complete && !profile?.enrolled" #footer><div class="face-enrollment__footer"><span><el-icon><Lock /></el-icon>当前账号：{{ userStore.userNo }}</span><el-button text :disabled="busy" @click="visible = false">暂不设置</el-button></div></template>
  </el-dialog>
</template>

<style scoped>
.face-prompt { --el-color-primary: #1b704e; --el-color-primary-light-3: #478f70; --el-color-primary-dark-2: #14563c; position: fixed; z-index: 100; top: 76px; left: calc(50% + 110px); transform: translateX(-50%); display: flex; align-items: center; gap: 18px; width: min(740px, calc(100vw - 280px)); padding: 22px 42px 22px 24px; border: 1px solid #dbe7df; border-radius: 16px; background: #fff; box-shadow: 0 10px 32px #193e3114, 0 2px 6px #193e3105; color: #294735; }
.face-prompt__icon { display: grid; place-items: center; flex: 0 0 48px; height: 48px; border-radius: 13px; color: #397a5c; background: #edf5ef; }
.face-prompt__icon svg { width: 29px; height: 29px; }
.face-prompt__copy { flex: 1; }
.face-prompt__copy strong { font-size: 14px; font-weight: 600; line-height: 1.6; }
.face-prompt__copy p { margin: 5px 0 0; font-size: 12px; color: #829286; line-height: 1.6; }
.face-prompt > .el-button { height: 36px; border-radius: 8px; font-size: 12px; }
.face-prompt__later, .face-prompt__close { border: 0; background: none; color: #8b9a8e; font: inherit; font-size: 12px; cursor: pointer; white-space: nowrap; }
.face-prompt__close { position: absolute; right: 7px; top: 7px; padding: 5px; }
.face-prompt button:focus-visible { outline: 2px solid #1b704e; outline-offset: 3px; }
.face-enrollment__intro { color: #819084; font-size: 14px; line-height: 1.8; margin: 4px 0 24px; }
.face-enrollment__grid { display: grid; grid-template-columns: 1.12fr 1fr; gap: 36px; padding-top: 26px; border-top: 1px solid #e8eee9; }
.face-enrollment__camera { min-width: 0; }
.face-enrollment__details h3 { display: flex; align-items: center; justify-content: space-between; margin: 1px 0 22px; color: #2d4a38; font-size: 16px; font-weight: 600; }
.face-enrollment__details h3 > span { font-size: 12px; color: #92a196; font-weight: 400; font-variant-numeric: tabular-nums; }
.face-enrollment__steps { margin: 0; padding: 0; list-style: none; }
.face-enrollment__steps li { position: relative; display: flex; gap: 14px; padding-bottom: 25px; color: #91a094; }
.face-enrollment__steps li:first-child::after { content: ''; position: absolute; top: 35px; bottom: 7px; left: 15px; width: 1px; background: #e1eae2; }
.face-enrollment__step-number { display: grid; place-items: center; flex: 0 0 32px; height: 32px; border: 1px solid #e0e8e1; border-radius: 50%; color: #82988a; font-size: 13px; }
.face-enrollment__steps strong { display: block; margin: 2px 0 7px; color: #738b7b; font-size: 14px; font-weight: 500; }
.face-enrollment__steps p { margin: 0; font-size: 12px; line-height: 1.7; }
.face-enrollment__steps .is-current .face-enrollment__step-number, .face-enrollment__steps .is-done .face-enrollment__step-number { background: #1b704e; color: #fff; border-color: #1b704e; }
.face-enrollment__steps .is-current strong, .face-enrollment__steps .is-done strong { color: #2e533c; }
.face-enrollment__advice { padding: 16px 18px; border-radius: 12px; background: #f4f7f3; }
.face-enrollment__advice strong { font-size: 12px; font-weight: 500; color: #49664f; }
.face-enrollment__advice p { margin: 7px 0 0; font-size: 12px; line-height: 1.8; color: #839489; }
.face-enrollment__consent { margin-top: 18px; padding-top: 16px; border-top: 1px solid #e8eee9; }
.face-enrollment__consent p { margin: 0 0 13px; font-size: 11px; line-height: 1.85; color: #8b998f; }
.face-enrollment__consent .el-icon { vertical-align: -2px; margin-right: 5px; }
.face-enrollment__consent :deep(.el-checkbox) { height: auto; align-items: flex-start; }
.face-enrollment__consent :deep(.el-checkbox__input) { margin-top: 3px; }
.face-enrollment__consent :deep(.el-checkbox__label) { white-space: normal; line-height: 1.8; font-size: 12px; color: #647c69; }
.face-enrollment__footer { display: flex; align-items: center; justify-content: space-between; margin-top: 25px; padding-top: 15px; border-top: 1px solid #e8eee9; }
.face-enrollment__footer > span { display: flex; align-items: center; gap: 6px; font-size: 11px; color: #94a096; }
.face-enrollment__footer .el-button { color: #7c9083; font-size: 12px; }
.face-enrollment__error { color: #a84035; font-size: 13px; line-height: 1.7; padding: 10px 12px; margin-bottom: 0; background: #fff5f3; border-radius: 8px; }
.face-enrollment__success { display: flex; align-items: center; flex-direction: column; padding: 24px 60px 12px; text-align: center; }
.face-enrollment__check { display: grid; place-items: center; width: 76px; height: 76px; margin: 18px 0; border-radius: 50%; background: #1b704e; color: #fff; box-shadow: 0 0 0 14px #f0f6ee, 0 0 0 28px #fafcf9; font-size: 34px; }
.face-enrollment__success h3 { margin: 34px 0 4px; font-size: 25px; font-weight: 600; color: #2b4835; }
.face-enrollment__success p { color: #839386; font-size: 13px; line-height: 1.8; }
.face-enrollment__count { font-size: 12px; color: #a0ada2; margin: 6px 0 28px; }
.face-enrollment__success > .el-button { width: 360px; max-width: 100%; height: 46px; border-radius: 10px; }
.face-enrollment__disable { margin: 20px 0 6px; padding: 4px; border: 0; background: none; color: #94a396; font-size: 11px; cursor: pointer; }
.face-enrollment__disable:hover { color: #5b7663; text-decoration: underline; text-underline-offset: 4px; }
.face-prompt-enter-active, .face-prompt-leave-active { transition: opacity 200ms ease-out, transform 200ms ease-out; }
.face-prompt-enter-from, .face-prompt-leave-to { opacity: 0; transform: translate(-50%, -8px); }
@media (prefers-reduced-motion: reduce) { .face-prompt-enter-active, .face-prompt-leave-active { transition: none; } }
</style>

<style>
.el-dialog.face-enrollment { --el-color-primary: #1b704e; --el-color-primary-light-3: #478f70; --el-color-primary-light-5: #8fbd9f; --el-color-primary-light-7: #c3dccc; --el-color-primary-light-9: #f0f6f1; --el-color-primary-dark-2: #14563c; display: flex; flex-direction: column; width: min(920px, calc(100vw - 64px)); max-height: calc(100svh - 120px); padding: 30px 32px 22px; border: 1px solid #edf1ec; border-radius: 22px; box-shadow: 0 28px 80px #112d2533; }
.face-enrollment .el-dialog__header { padding-bottom: 8px; }
.face-enrollment .el-dialog__title { font-size: 24px; font-weight: 600; letter-spacing: .2px; color: #254231; }
.face-enrollment .el-dialog__body { padding: 0; overflow-y: auto; }
.face-enrollment .el-dialog__headerbtn { top: 14px; right: 14px; }
.face-enrollment .el-dialog__footer { padding-top: 0; }
@media (max-width: 1023px) { .el-dialog.face-enrollment { max-height: calc(100svh - 80px); } }
</style>
