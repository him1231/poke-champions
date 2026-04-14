<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { pokemonRosterApi } from '../api/pokemonRoster'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { localizedName } from '../utils/localizedName'
import { typeBadgeClasses } from '../utils/pokemonTypesDisplay'
import { calcSpeeds, calcDoubled, tierIndexByBase } from '../utils/speedTiersCalc'

const { t } = useI18n()

const loading = ref(true)
const pokemonList = ref([])

const TIER_KEYS = [
  't120',
  't110_119',
  't100_109',
  't90_99',
  't80_89',
  't70_79',
  't60_69',
  't42_59',
  't0_41',
]

/**
 * 天氣／場地速度×2 代表列（first match in roster wins）。
 * applyDouble: false = 不適用×2（名冊若有收錄才顯示；×2 欄為「—」）
 */
const WEATHER_SPEED_ROWS = [
  { candidates: ['barraskewda'], applyDouble: true },
  /** 撥沙（沙暴下速度×2）— 白晝形態 */
  { candidates: ['lycanroc-midday'], applyDouble: true },
  /** 輕裝 Unburden：道具消耗後速度×2，數值與天氣類×2對照用 */
  { candidates: ['hawlucha'], applyDouble: true },
  /** 同上：輕裝 */
  { candidates: ['sneasler', 'sneasler-hisui'], applyDouble: true },
  { candidates: ['swampert-mega', 'mega-swampert', 'swampert'], applyDouble: true },
  { candidates: ['kingdra'], applyDouble: true },
  { candidates: ['excadrill'], applyDouble: true },
  { candidates: ['ludicolo'], applyDouble: true },
  { candidates: ['kabutops'], applyDouble: true },
  { candidates: ['beartic'], applyDouble: true },
  { candidates: ['floatzel'], applyDouble: true },
  { candidates: ['poliwrath'], applyDouble: true },
  { candidates: ['qwilfish'], applyDouble: true },
  { candidates: ['relicanth'], applyDouble: true },
  { candidates: ['golduck'], applyDouble: true },
  { candidates: ['seismitoad'], applyDouble: true },
  { candidates: ['jellicent'], applyDouble: true },
  { candidates: ['toxicroak'], applyDouble: true },
  { candidates: ['venusaur-mega', 'mega-venusaur', 'venusaur'], applyDouble: true },
  { candidates: ['leafeon'], applyDouble: true },
  { candidates: ['victreebel'], applyDouble: true },
  { candidates: ['shiftry'], applyDouble: true },
  { candidates: ['exeggutor-alola', 'exeggutor'], applyDouble: true },
  { candidates: ['lilligant'], applyDouble: true },
  { candidates: ['ninetales'], applyDouble: true },
  { candidates: ['whimsicott'], applyDouble: true },
  { candidates: ['haxorus'], applyDouble: true },
  { candidates: ['stoutland'], applyDouble: true },
]

function findPokemon(candidates) {
  const list = pokemonList.value
  for (const c of candidates) {
    const exact = list.find((p) => p.apiName === c)
    if (exact) return exact
  }
  for (const c of candidates) {
    const partial = list.find((p) => p.apiName?.includes(c))
    if (partial) return partial
  }
  return null
}

const weatherRows = computed(() => {
  const rows = []
  for (const { candidates, applyDouble } of WEATHER_SPEED_ROWS) {
    const p = findPokemon(candidates)
    if (!p || !p.statsSynced) continue
    const base = p.speed
    const { noEv, maxSpd, extreme } = calcSpeeds(base)
    const { maxDoubled, extremeDoubled } = calcDoubled(base)
    /** 列排序用：有×2 以「急速×2」為準，否則以單線「急速」為準（如雷吉艾勒奇） */
    const sortKey = applyDouble ? extremeDoubled : extreme
    rows.push({
      pokemon: p,
      base,
      noEv,
      maxSpd,
      extreme,
      applyDouble,
      maxDoubled,
      extremeDoubled,
      sortKey,
    })
  }
  rows.sort((a, b) => b.sortKey - a.sortKey || b.base - a.base)
  return rows
})

const tierGroups = computed(() => {
  const list = pokemonList.value.filter((p) => p.statsSynced && p.speed > 0)
  const buckets = TIER_KEYS.map(() => [])
  for (const p of list) {
    const idx = tierIndexByBase(p.speed)
    buckets[idx].push({ ...p, ...calcSpeeds(p.speed) })
  }
  for (const b of buckets) {
    b.sort((a, b) => b.base - a.base || b.extreme - a.extreme)
  }
  return TIER_KEYS.map((key, i) => ({
    key,
    label: t(`speedTiers.tiers.${key}`),
    pokemon: buckets[i],
  }))
})

function pokeLabel(p) {
  return localizedName(p) || p.displayName || p.apiName
}

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await pokemonRosterApi.getPokemonList()
    pokemonList.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="container speed-tiers-page">
    <header class="page-header">
      <h1 class="page-title">
        <span class="material-symbols-rounded title-icon">speed</span>
        {{ t('speedTiers.title') }}
      </h1>
      <p class="page-desc">{{ t('speedTiers.subtitle') }}</p>
    </header>

    <section class="formula-card">
      <h2>{{ t('speedTiers.formulaTitle') }}</h2>
      <ul class="formula-list">
        <li><code>{{ t('speedTiers.formulaNoEv') }}</code></li>
        <li><code>{{ t('speedTiers.formulaMax') }}</code></li>
        <li><code>{{ t('speedTiers.formulaExtreme') }}</code></li>
      </ul>
      <p class="formula-note">{{ t('speedTiers.formulaNote') }}</p>
    </section>

    <div v-if="loading" class="loading-state">
      <span class="material-symbols-rounded spin">progress_activity</span>
      <span>{{ t('common.loading') }}</span>
    </div>

    <template v-else>
      <section v-if="weatherRows.length" class="ability-section">
        <h2>{{ t('speedTiers.abilitySectionTitle') }}</h2>
        <p class="section-desc">{{ t('speedTiers.abilitySectionDesc') }}</p>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>{{ t('speedTiers.colPokemon') }}</th>
                <th>{{ t('speedTiers.colBase') }}</th>
                <th>{{ t('speedTiers.colNoEv') }}</th>
                <th>{{ t('speedTiers.colMax') }}</th>
                <th>{{ t('speedTiers.colExtreme') }}</th>
                <th>{{ t('speedTiers.colMaxDouble') }}</th>
                <th>{{ t('speedTiers.colExtremeDouble') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in weatherRows" :key="idx">
                <td class="col-poke">
                  <img :src="getPokemonImageUrl(row.pokemon)" class="poke-mini" alt="" />
                  <div class="poke-info">
                    <span class="poke-name">{{ pokeLabel(row.pokemon) }}</span>
                    <span class="poke-types">
                      <span
                        v-for="tn in (row.pokemon.types || [])"
                        :key="tn"
                        class="type-chip"
                        :class="typeBadgeClasses(tn)"
                      >{{ tn }}</span>
                    </span>
                  </div>
                </td>
                <td>{{ row.base }}</td>
                <td>{{ row.noEv }}</td>
                <td>{{ row.maxSpd }}</td>
                <td class="highlight">{{ row.extreme }}</td>
                <td>{{ row.applyDouble ? row.maxDoubled : '—' }}</td>
                <td class="highlight accent">{{ row.applyDouble ? row.extremeDoubled : '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section
        v-for="group in tierGroups"
        :key="group.key"
        v-show="group.pokemon.length"
        class="tier-section"
      >
        <h2 class="tier-heading">{{ group.label }}</h2>
        <div class="tier-grid">
          <div v-for="p in group.pokemon" :key="p.apiName + p.formId" class="tier-card">
            <img :src="getPokemonImageUrl(p)" class="tier-sprite" alt="" />
            <div class="tier-card-body">
              <div class="tier-name">{{ pokeLabel(p) }}</div>
              <div class="tier-stats">
                <span>{{ t('speedTiers.colBase') }} {{ p.base }}</span>
                <span>{{ t('speedTiers.shortNoEv') }} {{ p.noEv }}</span>
                <span>{{ t('speedTiers.shortMax') }} {{ p.maxSpd }}</span>
                <span class="tier-extreme">{{ t('speedTiers.shortExtreme') }} {{ p.extreme }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.speed-tiers-page {
  padding-bottom: 48px;
}

.page-header {
  margin-bottom: 28px;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 1.65rem;
  margin-bottom: 8px;
}

.title-icon {
  font-size: 1.75rem;
  color: var(--accent);
}

.page-desc {
  color: var(--text-muted);
  font-size: 0.92rem;
  max-width: 720px;
  line-height: 1.55;
}

.formula-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 22px 24px;
  margin-bottom: 32px;
}

.formula-card h2 {
  font-size: 1.05rem;
  margin-bottom: 12px;
  color: var(--text-primary);
}

.formula-list {
  margin: 0 0 12px;
  padding-left: 1.2rem;
  color: var(--text-secondary);
  line-height: 1.75;
}

.formula-list code {
  font-size: 0.88rem;
  background: var(--bg-glass);
  padding: 2px 8px;
  border-radius: 6px;
  color: var(--accent);
}

.formula-note {
  font-size: 0.82rem;
  color: var(--text-muted);
  margin: 0;
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 48px;
  justify-content: center;
  color: var(--text-muted);
}

.ability-section {
  margin-bottom: 40px;
}

.ability-section h2 {
  font-size: 1.15rem;
  margin-bottom: 8px;
}

.section-desc {
  font-size: 0.86rem;
  color: var(--text-muted);
  margin-bottom: 16px;
  max-width: 900px;
  line-height: 1.5;
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.82rem;
}

.data-table th,
.data-table td {
  padding: 10px 12px;
  text-align: left;
  border-bottom: 1px solid var(--border);
}

.data-table th {
  background: var(--bg-glass);
  color: var(--text-secondary);
  font-weight: 600;
  white-space: nowrap;
}

.data-table td.highlight {
  font-weight: 700;
  color: var(--text-primary);
}

.data-table td.highlight.accent {
  color: var(--accent);
}

.col-poke {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 180px;
}

.poke-mini {
  width: 40px;
  height: 40px;
  image-rendering: pixelated;
  flex-shrink: 0;
}

.poke-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.poke-name {
  font-weight: 600;
}

.poke-types {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.type-chip {
  font-size: 0.65rem;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: capitalize;
}

.tier-section {
  margin-bottom: 36px;
}

.tier-heading {
  font-size: 1.05rem;
  margin-bottom: 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
  color: var(--accent);
}

.tier-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.tier-card {
  display: flex;
  gap: 12px;
  align-items: center;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}

.tier-sprite {
  width: 48px;
  height: 48px;
  image-rendering: pixelated;
  flex-shrink: 0;
}

.tier-card-body {
  min-width: 0;
}

.tier-name {
  font-weight: 600;
  font-size: 0.9rem;
  margin-bottom: 6px;
}

.tier-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 0.75rem;
  color: var(--text-muted);
}

.tier-extreme {
  color: var(--accent);
  font-weight: 600;
}

@media (max-width: 768px) {
  .data-table {
    font-size: 0.75rem;
  }
  .data-table th,
  .data-table td {
    padding: 8px 6px;
  }
}
</style>
