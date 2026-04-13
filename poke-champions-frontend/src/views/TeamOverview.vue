<script setup>
import { computed, ref, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { typeBadgeClasses } from '../utils/pokemonTypesDisplay'
import { localizedName, localizedTypeName, localizedEffect } from '../utils/localizedName'
import { NATURE_DEFS } from '../constants/pokemonNatures'
import { teamToShowdownPaste } from '../utils/showdownTeamExport'
import {
  useTeamStore, ALL_TYPES, TYPE_ZH,
  STAT_KEYS, STAT_LABELS, STAT_COLORS, CATEGORY_LABELS,
  getDefenseMultipliers, calcStat,
} from '../composables/useTeamStore'
import { useLocalePath } from '../composables/useLocalePath'
import ShareTeamModal from '../components/ShareTeamModal.vue'

const { t } = useI18n()
const { localePath } = useLocalePath()
const { teamMembers, pokemonDisplayName } = useTeamStore()

const filledMembers = computed(() =>
  teamMembers
    .map((m, idx) => ({ ...m, slotIdx: idx }))
    .filter(m => m.pokemon)
)

const teamCount = computed(() => filledMembers.value.length)

function natureLabelZh(id) {
  return t('pokemon.natures.' + id)
}

function natureEffect(id) {
  const def = NATURE_DEFS[id]
  if (!def?.mult || Object.keys(def.mult).length === 0) return t('teamOverview.noNatureEffect')
  const parts = []
  for (const [k, v] of Object.entries(def.mult)) {
    if (v > 1) parts.push(`${t('pokemon.stats.' + k)}↑`)
    else if (v < 1) parts.push(`${t('pokemon.stats.' + k)}↓`)
  }
  return parts.join(' ')
}

function natureTrendForStat(natureId, statKey) {
  if (statKey === 'hp') return 'neutral'
  const mult = NATURE_DEFS[natureId]?.mult?.[statKey]
  if (mult == null) return 'neutral'
  if (mult > 1) return 'up'
  if (mult < 1) return 'down'
  return 'neutral'
}

function memberStats(m) {
  if (!m.pokemon) return []
  return STAT_KEYS.map(key => {
    const base = Number(m.pokemon[key] ?? 0)
    const actual = calcStat(m.pokemon, key, m.statPoints, m.nature)
    const maxStat = 260
    return {
      key, label: t('pokemon.stats.' + key), base, actual,
      barPct: Math.min((actual / maxStat) * 100, 100),
      color: STAT_COLORS[key],
      evPts: m.statPoints[key] || 0,
      natureTrend: natureTrendForStat(m.nature, key),
    }
  })
}

// ═══════════════════════════════════════════════════
//  隊伍整體分析
// ═══════════════════════════════════════════════════
const teamDefenseAnalysis = computed(() => {
  const members = filledMembers.value
  if (members.length === 0) return { perType: [], warnings: [], uncovered: [] }

  const perType = ALL_TYPES.map(atkType => {
    let weakCount = 0, resistCount = 0, immuneCount = 0
    members.forEach(m => {
      const mult = getDefenseMultipliers(m.types)[atkType]
      if (mult === 0) immuneCount++
      else if (mult > 1) weakCount++
      else if (mult < 1) resistCount++
    })
    return { type: atkType, typeZh: t('pokemon.types.' + atkType), weakCount, resistCount, immuneCount }
  })

  const warnings = perType.filter(t => t.weakCount >= 2).sort((a, b) => b.weakCount - a.weakCount)
  const uncovered = perType.filter(t => t.resistCount === 0 && t.immuneCount === 0)

  return { perType, warnings, uncovered }
})

const teamTypesCoverage = computed(() => {
  const attackTypes = new Set()
  filledMembers.value.forEach(m => {
    m.moves.forEach(mv => {
      if (mv?.type) attackTypes.add(mv.type)
    })
  })
  return ALL_TYPES.map(tp => ({
    type: tp,
    typeZh: t('pokemon.types.' + tp),
    covered: attackTypes.has(tp),
  }))
})

const uncoveredAttackTypes = computed(() =>
  teamTypesCoverage.value.filter(t => !t.covered)
)

const overviewRoot = ref(null)
const downloading = ref(false)
const exportMenu = ref(null)
const shareModalVisible = ref(false)

function closeExportMenu() {
  const el = exportMenu.value
  if (el) el.open = false
}

function onExportSummaryClick(e) {
  if (teamCount.value === 0) e.preventDefault()
}

async function downloadOverviewPng() {
  if (teamCount.value === 0 || !overviewRoot.value) return
  downloading.value = true
  await nextTick()
  try {
    const { default: html2canvas } = await import('html2canvas')
    const canvas = await html2canvas(overviewRoot.value, {
      scale: Math.min(2, window.devicePixelRatio || 2),
      useCORS: true,
      backgroundColor: '#0f1023',
      logging: false,
      /** 略過操作列；略過整段頭像區（不占位） */
      ignoreElements: el =>
        !!el.closest?.('.overview-export-ignore') || el.classList?.contains('mc-avatar-wrap'),
    })
    const stamp = new Date().toISOString().replace(/\D/g, '').slice(0, 14)
    const link = document.createElement('a')
    link.download = `poke-champions-team-overview-${stamp}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
  } catch (err) {
    console.error('overview export failed', err)
  } finally {
    downloading.value = false
    closeExportMenu()
  }
}

function downloadShowdownTxt() {
  if (teamCount.value === 0) return
  const text = teamToShowdownPaste(filledMembers.value)
  if (!text.trim()) return
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'showdown.txt'
  link.click()
  URL.revokeObjectURL(url)
  closeExportMenu()
}
</script>

<template>
  <div ref="overviewRoot" class="container team-overview">
    <!-- 頁首 -->
    <div class="page-header">
      <h1 class="page-title">
        <span class="material-symbols-rounded" style="font-size:1.6rem;vertical-align:middle;margin-right:8px">summarize</span>
        {{ t('teamOverview.title') }}
      </h1>
      <div class="page-header-actions overview-export-ignore">
        <details ref="exportMenu" class="export-dropdown" :class="{ 'is-disabled': teamCount === 0 }">
          <summary class="btn-export-summary" @click="onExportSummaryClick">
            <span class="material-symbols-rounded">download</span>
            {{ t('teamOverview.export') }}
            <span class="material-symbols-rounded export-dropdown-chevron">expand_more</span>
          </summary>
          <div class="export-dropdown-panel">
            <button
              type="button"
              class="export-dropdown-item"
              :disabled="teamCount === 0 || downloading"
              @click="downloadOverviewPng"
            >
              <span class="material-symbols-rounded">image</span>
              {{ downloading ? t('teamOverview.exporting') : t('teamOverview.downloadPng') }}
            </button>
            <button
              type="button"
              class="export-dropdown-item"
              :disabled="teamCount === 0"
              @click="downloadShowdownTxt"
            >
              <span class="material-symbols-rounded">description</span>
              {{ t('teamOverview.exportShowdown') }}
            </button>
          </div>
        </details>
        <button
          type="button"
          class="btn-share"
          :disabled="teamCount === 0"
          @click="shareModalVisible = true"
        >
          <span class="material-symbols-rounded">share</span>
          {{ t('teamShare.shareModal.title') }}
        </button>
        <router-link :to="localePath('/team-builder')" class="btn-back-edit">
          <span class="material-symbols-rounded">edit</span>
          {{ t('teamOverview.editTeam') }}
        </router-link>
      </div>
    </div>

    <template v-if="teamCount === 0">
      <div class="empty-state">
        <span class="material-symbols-rounded empty-icon">group_off</span>
        <p>{{ t('teamOverview.emptyTeam') }}</p>
        <router-link :to="localePath('/team-builder')" class="btn-go-build">{{ t('teamOverview.goToBuild') }}</router-link>
      </div>
    </template>

    <template v-else>
      <!-- ═══ 成員卡片 ═══ -->
      <section class="members-grid">
        <article v-for="m in filledMembers" :key="m.slotIdx" class="member-card">
          <!-- 頭部 -->
          <div class="mc-header">
            <div class="mc-avatar-wrap">
              <img :src="getPokemonImageUrl(m.pokemon)" class="mc-avatar" />
            </div>
            <div class="mc-info">
              <h3 class="mc-name">{{ pokemonDisplayName(m.pokemon) }}</h3>
              <div class="mc-types">
                <span v-for="tp in m.types" :key="tp" :class="typeBadgeClasses(tp)">{{ t('pokemon.types.' + tp) }}</span>
              </div>
              <div class="mc-nature">
                <span class="mc-nature-name">{{ natureLabelZh(m.nature) }}</span>
                <span class="mc-nature-fx">{{ natureEffect(m.nature) }}</span>
              </div>
              <div v-if="m.ability" class="mc-ability">
                <span class="material-symbols-rounded mc-ability-icon">auto_awesome</span>
                <span class="mc-ability-name">{{ localizedName(m.ability) }}</span>
              </div>
            </div>
            <div v-if="m.heldItem" class="mc-held-slot">
              <span class="material-symbols-rounded mc-held-icon">inventory_2</span>
              <span class="mc-held-name">{{ localizedName(m.heldItem) }}</span>
            </div>
            <div v-else class="mc-held-slot mc-held-empty">
              <span class="material-symbols-rounded mc-held-icon">block</span>
              <span class="mc-held-empty-text">{{ t('teamOverview.noItem') }}</span>
            </div>
          </div>

          <!-- 能力值迷你條 -->
          <div class="mc-stats">
            <div v-for="s in memberStats(m)" :key="s.key" class="mc-stat-row">
              <span class="mc-stat-label-wrap">
                <span class="mc-stat-label" :style="{ color: s.color }">{{ s.label }}</span>
                <span v-if="s.natureTrend === 'up'" class="nature-trend nature-trend-up" :title="t('pokemonDetail.natureUp')">
                  <span class="material-symbols-rounded">trending_up</span>
                </span>
                <span v-else-if="s.natureTrend === 'down'" class="nature-trend nature-trend-down" :title="t('pokemonDetail.natureDown')">
                  <span class="material-symbols-rounded">trending_down</span>
                </span>
              </span>
              <div class="mc-stat-bar-track">
                <div class="mc-stat-bar-fill" :style="{ width: s.barPct + '%', background: s.color }"></div>
              </div>
              <span class="mc-stat-val">{{ s.actual }}</span>
              <span v-if="s.evPts" class="mc-stat-ev">+{{ s.evPts }}</span>
            </div>
          </div>

          <!-- 招式 -->
          <div class="mc-moves">
            <div v-for="(mv, mi) in m.moves" :key="mi" class="mc-move-chip" :class="{ empty: !mv }">
              <template v-if="mv">
                <span :class="typeBadgeClasses(mv.type || mv.typeName)" class="mc-move-dot"></span>
                <span class="mc-move-name">{{ localizedName(mv) }}</span>
                <span class="mc-move-cat">{{ mv.category ? t('pokemon.categories.' + mv.category) : '' }}</span>
              </template>
              <template v-else>
                <span class="mc-move-empty">—</span>
              </template>
            </div>
          </div>
        </article>
      </section>

      <!-- ═══ 隊伍分析區 ═══ -->
      <section class="analysis-section">
        <h2 class="section-title">
          <span class="material-symbols-rounded">analytics</span>
          {{ t('teamOverview.analysis') }}
        </h2>

        <div class="analysis-grid">
          <!-- 防禦弱點 -->
          <div class="analysis-card">
            <h3 class="analysis-card-title">
              <span class="material-symbols-rounded">shield</span>
              {{ t('teamOverview.defenseWeakness') }}
            </h3>
            <div v-if="teamDefenseAnalysis.warnings.length" class="warn-list">
              <div v-for="w in teamDefenseAnalysis.warnings" :key="w.type" class="warn-row">
                <span :class="typeBadgeClasses(w.type)" class="ov-type-badge">{{ w.typeZh }}</span>
                <span class="warn-count">{{ t('teamOverview.weakCount', { count: w.weakCount }) }}</span>
              </div>
            </div>
            <div v-else class="analysis-ok">
              <span class="material-symbols-rounded">check_circle</span>
              {{ t('teamOverview.noMajorWeakness') }}
            </div>
          </div>

          <!-- 防禦無覆蓋 -->
          <div class="analysis-card">
            <h3 class="analysis-card-title">
              <span class="material-symbols-rounded">remove_moderator</span>
              {{ t('teamOverview.noResistCoverage') }}
            </h3>
            <div v-if="teamDefenseAnalysis.uncovered.length" class="uncovered-list">
              <span
                v-for="entry in teamDefenseAnalysis.uncovered"
                :key="entry.type"
                :class="typeBadgeClasses(entry.type)"
                class="ov-type-badge"
              >{{ entry.typeZh }}</span>
            </div>
            <div v-else class="analysis-ok">
              <span class="material-symbols-rounded">check_circle</span>
              {{ t('teamOverview.fullResistCoverage') }}
            </div>
          </div>

          <!-- 攻擊覆蓋 -->
          <div class="analysis-card">
            <h3 class="analysis-card-title">
              <span class="material-symbols-rounded">swords</span>
              {{ t('teamOverview.attackCoverage') }}
            </h3>
            <div class="coverage-grid">
              <span
                v-for="entry in teamTypesCoverage"
                :key="entry.type"
                :class="[...typeBadgeClasses(entry.type), { 'badge-dim': !entry.covered }]"
                class="ov-type-badge"
              >{{ entry.typeZh }}</span>
            </div>
            <p v-if="uncoveredAttackTypes.length" class="coverage-note">
              {{ t('teamOverview.missingAttack', { types: uncoveredAttackTypes.map(tp => tp.typeZh).join('、') }) }}
            </p>
            <div v-else class="analysis-ok" style="margin-top:8px">
              <span class="material-symbols-rounded">check_circle</span>
              {{ t('teamOverview.fullAttackCoverage') }}
            </div>
          </div>

          <!-- 完整相性表 -->
          <div class="analysis-card analysis-card-wide">
            <h3 class="analysis-card-title">
              <span class="material-symbols-rounded">grid_on</span>
              {{ t('teamOverview.defenseTable') }}
            </h3>
            <div class="defense-full-grid">
              <div
                v-for="entry in teamDefenseAnalysis.perType"
                :key="entry.type"
                class="dfg-cell"
                :class="{
                  'dfg-danger': entry.weakCount >= 2,
                  'dfg-good': entry.resistCount >= 2 || entry.immuneCount >= 1,
                }"
              >
                <span :class="typeBadgeClasses(entry.type)" class="dfg-badge">{{ entry.typeZh }}</span>
                <div class="dfg-counts">
                  <span v-if="entry.weakCount" class="dfg-c dfg-w">{{ entry.weakCount }}{{ t('teamOverview.weak') }}</span>
                  <span v-if="entry.resistCount" class="dfg-c dfg-r">{{ entry.resistCount }}{{ t('teamOverview.resist') }}</span>
                  <span v-if="entry.immuneCount" class="dfg-c dfg-i">{{ entry.immuneCount }}{{ t('teamOverview.immune') }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </template>

    <ShareTeamModal :visible="shareModalVisible" @close="shareModalVisible = false" />
  </div>
</template>

<style scoped>
/* ═══ 頁首 ═══ */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32px;
}

.page-header .page-title { margin-bottom: 0; }

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* ═══ 匯出下拉（Showdown / PNG）═══ */
.export-dropdown {
  position: relative;
}

.export-dropdown.is-disabled {
  opacity: 0.45;
  pointer-events: none;
}

.export-dropdown > summary {
  list-style: none;
  cursor: pointer;
}

.export-dropdown > summary::-webkit-details-marker {
  display: none;
}

.btn-export-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--accent-soft);
  border: 1px solid var(--accent-glow);
  border-radius: var(--radius-sm);
  color: var(--accent);
  font-size: 0.88rem;
  font-weight: 600;
  font-family: inherit;
  transition: all 0.2s;
  user-select: none;
}

.btn-export-summary:hover {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.btn-export-summary .material-symbols-rounded:first-of-type {
  font-size: 18px;
}

.export-dropdown-chevron {
  font-size: 20px;
  margin-left: 2px;
  transition: transform 0.2s;
}

.export-dropdown[open] .export-dropdown-chevron {
  transform: rotate(180deg);
}

.export-dropdown-panel {
  position: absolute;
  right: 0;
  top: calc(100% + 6px);
  min-width: 220px;
  padding: 6px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
  z-index: 50;
}

.export-dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-primary);
  font-size: 0.86rem;
  font-weight: 600;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s;
}

.export-dropdown-item:hover:not(:disabled) {
  background: var(--bg-glass);
}

.export-dropdown-item:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.export-dropdown-item .material-symbols-rounded {
  font-size: 20px;
  color: var(--accent);
}

.btn-share {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--accent-soft);
  border: 1px solid var(--accent-glow);
  border-radius: var(--radius-sm);
  color: var(--accent);
  font-size: 0.88rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-share:hover:not(:disabled) {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.btn-share:disabled { opacity: 0.45; cursor: not-allowed; }

.btn-share .material-symbols-rounded { font-size: 18px; }

.btn-back-edit {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  transition: all 0.2s;
}

.btn-back-edit:hover {
  background: var(--bg-elevated);
  color: var(--text-primary);
}

.btn-back-edit .material-symbols-rounded { font-size: 18px; }

/* ═══ 空狀態 ═══ */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 80px 20px;
  color: var(--text-muted);
}

.empty-icon { font-size: 56px; }

.btn-go-build {
  padding: 10px 24px;
  background: var(--accent);
  border-radius: var(--radius-sm);
  color: #fff;
  font-weight: 600;
  font-size: 0.9rem;
}

/* ═══ 成員卡片 ═══ */
.members-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 40px;
}

.member-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  transition: border-color 0.2s;
}

.member-card:hover {
  border-color: var(--border-light);
}

/* 卡片頭部 */
.mc-header {
  display: flex;
  align-items: center;
  gap: 14px;
}

.mc-avatar-wrap {
  flex-shrink: 0;
}

.mc-avatar {
  display: block;
  width: 64px;
  height: 64px;
  object-fit: contain;
  image-rendering: pixelated;
  background: var(--bg-glass);
  border-radius: var(--radius-sm);
  padding: 4px;
}

.mc-info { flex: 1; min-width: 0; }

.mc-name {
  font-size: 1.1rem;
  font-weight: 700;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mc-types { display: flex; gap: 5px; margin-bottom: 4px; }

.mc-nature {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.76rem;
}

.mc-nature-name {
  color: var(--accent);
  font-weight: 600;
}

.mc-nature-fx { color: var(--text-muted); }

.mc-ability {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
}

.mc-ability-icon {
  font-size: 14px;
  color: var(--accent);
}

.mc-ability-name {
  font-size: 0.74rem;
  font-weight: 500;
  color: var(--text-secondary);
}

/* 頭部右側：持有物（原總計位置） */
.mc-held-slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 12px;
  background: var(--bg-glass);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  max-width: 130px;
  min-width: 72px;
}

.mc-held-icon {
  font-size: 18px;
  color: var(--text-muted);
  line-height: 1;
}

.mc-held-name {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.25;
  text-align: center;
  word-break: break-word;
}

.mc-held-empty .mc-held-empty-text {
  font-size: 0.68rem;
  color: var(--text-muted);
  text-align: center;
  line-height: 1.2;
}

/* 迷你能力值條 */
.mc-stats { display: flex; flex-direction: column; gap: 4px; }

.mc-stat-row {
  display: grid;
  grid-template-columns: minmax(48px, auto) 1fr 32px 28px;
  align-items: center;
  gap: 6px;
}

.mc-stat-label-wrap {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.mc-stat-label { font-size: 0.68rem; font-weight: 600; }

.nature-trend {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nature-trend .material-symbols-rounded {
  font-size: 14px;
  font-variation-settings: 'FILL' 1;
}

.nature-trend-up { color: #4ade80; }

.nature-trend-down { color: #f87171; }

.mc-stat-bar-track {
  height: 6px;
  background: rgba(255,255,255,0.04);
  border-radius: 3px;
  overflow: hidden;
}

.mc-stat-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}

.mc-stat-val { font-size: 0.72rem; font-weight: 700; color: var(--text-primary); text-align: right; }

.mc-stat-ev {
  font-size: 0.62rem;
  color: #38bdf8;
  font-weight: 600;
}

/* 招式 */
.mc-moves {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.mc-move-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--bg-glass);
  border-radius: var(--radius-xs);
  font-size: 0.76rem;
}

.mc-move-chip.empty {
  justify-content: center;
  color: var(--text-muted);
}

.mc-move-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  padding: 0 !important;
  font-size: 0 !important;
}

.mc-move-name {
  flex: 1;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mc-move-cat { font-size: 0.64rem; color: var(--text-muted); }
.mc-move-empty { color: var(--text-muted); }

/* ═══ 分析區 ═══ */
.analysis-section { margin-bottom: 24px; }

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.3rem;
  font-weight: 800;
  margin-bottom: 20px;
}

.section-title .material-symbols-rounded { font-size: 24px; color: var(--accent); }

.analysis-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.analysis-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 18px;
}

.analysis-card-wide {
  grid-column: 1 / -1;
}

.analysis-card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.9rem;
  font-weight: 700;
  margin-bottom: 12px;
}

.analysis-card-title .material-symbols-rounded { font-size: 18px; color: var(--accent); }

.analysis-ok {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.82rem;
  color: #8ce68c;
}

.analysis-ok .material-symbols-rounded { font-size: 18px; }

/* 弱點列表 */
.warn-list { display: flex; flex-direction: column; gap: 6px; }

.warn-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: rgba(255, 60, 60, 0.06);
  border-radius: var(--radius-xs);
}

.warn-count { font-size: 0.78rem; color: #ff8888; font-weight: 600; }

.ov-type-badge {
  font-size: 0.62rem !important;
  padding: 2px 8px !important;
}

/* 無覆蓋 */
.uncovered-list {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

/* 攻擊覆蓋 */
.coverage-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.badge-dim { opacity: 0.2; }

.coverage-note {
  margin-top: 10px;
  font-size: 0.78rem;
  color: var(--text-muted);
}

/* 完整防禦表 */
.defense-full-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 6px;
}

.dfg-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 8px 4px;
  border-radius: var(--radius-xs);
  background: var(--bg-glass);
  border: 1px solid transparent;
}

.dfg-cell.dfg-danger {
  background: rgba(255, 60, 60, 0.1);
  border-color: rgba(255, 60, 60, 0.25);
}

.dfg-cell.dfg-good {
  background: rgba(98, 188, 90, 0.06);
  border-color: rgba(98, 188, 90, 0.15);
}

.dfg-badge {
  font-size: 0.56rem !important;
  padding: 1px 7px !important;
}

.dfg-counts { display: flex; gap: 3px; }

.dfg-c {
  font-size: 0.6rem;
  font-weight: 700;
  padding: 1px 4px;
  border-radius: 3px;
}

.dfg-w { background: rgba(255,60,60,0.15); color: #ff8888; }
.dfg-r { background: rgba(74,144,217,0.15); color: #88bbff; }
.dfg-i { background: rgba(180,180,180,0.12); color: #aaa; }

/* ═══ 響應式 ═══ */
@media (max-width: 1024px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }

  .analysis-card-wide {
    grid-column: auto;
  }

  .members-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .members-grid {
    grid-template-columns: 1fr;
  }

  .defense-full-grid {
    grid-template-columns: repeat(3, 1fr);
  }

  .mc-moves {
    grid-template-columns: 1fr;
  }
}
</style>
