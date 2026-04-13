<script setup>
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLocalePath } from '../composables/useLocalePath'
import { teamShareApi } from '../api/teamShareApi'
import { getPokemonImageUrl } from '../utils/pokemonImage'

const { t } = useI18n()
const { localePath } = useLocalePath()

const teams = ref([])
const loading = ref(true)
const sort = ref('latest')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
const PAGE_SIZE = 12

async function fetchTeams() {
  loading.value = true
  try {
    const { data } = await teamShareApi.list(sort.value, page.value, PAGE_SIZE)
    teams.value = data.content
    totalPages.value = data.totalPages
    totalElements.value = data.totalElements
  } catch (err) {
    console.error('Failed to fetch teams', err)
  } finally {
    loading.value = false
  }
}

function changeSort(s) {
  sort.value = s
  page.value = 0
}

function goPage(p) {
  if (p < 0 || p >= totalPages.value) return
  page.value = p
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function previewSprites(team) {
  const ids = team.previewPokemonIds || []
  return ids.slice(0, 6)
}

function battleFormatLabel(team) {
  return team.battleFormat === 'doubles'
    ? t('teamShare.listPage.tagDoubles')
    : t('teamShare.listPage.tagSingles')
}

watch([sort, page], fetchTeams)
onMounted(fetchTeams)
</script>

<template>
  <div class="container team-list-page">
    <div class="page-header">
      <h1 class="page-title">
        <span class="material-symbols-rounded" style="font-size:1.6rem;vertical-align:middle;margin-right:8px">explore</span>
        {{ t('teamShare.listPage.title') }}
      </h1>
      <div class="page-header-actions">
        <router-link
          :to="localePath('/team-builder')"
          class="btn-to-builder"
        >
          <span class="material-symbols-rounded">construction</span>
          {{ t('teamShare.listPage.goToTeamBuilder') }}
        </router-link>
        <div class="sort-tabs">
          <button
            :class="['sort-tab', { active: sort === 'latest' }]"
            @click="changeSort('latest')"
          >
            <span class="material-symbols-rounded">schedule</span>
            {{ t('teamShare.listPage.sortLatest') }}
          </button>
          <button
            :class="['sort-tab', { active: sort === 'popular' }]"
            @click="changeSort('popular')"
          >
            <span class="material-symbols-rounded">trending_up</span>
            {{ t('teamShare.listPage.sortPopular') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 載入中 -->
    <div v-if="loading" class="loading-state">
      <span class="material-symbols-rounded spin">progress_activity</span>
      <span>{{ t('common.loading') }}</span>
    </div>

    <!-- 空狀態 -->
    <div v-else-if="teams.length === 0" class="empty-state">
      <span class="material-symbols-rounded empty-icon">group_off</span>
      <p>{{ t('teamShare.listPage.noTeams') }}</p>
    </div>

    <!-- 隊伍卡片 -->
    <template v-else>
      <div class="teams-grid">
        <router-link
          v-for="team in teams"
          :key="team.rentalCode"
          :to="localePath('/teams/' + team.rentalCode)"
          class="team-card"
        >
          <div class="team-card-header">
            <div class="team-card-title-block">
              <h3 class="team-card-title">{{ team.title }}</h3>
              <span class="bf-tag">{{ battleFormatLabel(team) }}</span>
            </div>
            <span class="team-card-code">{{ team.rentalCode }}</span>
          </div>
          <p v-if="team.description" class="team-card-desc">{{ team.description }}</p>
          <div class="team-card-sprites">
            <img
              v-for="(poke, idx) in previewSprites(team)"
              :key="idx"
              :src="typeof poke === 'object' ? getPokemonImageUrl(poke) : ''"
              class="team-sprite"
              loading="lazy"
            />
          </div>
          <div class="team-card-meta">
            <span class="meta-item">
              <span class="material-symbols-rounded">visibility</span>
              {{ team.viewCount }}
            </span>
            <span class="meta-item">
              <span class="material-symbols-rounded">schedule</span>
              {{ new Date(team.createdAt).toLocaleDateString() }}
            </span>
          </div>
        </router-link>
      </div>

      <!-- 分頁 -->
      <div v-if="totalPages > 1" class="pagination">
        <button
          class="page-btn"
          :disabled="page === 0"
          @click="goPage(page - 1)"
        >
          <span class="material-symbols-rounded">chevron_left</span>
        </button>
        <span class="page-info">{{ page + 1 }} / {{ totalPages }}</span>
        <button
          class="page-btn"
          :disabled="page >= totalPages - 1"
          @click="goPage(page + 1)"
        >
          <span class="material-symbols-rounded">chevron_right</span>
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32px;
  flex-wrap: wrap;
  gap: 12px;
}

.page-header .page-title { margin-bottom: 0; }

.page-header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.btn-to-builder {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--accent);
  border: none;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 0.85rem;
  font-weight: 600;
  font-family: inherit;
  text-decoration: none;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.2s;
  white-space: nowrap;
}

.btn-to-builder:hover {
  opacity: 0.92;
  transform: translateY(-1px);
}

.btn-to-builder .material-symbols-rounded { font-size: 18px; }

.sort-tabs {
  display: flex;
  gap: 4px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 3px;
}

.sort-tab {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 8px 16px;
  border: none;
  border-radius: 7px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.84rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}

.sort-tab .material-symbols-rounded { font-size: 16px; }

.sort-tab:hover { color: var(--text-primary); }

.sort-tab.active {
  background: var(--accent-soft);
  color: var(--accent);
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 80px 0;
  color: var(--text-muted);
  font-size: 0.9rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 80px 20px;
  color: var(--text-muted);
}

.empty-icon { font-size: 56px; }

.teams-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.team-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  transition: all 0.2s;
  text-decoration: none;
  color: inherit;
}

.team-card:hover {
  border-color: var(--accent-glow);
  background: var(--bg-card-hover);
  transform: translateY(-2px);
  box-shadow: var(--shadow);
}

.team-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.team-card-title-block {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.team-card-title {
  font-size: 1rem;
  font-weight: 700;
  line-height: 1.3;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.bf-tag {
  flex-shrink: 0;
  font-size: 0.68rem;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: var(--radius-xs);
  background: var(--bg-glass);
  border: 1px solid var(--border);
  color: var(--text-secondary);
}

.team-card-code {
  flex-shrink: 0;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 3px 8px;
  border-radius: var(--radius-xs);
  letter-spacing: 0.03em;
}

.team-card-desc {
  font-size: 0.82rem;
  color: var(--text-secondary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.team-card-sprites {
  display: flex;
  gap: 2px;
}

.team-sprite {
  width: 40px;
  height: 40px;
  object-fit: contain;
  image-rendering: pixelated;
  background: var(--bg-glass);
  border-radius: var(--radius-xs);
}

.team-card-meta {
  display: flex;
  gap: 14px;
  padding-top: 8px;
  border-top: 1px solid var(--border);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.76rem;
  color: var(--text-muted);
}

.meta-item .material-symbols-rounded { font-size: 14px; }

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 20px 0;
}

.page-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-glass);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: var(--accent-soft);
  color: var(--accent);
  border-color: var(--accent-glow);
}

.page-btn:disabled { opacity: 0.3; cursor: not-allowed; }

.page-info {
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 1024px) {
  .teams-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 640px) {
  .teams-grid { grid-template-columns: 1fr; }
  .page-header { flex-direction: column; align-items: flex-start; }
}
</style>
