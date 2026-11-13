<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowDown, Expand, Fold } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { usePermission } from '@/composables/usePermission'
import { findMenuChain, menuRoutes } from '@/router/routes'
import SidebarMenuItem from './components/SidebarMenuItem.vue'
import TagsView from './components/TagsView.vue'
import FaceEnrollment from '@/components/face/FaceEnrollment.vue'
import brandIcon from '@/assets/images/sidebar-mes-logo-refined.webp'

defineOptions({ name: 'BasicLayout' })

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const faceEnrollment = ref<InstanceType<typeof FaceEnrollment>>()
const { hasRole } = usePermission()

/** 一级菜单按角色/hidden 过滤，子级过滤在 SidebarMenuItem 内递归处理 */
const menus = computed(() =>
  menuRoutes.filter((item) => !item.meta?.hidden && hasRole(item.meta?.roles)),
)

const activeMenu = computed(() => route.meta.activeMenu ?? route.path)

/** 面包屑：从菜单树反查祖先链（路由已拍平，route.matched 不含分组节点） */
const breadcrumbs = computed(() => {
  const chain = findMenuChain(menuRoutes, route.path)
  if (chain) return chain.map((item) => item.meta?.title).filter(Boolean)
  return route.meta.title ? [route.meta.title] : []
})

const asideWidth = computed(() =>
  appStore.sidebarCollapsed
    ? 'var(--mes-sidebar-collapsed-width)'
    : 'var(--mes-sidebar-width)',
)

// ---------- 修改密码 ----------

const passwordVisible = ref(false)
const passwordLoading = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '长度 6 到 32 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== passwordForm.newPassword) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

function openPasswordDialog() {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisible.value = true
}

async function handleChangePassword() {
  try {
    await passwordFormRef.value?.validate()
  } catch {
    return
  }
  passwordLoading.value = true
  try {
    // 成功后后端使会话失效，本地态已由 store 清空
    await userStore.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordVisible.value = false
    appStore.clearTabs()
    ElMessage.success('密码已修改，请重新登录')
    router.push('/login')
  } catch {
    // 错误提示由 request 拦截器统一弹出
  } finally {
    passwordLoading.value = false
  }
}

async function handleUserCommand(command: string | number | object) {
  if (command === 'face') {
    void faceEnrollment.value?.open()
    return
  }
  if (command === 'password') {
    openPasswordDialog()
    return
  }
  if (command === 'logout') {
    await userStore.logout()
    appStore.clearTabs()
    router.push('/login')
  }
}
</script>

<template>
  <el-container class="basic-layout">
    <el-aside :width="asideWidth" class="basic-layout__aside">
      <div class="basic-layout__logo" role="img" aria-label="羽毛球 MES">
        <span class="basic-layout__brand-icon" aria-hidden="true">
          <img class="basic-layout__brand-image" :src="brandIcon" alt="" width="64" height="64" />
        </span>
        <span v-if="!appStore.sidebarCollapsed">羽毛球 MES</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="activeMenu"
          :collapse="appStore.sidebarCollapsed"
          :collapse-transition="false"
          unique-opened
          router
          class="basic-layout__menu"
        >
          <SidebarMenuItem v-for="item in menus" :key="item.path" :route="item" />
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container class="basic-layout__right">
      <el-header class="basic-layout__header">
        <div class="basic-layout__header-left">
          <el-icon class="basic-layout__collapse" :size="18" @click="appStore.toggleSidebar()">
            <Fold v-if="!appStore.sidebarCollapsed" />
            <Expand v-else />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="(title, index) in breadcrumbs" :key="index">
              {{ title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <el-dropdown trigger="click" @command="handleUserCommand">
          <span class="basic-layout__user">
            <el-avatar :size="28" class="basic-layout__avatar">
              {{ (userStore.userName || '未').slice(0, 1) }}
            </el-avatar>
            <span>{{ userStore.userName || '未登录' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>
                工号：{{ userStore.userNo || '-' }}
              </el-dropdown-item>
              <el-dropdown-item disabled>
                角色：{{ userStore.roleCodes.join(' / ') || '-' }}
              </el-dropdown-item>
              <el-dropdown-item divided command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="face">人脸登录设置</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <TagsView />

      <el-main class="basic-layout__main">
        <router-view v-slot="{ Component }">
          <!-- include 匹配组件名：约定页面 defineOptions({ name }) 与路由 name 一致 -->
          <keep-alive :include="appStore.cachedViews">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>

    <FaceEnrollment ref="faceEnrollment" />

    <el-dialog
      v-model="passwordVisible"
      title="修改密码"
      width="440px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="90px"
      >
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="handleChangePassword">
          确定
        </el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<style scoped>
.basic-layout {
  height: 100%;
}

.basic-layout__aside {
  display: flex;
  flex-direction: column;
  background: #001529;
  transition: width 0.2s;
}

.basic-layout__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  gap: 10px;
  height: var(--mes-header-height);
  font-size: 16px;
  font-weight: 600;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
}

.basic-layout__brand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  overflow: hidden;
}

.basic-layout__brand-image {
  display: block;
  flex: 0 0 64px;
  width: 64px;
  height: 64px;
  max-width: none;
  object-fit: contain;
}

.basic-layout__menu {
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: rgb(255 255 255 / 65%);
  --el-menu-hover-bg-color: rgb(255 255 255 / 10%);
  --el-menu-active-color: #fff;
}

.basic-layout__menu :deep(.el-menu) {
  background: transparent;
}

.basic-layout__menu :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary);
}

.basic-layout__right {
  min-width: 0;
}

.basic-layout__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--mes-header-height);
  padding: 0 16px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.basic-layout__header-left {
  display: flex;
  gap: 12px;
  align-items: center;
}

.basic-layout__collapse {
  cursor: pointer;
}

.basic-layout__user {
  display: flex;
  gap: 6px;
  align-items: center;
  cursor: pointer;
}

.basic-layout__avatar {
  background: var(--el-color-primary);
}

.basic-layout__main {
  padding: 16px;
  overflow: auto;
  background: var(--mes-page-bg);
}
</style>
