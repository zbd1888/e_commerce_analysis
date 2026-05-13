<template>
  <div class="region-container">
    <!-- 顶部标题 -->
    <header class="page-header">
      <div class="header-title-row">
        <h1 class="main-title">地域可视化</h1>
        <button class="refresh-btn" @click="handleRefreshCache" :disabled="loading" title="刷新缓存">刷新</button>
      </div>
      <p class="sub-title">基于商品发货地分析各地区销售分布</p>
    </header>

    <!-- 筛选条件 -->
    <div class="panel-box filter-panel">
      <div class="corner-decoration tl"></div>
      <div class="corner-decoration tr"></div>
      <div class="corner-decoration bl"></div>
      <div class="corner-decoration br"></div>
      <div class="filter-row">
        <div class="filter-item">
          <span class="filter-label">品类</span>
          <select v-model="filters.keyword" class="dark-select" @change="handleKeywordChange">
            <option value="">全部品类</option>
            <option v-for="k in keywords" :key="k" :value="k">{{ k }}</option>
          </select>
        </div>
        <div class="filter-item">
          <span class="filter-label">数据维度</span>
          <div class="radio-group">
            <label class="radio-btn" :class="{ active: filters.dimension === 'count' }">
              <input type="radio" v-model="filters.dimension" value="count" @change="initChart" /> 商品数量
            </label>
            <label class="radio-btn" :class="{ active: filters.dimension === 'sales' }">
              <input type="radio" v-model="filters.dimension" value="sales" @change="initChart" /> 总销量
            </label>
          </div>
        </div>
        <button class="query-btn" @click="fetchRegionData" :disabled="loading">
          <span v-if="loading">加载中...</span>
          <span v-else>查询</span>
        </button>
      </div>
    </div>

    <!-- 地图区域 -->
    <div class="panel-box map-panel">
      <div class="corner-decoration tl"></div>
      <div class="corner-decoration tr"></div>
      <div class="corner-decoration bl"></div>
      <div class="corner-decoration br"></div>
      <div class="panel-title">{{ filters.keyword || '全部品类' }} - 地域分布热力图</div>

      <!-- 骨架屏 -->
      <div v-if="loading" class="skeleton-container">
        <div class="skeleton-shimmer"></div>
        <span class="skeleton-text">数据加载中...</span>
      </div>
      <!-- 空数据提示 -->
      <div v-else-if="regionData.length === 0" class="empty-data">
        <div class="empty-icon">暂无数据</div>
        <p>暂无该品类的地域数据</p>
        <p class="empty-hint">请尝试选择其他品类或检查数据是否已采集</p>
      </div>
      <!-- 地图 -->
      <div v-else ref="mapChart" class="map-chart"></div>
    </div>

    <!-- 地域排行榜 -->
    <div class="panel-row">
      <div class="panel-box half-width">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="panel-title">商品数量 TOP10</div>
        <div class="data-table">
          <table>
            <thead><tr><th>#</th><th>地区</th><th>商品数</th></tr></thead>
            <tbody>
              <tr v-for="(row, idx) in topRegionsByCount" :key="row.region">
                <td class="rank-cell">{{ idx + 1 }}</td>
                <td>{{ row.region }}</td>
                <td><span class="tag-badge">{{ row.count }}</span></td>
              </tr>
              <tr v-if="topRegionsByCount.length === 0">
                <td colspan="3" class="no-data">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="panel-box half-width">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="panel-title">总销量 TOP10</div>
        <div class="data-table">
          <table>
            <thead><tr><th>#</th><th>地区</th><th>总销量</th></tr></thead>
            <tbody>
              <tr v-for="(row, idx) in topRegionsBySales" :key="row.region">
                <td class="rank-cell">{{ idx + 1 }}</td>
                <td>{{ row.region }}</td>
                <td><span class="tag-badge hot">{{ formatSales(row.totalSales) }}</span></td>
              </tr>
              <tr v-if="topRegionsBySales.length === 0">
                <td colspan="3" class="no-data">暂无数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import '@/assets/china.js'  // 本地地图，自动注册
import { ElMessage } from 'element-plus'
import { useCacheStore } from '@/store/cache'
import { useAnalysisCacheStore } from '@/store/analysisCache'

const cacheStore = useCacheStore()
const analysisCacheStore = useAnalysisCacheStore()
const keywords = computed(() => cacheStore.keywords)
const filters = ref({ keyword: '', dimension: 'count' })
const mapChart = ref(null)
const regionData = ref([])
const topRegionsByCount = ref([])
const topRegionsBySales = ref([])
const loading = ref(true)
let chartInstance = null

const formatSales = (val) => val >= 10000 ? (val / 10000).toFixed(1) + '万' : val?.toLocaleString()

// 省份名称映射（处理数据中可能的简称）
const provinceMap = {
  '北京': '北京', '天津': '天津', '上海': '上海', '重庆': '重庆',
  '河北': '河北', '山西': '山西', '辽宁': '辽宁', '吉林': '吉林', '黑龙江': '黑龙江',
  '江苏': '江苏', '浙江': '浙江', '安徽': '安徽', '福建': '福建', '江西': '江西',
  '山东': '山东', '河南': '河南', '湖北': '湖北', '湖南': '湖南',
  '广东': '广东', '海南': '海南', '四川': '四川', '贵州': '贵州', '云南': '云南',
  '陕西': '陕西', '甘肃': '甘肃', '青海': '青海', '台湾': '台湾',
  '内蒙古': '内蒙古', '广西': '广西', '西藏': '西藏', '宁夏': '宁夏', '新疆': '新疆',
  '香港': '香港', '澳门': '澳门'
}

// 处理品类切换 - 确保清空时 keyword 为空字符串
const handleKeywordChange = () => {
  // 确保 keyword 始终是字符串，不是 null/undefined
  if (filters.value.keyword === null || filters.value.keyword === undefined) {
    filters.value.keyword = ''
  }
  fetchRegionData()
}

const initChart = () => {
  // 如果没有数据，不初始化地图
  if (regionData.value.length === 0) {
    console.log('[Region] 无数据，跳过地图初始化')
    return
  }

  // 如果 DOM 元素不存在，等待下一个渲染周期
  if (!mapChart.value) {
    console.log('[Region] DOM 未就绪，延迟初始化')
    setTimeout(() => initChart(), 100)
    return
  }

  // 销毁旧实例（如果存在）
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }

  chartInstance = echarts.init(mapChart.value, 'dark')
  console.log('[Region] 地图初始化成功，数据条数:', regionData.value.length)

  // 后端返回字段: name(省份), value(商品数), sales(总销量)
  const mapData = regionData.value.map(item => ({
    name: provinceMap[item.name] || item.name,
    value: filters.value.dimension === 'count' ? item.value : item.sales
  }))

  // 计算最大值，确保有合理的颜色区分
  const maxValue = Math.max(...mapData.map(d => d.value || 0), 1)

  chartInstance.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(10, 20, 40, 0.9)',
      borderColor: '#4788fb',
      textStyle: { color: '#61d2f7' },
      formatter: (params) => {
        if (params.value) {
          return `${params.name}<br/>${filters.value.dimension === 'count' ? '商品数' : '总销量'}: ${filters.value.dimension === 'sales' ? formatSales(params.value) : params.value}`
        }
        return `${params.name}<br/>暂无数据`
      }
    },
    visualMap: {
      min: 0,
      max: maxValue,
      text: ['高', '低'],
      realtime: false,
      calculable: true,
      inRange: {
        color: ['#1a3a5c', '#2a5a8c', '#3a7abc', '#4a9aec', '#5abaff', '#6adaff']
      },
      left: 'left',
      bottom: '20px',
      textStyle: { color: '#a0b4c8', fontSize: 16 }
    },
    series: [{
      name: filters.value.dimension === 'count' ? '商品数量' : '总销量',
      type: 'map',
      map: 'china',
      roam: true,
      zoom: 1.2,
      center: [105, 36],
      layoutCenter: ['50%', '50%'],
      layoutSize: '95%',
      label: {
        show: true,
        fontSize: 14,
        color: '#a0b4c8',
        formatter: (params) => {
          if (params.value && params.value > 0) {
            const val = filters.value.dimension === 'sales'
              ? (params.value >= 10000 ? (params.value/10000).toFixed(1) + '万' : params.value)
              : params.value
            return `${params.name}\n${val}`
          }
          return params.name
        }
      },
      itemStyle: {
        areaColor: '#1a2a4a',
        borderColor: '#4788fb',
        borderWidth: 0.5
      },
      emphasis: {
        label: { show: true, color: '#fff', fontWeight: 'bold' },
        itemStyle: { areaColor: '#ff7043', borderColor: '#fff', borderWidth: 1.5 }
      },
      data: mapData
    }]
  })
}

const fetchRegionData = async () => {
  loading.value = true
  try {
    // 使用带缓存的 store 方法获取数据
    const data = await analysisCacheStore.getRegionDistribution(filters.value.keyword)
    regionData.value = data || []

    // 后端返回字段: name(省份), value(商品数), sales(总销量)
    // 转换为前端表格需要的格式
    topRegionsByCount.value = [...regionData.value]
      .sort((a, b) => (b.value || 0) - (a.value || 0))
      .slice(0, 10)
      .map(item => ({ region: item.name, count: item.value, totalSales: item.sales }))

    topRegionsBySales.value = [...regionData.value]
      .sort((a, b) => (b.sales || 0) - (a.sales || 0))
      .slice(0, 10)
      .map(item => ({ region: item.name, count: item.value, totalSales: item.sales }))

    loading.value = false
    // 等待 DOM 更新（条件渲染可能需要多个周期）
    await nextTick()
    // 再次等待确保地图 DOM 已渲染
    await nextTick()
    initChart()
  } catch (e) {
    console.error('获取地域数据失败', e)
    ElMessage.error('获取地域数据失败')
    loading.value = false
  }
}

// 刷新缓存
const handleRefreshCache = async () => {
  cacheStore.clearCache()
  analysisCacheStore.clearCache()
  filters.value.keyword = ''
  await cacheStore.fetchKeywords()
  await fetchRegionData()
  ElMessage.success('缓存已刷新')
}

onMounted(async () => {
  await cacheStore.fetchKeywords()
  await fetchRegionData()

  // 预加载热门品类数据
  nextTick(() => {
    analysisCacheStore.prefetchTopKeywords(cacheStore.keywords)
  })
})

onUnmounted(() => {
  if (chartInstance) chartInstance.dispose()
})
</script>

<style scoped>
.region-container { min-height: 100vh; background: linear-gradient(135deg, #0f1629 0%, #1a2238 100%); padding: 10px 14px; color: #fff; }

/* 页面标题 */
.page-header { text-align: center; padding: 6px 0 12px; }
.header-title-row { display: flex; align-items: center; justify-content: center; gap: 16px; }
.main-title { font-size: 32px; font-weight: bold; color: #61d2f7; text-shadow: 0 0 10px rgba(97, 210, 247, 0.5); margin: 0; }
.sub-title { font-size: 19px; color: #a0b4c8; margin: 6px 0 0; }
.refresh-btn { background: rgba(71, 136, 251, 0.3); border: 1px solid #4788fb; color: #61d2f7; padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 18px; transition: all 0.3s; }
.refresh-btn:hover:not(:disabled) { background: rgba(71, 136, 251, 0.5); }
.refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* 面板样式 */
.panel-box { background: rgba(10, 20, 40, 0.6); border: 1px solid #2C58A6; border-radius: 8px; padding: 12px 14px; position: relative; margin-bottom: 10px; }
.panel-title { font-size: 22px; font-weight: bold; color: #61d2f7; margin-bottom: 10px; }
.panel-row { display: flex; gap: 10px; }
.half-width { flex: 1; }

/* 角落装饰 */
.corner-decoration { position: absolute; width: 12px; height: 12px; }
.corner-decoration.tl { top: 0; left: 0; border-top: 2px solid #4788fb; border-left: 2px solid #4788fb; }
.corner-decoration.tr { top: 0; right: 0; border-top: 2px solid #4788fb; border-right: 2px solid #4788fb; }
.corner-decoration.bl { bottom: 0; left: 0; border-bottom: 2px solid #4788fb; border-left: 2px solid #4788fb; }
.corner-decoration.br { bottom: 0; right: 0; border-bottom: 2px solid #4788fb; border-right: 2px solid #4788fb; }

/* 筛选区域 */
.filter-panel { margin-bottom: 10px; }
.filter-row { display: flex; align-items: center; gap: 24px; flex-wrap: wrap; }
.filter-item { display: flex; align-items: center; gap: 8px; }
.filter-label { color: #a0b4c8; font-size: 19px; }
.dark-select { background: rgba(44, 88, 166, 0.3); border: 1px solid #2C58A6; color: #61d2f7; padding: 8px 12px; border-radius: 4px; outline: none; cursor: pointer; min-width: 140px; font-size: 18px; }
.dark-select option { background: #1a2238; color: #fff; }
.radio-group { display: flex; gap: 8px; }
.radio-btn { display: flex; align-items: center; gap: 4px; padding: 8px 16px; background: rgba(44, 88, 166, 0.2); border: 1px solid #2C58A6; border-radius: 4px; color: #a0b4c8; cursor: pointer; font-size: 18px; transition: all 0.3s; }
.radio-btn input { display: none; }
.radio-btn.active { background: rgba(71, 136, 251, 0.4); border-color: #4788fb; color: #61d2f7; }
.query-btn { background: linear-gradient(135deg, #4788fb 0%, #2C58A6 100%); border: none; color: #fff; padding: 8px 22px; border-radius: 4px; cursor: pointer; font-size: 19px; transition: all 0.3s; }
.query-btn:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(71, 136, 251, 0.4); }
.query-btn:disabled { opacity: 0.6; cursor: not-allowed; transform: none; }

/* 地图区域 - 地域分布热力图容器和图表放大2倍 */
.map-panel { min-height: 1100px; }
.map-chart { height: 1000px; }

/* 骨架屏 */
.skeleton-container { height: 1000px; display: flex; flex-direction: column; align-items: center; justify-content: center; position: relative; overflow: hidden; border-radius: 8px; background: rgba(44, 88, 166, 0.1); }
.skeleton-shimmer { position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: linear-gradient(90deg, transparent 25%, rgba(71, 136, 251, 0.1) 50%, transparent 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
.skeleton-text { color: #61d2f7; font-size: 18px; z-index: 1; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }

/* 空数据提示 */
.empty-data { height: 1000px; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #a0b4c8; }
.empty-icon { font-size: 56px; margin-bottom: 16px; }
.empty-data p { margin: 4px 0; font-size: 18px; }
.empty-hint { font-size: 16px; color: #6a7a8a; }

/* 数据表格 */
.data-table { max-height: 320px; overflow-y: auto; }
.data-table table { width: 100%; border-collapse: collapse; font-size: 19px; }
.data-table th { background: rgba(44, 88, 166, 0.4); color: #61d2f7; padding: 12px 10px; text-align: left; position: sticky; top: 0; z-index: 1; font-size: 19px; font-weight: 600; }
.data-table td { padding: 10px; border-bottom: 1px solid rgba(44, 88, 166, 0.3); color: #c0d4e8; font-size: 19px; }
.data-table tr:nth-child(even) { background: rgba(44, 88, 166, 0.1); }
.data-table tr:hover { background: rgba(71, 136, 251, 0.2); }
.rank-cell { color: #61d2f7; font-weight: bold; font-size: 18px; }
.no-data { text-align: center; color: #6a7a8a; font-size: 17px; }
.tag-badge { display: inline-block; padding: 5px 14px; background: rgba(71, 136, 251, 0.3); border: 1px solid #4788fb; border-radius: 4px; font-size: 18px; color: #61d2f7; }
.tag-badge.hot { background: rgba(255, 165, 151, 0.2); border-color: #ffa597; color: #ffa597; }
</style>

