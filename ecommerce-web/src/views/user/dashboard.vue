<template>
  <div class="dashboard-container">
    <!-- 顶部标题栏 -->
    <header class="dashboard-header">
      <div class="header-left">
        <span class="date-text">{{ currentDate }}</span>
      </div>
      <div class="header-center">
        <h1 class="main-title">电商爆品分析可视化大屏</h1>
      </div>
      <div class="header-right">
        <button class="refresh-btn" @click="handleRefreshCache" :disabled="loading" title="刷新缓存">
          刷新
        </button>
        <span class="time-text">{{ currentTime }}</span>
      </div>
    </header>

    <!-- 骨架屏 - 加载状态 -->
    <template v-if="loading">
      <div class="stats-row">
        <div class="stat-box skeleton-box" v-for="i in 5" :key="i">
          <div class="corner-decoration tl"></div>
          <div class="corner-decoration tr"></div>
          <div class="corner-decoration bl"></div>
          <div class="corner-decoration br"></div>
          <el-skeleton :rows="1" animated />
        </div>
      </div>
    </template>

    <!-- 核心指标卡片 -->
    <div v-else class="stats-row">
      <div class="stat-box">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="stat-value blue">{{ animateValue(stats.totalProducts) }}</div>
        <div class="stat-label">商品总数</div>
      </div>
      <div class="stat-box">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="stat-value green">{{ stats.totalCategories }}</div>
        <div class="stat-label">品类数量</div>
      </div>
      <div class="stat-box">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="stat-value orange">{{ formatSales(stats.totalSales) }}</div>
        <div class="stat-label">总销量</div>
      </div>
      <div class="stat-box">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="stat-value red">¥{{ stats.avgPrice?.toFixed(2) }}</div>
        <div class="stat-label">平均价格</div>
      </div>
      <div class="stat-box">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="stat-value purple">{{ rankCount }}</div>
        <div class="stat-label">天猫榜单数</div>
      </div>
    </div>

    <!-- 主体内容区 -->
    <div class="main-content">
      <!-- 左侧面板 -->
      <div class="panel-column left-panel">
        <!-- 品类销量排行 -->
        <div class="panel-box">
          <div class="corner-decoration tl"></div>
          <div class="corner-decoration tr"></div>
          <div class="corner-decoration bl"></div>
          <div class="corner-decoration br"></div>
          <div class="panel-title">
            <span>品类销量排行</span>
          </div>
          <div ref="categoryChart" class="chart-container"></div>
        </div>
        <!-- 地域分布 -->
        <div class="panel-box">
          <div class="corner-decoration tl"></div>
          <div class="corner-decoration tr"></div>
          <div class="corner-decoration bl"></div>
          <div class="corner-decoration br"></div>
          <div class="panel-title">
            <span>商品地域分布</span>
          </div>
          <div ref="regionChart" class="chart-container"></div>
        </div>
      </div>

      <!-- 中间面板 -->
      <div class="panel-column center-panel">
        <!-- 热度榜单TOP10 -->
        <div class="panel-box">
          <div class="corner-decoration tl"></div>
          <div class="corner-decoration tr"></div>
          <div class="corner-decoration bl"></div>
          <div class="corner-decoration br"></div>
          <div class="panel-title">
            <span>天猫热度榜单 TOP10</span>
          </div>
          <div ref="rankChart" class="chart-container"></div>
        </div>
        <!-- 热销商品TOP10 -->
        <div class="panel-box table-box">
          <div class="corner-decoration tl"></div>
          <div class="corner-decoration tr"></div>
          <div class="corner-decoration bl"></div>
          <div class="corner-decoration br"></div>
          <div class="panel-title">
            <span>热销商品 TOP10</span>
          </div>
          <div class="data-table">
            <table>
              <thead>
                <tr>
                  <th>排名</th>
                  <th>商品标题</th>
                  <th>价格</th>
                  <th>销量</th>
                  <th>店铺</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(item, index) in topProducts" :key="item.id">
                  <td><span class="rank-badge" :class="'rank-' + (index + 1)">{{ index + 1 }}</span></td>
                  <td class="title-cell">{{ item.title }}</td>
                  <td class="price-cell">¥{{ item.price }}</td>
                  <td>{{ item.saleValue }}</td>
                  <td class="store-cell">{{ item.store }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- 右侧面板 -->
      <div class="panel-column right-panel">
        <!-- 价格区间分布 -->
        <div class="panel-box">
          <div class="corner-decoration tl"></div>
          <div class="corner-decoration tr"></div>
          <div class="corner-decoration bl"></div>
          <div class="corner-decoration br"></div>
          <div class="panel-title">
            <span>价格区间分布</span>
          </div>
          <div ref="priceChart" class="chart-container"></div>
        </div>
        <!-- 店铺标签分布 -->
        <div class="panel-box">
          <div class="corner-decoration tl"></div>
          <div class="corner-decoration tr"></div>
          <div class="corner-decoration bl"></div>
          <div class="corner-decoration br"></div>
          <div class="panel-title">
            <span>店铺标签分布</span>
          </div>
          <div ref="shopTagChart" class="chart-container"></div>
        </div>
      </div>
    </div>

    <!-- 底部店铺排行 -->
    <div class="bottom-section">
      <div class="panel-box wide-box">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="panel-title">
          <span>店铺销量 TOP10</span>
        </div>
        <div class="data-table store-table">
          <table>
            <thead>
              <tr>
                <th>排名</th>
                <th>店铺名称</th>
                <th>总销量</th>
                <th>商品数</th>
                <th>均价</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in storeRanking.slice(0, 10)" :key="item.store">
                <td><span class="rank-badge" :class="'rank-' + (index + 1)">{{ index + 1 }}</span></td>
                <td class="store-name-cell">{{ item.store }}</td>
                <td class="sales-cell">{{ formatSales(item.totalSales) }}</td>
                <td>{{ item.productCount }}</td>
                <td class="price-cell">¥{{ item.avgPrice }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import '@/assets/china.js'  // 本地地图，自动注册
import { useCacheStore } from '@/store/cache'
import { useAnalysisCacheStore } from '@/store/analysisCache'
import { ElMessage } from 'element-plus'

const cacheStore = useCacheStore()
const analysisCacheStore = useAnalysisCacheStore()

const loading = ref(true)
const stats = ref({ totalProducts: 0, totalCategories: 0, totalSales: 0, avgPrice: 0 })
const topProducts = ref([])
const rankHotlist = ref([])
const rankCount = ref(0)
const storeRanking = ref([])
const categoryChart = ref(null)
const priceChart = ref(null)
const rankChart = ref(null)
const regionChart = ref(null)
const shopTagChart = ref(null)
let chartInstances = []

const currentDate = ref('')
const currentTime = ref('')
let timeInterval = null

const updateTime = () => {
  const now = new Date()
  currentDate.value = now.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const formatSales = (val) => val >= 10000 ? (val / 10000).toFixed(1) + '万' : val?.toLocaleString()
const animateValue = (val) => val?.toLocaleString() || '0'

const fixImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('//')) return 'https:' + url
  return url
}

// 深色主题配置
const darkChartTheme = {
  backgroundColor: 'transparent',
  textStyle: { color: '#61d2f7' },
  axisLine: { lineStyle: { color: '#2C58A6' } },
  splitLine: { lineStyle: { color: '#1a2a4a' } }
}

const initCharts = (categoryData, priceData, rankData, regionData, shopTagData) => {
  // 品类销量图 - 横向柱状图 + 渐变色
  const chart1 = echarts.init(categoryChart.value)
  const sortedCategory = [...categoryData].sort((a, b) => a.totalSales - b.totalSales).slice(-8)
  chart1.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: 'rgba(0,0,0,0.7)', borderColor: '#2C58A6', textStyle: { color: '#fff' } },
    grid: { left: '3%', right: '12%', bottom: '5%', top: '5%', containLabel: true },
    xAxis: { type: 'value', axisLabel: { color: '#61d2f7', formatter: v => v >= 10000 ? (v/10000).toFixed(0) + '万' : v }, splitLine: { lineStyle: { color: '#1a2a4a' } }, axisLine: { lineStyle: { color: '#2C58A6' } } },
    yAxis: { type: 'category', data: sortedCategory.map(d => d.keyword), axisLabel: { fontSize: 14, color: '#61d2f7' }, axisLine: { lineStyle: { color: '#2C58A6' } } },
    series: [{ type: 'bar', data: sortedCategory.map((d, i) => ({ value: d.totalSales, itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{offset: 0, color: '#4788fb'}, {offset: 1, color: '#6bc0fb'}]), borderRadius: [0, 4, 4, 0] } })), barWidth: '55%', label: { show: true, position: 'right', color: '#6bc0fb', fontSize: 14, formatter: p => p.value >= 10000 ? (p.value/10000).toFixed(1) + '万' : p.value } }]
  })
  chartInstances.push(chart1)

  // 热度榜单图 - 柱状图 + 显示数值
  if (rankChart.value && rankData.length > 0) {
    const chart2 = echarts.init(rankChart.value)
    const topRank = rankData.slice(0, 8)
    chart2.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', formatter: p => `${p[0].name}<br/>热度: ${p[0].value.toLocaleString()}`, backgroundColor: 'rgba(0,0,0,0.7)', borderColor: '#2C58A6', textStyle: { color: '#fff' } },
      grid: { left: '3%', right: '4%', bottom: '18%', top: '15%', containLabel: true },
      xAxis: { type: 'category', data: topRank.map(d => d.rankName.length > 5 ? d.rankName.slice(0,5)+'..' : d.rankName), axisLabel: { rotate: 30, fontSize: 13, color: '#61d2f7' }, axisLine: { lineStyle: { color: '#2C58A6' } } },
      yAxis: { type: 'value', axisLabel: { color: '#61d2f7', formatter: v => v >= 10000 ? (v/10000).toFixed(0)+'万' : v }, splitLine: { lineStyle: { color: '#1a2a4a' } }, axisLine: { lineStyle: { color: '#2C58A6' } } },
      series: [{ type: 'bar', data: topRank.map((d, i) => ({ value: d.hotValue, itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{offset: 0, color: '#ff6b6b'}, {offset: 1, color: '#ffa502'}]) } })), barWidth: '50%',
        label: { show: true, position: 'top', color: '#ffa502', fontSize: 13, formatter: p => p.value >= 10000 ? (p.value/10000).toFixed(1)+'万' : p.value }
      }]
    })
    chartInstances.push(chart2)
  }

  // 地域分布图 - 柱状图 + 显示数值
  if (regionChart.value && regionData.length > 0) {
    const chart3 = echarts.init(regionChart.value)
    const topRegion = regionData.slice(0, 8)
    chart3.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(0,0,0,0.7)', borderColor: '#2C58A6', textStyle: { color: '#fff' } },
      grid: { left: '3%', right: '4%', bottom: '18%', top: '15%', containLabel: true },
      xAxis: { type: 'category', data: topRegion.map(d => d.name), axisLabel: { rotate: 30, fontSize: 13, color: '#61d2f7' }, axisLine: { lineStyle: { color: '#2C58A6' } } },
      yAxis: { type: 'value', name: '商品数', nameTextStyle: { color: '#61d2f7' }, axisLabel: { color: '#61d2f7' }, splitLine: { lineStyle: { color: '#1a2a4a' } }, axisLine: { lineStyle: { color: '#2C58A6' } } },
      series: [{ type: 'bar', data: topRegion.map(d => d.value), itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{offset: 0, color: '#7fec9d'}, {offset: 1, color: '#25f3e6'}]) }, barWidth: '50%',
        label: { show: true, position: 'top', color: '#7fec9d', fontSize: 13 }
      }]
    })
    chartInstances.push(chart3)
  }

  // 价格分布图 - 饼图 + 显示占比
  const chart4 = echarts.init(priceChart.value)
  const priceColors = ['#ff6b6b', '#ffa502', '#ffeaa7', '#7fec9d', '#6bc0fb', '#a29bfe']
  chart4.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: p => `${p.name}: ${p.value} (${p.percent}%)`, backgroundColor: 'rgba(0,0,0,0.7)', borderColor: '#2C58A6', textStyle: { color: '#fff' } },
    legend: { orient: 'vertical', right: '0%', top: 'center', textStyle: { color: '#61d2f7', fontSize: 12 }, itemWidth: 12, itemHeight: 10 },
    series: [{ type: 'pie', radius: ['30%', '50%'], center: ['40%', '50%'],
      label: { show: true, position: 'outside', formatter: '{d}%', color: '#a0b4c8', fontSize: 12, distanceToLabelLine: 2 },
      labelLine: { show: true, length: 8, length2: 5, lineStyle: { color: '#4788fb' } },
      data: priceData.map((d, i) => ({ name: d.range, value: d.count, itemStyle: { color: priceColors[i % priceColors.length] } })) }]
  })
  chartInstances.push(chart4)

  // 店铺标签分布 - 饼图 + 显示占比
  if (shopTagChart.value && shopTagData.length > 0) {
    const chart5 = echarts.init(shopTagChart.value)
    const tagColors = ['#4788fb', '#25f3e6', '#7fec9d', '#fedd8b', '#ffa597', '#a29bfe']
    chart5.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'item', formatter: p => `${p.name}: ${p.value} (${p.percent}%)`, backgroundColor: 'rgba(0,0,0,0.7)', borderColor: '#2C58A6', textStyle: { color: '#fff' } },
      legend: { orient: 'vertical', right: '0%', top: 'center', textStyle: { color: '#61d2f7', fontSize: 12 }, itemWidth: 12, itemHeight: 10 },
      series: [{ type: 'pie', radius: ['30%', '50%'], center: ['40%', '50%'],
        label: { show: true, position: 'outside', formatter: '{d}%', color: '#a0b4c8', fontSize: 12, distanceToLabelLine: 2 },
        labelLine: { show: true, length: 8, length2: 5, lineStyle: { color: '#4788fb' } },
        data: shopTagData.slice(0, 6).map((d, i) => ({ name: d.tag, value: d.count, itemStyle: { color: tagColors[i] } })) }]
    })
    chartInstances.push(chart5)
  }
}

const handleResize = () => chartInstances.forEach(c => c.resize())

const fetchData = async () => {
  loading.value = true
  try {
    // 使用缓存Store获取数据（带2分钟缓存）
    const data = await cacheStore.fetchDashboard() || {}

    stats.value = data.stats || {}
    topProducts.value = data.topProducts || []
    rankHotlist.value = data.rankHotlist || []
    rankCount.value = data.rankCount || 0
    storeRanking.value = data.storeRanking || []

    loading.value = false
    initCharts(
      data.categorySales || [],
      data.priceDistribution || [],
      data.rankHotlist || [],
      data.regionDistribution || [],
      data.shopTagDistribution || []
    )
  } catch (e) {
    console.error('加载数据失败', e)
    loading.value = false
  }
}

// 刷新缓存
const handleRefreshCache = async () => {
  cacheStore.clearCache()
  analysisCacheStore.clearCache()
  await fetchData()
  ElMessage.success('缓存已刷新')
}

onMounted(() => {
  updateTime()
  timeInterval = setInterval(updateTime, 1000)
  fetchData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  clearInterval(timeInterval)
  chartInstances.forEach(c => c.dispose())
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
/* 大屏容器 */
.dashboard-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #0f1629 0%, #1a2238 100%);
  color: #fff;
  padding: 6px 8px;
  box-sizing: border-box;
}

/* 顶部标题栏 */
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: linear-gradient(90deg, transparent, rgba(44, 88, 166, 0.4), transparent);
  border-bottom: 2px solid #2C58A6;
  margin-bottom: 8px;
  position: relative;
}
.dashboard-header::before, .dashboard-header::after {
  content: '';
  position: absolute;
  width: 100px;
  height: 3px;
  background: linear-gradient(90deg, transparent, #4788fb, transparent);
  bottom: -2px;
}
.dashboard-header::before { left: 0; }
.dashboard-header::after { right: 0; }
.main-title {
  margin: 0;
  font-size: 36px;
  font-weight: 700;
  background: linear-gradient(90deg, #4788fb, #25f3e6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  text-shadow: 0 0 30px rgba(71, 136, 251, 0.5);
  letter-spacing: 4px;
}
.date-text, .time-text {
  font-size: 20px;
  color: #61d2f7;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.refresh-btn {
  background: rgba(71, 136, 251, 0.3);
  border: 1px solid #4788fb;
  color: #61d2f7;
  padding: 6px 14px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 18px;
  transition: all 0.3s;
}
.refresh-btn:hover:not(:disabled) {
  background: rgba(71, 136, 251, 0.5);
  transform: scale(1.05);
}
.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 核心指标行 */
.stats-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.stat-box {
  flex: 1;
  background: rgba(26, 34, 56, 0.8);
  border: 1px solid #2C58A6;
  border-radius: 8px;
  padding: 12px 8px;
  text-align: center;
  position: relative;
  box-shadow: 0 0 15px rgba(44, 88, 166, 0.3);
}
.stat-value {
  font-size: 44px;
  font-weight: 700;
  text-shadow: 0 0 10px currentColor;
}
.stat-value.blue { color: #6bc0fb; }
.stat-value.green { color: #7fec9d; }
.stat-value.orange { color: #fedd8b; }
.stat-value.red { color: #ffa597; }
.stat-value.purple { color: #84e4dd; }
.stat-label {
  font-size: 19px;
  color: #61d2f7;
  margin-top: 4px;
}

/* 角落装饰 */
.corner-decoration {
  position: absolute;
  width: 20px;
  height: 20px;
}
.corner-decoration::before, .corner-decoration::after {
  content: '';
  position: absolute;
  background: #4788fb;
  box-shadow: 0 0 8px #4788fb;
}
.corner-decoration.tl { top: -1px; left: -1px; }
.corner-decoration.tl::before { width: 20px; height: 3px; top: 0; left: 0; }
.corner-decoration.tl::after { width: 3px; height: 20px; top: 0; left: 0; }
.corner-decoration.tr { top: -1px; right: -1px; }
.corner-decoration.tr::before { width: 20px; height: 3px; top: 0; right: 0; }
.corner-decoration.tr::after { width: 3px; height: 20px; top: 0; right: 0; }
.corner-decoration.bl { bottom: -1px; left: -1px; }
.corner-decoration.bl::before { width: 20px; height: 3px; bottom: 0; left: 0; }
.corner-decoration.bl::after { width: 3px; height: 20px; bottom: 0; left: 0; }
.corner-decoration.br { bottom: -1px; right: -1px; }
.corner-decoration.br::before { width: 20px; height: 3px; bottom: 0; right: 0; }
.corner-decoration.br::after { width: 3px; height: 20px; bottom: 0; right: 0; }

/* 主体内容区 */
.main-content {
  display: flex;
  gap: 8px;
  height: calc(100vh - 190px);
  min-height: 500px;
}
.panel-column {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.left-panel, .right-panel { width: 25%; }
.center-panel { flex: 1; }

/* 面板盒子 */
.panel-box {
  flex: 1;
  background: rgba(26, 34, 56, 0.8);
  border: 1px solid #2C58A6;
  border-radius: 8px;
  padding: 10px;
  position: relative;
  box-shadow: 0 0 15px rgba(44, 88, 166, 0.3);
  display: flex;
  flex-direction: column;
}
.panel-title {
  font-size: 21px;
  font-weight: 600;
  color: #61d2f7;
  margin-bottom: 6px;
  padding-left: 10px;
  border-left: 3px solid #4788fb;
  display: flex;
  align-items: center;
  gap: 6px;
}
.chart-container { flex: 1; min-height: 0; }

/* 数据表格 */
.data-table {
  flex: 1;
  overflow: auto;
}
.data-table table {
  width: 100%;
  border-collapse: collapse;
}
.data-table thead tr {
  background: rgba(44, 88, 166, 0.3);
}
.data-table th {
  padding: 8px 10px;
  font-size: 18px;
  font-weight: 600;
  color: #61d2f7;
  text-align: center;
  border-bottom: 1px solid #2C58A6;
}
.data-table tbody tr {
  transition: background 0.3s;
}
.data-table tbody tr:nth-child(odd) {
  background: rgba(7, 41, 81, 0.5);
}
.data-table tbody tr:hover {
  background: rgba(44, 88, 166, 0.3);
}
.data-table td {
  padding: 8px 10px;
  font-size: 18px;
  color: #fff;
  text-align: center;
  border-bottom: 1px solid rgba(44, 88, 166, 0.3);
}
.title-cell, .store-cell, .store-name-cell {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}
.price-cell { color: #fedd8b; }
.sales-cell { color: #7fec9d; }
.rank-badge {
  display: inline-block;
  width: 26px;
  height: 26px;
  line-height: 26px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 600;
  background: #2C58A6;
}
.rank-badge.rank-1 { background: linear-gradient(135deg, #f7971e, #ffd200); }
.rank-badge.rank-2 { background: linear-gradient(135deg, #bdc3c7, #606c88); }
.rank-badge.rank-3 { background: linear-gradient(135deg, #b8860b, #cd853f); }

/* 底部区域 */
.bottom-section {
  margin-top: 8px;
}
.wide-box {
  flex: none;
  height: auto;
}
.store-table {
  max-height: 240px;
}
.table-box {
  flex: 1.2;
}
/* 骨架屏样式 */
.skeleton-box {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 80px;
}
.skeleton-box :deep(.el-skeleton) {
  width: 80%;
}
.skeleton-box :deep(.el-skeleton__item) {
  background: linear-gradient(90deg, rgba(44, 88, 166, 0.3) 25%, rgba(71, 136, 251, 0.3) 50%, rgba(44, 88, 166, 0.3) 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
}
@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
