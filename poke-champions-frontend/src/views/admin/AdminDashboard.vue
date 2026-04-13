<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '../../api/adminApi'

const stats = ref(null)
const loading = ref(true)

const cards = [
  { key: 'totalTeams',    label: '總隊伍數',    icon: 'groups',       color: '#3b82f6' },
  { key: 'publicTeams',   label: '公開隊伍',    icon: 'public',       color: '#10b981' },
  { key: 'reportedTeams', label: '被回報隊伍',  icon: 'flag',         color: '#f59e0b' },
  { key: 'expiredTeams',  label: '已標記失效',  icon: 'cancel',       color: '#ef4444' },
  { key: 'totalViews',    label: '總瀏覽數',    icon: 'visibility',   color: '#8b5cf6' },
]

onMounted(async () => {
  try {
    const { data } = await adminApi.getStats()
    stats.value = data
  } catch (e) {
    console.error('Failed to load stats', e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="admin-dashboard">
    <h1 class="page-title">
      <span class="material-symbols-rounded">dashboard</span>
      儀表板
    </h1>

    <div v-if="loading" class="loading-state">
      <span class="material-symbols-rounded spin">progress_activity</span>
    </div>

    <div v-else-if="stats" class="stats-grid">
      <div v-for="c in cards" :key="c.key" class="stat-card">
        <div class="stat-icon" :style="{ background: c.color + '22', color: c.color }">
          <span class="material-symbols-rounded">{{ c.icon }}</span>
        </div>
        <div class="stat-body">
          <span class="stat-value">{{ stats[c.key]?.toLocaleString() ?? '—' }}</span>
          <span class="stat-label">{{ c.label }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-dashboard { padding: 0; }

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1.4rem;
  color: var(--text-primary, #f1f5f9);
  margin: 0 0 28px;
}
.page-title .material-symbols-rounded { font-size: 28px; color: var(--accent, #f97316); }

.loading-state {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}
.loading-state .material-symbols-rounded { font-size: 32px; color: var(--accent, #f97316); }

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: var(--bg-card, #1e293b);
  border: 1px solid var(--border, #334155);
  border-radius: 12px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-icon .material-symbols-rounded { font-size: 24px; }

.stat-body {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary, #f1f5f9);
  line-height: 1.2;
}

.stat-label {
  font-size: 0.8rem;
  color: var(--text-muted, #94a3b8);
  margin-top: 2px;
}

@keyframes spin { to { transform: rotate(360deg); } }
.spin { animation: spin 1s linear infinite; }
</style>
