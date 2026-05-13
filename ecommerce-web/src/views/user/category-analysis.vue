<template>
  <div class="category-analysis-container">
    <!-- 顶部标题 -->
    <header class="analysis-header">
      <div class="header-title-row">
        <h1 class="main-title">行业/品类分析</h1>
        <button class="refresh-btn" @click="handleRefreshCache" :disabled="loading" title="刷新缓存">刷新</button>
      </div>
      <p class="sub-title">洞察市场供需关系，发现蓝海品类机会</p>
    </header>

    <!-- 蓝海红海对比 -->
    <div class="panel-row">
      <div class="panel-box half-width">
        <div class="corner-decoration tl"></div>
        <div class="corner-decoration tr"></div>
        <div class="corner-decoration bl"></div>
        <div class="corner-decoration br"></div>
        <div class="panel-title">蓝海品类（高热度低竞争）</div>
        <div class="data-table">
          <table>
            <thead><tr><th>品类</th><th>热度</th><th>有效商品</th><th>供需比</th></tr></thead>
            <tbody>
              <tr v-for="row in blueOcean" :key="row.keyword">
                <td>{{ row.keyword }}</td>
                <td class="hot-value">{{ row.hotValue?.toLocaleString() }}</td>
                <td>{{ row.validProductCount }}</td>
                <td class="ratio-value">{{ row.supplyDemandRatio?.toFixed(3) }}</td>
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
        <div class="panel-title">红海品类（高竞争）</div>
        <div class="data-table">
          <table>
            <thead><tr><th>品类</th><th>热度</th><th>有效商品</th><th>供需比</th></tr></thead>
            <tbody>
              <tr v-for="row in redOcean" :key="row.keyword">
                <td>{{ row.keyword }}</td>
                <td class="hot-value">{{ row.hotValue?.toLocaleString() }}</td>
                <td>{{ row.validProductCount }}</td>
                <td class="ratio-value red">{{ row.supplyDemandRatio?.toFixed(3) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 市场供需对比图 -->
    <div class="panel-box">
      <div class="corner-decoration tl"></div>
      <div class="corner-decoration tr"></div>
      <div class="corner-decoration bl"></div>
      <div class="corner-decoration br"></div>
      <div class="panel-title">市场供需对比图</div>
      <div ref="supplyDemandChart" class="chart-area" style="height: 420px"></div>
    </div>

    <!-- 品类潜力排行榜 -->
    <div class="panel-box">
      <div class="corner-decoration tl"></div>
      <div class="corner-decoration tr"></div>
      <div class="corner-decoration bl"></div>
      <div class="corner-decoration br"></div>
      <div class="panel-title">品类潜力排行榜</div>
      <div class="data-table">
        <table>
          <thead><tr><th>#</th><th>品类</th><th>综合评分</th><th>热度分</th><th>竞争分</th><th>榜单热度</th><th>商品数</th><th>建议</th></tr></thead>
          <tbody>
            <tr v-for="(row, index) in categoryPotential" :key="row.keyword">
              <td><span class="rank-badge" :class="'rank-' + (index + 1)">{{ index + 1 }}</span></td>
              <td>{{ row.keyword }}</td>
              <td>
                <div class="progress-bar">
                  <div class="progress-fill" :style="{ width: row.totalScore + '%', background: getScoreColor(row.totalScore) }"></div>
                  <span class="progress-text">{{ row.totalScore }}</span>
                </div>
              </td>
              <td class="hot-value">{{ row.hotScore }}</td>
              <td>{{ row.competitionScore }}</td>
              <td class="hot-value">{{ row.hotValue?.toLocaleString() }}</td>
              <td>{{ row.productCount }}</td>
              <td class="recommend-cell">{{ row.recommendation }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { useCacheStore } from '@/store/cache'
import { useAnalysisCacheStore } from '@/store/analysisCache'
import { ElMessage } from 'element-plus'

const cacheStore = useCacheStore()
const analysisCacheStore = useAnalysisCacheStore()
const loading = ref(false)
const blueOcean = ref([])
const redOcean = ref([])
const categoryPotential = ref([])
const supplyDemandChart = ref(null)
const supplyData = ref([])
let chartInstance = null

const getScoreColor = (score) => score >= 70 ? '#7fec9d' : score >= 50 ? '#fedd8b' : '#ffa597'

const initChart = () => {
  if (supplyDemandChart.value && supplyData.value.length) {
    chartInstance = echarts.init(supplyDemandChart.value)
    const data = supplyData.value.slice(0, 12)
    chartInstance.setOption({
      backgroundColor: 'transparent',
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(0,0,0,0.7)', borderColor: '#2C58A6', textStyle: { color: '#fff' } },
      legend: { data: ['榜单热度', '有效商品数'], top: 5, textStyle: { color: '#61d2f7', fontSize: 14 } },
      grid: { left: '3%', right: '4%', bottom: '20%', top: '15%', containLabel: true },
      xAxis: { type: 'category', data: data.map(d => d.keyword), axisLabel: { rotate: 40, fontSize: 14, color: '#61d2f7' }, axisLine: { lineStyle: { color: '#2C58A6' } } },
      yAxis: [
        { type: 'value', name: '热度', nameTextStyle: { color: '#61d2f7' }, axisLabel: { color: '#61d2f7', formatter: v => v >= 10000 ? (v/10000).toFixed(0)+'万' : v }, splitLine: { lineStyle: { color: '#1a2a4a' } }, axisLine: { lineStyle: { color: '#2C58A6' } } },
        { type: 'value', name: '商品数', nameTextStyle: { color: '#61d2f7' }, axisLabel: { color: '#61d2f7' }, splitLine: { show: false }, axisLine: { lineStyle: { color: '#2C58A6' } } }
      ],
      series: [
        { name: '榜单热度', type: 'bar', data: data.map(d => d.hotValue), itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{offset: 0, color: '#6bc0fb'}, {offset: 1, color: '#4788fb'}]) }, barWidth: '40%',
          label: { show: true, position: 'top', color: '#6bc0fb', fontSize: 13, formatter: p => p.value >= 10000 ? (p.value/10000).toFixed(1)+'万' : p.value }
        },
        { name: '有效商品数', type: 'line', yAxisIndex: 1, data: data.map(d => d.validProductCount), smooth: true, symbol: 'circle', symbolSize: 6, itemStyle: { color: '#ffa597' }, lineStyle: { width: 2 } }
      ]
    })
  }
}

const fetchData = async (force = false) => {
  try {
    const data = await cacheStore.fetchCategoryAnalysis(force)
    if (data) {
      blueOcean.value = data.blueOcean || []
      redOcean.value = data.redOcean || []
      categoryPotential.value = data.categoryPotential || []
      supplyData.value = data.supplyData || []
      initChart()
    }
  } catch (e) { console.error('加载数据失败', e) }
}

// 刷新缓存
const handleRefreshCache = async () => {
  cacheStore.clearCache()
  analysisCacheStore.clearCache()
  loading.value = true
  await fetchData(true)
  loading.value = false
  ElMessage.success('缓存已刷新')
}

onMounted(fetchData)
onUnmounted(() => { if (chartInstance) chartInstance.dispose() })
</script>

<style scoped>
.category-analysis-container { min-height: 100vh; background: linear-gradient(135deg, #0f1629 0%, #1a2238 100%); padding: 10px 14px; color: #fff; }
.analysis-header { text-align: center; padding: 6px 0 12px; }
.header-title-row { display: flex; align-items: center; justify-content: center; gap: 16px; }
.main-title { font-size: 34px; font-weight: bold; color: #61d2f7; text-shadow: 0 0 10px rgba(97, 210, 247, 0.5); margin: 0; }
.sub-title { font-size: 20px; color: #a0b4c8; margin: 6px 0 0; }
.refresh-btn { background: rgba(71, 136, 251, 0.3); border: 1px solid #4788fb; color: #61d2f7; padding: 6px 14px; border-radius: 4px; cursor: pointer; font-size: 18px; transition: all 0.3s; }
.refresh-btn:hover:not(:disabled) { background: rgba(71, 136, 251, 0.5); }
.refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.panel-row { display: flex; gap: 10px; margin-bottom: 10px; }
.panel-box { background: rgba(10, 20, 40, 0.6); border: 1px solid #2C58A6; border-radius: 8px; padding: 12px 14px; position: relative; margin-bottom: 10px; }
.half-width { flex: 1; }

.corner-decoration { position: absolute; width: 12px; height: 12px; }
.corner-decoration.tl { top: 0; left: 0; border-top: 2px solid #4788fb; border-left: 2px solid #4788fb; }
.corner-decoration.tr { top: 0; right: 0; border-top: 2px solid #4788fb; border-right: 2px solid #4788fb; }
.corner-decoration.bl { bottom: 0; left: 0; border-bottom: 2px solid #4788fb; border-left: 2px solid #4788fb; }
.corner-decoration.br { bottom: 0; right: 0; border-bottom: 2px solid #4788fb; border-right: 2px solid #4788fb; }

.panel-title { font-size: 22px; font-weight: bold; color: #61d2f7; margin-bottom: 10px; }

.data-table { overflow-x: auto; max-height: 320px; overflow-y: auto; }
.data-table table { width: 100%; border-collapse: collapse; font-size: 19px; }
.data-table th { background: rgba(44, 88, 166, 0.4); color: #61d2f7; padding: 12px 10px; text-align: left; position: sticky; top: 0; z-index: 1; font-size: 19px; font-weight: 600; }
.data-table td { padding: 10px; border-bottom: 1px solid rgba(44, 88, 166, 0.3); color: #c0d4e8; font-size: 19px; }
.data-table tr:nth-child(even) { background: rgba(44, 88, 166, 0.1); }
.data-table tr:hover { background: rgba(71, 136, 251, 0.2); }

.hot-value { color: #ffa597; font-weight: bold; }
.ratio-value { color: #7fec9d; }
.ratio-value.red { color: #ffa597; }
.recommend-cell { color: #a0b4c8; font-size: 17px; max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.rank-badge { display: inline-block; width: 28px; height: 28px; line-height: 28px; text-align: center; border-radius: 4px; font-size: 15px; font-weight: bold; background: rgba(44, 88, 166, 0.5); color: #61d2f7; }
.rank-badge.rank-1 { background: linear-gradient(135deg, #ffd700, #ffb800); color: #1a1a1a; }
.rank-badge.rank-2 { background: linear-gradient(135deg, #c0c0c0, #a0a0a0); color: #1a1a1a; }
.rank-badge.rank-3 { background: linear-gradient(135deg, #cd7f32, #b87333); color: #fff; }

.progress-bar { position: relative; height: 26px; background: rgba(44, 88, 166, 0.3); border-radius: 10px; overflow: hidden; min-width: 90px; }
.progress-fill { height: 100%; border-radius: 10px; transition: width 0.5s ease; }
.progress-text { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); font-size: 15px; font-weight: bold; color: #fff; text-shadow: 0 0 2px rgba(0,0,0,0.5); }

.chart-area { width: 100%; }
</style>

