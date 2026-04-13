<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { moveRosterApi } from '../api/moveRoster'
import { typeRosterApi } from '../api/typeRoster'
import { localizedName, localizedTypeName, localizedDescription } from '../utils/localizedName'

const { t } = useI18n()
const route = useRoute()
const allMoves = ref([])
const types = ref([])
const loading = ref(true)
const error = ref(null)
const search = ref('')
const selectedType = ref('')
const selectedCategory = ref('')
const expandedMove = ref(null)
const moveLearners = ref([])
const learnersLoading = ref(false)

onMounted(async () => {
  try {
    const [movesRes, typesRes] = await Promise.all([moveRosterApi.getMoves(), typeRosterApi.getTypes()])
    allMoves.value = Array.isArray(movesRes.data) ? movesRes.data : []
    types.value = Array.isArray(typesRes.data) ? typesRes.data : []

    if (route.query.highlight) {
      expandedMove.value = route.query.highlight
      loadLearners(route.query.highlight)
    }
  } catch (e) {
    error.value = t('movesPage.loadError')
  } finally {
    loading.value = false
  }
})

const categories = ['PHYSICAL', 'SPECIAL', 'STATUS']

const filtered = computed(() => {
  let list = allMoves.value

  if (search.value) {
    const q = search.value.toLowerCase()
    list = list.filter(
      (m) =>
        (m.displayName && m.displayName.toLowerCase().includes(q)) ||
        (m.name && m.name.toLowerCase().includes(q)) ||
        (m.chineseName && m.chineseName.toLowerCase().includes(q)),
    )
  }

  if (selectedType.value) {
    list = list.filter((m) => m.type && m.type.name === selectedType.value)
  }

  if (selectedCategory.value) {
    list = list.filter((m) => m.category === selectedCategory.value)
  }

  return list
})

async function toggleMove(moveName) {
  if (expandedMove.value === moveName) {
    expandedMove.value = null
    moveLearners.value = []
    return
  }
  expandedMove.value = moveName
  await loadLearners(moveName)
}

async function loadLearners(moveName) {
  learnersLoading.value = true
  try {
    const res = await moveRosterApi.getMoveLearners(moveName)
    moveLearners.value = Array.isArray(res.data) ? res.data : []
  } catch {
    moveLearners.value = []
  } finally {
    learnersLoading.value = false
  }
}

function getTypeName(move) {
  return move.type?.name || ''
}

function categoryBadgeClass(move) {
  const c = (move.category || 'STATUS').toString().toLowerCase()
  return `category-badge ${c}`
}
</script>

<template>
  <div class="container">
    <div class="page-header">
      <h1 class="page-title">{{ t('movesPage.title') }}</h1>
      <span class="result-badge" v-if="!loading && !error">{{ t('common.resultCount', { count: filtered.length }) }}</span>
    </div>

    <div class="filters-bar">
      <div class="search-box">
        <span class="material-symbols-rounded search-icon">search</span>
        <input v-model="search" type="text" :placeholder="t('movesPage.searchPlaceholder')" class="search-input" />
        <button v-if="search" class="search-clear" @click="search = ''">
          <span class="material-symbols-rounded">close</span>
        </button>
      </div>

      <div class="filter-group">
        <select v-model="selectedType" class="filter-select">
          <option value="">{{ t('common.allTypes') }}</option>
          <option v-for="tp in types" :key="tp.name" :value="tp.name">{{ localizedTypeName(tp) }} ({{ tp.name }})</option>
        </select>

        <select v-model="selectedCategory" class="filter-select">
          <option value="">{{ t('common.allCategories') }}</option>
          <option v-for="c in categories" :key="c" :value="c">{{ t('pokemon.categories.' + c) }} ({{ c }})</option>
        </select>
      </div>
    </div>

    <div v-if="loading" class="loading">{{ t('common.loading') }}</div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>

    <div v-else class="moves-list">
      <div class="table-wrap">
        <table class="moves-table">
          <thead>
            <tr>
              <th>{{ t('movesPage.moveTable.name') }}</th>
              <th>{{ t('movesPage.moveTable.type') }}</th>
              <th>{{ t('movesPage.moveTable.category') }}</th>
              <th>{{ t('movesPage.moveTable.power') }}</th>
              <th>{{ t('movesPage.moveTable.accuracy') }}</th>
              <th>{{ t('movesPage.moveTable.pp') }}</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="m in filtered" :key="m.name">
              <tr
                :class="{ 'row-expanded': expandedMove === m.name }"
                class="move-row"
                @click="toggleMove(m.name)"
              >
                <td class="move-name-cell">{{ localizedName(m) || m.name }}</td>
                <td>
                  <span v-if="getTypeName(m)" :class="`type-badge ${getTypeName(m)}`">
                    {{ localizedTypeName(m.type) }}
                  </span>
                </td>
                <td>
                  <span :class="categoryBadgeClass(m)">
                    {{ t('pokemon.categories.' + m.category) }}
                  </span>
                </td>
                <td class="num-cell">{{ m.power ?? '—' }}</td>
                <td class="num-cell">{{ m.accuracy != null ? m.accuracy + '%' : '—' }}</td>
                <td class="num-cell">{{ m.pp ?? '—' }}</td>
              </tr>
              <tr v-if="expandedMove === m.name" class="detail-row">
                <td colspan="6">
                  <div class="move-detail">
                    <p v-if="localizedDescription(m)" class="move-desc">{{ localizedDescription(m) }}</p>

                    <div class="learners-section">
                      <h4>
                        <span class="material-symbols-rounded" style="font-size: 16px">group</span>
                        {{ t('movesPage.learners') }}
                      </h4>
                      <div v-if="learnersLoading" class="loading" style="padding: 20px">{{ t('common.loading') }}</div>
                      <div v-else-if="moveLearners.length" class="learners-grid">
                        <router-link
                          v-for="p in moveLearners"
                          :key="p.apiName"
                          :to="`/pokemon/${p.apiName}`"
                          class="learner-chip"
                        >
                          {{ localizedName(p) }}
                          <span v-if="p.mega || p.isMega" class="mini-mega">M</span>
                        </router-link>
                      </div>
                      <p v-else class="no-data">{{ t('movesPage.noData') }}</p>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="!loading && !error && filtered.length === 0" class="empty-state">
      <span class="material-symbols-rounded empty-icon">search_off</span>
      <p>{{ t('movesPage.empty') }}</p>
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}

.page-header .page-title {
  margin-bottom: 0;
}

.result-badge {
  padding: 4px 12px;
  border-radius: 20px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-muted);
}

.filters-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 28px;
}

.search-box {
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 0 14px;
  flex: 1;
  min-width: 220px;
  transition: border-color 0.2s;
}

.search-box:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.search-icon {
  color: var(--text-muted);
  font-size: 20px;
  margin-right: 10px;
}

.search-input {
  flex: 1;
  padding: 12px 0;
  background: none;
  border: none;
  color: var(--text-primary);
  outline: none;
  font-size: 0.9rem;
}

.search-input::placeholder {
  color: var(--text-muted);
}

.search-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-muted);
  transition: all 0.15s;
}

.search-clear:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-primary);
}

.search-clear .material-symbols-rounded {
  font-size: 16px;
}

.filter-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-select {
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  color: var(--text-primary);
  outline: none;
  cursor: pointer;
  font-size: 0.88rem;
  transition: border-color 0.2s;
}

.filter-select:focus {
  border-color: var(--accent);
}

.filter-select option {
  background: var(--bg-secondary);
}

.table-wrap {
  overflow-x: auto;
  border-radius: var(--radius);
  border: 1px solid var(--border);
}

.moves-table {
  width: 100%;
  border-collapse: collapse;
  background: var(--bg-card);
}

.moves-table th {
  text-align: left;
  padding: 14px 16px;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.02);
  position: sticky;
  top: 0;
  z-index: 1;
}

.moves-table td {
  padding: 12px 16px;
  font-size: 0.88rem;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}

.num-cell {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
}

.move-row {
  cursor: pointer;
  transition: background 0.15s;
}

.move-row:hover {
  background: var(--bg-card-hover);
}

.row-expanded {
  background: var(--bg-card-hover);
}

.move-name-cell {
  font-weight: 600;
}

.detail-row td {
  padding: 0;
  background: rgba(255, 255, 255, 0.02);
}

.move-detail {
  padding: 24px;
}

.move-desc {
  color: var(--text-secondary);
  font-size: 0.9rem;
  margin-bottom: 20px;
  line-height: 1.6;
}

.learners-section h4 {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
}

.learners-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.learner-chip {
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border);
  border-radius: 10px;
  font-size: 0.82rem;
  color: var(--text-primary);
  transition: all 0.15s;
  font-weight: 500;
}

.learner-chip:hover {
  background: var(--bg-card-hover);
  border-color: var(--accent);
  color: var(--accent);
}

.mini-mega {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.55rem;
  font-weight: 800;
  background: linear-gradient(135deg, #ff6b6b, #ffa500);
  color: #fff;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  margin-left: 4px;
  vertical-align: middle;
}

.no-data {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 80px 20px;
  color: var(--text-muted);
}

.empty-icon {
  font-size: 48px;
  opacity: 0.5;
}

.empty-state p {
  font-size: 1rem;
}

@media (max-width: 768px) {
  .moves-table th,
  .moves-table td {
    padding: 10px 12px;
    font-size: 0.82rem;
  }

  .search-box {
    min-width: 100%;
  }

  .filter-group {
    width: 100%;
  }

  .filter-select {
    flex: 1;
  }
}
</style>
