<template>
  <div class="login-container">
    <div class="login-bg"></div>
    <div class="login-card">
      <div class="login-header">
        <div class="logo">
          <el-icon :size="64" color="#409EFF"><DataAnalysis /></el-icon>
        </div>
        <h1>电商爆款分析系统</h1>
        <p>E-commerce Hot Product Analysis System</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" class="login-form">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <div class="form-options">
            <el-checkbox v-model="rememberMe">记住我</el-checkbox>
            <router-link to="/register" class="register-link">没有账号？立即注册</router-link>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" class="login-btn" @click="handleLogin">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const rememberMe = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 页面加载时检查是否有保存的登录信息（仅当勾选"记住我"时才自动填充）
onMounted(() => {
  const savedUser = localStorage.getItem('rememberedUser')
  if (savedUser) {
    const userData = JSON.parse(savedUser)
    form.username = userData.username
    form.password = userData.password
    rememberMe.value = true
  }
  // 不再默认填充账号密码，保持输入框为空
})

const handleLogin = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const res = await login(form)

    // 登录前先清除所有存储的登录信息（解决角色切换问题）
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('user')

    // 处理"记住我"功能
    if (rememberMe.value) {
      // 记住我：使用localStorage，关闭浏览器后仍保持登录
      localStorage.setItem('rememberedUser', JSON.stringify({
        username: form.username,
        password: form.password
      }))
      // 设置token过期时间为7天
      const expireTime = Date.now() + 7 * 24 * 60 * 60 * 1000
      localStorage.setItem('tokenExpire', expireTime.toString())
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('user', JSON.stringify(res.data))
    } else {
      // 不记住我：使用sessionStorage，关闭浏览器后需重新登录
      localStorage.removeItem('rememberedUser')
      localStorage.removeItem('tokenExpire')
      sessionStorage.setItem('token', res.data.token)
      sessionStorage.setItem('user', JSON.stringify(res.data))
    }

    ElMessage.success('登录成功')
    router.push('/dashboard')
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: url('@/assets/login-bg.jpeg') no-repeat center center;
  background-size: cover;
  z-index: 0;
}

.login-bg::after {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.3);
}

.login-card {
  width: 520px;
  padding: 40px 50px 44px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  position: relative;
  z-index: 1;
  backdrop-filter: blur(10px);
  border: 2px solid rgba(64, 158, 255, 0.3);
}

.login-header { text-align: center; margin-bottom: 28px; }
.logo { margin-bottom: 14px; }
.login-header h1 { font-size: 32px; color: #303133; margin: 0 0 10px 0; font-weight: 700; }
.login-header p { font-size: 16px; color: #909399; margin: 0; letter-spacing: 1px; }
.login-form { padding: 0; max-width: 100%; }

.login-form :deep(.el-form-item) { margin-bottom: 20px; }
.login-form :deep(.el-input__wrapper) { padding: 12px 20px; min-height: 48px; }
.login-form :deep(.el-input__inner) { font-size: 19px; height: 36px; }
.login-form :deep(.el-input__prefix .el-icon) { font-size: 24px; }

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.form-options :deep(.el-checkbox__label) { font-size: 18px; }

.register-link {
  color: #409EFF;
  font-size: 18px;
  text-decoration: none;
}

.register-link:hover { text-decoration: underline; }

.login-btn {
  width: 100%;
  height: 52px;
  font-size: 20px;
  border-radius: 10px;
  letter-spacing: 6px;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  color: #909399;
  font-size: 15px;
}
</style>