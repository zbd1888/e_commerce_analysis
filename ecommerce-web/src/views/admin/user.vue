<template>
  <div class="user-manage">
    <section class="admin-page-head">
      <div>
        <h2 class="admin-page-title">用户管理</h2>
        <p class="admin-page-desc">维护系统账号、角色和启用状态，确保后台权限边界清晰。</p>
      </div>
      <div class="admin-page-actions">
        <el-button type="primary" @click="showAddDialog" icon="Plus">添加用户</el-button>
      </div>
    </section>

    <el-card class="user-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">用户管理</span>
          <el-tag type="info" effect="plain">共 {{ total }} 位用户</el-tag>
        </div>
      </template>

      <el-table :data="users" v-loading="loading" size="default" stripe :max-height="tableHeight">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="username" label="用户名" min-width="160">
          <template #default="{ row }">
            <div class="user-info">
              <el-avatar :size="32" :style="{ background: getAvatarColor(row.role) }">{{ row.username?.charAt(0).toUpperCase() }}</el-avatar>
              <span class="username">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="role" label="角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" effect="dark">
              {{ row.role === 'admin' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="dark">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" min-width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" :type="row.role === 'admin' ? 'warning' : 'primary'" plain @click="toggleRole(row)">
              {{ row.role === 'admin' ? '降为用户' : '升为管理员' }}
            </el-button>
            <el-button size="small" :type="row.status === 1 ? 'warning' : 'success'" plain @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" plain @click="deleteUser(row.id)" icon="Delete">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="page" :page-size="20" :total="total" layout="total, prev, pager, next" @current-change="fetchUsers" background />
      </div>
    </el-card>

    <!-- 添加用户对话框 -->
    <el-dialog v-model="dialogVisible" title="添加用户" width="450px">
      <el-form :model="newUser" label-width="80px" size="default">
        <el-form-item label="用户名" required>
          <el-input v-model="newUser.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="newUser.nickname" placeholder="请输入昵称（可选）" />
        </el-form-item>
        <el-form-item label="密码" required>
          <el-input v-model="newUser.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="newUser.role">
            <el-radio value="user">普通用户</el-radio>
            <el-radio value="admin">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="addUser" :loading="submitting">确定添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const users = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)
const dialogVisible = ref(false)
const submitting = ref(false)
const newUser = ref({ username: '', nickname: '', password: '', role: 'user' })
const tableHeight = computed(() => Math.max(320, window.innerHeight - 200))
const getAvatarColor = (role) => role === 'admin' ? '#F56C6C' : '#409EFF'

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await request.get('/admin/users', { params: { page: page.value, size: 20 } })
    users.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) { console.error('获取用户失败', e) }
  finally { loading.value = false }
}

const showAddDialog = () => { newUser.value = { username: '', nickname: '', password: '', role: 'user' }; dialogVisible.value = true }

const addUser = async () => {
  if (!newUser.value.username || !newUser.value.password) { ElMessage.warning('请填写用户名和密码'); return }
  submitting.value = true
  try {
    await request.post('/admin/users', newUser.value)
    ElMessage.success('添加成功')
    dialogVisible.value = false
    fetchUsers()
  } catch (e) { ElMessage.error('添加失败: ' + e.message) }
  finally { submitting.value = false }
}

const toggleRole = async (user) => {
  const newRole = user.role === 'admin' ? 'user' : 'admin'
  await request.put(`/admin/users/${user.id}/role`, { role: newRole })
  ElMessage.success('角色修改成功')
  fetchUsers()
}

const toggleStatus = async (user) => {
  const newStatus = user.status === 1 ? 0 : 1
  await request.put(`/admin/users/${user.id}/status`, { status: newStatus })
  ElMessage.success('状态修改成功')
  fetchUsers()
}

const deleteUser = async (id) => {
  await ElMessageBox.confirm('确定删除该用户吗？', '提示', { type: 'warning' })
  await request.delete(`/admin/users/${id}`)
  ElMessage.success('删除成功')
  fetchUsers()
}

onMounted(fetchUsers)
</script>

<style scoped>
.user-manage { padding: 16px; background: var(--app-admin-bg); min-height: calc(100vh - 70px); display: flex; flex-direction: column; }
.user-card { flex: 1; display: flex; flex-direction: column; min-height: 0; border-radius: 10px; border: 1px solid var(--app-admin-border); box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04); }
.user-card :deep(.el-card__body) { padding: 14px 18px; flex: 1; display: flex; flex-direction: column; min-height: 0; }
.user-card :deep(.el-table) { font-size: 15px; width: 100%; }
.user-card :deep(.el-table th) { color: #475569; font-size: 15px; font-weight: 700; }
.user-card :deep(.el-table td) { font-size: 15px; }
.user-card :deep(.el-table .cell) { white-space: nowrap; }
.user-card :deep(.el-button) { font-size: 14px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 17px; font-weight: 700; color: var(--app-admin-text); }
.user-info { display: flex; align-items: center; gap: 10px; }
.username { font-weight: 600; font-size: 15px; }
.pagination-wrapper { margin-top: 10px; flex-shrink: 0; display: flex; justify-content: flex-end; }
.pagination-wrapper :deep(.el-pagination__total),
.pagination-wrapper :deep(.el-pager li) { font-size: 18px; }
</style>
