<template>
  <div class="hot-analysis-container">
    <!-- 顶部标题 -->
    <header class="analysis-header">
      <div class="header-title-row">
        <h1 class="main-title">爆品详情分析</h1>
        <button class="refresh-btn" @click="handleRefreshCache" :disabled="loading" title="刷新缓存">刷新</button>
      </div>
      <p class="sub-title">深度分析各品类爆款商品特征，助力精准选品</p>
    </header>

    <!-- 各品类爆款特征对比 -->
    <div class="panel-box category-compare-panel">
      <div class="corner-decoration tl"></div>
      <div class="corner-decoration tr"></div>
      <div class="corner-decoration bl"></div>
      <div class="corner-decoration br"></div>
      <div class="panel-title-row">
        <div class="panel-title">各品类爆款特征对比</div>
        <select v-model="selectedKeyword" class="dark-select">
          <option value="">全部品类</option>
          <option v-for="k in allKeywords" :key="k" :value="k">{{ k }}</option>
        </select>
      </div>
      <div class="data-table">
        <table>
          <thead><tr><th>品类</th><th>爆款样本</th><th>价格区间</th><th>均价</th><th>平均销量</th><th>主流标签</th><th>发货地</th></tr></thead>
          <tbody>
            <tr v-for="row in filteredCategoryFeatures" :key="row.keyword">
              <td>{{ row.keyword }}</td>
              <td>{{ row.sampleSize }}</td>
              <td class="price-cell">¥{{ row.priceMin }} - ¥{{ row.priceMax }}</td>
              <td class="price-cell">¥{{ row.priceAvg }}</td>
              <td class="sales-cell">{{ formatSales(row.avgSales) }}</td>
              <td><span class="tag-badge">{{ row.topShopTag || '无' }}</span></td>
              <td><span class="tag-badge green">{{ row.topRegion || '未知' }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 四列统计卡片 -->
    <div class="panel-row">
      <div class="panel-box fourth-width">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="panel-title">{{ selectedKeyword || '全局' }}爆款价格特征</div>
        <div v-if="loading" class="loading-hint">加载中...</div>
        <div v-else class="stat-list">
          <div class="stat-row"><span class="stat-label">最低价</span><span class="stat-val price">¥{{ hotFeatures.hotPriceMin }}</span></div>
          <div class="stat-row"><span class="stat-label">最高价</span><span class="stat-val price">¥{{ hotFeatures.hotPriceMax }}</span></div>
          <div class="stat-row"><span class="stat-label">平均价</span><span class="stat-val price">¥{{ hotFeatures.hotPriceAvg }}</span></div>
          <div class="stat-row"><span class="stat-label">样本数</span><span class="stat-val">{{ hotFeatures.sampleSize }}个</span></div>
        </div>
      </div>
      <div class="panel-box fourth-width">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="panel-title">{{ selectedKeyword || '全局' }}店铺标签分布</div>
        <div v-if="loading" class="loading-hint">加载中...</div>
        <div v-else class="tag-list">
          <div v-for="tag in hotFeatures.hotShopTags" :key="tag.tag" class="tag-row">
            <span class="tag-badge">{{ tag.tag }}</span><span class="tag-count">{{ tag.count }}个</span>
          </div>
          <div v-if="!hotFeatures.hotShopTags?.length" class="no-data">暂无数据</div>
        </div>
      </div>
      <div class="panel-box fourth-width">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="panel-title">{{ selectedKeyword || '全局' }}地域分布</div>
        <div v-if="loading" class="loading-hint">加载中...</div>
        <div v-else class="tag-list">
          <div v-for="r in hotFeatures.hotRegions" :key="r.region" class="tag-row">
            <span class="tag-badge green">{{ r.region }}</span><span class="tag-count">{{ r.count }}个</span>
          </div>
          <div v-if="!hotFeatures.hotRegions?.length" class="no-data">暂无数据</div>
        </div>
      </div>
      <!-- 规则匹配占比饼图 -->
      <div class="panel-box fourth-width">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="panel-title">爆品规则匹配占比</div>
        <div v-if="loading" class="loading-hint">加载中...</div>
        <div v-show="!loading && hasRuleStats" ref="ruleChartRef" class="rule-chart"></div>
        <div v-if="!loading && !hasRuleStats" class="no-data">暂无规则匹配数据</div>
      </div>
    </div>

    <!-- 热门标题关键词 -->
    <div class="panel-box">
      <div class="corner-decoration tl"></div>
      <div class="corner-decoration tr"></div>
      <div class="corner-decoration bl"></div>
      <div class="corner-decoration br"></div>
      <div class="panel-title">{{ selectedKeyword || '全局' }}热门标题关键词</div>
      <div v-if="loading" class="loading-hint">加载中...</div>
      <div v-else class="keyword-cloud">
        <span v-for="kw in hotTitleKeywords" :key="kw.word" class="keyword-tag" :class="getTagClass(kw.count)">{{ kw.word }} ({{ kw.count }})</span>
        <div v-if="!hotTitleKeywords?.length" class="no-data">暂无数据</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { useCacheStore } from '@/store/cache'
import { useAnalysisCacheStore } from '@/store/analysisCache'
import { ElMessage } from 'element-plus'

const cacheStore = useCacheStore()
const analysisCacheStore = useAnalysisCacheStore()
const hotFeatures = ref({ hotShopTags: [], hotRegions: [], categoryFeatures: [], ruleStats: [] })
const hotTitleKeywords = ref([])
const selectedKeyword = ref('')
const allKeywordsCache = ref([]) // 缓存所有品类列表
const loading = ref(false)
const ruleChartRef = ref(null)
let ruleChart = null

// 使用缓存的品类列表，而不是从当前数据中提取
const allKeywords = computed(() => allKeywordsCache.value)

const filteredCategoryFeatures = computed(() => {
  const features = hotFeatures.value.categoryFeatures || []
  if (!selectedKeyword.value) return features
  return features.filter(f => f.keyword === selectedKeyword.value)
})

// 判断是否有规则匹配数据
const hasRuleStats = computed(() => {
  const stats = hotFeatures.value.ruleStats || []
  return stats.some(r => r.matchCount > 0)
})

const formatSales = (val) => val >= 10000 ? (val / 10000).toFixed(1) + '万' : val?.toLocaleString()
const getTagClass = (count) => count > 20 ? 'hot' : count > 10 ? 'warm' : ''

// 初始化或更新规则匹配饼图
const initRuleChart = () => {
  if (!ruleChartRef.value) {
    console.log('ruleChartRef not ready')
    return
  }
  if (ruleChart) {
    ruleChart.dispose()
  }
  ruleChart = echarts.init(ruleChartRef.value)
  updateRuleChart()
}

// 更新规则匹配饼图
const updateRuleChart = () => {
  if (!ruleChartRef.value) {
    // 如果图表容器不存在，尝试初始化
    return
  }
  if (!ruleChart) {
    ruleChart = echarts.init(ruleChartRef.value)
  }
  const ruleStats = hotFeatures.value.ruleStats || []
  console.log('ruleStats:', ruleStats) // 调试日志
  const data = ruleStats.filter(r => r.matchCount > 0).map(r => ({
    name: r.ruleName,
    value: r.matchCount
  }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: 'rgba(10, 20, 40, 0.9)',
      borderColor: '#2C58A6',
      textStyle: { color: '#fff' }
    },
    legend: {
      orient: 'vertical',
      right: 0,
      top: 'center',
      width: '40%',
      itemGap: 12,
      textStyle: {
        color: '#a0b4c8',
        fontSize: 13,
        width: 92,
        overflow: 'break'
      },
      itemWidth: 12,
      itemHeight: 12
    },
    series: [{
      type: 'pie',
      radius: ['34%', '58%'],
      center: ['40%', '52%'],
      left: '12%',
      right: '38%',
      top: '10%',
      bottom: '10%',
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 4,
        borderColor: '#1a2238',
        borderWidth: 2
      },
      label: {
        show: true,
        position: 'outside',
        formatter: '{d}%',
        color: '#a0b4c8',
        fontSize: 13
      },
      labelLine: {
        show: true,
        length: 8,
        length2: 6,
        lineStyle: { color: '#4788fb' }
      },
      labelLayout: {
        hideOverlap: false,
        moveOverlap: 'shiftY'
      },
      emphasis: {
        label: { show: true, fontSize: 12, fontWeight: 'bold', color: '#fff' }
      },
      data: data,
      color: ['#4788fb', '#7fec9d', '#fedd8b', '#ffa597', '#61d2f7', '#c792ea']
    }]
  }
  ruleChart.setOption(option)
}

// 获取爆品特征和热门关键词数据（使用缓存）
const fetchHotFeatures = async (keyword = '', force = false) => {
  loading.value = true
  try {
    const data = await cacheStore.fetchHotAnalysis(keyword, force)
    if (data) {
      hotFeatures.value = data.hotFeatures || { hotShopTags: [], hotRegions: [], categoryFeatures: [], ruleStats: [] }
      hotTitleKeywords.value = data.hotTitleKeywords || []
    }
  } catch (e) {
    console.error('加载爆品特征失败', e)
  } finally {
    loading.value = false
    await nextTick()
    updateRuleChart()
  }
}

// 获取初始数据
const fetchData = async (force = false) => {
  loading.value = true
  try {
    const data = await cacheStore.fetchHotAnalysis('', force)
    if (data) {
      hotFeatures.value = data.hotFeatures || { hotShopTags: [], hotRegions: [], categoryFeatures: [], ruleStats: [] }
      hotTitleKeywords.value = data.hotTitleKeywords || []
      // 缓存所有品类列表
      allKeywordsCache.value = (hotFeatures.value.categoryFeatures || []).map(f => f.keyword)
    }
  } catch (e) { console.error('加载数据失败', e) }
  finally {
    loading.value = false
    await nextTick()
    initRuleChart()
  }
}

// 监听品类变化，重新请求数据
watch(selectedKeyword, async (newKeyword) => {
  await fetchHotFeatures(newKeyword)
})

// 刷新缓存
const handleRefreshCache = async () => {
  cacheStore.clearCache()
  analysisCacheStore.clearCache()
  selectedKeyword.value = ''
  await fetchData(true)
  ElMessage.success('缓存已刷新')
}

onMounted(fetchData)
</script>

<style scoped>
.hot-analysis-container { min-height: 100vh; background: linear-gradient(135deg, #0f1629 0%, #1a2238 100%); padding: 10px 14px; color: #fff; display: flex; flex-direction: column; }
.analysis-header { text-align: center; padding: 6px 0 12px; flex-shrink: 0; }
.header-title-row { display: flex; align-items: center; justify-content: center; gap: 16px; }
.main-title { font-size: 34px; font-weight: bold; color: #61d2f7; text-shadow: 0 0 10px rgba(97, 210, 247, 0.5); margin: 0; }
.sub-title { font-size: 20px; color: #a0b4c8; margin: 6px 0 0; }
.refresh-btn { background: rgba(71, 136, 251, 0.3); border: 1px solid #4788fb; color: #61d2f7; padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 18px; transition: all 0.3s; }
.refresh-btn:hover:not(:disabled) { background: rgba(71, 136, 251, 0.5); }
.refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.panel-row { display: flex; gap: 10px; margin-top: 10px; }
.panel-box { background: rgba(10, 20, 40, 0.6); border: 1px solid #2C58A6; border-radius: 8px; padding: 12px 14px; position: relative; margin-bottom: 10px; flex: 1; min-height: 0; }
.fourth-width { flex: 1; min-width: 0; }
.rule-chart { height: 260px; width: 100%; min-height: 260px; }

.corner-decoration { position: absolute; width: 12px; height: 12px; }
.corner-decoration.tl { top: 0; left: 0; border-top: 2px solid #4788fb; border-left: 2px solid #4788fb; }
.corner-decoration.tr { top: 0; right: 0; border-top: 2px solid #4788fb; border-right: 2px solid #4788fb; }
.corner-decoration.bl { bottom: 0; left: 0; border-bottom: 2px solid #4788fb; border-left: 2px solid #4788fb; }
.corner-decoration.br { bottom: 0; right: 0; border-bottom: 2px solid #4788fb; border-right: 2px solid #4788fb; }

.panel-title { font-size: 22px; font-weight: bold; color: #61d2f7; margin-bottom: 10px; }
.panel-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.category-compare-panel .panel-title-row { margin-bottom: 20px; min-height: 48px; }
.category-compare-panel .data-table { margin-top: 24px; }
.dark-select { background: rgba(44, 88, 166, 0.3); border: 1px solid #2C58A6; color: #61d2f7; padding: 8px 14px; border-radius: 4px; outline: none; cursor: pointer; font-size: 18px; }
.dark-select option { background: #1a2238; color: #fff; }

.data-table { overflow-x: auto; max-height: 400px; overflow-y: auto; }
.data-table table { width: 100%; border-collapse: collapse; font-size: 19px; }
.data-table th { background: rgba(44, 88, 166, 0.4); color: #61d2f7; padding: 12px 10px; text-align: left; position: sticky; top: 0; z-index: 1; font-size: 19px; font-weight: 600; }
.data-table td { padding: 10px; border-bottom: 1px solid rgba(44, 88, 166, 0.3); color: #c0d4e8; font-size: 19px; }
.data-table tr:nth-child(even) { background: rgba(44, 88, 166, 0.1); }
.data-table tr:hover { background: rgba(71, 136, 251, 0.2); }

.price-cell { color: #fedd8b; }
.sales-cell { color: #84e4dd; }
.tag-badge { display: inline-block; padding: 5px 14px; background: rgba(71, 136, 251, 0.3); border: 1px solid #4788fb; border-radius: 4px; font-size: 18px; color: #61d2f7; }
.tag-badge.green { background: rgba(127, 236, 157, 0.2); border-color: #7fec9d; color: #7fec9d; }

.stat-list { display: flex; flex-direction: column; gap: 10px; }
.stat-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid rgba(44, 88, 166, 0.3); }
.stat-label { color: #a0b4c8; font-size: 18px; }
.stat-val { color: #61d2f7; font-weight: bold; font-size: 20px; }
.stat-val.price { color: #fedd8b; }

.tag-list { display: flex; flex-direction: column; gap: 8px; max-height: 240px; overflow-y: auto; }
.tag-row { display: flex; justify-content: space-between; align-items: center; }
.tag-count { color: #a0b4c8; font-size: 17px; }

.keyword-cloud { display: flex; flex-wrap: wrap; gap: 10px; padding: 10px 0; }
.keyword-tag { display: inline-block; padding: 8px 18px; background: rgba(71, 136, 251, 0.2); border: 1px solid #4788fb; border-radius: 16px; font-size: 18px; color: #61d2f7; transition: all 0.3s; }
.keyword-tag:hover { background: rgba(71, 136, 251, 0.4); transform: scale(1.05); }
.keyword-tag.warm { background: rgba(254, 221, 139, 0.2); border-color: #fedd8b; color: #fedd8b; }
.keyword-tag.hot { background: rgba(255, 165, 151, 0.2); border-color: #ffa597; color: #ffa597; }

.loading-hint { color: #61d2f7; font-size: 18px; padding: 20px 0; text-align: center; }
.no-data { color: #6a7a8a; font-size: 17px; text-align: center; padding: 10px 0; }
</style>

