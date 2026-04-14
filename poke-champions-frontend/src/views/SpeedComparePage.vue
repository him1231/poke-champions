<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { pokemonRosterApi } from '../api/pokemonRoster'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { localizedName } from '../utils/localizedName'
import { typeBadgeClasses } from '../utils/pokemonTypesDisplay'
import { calcSpeeds } from '../utils/speedTiersCalc'
import { comparePokemonByFormId } from '../utils/pokemonSort'

const { t } = useI18n()

const loading = ref(true)
const allPokemon = ref([])
const allySlots = ref(Array.from({ length: 6 }, () => ({ pokemon: null })))
const foeSlots = ref(Array.from({ length: 6 }, () => ({ pokemon: null })))

const pickerOpen = ref(false)
const pickerSide = ref('ally')
const pickerIndex = ref(0)
const search = ref('')
const sortMetric = ref('extreme')

/** 含超級進化形態；依圖鑑／形態排序 */
const rosterSorted = computed(() =>
  [...allPokemon.value].sort(comparePokemonByFormId),
)

const filteredPokemon = computed(() => {
  const q = search.value.toLowerCase().trim()
  const list = rosterSorted.value
  if (!q) return list
  return list.filter(
    (p) =>
      (p.displayName || '').toLowerCase().includes(q) ||
      (p.chineseName || '').toLowerCase().includes(q) ||
      (p.japaneseName || '').toLowerCase().includes(q) ||
      (p.apiName || '').toLowerCase().includes(q),
  )
})

function openPicker(side, index) {
  pickerSide.value = side
  pickerIndex.value = index
  pickerOpen.value = true
  search.value = ''
}

function closePicker() {
  pickerOpen.value = false
  search.value = ''
}

function selectPokemon(p) {
  const slots = pickerSide.value === 'ally' ? allySlots.value : foeSlots.value
  slots[pickerIndex.value].pokemon = p
  closePicker()
}

function clearSlot(side, index) {
  const slots = side === 'ally' ? allySlots.value : foeSlots.value
  slots[index].pokemon = null
}

function pokeLabel(p) {
  return localizedName(p) || p.displayName || p.apiName
}

const comparisonRows = computed(() => {
  const rows = []
  const push = (side, slots) => {
    slots.forEach((slot, slotIndex) => {
      const p = slot.pokemon
      if (!p || !p.statsSynced || !(Number(p.speed) > 0)) return
      const speeds = calcSpeeds(p.speed)
      rows.push({
        key: `${side}-${slotIndex}-${p.apiName}`,
        side,
        slotIndex: slotIndex + 1,
        pokemon: p,
        ...speeds,
      })
    })
  }
  push('ally', allySlots.value)
  push('foe', foeSlots.value)
  const metric = sortMetric.value
  rows.sort((a, b) => (b[metric] || 0) - (a[metric] || 0) || b.base - a.base)
  return rows
})

const maxForBars = computed(() => {
  const rows = comparisonRows.value
  if (!rows.length) return 1
  return Math.max(...rows.map((r) => r.extreme), 1)
})

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await pokemonRosterApi.getPokemonList()
    allPokemon.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="container speed-compare-page">
    <header class="page-header">
      <h1 class="page-title">
        <span class="material-symbols-rounded title-icon">compare_arrows</span>
        {{ t('speedCompare.title') }}
      </h1>
      <p class="page-desc">{{ t('speedCompare.subtitle') }}</p>
      <p class="page-note">{{ t('speedCompare.formulaNote') }}</p>
    </header>

    <div v-if="loading" class="loading-state">
      <span class="material-symbols-rounded spin">progress_activity</span>
      <span>{{ t('common.loading') }}</span>
    </div>

    <template v-else>
      <section class="slots-section">
        <div class="slots-column">
          <h2 class="slots-heading ally">{{ t('speedCompare.allyTitle') }}</h2>
          <div class="slot-grid">
            <div
              v-for="(slot, idx) in allySlots"
              :key="'a-' + idx"
              role="button"
              tabindex="0"
              class="slot-tile"
              :class="{ filled: slot.pokemon }"
              @click="openPicker('ally', idx)"
              @keydown.enter.prevent="openPicker('ally', idx)"
            >
              <template v-if="slot.pokemon">
                <img :src="getPokemonImageUrl(slot.pokemon)" class="slot-img" alt="" />
                <span class="slot-name">{{ pokeLabel(slot.pokemon) }}</span>
                <span class="slot-spd">{{ slot.pokemon.speed }}</span>
                <button type="button" class="slot-clear" :aria-label="t('common.remove')" @click.stop="clearSlot('ally', idx)">×</button>
              </template>
              <template v-else>
                <span class="material-symbols-rounded slot-plus">add</span>
                <span class="slot-placeholder">{{ t('speedCompare.slotEmpty', { n: idx + 1 }) }}</span>
              </template>
            </div>
          </div>
        </div>
        <div class="slots-column">
          <h2 class="slots-heading foe">{{ t('speedCompare.foeTitle') }}</h2>
          <div class="slot-grid">
            <div
              v-for="(slot, idx) in foeSlots"
              :key="'f-' + idx"
              role="button"
              tabindex="0"
              class="slot-tile"
              :class="{ filled: slot.pokemon }"
              @click="openPicker('foe', idx)"
              @keydown.enter.prevent="openPicker('foe', idx)"
            >
              <template v-if="slot.pokemon">
                <img :src="getPokemonImageUrl(slot.pokemon)" class="slot-img" alt="" />
                <span class="slot-name">{{ pokeLabel(slot.pokemon) }}</span>
                <span class="slot-spd">{{ slot.pokemon.speed }}</span>
                <button type="button" class="slot-clear" :aria-label="t('common.remove')" @click.stop="clearSlot('foe', idx)">×</button>
              </template>
              <template v-else>
                <span class="material-symbols-rounded slot-plus">add</span>
                <span class="slot-placeholder">{{ t('speedCompare.slotEmpty', { n: idx + 1 }) }}</span>
              </template>
            </div>
          </div>
        </div>
      </section>

      <section v-if="comparisonRows.length" class="result-section">
        <div class="result-toolbar">
          <h2>{{ t('speedCompare.resultTitle') }}</h2>
          <label class="sort-label">
            <span>{{ t('speedCompare.sortBy') }}</span>
            <select v-model="sortMetric" class="sort-select">
              <option value="extreme">{{ t('speedCompare.sortExtreme') }}</option>
              <option value="maxSpd">{{ t('speedCompare.sortMax') }}</option>
              <option value="noEv">{{ t('speedCompare.sortNoEv') }}</option>
              <option value="base">{{ t('speedCompare.sortBase') }}</option>
            </select>
          </label>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>{{ t('speedCompare.colSide') }}</th>
                <th>{{ t('speedCompare.colPokemon') }}</th>
                <th>{{ t('speedCompare.colBase') }}</th>
                <th>{{ t('speedCompare.colNoEv') }}</th>
                <th>{{ t('speedCompare.colMax') }}</th>
                <th>{{ t('speedCompare.colExtreme') }}</th>
                <th>{{ t('speedCompare.colBar') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in comparisonRows" :key="row.key">
                <td>
                  <span class="side-tag" :class="row.side">{{ row.side === 'ally' ? t('speedCompare.sideAlly') : t('speedCompare.sideFoe') }}</span>
                  <span class="slot-num">#{{ row.slotIndex }}</span>
                </td>
                <td class="col-poke">
                  <img :src="getPokemonImageUrl(row.pokemon)" class="poke-mini" alt="" />
                  <span>{{ pokeLabel(row.pokemon) }}</span>
                </td>
                <td>{{ row.base }}</td>
                <td>{{ row.noEv }}</td>
                <td>{{ row.maxSpd }}</td>
                <td class="highlight">{{ row.extreme }}</td>
                <td class="bar-cell">
                  <div class="bar-track">
                    <div
                      class="bar-fill"
                      :class="row.side"
                      :style="{ width: `${(row.extreme / maxForBars) * 100}%` }"
                    />
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <p v-else class="empty-hint">{{ t('speedCompare.emptyHint') }}</p>
    </template>

    <Teleport to="body">
      <div v-if="pickerOpen" class="picker-overlay" @click.self="closePicker">
        <div class="picker-modal">
          <div class="picker-header">
            <h3>{{ t('speedCompare.pickerTitle') }}</h3>
            <button type="button" class="picker-close" :aria-label="t('common.close')" @click="closePicker">
              <span class="material-symbols-rounded">close</span>
            </button>
          </div>
          <input v-model="search" type="search" class="picker-search" :placeholder="t('common.search')" />
          <div class="picker-list">
            <button
              v-for="p in filteredPokemon"
              :key="p.apiName + (p.formId ?? '')"
              type="button"
              class="picker-row"
              @click="selectPokemon(p)"
            >
              <img :src="getPokemonImageUrl(p)" class="picker-sprite" alt="" />
              <div class="picker-row-text">
                <span class="picker-name">{{ pokeLabel(p) }}</span>
                <span class="picker-meta">
                  {{ t('speedCompare.colBase') }} {{ p.speed }}
                  <span v-if="p.mega || p.isMega" class="mega-tag">Mega</span>
                </span>
              </div>
              <span
                v-for="tn in (p.types || [])"
                :key="tn"
                class="type-chip"
                :class="typeBadgeClasses(tn)"
              >{{ tn }}</span>
            </button>
            <p v-if="!filteredPokemon.length" class="picker-empty">{{ t('common.noResults') }}</p>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.speed-compare-page {
  padding-bottom: 48px;
}

.page-header {
  margin-bottom: 24px;
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
  margin: 0 0 8px;
}

.page-note {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin: 0;
  max-width: 720px;
  line-height: 1.45;
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 48px;
  justify-content: center;
  color: var(--text-muted);
}

.slots-section {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 32px;
}

@media (max-width: 768px) {
  .slots-section {
    grid-template-columns: 1fr;
  }
}

.slots-heading {
  font-size: 1rem;
  margin: 0 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--border);
}

.slots-heading.ally {
  color: #4a90d9;
}

.slots-heading.foe {
  color: #e55353;
}

.slot-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

@media (max-width: 520px) {
  .slot-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.slot-tile {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 100px;
  padding: 10px 8px;
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  cursor: pointer;
  font-family: inherit;
  color: var(--text-secondary);
  transition: border-color 0.15s, background 0.15s;
}

.slot-tile:hover {
  border-color: var(--accent);
  background: var(--bg-glass);
}

.slot-tile.filled {
  border-style: solid;
  border-color: var(--border);
}

.slot-img {
  width: 48px;
  height: 48px;
  image-rendering: pixelated;
}

.slot-name {
  font-size: 0.72rem;
  font-weight: 600;
  text-align: center;
  line-height: 1.25;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.slot-spd {
  font-size: 0.7rem;
  color: var(--text-muted);
}

.slot-plus {
  font-size: 28px;
  opacity: 0.5;
}

.slot-placeholder {
  font-size: 0.72rem;
}

.slot-clear {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

.slot-clear:hover {
  background: rgba(229, 57, 53, 0.85);
}

.result-section {
  margin-top: 8px;
}

.result-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.result-toolbar h2 {
  margin: 0;
  font-size: 1.1rem;
}

.sort-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.sort-select {
  padding: 6px 10px;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: var(--bg-glass);
  color: var(--text-primary);
  font-size: 0.82rem;
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
  color: var(--accent);
}

.col-poke {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 140px;
}

.poke-mini {
  width: 32px;
  height: 32px;
  image-rendering: pixelated;
  flex-shrink: 0;
}

.side-tag {
  display: inline-block;
  font-size: 0.65rem;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
}

.side-tag.ally {
  background: rgba(74, 144, 217, 0.2);
  color: #6ba3e8;
}

.side-tag.foe {
  background: rgba(229, 83, 79, 0.2);
  color: #e57373;
}

.slot-num {
  margin-left: 6px;
  font-size: 0.72rem;
  color: var(--text-muted);
}

.bar-cell {
  min-width: 100px;
  vertical-align: middle;
}

.bar-track {
  height: 8px;
  border-radius: 4px;
  background: var(--bg-glass);
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 4px;
  min-width: 4px;
  transition: width 0.25s ease;
}

.bar-fill.ally {
  background: linear-gradient(90deg, #4a90d9, #62bc5a);
}

.bar-fill.foe {
  background: linear-gradient(90deg, #e55353, #ff9741);
}

.empty-hint {
  text-align: center;
  color: var(--text-muted);
  padding: 32px;
  font-size: 0.9rem;
}

.picker-overlay {
  position: fixed;
  inset: 0;
  z-index: 400;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 48px 16px;
  overflow-y: auto;
}

.picker-modal {
  width: 100%;
  max-width: 420px;
  max-height: min(80vh, 560px);
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45);
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

.picker-header h3 {
  margin: 0;
  font-size: 1rem;
}

.picker-close {
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 8px;
}

.picker-close:hover {
  background: var(--bg-glass);
  color: var(--text-primary);
}

.picker-search {
  margin: 12px 16px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 0.9rem;
}

.picker-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 12px;
  max-height: 400px;
}

.picker-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 8px;
  margin-bottom: 4px;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  color: var(--text-primary);
}

.picker-row:hover {
  background: var(--bg-glass);
}

.picker-sprite {
  width: 40px;
  height: 40px;
  image-rendering: pixelated;
  flex-shrink: 0;
}

.picker-row-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.picker-name {
  font-weight: 600;
  font-size: 0.88rem;
}

.picker-meta {
  font-size: 0.72rem;
  color: var(--text-muted);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.mega-tag {
  font-size: 0.6rem;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  background: linear-gradient(135deg, #ab6ac8, #7e6bc4);
  color: #fff;
}

.type-chip {
  font-size: 0.6rem;
  padding: 2px 5px;
  border-radius: 4px;
  text-transform: capitalize;
}

.picker-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
  margin: 0;
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
