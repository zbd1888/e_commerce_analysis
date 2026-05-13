import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/api/request'

// 缓存过期时间
const CACHE_MS = 10 * 60 * 1000 // 10分钟缓存

/**
 * 全局缓存 Store
 * 用于缓存关键词、发货地、Dashboard、分析页面等数据，避免每次切换页面都重新请求
 */
export const useCacheStore = defineStore('cache', () => {
  // 基础缓存数据
  const keywords = ref([])
  const locations = ref([])

  // Dashboard缓存
  const dashboardData = ref(null)
  const dashboardCacheTime = ref(0)
  const dashboardLoading = ref(false)

  // 爆品分析缓存（按关键词存储）
  const hotAnalysisCache = ref({})  // { keyword: { data, time } }
  const hotAnalysisLoading = ref({})

  // 品类分析缓存
  const categoryAnalysisData = ref(null)
  const categoryAnalysisCacheTime = ref(0)
  const categoryAnalysisLoading = ref(false)
  
  // 加载状态
  const keywordsLoading = ref(false)
  const locationsLoading = ref(false)
  
  // 是否已加载
  const keywordsLoaded = ref(false)
  const locationsLoaded = ref(false)

  /**
   * 获取关键词列表（带缓存）
   */
  const fetchKeywords = async (force = false) => {
    // 如果已加载且不强制刷新，直接返回缓存
    if (keywordsLoaded.value && !force) {
      return keywords.value
    }
    
    // 如果正在加载，等待完成
    if (keywordsLoading.value) {
      return new Promise((resolve) => {
        const check = setInterval(() => {
          if (!keywordsLoading.value) {
            clearInterval(check)
            resolve(keywords.value)
          }
        }, 50)
      })
    }
    
    keywordsLoading.value = true
    try {
      const res = await request.get('/product/keywords')
      keywords.value = res.data || []
      keywordsLoaded.value = true
    } catch (e) {
      console.error('获取关键词失败', e)
    } finally {
      keywordsLoading.value = false
    }
    return keywords.value
  }

  /**
   * 获取发货地列表（带缓存）
   */
  const fetchLocations = async (force = false) => {
    if (locationsLoaded.value && !force) {
      return locations.value
    }
    
    if (locationsLoading.value) {
      return new Promise((resolve) => {
        const check = setInterval(() => {
          if (!locationsLoading.value) {
            clearInterval(check)
            resolve(locations.value)
          }
        }, 50)
      })
    }
    
    locationsLoading.value = true
    try {
      const res = await request.get('/product/locations')
      locations.value = res.data || []
      locationsLoaded.value = true
    } catch (e) {
      console.error('获取发货地失败', e)
    } finally {
      locationsLoading.value = false
    }
    return locations.value
  }

  /**
   * 获取Dashboard数据（带2分钟缓存）
   */
  const fetchDashboard = async (force = false) => {
    if (!force && dashboardData.value && Date.now() - dashboardCacheTime.value < CACHE_MS) {
      return dashboardData.value
    }
    if (dashboardLoading.value) {
      return new Promise((resolve) => {
        const check = setInterval(() => {
          if (!dashboardLoading.value) { clearInterval(check); resolve(dashboardData.value) }
        }, 50)
      })
    }
    dashboardLoading.value = true
    try {
      const res = await request.get('/dashboard/aggregated', { params: { limit: 10 } })
      dashboardData.value = res.data || {}
      dashboardCacheTime.value = Date.now()
    } catch (e) {
      console.error('获取Dashboard数据失败', e)
    } finally {
      dashboardLoading.value = false
    }
    return dashboardData.value
  }

  /**
   * 获取爆品分析数据（带缓存，按关键词区分）
   */
  const fetchHotAnalysis = async (keyword = '', force = false) => {
    const key = keyword || '__all__'
    const cached = hotAnalysisCache.value[key]
    if (!force && cached && Date.now() - cached.time < CACHE_MS) {
      return cached.data
    }
    if (hotAnalysisLoading.value[key]) {
      return new Promise((resolve) => {
        const check = setInterval(() => {
          if (!hotAnalysisLoading.value[key]) { clearInterval(check); resolve(hotAnalysisCache.value[key]?.data) }
        }, 50)
      })
    }
    hotAnalysisLoading.value[key] = true
    try {
      const [hotFeatRes, hotKwRes] = await Promise.all([
        request.get('/analysis/hot-product-features', { params: { keyword } }),
        request.get('/analysis/hot-title-keywords', { params: { keyword } })
      ])
      const data = {
        hotFeatures: hotFeatRes.data || {},
        hotTitleKeywords: hotKwRes.data || []
      }
      hotAnalysisCache.value[key] = { data, time: Date.now() }
      return data
    } catch (e) {
      console.error('获取爆品分析数据失败', e)
      return null
    } finally {
      hotAnalysisLoading.value[key] = false
    }
  }

  /**
   * 获取品类分析数据（带2分钟缓存）
   */
  const fetchCategoryAnalysis = async (force = false) => {
    if (!force && categoryAnalysisData.value && Date.now() - categoryAnalysisCacheTime.value < CACHE_MS) {
      return categoryAnalysisData.value
    }
    if (categoryAnalysisLoading.value) {
      return new Promise((resolve) => {
        const check = setInterval(() => {
          if (!categoryAnalysisLoading.value) { clearInterval(check); resolve(categoryAnalysisData.value) }
        }, 50)
      })
    }
    categoryAnalysisLoading.value = true
    try {
      const [blueRes, redRes, potentialRes, supplyRes] = await Promise.all([
        request.get('/analysis/blue-ocean'),
        request.get('/analysis/red-ocean'),
        request.get('/analysis/category-potential'),
        request.get('/analysis/market-supply-demand')
      ])
      categoryAnalysisData.value = {
        blueOcean: blueRes.data || [],
        redOcean: redRes.data || [],
        categoryPotential: potentialRes.data || [],
        supplyData: supplyRes.data || []
      }
      categoryAnalysisCacheTime.value = Date.now()
    } catch (e) {
      console.error('获取品类分析数据失败', e)
    } finally {
      categoryAnalysisLoading.value = false
    }
    return categoryAnalysisData.value
  }

  /**
   * 清除所有缓存（刷新数据时调用）
   */
  const clearCache = () => {
    keywords.value = []
    locations.value = []
    keywordsLoaded.value = false
    locationsLoaded.value = false
    dashboardData.value = null
    dashboardCacheTime.value = 0
    hotAnalysisCache.value = {}
    categoryAnalysisData.value = null
    categoryAnalysisCacheTime.value = 0
  }

  return {
    keywords,
    locations,
    keywordsLoading,
    locationsLoading,
    dashboardData,
    dashboardLoading,
    fetchKeywords,
    fetchLocations,
    fetchDashboard,
    fetchHotAnalysis,
    fetchCategoryAnalysis,
    clearCache
  }
})

