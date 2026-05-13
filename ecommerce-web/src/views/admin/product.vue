<template>
  <div class="product-manage">
    <section class="admin-page-head">
      <div>
        <h2 class="admin-page-title">商品管理</h2>
        <p class="admin-page-desc">筛选、查看、删除和导出商品数据，支持按品类、价格、销量、店铺与发货地定位数据。</p>
      </div>
      <div class="admin-page-actions">
        <el-tag type="info" effect="plain">共 {{ total }} 条</el-tag>
      </div>
    </section>

    <!-- 筛选条件 -->
    <el-card class="filter-card">
      <el-form :model="filters" inline size="default">
        <el-form-item label="关键词">
          <el-select v-model="filters.keyword" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="k in keywords" :key="k" :label="k" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格区间">
          <el-input-number v-model="filters.minPrice" :min="0" :controls="false" placeholder="最低" style="width: 100px" />
          <span style="margin: 0 8px; color: #909399">~</span>
          <el-input-number v-model="filters.maxPrice" :min="0" :controls="false" placeholder="最高" style="width: 100px" />
        </el-form-item>
        <el-form-item label="销量范围">
          <el-input-number v-model="filters.minSales" :min="0" :controls="false" placeholder="最低" style="width: 100px" />
          <span style="margin: 0 8px; color: #909399">~</span>
          <el-input-number v-model="filters.maxSales" :min="0" :controls="false" placeholder="最高" style="width: 100px" />
        </el-form-item>
        <el-form-item label="发货地">
          <el-select v-model="filters.location" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="l in locations" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="店铺">
          <el-input v-model="filters.shopName" placeholder="店铺名称" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchProducts" icon="Search">搜索</el-button>
          <el-button @click="resetFilters" icon="Refresh">重置</el-button>
          <el-button type="danger" @click="batchDelete" :disabled="selectedIds.length === 0" icon="Delete">
            批量删除 ({{ selectedIds.length }})
          </el-button>
          <el-button @click="exportExcel" icon="Download">导出</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 商品列表 -->
    <el-card class="table-card">
      <el-table :data="products" @selection-change="handleSelection" v-loading="loading" size="default" stripe :max-height="tableHeight">
        <el-table-column type="selection" width="55" />
        <el-table-column label="商品图片" width="90" align="center">
          <template #default="{ row }">
            <el-image :src="row.picUrl" class="product-image" fit="cover" :preview-src-list="[row.picUrl]" preview-teleported>
              <template #error><div class="image-error">无图</div></template>
            </el-image>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="商品标题" show-overflow-tooltip min-width="280" />
        <el-table-column prop="price" label="价格" width="110" align="right" sortable>
          <template #default="{ row }"><span class="price-text">¥{{ row.price }}</span></template>
        </el-table-column>
        <el-table-column prop="saleValue" label="销量" width="110" align="right" sortable>
          <template #default="{ row }"><span class="sales-text">{{ row.saleValue?.toLocaleString() }}</span></template>
        </el-table-column>
        <el-table-column prop="store" label="店铺" width="160" show-overflow-tooltip />
        <el-table-column prop="location" label="发货地" width="100" align="center" />
        <el-table-column prop="keyword" label="关键词" width="120" align="center">
          <template #default="{ row }"><el-tag size="small">{{ row.keyword }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="deleteProduct(row.id)" icon="Delete" plain>删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
          layout="total, sizes, prev, pager, next, jumper" :page-sizes="[20, 50, 100]" @change="fetchProducts" background />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import request from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCacheStore } from '@/store/cache'

const cacheStore = useCacheStore()
const products = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedIds = ref([])
const keywords = computed(() => cacheStore.keywords)
const locations = computed(() => cacheStore.locations)
const filters = ref({ keyword: '', minPrice: null, maxPrice: null, minSales: null, maxSales: null, location: '', shopName: '' })
const tableHeight = computed(() => window.innerHeight - 260)

const handleSelection = (selection) => { selectedIds.value = selection.map(s => s.id) }
const resetFilters = () => { filters.value = { keyword: '', minPrice: null, maxPrice: null, minSales: null, maxSales: null, location: '', shopName: '' }; fetchProducts() }

// 处理图片URL，确保有协议头
const fixImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('//')) return 'https:' + url
  return url
}

const fetchProducts = async () => {
  loading.value = true
  try {
    const res = await request.get('/product/list', { params: { pageNum: page.value, pageSize: pageSize.value, keyword: filters.value.keyword, store: filters.value.shopName, province: filters.value.location } })
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
    // 使用缓存 store，并行加载
    await Promise.all([cacheStore.fetchKeywords(), cacheStore.fetchLocations()])
  } catch (e) { console.error('获取筛选项失败', e) }
}

const deleteProduct = async (id) => {
  await ElMessageBox.confirm('确定删除该商品吗？', '提示', { type: 'warning' })
  await request.delete(`/admin/products/${id}`)
  ElMessage.success('删除成功')
  fetchProducts()
}

const batchDelete = async () => {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个商品吗？`, '提示', { type: 'warning' })
  await request.post('/admin/products/batch-delete', { ids: selectedIds.value })
  ElMessage.success('批量删除成功')
  selectedIds.value = []
  fetchProducts()
}

const exportExcel = () => { window.open(`/api/products/export?${new URLSearchParams(filters.value).toString()}`) }

onMounted(() => { fetchProducts(); fetchFilters() })
</script>

<style scoped>
.product-manage { padding: 16px; min-height: calc(100vh - 70px); background: var(--app-admin-bg); }
.filter-card { margin-bottom: 14px; border-radius: 10px; border: 1px solid var(--app-admin-border); box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04); }
.filter-card :deep(.el-card__body) { padding: 16px 18px 8px; }
.filter-card :deep(.el-form-item) { margin-bottom: 10px; }
.filter-card :deep(.el-form-item__label) { color: #475569; font-size: 15px; font-weight: 600; }
.filter-card :deep(.el-button) { font-size: 15px; }
.filter-card :deep(.el-input__inner),
.filter-card :deep(.el-select .el-input__inner) { font-size: 15px; }
.filter-card :deep(.el-input__wrapper),
.filter-card :deep(.el-select .el-input__wrapper),
.filter-card :deep(.el-input-number .el-input__wrapper) { border-radius: 8px; }
.table-card { margin-bottom: 0; border-radius: 10px; border: 1px solid var(--app-admin-border); box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04); }
.table-card :deep(.el-card__body) { padding: 14px 16px; }
.table-card :deep(.el-table) { font-size: 15px; }
.table-card :deep(.el-table th) { color: #475569; font-size: 15px; font-weight: 700; }
.table-card :deep(.el-table td) { font-size: 15px; }
.product-image { width: 68px; height: 68px; border-radius: 6px; }
.image-error { width: 68px; height: 68px; background: #f5f7fa; display: flex; align-items: center; justify-content: center; color: #909399; font-size: 15px; border-radius: 6px; }
.price-text { color: #e11d48; font-weight: 700; font-size: 15px; }
.sales-text { color: #16a34a; font-weight: 700; font-size: 15px; }
.pagination-wrapper { margin-top: 12px; display: flex; justify-content: flex-end; }
.pagination-wrapper :deep(.el-pagination__total),
.pagination-wrapper :deep(.el-pager li) { font-size: 17px; }
</style>
