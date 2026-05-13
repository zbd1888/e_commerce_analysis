<template>
  <div class="rule-config">
    <section class="admin-page-head">
      <div>
        <h2 class="admin-page-title">爆品规则配置</h2>
        <p class="admin-page-desc">配置品类、销量、价格和店铺标签规则，用于识别和管理爆品条件。</p>
      </div>
      <div class="admin-page-actions">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon> 新增规则
        </el-button>
      </div>
    </section>

    <el-card class="rule-card">
      <template #header>
        <div class="card-header">
          <span>规则列表</span>
          <el-tag type="info" effect="plain">共 {{ rules.length }} 条</el-tag>
        </div>
      </template>

      <!-- 规则列表 -->
      <el-table :data="rules" border stripe class="rule-table" :max-height="ruleTableHeight">
        <el-table-column type="index" label="#" width="60" />
        <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="keyword" label="适用品类" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.keyword" size="small">{{ row.keyword }}</el-tag>
            <el-tag v-else type="info" size="small">全局</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="销量范围" min-width="140">
          <template #default="{ row }">
            {{ row.minSales || 0 }} ~ {{ row.maxSales || '∞' }}
          </template>
        </el-table-column>
        <el-table-column label="价格范围" min-width="150">
          <template #default="{ row }">
            ¥{{ row.minPrice || 0 }} ~ ¥{{ row.maxPrice || '∞' }}
          </template>
        </el-table-column>
        <el-table-column prop="shopTags" label="店铺标签" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="tag in (row.shopTags || '').split(',').filter(Boolean)" :key="tag" size="small" style="margin: 2px">
              {{ tag }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="matchMode" label="匹配模式" width="120">
          <template #default="{ row }">
            <el-tag :type="row.matchMode === 'OR' ? 'warning' : 'primary'" size="small">
              {{ row.matchMode === 'OR' ? '满足任一' : '全部满足' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="规则名称" required>
          <el-input v-model="form.ruleName" placeholder="例如：高销量爆品" />
        </el-form-item>
        <el-form-item label="适用品类">
          <el-select v-model="form.keyword" placeholder="留空表示全局规则" clearable style="width: 100%">
            <el-option v-for="k in keywords" :key="k" :label="k" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="最低销量">
          <el-input-number v-model="form.minSales" :min="0" placeholder="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最高销量">
          <el-input-number v-model="form.maxSales" :min="0" placeholder="不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最低价格">
          <el-input-number v-model="form.minPrice" :min="0" :precision="2" placeholder="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最高价格">
          <el-input-number v-model="form.maxPrice" :min="0" :precision="2" placeholder="不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="店铺标签">
          <el-input v-model="form.shopTags" placeholder="多个标签用逗号分隔，例如：天猫,旗舰店" />
        </el-form-item>
        <el-form-item label="匹配模式">
          <el-radio-group v-model="form.matchMode">
            <el-radio label="AND">
              <span>全部满足</span>
              <el-tooltip content="商品必须同时满足所有设置的条件才算匹配" placement="top">
                <el-icon style="margin-left: 4px; color: #909399;"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-radio>
            <el-radio label="OR">
              <span>满足任一</span>
              <el-tooltip content="商品只需满足任意一个设置的条件即可匹配" placement="top">
                <el-icon style="margin-left: 4px; color: #909399;"><QuestionFilled /></el-icon>
              </el-tooltip>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCacheStore } from '@/store/cache'

const cacheStore = useCacheStore()
const keywords = computed(() => cacheStore.keywords)
const rules = ref([])
const ruleTableHeight = computed(() => Math.max(320, window.innerHeight - 220))
const dialogVisible = ref(false)
const dialogTitle = ref('新增规则')
const form = ref({
  id: null,
  ruleName: '',
  keyword: '',
  minSales: 0,
  maxSales: null,
  minPrice: null,
  maxPrice: null,
  shopTags: '',
  matchMode: 'AND',
  status: 1
})

const fetchRules = async () => {
  try {
    const res = await request.get('/admin/rule/list')
    rules.value = res.data || []
  } catch (e) {
    console.error('获取规则列表失败', e)
    ElMessage.error('获取规则列表失败，该功能需要后端接口支持')
  }
}

const handleAdd = () => {
  dialogTitle.value = '新增规则'
  form.value = { id: null, ruleName: '', keyword: '', minSales: 0, maxSales: null, minPrice: null, maxPrice: null, shopTags: '', matchMode: 'AND', status: 1 }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  dialogTitle.value = '编辑规则'
  form.value = { ...row }
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!form.value.ruleName) {
    ElMessage.warning('请输入规则名称')
    return
  }
  
  try {
    await request.post('/admin/rule/save', form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchRules()
  } catch (e) {
    console.error('保存失败', e)
    ElMessage.error('保存失败，该功能需要后端接口支持')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该规则吗？', '提示', { type: 'warning' })
    await request.delete(`/admin/rule/${row.id}`)
    ElMessage.success('删除成功')
    fetchRules()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除失败', e)
      ElMessage.error('删除失败')
    }
  }
}

onMounted(async () => {
  await cacheStore.fetchKeywords()
  fetchRules()
})
</script>

<style scoped>
.rule-config { padding: 16px; background: var(--app-admin-bg); min-height: calc(100vh - 70px); display: flex; flex-direction: column; }
.rule-card { flex: 1; display: flex; flex-direction: column; min-height: 0; border-radius: 10px; border: 1px solid var(--app-admin-border); box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04); }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-header span { font-size: 17px; font-weight: 700; color: var(--app-admin-text); }
.rule-card :deep(.el-card__body) { padding: 14px 18px; flex: 1; display: flex; flex-direction: column; min-height: 0; }
.rule-table { width: 100%; }
.rule-config :deep(.el-table) { font-size: 15px; }
.rule-config :deep(.el-table th) { color: #475569; font-size: 15px; font-weight: 700; }
.rule-config :deep(.el-table td) { font-size: 15px; }
.rule-config :deep(.el-table .cell) { white-space: nowrap; }
.rule-config :deep(.el-form-item__label) { font-size: 15px; font-weight: 600; }
.rule-config :deep(.el-button) { font-size: 14px; }
.rule-config :deep(.el-tag) { font-size: 13px; }
</style>
