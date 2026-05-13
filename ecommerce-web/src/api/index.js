import request from './request'

// 用户登录
export const login = (data) => request.post('/user/login', data)

// 用户注册
export const register = (data) => request.post('/user/register', data)

// 商品列表
export const getProductList = (params) => request.get('/product/list', { params })

// 获取关键词列表（用于下拉筛选）
export const getKeywordList = () => request.get('/product/keywords')

// 商品统计（支持按关键词筛选）
export const getKeywordStats = () => request.get('/product/stats/keyword')
export const getProvinceStats = (keyword) => request.get('/product/stats/province', { params: { keyword } })
export const getStoreStats = (limit = 10, keyword) => request.get('/product/stats/store', { params: { limit, keyword } })
export const getStoreDetailStats = (limit = 10, keyword) => request.get('/product/stats/store-detail', { params: { limit, keyword } })
export const getPriceStats = (keyword) => request.get('/product/stats/price', { params: { keyword } })
export const getOverviewStats = (keyword) => request.get('/product/stats/overview', { params: { keyword } })

// 导入商品数据
export const importProduct = (filePath) => request.post('/product/import', null, { params: { filePath } })

// 删除商品
export const deleteProduct = (id) => request.delete('/product/' + id)

// 榜单列表
export const getRankList = (params) => request.get('/rank/list', { params })
export const getCategoryStats = () => request.get('/rank/stats/category')
export const getTopRanks = (limit = 10) => request.get('/rank/top', { params: { limit } })

// 导入榜单数据
export const importRank = (filePath) => request.post('/rank/import', null, { params: { filePath } })

// 爬虫任务（旧接口，保留兼容）
export const startTaobaoCrawl = (keyword, pageCount = 10) => request.post('/crawl/taobao', null, { params: { keyword, pageCount } })
export const startTmallCrawl = () => request.post('/crawl/tmall')
export const startDataClean = () => request.post('/crawl/clean')
export const getTaskStatus = (taskId) => request.get('/crawl/status/' + taskId)

// Python爬虫服务API（新接口）
import axios from 'axios'

const pythonApi = axios.create({
  baseURL: 'http://localhost:5000',
  timeout: 30000
})

// 账号管理
export const startAccountLogin = () => pythonApi.post('/api/account/start-login')
export const confirmAccountLogin = (sessionId) => pythonApi.post('/api/account/confirm-login', { sessionId })
export const addAnotherAccount = (sessionId) => pythonApi.post('/api/account/add-another', { sessionId })
export const finishAccountSetup = (sessionId) => pythonApi.post('/api/account/finish', { sessionId })
export const getAccountList = () => pythonApi.get('/api/account/list')

// 爬虫控制
export const startCrawl = (keyword, pageCount) => pythonApi.post('/api/crawl/start', { keyword, pageCount })
export const continueCrawl = (taskId, keyword, pageCount) => pythonApi.post('/api/crawl/continue', { taskId, keyword, pageCount })
export const stopCrawl = (taskId) => pythonApi.post('/api/crawl/stop', { taskId })
export const getCrawlStatus = (taskId) => pythonApi.get('/api/crawl/status/' + taskId)

// 天猫榜单爬虫
export const startTmallRankCrawl = () => pythonApi.post('/api/tmall/start')

// AI 智能助手
export const aiChat = (message) => request.post('/ai/chat', { message })
