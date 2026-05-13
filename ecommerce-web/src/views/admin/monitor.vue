<template>
  <div class="monitor-container">
    <section class="admin-page-head">
      <div>
        <h2 class="admin-page-title">系统监控</h2>
        <p class="admin-page-desc">监控后端、爬虫和数据库状态，查看关键数据统计与最近采集任务。</p>
      </div>
      <div class="admin-page-actions">
        <el-button type="primary" @click="handleRefresh" :loading="refreshing">
          <el-icon v-if="!refreshing"><Refresh /></el-icon> {{ refreshing ? '刷新中...' : '刷新' }}
        </el-button>
      </div>
    </section>

    <el-card class="monitor-card">
      <template #header>
        <div class="card-header">
          <span>服务状态</span>
          <el-tag type="info" effect="plain">实时概览</el-tag>
        </div>
      </template>

      <!-- 服务状态 -->
      <el-row :gutter="20">
        <el-col :span="8">
          <el-card shadow="hover" class="status-card">
            <div class="status-icon" :class="monitorData.backendStatus === 'running' ? 'success' : 'error'">
              <el-icon :size="40"><Monitor /></el-icon>
            </div>
            <h3>后端服务</h3>
            <el-tag :type="monitorData.backendStatus === 'running' ? 'success' : 'danger'">
              {{ monitorData.backendStatus === 'running' ? '运行中' : '异常' }}
            </el-tag>
            <p class="status-time">{{ monitorData.backendUptime || '-' }}</p>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="status-card">
            <div class="status-icon" :class="monitorData.crawlerStatus === 'running' ? 'success' : 'error'">
              <el-icon :size="40"><Connection /></el-icon>
            </div>
            <h3>爬虫服务</h3>
            <el-tag :type="monitorData.crawlerStatus === 'running' ? 'success' : 'danger'">
              {{ monitorData.crawlerStatus === 'running' ? '运行中' : '异常' }}
            </el-tag>
            <p class="status-time">最后运行: {{ monitorData.lastCrawlTime || '-' }}</p>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="status-card">
            <div class="status-icon" :class="monitorData.dbStatus === 'connected' ? 'success' : 'error'">
              <el-icon :size="40"><Coin /></el-icon>
            </div>
            <h3>数据库</h3>
            <el-tag :type="monitorData.dbStatus === 'connected' ? 'success' : 'danger'">
              {{ monitorData.dbStatus === 'connected' ? '已连接' : '断开' }}
            </el-tag>
            <p class="status-time">表数量: {{ monitorData.dbTableCount || 0 }}</p>
          </el-card>
        </el-col>
      </el-row>

      <!-- 数据统计 -->
      <el-row :gutter="20" style="margin-top: 20px">
        <el-col :span="6">
          <el-statistic title="商品总数" :value="monitorData.totalProducts || 0">
            <template #suffix>个</template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="今日新增" :value="monitorData.todayNewProducts || 0">
            <template #suffix>个</template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="清洗成功率" :value="monitorData.cleanSuccessRate || 0">
            <template #suffix>%</template>
          </el-statistic>
        </el-col>
        <el-col :span="6">
          <el-statistic title="有效商品数" :value="monitorData.validProducts || 0">
            <template #suffix>个</template>
          </el-statistic>
        </el-col>
      </el-row>

      <!-- 最近爬取任务：可折叠下拉滚动区域 -->
      <el-divider content-position="left">最近爬取任务</el-divider>
      <el-collapse v-model="taskCollapseActive">
        <el-collapse-item name="task">
          <template #title>
            <span class="task-collapse-title">点击展开/收起查看爬取任务列表</span>
          </template>
          <div class="task-scroll-wrap">
            <el-table :data="monitorData.recentTasks || []" border stripe size="default" class="task-table" :max-height="taskTableHeight">
        <el-table-column type="index" label="#" width="70" />
        <el-table-column prop="keyword" label="品类" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : row.status === 'running' ? 'warning' : 'danger'" size="default">
              {{ row.status === 'completed' ? '完成' : row.status === 'running' ? '运行中' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="总数" width="110" />
        <el-table-column prop="successCount" label="成功" width="110" />
        <el-table-column prop="failCount" label="失败" width="110" />
        <el-table-column prop="startTime" label="开始时间" min-width="220" show-overflow-tooltip />
        <el-table-column prop="endTime" label="结束时间" min-width="220" show-overflow-tooltip />
        <el-table-column prop="duration" label="耗时" width="120" />
      </el-table>
          </div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/api/request'
import { ElMessage } from 'element-plus'

const taskCollapseActive = ref(['task'])
const taskTableHeight = computed(() => Math.max(360, window.innerHeight - 480))
const monitorData = ref({
  backendStatus: 'running',
  backendUptime: '-',
  crawlerStatus: 'unknown',
  lastCrawlTime: '-',
  dbStatus: 'connected',
  dbConnections: 0,
  totalProducts: 0,
  todayNewProducts: 0,
  cleanSuccessRate: 0,
  validProducts: 0,
  recentTasks: []
})
const refreshing = ref(false)

// 获取监控数据（refresh=true时实时刷新缓存）
const fetchMonitorData = async (refresh = false) => {
  try {
    const res = await request.get('/admin/monitor/status', { params: { refresh } })
    monitorData.value = res.data || monitorData.value
  } catch (e) {
    console.error('获取监控数据失败', e)
    ElMessage.error('获取监控数据失败，该功能需要后端接口支持')
    // 使用模拟数据
    monitorData.value = {
      backendStatus: 'running',
      backendUptime: '运行中',
      crawlerStatus: 'unknown',
      lastCrawlTime: '暂无数据',
      dbStatus: 'connected',
      dbConnections: 10,
      totalProducts: 0,
      todayNewProducts: 0,
      cleanSuccessRate: 0,
      validProducts: 0,
      recentTasks: []
    }
  }
}

// 刷新按钮点击，实时刷新统计数据
const handleRefresh = async () => {
  refreshing.value = true
  await fetchMonitorData(true)
  refreshing.value = false
  ElMessage.success('数据已刷新（实时统计）')
}

onMounted(() => fetchMonitorData(false))
</script>

<style scoped>
.monitor-container { padding: 16px; background: var(--app-admin-bg); min-height: calc(100vh - 70px); display: flex; flex-direction: column; }
.monitor-card { flex: 1; display: flex; flex-direction: column; min-height: 0; border-radius: 10px; border: 1px solid var(--app-admin-border); box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04); }
.monitor-card :deep(.el-card__body) { flex: 1; display: flex; flex-direction: column; padding: 16px 18px 12px; min-height: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header span { font-size: 17px; font-weight: 700; color: var(--app-admin-text); }
.card-header :deep(.el-button) { font-size: 14px; }

.status-card { text-align: center; padding: 18px 10px; border-radius: 10px; border: 1px solid #e2e8f0; }
.status-card :deep(.el-tag) { font-size: 13px; }
.status-icon { font-size: 48px; margin-bottom: 12px; }
.status-icon.success { color: #67C23A; }
.status-icon.error { color: #F56C6C; }
.status-card h3 { margin: 10px 0; font-size: 18px; color: #111827; }
.status-time { margin-top: 8px; font-size: 14px; color: #64748b; }
.monitor-container :deep(.el-statistic__head) { font-size: 15px; }
.monitor-container :deep(.el-statistic__content) { font-size: 30px; }
.monitor-container :deep(.el-divider__text) { font-size: 17px; font-weight: 700; color: #334155; }

.task-collapse-title { font-size: 15px; color: #64748b; }
.monitor-container :deep(.el-collapse-item__header) { font-size: 15px; }
.monitor-container :deep(.el-collapse) { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.monitor-container :deep(.el-collapse-item__wrap) { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.task-scroll-wrap { overflow: hidden; flex: 1; min-height: 0; }
.task-table { width: 100%; }
.monitor-container :deep(.task-table) { font-size: 15px; }
.monitor-container :deep(.task-table .el-table th) { color: #475569; font-size: 15px; font-weight: 700; }
.monitor-container :deep(.task-table .el-table td) { font-size: 15px; }
.monitor-container :deep(.task-table .el-table .cell) { white-space: nowrap; }
.monitor-container :deep(.task-table .el-tag) { font-size: 13px; }
</style>
