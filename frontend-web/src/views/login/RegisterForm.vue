<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Postcard, Suitcase, Lock, Key, Right, Hide, View } from '@element-plus/icons-vue'
import { getRegistrationRoles, registerAccount } from '@/api/auth'
import type { RegistrationRole } from '@/api/auth'

const emit = defineEmits<{
  cancel: []
  registered: [userNo: string, password: string]
}>()

const formRef = ref<FormInstance>()
const roles = ref<RegistrationRole[]>([])
const loadingRoles = ref(false)
const rolesError = ref(false)
const submitting = ref(false)
const passwordVisibility = reactive({ password: false, confirmPassword: false })
const form = reactive({
  userNo: '',
  userName: '',
  roleId: undefined as number | undefined,
  password: '',
  confirmPassword: '',
})

const rules: FormRules = {
  userNo: [
    { required: true, whitespace: true, message: '请输入工号', trigger: 'blur' },
    { max: 32, message: '工号最多 32 个字符', trigger: 'blur' },
  ],
  userName: [
    { required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' },
    { max: 64, message: '姓名最多 64 个字符', trigger: 'blur' },
  ],
  roleId: [{ required: true, message: '请选择职位', trigger: 'change' }],
  password: [
    { required: true, whitespace: true, message: '请设置密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码需为 6–32 位', trigger: 'blur' },
    {
      validator: (_rule, _value, callback) => {
        if (form.confirmPassword) void formRef.value?.validateField('confirmPassword').catch(() => {})
        callback()
      },
      trigger: 'change',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        callback(value === form.password ? undefined : new Error('两次密码不一致'))
      },
      trigger: 'blur',
    },
  ],
}

async function loadRoles() {
  if (loadingRoles.value) return
  loadingRoles.value = true
  rolesError.value = false
  try {
    roles.value = await getRegistrationRoles()
  } catch {
    rolesError.value = true
  } finally {
    loadingRoles.value = false
  }
}

async function submitRegistration() {
  if (submitting.value || loadingRoles.value || rolesError.value || !roles.value.length) return
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  if (submitting.value || form.roleId === undefined) return
  submitting.value = true
  try {
    await registerAccount({
      userNo: form.userNo.trim(),
      userName: form.userName.trim(),
      password: form.password,
      roleId: form.roleId,
    })
    ElMessage.success('注册成功，正在进入工作台')
    emit('registered', form.userNo.trim(), form.password)
  } catch {
    // 业务错误由请求拦截器统一展示，保留表单供用户修正。
  } finally {
    submitting.value = false
  }
}

onMounted(() => { void loadRoles() })
</script>

<template>
  <div class="register-form">
    <header class="register-form__header">
      <h2 id="register-title">注册工作账号</h2>
      <p>填写基本信息，创建您的工作账号</p>
    </header>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      hide-required-asterisk
      :disabled="submitting"
      @submit.prevent="submitRegistration"
    >
      <div class="register-form__row">
        <el-form-item label="工号" prop="userNo" for="register-user-no">
          <template #label><el-icon><User /></el-icon><span>工号</span></template>
          <el-input id="register-user-no" v-model="form.userNo" autocomplete="username" placeholder="请输入工号" :maxlength="32" />
        </el-form-item>
        <el-form-item label="姓名" prop="userName" for="register-user-name">
          <template #label><el-icon><Postcard /></el-icon><span>姓名</span></template>
          <el-input id="register-user-name" v-model="form.userName" autocomplete="name" placeholder="请输入姓名" :maxlength="64" />
        </el-form-item>
      </div>

      <el-form-item label="职位" prop="roleId" for="register-role">
        <template #label><el-icon><Suitcase /></el-icon><span>职位</span></template>
        <el-select
          id="register-role"
          v-model="form.roleId"
          placeholder="请选择您的职位"
          :loading="loadingRoles"
          :disabled="submitting || loadingRoles || rolesError || !roles.length"
        >
          <el-option v-for="role in roles" :key="role.id" :label="role.roleName" :value="role.id" />
        </el-select>
      </el-form-item>
      <div v-if="rolesError" class="register-form__notice" role="alert">
        职位加载失败，<button type="button" @click="loadRoles">重新加载</button>
      </div>
      <p v-else-if="!loadingRoles && !roles.length" class="register-form__notice" role="status">
        暂无可注册职位，请联系系统管理员。
      </p>

      <div class="register-form__row">
        <el-form-item label="密码" prop="password" for="register-password">
          <template #label><el-icon><Lock /></el-icon><span>密码</span></template>
          <el-input id="register-password" v-model="form.password" :type="passwordVisibility.password ? 'text' : 'password'" autocomplete="new-password" placeholder="6–32 位密码" :maxlength="32">
            <template #suffix><button class="register-form__password-toggle" type="button" :aria-label="passwordVisibility.password ? '隐藏密码' : '显示密码'" :aria-pressed="passwordVisibility.password" :disabled="submitting" @click="passwordVisibility.password = !passwordVisibility.password"><el-icon><component :is="passwordVisibility.password ? View : Hide" /></el-icon></button></template>
          </el-input>
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword" for="register-confirm-password">
          <template #label><el-icon><Key /></el-icon><span>确认密码</span></template>
          <el-input id="register-confirm-password" v-model="form.confirmPassword" :type="passwordVisibility.confirmPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="再次输入密码" :maxlength="32">
            <template #suffix><button class="register-form__password-toggle" type="button" :aria-label="passwordVisibility.confirmPassword ? '隐藏确认密码' : '显示确认密码'" :aria-pressed="passwordVisibility.confirmPassword" :disabled="submitting" @click="passwordVisibility.confirmPassword = !passwordVisibility.confirmPassword"><el-icon><component :is="passwordVisibility.confirmPassword ? View : Hide" /></el-icon></button></template>
          </el-input>
        </el-form-item>
      </div>
      <el-button
        type="primary"
        native-type="submit"
        class="register-form__submit"
        :loading="submitting"
        :disabled="loadingRoles || rolesError || !roles.length"
        :aria-busy="submitting"
      >
        {{ submitting ? '正在注册…' : '注 册' }}
        <el-icon v-if="!submitting" class="register-form__arrow"><Right /></el-icon>
      </el-button>
    </el-form>
    <p class="register-form__back">
      已有账号？<button type="button" :disabled="submitting" @click="emit('cancel')">返回登录</button>
    </p>
    <p class="register-form__note">请按实际岗位选择职位，账号权限由企业统一管理。</p>
  </div>
</template>

<style scoped>
.register-form { --login-control-height: calc(49 * var(--u)); --login-control-font-size: calc(16 * var(--u)); --login-input-padding: calc(15 * var(--u)); --login-suffix-offset: 0px; --login-field-gap: calc(22 * var(--u)); }
.register-form__header { margin-bottom: calc(20 * var(--u)); }
.register-form__header h2 { margin: 0; color: #152632; font-size: calc(33 * var(--u)); line-height: calc(45 * var(--u)); font-weight: 800; letter-spacing: calc(1 * var(--u)); }
.register-form__header h2::after { content: ''; display: block; width: calc(37 * var(--u)); height: calc(5 * var(--u)); margin-top: calc(7 * var(--u)); border-radius: 3px; background: #188657; }
.register-form__header p { margin: calc(13 * var(--u)) 0 0; color: #566979; font-size: calc(17 * var(--u)); line-height: calc(26 * var(--u)); }
.register-form__row { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: calc(16 * var(--u)); }
.register-form :deep(.el-form-item) { min-width: 0; margin-bottom: calc(22 * var(--u)); }
.register-form__password-toggle { display: grid; place-items: center; width: calc(22 * var(--u)); height: calc(30 * var(--u)); padding: 0; border: 0; background: none; color: #718694; font-size: calc(19 * var(--u)); cursor: pointer; }
.register-form__password-toggle:focus-visible { outline: 2px solid #168565; outline-offset: 2px; border-radius: 3px; }
.register-form :deep(.el-select) { width: 100%; }
.register-form__submit { position: relative; width: 100%; font-family: inherit; font-weight: 600; transition: filter 150ms ease; }
.register-form__submit.is-disabled { opacity: .55; }
.register-form__arrow { position: absolute; right: calc(20 * var(--u)); font-size: calc(27 * var(--u)); }
.register-form__back { margin: calc(25 * var(--u)) 0 0; color: #566979; text-align: center; font-size: calc(17 * var(--u)); line-height: calc(26 * var(--u)); }
.register-form__back button, .register-form__notice button { padding: 0; border: 0; background: transparent; color: var(--login-accent); font: inherit; font-weight: 600; cursor: pointer; }
.register-form__back button:hover, .register-form__notice button:hover { text-decoration: underline; text-underline-offset: 4px; }
.register-form__back button:disabled { opacity: .5; cursor: wait; }
.register-form__back button:focus-visible, .register-form__notice button:focus-visible { outline: 2px solid #168565; outline-offset: 3px; }
.register-form__note { margin: calc(13 * var(--u)) 0 0; color: #657a87; font-size: calc(13 * var(--u)); text-align: center; line-height: 1.7; }
.register-form__notice { margin: calc(-10 * var(--u)) 0 calc(20 * var(--u)); color: #a13d35; font-size: calc(14 * var(--u)); line-height: 1.7; }
@media (prefers-reduced-motion: reduce) {
  .register-form__submit, .register-form :deep(.el-select__wrapper) { transition: none; }
  .register-form :deep(.el-icon.is-loading) { animation: none; }
}
</style>
