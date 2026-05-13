<template>
  <div class="register-container">
    <div class="register-bg"></div>
    <div class="register-card">
      <div class="register-header">
        <div class="logo">
          <el-icon :size="64" color="#409EFF"><DataAnalysis /></el-icon>
        </div>
        <h1>用户注册</h1>
        <p>Create your account</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" class="register-form">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" placeholder="请确认密码" size="large" prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input v-model="form.nickname" placeholder="请输入昵称（选填）" size="large" prefix-icon="UserFilled" />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" placeholder="请输入邮箱（选填）" size="large" prefix-icon="Message" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" class="register-btn" @click="handleRegister">
            {{ loading ? '注册中...' : '立即注册' }}
          </el-button>
        </el-form-item>
      </el-form>
      <div class="register-footer">
        <span>已有账号？</span>
        <router-link to="/login" class="login-link">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '@/api'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  email: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在3-20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在6-20个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    await register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || form.username,
      email: form.email
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.register-bg {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: url('@/assets/login-bg.jpeg') no-repeat center center;
  background-size: cover;
  z-index: 0;
}

.register-bg::after {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.3);
}

.register-card {
  width: 520px;
  padding: 36px 50px 40px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  position: relative;
  z-index: 1;
  backdrop-filter: blur(10px);
  border: 2px solid rgba(64, 158, 255, 0.3);
}

.register-header { text-align: center; margin-bottom: 24px; }
.logo { margin-bottom: 12px; }
.register-header h1 { font-size: 32px; color: #303133; margin: 0 0 8px 0; font-weight: 700; }
.register-header p { font-size: 16px; color: #909399; margin: 0; letter-spacing: 1px; }
.register-form { padding: 0; max-width: 100%; }

.register-form :deep(.el-form-item) { margin-bottom: 18px; }
.register-form :deep(.el-input__wrapper) { padding: 12px 20px; min-height: 48px; }
.register-form :deep(.el-input__inner) { font-size: 19px; height: 36px; }
.register-form :deep(.el-input__prefix .el-icon) { font-size: 24px; }

.register-btn {
  width: 100%;
  height: 52px;
  font-size: 20px;
  border-radius: 10px;
}

.register-footer {
  text-align: center;
  margin-top: 20px;
  color: #909399;
  font-size: 18px;
}

.login-link {
  color: #409EFF;
  text-decoration: none;
  margin-left: 4px;
  font-size: 18px;
}

.login-link:hover { text-decoration: underline; }
</style>