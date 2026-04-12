<script setup>
import { computed, ref, nextTick } from 'vue'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { typeBadgeClasses } from '../utils/pokemonTypesDisplay'
import { NATURE_DEFS } from '../constants/pokemonNatures'
import {
  useTeamStore, ALL_TYPES, TYPE_ZH,
  STAT_KEYS, STAT_LABELS, STAT_COLORS, CATEGORY_LABELS,
  getDefenseMultipliers, calcStat,
} from '../composables/useTeamStore'

const { teamMembers, pokemonDisplayName } = useTeamStore()

const filledMembers = computed(() =>
  teamMembers
    .map((m, idx) => ({ ...m, slotIdx: idx }))
    .filter(m => m.pokemon)
)

const teamCount = computed(() => filledMembers.value.length)

function natureLabelZh(id) {
  return NATURE_DEFS[id]?.labelZh ?? id
}

function natureEffect(id) {
  const def = NATURE_DEFS[id]
  if (!def?.mult || Object.keys(def.mult).length === 0) return '無加減'
  const parts = []
  for (const [k, v] of Object.entries(def.mult)) {
    if (v > 1) parts.push(`${STAT_LABELS[k]}↑`)
    else if (v < 1) parts.push(`${STAT_LABELS[k]}↓`)
  }
  return parts.join(' ')
}

function memberStats(m) {
  if (!m.pokemon) return []
  return STAT_KEYS.map(key => {
    const base = Number(m.pokemon[key] ?? 0)
    const actual = calcStat(m.pokemon, key, m.statPoints, m.nature)
    const maxStat = 260
    return {
      key, label: STAT_LABELS[key], base, actual,
      barPct: Math.min((actual / maxStat) * 100, 100),
      color: STAT_COLORS[key],
      evPts: m.statPoints[key] || 0,
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
    return { type: atkType, typeZh: TYPE_ZH[atkType], weakCount, resistCount, immuneCount }
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
  return ALL_TYPES.map(t => ({
    type: t,
    typeZh: TYPE_ZH[t],
    covered: attackTypes.has(t),
  }))
})

const uncoveredAttackTypes = computed(() =>
  teamTypesCoverage.value.filter(t => !t.covered)
)

const overviewRoot = ref(null)
const downloading = ref(false)

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
      /** 略過操作列；略過頭像（跨域圖無法可靠畫進 canvas，匯出改為不顯示圖片） */
      ignoreElements: el =>
        !!el.closest?.('.overview-export-ignore') || el.classList?.contains('mc-avatar'),
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
  }
}
</script>

<template>
  <div ref="overviewRoot" class="container team-overview">
    <!-- 頁首 -->
    <div class="page-header">
      <h1 class="page-title">
        <span class="material-symbols-rounded" style="font-size:1.6rem;vertical-align:middle;margin-right:8px">summarize</span>
        隊伍總覽
      </h1>
      <div class="page-header-actions overview-export-ignore">
        <button
          type="button"
          class="btn-download-overview"
          :disabled="teamCount === 0 || downloading"
          @click="downloadOverviewPng"
        >
          <span class="material-symbols-rounded">download</span>
          {{ downloading ? '輸出中…' : '下載圖片' }}
        </button>
        <router-link to="/team-builder" class="btn-back-edit">
          <span class="material-symbols-rounded">edit</span>
          編輯隊伍
        </router-link>
      </div>
    </div>

    <template v-if="teamCount === 0">
      <div class="empty-state">
        <span class="material-symbols-rounded empty-icon">group_off</span>
        <p>尚未建立隊伍</p>
        <router-link to="/team-builder" class="btn-go-build">前往組隊</router-link>
      </div>
    </template>

    <template v-else>
      <!-- ═══ 成員卡片 ═══ -->
      <section class="members-grid">
        <article v-for="m in filledMembers" :key="m.slotIdx" class="member-card">
          <!-- 頭部 -->
          <div class="mc-header">
            <img :src="getPokemonImageUrl(m.pokemon)" class="mc-avatar" />
            <div class="mc-info">
              <h3 class="mc-name">{{ pokemonDisplayName(m.pokemon) }}</h3>
              <div class="mc-types">
                <span v-for="t in m.types" :key="t" :class="typeBadgeClasses(t)">{{ TYPE_ZH[t] || t }}</span>
              </div>
              <div class="mc-nature">
                <span class="mc-nature-name">{{ natureLabelZh(m.nature) }}</span>
                <span class="mc-nature-fx">{{ natureEffect(m.nature) }}</span>
              </div>
            </div>
            <div v-if="m.heldItem" class="mc-held-slot">
              <span class="material-symbols-rounded mc-held-icon">inventory_2</span>
              <span class="mc-held-name">{{ m.heldItem.chineseName || m.heldItem.displayName }}</span>
            </div>
            <div v-else class="mc-held-slot mc-held-empty">
              <span class="material-symbols-rounded mc-held-icon">block</span>
              <span class="mc-held-empty-text">無持有物</span>
            </div>
          </div>

          <!-- 能力值迷你條 -->
          <div class="mc-stats">
            <div v-for="s in memberStats(m)" :key="s.key" class="mc-stat-row">
              <span class="mc-stat-label">{{ s.label }}</span>
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
                <span class="mc-move-name">{{ mv.chineseName || mv.displayName }}</span>
                <span class="mc-move-cat">{{ CATEGORY_LABELS[mv.category] || '' }}</span>
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
          隊伍分析
        </h2>

        <div class="analysis-grid">
          <!-- 防禦弱點 -->
          <div class="analysis-card">
            <h3 class="analysis-card-title">
              <span class="material-symbols-rounded">shield</span>
              防禦弱點
            </h3>
            <div v-if="teamDefenseAnalysis.warnings.length" class="warn-list">
              <div v-for="w in teamDefenseAnalysis.warnings" :key="w.type" class="warn-row">
                <span :class="typeBadgeClasses(w.type)" class="ov-type-badge">{{ w.typeZh }}</span>
                <span class="warn-count">{{ w.weakCount }} 位弱點</span>
              </div>
            </div>
            <div v-else class="analysis-ok">
              <span class="material-symbols-rounded">check_circle</span>
              無重大共同弱點
            </div>
          </div>

          <!-- 防禦無覆蓋 -->
          <div class="analysis-card">
            <h3 class="analysis-card-title">
              <span class="material-symbols-rounded">remove_moderator</span>
              無抗性覆蓋
            </h3>
            <div v-if="teamDefenseAnalysis.uncovered.length" class="uncovered-list">
              <span
                v-for="t in teamDefenseAnalysis.uncovered"
                :key="t.type"
                :class="typeBadgeClasses(t.type)"
                class="ov-type-badge"
              >{{ t.typeZh }}</span>
            </div>
            <div v-else class="analysis-ok">
              <span class="material-symbols-rounded">check_circle</span>
              每個屬性都有成員可抵抗
            </div>
          </div>

          <!-- 攻擊覆蓋 -->
          <div class="analysis-card">
            <h3 class="analysis-card-title">
              <span class="material-symbols-rounded">swords</span>
              攻擊覆蓋
            </h3>
            <div class="coverage-grid">
              <span
                v-for="t in teamTypesCoverage"
                :key="t.type"
                :class="[...typeBadgeClasses(t.type), { 'badge-dim': !t.covered }]"
                class="ov-type-badge"
              >{{ t.typeZh }}</span>
            </div>
            <p v-if="uncoveredAttackTypes.length" class="coverage-note">
              缺少 {{ uncoveredAttackTypes.map(t => t.typeZh).join('、') }} 屬性攻擊
            </p>
            <div v-else class="analysis-ok" style="margin-top:8px">
              <span class="material-symbols-rounded">check_circle</span>
              18 種屬性攻擊全覆蓋
            </div>
          </div>

          <!-- 完整相性表 -->
          <div class="analysis-card analysis-card-wide">
            <h3 class="analysis-card-title">
              <span class="material-symbols-rounded">grid_on</span>
              防禦相性總表
            </h3>
            <div class="defense-full-grid">
              <div
                v-for="t in teamDefenseAnalysis.perType"
                :key="t.type"
                class="dfg-cell"
                :class="{
                  'dfg-danger': t.weakCount >= 2,
                  'dfg-good': t.resistCount >= 2 || t.immuneCount >= 1,
                }"
              >
                <span :class="typeBadgeClasses(t.type)" class="dfg-badge">{{ t.typeZh }}</span>
                <div class="dfg-counts">
                  <span v-if="t.weakCount" class="dfg-c dfg-w">{{ t.weakCount }}弱</span>
                  <span v-if="t.resistCount" class="dfg-c dfg-r">{{ t.resistCount }}抗</span>
                  <span v-if="t.immuneCount" class="dfg-c dfg-i">{{ t.immuneCount }}免</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </template>
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

.btn-download-overview {
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
  cursor: pointer;
  font-family: inherit;
  transition: all 0.2s;
}

.btn-download-overview:hover:not(:disabled) {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.btn-download-overview:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn-download-overview .material-symbols-rounded { font-size: 18px; }

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
  grid-template-columns: repeat(2, 1fr);
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

.mc-avatar {
  width: 64px;
  height: 64px;
  object-fit: contain;
  image-rendering: pixelated;
  background: var(--bg-glass);
  border-radius: var(--radius-sm);
  padding: 4px;
  flex-shrink: 0;
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
  grid-template-columns: 30px 1fr 32px 28px;
  align-items: center;
  gap: 6px;
}

.mc-stat-label { font-size: 0.68rem; font-weight: 600; color: var(--text-muted); }

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
