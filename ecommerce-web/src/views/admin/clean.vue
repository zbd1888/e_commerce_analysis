<template>
  <div class="clean-page">
    <section class="admin-page-head">
      <div>
        <h2 class="admin-page-title">数据清洗</h2>
        <p class="admin-page-desc">选择待清洗文件，配置清洗规则，预览效果后执行入库并追踪历史批次。</p>
      </div>
      <div class="admin-page-actions">
        <el-tag type="info" effect="plain">待处理 {{ files.length }} 个文件</el-tag>
      </div>
    </section>

    <el-row :gutter="16" class="top-row">
      <!-- 待清洗文件 -->
      <el-col :span="14">
        <el-card class="file-card">
          <template #header><span class="card-title">待清洗文件</span></template>
          <el-table :data="files" @selection-change="handleSelectionChange" size="default" :max-height="320" stripe>
            <el-table-column type="selection" width="55" />
            <el-table-column prop="name" label="文件名" show-overflow-tooltip min-width="240" />
            <el-table-column prop="size" label="大小" width="140" align="center" />
            <el-table-column prop="modifiedTime" label="修改时间" min-width="200" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>

      <!-- 清洗配置 -->
      <el-col :span="10">
        <el-card class="config-card">
          <template #header><span class="card-title">清洗配置</span></template>
          <el-form :model="cleanConfig" label-width="130px" size="default">
            <el-form-item label="最小销量阈值">
              <el-input-number v-model="cleanConfig.minSales" :min="0" :max="1000" style="width: 100%" />
            </el-form-item>
            <el-form-item label="标题清洗规则">
              <div class="checkbox-group">
                <el-checkbox v-model="cleanConfig.removeHtml">去除HTML标签</el-checkbox>
                <el-checkbox v-model="cleanConfig.removeSymbols">去除特殊符号</el-checkbox>
                <el-checkbox v-model="cleanConfig.removeSpaces">去除多余空格</el-checkbox>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button size="large" @click="previewClean" :disabled="selectedFiles.length === 0">预览效果</el-button>
              <el-button size="large" type="primary" @click="startClean" :loading="cleaning" :disabled="selectedFiles.length === 0">
                开始清洗入库
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 预览结果 -->
        <el-card v-if="preview.show" class="preview-card">
          <template #header><span class="card-title">清洗效果预览</span></template>
          <div class="preview-stats">
            <div class="stat-item"><span class="label">总记录数</span><span class="value">{{ preview.total }}</span></div>
            <div class="stat-item"><span class="label">将新增</span><span class="value success">{{ preview.insert }}</span></div>
            <div class="stat-item"><span class="label">将跳过</span><span class="value warning">{{ preview.skip }}</span></div>
            <div class="stat-item"><span class="label">标题清洗</span><span class="value">{{ preview.titleCleaned }} 条</span></div>
          </div>
          <el-table :data="preview.samples" size="small" max-height="180" stripe>
            <el-table-column prop="before" label="清洗前" show-overflow-tooltip />
            <el-table-column label="" width="50" align="center"><template #default><el-icon color="#409EFF"><Right /></el-icon></template></el-table-column>
            <el-table-column prop="after" label="清洗后" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 清洗历史日志 -->
    <el-card class="log-card">
      <template #header><span class="card-title">清洗历史日志</span></template>
      <el-table :data="logs" size="default" stripe class="log-table" :max-height="logTableHeight">
        <el-table-column prop="batchId" label="批次ID" width="240" min-width="240" show-overflow-tooltip />
        <el-table-column prop="fileName" label="文件名" show-overflow-tooltip min-width="300" />
        <el-table-column prop="insertedCount" label="新增" width="110" align="center">
          <template #default="{ row }"><span class="count-success">+{{ row.insertedCount }}</span></template>
        </el-table-column>
        <el-table-column prop="updatedCount" label="更新" width="110" align="center">
          <template #default="{ row }"><span class="count-update">{{ row.updatedCount }}</span></template>
        </el-table-column>
        <el-table-column prop="skippedCount" label="跳过" width="110" align="center">
          <template #default="{ row }"><span class="count-skip">{{ row.skippedCount }}</span></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" effect="dark">
              {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="处理时间" min-width="220" show-overflow-tooltip />
      </el-table>
      <el-pagination v-model:current-page="logPage" :page-size="10" :total="logTotal" layout="total, prev, pager, next" @current-change="fetchLogs" class="log-pagination" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/api/request'
import axios from 'axios'
import { ElMessage } from 'element-plus'

// Python清洗服务地址
const crawlerApi = axios.create({ baseURL: 'http://localhost:5000', timeout: 60000 })

const files = ref([])
const selectedFiles = ref([])
const cleanConfig = ref({ minSales: 10, removeHtml: true, removeSymbols: true, removeSpaces: true })
const cleaning = ref(false)
const logs = ref([])
const logPage = ref(1)
const logTotal = ref(0)
const preview = ref({ show: false, total: 0, insert: 0, skip: 0, titleCleaned: 0, samples: [] })
const logTableHeight = computed(() => Math.max(300, window.innerHeight - 450))

const handleSelectionChange = (selection) => { selectedFiles.value = selection }

const fetchFiles = async () => {
  try {
    const res = await request.get('/admin/pending-files')
    files.value = res.data || []
  } catch (e) { console.error('获取文件列表失败', e) }
}

const fetchLogs = async () => {
  try {
    const res = await request.get('/admin/clean-logs', { params: { page: logPage.value, size: 10 } })
    logs.value = res.data?.records || []
    logTotal.value = res.data?.total || 0
  } catch (e) { console.error('获取日志失败', e) }
}

const previewClean = async () => {
  try {
    const res = await crawlerApi.post('/api/clean/preview', {
      files: selectedFiles.value.map(f => f.name),
      config: cleanConfig.value
    })
    if (res.data.code === 200) {
      preview.value = { show: true, ...res.data.data }
    } else {
      ElMessage.error(res.data.message || '预览失败')
    }
  } catch (e) { ElMessage.error('预览失败: ' + (e.response?.data?.message || e.message)) }
}

const startClean = async () => {
  cleaning.value = true
  try {
    // 从localStorage或sessionStorage获取用户信息
    const userStr = localStorage.getItem('user') || sessionStorage.getItem('user') || '{}'
    const user = JSON.parse(userStr)
    const res = await crawlerApi.post('/api/clean/execute', {
      files: selectedFiles.value.map(f => f.name),
      config: cleanConfig.value,
      operator: user.username || 'admin'
    })
    if (res.data.code === 200) {
      ElMessage.success(res.data.message || '清洗完成')
      fetchFiles()
      fetchLogs()
      preview.value.show = false
    } else {
      ElMessage.error(res.data.message || '清洗失败')
    }
  } catch (e) { ElMessage.error('清洗失败: ' + (e.response?.data?.message || e.message)) }
  finally { cleaning.value = false }
}

onMounted(() => { fetchFiles(); fetchLogs() })
</script>

<style scoped>
.clean-page { padding: 16px; background: var(--app-admin-bg); min-height: calc(100vh - 70px); display: flex; flex-direction: column; }
.top-row { flex-shrink: 0; }
.card-title { font-size: 17px; font-weight: 700; color: var(--app-admin-text); }
.file-card, .config-card, .preview-card, .log-card { border-radius: 10px; border: 1px solid var(--app-admin-border); box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04); }
.file-card, .config-card { margin-bottom: 12px; }
.file-card :deep(.el-card__body), .config-card :deep(.el-card__body) { padding: 12px 14px; }
.file-card :deep(.el-table) { font-size: 15px; }
.file-card :deep(.el-table th) { color: #475569; font-size: 15px; font-weight: 700; }
.file-card :deep(.el-table td) { font-size: 15px; }
.file-card :deep(.el-table .cell) { white-space: nowrap; }
.checkbox-group { display: flex; flex-direction: column; gap: 10px; }
.checkbox-group :deep(.el-checkbox__label) { font-size: 15px; }
.preview-card { margin-top: 10px; }
.preview-card :deep(.el-table) { font-size: 19px; }
.preview-stats { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 10px; padding: 10px; background: #f5f7fa; border-radius: 6px; }
.stat-item { display: flex; justify-content: space-between; padding: 8px 12px; background: #fff; border-radius: 8px; border: 1px solid #e2e8f0; }
.stat-item .label { color: #64748b; font-size: 15px; }
.stat-item .value { font-weight: 700; font-size: 16px; color: #111827; }
.stat-item .value.success { color: #67C23A; }
.stat-item .value.warning { color: #E6A23C; }
.log-card { margin-top: 10px; flex: 1; display: flex; flex-direction: column; min-height: 0; }
.log-card :deep(.el-card__body) { padding: 12px 14px; flex: 1; display: flex; flex-direction: column; min-height: 0; }
.log-card :deep(.el-table) { font-size: 15px; }
.log-card :deep(.el-table th) { color: #475569; font-size: 15px; font-weight: 700; }
.log-card :deep(.el-table td) { font-size: 15px; }
.log-card :deep(.el-table .cell) { white-space: nowrap; }
.count-success { color: #16a34a; font-weight: 700; font-size: 15px; }
.count-update { color: #2f7cf6; font-weight: 700; font-size: 15px; }
.count-skip { color: #94a3b8; font-size: 15px; }
.log-pagination { margin-top: 8px; flex-shrink: 0; justify-content: flex-end; }
.log-pagination :deep(.el-pagination__total),
.log-pagination :deep(.el-pager li) { font-size: 18px; }
.config-card :deep(.el-form-item__label) { font-size: 15px; font-weight: 600; white-space: nowrap; }
.clean-page :deep(.el-button) { font-size: 14px; }
.clean-page :deep(.el-input-number .el-input__inner) { font-size: 15px; }
.clean-page :deep(.el-form-item) { margin-bottom: 18px; }
</style>
