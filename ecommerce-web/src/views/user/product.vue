<template>
  <div class="product-browse">
    <!-- 筛选条件 -->
    <el-card class="filter-card">
      <el-form :model="filters" inline>
        <el-form-item label="品类">
          <el-select v-model="filters.keyword" placeholder="全部品类" clearable style="width: 120px">
            <el-option v-for="k in keywords" :key="k" :label="k" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="filters.minPrice" :controls="false" :min="0" placeholder="最低" style="width: 80px" />
          <span style="margin: 0 5px">-</span>
          <el-input-number v-model="filters.maxPrice" :controls="false" :min="0" placeholder="最高" style="width: 80px" />
        </el-form-item>
        <el-form-item label="销量">
          <el-input-number v-model="filters.minSales" :controls="false" :min="0" placeholder="最低" style="width: 80px" />
          <span style="margin: 0 5px">-</span>
          <el-input-number v-model="filters.maxSales" :controls="false" :min="0" placeholder="最高" style="width: 80px" />
        </el-form-item>
        <el-form-item label="店铺">
          <el-input v-model="filters.store" placeholder="输入店铺名" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="发货地">
          <el-input v-model="filters.location" placeholder="输入发货地" clearable style="width: 100px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchProducts">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 商品卡片列表 -->
    <div class="product-grid" v-loading="loading">
      <el-card v-for="product in products" :key="product.id" class="product-card" shadow="hover">
        <div class="product-image" @click="openDetail(product)">
          <el-image :src="product.picUrl" fit="cover" class="product-img">
            <template #error><div class="image-error"><el-icon><Picture /></el-icon></div></template>
          </el-image>
          <div class="click-hint">点击查看详情</div>
        </div>
        <div class="product-info">
          <div class="product-title" :title="product.title">{{ product.title }}</div>
          <div class="product-meta">
            <span class="price">¥{{ product.price }}</span>
            <span class="sales">销量: {{ product.saleValue }}</span>
          </div>
          <div class="product-shop">
            <el-icon><Shop /></el-icon>
            <span>{{ product.store }}</span>
          </div>
          <div class="product-location">
            <el-icon><Location /></el-icon>
            <span>{{ product.location }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination v-model:current-page="page" :page-size="10" :total="total"
        layout="total, prev, pager, next" @current-change="fetchProducts" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import request from '@/api/request'
import { ElMessage } from 'element-plus'
import { useCacheStore } from '@/store/cache'

const cacheStore = useCacheStore()
const products = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const keywords = computed(() => cacheStore.keywords)
const filters = ref({ keyword: '', minPrice: null, maxPrice: null, minSales: null, maxSales: null, store: '', location: '' })

const resetFilters = () => {
  filters.value = { keyword: '', minPrice: null, maxPrice: null, minSales: null, maxSales: null, store: '', location: '' }
  page.value = 1
  fetchProducts()
}

// 处理图片URL，确保有协议头
const fixImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('//')) return 'https:' + url
  return url
}

const fetchProducts = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: page.value,
      pageSize: pageSize.value,
      keyword: filters.value.keyword || undefined,
      store: filters.value.store || undefined,
      province: filters.value.location || undefined,
      minPrice: filters.value.minPrice || undefined,
      maxPrice: filters.value.maxPrice || undefined,
      minSales: filters.value.minSales || undefined,
      maxSales: filters.value.maxSales || undefined
    }
    const res = await request.get('/product/list', { params })
    // 处理图片URL格式
    const records = res.data?.records || []
    records.forEach(p => { if (p.picUrl) p.picUrl = fixImageUrl(p.picUrl) })
    products.value = records
    total.value = res.data?.total || 0
  } catch (e) { console.error('获取商品失败', e) }
  finally { loading.value = false }
}

const fetchFilters = async () => {
  try {
    await cacheStore.fetchKeywords()
  } catch (e) { console.error('获取筛选项失败', e) }
}

const openDetail = (product) => {
  // 使用 shopUrl 字段（后端返回的商品链接字段名）
  if (product.shopUrl) {
    window.open(product.shopUrl, '_blank')
  } else if (product.productId) {
    // 如果没有shopUrl，尝试通过productId构造淘宝链接
    window.open(`https://item.taobao.com/item.htm?id=${product.productId}`, '_blank')
  } else {
    ElMessage.info('该商品暂无详情链接')
  }
}

onMounted(() => { fetchProducts(); fetchFilters() })
</script>

<style scoped>
.product-browse {
  padding: 12px 14px;
  background: #f0f2f5;
  min-height: calc(100vh - 60px);
  display: flex;
  flex-direction: column;
}
.filter-card {
  margin-bottom: 10px;
  border-radius: 8px;
  flex-shrink: 0;
}
.filter-card :deep(.el-form-item) { margin-bottom: 8px; }
.filter-card :deep(.el-form-item__label) { font-size: 19px; }
.filter-card :deep(.el-input__inner),
.filter-card :deep(.el-input-number .el-input__inner) { font-size: 17px; }

/* 商品网格：固定5列布局，放大卡片减少底部留白 */
.product-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  flex: 1;
}

/* 响应式：根据屏幕宽度调整列数 */
@media (max-width: 1800px) {
  .product-grid { grid-template-columns: repeat(4, 1fr); }
}
@media (max-width: 1500px) {
  .product-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 1200px) {
  .product-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 900px) {
  .product-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 600px) {
  .product-grid { grid-template-columns: 1fr; }
}

.product-card {
  overflow: hidden;
  border-radius: 12px;
  transition: transform 0.3s, box-shadow 0.3s;
}
.product-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 24px rgba(0,0,0,0.15);
}
.product-card :deep(.el-card__body) {
  padding: 0;
}

.product-image {
  position: relative;
  cursor: pointer;
  overflow: hidden;
}
.product-img {
  width: 100%;
  height: 280px;
  transition: transform 0.3s;
}
.product-image:hover .product-img {
  transform: scale(1.05);
}
.product-image:hover .click-hint { opacity: 1; }
.click-hint {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0,0,0,0.8));
  color: #fff;
  text-align: center;
  padding: 15px 8px 10px;
  opacity: 0;
  transition: opacity 0.3s;
  font-size: 17px;
}
.image-error {
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);
  color: #909399;
  font-size: 48px;
}

.product-info {
  padding: 16px;
}
.product-title {
  font-size: 19px;
  color: #303133;
  line-height: 1.5;
  height: 54px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 10px;
}
.product-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 10px 0;
}
.price {
  font-size: 26px;
  font-weight: bold;
  color: #F56C6C;
}
.sales {
  color: #909399;
  font-size: 17px;
  background: #f5f7fa;
  padding: 2px 10px;
  border-radius: 10px;
}
.product-shop, .product-location {
  font-size: 17px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 6px;
}
.product-shop span, .product-location span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pagination-wrap {
  margin-top: 16px;
  padding: 16px;
  display: flex;
  justify-content: center;
  background: #fff;
  border-radius: 8px;
  flex-shrink: 0;
}
.pagination-wrap :deep(.el-pagination) { font-size: 17px; }
.pagination-wrap :deep(.el-pagination .btn-prev),
.pagination-wrap :deep(.el-pagination .btn-next),
.pagination-wrap :deep(.el-pagination .el-pager li) { font-size: 16px; }
</style>

