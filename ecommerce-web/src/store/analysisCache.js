import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/api/request'

// 缓存过期时间（5分钟）
const CACHE_EXPIRY_MS = 5 * 60 * 1000
const STORAGE_KEY = 'analysis_cache'

/**
 * 分析数据缓存 Store
 * 实现 Pinia + LocalStorage 两级缓存
 * 用于品类切换时快速加载数据
 */
export const useAnalysisCacheStore = defineStore('analysisCache', () => {
  // 内存缓存（Pinia）
  const regionCache = ref(new Map())  // key: keyword, value: { data, timestamp }
  const hotFeaturesCache = ref(new Map())
  const shopTagCache = ref(new Map())
  
  // 加载状态
  const loadingKeys = ref(new Set())
  
  /**
   * 从 LocalStorage 加载缓存
   */
  const loadFromStorage = () => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        const parsed = JSON.parse(stored)
        const now = Date.now()
        // 只加载未过期的数据
        if (parsed.region) {
          Object.entries(parsed.region).forEach(([key, value]) => {
            if (now - value.timestamp < CACHE_EXPIRY_MS) {
              regionCache.value.set(key, value)
            }
          })
        }
        if (parsed.hotFeatures) {
          Object.entries(parsed.hotFeatures).forEach(([key, value]) => {
            if (now - value.timestamp < CACHE_EXPIRY_MS) {
              hotFeaturesCache.value.set(key, value)
            }
          })
        }
        if (parsed.shopTag) {
          Object.entries(parsed.shopTag).forEach(([key, value]) => {
            if (now - value.timestamp < CACHE_EXPIRY_MS) {
              shopTagCache.value.set(key, value)
            }
          })
        }
      }
    } catch (e) {
      console.warn('加载分析缓存失败', e)
    }
  }
  
  /**
   * 保存缓存到 LocalStorage
   */
  const saveToStorage = () => {
    try {
      const data = {
        region: Object.fromEntries(regionCache.value),
        hotFeatures: Object.fromEntries(hotFeaturesCache.value),
        shopTag: Object.fromEntries(shopTagCache.value)
      }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
    } catch (e) {
      console.warn('保存分析缓存失败', e)
    }
  }
  
  /**
   * 检查缓存是否有效
   */
  const isCacheValid = (cache, key) => {
    const cached = cache.get(key)
    if (!cached) return false
    return Date.now() - cached.timestamp < CACHE_EXPIRY_MS
  }
  
  /**
   * 获取地域分布数据（带缓存）
   */
  const getRegionDistribution = async (keyword = '') => {
    const cacheKey = keyword || '__all__'
    
    // 检查内存缓存
    if (isCacheValid(regionCache.value, cacheKey)) {
      return regionCache.value.get(cacheKey).data
    }
    
    // 防止重复请求
    if (loadingKeys.value.has(`region_${cacheKey}`)) {
      return new Promise((resolve) => {
        const check = setInterval(() => {
          if (!loadingKeys.value.has(`region_${cacheKey}`)) {
            clearInterval(check)
            resolve(regionCache.value.get(cacheKey)?.data || [])
          }
        }, 50)
      })
    }
    
    loadingKeys.value.add(`region_${cacheKey}`)
    try {
      const res = await request.get('/analysis/region-distribution', {
        params: { keyword }
      })
      const data = res.data || []
      regionCache.value.set(cacheKey, { data, timestamp: Date.now() })
      saveToStorage()
      return data
    } finally {
      loadingKeys.value.delete(`region_${cacheKey}`)
    }
  }
  
  /**
   * 获取店铺标签数据（带缓存）
   */
  const getShopTagByCategory = async (keyword = '') => {
    const cacheKey = keyword || '__all__'
    if (isCacheValid(shopTagCache.value, cacheKey)) {
      return shopTagCache.value.get(cacheKey).data
    }
    
    if (loadingKeys.value.has(`shopTag_${cacheKey}`)) {
      return new Promise((resolve) => {
        const check = setInterval(() => {
          if (!loadingKeys.value.has(`shopTag_${cacheKey}`)) {
            clearInterval(check)
            resolve(shopTagCache.value.get(cacheKey)?.data || [])
          }
        }, 50)
      })
    }
    
    loadingKeys.value.add(`shopTag_${cacheKey}`)
    try {
      const res = await request.get('/analysis/shop-tag-by-category', {
        params: { keyword }
      })
      const data = res.data || []
      shopTagCache.value.set(cacheKey, { data, timestamp: Date.now() })
      saveToStorage()
      return data
    } finally {
      loadingKeys.value.delete(`shopTag_${cacheKey}`)
    }
  }
  
  /**
   * 预加载热门品类数据
   */
  const prefetchTopKeywords = async (keywords = []) => {
    // 获取前5个热门品类
    const topKeywords = keywords.slice(0, 5)
    // 并行预加载
    await Promise.all(
      topKeywords.map(keyword => getRegionDistribution(keyword))
    )
  }
  
  /**
   * 清除所有缓存
   */
  const clearCache = () => {
    regionCache.value.clear()
    hotFeaturesCache.value.clear()
    shopTagCache.value.clear()
    localStorage.removeItem(STORAGE_KEY)
  }
  
  // 初始化时从 LocalStorage 加载
  loadFromStorage()
  
  return {
    regionCache,
    hotFeaturesCache,
    shopTagCache,
    loadingKeys,
    getRegionDistribution,
    getShopTagByCategory,
    prefetchTopKeywords,
    clearCache
  }
})

