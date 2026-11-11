<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Grid, DocumentCopy, Service, User, Lock, Right, CirclePlus, Hide, View } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import LoginIntro from './LoginIntro.vue'
import ShuttleMark from './ShuttleMark.vue'
import background from '@/assets/images/login-reference-background.webp'
import RegisterForm from './RegisterForm.vue'
import FaceCapture from '@/components/face/FaceCapture.vue'
import type { FaceLoginRequestOptions } from '@/api/face'
import FaceScanIcon from '@/components/face/FaceScanIcon.vue'

defineOptions({ name: 'LoginView' })

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const showRegistration = ref(false)
const passwordVisible = ref(false)
const loginMode = ref<'password' | 'face'>('password')

function finishLogin() {
  ElMessage.success(`欢迎，${userStore.userName}`)
  const redirect = route.query.redirect
  router.push(typeof redirect === 'string' ? redirect : '/')
}

async function handleFaceLogin(image: Blob, options: FaceLoginRequestOptions) {
  await userStore.loginWithFace(image, options)
  options.signal?.throwIfAborted()
  finishLogin()
}

const form = reactive({
  userNo: '',
  password: '',
})

const rules: FormRules = {
  userNo: [{ required: true, message: '请输入工号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (loading.value) return
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  if (loading.value) return
  loading.value = true
  try {
    await userStore.login({ userNo: form.userNo, password: form.password })
    finishLogin()
  } catch {
    // 错误提示由 request 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

async function handleRegistered(userNo: string, password: string) {
  form.userNo = userNo
  form.password = ''
  passwordVisible.value = false
  showRegistration.value = false
  loginMode.value = 'password'
  loading.value = true
  try {
    await userStore.login({ userNo, password })
    finishLogin()
  } catch {
    ElMessage.info('账号已创建，请使用新账号密码登录')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <img class="login-page__background" :src="background" alt="" width="1672" height="941" fetchpriority="high" decoding="async" />
    <div class="login-page__canvas">
      <header class="login-page__header">
        <div class="login-page__brand">
          <ShuttleMark class="login-page__brand-icon" />
          <span>羽毛球 MES</span>
          <span class="login-page__brand-description">生产制造执行系统</span>
        </div>
        <nav class="login-page__header-links" aria-label="平台服务">
          <span class="login-page__platform"><el-icon><Grid /></el-icon>企业管理平台</span>
          <el-popover placement="bottom-end" trigger="click" :width="276" content="首次使用请先注册工作账号；已有账号可使用工号和密码登录。忘记密码时请联系系统管理员。" popper-class="login-page-help">
            <template #reference>
              <button class="login-page__header-help" type="button"><el-icon><DocumentCopy /></el-icon>帮助中心</button>
            </template>
          </el-popover>
          <el-popover placement="bottom-end" trigger="click" :width="248" content="如需账号、权限或使用支持，请联系企业系统管理员。" popper-class="login-page-help">
            <template #reference>
              <button class="login-page__header-help" type="button"><el-icon><Service /></el-icon>技术支持</button>
            </template>
          </el-popover>
        </nav>
      </header>
      <LoginIntro class="login-page__intro" />

      <section class="login-page__auth" :class="{ 'login-page__auth--register': showRegistration, 'login-page__auth--face': !showRegistration && loginMode === 'face' }" :aria-labelledby="showRegistration ? 'register-title' : 'login-title'">
        <svg class="login-page__ribbons" viewBox="0 0 400 200" fill="none" aria-hidden="true">
          <defs>
            <linearGradient id="auth-ribbon" x1="380" y1="0" x2="100" y2="160" gradientUnits="userSpaceOnUse"><stop stop-color="#b4e5a0" stop-opacity=".6" /><stop offset=".5" stop-color="#88ddba" stop-opacity=".3" /><stop offset="1" stop-color="#b9edf2" stop-opacity="0" /></linearGradient>
          </defs>
          <path d="M100 180C230 185 206-12 300-12H400V13C294 17 299 183 100 180Z" fill="url(#auth-ribbon)" />
          <path d="M2 156C174 205 210 13 322 0H400V37C267 55 264 226 2 156Z" fill="url(#auth-ribbon)" />
          <path d="M151 109C244 80 258-3 341-3" stroke="white" stroke-opacity=".8" stroke-width="1.5" />
        </svg>
        <div class="login-page__form-content">
          <div class="login-page__panel-brand">
            <span class="login-page__panel-mark"><ShuttleMark monochrome /></span>
            <div><strong>羽毛球 MES</strong><p>生产制造执行系统</p></div>
          </div>
          <RegisterForm v-if="showRegistration" @cancel="showRegistration = false" @registered="handleRegistered" />
          <template v-else>
            <header v-if="loginMode === 'password'" class="login-page__form-header">
              <h2 id="login-title">登录工作台</h2>
              <p>请选择您的登录方式</p>
            </header>
            <el-tabs v-model="loginMode" class="login-page__mode" aria-label="登录方式" @tab-change="passwordVisible = false">
              <el-tab-pane name="password" :disabled="loading"><template #label><el-icon><Lock /></el-icon><span>密码登录</span></template></el-tab-pane>
              <el-tab-pane name="face" :disabled="loading"><template #label><FaceScanIcon /><span>人脸登录</span></template></el-tab-pane>
            </el-tabs>
            <h2 v-if="loginMode === 'face'" id="login-title" class="login-page__sr-only">人脸登录</h2>
            <template v-if="loginMode === 'face'">
              <FaceCapture mode="login" :verify="handleFaceLogin" />
              <p class="login-page__sr-only">首次使用请先用密码登录，在工作台完成人脸采集。</p>
            </template>
            <el-form v-else ref="formRef" :model="form" :rules="rules" label-position="top" hide-required-asterisk @submit.prevent="handleLogin">
              <el-form-item label="工号" prop="userNo" for="login-user-no">
                <template #label><el-icon><User /></el-icon><span>工号</span></template>
                <el-input id="login-user-no" v-model="form.userNo" placeholder="请输入工号" autocomplete="username" :disabled="loading" />
              </el-form-item>
              <div class="login-page__password-field">
                <el-form-item label="密码" prop="password" for="login-password">
                  <template #label><el-icon><Lock /></el-icon><span>密码</span></template>
                  <el-input id="login-password" v-model="form.password" :type="passwordVisible ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" :disabled="loading">
                    <template #suffix>
                      <button class="login-page__password-toggle" type="button" :aria-label="passwordVisible ? '隐藏密码' : '显示密码'" :aria-pressed="passwordVisible" :disabled="loading" @click="passwordVisible = !passwordVisible"><el-icon><component :is="passwordVisible ? View : Hide" /></el-icon></button>
                    </template>
                  </el-input>
                </el-form-item>
                <el-popover placement="top-end" trigger="click" :width="248" content="忘记工号或密码时，请联系系统管理员。" popper-class="login-page-help">
                  <template #reference><button class="login-page__help" type="button">登录遇到问题？</button></template>
                </el-popover>
              </div>
              <el-button type="primary" native-type="submit" class="login-page__submit" :loading="loading" :aria-busy="loading">
                {{ loading ? '正在登录…' : '登 录' }}
                <el-icon v-if="!loading" class="login-page__submit-arrow"><Right /></el-icon>
              </el-button>
            </el-form>
            <div class="login-page__separator"><span>还没有账号？</span></div>
            <el-button class="login-page__register" :disabled="loading" @click="passwordVisible = false; showRegistration = true">
              <span class="login-page__register-icon" aria-hidden="true"><el-icon><User /></el-icon><el-icon class="login-page__register-plus"><CirclePlus /></el-icon></span>
              <span>注册工作账号</span>
            </el-button>
          </template>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.login-page {
  --u: min(0.0598086124vw, 0.1062699256svh);
  --el-color-primary: #168565;
  --el-color-primary-light-3: #5bae94;
  --el-color-primary-light-5: #8bc9b5;
  --el-color-primary-light-7: #b9e0d3;
  --el-color-primary-light-9: #ecf7f2;
  --el-color-primary-dark-2: #106c50;
  --el-border-radius-base: calc(14 * var(--u));
  --el-border-color: #ccd9e5;
  --el-text-color-primary: #152632;
  --el-text-color-regular: #405665;
  --el-text-color-placeholder: #8c9ba9;
  --login-control-height: calc(53 * var(--u));
  --login-control-font-size: calc(19 * var(--u));
  --login-title-font-size: calc(35 * var(--u));
  --login-label-font-size: calc(18 * var(--u));
  --login-hint-font-size: calc(16 * var(--u));
  --login-field-gap: calc(23 * var(--u));
  --login-accent: #087b53;
  position: relative;
  isolation: isolate;
  display: grid;
  place-items: center;
  min-height: 100svh;
  overflow: clip;
  color: #152632;
  background: #e5f3f6;
  font-family: 'Microsoft YaHei', 'PingFang SC', 'Noto Sans CJK SC', sans-serif;
}
.login-page__background { position: absolute; z-index: -1; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.login-page__canvas { position: relative; width: calc(1672 * var(--u)); height: calc(941 * var(--u)); }
.login-page__header { position: absolute; z-index: 2; top: calc(28 * var(--u)); left: calc(89 * var(--u)); right: calc(79 * var(--u)); display: flex; justify-content: space-between; align-items: center; }
.login-page__brand { display: flex; align-items: center; gap: calc(15 * var(--u)); white-space: nowrap; font-size: calc(26 * var(--u)); font-weight: 700; }
.login-page__brand-icon { width: calc(43 * var(--u)); height: calc(43 * var(--u)); }
.login-page__brand-description { margin-left: calc(26 * var(--u)); padding-left: calc(30 * var(--u)); border-left: 1px solid #b5c9d1; color: #3c5262; font-size: calc(18 * var(--u)); font-weight: 500; line-height: 1.25; }
.login-page__header-links { display: flex; align-items: center; gap: calc(48 * var(--u)); color: #344c5c; font-size: calc(16 * var(--u)); font-weight: 500; }
.login-page__platform, .login-page__header-help { display: inline-flex; align-items: center; gap: calc(10 * var(--u)); white-space: nowrap; }
.login-page__header-links .el-icon { font-size: calc(23 * var(--u)); }
.login-page__header-help { padding: calc(5 * var(--u)) 0; border: 0; background: transparent; color: inherit; font: inherit; cursor: pointer; }
.login-page__header-help:hover { color: #087c66; }
.login-page__header-help:focus-visible, .login-page__help:focus-visible { outline: 2px solid #168565; outline-offset: 4px; border-radius: 3px; }
.login-page__intro { position: absolute; top: 0; left: 0; width: calc(982 * var(--u)); height: 100%; }
.login-page__auth { position: absolute; top: calc(110 * var(--u)); right: calc(88 * var(--u)); width: calc(545 * var(--u)); min-height: calc(738 * var(--u)); padding: calc(45 * var(--u)) calc(56 * var(--u)) calc(50 * var(--u)) calc(59 * var(--u)); border: calc(2 * var(--u)) solid #ffffffeb; border-radius: calc(40 * var(--u)); background: linear-gradient(120deg, #ffffffef, #f5fbfff0); box-shadow: 0 calc(24 * var(--u)) calc(60 * var(--u)) #5d9da61a, inset 0 1px 1px #fff; overflow: hidden; }
.login-page__ribbons { opacity: .65; position: absolute; pointer-events: none; top: 0; right: 0; width: calc(365 * var(--u)); height: calc(183 * var(--u)); }
.login-page__auth { max-height: calc(820 * var(--u)); overflow-y: auto; scrollbar-width: thin; }
.login-page__form-content { position: relative; }
.login-page__mode { margin-bottom: calc(20 * var(--u)); }
.login-page__mode :deep(.el-tabs__header) { margin: 0; padding: 0; border-radius: 999px; background: #dce5e973; }
.login-page__mode :deep(.el-tabs__nav-wrap::after), .login-page__mode :deep(.el-tabs__active-bar) { display: none; }
.login-page__mode :deep(.el-tabs__nav) { display: flex; float: none; width: 100%; }
.login-page__mode :deep(.el-tabs__item) { flex: 1; gap: calc(11 * var(--u)); height: calc(48 * var(--u)); padding: 0; border: 1px solid transparent; border-radius: 999px; color: #415665; font-size: calc(18 * var(--u)); font-weight: 600; transition: background-color 150ms ease, color 150ms ease; }
.login-page__mode :deep(.el-tabs__item .el-icon), .login-page__mode :deep(.el-tabs__item > svg) { width: calc(23 * var(--u)); height: calc(23 * var(--u)); font-size: calc(23 * var(--u)); }
.login-page__mode :deep(.el-tabs__item.is-active) { border-color: #57d5ac; background: linear-gradient(110deg, #fff, #e4fff4); color: #009a36; box-shadow: 0 4px 12px #0bc68c26, inset 0 1px #fff; }
.login-page__mode :deep(.el-tabs__item:focus-visible) { outline: 2px solid #009a36; outline-offset: 2px; }
.login-page__mode :deep(.el-tabs__content) { display: none; }
.login-page__face-error { padding: 9px 12px; border-radius: 8px; color: #a84035; background: #fff5f3; font-size: 12px; line-height: 1.7; }
.login-page__sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; border: 0; }
.login-page__panel-brand { display: flex; align-items: center; gap: calc(23 * var(--u)); height: calc(56 * var(--u)); margin-bottom: calc(43 * var(--u)); }
.login-page__panel-mark { display: grid; place-items: center; flex: 0 0 calc(59 * var(--u)); height: calc(56 * var(--u)); color: #fff; border-radius: calc(17 * var(--u)); background: radial-gradient(ellipse at 0 0, #80cc83, transparent 80%), linear-gradient(140deg, #48b49c, #128875); box-shadow: inset 0 1px 3px #ffffff55; }
.login-page__panel-mark svg { width: calc(35 * var(--u)); height: calc(35 * var(--u)); }
.login-page__panel-brand strong { display: block; font-size: calc(26 * var(--u)); font-weight: 700; line-height: calc(33 * var(--u)); }
.login-page__panel-brand p { margin: 0; color: #566979; font-size: calc(18 * var(--u)); line-height: calc(25 * var(--u)); }
.login-page__form-header { margin-bottom: calc(24 * var(--u)); }
.login-page__form-header h2 { margin: 0; font-size: var(--login-title-font-size); font-weight: 700; line-height: calc(49 * var(--u)); letter-spacing: 0; }
.login-page__form-header h2::after { content: ''; display: block; width: calc(37 * var(--u)); height: calc(5 * var(--u)); margin-top: calc(7 * var(--u)); border-radius: 3px; background: #188657; }
.login-page__form-header p { margin: calc(14 * var(--u)) 0 0; color: #566979; font-size: var(--login-control-font-size); line-height: calc(29 * var(--u)); }
.login-page :deep(.el-form-item) { margin-bottom: var(--login-field-gap); }
.login-page :deep(.el-form-item__label) { display: flex; align-items: center; gap: calc(10 * var(--u)); height: calc(22 * var(--u)); margin-bottom: calc(7 * var(--u)); padding: 0; color: #192c39; font-size: var(--login-label-font-size); font-weight: 600; line-height: calc(22 * var(--u)); }
.login-page :deep(.el-form-item__label .el-icon) { font-size: calc(24 * var(--u)); }
.login-page :deep(.el-input__wrapper), .login-page :deep(.el-select__wrapper) { min-height: var(--login-control-height); padding: 1px var(--login-input-padding, calc(37 * var(--u))); background: #ffffffc9; border-radius: calc(14 * var(--u)); box-shadow: 0 0 0 1px #ccd9e5 inset; transition: box-shadow 150ms ease; }
.login-page :deep(.el-input__wrapper:hover), .login-page :deep(.el-select__wrapper:hover) { box-shadow: 0 0 0 1px #83b9ad inset; }
.login-page :deep(.el-input__wrapper.is-focus), .login-page :deep(.el-select__wrapper.is-focused) { box-shadow: 0 0 0 1px #259b7b inset, 0 0 0 3px #259b7b14; }
.login-page :deep(.is-error .el-input__wrapper), .login-page :deep(.is-error .el-select__wrapper) { box-shadow: 0 0 0 1px var(--el-color-danger) inset; }
.login-page :deep(.el-input__inner), .login-page :deep(.el-select__wrapper) { font-size: var(--login-control-font-size); }
.login-page :deep(.el-input__suffix) { margin-right: var(--login-suffix-offset, calc(-17 * var(--u))); }
.login-page :deep(.el-input__password) { color: #718694; font-size: calc(23 * var(--u)); }
.login-page :deep(.el-form-item__error) { padding-top: calc(3 * var(--u)); font-size: calc(12 * var(--u)); }
.login-page :deep(.el-input__inner:is(:autofill, :-webkit-autofill)) { box-shadow: 0 0 0 1000px #fbfdff inset; -webkit-text-fill-color: #192c39; caret-color: #192c39; }
.login-page__password-toggle { display: grid; place-items: center; width: calc(28 * var(--u)); height: calc(32 * var(--u)); padding: 0; border: 0; background: none; color: #718694; font-size: calc(23 * var(--u)); cursor: pointer; }
.login-page__password-toggle:focus-visible { outline: 2px solid #168565; outline-offset: 2px; border-radius: 3px; }
.login-page__password-field { position: relative; }
.login-page__password-field :deep(.el-form-item) { margin-bottom: calc(32 * var(--u)); }
.login-page__help { position: absolute; top: 0; right: 0; border: 0; padding: 0; background: transparent; color: #087b53; font: inherit; font-size: calc(15 * var(--u)); font-weight: 600; line-height: calc(25 * var(--u)); cursor: pointer; }
.login-page__help:hover { text-decoration: underline; text-underline-offset: 4px; }
.login-page__submit, .login-page__register { position: relative; width: 100%; font-family: inherit; font-weight: 600; transition: filter 150ms ease, background-color 150ms ease, border-color 150ms ease; }
.login-page__submit, .login-page :deep(.register-form__submit) { height: calc(62 * var(--u)); border: 0; border-radius: calc(16 * var(--u)); background: radial-gradient(ellipse at 0 0, #55bca5, transparent 65%), linear-gradient(115deg, #128e8a, #329e59); color: #fff; font-size: calc(23 * var(--u)); box-shadow: 0 calc(10 * var(--u)) calc(20 * var(--u)) #35a69022, inset 0 1px 2px #ffffff25; }
.login-page__submit:hover, .login-page :deep(.register-form__submit:hover) { filter: brightness(1.05); }
.login-page__submit:active, .login-page :deep(.register-form__submit:active) { filter: brightness(.96); }
.login-page__submit-arrow { position: absolute; right: calc(20 * var(--u)); font-size: calc(29 * var(--u)); }
.login-page__separator { display: flex; align-items: center; gap: calc(17 * var(--u)); margin: calc(31 * var(--u)) 0 calc(23 * var(--u)); color: #4e6678; font-size: calc(17 * var(--u)); line-height: calc(23 * var(--u)); }
.login-page__separator::before, .login-page__separator::after { content: ''; flex: 1; height: 1px; background: #d3e1e8; }
.login-page__register { height: calc(54 * var(--u)); margin: 0; border-color: #79c1b0; border-radius: calc(15 * var(--u)); background: #f7fcfd33; color: #087b53; font-size: calc(19 * var(--u)); box-shadow: 0 calc(8 * var(--u)) calc(22 * var(--u)) #4dbe9d12; }
.login-page__register:hover, .login-page__register:focus-visible { color: #086847; border-color: #278f73; background: #eaf7f1; }
.login-page__register-icon { position: relative; display: inline-flex; margin-right: calc(15 * var(--u)); }
.login-page__register-icon > .el-icon { font-size: calc(25 * var(--u)); }
.login-page__register-icon > .login-page__register-plus { position: absolute; right: calc(-5 * var(--u)); bottom: calc(-1 * var(--u)); font-size: calc(13 * var(--u)); background: #f6fbfc; }
.login-page__auth--register { padding-top: calc(33 * var(--u)); padding-bottom: calc(27 * var(--u)); }
.login-page__auth--register .login-page__panel-brand { margin-bottom: calc(25 * var(--u)); }
/* Keep the Docker page's spatial scale; migrate the capsule and face login only. */
.login-page__header { top: calc(13 * var(--u)); left: calc(61 * var(--u)); right: calc(51 * var(--u)); height: calc(70 * var(--u)); padding: 0 calc(28 * var(--u)); border: 1px solid #ffffffba; border-radius: 999px; background: linear-gradient(100deg, #ffffff75, #e9f9ff3d); }
.login-page__auth:not(.login-page__auth--register) { min-height: calc(812 * var(--u)); }
@media (max-width: 1023px) {
  .login-page { --u: 1px; padding: 96px 24px 36px; overflow: visible; }
  .login-page__canvas { display: flex; justify-content: center; width: 100%; height: auto; }
  .login-page__intro, .login-page__header-links, .login-page__brand-description { display: none; }
  .login-page__header { position: fixed; top: 24px; left: 28px; right: 28px; }
  .login-page__auth, .login-page__auth:not(.login-page__auth--register) { position: relative; top: auto; right: auto; width: min(545px, 100%); min-height: 0; max-height: none; padding: 36px clamp(24px, 6vw, 59px); }
  .login-page__panel-brand { margin-bottom: 32px; }
}
@media (prefers-reduced-motion: reduce) {
  .login-page__mode :deep(.el-tabs__item) { transition: none; }
  .login-page :deep(.el-input__wrapper), .login-page :deep(.el-select__wrapper), .login-page__submit, .login-page__register { transition: none; }
  .login-page :deep(.el-icon.is-loading) { animation: none; }
  :global(.login-page-help) { --el-transition-duration: 0s; --el-transition-duration-fast: 0s; }
}
@media (forced-colors: active) {
  .login-page :deep(.el-input__inner:is(:autofill, :-webkit-autofill)) { box-shadow: none; -webkit-text-fill-color: FieldText; caret-color: FieldText; }
}
</style>
