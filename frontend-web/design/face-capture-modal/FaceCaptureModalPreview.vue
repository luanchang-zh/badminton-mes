<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElCheckbox, ElConfigProvider, ElDialog, ElIcon, ElPopover } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ArrowDown, ArrowLeft, ArrowRight, Camera, Check, CircleCheck, Cpu, Fold, Grid, InfoFilled, Lock, Odometer, Operation, RefreshLeft, SetUp, User } from '@element-plus/icons-vue'
import FaceScanIcon from '../../src/components/face/FaceScanIcon.vue'
import examplesUrl from '../../../docs/design/login/face/proposals-20261111/face-pose-examples-v1.png'

// This isolated design entry uses the existing fictional portrait as a camera fixture.
// Starting the production camera and submitting enrollment remain a later integration.
type Phase = 'intro' | 'capture' | 'review' | 'success'
const preset = new URLSearchParams(location.search).get('preview')
const visible = ref(true)
const phase = ref<Phase>(preset === 'capture' ? 'capture' : 'intro')
const consent = ref(preset === 'capture')
const activeStep = ref(0)
const captured = ref([false, false, false])
const enabled = ref(false)
const exampleOpen = ref(false)
const showCameraHelp = ref(false)
const poses = [
  { title: '正面', action: '请正对镜头', hint: '将脸部完整放入取景框，目光看向镜头。' },
  { title: '左转头', action: '请微微向左转头', hint: '肩膀保持不动，像示例一样轻轻转头。' },
  { title: '右转头', action: '请微微向右转头', hint: '保持面部清晰，转动角度无需太大。' },
]
const currentPose = computed(() => poses[activeStep.value]!)
const modalTitle = computed(() => phase.value === 'review' ? '确认采集照片' : phase.value === 'success' ? '设置完成' : '设置人脸登录')
const navigation = [
  { label: '工作台', icon: Odometer, href: '/dashboard' },
  { label: '生产管理', icon: Operation, href: '/production/work-orders' },
  { label: '工艺管理', icon: SetUp, href: '/craft/processes' },
  { label: '设备管理', icon: Cpu, href: '/equipment/ledgers' },
  { label: '组件示例', icon: Grid, href: '/demo/table' },
]

function openEnrollment() {
  phase.value = 'intro'
  consent.value = false
  captured.value = [false, false, false]
  activeStep.value = 0
  visible.value = true
}

function begin() {
  if (!consent.value) return
  phase.value = 'capture'
}

function takePhoto() {
  if (phase.value !== 'capture' || !consent.value || showCameraHelp.value) return
  captured.value[activeStep.value] = true
  const next = captured.value.findIndex(value => !value)
  if (next === -1) phase.value = 'review'
  else activeStep.value = next
  exampleOpen.value = false
}

function retake(index: number) {
  captured.value[index] = false
  activeStep.value = index
  phase.value = 'capture'
}

function previous() {
  if (activeStep.value === 0) phase.value = 'intro'
  else retake(activeStep.value - 1)
  exampleOpen.value = false
}

function confirm() {
  if (!captured.value.every(Boolean) || !consent.value) return
  enabled.value = true
  phase.value = 'success'
}

function closeHelp() {
  showCameraHelp.value = false
}

function clearTransientState() {
  captured.value = [false, false, false]
  consent.value = false
  exampleOpen.value = false
  showCameraHelp.value = false
}
</script>

<template>
  <ElConfigProvider :locale="zhCn">
  <div class="enrollment-preview">
    <aside class="workspace-sidebar" aria-label="主导航">
      <a class="workspace-brand" href="/dashboard">羽毛球 MES</a>
      <nav><a v-for="item in navigation" :key="item.label" :href="item.href"><ElIcon :size="18"><component :is="item.icon" /></ElIcon><span>{{ item.label }}</span><ElIcon v-if="item.label !== '工作台'" class="menu-arrow" :size="11"><ArrowDown /></ElIcon></a></nav>
    </aside>
    <div class="workspace-content">
      <header class="workspace-header"><div><ElIcon :size="18"><Fold /></ElIcon><span>个人设置</span></div><div class="workspace-user"><span class="user-avatar"><ElIcon :size="16"><User /></ElIcon></span>操作员<ElIcon :size="11"><ArrowDown /></ElIcon></div></header>
      <main class="account-page">
        <h1>个人设置</h1>
        <div class="account-layout">
          <nav class="account-navigation" aria-label="个人设置"><span>账户资料</span><span class="is-selected" aria-current="page">登录与安全</span></nav>
          <section class="security-settings" aria-labelledby="security-heading">
            <header><h2 id="security-heading">登录方式</h2><p>选择适合自己的方式，安全进入工作台。</p></header>
            <div class="security-row"><span class="security-icon"><ElIcon :size="23"><Lock /></ElIcon></span><div><h3>工号与密码</h3><p>使用工作账号和密码登录系统</p></div><span class="password-status">已设置</span></div>
            <div class="security-row"><span class="security-icon"><FaceScanIcon /></span><div><h3>人脸登录 <span v-if="enabled" class="enabled-status">已开启</span></h3><p>验证人脸，快捷进入工作台</p></div><ElButton @click="openEnrollment">{{ enabled ? '重新采集' : '开启人脸登录' }}</ElButton></div>
          </section>
        </div>
      </main>
    </div>

    <ElDialog v-model="visible" :title="modalTitle" width="620px" align-center class="enroll-dialog" modal-class="enroll-overlay" :close-on-click-modal="false" destroy-on-close @closed="clearTransientState">
      <template #header><h2 class="dialog-heading">{{ modalTitle }}</h2></template>

      <div v-if="phase === 'intro'" class="intro-view">
        <div class="intro-mark"><FaceScanIcon /></div>
        <h3>让下一次登录更轻松</h3>
        <p class="intro-description">采集三个角度的照片，即可使用人脸登录。</p>
        <div class="intro-poses" aria-label="需要采集的三个角度">
          <figure v-for="(pose, index) in poses" :key="pose.title"><div class="pose-photo"><img :src="examplesUrl" :style="{ left: `${index * -100}%` }" :alt="`${pose.title}示例`" width="2172" height="724" /></div><figcaption>{{ pose.title }}</figcaption></figure>
        </div>
        <p class="preparation-note">保持光线充足，摘下遮挡面部的物品。</p>
        <div class="intro-consent"><ElCheckbox v-model="consent">同意采集人脸信息，用于本账号登录</ElCheckbox></div>
        <ElButton type="primary" class="primary-action start-action" :disabled="!consent" @click="begin">开始采集<ElIcon class="button-next"><ArrowRight /></ElIcon></ElButton>
        <button class="quiet-action" type="button" @click="visible = false">暂不开启</button>
      </div>

      <div v-else-if="phase === 'capture'" class="capture-view">
        <ol class="pose-progress" aria-label="采集进度">
          <li v-for="(pose, index) in poses" :key="pose.title" :class="{ 'is-active': activeStep === index, 'is-finished': captured[index] }" :aria-current="activeStep === index ? 'step' : undefined"><span class="pose-progress-label"><ElIcon v-if="captured[index]" :size="13"><Check /></ElIcon>{{ pose.title }}</span><span class="pose-progress-track" /></li>
        </ol>
        <div class="capture-stage">
          <div v-if="showCameraHelp" class="camera-help" role="alert"><ElIcon :size="32"><Camera /></ElIcon><strong>检查摄像头访问权限</strong><p>请允许浏览器使用摄像头，<br />并确认摄像头未被其他应用占用。</p><ElButton @click="closeHelp">重新尝试</ElButton></div>
          <template v-else>
            <div class="camera-oval"><div class="camera-portrait"><img :src="examplesUrl" :style="{ left: `${activeStep * -100}%` }" alt="当前姿态预览" width="2172" height="724" /></div></div>
            <span class="alignment-guide" aria-hidden="true" />
          </template>
        </div>
        <div class="capture-direction" aria-live="polite"><h3>{{ currentPose.action }}</h3><p>{{ currentPose.hint }}</p></div>
        <ElPopover v-model:visible="exampleOpen" placement="right" :width="180" trigger="click" popper-class="pose-example-popover">
          <div class="example-popover-content"><div class="pose-photo"><img :src="examplesUrl" :style="{ left: `${activeStep * -100}%` }" :alt="`${currentPose.title}示例`" width="2172" height="724" /></div><span>{{ currentPose.title }}示例</span></div>
          <template #reference><button class="pose-example-link" type="button"><ElIcon :size="13"><InfoFilled /></ElIcon>查看姿态示例</button></template>
        </ElPopover>
        <div class="capture-controls"><ElButton class="previous-action" :icon="ArrowLeft" @click="previous">上一步</ElButton><ElButton type="primary" class="primary-action" :icon="Camera" :disabled="showCameraHelp" @click="takePhoto">拍摄{{ currentPose.title }}</ElButton><button class="camera-troubleshoot" type="button" aria-label="摄像头使用帮助" @click="showCameraHelp = !showCameraHelp"><ElIcon :size="18"><InfoFilled /></ElIcon></button></div>
      </div>

      <div v-else-if="phase === 'review'" class="review-view">
        <div class="review-heading"><span class="review-check"><ElIcon :size="22"><CircleCheck /></ElIcon></span><h3>三个角度，采集完成</h3><p>确认照片清晰、面部无遮挡后，即可开启。</p></div>
        <div class="review-photos"><figure v-for="(pose, index) in poses" :key="pose.title"><div class="pose-photo"><img :src="examplesUrl" :style="{ left: `${index * -100}%` }" :alt="`已采集${pose.title}照片`" width="2172" height="724" /></div><figcaption><span>{{ pose.title }}</span><button type="button" :aria-label="`重拍${pose.title}`" @click="retake(index)"><ElIcon :size="13"><RefreshLeft /></ElIcon>重拍</button></figcaption></figure></div>
        <ElButton type="primary" class="primary-action review-confirm" @click="confirm">确认并开启</ElButton>
      </div>

      <div v-else class="success-view" role="status"><span class="success-mark"><ElIcon :size="36"><Check /></ElIcon></span><h3>人脸登录已开启</h3><p>下次登录时，选择「人脸登录」即可。</p><ElButton type="primary" class="primary-action" @click="visible = false">完成</ElButton></div>

      <div class="privacy-note"><ElIcon :size="13"><Lock /></ElIcon><span>仅用于本账号登录，可在个人设置中随时关闭</span></div>
    </ElDialog>
  </div>
  </ElConfigProvider>
</template>

<style scoped>
.enrollment-preview { display: grid; grid-template-columns: 220px minmax(0, 1fr); min-height: 100vh; background: #f5f7fa; color: #243141; }
.workspace-sidebar { background: #001529; }
.workspace-brand { display: grid; place-items: center; height: 56px; font-size: 16px; font-weight: 600; color: #fff; text-decoration: none; }
.workspace-sidebar nav { padding-top: 16px; }
.workspace-sidebar nav a { min-height: 52px; padding: 0 24px; display: flex; align-items: center; gap: 12px; font-size: 14px; text-decoration: none; color: #c3ccd5; }
.workspace-sidebar nav a:hover { color: white; background: rgb(255 255 255 / 8%); }
.menu-arrow { margin-left: auto; }
.workspace-content { min-width: 0; }
.workspace-header { display: flex; align-items: center; justify-content: space-between; height: 56px; padding: 0 24px; border-bottom: 1px solid #e7ebf0; background: #fff; }
.workspace-header > div { display: flex; align-items: center; gap: 18px; font-size: 14px; }
.workspace-header .workspace-user { gap: 10px; font-size: 13px; }
.user-avatar { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; background: #eef3f9; color: #376ca7; }
.account-page { max-width: 1140px; margin: 0 auto; padding: 36px 44px; }
.account-page h1 { margin: 0 0 32px; font-size: 24px; font-weight: 600; }
.account-layout { display: grid; grid-template-columns: 158px minmax(0, 1fr); gap: 26px; }
.account-navigation { display: flex; flex-direction: column; gap: 6px; font-size: 14px; }
.account-navigation span { padding: 12px 16px; color: #6c7785; }
.account-navigation .is-selected { color: #285a9b; font-weight: 500; background: #e8eef6; border-radius: 6px; }
.security-settings { padding: 26px 30px 4px; background: white; border: 1px solid #e4e9ef; border-radius: 8px; }
.security-settings h2 { margin: 0 0 8px; font-size: 18px; font-weight: 600; }
.security-settings header p { margin: 0 0 18px; color: #778392; font-size: 13px; }
.security-row { display: flex; align-items: center; gap: 18px; padding: 26px 0; border-top: 1px solid #edf0f4; }
.security-row > div { flex: 1; }
.security-icon { display: grid; place-items: center; color: #56687e; }
.security-icon > svg { width: 25px; height: 25px; }
.security-row h3 { margin: 0 0 7px; font-size: 14px; font-weight: 500; }
.security-row p { margin: 0; color: #778392; font-size: 12px; }
.password-status { color: #687585; font-size: 12px; }
.enabled-status { margin-left: 8px; color: #2a7b60; font-size: 12px; font-weight: 400; }

/* A scoped, independent dialog skin. Element Plus provides focus management. */
:global(.enroll-overlay) { background: rgb(15 26 40 / 48%); }
:global(.enroll-dialog) {
  --el-color-primary: #2866b7;
  --el-color-primary-light-3: #6994cd;
  --el-color-primary-light-5: #94b3db;
  --el-color-primary-light-7: #bfd2e9;
  --el-color-primary-light-9: #eaf0f8;
  --el-color-primary-dark-2: #205292;
  --el-border-radius-base: 6px;
  max-width: calc(100vw - 80px);
  margin: 28px auto;
  padding: 0;
  overflow: hidden;
  border-radius: 14px;
  box-shadow: 0 24px 80px rgb(8 23 43 / 20%);
  color: #233246;
}
:global(.enroll-dialog .el-dialog__header) { padding: 27px 32px 22px; margin: 0; }
:global(.enroll-dialog .el-dialog__headerbtn) { top: 19px; right: 18px; width: 36px; height: 36px; font-size: 19px; }
:global(.enroll-dialog .el-dialog__body) { padding: 0; color: #233246; }
.dialog-heading { margin: 0; font-size: 19px; font-weight: 600; line-height: 28px; letter-spacing: .2px; }
.intro-view { padding: 12px 48px 0; text-align: center; }
.intro-mark { display: grid; place-items: center; margin: 0 auto 22px; color: #2866b7; }
.intro-mark > svg { width: 46px; height: 46px; }
.intro-view h3 { margin: 0 0 12px; color: #233246; font-size: 25px; line-height: 36px; font-weight: 600; letter-spacing: -.5px; }
.intro-description { margin: 0; color: #6e7b8a; font-size: 14px; line-height: 24px; }
.intro-poses { display: flex; justify-content: center; gap: 18px; margin: 28px 0 20px; }
.intro-poses figure { width: 116px; margin: 0; }
.pose-photo { position: relative; overflow: hidden; background: #f1f4f7; }
.pose-photo img { position: absolute; top: 0; width: 300%; max-width: none; height: auto; display: block; mix-blend-mode: multiply; }
.intro-poses .pose-photo { height: 112px; border-radius: 9px; }
.intro-poses figcaption { padding-top: 10px; font-size: 13px; color: #697789; }
.preparation-note { margin: 0 0 22px; font-size: 13px; color: #6e7b8a; line-height: 22px; }
.intro-consent { padding-top: 17px; border-top: 1px solid #e9edf2; }
.intro-consent :deep(.el-checkbox__label) { color: #5c6c80; font-size: 13px; }
.primary-action { height: 44px; min-width: 218px; padding: 0 24px; font-size: 14px; font-weight: 500; }
.start-action { width: 100%; margin-top: 15px; }
.button-next { margin-left: 12px; }
.quiet-action { display: block; margin: 15px auto 0; padding: 4px 12px; border: 0; color: #6d7c8e; background: none; font-size: 13px; cursor: pointer; }
.capture-view { padding: 0 32px; text-align: center; }
.pose-progress { display: flex; gap: 12px; padding: 0; margin: 0 0 24px; list-style: none; text-align: left; }
.pose-progress li { flex: 1; }
.pose-progress-label { display: flex; align-items: center; gap: 6px; color: #67778b; font-size: 13px; line-height: 20px; }
.pose-progress-track { display: block; margin-top: 8px; height: 3px; border-radius: 2px; background: #e6ebf1; }
.pose-progress .is-active .pose-progress-label { color: #2866b7; font-weight: 600; }
.pose-progress .is-active .pose-progress-track { background: #2866b7; }
.pose-progress .is-finished .pose-progress-label { color: #357e68; }
.pose-progress .is-finished .pose-progress-track { background: #b5cfc4; }
.capture-stage { height: 316px; position: relative; display: grid; place-items: center; overflow: hidden; border-radius: 10px; background: #f2f5f8; }
.camera-oval { position: relative; width: 224px; height: 274px; overflow: hidden; border-radius: 49%; background: #eef1f4; }
.camera-portrait { position: absolute; top: -5px; left: -43px; width: 310px; height: 310px; mix-blend-mode: multiply; }
.camera-portrait img { position: absolute; top: 0; width: 300%; height: 100%; max-width: none; }
.alignment-guide { position: absolute; width: 238px; height: 288px; border: 1.5px solid #8caaca; border-radius: 49%; pointer-events: none; }
.capture-direction { padding: 23px 0 0; }
.capture-direction h3 { margin: 0; font-size: 24px; line-height: 34px; font-weight: 600; letter-spacing: -.3px; }
.capture-direction p { margin: 8px 0 0; color: #65758b; font-size: 14px; line-height: 24px; }
.pose-example-link { display: inline-flex; align-items: center; gap: 6px; padding: 4px 0; margin-top: 7px; color: #536e8e; background: transparent; border: 0; font-size: 12px; cursor: pointer; }
.capture-controls { position: relative; display: flex; justify-content: center; gap: 12px; margin-top: 22px; }
.capture-controls .el-button { margin: 0; }
.previous-action { height: 44px; border-color: transparent; color: #687b91; background: transparent; font-size: 13px; }
.previous-action:hover { background: #f5f7fa; }
.camera-troubleshoot { position: absolute; right: 0; top: 8px; width: 28px; height: 28px; border: 0; display: grid; place-items: center; color: #8a97a6; background: transparent; cursor: pointer; }
.camera-help { display: grid; justify-items: center; gap: 14px; color: #758397; }
.camera-help strong { color: #33465e; font-size: 16px; font-weight: 500; }
.camera-help p { margin: 0 0 5px; font-size: 13px; line-height: 23px; }
.example-popover-content { padding: 4px; text-align: center; }
.example-popover-content .pose-photo { width: 130px; height: 130px; margin: 0 auto 10px; border-radius: 6px; }
.example-popover-content > span { font-size: 12px; color: #627389; }
.review-view { padding: 12px 32px 8px; }
.review-heading { text-align: center; }
.review-check { display: inline-grid; place-items: center; color: #357e68; margin-bottom: 16px; }
.review-heading h3 { margin: 0 0 10px; font-size: 24px; font-weight: 600; line-height: 34px; }
.review-heading p { margin: 0; font-size: 13px; line-height: 22px; color: #718095; }
.review-photos { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 14px; margin: 32px 0; }
.review-photos figure { margin: 0; }
.review-photos .pose-photo { aspect-ratio: 1; border-radius: 8px; }
.review-photos figcaption { display: flex; justify-content: space-between; align-items: center; padding-top: 13px; font-size: 13px; color: #52647c; }
.review-photos figcaption button { display: flex; align-items: center; gap: 4px; padding: 3px 0; color: #6d819a; border: 0; background: none; font-size: 12px; cursor: pointer; }
.review-confirm { display: flex; width: 100%; }
.success-view { display: grid; justify-items: center; padding: 32px 32px 22px; text-align: center; }
.success-mark { display: grid; place-items: center; width: 80px; height: 80px; border-radius: 50%; color: #2e7a5f; background: #edf6f0; margin-bottom: 25px; }
.success-view h3 { margin: 0 0 12px; font-size: 25px; font-weight: 600; line-height: 36px; }
.success-view p { margin: 0 0 32px; color: #6e7b8a; font-size: 14px; line-height: 24px; }
.privacy-note { display: flex; align-items: center; justify-content: center; gap: 6px; color: #65758a; font-size: 12px; line-height: 18px; padding: 24px 24px 23px; }
.quiet-action:hover, .pose-example-link:hover, .camera-troubleshoot:hover { color: #2866b7; }
button:focus-visible, a:focus-visible { outline: 2px solid #2866b7; outline-offset: 4px; }
@media (min-width: 1100px) and (max-height: 820px) {
  :global(.enroll-dialog .el-dialog__header) { padding-top: 21px; padding-bottom: 18px; }
  :global(.enroll-dialog .el-dialog__headerbtn) { top: 13px; }
  .pose-progress { margin-bottom: 18px; }
  .capture-stage { height: 266px; }
  .camera-oval { width: 190px; height: 232px; }
  .camera-portrait { width: 264px; height: 264px; left: -37px; }
  .alignment-guide { width: 204px; height: 246px; }
  .capture-direction { padding-top: 18px; }
  .capture-controls { margin-top: 15px; }
  .privacy-note { padding-top: 19px; padding-bottom: 19px; }
  .intro-mark { margin-bottom: 16px; }
  .intro-poses { margin-top: 22px; margin-bottom: 16px; }
  .preparation-note { margin-bottom: 17px; }
}
@media (prefers-reduced-motion: reduce) {
  .enrollment-preview * { animation: none; transition: none; }
  :global(.enroll-overlay), :global(.enroll-overlay *), :global(.pose-example-popover), :global(.pose-example-popover *) { animation: none; transition: none; }
  :global(.enroll-overlay .el-overlay-dialog) { animation: none; transition: none; }
}
</style>
