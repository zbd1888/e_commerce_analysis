<template>
  <div class="crawl-page">
    <section class="admin-page-head">
      <div>
        <h2 class="admin-page-title">数据采集</h2>
        <p class="admin-page-desc">管理淘宝账号池，配置采集任务并实时查看爬虫进度和运行日志。</p>
      </div>
      <div class="admin-page-actions">
        <el-tag :type="statusType" effect="plain">{{ statusText }}</el-tag>
      </div>
    </section>

    <!-- 账号管理区域：紧凑布局，缩短上下空间 -->
    <el-card class="account-card account-card-compact" shadow="hover">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-icon :size="24" color="#409EFF"><User /></el-icon>
            <span>淘宝账号池管理</span>
          </div>
          <el-button type="primary" size="small" @click="startAddAccount" :loading="accountLoading">
            添加账号
          </el-button>
        </div>
      </template>

      <div class="account-list">
        <el-empty v-if="accounts.length === 0" description="暂无账号，请先添加淘宝账号" :image-size="80" class="account-empty">
          <el-button type="primary" size="default" @click="startAddAccount">立即添加</el-button>
        </el-empty>
        <div v-else class="account-items">
          <el-tag v-for="account in accounts" :key="account.accountId" size="large" type="success" effect="plain" class="account-tag">
            <el-icon><UserFilled /></el-icon>
            {{ account.accountName }}
            <span class="account-time">{{ account.addTime }}</span>
          </el-tag>
        </div>
      </div>

      <div v-if="accountStatus" class="account-status">
        <el-alert :title="accountStatus" :type="accountStatusType" :closable="false" show-icon />
      </div>
    </el-card>

    <!-- 上下结构：采集配置（上）+ 采集进度（下） -->
    <div class="config-stack">
      <!-- 采集配置（上） -->
      <el-card class="config-card">
          <template #header><span>采集配置</span></template>
          <el-form :model="config" label-width="120px" class="crawl-form">
            <el-form-item label="采集类型">
              <el-radio-group v-model="config.type">
                <el-radio value="taobao">淘宝关键词搜索</el-radio>
                <el-radio value="tmall">天猫热销榜单</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="搜索关键词" v-if="config.type === 'taobao'">
              <el-input v-model="config.keyword" placeholder="请输入搜索关键词" />
            </el-form-item>
            <el-form-item label="爬取页数" v-if="config.type === 'taobao'">
              <el-input-number v-model="config.pages" :min="1" :max="50" />
            </el-form-item>
            <el-form-item label="爬取间隔" v-if="config.type === 'taobao'">
              <div class="interval-input">
                <el-input-number v-model="config.minInterval" :min="1" :max="10" :controls="true" controls-position="right" style="width: 100px" />
                <span class="interval-sep">~</span>
                <el-input-number v-model="config.maxInterval" :min="1" :max="30" :controls="true" controls-position="right" style="width: 100px" />
                <span class="interval-unit">秒（随机等待）</span>
              </div>
              <div class="interval-hint">当前设置: {{ config.minInterval }} - {{ config.maxInterval }} 秒</div>
            </el-form-item>
            <el-form-item v-if="config.type === 'tmall'">
              <el-alert type="info" :closable="false" show-icon>
                <template #title>天猫榜单说明</template>
                <span>天猫热销榜单爬取将自动获取15个分类的热销数据，无需额外配置</span>
              </el-alert>
            </el-form-item>
            <el-form-item label="使用代理">
              <el-switch v-model="config.useProxy" />
            </el-form-item>
            <el-form-item label="代理API" v-if="config.useProxy">
              <el-input v-model="config.proxyApi" placeholder="https://proxy.example.com/get?key=xxx" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="startCrawl" :loading="crawling" :disabled="crawling || accounts.length === 0">
                {{ crawling ? '采集中...' : '开始采集' }}
              </el-button>
              <el-button @click="stopCrawl" v-if="crawling" type="danger">停止采集</el-button>
            </el-form-item>
          </el-form>
        </el-card>

      <!-- 采集进度（下） -->
      <el-card class="progress-card">
          <template #header>
            <span>采集进度</span>
            <el-tag :type="statusType" size="small" style="margin-left: 10px">{{ statusText }}</el-tag>
          </template>
          <div class="progress-info">
            <el-progress :percentage="progress" :stroke-width="20" :format="progressFormat" />
            <div class="count-info">已获取: <strong>{{ totalCount }}</strong> 条{{ config.type === 'tmall' ? '榜单数据' : '商品' }}</div>
          </div>
          <div class="log-area">
            <div class="log-title">实时日志:</div>
            <div class="log-content" ref="logContainer">
              <div v-for="(log, idx) in logs" :key="idx" class="log-item">{{ log }}</div>
            </div>
          </div>
        </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { io } from 'socket.io-client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, UserFilled } from '@element-plus/icons-vue'
import { startAccountLogin, confirmAccountLogin, addAnotherAccount, finishAccountSetup, getAccountList } from '@/api'

// ==================== 账号管理 ====================
const accounts = ref([])
const accountLoading = ref(false)
const accountStatus = ref('')
const accountStatusType = ref('info')
const currentSessionId = ref('')

const loadAccounts = async () => {
  try {
    const res = await getAccountList()
    if (res.data.code === 200) accounts.value = res.data.data
  } catch (e) { console.error('加载账号列表失败:', e) }
}

const startAddAccount = async () => {
  try {
    accountLoading.value = true
    accountStatus.value = '正在启动浏览器...'
    accountStatusType.value = 'info'
    const res = await startAccountLogin()
    if (res.data.code === 200) {
      currentSessionId.value = res.data.data.sessionId
      accountStatus.value = '浏览器即将打开，请在浏览器中登录淘宝账号'
      accountStatusType.value = 'warning'
    }
  } catch (e) {
    ElMessage.error('启动失败: ' + e.message)
    accountLoading.value = false
    accountStatus.value = ''
  }
}

const confirmLogin = async () => {
  try {
    const res = await confirmAccountLogin(currentSessionId.value)
    if (res.data.code === 200) {
      accountStatus.value = `${res.data.data.accountName} 添加成功！`
      accountStatusType.value = 'success'
      setTimeout(() => {
        ElMessageBox.confirm('是否继续添加下一个账号？', '提示', {
          confirmButtonText: '是，继续添加', cancelButtonText: '否，完成设置', type: 'info'
        }).then(async () => {
          await addAnotherAccount(currentSessionId.value)
          accountStatus.value = 'Cookies已清除，请登录下一个账号'
          accountStatusType.value = 'warning'
          setTimeout(() => promptLoginConfirm(), 3000)
        }).catch(async () => {
          await finishAccountSetup(currentSessionId.value)
          accountLoading.value = false; accountStatus.value = ''
          await loadAccounts()
          ElMessage.success('账号池设置完成！')
        })
      }, 1000)
    }
  } catch (e) { ElMessage.error('确认失败: ' + e.message) }
}

const promptLoginConfirm = () => {
  ElMessageBox.confirm('请在浏览器中完成登录，登录完成后点击"确认"按钮', '等待登录', {
    confirmButtonText: '确认登录完成', cancelButtonText: '取消', type: 'warning', closeOnClickModal: false
  }).then(() => confirmLogin()).catch(() => { accountLoading.value = false; accountStatus.value = '' })
}

// ==================== 采集配置 ====================
const config = ref({ type: 'taobao', keyword: '', pages: 10, minInterval: 2, maxInterval: 5, useProxy: false, proxyApi: '' })
const crawling = ref(false)
const progress = ref(0)
const currentPage = ref(0)
const totalCount = ref(0)
const logs = ref([])
const logContainer = ref(null)
const socket = ref(null)
const taskId = ref(null)

const crawlCompleted = ref(false)
const tmallTotal = ref(15) // 天猫榜单共15个分类
const currentCategory = ref('') // 当前爬取的分类名称

const statusText = computed(() => {
  if (crawlCompleted.value) return '已完成'
  return crawling.value ? '采集中' : '空闲'
})
const statusType = computed(() => {
  if (crawlCompleted.value) return 'success'
  return crawling.value ? 'warning' : 'info'
})
const progressFormat = () => {
  if (config.value.type === 'tmall') {
    return currentCategory.value ? `${currentPage.value}/${tmallTotal.value}类` : `${progress.value}%`
  }
  return `${currentPage.value}/${config.value.pages}页`
}
const scrollToBottom = () => nextTick(() => { if (logContainer.value) logContainer.value.scrollTop = logContainer.value.scrollHeight })

const startCrawl = () => {
  if (accounts.value.length === 0) { ElMessage.warning('请先添加淘宝账号'); return }
  if (config.value.type === 'taobao' && !config.value.keyword) { ElMessage.warning('请输入搜索关键词'); return }
  crawling.value = true; crawlCompleted.value = false; logs.value = []; progress.value = 0; currentPage.value = 0; totalCount.value = 0
  socket.value?.emit('crawl:start', {
    type: config.value.type, keyword: config.value.keyword, pages: config.value.pages,
    minInterval: config.value.minInterval, maxInterval: config.value.maxInterval,
    useProxy: config.value.useProxy, proxyApi: config.value.proxyApi
  })
}

const stopCrawl = () => { socket.value?.emit('crawl:stop', { taskId: taskId.value }); crawling.value = false }

onMounted(() => {
  loadAccounts()
  socket.value = io('http://localhost:5000')
  socket.value.on('crawl:log', (data) => { logs.value.push(data.message); scrollToBottom() })
  socket.value.on('crawl:progress', (data) => { currentPage.value = data.current; progress.value = Math.round((data.current / data.total) * 100); totalCount.value = data.count || 0 })
  socket.value.on('crawl:started', (data) => { taskId.value = data.taskId; logs.value.push(`任务已启动: ${data.taskId}`) })
  socket.value.on('crawl:completed', (data) => {
    crawling.value = false;
    crawlCompleted.value = true;
    progress.value = 100;
    if (data?.totalProducts) totalCount.value = data.totalProducts;
    ElMessage.success(data?.message || '采集完成')
  })
  socket.value.on('crawl:error', (data) => { crawling.value = false; ElMessage.error(data.message) })
  // 天猫榜单相关事件
  socket.value.on('tmall:started', (data) => { taskId.value = data.taskId; logs.value.push(data.message); scrollToBottom() })
  socket.value.on('tmall:progress', (data) => {
    currentPage.value = data.current
    tmallTotal.value = data.total
    currentCategory.value = data.category || ''
    progress.value = Math.round((data.current / data.total) * 100)
  })
  socket.value.on('tmall:log', (data) => { logs.value.push(data.message); scrollToBottom() })
  socket.value.on('tmall:complete', (data) => {
    crawling.value = false
    crawlCompleted.value = true
    progress.value = 100
    totalCount.value = data.totalCount || 0
    currentCategory.value = ''
    ElMessage.success(`天猫榜单采集完成！共获取 ${data.totalCount} 条数据`)
  })
  socket.value.on('tmall:error', (data) => { crawling.value = false; ElMessage.error(data.message) })
  // 账号登录相关事件
  socket.value.on('login:browser-opened', (data) => {
    accountStatus.value = data.message; accountStatusType.value = 'warning'
    setTimeout(() => promptLoginConfirm(), 3000)
  })
  socket.value.on('login:error', (data) => { ElMessage.error(data.message); accountLoading.value = false; accountStatus.value = '' })
})

onUnmounted(() => { socket.value?.disconnect() })
</script>

<style scoped>
.crawl-page { padding: 16px; background: var(--app-admin-bg); min-height: calc(100vh - 70px); display: flex; flex-direction: column; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.header-left { display: flex; align-items: center; gap: 10px; font-size: 17px; font-weight: 700; color: var(--app-admin-text); }
.account-card, .config-card, .progress-card { border-radius: 10px; border: 1px solid var(--app-admin-border); box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04); }
.account-card { margin-bottom: 12px; }
.account-card-compact :deep(.el-card__body) { padding: 12px 16px; }
.account-card-compact .account-list { min-height: 36px; }
.account-empty { padding: 16px 0 20px !important; }
.account-empty :deep(.el-empty__description) { margin-top: 8px; font-size: 16px; }
.account-list { min-height: 36px; }
.account-items { display: flex; flex-wrap: wrap; gap: 10px; }
.account-tag { padding: 10px 16px; font-size: 16px; display: flex; align-items: center; gap: 10px; border-radius: 8px; }
.account-time { margin-left: 10px; font-size: 14px; opacity: 0.7; }
.account-status { margin-top: 8px; }
.config-stack { flex: 1; margin-top: 12px; display: flex; flex-direction: column; gap: 12px; margin-bottom: 0; min-height: 0; }
.config-card { flex-shrink: 0; width: 100%; }
.config-card :deep(.el-form) { max-width: 100%; }
.progress-card { flex: 1; display: flex; flex-direction: column; min-height: 0; width: 100%; }
.progress-card :deep(.el-card__body) { flex: 1; display: flex; flex-direction: column; padding: 14px; min-height: 0; }
.progress-info { margin-bottom: 12px; flex-shrink: 0; }
.progress-info :deep(.el-progress-bar__outer) { height: 26px !important; }
.count-info { margin-top: 10px; color: #334155; font-size: 16px; }
.count-info strong { color: #2f7cf6; font-size: 24px; }
.log-area { border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; background: #f8fafc; flex: 1; display: flex; flex-direction: column; min-height: 0; }
.log-title { font-weight: 700; margin-bottom: 8px; color: #334155; font-size: 16px; }
.log-content { flex: 1; min-height: 160px; max-height: 320px; overflow-y: auto; font-family: 'Consolas', 'Monaco', monospace; font-size: 14px; background: #0f172a; padding: 12px; border-radius: 8px; color: #dbeafe; line-height: 1.7; }
.log-item { padding: 2px 0; border-bottom: 1px solid rgba(255,255,255,0.05); }
.log-item:last-child { border-bottom: none; }
.interval-input { display: flex; align-items: center; gap: 8px; flex-wrap: nowrap; }
.interval-input .interval-unit { white-space: nowrap; }
.interval-sep { color: #909399; font-size: 17px; }
.interval-unit { color: #606266; margin-left: 4px; font-size: 17px; }
.interval-hint { margin-top: 8px; padding: 6px 12px; background: #e6f7ff; border-radius: 4px; color: #1890ff; font-size: 16px; font-weight: 500; white-space: nowrap; }
.config-card :deep(.el-card__body) { padding: 14px; }
.crawl-form :deep(.el-form-item) { margin-bottom: 16px; }
.crawl-form :deep(.el-form-item__label) { font-size: 15px; font-weight: 600; white-space: nowrap; }
.crawl-page :deep(.el-radio__label) { font-size: 15px; white-space: nowrap; }
.crawl-page :deep(.el-card__header span) { font-size: 17px; font-weight: 700; }
.crawl-page :deep(.el-input__inner) { font-size: 15px; }
.crawl-page :deep(.el-button) { font-size: 14px; }
</style>
