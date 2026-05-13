<template>
  <div class="prediction-page">
    <section class="prediction-hero">
      <div>
        <h1>选品助手</h1>
        <p>结合品类、价格、发货地和店铺标签，生成成功率、销量区间与优化建议。</p>
      </div>
    </section>

    <!-- 综合预测输入 -->
    <el-card class="predict-card">
      <template #header>
        <div class="section-header">
          <div>
            <span class="section-title">综合商品预测</span>
            <p class="section-desc">补全关键信息后开始预测，商品标题可用于辅助判断关键词表现。</p>
          </div>
          <el-tag type="primary" effect="light">实时分析</el-tag>
        </div>
      </template>
      <el-form :model="form" label-width="100px" :inline="false">
        <el-row :gutter="20">
          <el-col :xs="24" :md="8">
            <el-form-item label="商品品类" required>
              <el-select v-model="form.keyword" placeholder="请选择品类" filterable style="width: 100%">
                <el-option v-for="k in keywords" :key="k" :label="k" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="商品价格" required>
              <el-input-number v-model="form.price" :min="1" :max="99999" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="8">
            <el-form-item label="发货地">
              <el-input v-model="form.location" placeholder="如：广东 深圳" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :xs="24" :md="8">
            <el-form-item label="店铺标签">
              <el-select v-model="form.shopTag" placeholder="选择店铺类型" clearable style="width: 100%">
                <el-option label="品牌旗舰店" value="品牌旗舰店" />
                <el-option label="官方旗舰店" value="官方旗舰店" />
                <el-option label="品牌专卖店" value="品牌专卖店" />
                <el-option label="企业店铺" value="企业店铺" />
                <el-option label="个人店铺" value="个人店铺" />
                <el-option label="无标签" value="" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :md="16">
            <el-form-item label="商品标题">
              <el-input v-model="form.title" placeholder="输入商品标题（可选）" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="form-actions">
          <el-button type="primary" size="large" @click="predict" :loading="loading" class="primary-action">
            <el-icon><Search /></el-icon> 开始综合预测
          </el-button>
          <span class="action-hint">预测结果将生成评分、销量区间和标题关键词建议</span>
        </div>
      </el-form>
    </el-card>

    <!-- 预测结果 -->
    <template v-if="result.success">
      <el-row :gutter="20" class="result-row">
        <!-- 综合评分 -->
        <el-col :xs="24" :lg="8">
          <el-card class="score-card">
            <div class="score-circle" :style="{ borderColor: getScoreColor(result.successScore) }">
              <div class="score-value">{{ result.successScore }}</div>
              <div class="score-label">成功率评分</div>
            </div>
            <div class="recommendation">{{ result.overallRecommendation }}</div>
          </el-card>
        </el-col>

        <!-- 各维度评分 -->
        <el-col :xs="24" :lg="16">
          <el-card class="dimension-card">
            <template #header><span class="section-title">各维度评分</span></template>
            <el-row :gutter="10">
              <el-col :xs="12" :md="6">
                <div class="dim-score">
                  <el-progress type="circle" :percentage="result.priceScore" :color="getScoreColor(result.priceScore)" :width="100" />
                  <div class="dim-label">价格竞争力</div>
                  <div class="dim-tip">{{ result.priceSuggestion }}</div>
                </div>
              </el-col>
              <el-col :xs="12" :md="6">
                <div class="dim-score">
                  <el-progress type="circle" :percentage="result.tagScore" :color="getScoreColor(result.tagScore)" :width="100" />
                  <div class="dim-label">店铺标签</div>
                  <div class="dim-tip">{{ result.tagSuggestion }}</div>
                </div>
              </el-col>
              <el-col :xs="12" :md="6">
                <div class="dim-score">
                  <el-progress type="circle" :percentage="result.locationScore" :color="getScoreColor(result.locationScore)" :width="100" />
                  <div class="dim-label">发货地优势</div>
                  <div class="dim-tip">{{ result.locationSuggestion }}</div>
                </div>
              </el-col>
              <el-col :xs="12" :md="6">
                <div class="dim-score">
                  <el-progress type="circle" :percentage="result.hotScore" :color="getScoreColor(result.hotScore)" :width="100" />
                  <div class="dim-label">品类热度</div>
                  <div class="dim-tip">样本数: {{ result.sampleSize }}</div>
                </div>
              </el-col>
            </el-row>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="result-row">
        <!-- 销量预测 -->
        <el-col :xs="24" :lg="12">
          <el-card class="sales-card">
            <template #header>
              <div class="card-header-flex">
                <span class="section-title">销量预测</span>
                <el-tag v-if="result.predictionConfidence" :type="result.predictionConfidence >= 70 ? 'success' : 'warning'" size="small">
                  置信度: {{ result.predictionConfidence }}%
                </el-tag>
              </div>
            </template>
            <div class="sales-prediction-main">
              <div class="sales-value-box">
                <div class="sales-label">预测销量</div>
                <div class="sales-value">{{ formatSales(result.predictedSalesMid) }}</div>
                <div class="sales-range">区间: {{ formatSales(result.predictedSalesLow) }} ~ {{ formatSales(result.predictedSalesHigh) }}</div>
              </div>
            </div>
            <el-descriptions :column="2" border size="small" style="margin-top: 15px">
              <el-descriptions-item label="品类均价">¥{{ result.categoryAvgPrice }}</el-descriptions-item>
              <el-descriptions-item label="最优价格区间">{{ result.bestPriceRange || '-' }}</el-descriptions-item>
              <el-descriptions-item label="热门发货地">{{ result.topLocation }}</el-descriptions-item>
              <el-descriptions-item label="推荐发货地">{{ result.bestSalesLocation || '-' }}</el-descriptions-item>
              <el-descriptions-item label="相似样本数">{{ result.similarSampleCount || result.sampleSize }}</el-descriptions-item>
              <el-descriptions-item label="竞品数量">
                <el-tag :type="result.competitionLevel === '激烈' ? 'danger' : result.competitionLevel === '中等' ? 'warning' : 'success'" size="small">
                  {{ result.competitorCount || 0 }} ({{ result.competitionLevel || '未知' }})
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <!-- 优化建议 -->
        <el-col :xs="24" :lg="12">
          <el-card class="suggestion-card">
            <template #header><span class="section-title">优化建议</span></template>
            <div class="suggestion-list">
              <div v-for="(s, i) in result.suggestions" :key="i" class="suggestion-item" :class="'suggestion-' + s.status">
                <div class="suggestion-header">
                  <el-icon v-if="s.status === 'good'" color="#67C23A"><CircleCheckFilled /></el-icon>
                  <el-icon v-else-if="s.status === 'normal'" color="#E6A23C"><WarningFilled /></el-icon>
                  <el-icon v-else color="#F56C6C"><CircleCloseFilled /></el-icon>
                  <span class="suggestion-text">{{ s.text }}</span>
                  <el-tag :type="s.status === 'good' ? 'success' : s.status === 'normal' ? 'warning' : 'danger'" size="small" style="margin-left: auto">
                    {{ s.score }}分
                  </el-tag>
                </div>
                <div class="suggestion-detail">{{ s.detail }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 标题关键词建议 -->
      <el-row :gutter="20" class="result-row" v-if="result.titleKeywords && result.titleKeywords.length > 0">
        <el-col :span="24">
          <el-card class="keyword-card">
            <template #header>
              <div class="card-header-flex">
                <span class="section-title">标题关键词建议</span>
                <el-tag type="info" size="small">基于爆款商品标题分析</el-tag>
              </div>
            </template>
            <div class="title-keyword-tip" v-if="result.titleKeywordSuggestion">
              <el-alert :title="result.titleKeywordSuggestion" type="success" :closable="false" show-icon />
            </div>
            <div class="keyword-tags" style="margin-top: 12px">
              <el-tag
                v-for="(kw, i) in result.titleKeywords"
                :key="i"
                :type="i < 3 ? '' : 'info'"
                :effect="i < 3 ? 'dark' : 'light'"
                size="large"
                class="keyword-tag"
              >
                {{ kw.word }}
                <span class="keyword-count">{{ kw.count }}次</span>
              </el-tag>
            </div>
          </el-card>
        </el-col>
      </el-row>

    </template>

    <!-- 报告下载 -->
    <el-card class="report-card">
      <template #header>
        <div class="section-header">
          <div>
            <span class="section-title">报告下载</span>
            <p class="section-desc">导出商品数据、市场分析报告，或手动刷新分析缓存。</p>
          </div>
        </div>
      </template>
      <div class="report-actions">
        <el-button type="primary" @click="downloadProducts">下载商品数据 (Excel)</el-button>
        <el-button type="success" @click="downloadMarketAnalysis">下载市场分析报告 (Excel)</el-button>
        <el-button type="warning" @click="refreshCache" :loading="refreshing">刷新分析缓存</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { Search, CircleCheckFilled, WarningFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import request from '@/api/request'
import { ElMessage } from 'element-plus'
import { useCacheStore } from '@/store/cache'

const cacheStore = useCacheStore()
const keywords = computed(() => cacheStore.keywords)
const form = ref({ keyword: '', price: 100, location: '', shopTag: '', title: '' })
const result = ref({})
const loading = ref(false)
const refreshing = ref(false)

const formatSales = (val) => {
  if (val == null) return '-'
  return val >= 10000 ? (val / 10000).toFixed(1) + '万' : val.toLocaleString()
}
const getScoreColor = (score) => score >= 70 ? '#67C23A' : score >= 50 ? '#E6A23C' : '#F56C6C'

const fetchKeywords = async () => {
  await cacheStore.fetchKeywords()
}

const predict = async () => {
  if (!form.value.keyword) return ElMessage.warning('请选择品类')
  if (!form.value.price || form.value.price <= 0) return ElMessage.warning('请输入有效价格')
  loading.value = true
  try {
    const res = await request.get('/analysis/predict/comprehensive', {
      params: {
        keyword: form.value.keyword,
        price: form.value.price,
        location: form.value.location || '',
        shopTag: form.value.shopTag || '',
        title: form.value.title || ''
      }
    })
    result.value = res.data || {}
    if (!result.value.success) {
      ElMessage.warning(result.value.message || '预测失败')
    }
  } catch (e) {
    ElMessage.error('预测请求失败')
  }
  loading.value = false
}

const downloadProducts = () => { window.open('/api/report/export/products', '_blank') }
const downloadMarketAnalysis = () => { window.open('/api/report/export/market-analysis', '_blank') }

const refreshCache = async () => {
  refreshing.value = true
  await request.post('/report/refresh-cache')
  ElMessage.success('缓存刷新成功')
  refreshing.value = false
}

onMounted(fetchKeywords)
</script>

<style scoped>
.prediction-page {
  padding: 18px 20px 28px;
  min-height: calc(100vh - 70px);
  background:
    radial-gradient(circle at top left, rgba(45, 127, 249, 0.16), transparent 30%),
    linear-gradient(180deg, #eef5ff 0%, #f7faff 58%, #eef4fb 100%);
  color: #1f2d3d;
}

.prediction-hero {
  min-height: 118px;
  padding: 24px 28px;
  border-radius: 12px;
  background:
    linear-gradient(135deg, rgba(11, 31, 63, 0.98), rgba(29, 78, 216, 0.88));
  color: #fff;
  display: flex;
  align-items: center;
  border: 1px solid rgba(96, 165, 250, 0.24);
  box-shadow: 0 16px 34px rgba(37, 99, 235, 0.16);
}

.prediction-hero h1 {
  margin: 0;
  font-size: 32px;
  line-height: 1.2;
  font-weight: 800;
}

.prediction-hero p {
  max-width: 760px;
  margin: 10px 0 0;
  color: #dbeafe;
  font-size: 16px;
  line-height: 1.7;
}

.predict-card,
.score-card,
.dimension-card,
.sales-card,
.suggestion-card,
.keyword-card,
.report-card {
  margin-top: 18px;
  border-radius: 12px;
  border: 1px solid #dbe6f4;
  background: #ffffff;
  box-shadow: 0 12px 28px rgba(30, 64, 175, 0.08);
}

.predict-card :deep(.el-card__body) {
  padding: 24px 26px 22px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.section-title {
  display: block;
  max-width: 100%;
  font-size: 21px;
  font-weight: 800;
  color: #10243f;
  overflow-wrap: anywhere;
}

.section-desc {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.prediction-page :deep(.el-card__header) {
  padding: 18px 22px;
  border-bottom: 1px solid #e5edf7;
  background: #f8fbff;
}

.prediction-page :deep(.el-form-item__label) {
  color: #334155;
  font-size: 16px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.prediction-page :deep(.el-input__wrapper),
.prediction-page :deep(.el-input-number .el-input__wrapper),
.prediction-page :deep(.el-select .el-input__wrapper) {
  min-height: 42px;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 0 0 1px #d8e2ef inset;
}

.prediction-page :deep(.el-input__wrapper:hover),
.prediction-page :deep(.el-input-number .el-input__wrapper:hover),
.prediction-page :deep(.el-select .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #2f7cf6 inset;
}

.prediction-page :deep(.el-input__inner),
.prediction-page :deep(.el-input-number .el-input__inner) {
  color: #1f2937;
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.prediction-page :deep(.el-input__inner::placeholder) { color: #94a3b8; }
.prediction-page :deep(.el-input-number__decrease),
.prediction-page :deep(.el-input-number__increase) {
  background: #f1f5f9;
  color: #475569;
}
.prediction-page :deep(.el-descriptions__label) { color: #475569; font-size: 16px; background: #f8fafc; }
.prediction-page :deep(.el-descriptions__content) { color: #1f2937; font-size: 16px; background: #fff; overflow-wrap: anywhere; }
.prediction-page :deep(.el-descriptions__cell) { border-color: #e2e8f0 !important; }
.prediction-page :deep(.el-button) { font-size: 16px; }

.form-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-left: 100px;
  margin-top: 2px;
}

.primary-action {
  min-width: 178px;
  border-radius: 10px;
  font-weight: 700;
}

.action-hint {
  color: #64748b;
  font-size: 14px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.result-row { margin-top: 18px; }

.score-card {
  text-align: center;
  overflow: hidden;
  background: #ffffff;
}

.score-card :deep(.el-card__body) {
  padding: 30px 24px;
}

.score-circle {
  width: 178px;
  height: 178px;
  border-radius: 50%;
  border: 8px solid #67C23A;
  margin: 0 auto 18px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background: #fff;
  box-shadow: inset 0 0 0 12px #eef5ff, 0 16px 30px rgba(30, 64, 175, 0.1);
}

.score-value { font-size: 56px; font-weight: 800; color: #10243f; }
.score-label { font-size: 16px; color: #64748b; }
.recommendation { font-size: 20px; color: #10243f; margin-top: 12px; font-weight: 700; overflow-wrap: anywhere; }

.dimension-card :deep(.el-card__body) {
  padding: 24px 18px;
}

.dim-score {
  min-height: 194px;
  text-align: center;
  padding: 14px 10px;
  border-radius: 14px;
  background: #f8fbff;
  border: 1px solid #e5edf7;
}

.dim-label { margin-top: 12px; font-weight: 800; color: #10243f; font-size: 17px; overflow-wrap: anywhere; }
.dim-tip { font-size: 14px; color: #64748b; margin-top: 6px; min-height: 40px; line-height: 1.45; overflow-wrap: anywhere; }

.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.sales-card :deep(.el-card__body),
.suggestion-card :deep(.el-card__body),
.keyword-card :deep(.el-card__body),
.report-card :deep(.el-card__body) {
  padding: 22px;
}

.sales-prediction-main {
  text-align: center;
  padding: 24px 18px;
  background: linear-gradient(135deg, #eef5ff, #f8fbff);
  border: 1px solid #dbeafe;
  border-radius: 16px;
}

.sales-value-box .sales-label { font-size: 16px; color: #64748b; }
.sales-value-box .sales-value { font-size: 52px; font-weight: 800; color: #2563eb; margin: 8px 0; overflow-wrap: anywhere; }
.sales-value-box .sales-range { font-size: 16px; color: #64748b; overflow-wrap: anywhere; }

.recommendation-box { margin-top: 15px; padding: 10px; background: #f5f7fa; border-radius: 6px; }
.rec-item { font-size: 15px; color: #606266; }
.rec-label { color: #909399; }

.suggestion-list { display: flex; flex-direction: column; gap: 12px; }
.suggestion-item {
  padding: 16px 18px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  border-left: 5px solid #dcdfe6;
  background: #fff;
}
.suggestion-good { border-left-color: #22c55e; background: #f0fdf4; }
.suggestion-normal { border-left-color: #f59e0b; background: #fffbeb; }
.suggestion-warning { border-left-color: #ef4444; background: #fef2f2; }
.suggestion-header { display: flex; align-items: center; gap: 8px; }
.suggestion-text { font-size: 17px; font-weight: 800; color: #10243f; overflow-wrap: anywhere; }
.suggestion-detail { font-size: 15px; color: #64748b; margin-top: 6px; padding-left: 26px; line-height: 1.55; overflow-wrap: anywhere; }

.keyword-tags { display: flex; flex-wrap: wrap; gap: 10px; }
.keyword-tag { cursor: default; font-size: 16px; border-radius: 999px; }
.keyword-count { font-size: 14px; margin-left: 4px; opacity: 0.75; }
.title-keyword-tip { margin-bottom: 8px; }

.report-card {
  margin-bottom: 4px;
}

.report-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

@media (max-width: 960px) {
  .prediction-hero {
    padding: 22px;
  }

  .form-actions {
    padding-left: 0;
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
