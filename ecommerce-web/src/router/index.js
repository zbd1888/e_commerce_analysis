import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'

// 辅助函数：从localStorage或sessionStorage获取数据（记住我/不记住我）
const getStorageItem = (key) => {
  return localStorage.getItem(key) || sessionStorage.getItem(key)
}

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/login/index.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/register/index.vue')
  },
  {
    path: '/',
    component: () => import('../layout/index.vue'),
    children: [
      // ========== 管理员专属页面 ==========
      {
        path: 'admin/dashboard',
        name: 'AdminDashboard',
        component: () => import('../views/admin/dashboard.vue'),
        meta: { title: '数据质量大屏', icon: 'Monitor', requireAdmin: true }
      },
      {
        path: 'admin/crawl',
        name: 'AdminCrawl',
        component: () => import('../views/admin/crawl.vue'),
        meta: { title: '数据采集', icon: 'Download', requireAdmin: true }
      },
      {
        path: 'admin/clean',
        name: 'AdminClean',
        component: () => import('../views/admin/clean.vue'),
        meta: { title: '数据清洗', icon: 'Brush', requireAdmin: true }
      },
      {
        path: 'admin/product',
        name: 'AdminProduct',
        component: () => import('../views/admin/product.vue'),
        meta: { title: '商品管理', icon: 'Goods', requireAdmin: true }
      },
      {
        path: 'admin/user',
        name: 'AdminUser',
        component: () => import('../views/admin/user.vue'),
        meta: { title: '用户管理', icon: 'User', requireAdmin: true }
      },
      {
        path: 'admin/rule',
        name: 'AdminRule',
        component: () => import('../views/admin/rule.vue'),
        meta: { title: '爆品规则配置', icon: 'Setting', requireAdmin: true }
      },
      {
        path: 'admin/monitor',
        name: 'AdminMonitor',
        component: () => import('../views/admin/monitor.vue'),
        meta: { title: '系统监控', icon: 'DataLine', requireAdmin: true }
      },
      // ========== 普通用户页面 ==========
      {
        path: 'user/dashboard',
        name: 'UserDashboard',
        component: () => import('../views/user/dashboard.vue'),
        meta: { title: '爆品总览', icon: 'DataAnalysis' }
      },
      {
        path: 'user/product',
        name: 'UserProduct',
        component: () => import('../views/user/product.vue'),
        meta: { title: '爆品发现', icon: 'Search' }
      },
      {
        path: 'user/hot-analysis',
        name: 'UserHotAnalysis',
        component: () => import('../views/user/hot-analysis.vue'),
        meta: { title: '爆品详情分析', icon: 'TrendCharts' }
      },
      {
        path: 'user/category',
        name: 'UserCategory',
        component: () => import('../views/user/category-analysis.vue'),
        meta: { title: '行业/品类分析', icon: 'PieChart' }
      },
      {
        path: 'user/region',
        name: 'UserRegion',
        component: () => import('../views/user/region.vue'),
        meta: { title: '地域可视化', icon: 'Location' }
      },
      {
        path: 'user/prediction',
        name: 'UserPrediction',
        component: () => import('../views/user/prediction.vue'),
        meta: { title: '选品助手', icon: 'Aim' }
      },
      {
        path: 'user/ai-assistant',
        name: 'AiAssistant',
        component: () => import('../views/user/ai-assistant.vue'),
        meta: { title: 'AI 选品分析', icon: 'MagicStick' }
      },

      // 保留旧路由兼容
      {
        path: 'user/analysis',
        redirect: '/user/hot-analysis'
      },
      // ========== 兼容旧路由 ==========
      {
        path: 'dashboard',
        redirect: to => {
          const user = JSON.parse(getStorageItem('user') || '{}')
          return user.role === 'admin' ? '/admin/dashboard' : '/user/dashboard'
        }
      },
      {
        path: 'product',
        redirect: to => {
          const user = JSON.parse(getStorageItem('user') || '{}')
          return user.role === 'admin' ? '/admin/product' : '/user/product'
        }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  // 优先从localStorage获取（记住我），其次从sessionStorage获取（不记住我）
  const token = getStorageItem('token')
  const userStr = getStorageItem('user')
  const user = userStr ? JSON.parse(userStr) : null

  // 登录和注册页面不需要token
  if (to.path === '/login' || to.path === '/register') {
    next()
    return
  }

  // 未登录跳转到登录页
  if (!token) {
    next('/login')
    return
  }

  // 根路径重定向
  if (to.path === '/') {
    if (user?.role === 'admin') {
      next('/admin/dashboard')
    } else {
      next('/user/dashboard')
    }
    return
  }

  // 检查管理员权限
  if (to.meta?.requireAdmin && user?.role !== 'admin') {
    ElMessage.warning('您没有权限访问该页面')
    next('/user/dashboard')
    return
  }

  next()
})

export default router
