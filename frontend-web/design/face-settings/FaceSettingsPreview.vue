<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElButton, ElCheckbox, ElDialog, ElIcon } from 'element-plus'
import { ArrowDown, ArrowLeft, ArrowRight, Camera, Check, Cpu, Fold, Grid, Odometer, Operation, RefreshLeft, SetUp, User } from '@element-plus/icons-vue'
import examplesUrl from '../../../docs/design/login/face/proposals-20261111/face-pose-examples-v1.png'

// Use the already approved, fictional three-pose asset. No camera or network calls.
const labels = ['正面', '左转头', '右转头']
const instructions = ['请正对镜头', '请向左转头', '请向右转头']
const nav = [
  { title: '工作台', icon: Odometer, href: '/dashboard' },
  { title: '生产管理', icon: Operation, href: '/production/work-orders', group: true },
  { title: '工艺管理', icon: SetUp, href: '/craft/processes', group: true },
  { title: '设备管理', icon: Cpu, href: '/equipment/ledgers', group: true },
  { title: '组件示例', icon: Grid, href: '/demo/table', group: true },
]
const collapsed = ref(false)
const consent = ref(true)
const explanationOpen = ref(false)
const activeStep = ref(1)
const complete = computed(() => activeStep.value === labels.length)
const pose = computed(() => Math.min(activeStep.value, labels.length - 1))
const liveText = computed(() => complete.value ? '人脸采集完成' : instructions[activeStep.value])

function capture() {
  if (!consent.value || complete.value) return
  activeStep.value += 1
}

function restart() {
  activeStep.value = 0
}

function retake() {
  activeStep.value = Math.max(0, activeStep.value - 1)
}
</script>

<template>
  <div class="face-review" :class="{ 'is-collapsed': collapsed }">
    <aside class="mes-sidebar" aria-label="主导航">
      <a class="mes-brand" href="/dashboard">{{ collapsed ? 'MES' : '羽毛球 MES' }}</a>
      <nav>
        <a v-for="item in nav" :key="item.title" :href="item.href" :title="collapsed ? item.title : undefined">
          <ElIcon :size="18"><component :is="item.icon" /></ElIcon>
          <span v-if="!collapsed">{{ item.title }}</span>
          <ElIcon v-if="!collapsed && item.group" class="nav-chevron" :size="12"><ArrowDown /></ElIcon>
        </a>
      </nav>
    </aside>

    <div class="mes-workspace">
      <header class="mes-topbar">
        <div class="topbar-context">
          <button class="icon-button" type="button" :aria-label="collapsed ? '展开侧栏' : '收起侧栏'" :aria-expanded="!collapsed" @click="collapsed = !collapsed"><ElIcon :size="19"><Fold /></ElIcon></button>
          <div class="breadcrumb"><span>个人设置</span><ElIcon :size="12"><ArrowRight /></ElIcon><span>人脸登录</span></div>
        </div>
        <div class="topbar-user"><span class="user-avatar"><ElIcon :size="17"><User /></ElIcon></span><span>操作员</span><ElIcon :size="12"><ArrowDown /></ElIcon></div>
      </header>
      <div class="mes-tags" aria-label="已打开页面"><a href="/dashboard">工作台</a><span class="active-tag" aria-current="page">个人设置</span></div>

      <main class="settings-page">
        <div class="page-heading"><h1>人脸采集</h1><a class="back-link" href="/dashboard"><ElIcon :size="15"><ArrowLeft /></ElIcon>返回工作台</a></div>

        <section class="capture-card" aria-label="人脸采集">
          <div class="capture-body">
            <div class="camera-column">
              <h2 id="capture-instruction" role="status" aria-live="polite">{{ liveText }}</h2>
              <div class="camera-frame" :class="{ 'is-complete': complete }" aria-labelledby="capture-instruction">
                <div v-if="!complete" class="portrait-window" aria-hidden="true"><img :src="examplesUrl" :style="{ left: `${pose * -100}%` }" alt="" width="2172" height="724" fetchpriority="high" /></div>
                <!-- Reuse the contour from the project's FaceCapture.vue. -->
                <svg v-if="!complete" class="face-contour" viewBox="0 0 200 240" aria-hidden="true"><path d="M100 10C66 10 45 28 42 62L40 91C34 84 24 88 25 105L28 128C30 139 36 142 42 139C47 170 56 192 74 207C84 218 91 222 100 222C109 222 116 218 126 207C144 192 153 170 158 139C164 142 170 139 172 128L175 105C176 88 166 84 160 91L158 62C155 28 134 10 100 10Z" /></svg>
                <div v-if="complete" class="completion"><span class="completion-check"><ElIcon :size="30"><Check /></ElIcon></span><strong>已完成三张照片采集</strong><span>下次登录可选择人脸登录</span></div>
              </div>

              <div class="pose-examples" aria-label="拍摄姿态示例">
                <figure v-for="(label, index) in labels" :key="label" :class="{ 'is-current': !complete && activeStep === index }">
                  <div class="example-portrait"><img :src="examplesUrl" :style="{ left: `${index * -100}%` }" :alt="`${label}示例`" width="2172" height="724" /></div>
                  <figcaption>{{ label }}</figcaption>
                </figure>
              </div>
            </div>

            <ol class="capture-progress" aria-label="采集进度">
              <li v-for="(label, index) in labels" :key="label" :class="{ 'is-done': activeStep > index, 'is-active': activeStep === index }" :aria-current="activeStep === index ? 'step' : undefined">
                <span class="step-marker"><ElIcon v-if="activeStep > index" :size="15"><Check /></ElIcon><span v-else>{{ index + 1 }}</span></span>
                <span class="step-label">{{ label }}</span>
              </li>
            </ol>
          </div>

          <footer class="capture-footer">
            <div class="consent-row"><ElCheckbox v-model="consent" :disabled="complete">同意用于人脸登录</ElCheckbox><button class="explanation-link" type="button" @click="explanationOpen = true">查看说明</button></div>
            <div class="capture-actions">
              <ElButton v-if="!complete" :disabled="activeStep === 0" :icon="RefreshLeft" @click="retake">重拍</ElButton>
              <ElButton v-if="!complete" type="primary" :icon="Camera" :disabled="!consent" @click="capture">拍摄照片</ElButton>
              <ElButton v-else :icon="RefreshLeft" @click="restart">重新采集</ElButton>
            </div>
          </footer>
        </section>
      </main>
    </div>

    <ElDialog v-model="explanationOpen" title="人脸登录说明" width="440px" align-center :close-on-click-modal="false" class="face-consent-dialog" modal-class="face-review-overlay">
      <p>采集正面、左转头和右转头三张照片，用于当前账号的人脸登录。请保持光线充足，确保画面中只有您一人。</p>
      <p>您可以在个人设置中关闭人脸登录，并删除已采集的人脸特征。</p>
      <template #header><div class="consent-dialog-heading"><strong>人脸登录说明</strong></div></template>
      <template #footer><ElButton type="primary" @click="explanationOpen = false">我知道了</ElButton></template>
    </ElDialog>
  </div>
</template>

<style scoped>
.face-review {
  --review-sidebar: 220px;
  --review-primary: #3377c9;
  --el-color-primary: var(--review-primary);
  --el-color-primary-light-3: #709fd9;
  --el-color-primary-light-5: #99bbe4;
  --el-color-primary-light-7: #c2d6ef;
  --el-color-primary-light-9: #edf3fa;
  --el-color-primary-dark-2: #285fa3;
  --el-border-radius-base: 4px;
  display: grid;
  grid-template-columns: var(--review-sidebar) minmax(0, 1fr);
  min-height: 100vh;
  background: #fff;
  color: #303133;
}
.is-collapsed { --review-sidebar: 64px; }
.mes-sidebar { background: #001529; }
.mes-brand { display: flex; align-items: center; justify-content: center; height: 56px; font-size: 16px; font-weight: 600; color: white; text-decoration: none; }
.mes-sidebar nav { padding-top: 16px; }
.mes-sidebar nav a { display: flex; align-items: center; gap: 12px; min-height: 52px; padding: 0 24px; color: #c3ccd5; font-size: 14px; text-decoration: none; }
.mes-sidebar nav a:hover { background: rgb(255 255 255 / 8%); color: white; }
.is-collapsed .mes-sidebar nav a { justify-content: center; padding: 0; }
.nav-chevron { margin-left: auto; }
.mes-workspace { min-width: 0; }
.mes-topbar { height: 56px; padding: 0 22px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #ebeef5; }
.topbar-context, .breadcrumb, .topbar-user { display: flex; align-items: center; }
.topbar-context { gap: 17px; }
.breadcrumb { gap: 12px; font-size: 14px; }
.breadcrumb > :first-child { color: #777f89; }
.breadcrumb .el-icon { color: #b8bec8; }
.topbar-user { gap: 9px; font-size: 13px; }
.user-avatar { display: grid; place-items: center; height: 28px; width: 28px; background: #edf3fa; border-radius: 50%; color: #3377c9; }
.topbar-user > .el-icon { margin-left: 6px; color: #8c939d; }
.icon-button { display: grid; place-items: center; padding: 4px; border: 0; background: transparent; color: #535c68; cursor: pointer; }
.mes-tags { display: flex; align-items: center; gap: 8px; height: 40px; padding: 0 20px; border-bottom: 1px solid #ebeef5; background: #fff; font-size: 12px; }
.mes-tags a, .mes-tags > span { padding: 4px 12px; border: 1px solid #e5e7eb; border-radius: 3px; text-decoration: none; color: #606266; }
.mes-tags .active-tag { color: #3377c9; border-color: #cbdcf1; background: #f0f5fc; }
.settings-page { max-width: 1000px; padding: 30px 40px 48px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-heading h1 { margin: 0; font-size: 22px; font-weight: 600; letter-spacing: .2px; line-height: 32px; }
.back-link { display: flex; align-items: center; gap: 6px; color: #66717f; font-size: 13px; text-decoration: none; }
.back-link:hover { color: var(--review-primary); }
.capture-card { overflow: hidden; border: 1px solid #e2e6ec; border-radius: 6px; background: white; }
.capture-body { display: grid; grid-template-columns: minmax(0, 1fr) 148px; gap: 36px; padding: 26px 30px 22px; }
.camera-column { min-width: 0; }
.camera-column h2 { margin: 0 0 18px; font-weight: 500; font-size: 17px; line-height: 24px; }
.camera-frame { display: grid; place-items: center; position: relative; overflow: hidden; height: 332px; border: 1px solid #e4e8ed; border-radius: 4px; background: #f1f3f5; }
.portrait-window { position: absolute; width: 342px; height: 342px; overflow: hidden; bottom: -12px; mix-blend-mode: multiply; }
.portrait-window img, .example-portrait img { display: block; position: absolute; top: 0; width: 300%; max-width: none; height: 100%; }
.face-contour { position: absolute; top: 7px; width: 238px; height: 276px; fill: none; stroke: #54738b; stroke-width: 1.15; stroke-dasharray: 5 5; opacity: .44; }
.pose-examples { display: flex; justify-content: center; gap: 34px; margin-top: 22px; }
.pose-examples figure { margin: 0; width: 86px; }
.example-portrait { position: relative; height: 74px; overflow: hidden; background: #f6f7f9; border-radius: 3px; }
.example-portrait img { height: auto; mix-blend-mode: multiply; }
.pose-examples figcaption { padding-top: 8px; text-align: center; font-size: 12px; color: #687280; line-height: 18px; }
.pose-examples .is-current figcaption { color: #3377c9; font-weight: 500; }
.capture-progress { align-self: start; padding: 0; margin: 89px 0 0; list-style: none; }
.capture-progress li { position: relative; display: flex; align-items: flex-start; gap: 13px; min-height: 107px; padding: 0 0 28px; }
.capture-progress li:last-child { min-height: 0; padding-bottom: 0; }
.capture-progress li:not(:last-child)::after { position: absolute; content: ''; left: 13px; top: 36px; bottom: 8px; width: 1px; background: #e2e6ec; }
.capture-progress li.is-done::after { background: #bfd3e9; }
.step-marker { display: grid; place-items: center; flex: 0 0 28px; height: 28px; border: 1px solid #dce1e8; border-radius: 50%; color: #9aa2ae; font-size: 12px; background: #fff; }
.is-done .step-marker { border-color: #d3e4dd; background: #f0f7f4; color: #318464; }
.is-active .step-marker { border-color: #3377c9; background: #3377c9; color: white; }
.step-label { display: block; margin-top: 3px; color: #6e7784; line-height: 22px; font-size: 14px; }
.is-active .step-label { color: #303133; font-weight: 600; }
.is-done .step-label { color: #606a78; }
.capture-footer { display: flex; align-items: center; justify-content: space-between; min-height: 79px; padding: 18px 30px; border-top: 1px solid #ebeef5; }
.consent-row { display: flex; align-items: center; gap: 15px; }
.consent-row :deep(.el-checkbox__label) { font-size: 13px; color: #606a78; }
.explanation-link { padding: 4px 0; border: 0; background: none; color: #727d8b; font-size: 12px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }
.explanation-link:hover { color: var(--review-primary); }
.capture-actions { display: flex; align-items: center; gap: 10px; }
.capture-actions .el-button { height: 36px; margin: 0; padding: 0 17px; font-size: 13px; }
.capture-actions .el-button--primary { min-width: 123px; }
.completion { display: grid; justify-items: center; gap: 14px; }
.completion-check { display: grid; place-items: center; width: 60px; height: 60px; background: #e8f3ed; color: #318464; border-radius: 50%; }
.completion strong { margin-top: 2px; font-size: 16px; font-weight: 500; }
.completion > span:last-child { font-size: 13px; color: #7d8793; }
.is-complete { background: #f7f9f8; }
:global(.face-consent-dialog p) { line-height: 1.8; margin: 0 0 16px; }
.consent-dialog-heading { padding-right: 20px; font-size: 17px; }
button:focus-visible, a:focus-visible { outline: 2px solid #3377c9; outline-offset: 4px; }
@media (hover: hover) and (pointer: fine) {
  .back-link, .explanation-link, .mes-sidebar nav a { transition: color 140ms ease; }
}
@media (min-width: 1100px) and (max-height: 820px) {
  .settings-page { padding-top: 22px; padding-bottom: 24px; }
  .page-heading { margin-bottom: 20px; }
  .capture-body { padding: 22px 28px 18px; }
  .camera-column h2 { margin-bottom: 14px; }
  .camera-frame { height: 284px; }
  .portrait-window { width: 294px; height: 294px; bottom: -10px; }
  .face-contour { top: 4px; width: 206px; height: 238px; }
  .pose-examples { margin-top: 18px; gap: 28px; }
  .pose-examples figure { width: 76px; }
  .example-portrait { height: 64px; }
  .capture-progress { margin-top: 73px; }
  .capture-progress li { min-height: 92px; }
  .capture-progress li:last-child { min-height: 0; }
  .capture-footer { min-height: 72px; padding: 16px 28px; }
}
@media (prefers-reduced-motion: reduce) {
  .face-review *, .face-review *::before, .face-review *::after { animation: none; transition: none; }
  :global(.face-review-overlay), :global(.face-review-overlay *) { animation: none; transition: none; }
}
</style>
