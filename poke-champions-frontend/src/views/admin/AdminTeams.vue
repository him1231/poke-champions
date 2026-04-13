<script setup>
import { ref, watch, onMounted } from 'vue'
import { adminApi } from '../../api/adminApi'

const teams = ref([])
const loading = ref(true)
const search = ref('')
const filter = ref('all')
const sort = ref('latest')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)

const deleteTarget = ref(null)
const deleting = ref(false)

let searchTimer = null

async function fetchTeams() {
  loading.value = true
  try {
    const { data } = await adminApi.getTeams({
      search: search.value || undefined,
      filter: filter.value,
      sort: sort.value,
      page: page.value,
      size: 20,
    })
    teams.value = data.content || []
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
  } catch (e) {
    console.error('Failed to load teams', e)
  } finally {
    loading.value = false
  }
}

function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { page.value = 0; fetchTeams() }, 400)
}

function setFilter(f) {
  filter.value = f
  page.value = 0
  fetchTeams()
}

function setSort(s) {
  sort.value = s
  page.value = 0
  fetchTeams()
}

function goPage(p) {
  page.value = p
  fetchTeams()
}

async function toggleExpired(team) {
  try {
    await adminApi.markExpired(team.rentalCode, !team.expired)
    team.expired = !team.expired
  } catch (e) {
    console.error('Failed to toggle expired', e)
  }
}

function confirmDelete(team) {
  deleteTarget.value = team
}

async function doDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await adminApi.forceDelete(deleteTarget.value.rentalCode)
    deleteTarget.value = null
    fetchTeams()
  } catch (e) {
    console.error('Failed to delete', e)
  } finally {
    deleting.value = false
  }
}

function statusLabel(t) {
  if (t.expired) return '已失效'
  if (t.reportCount > 0) return `被回報 (${t.reportCount})`
  return '正常'
}

function statusClass(t) {
  if (t.expired) return 'status-expired'
  if (t.reportCount > 0) return 'status-reported'
  return 'status-ok'
}

function formatBattle(f) {
  if (f === 'doubles') return '雙打'
  return '單打'
}

onMounted(fetchTeams)
</script>

<template>
  <div class="admin-teams">
    <h1 class="page-title">
      <span class="material-symbols-rounded">list_alt</span>
      隊伍管理
      <span class="title-count">{{ totalElements }} 筆</span>
    </h1>

    <!-- Toolbar -->
    <div class="toolbar">
      <div class="search-box">
        <span class="material-symbols-rounded">search</span>
        <input
          v-model="search"
          @input="onSearchInput"
          type="text"
          placeholder="搜尋代碼或標題…"
        />
      </div>

      <div class="filter-tabs">
        <button
          v-for="f in [
            { key: 'all', label: '全部', icon: 'apps' },
            { key: 'reported', label: '被回報', icon: 'flag' },
            { key: 'expired', label: '已失效', icon: 'cancel' },
          ]"
          :key="f.key"
          :class="['filter-tab', { active: filter === f.key }]"
          @click="setFilter(f.key)"
        >
          <span class="material-symbols-rounded">{{ f.icon }}</span>
          {{ f.label }}
        </button>
      </div>

      <div class="sort-tabs">
        <button
          v-for="s in [
            { key: 'latest', label: '最新' },
            { key: 'popular', label: '最多瀏覽' },
            { key: 'mostReported', label: '最多回報' },
          ]"
          :key="s.key"
          :class="['sort-btn', { active: sort === s.key }]"
          @click="setSort(s.key)"
        >{{ s.label }}</button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <span class="material-symbols-rounded spin">progress_activity</span>
    </div>

    <!-- Table -->
    <div v-else-if="teams.length" class="table-wrap">
      <table class="teams-table">
        <thead>
          <tr>
            <th>隊伍 ID</th>
            <th>標題</th>
            <th>模式</th>
            <th>瀏覽</th>
            <th>回報</th>
            <th>狀態</th>
            <th>建立日期</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in teams" :key="t.rentalCode" :class="{ 'row-expired': t.expired }">
            <td class="col-code">{{ t.rentalCode }}</td>
            <td class="col-title">{{ t.title }}</td>
            <td class="col-format">{{ formatBattle(t.battleFormat) }}</td>
            <td class="col-num">{{ t.viewCount }}</td>
            <td class="col-num">
              <span v-if="t.reportCount > 0" class="report-badge">{{ t.reportCount }}</span>
              <span v-else>0</span>
            </td>
            <td>
              <span :class="['status-badge', statusClass(t)]">{{ statusLabel(t) }}</span>
            </td>
            <td class="col-date">{{ new Date(t.createdAt).toLocaleDateString() }}</td>
            <td class="col-actions">
              <button
                class="action-btn"
                :class="t.expired ? 'btn-restore' : 'btn-expire'"
                @click="toggleExpired(t)"
                :title="t.expired ? '恢復' : '標記失效'"
              >
                <span class="material-symbols-rounded">{{ t.expired ? 'undo' : 'block' }}</span>
              </button>
              <button class="action-btn btn-del" @click="confirmDelete(t)" title="刪除">
                <span class="material-symbols-rounded">delete</span>
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Empty -->
    <div v-else class="empty-state">
      <span class="material-symbols-rounded">inbox</span>
      <p>沒有找到符合條件的隊伍</p>
    </div>

    <!-- Pagination -->
    <div v-if="totalPages > 1" class="pagination">
      <button :disabled="page <= 0" @click="goPage(page - 1)" class="page-btn">
        <span class="material-symbols-rounded">chevron_left</span>
      </button>
      <span class="page-info">{{ page + 1 }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages - 1" @click="goPage(page + 1)" class="page-btn">
        <span class="material-symbols-rounded">chevron_right</span>
      </button>
    </div>

    <!-- Delete confirm -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="deleteTarget" class="modal-overlay" @click.self="deleteTarget = null">
          <div class="modal-box">
            <h3 class="delete-title">確定要刪除隊伍？</h3>
            <p class="delete-msg">
              將永久刪除 <strong>{{ deleteTarget.rentalCode }}</strong>（{{ deleteTarget.title }}），此操作無法復原。
            </p>
            <div class="modal-actions">
              <button class="btn-cancel" @click="deleteTarget = null">取消</button>
              <button class="btn-danger" :disabled="deleting" @click="doDelete">
                <span v-if="deleting" class="material-symbols-rounded spin">progress_activity</span>
                確定刪除
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.admin-teams { padding: 0; }

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1.4rem;
  color: var(--text-primary, #f1f5f9);
  margin: 0 0 24px;
}
.page-title .material-symbols-rounded { font-size: 28px; color: var(--accent, #f97316); }
.title-count {
  font-size: 0.8rem;
  font-weight: 400;
  color: var(--text-muted, #94a3b8);
  margin-left: auto;
}

/* Toolbar */
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
  align-items: center;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: var(--bg-base, #0f172a);
  border: 1px solid var(--border, #334155);
  border-radius: 10px;
  flex: 1;
  min-width: 200px;
}
.search-box .material-symbols-rounded { font-size: 20px; color: var(--text-muted, #94a3b8); }
.search-box input {
  flex: 1;
  background: transparent;
  border: none;
  color: var(--text-primary, #f1f5f9);
  font-size: 0.88rem;
  font-family: inherit;
  outline: none;
}

.filter-tabs, .sort-tabs {
  display: flex;
  gap: 4px;
}

.filter-tab, .sort-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 7px 14px;
  border: 1px solid var(--border, #334155);
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted, #94a3b8);
  font-size: 0.8rem;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}
.filter-tab .material-symbols-rounded { font-size: 16px; }
.filter-tab.active, .sort-btn.active {
  background: var(--accent-soft, rgba(249,115,22,0.12));
  border-color: var(--accent-glow, rgba(249,115,22,0.3));
  color: var(--accent, #f97316);
}

/* Table */
.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--border, #334155);
  border-radius: 12px;
}

.teams-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

.teams-table th {
  text-align: left;
  padding: 12px 14px;
  background: var(--bg-base, #0f172a);
  color: var(--text-muted, #94a3b8);
  font-weight: 600;
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 1px solid var(--border, #334155);
  white-space: nowrap;
}

.teams-table td {
  padding: 10px 14px;
  border-bottom: 1px solid var(--border, #334155);
  color: var(--text-primary, #f1f5f9);
  vertical-align: middle;
}

.teams-table tbody tr:last-child td { border-bottom: none; }
.teams-table tbody tr:hover { background: rgba(255,255,255,0.02); }
.row-expired { opacity: 0.55; }

.col-code { font-weight: 600; font-family: monospace; white-space: nowrap; }
.col-title { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-num { text-align: center; }
.col-date { white-space: nowrap; color: var(--text-muted, #94a3b8); font-size: 0.82rem; }

.report-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 99px;
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
  font-weight: 600;
  font-size: 0.78rem;
}

.status-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 99px;
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
}
.status-ok { background: rgba(16,185,129,0.12); color: #10b981; }
.status-reported { background: rgba(245,158,11,0.12); color: #f59e0b; }
.status-expired { background: rgba(239,68,68,0.12); color: #ef4444; }

/* Actions */
.col-actions {
  display: flex;
  gap: 6px;
  white-space: nowrap;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border, #334155);
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  transition: all 0.2s;
}
.action-btn .material-symbols-rounded { font-size: 18px; }

.btn-expire { color: #f59e0b; }
.btn-expire:hover { background: rgba(245,158,11,0.15); border-color: #f59e0b; }
.btn-restore { color: #10b981; }
.btn-restore:hover { background: rgba(16,185,129,0.15); border-color: #10b981; }
.btn-del { color: #ef4444; }
.btn-del:hover { background: rgba(239,68,68,0.15); border-color: #ef4444; }

/* Empty / Loading */
.loading-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: var(--text-muted, #94a3b8);
  gap: 12px;
}
.loading-state .material-symbols-rounded,
.empty-state .material-symbols-rounded {
  font-size: 40px;
  color: var(--text-muted, #94a3b8);
}

/* Pagination */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
}
.page-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--border, #334155);
  border-radius: 8px;
  background: transparent;
  color: var(--text-primary, #f1f5f9);
  cursor: pointer;
}
.page-btn:disabled { opacity: 0.3; cursor: not-allowed; }
.page-info { font-size: 0.85rem; color: var(--text-muted, #94a3b8); }

/* Delete Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.modal-box {
  background: var(--bg-card, #1e293b);
  border: 1px solid var(--border, #334155);
  border-radius: 16px;
  padding: 28px;
  max-width: 420px;
  width: 90%;
}
.delete-title { color: #ef4444; font-size: 1.1rem; margin: 0 0 8px; }
.delete-msg { color: var(--text-muted, #94a3b8); font-size: 0.9rem; line-height: 1.5; margin: 0 0 20px; }
.modal-actions { display: flex; gap: 10px; justify-content: flex-end; }

.btn-cancel, .btn-danger {
  padding: 8px 18px;
  border: 1px solid var(--border, #334155);
  border-radius: 8px;
  font-size: 0.85rem;
  font-family: inherit;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
}
.btn-cancel {
  background: transparent;
  color: var(--text-muted, #94a3b8);
}
.btn-danger {
  background: #ef4444;
  border-color: #ef4444;
  color: #fff;
  font-weight: 600;
}
.btn-danger:disabled { opacity: 0.5; }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }

@keyframes spin { to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
</style>
