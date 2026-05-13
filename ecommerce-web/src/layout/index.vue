<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '72px' : '252px'" class="layout-aside">
      <div class="logo-area">
        <div class="logo-pill">{{ isCollapse ? '爆' : '爆品' }}</div>
        <div v-show="!isCollapse" class="logo-copy">
          <span class="logo-text">{{ isAdmin ? '后台管理' : '数据分析' }}</span>
          <span class="logo-subtitle">{{ isAdmin ? '管理控制台' : '选品工作台' }}</span>
        </div>
      </div>
      <el-menu :default-active="activeMenu" :collapse="isCollapse" background-color="transparent" text-color="#9aa7bd" active-text-color="#ffffff" router>
        <!-- ========== 管理员菜单 ========== -->
        <template v-if="isAdmin">
          <el-menu-item index="/admin/dashboard">
            <el-icon><Monitor /></el-icon>
            <span>数据质量大屏</span>
          </el-menu-item>
          <el-menu-item index="/admin/crawl">
            <el-icon><Download /></el-icon>
            <span>数据采集</span>
          </el-menu-item>
          <el-menu-item index="/admin/clean">
            <el-icon><Brush /></el-icon>
            <span>数据清洗</span>
          </el-menu-item>
          <el-menu-item index="/admin/product">
            <el-icon><Goods /></el-icon>
            <span>商品管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/user">
            <el-icon><UserFilled /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/rule">
            <el-icon><Setting /></el-icon>
            <span>爆品规则配置</span>
          </el-menu-item>
          <el-menu-item index="/admin/monitor">
            <el-icon><DataLine /></el-icon>
            <span>系统监控</span>
          </el-menu-item>
        </template>
        <!-- ========== 普通用户菜单 ========== -->
        <template v-else>
          <el-menu-item index="/user/dashboard">
            <el-icon><DataAnalysis /></el-icon>
            <span>爆品总览</span>
          </el-menu-item>
          <el-menu-item index="/user/product">
            <el-icon><Search /></el-icon>
            <span>爆品发现</span>
          </el-menu-item>
          <el-menu-item index="/user/hot-analysis">
            <el-icon><TrendCharts /></el-icon>
            <span>爆品详情分析</span>
          </el-menu-item>
          <el-menu-item index="/user/category">
            <el-icon><PieChart /></el-icon>
            <span>行业/品类分析</span>
          </el-menu-item>
          <el-menu-item index="/user/region">
            <el-icon><Location /></el-icon>
            <span>地域可视化</span>
          </el-menu-item>
          <el-menu-item index="/user/prediction">
            <el-icon><Aim /></el-icon>
            <span>选品助手</span>
          </el-menu-item>
          <el-menu-item index="/user/ai-assistant">
            <el-icon><MagicStick /></el-icon>
            <span>AI 选品分析</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <button class="collapse-btn" type="button" @click="isCollapse = !isCollapse" :title="isCollapse ? '展开菜单' : '收起菜单'">
            <el-icon>
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
          </button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag v-if="isAdmin" type="danger" size="small" class="role-tag" effect="light">管理员</el-tag>
          <el-tag v-else type="info" size="small" class="role-tag" effect="light">普通用户</el-tag>
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="36" icon="User" />
              <span class="username">{{ user?.nickname || user?.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>
                  <el-icon><User /></el-icon>{{ user?.username }}
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const isCollapse = ref(false)

// 从localStorage或sessionStorage获取用户信息
const getStorageItem = (key) => localStorage.getItem(key) || sessionStorage.getItem(key)

const user = computed(() => {
  try {
    const u = getStorageItem('user')
    if (!u) return null
    const parsed = JSON.parse(u)
    // 确保用户名有值
    if (!parsed.nickname && !parsed.username) {
      parsed.nickname = parsed.role === 'admin' ? '管理员' : '用户'
    }
    return parsed
  } catch (e) {
    console.error('解析用户信息失败', e)
    return null
  }
})

const isAdmin = computed(() => {
  return user.value?.role === 'admin'
})

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '爆品总览')

const handleCommand = (cmd) => {
  if (cmd === 'logout') {
    // 清除所有存储的登录信息
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    localStorage.removeItem('rememberedUser')
    localStorage.removeItem('tokenExpire')
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('user')
    router.push('/login')
  }
}
</script>

<style scoped>
.layout-container { height: 100vh; background: #eef3f8; }

.layout-aside {
  background:
    linear-gradient(180deg, #111827 0%, #161f2d 46%, #0f1724 100%);
  transition: width 0.3s;
  overflow: hidden;
  box-shadow: 12px 0 30px rgba(15, 23, 42, 0.16);
  position: relative;
}

.layout-aside::after {
  content: '';
  position: absolute;
  inset: 0 0 auto auto;
  width: 1px;
  height: 100%;
  background: linear-gradient(180deg, rgba(96, 165, 250, 0.45), transparent 38%);
  pointer-events: none;
}

.logo-area {
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.12);
}

.logo-pill {
  min-width: 44px;
  height: 44px;
  padding: 0 10px;
  border-radius: 10px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 800;
  background: linear-gradient(135deg, #2563eb, #22c55e);
  box-shadow: 0 14px 28px rgba(37, 99, 235, 0.28);
  flex-shrink: 0;
}

.logo-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.logo-text {
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
  line-height: 1.4;
}

.logo-subtitle {
  color: #7dd3fc;
  font-size: 12px;
  letter-spacing: 0.6px;
  text-transform: uppercase;
}

.el-menu {
  border-right: none;
  padding: 12px 10px 18px;
}

.el-menu :deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  padding: 0 16px !important;
  margin: 6px 0;
  font-size: 16px;
  border-radius: 10px;
  transition: background 0.2s ease, color 0.2s ease, transform 0.2s ease;
}

.el-menu :deep(.el-menu-item:hover) {
  color: #ffffff;
  background: rgba(59, 130, 246, 0.14);
  transform: translateX(2px);
}

.el-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.98), rgba(14, 165, 233, 0.9));
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.22);
}

.el-menu :deep(.el-menu-item .el-icon) {
  font-size: 20px;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 70px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.94);
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(16px);
  z-index: 2;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  width: 38px;
  height: 38px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  cursor: pointer;
  color: #475569;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
}

.collapse-btn:hover {
  color: #2563eb;
  border-color: #bfdbfe;
  background: #eff6ff;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-tag { margin-right: 8px; font-size: 14px; border-radius: 999px; }

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
}

.user-info:hover {
  background: #f8fafc;
  border-color: #e2e8f0;
}

.username {
  color: #606266;
  font-size: 15px;
}

.layout-main {
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.08), transparent 32%),
    linear-gradient(180deg, #f7faff 0%, #eef3f8 100%);
  padding: 0;
  overflow-y: auto;
}
</style>
