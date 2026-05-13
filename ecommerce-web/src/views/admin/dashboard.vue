<template>
  <div class="admin-dashboard">
    <section class="admin-page-head dashboard-head">
      <div>
        <h2 class="admin-page-title">数据质量监控大屏</h2>
        <p class="admin-page-desc">统一查看商品数据规模、清洗结果和关键质量指标，快速定位数据治理状态。</p>
      </div>
      <div class="admin-page-actions hero-score">
        <span>综合质量评分</span>
        <strong :style="{ color: getColor(avgQuality) }">{{ avgQuality }}%</strong>
      </div>
    </section>

    <!-- 核心指标卡片 -->
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-blue">
          <div class="stat-icon"><el-icon><Document /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalProducts?.toLocaleString() }}</div>
            <div class="stat-label">商品总数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-green">
          <div class="stat-icon"><el-icon><FolderOpened /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalCategories }}</div>
            <div class="stat-label">品类数量</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-amber">
          <div class="stat-icon"><el-icon><Files /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.pendingFiles }}</div>
            <div class="stat-label">待清洗文件</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card stat-rose">
          <div class="stat-icon"><el-icon><User /></el-icon></div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalUsers }}</div>
            <div class="stat-label">用户数量</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 上下结构：最近清洗日志（上）+ 数据质量指标（下） -->
    <div class="content-stack">
      <!-- 最近清洗日志（上）：可折叠下拉滚动区域 -->
      <el-card class="log-card">
        <template #header>
          <div class="card-header">
            <span class="card-title">最近清洗日志</span>
            <el-tag type="info" effect="plain">最近 50 条</el-tag>
          </div>
        </template>
        <el-collapse v-model="logCollapseActive">
          <el-collapse-item name="log">
            <template #title>
              <span class="collapse-title">点击展开/收起查看清洗日志</span>
            </template>
            <div class="log-scroll-wrap">
              <el-table :data="cleanLogs" size="default" max-height="360" stripe>
            <el-table-column prop="fileName" label="文件名" show-overflow-tooltip min-width="280" />
            <el-table-column prop="insertedCount" label="新增" width="90" align="center">
              <template #default="{ row }"><span class="count-success">+{{ row.insertedCount }}</span></template>
            </el-table-column>
            <el-table-column prop="skippedCount" label="跳过" width="90" align="center">
              <template #default="{ row }"><span class="count-skip">{{ row.skippedCount }}</span></template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'" effect="dark">
                  {{ row.status === 'SUCCESS' ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="处理时间" width="180" />
          </el-table>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-card>

      <!-- 数据质量指标（下） -->
      <el-card class="quality-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">数据质量指标</span>
              <span class="card-subtitle">完整性、有效率与综合评分</span>
            </div>
          </template>
          <div class="quality-item">
            <div class="quality-header">
              <span class="quality-name">数据完整性</span>
              <span class="quality-percent" :style="{ color: getColor(quality.completeness) }">{{ quality.completeness }}%</span>
            </div>
            <el-progress :percentage="quality.completeness" :color="getColor(quality.completeness)" :stroke-width="18" :show-text="false" />
          </div>
          <div class="quality-item">
            <div class="quality-header">
              <span class="quality-name">价格有效率</span>
              <span class="quality-percent" :style="{ color: getColor(quality.priceValid) }">{{ quality.priceValid }}%</span>
            </div>
            <el-progress :percentage="quality.priceValid" :color="getColor(quality.priceValid)" :stroke-width="18" :show-text="false" />
          </div>
          <div class="quality-item">
            <div class="quality-header">
              <span class="quality-name">销量有效率</span>
              <span class="quality-percent" :style="{ color: getColor(quality.saleValid) }">{{ quality.saleValid }}%</span>
            </div>
            <el-progress :percentage="quality.saleValid" :color="getColor(quality.saleValid)" :stroke-width="18" :show-text="false" />
          </div>
          <div class="quality-item">
            <div class="quality-header">
              <span class="quality-name">图片有效率</span>
              <span class="quality-percent" :style="{ color: getColor(quality.imageValid) }">{{ quality.imageValid }}%</span>
            </div>
            <el-progress :percentage="quality.imageValid" :color="getColor(quality.imageValid)" :stroke-width="18" :show-text="false" />
          </div>
          <div class="quality-summary">
            <div class="summary-item">
              <span class="summary-label">综合评分</span>
              <span class="summary-value" :style="{ color: getColor(avgQuality) }">{{ avgQuality }}%</span>
            </div>
          </div>
        </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/api/request'

const stats = ref({ totalProducts: 0, totalCategories: 0, pendingFiles: 0, totalUsers: 0 })
const cleanLogs = ref([])
const quality = ref({ completeness: 0, priceValid: 0, saleValid: 0, imageValid: 0 })
const logCollapseActive = ref(['log'])

const getColor = (val) => val >= 90 ? '#67C23A' : val >= 70 ? '#E6A23C' : '#F56C6C'
const avgQuality = computed(() => Math.round((quality.value.completeness + quality.value.priceValid + quality.value.saleValid + quality.value.imageValid) / 4))

const fetchData = async () => {
  try {
    const [statsRes, logsRes, qualityRes] = await Promise.all([
      request.get('/admin/stats'),
      request.get('/admin/clean-logs', { params: { page: 1, size: 50 } }),
      request.get('/admin/quality')
    ])
    stats.value = statsRes.data || stats.value
    cleanLogs.value = logsRes.data?.records || []
    quality.value = qualityRes.data || quality.value
  } catch (e) { console.error('加载数据失败', e) }
}

onMounted(fetchData)
</script>

<style scoped>
.admin-dashboard {
  padding: 16px;
  min-height: calc(100vh - 70px);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.hero-score {
  min-width: 166px;
  padding: 10px 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
  color: #64748b;
}

.hero-score strong {
  font-size: 28px;
  line-height: 1;
}

.stat-cards { margin-bottom: 0; }

.stat-card {
  position: relative;
  min-height: 128px;
  overflow: hidden;
  border-radius: 10px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}

.stat-card :deep(.el-card__body) {
  height: 100%;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 18px;
}

.stat-card::after {
  content: '';
  position: absolute;
  right: -42px;
  top: -42px;
  width: 132px;
  height: 132px;
  border-radius: 50%;
  opacity: 0.12;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 18px 32px rgba(15, 23, 42, 0.12);
}

.stat-blue::after { background: #2563eb; }
.stat-green::after { background: #16a34a; }
.stat-amber::after { background: #d97706; }
.stat-rose::after { background: #e11d48; }

.stat-icon {
  width: 68px;
  height: 68px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 14px 26px rgba(15, 23, 42, 0.14);
  flex-shrink: 0;
}

.stat-blue .stat-icon { background: linear-gradient(135deg, #2563eb, #60a5fa); }
.stat-green .stat-icon { background: linear-gradient(135deg, #16a34a, #86efac); }
.stat-amber .stat-icon { background: linear-gradient(135deg, #d97706, #fbbf24); }
.stat-rose .stat-icon { background: linear-gradient(135deg, #e11d48, #fb7185); }

.stat-icon .el-icon { font-size: 34px; color: #fff; }
.stat-value { font-size: 34px; font-weight: 800; color: #111827; line-height: 1.1; }
.stat-label { color: #64748b; font-size: 16px; margin-top: 8px; }

.content-stack {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.card-title { font-size: 20px; font-weight: 700; color: #111827; }
.card-subtitle { color: #94a3b8; font-size: 14px; }

.log-card,
.quality-card {
  border-radius: 10px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.07);
}

.log-card { flex-shrink: 0; }
.log-card :deep(.el-card__body) { padding: 12px 18px 18px; }
.log-card :deep(.el-collapse) { border: none; }
.log-card :deep(.el-collapse-item__header) { font-size: 16px; border-bottom-color: #edf2f7; }
.log-card :deep(.el-collapse-item__wrap) { border-bottom: none; }
.collapse-title { font-size: 16px; color: #64748b; }
.log-scroll-wrap { overflow: hidden; }
.log-card :deep(.el-table) { font-size: 16px; border-radius: 12px; overflow: hidden; }
.log-card :deep(.el-table th) { font-size: 16px; font-weight: 700; color: #475569; background: #f8fafc; }
.log-card :deep(.el-table td) { font-size: 16px; color: #475569; }
.count-success { color: #16a34a; font-weight: 700; font-size: 17px; }
.count-skip { color: #94a3b8; font-size: 17px; }

.quality-card { flex: 1; min-height: 0; }
.quality-card :deep(.el-card__body) { padding: 22px 26px; }
.quality-item { margin-bottom: 22px; }
.quality-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.quality-name { font-size: 17px; color: #475569; font-weight: 700; }
.quality-percent { font-size: 24px; font-weight: 800; }
.quality-card :deep(.el-progress-bar__outer) { background: #e2e8f0; }
.quality-summary { margin-top: 18px; padding-top: 18px; border-top: 1px solid #e2e8f0; }
.summary-item { display: flex; justify-content: space-between; align-items: center; }
.summary-label { font-size: 20px; color: #111827; font-weight: 700; }
.summary-value { font-size: 40px; font-weight: 800; }
</style>
