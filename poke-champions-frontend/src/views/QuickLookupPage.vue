<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { pokemonRosterApi } from '../api/pokemonRoster'
import { typeRosterApi } from '../api/typeRoster'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { comparePokemonByFormId } from '../utils/pokemonSort'
import { typeBadgeClasses } from '../utils/pokemonTypesDisplay'
import { localizedName } from '../utils/localizedName'
import { useLocalePath } from '../composables/useLocalePath'
import { ALL_TYPES, getDefenseMultipliers } from '../composables/useTeamStore'

const { t } = useI18n()
const { localePath } = useLocalePath()

const PIN_STORAGE_KEY = 'quick-lookup-pinned-pokemon'

const pokemonList = ref([])
const types = ref([])
const loading = ref(true)
const error = ref(null)
const search = ref('')
const selectedTypes = ref([])
const pinnedPokemon = ref(loadPinnedPokemon())
const abilitiesByPokemon = ref({})

function loadPinnedPokemon() {
  try {
    const raw = localStorage.getItem(PIN_STORAGE_KEY)
    const parsed = JSON.parse(raw || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function savePinnedPokemon() {
  localStorage.setItem(PIN_STORAGE_KEY, JSON.stringify(pinnedPokemon.value))
}

function isPinned(apiName) {
  return pinnedPokemon.value.includes(apiName)
}

function togglePin(apiName) {
  if (!apiName) return
  if (isPinned(apiName)) {
    pinnedPokemon.value = pinnedPokemon.value.filter((entry) => entry !== apiName)
  } else {
    pinnedPokemon.value = [...pinnedPokemon.value, apiName]
  }
  savePinnedPokemon()
}

async function loadAbilitiesMap() {
  const url = `${import.meta.env.BASE_URL}static-data/pokemon-abilities.json`
  const res = await fetch(url)
  if (!res.ok) throw new Error(`Failed to load abilities map: ${res.status}`)
  return res.json()
}

onMounted(async () => {
  try {
    const [pokemonRes, typesRes, abilitiesMap] = await Promise.all([
      pokemonRosterApi.getPokemonList(),
      typeRosterApi.getTypes(),
      loadAbilitiesMap(),
    ])
    const arr = Array.isArray(pokemonRes.data) ? pokemonRes.data : []
    pokemonList.value = [...arr].sort(comparePokemonByFormId)
    types.value = Array.isArray(typesRes.data) ? typesRes.data : []
    abilitiesByPokemon.value = abilitiesMap && typeof abilitiesMap === 'object' ? abilitiesMap : {}
  } catch (e) {
    error.value = t('quickLookup.loadError')
  } finally {
    loading.value = false
  }
})

function toggleType(typeName) {
  if (!typeName) return
  if (selectedTypes.value.includes(typeName)) {
    selectedTypes.value = selectedTypes.value.filter((entry) => entry !== typeName)
    return
  }
  if (selectedTypes.value.length >= 2) return
  selectedTypes.value = [...selectedTypes.value, typeName]
}

function clearFilters() {
  search.value = ''
  selectedTypes.value = []
}

function matchesSearch(pokemon) {
  if (!search.value) return true
  const raw = search.value.trim()
  const q = raw.toLowerCase()
  return (
    (pokemon.displayName && pokemon.displayName.toLowerCase().includes(q)) ||
    (pokemon.chineseName && pokemon.chineseName.toLowerCase().includes(q)) ||
    (pokemon.japaneseName && pokemon.japaneseName.toLowerCase().includes(q)) ||
    (pokemon.apiName && pokemon.apiName.toLowerCase().includes(q)) ||
    String(pokemon.nationalDexNumber).includes(raw)
  )
}

function matchesSelectedTypes(pokemon) {
  if (!selectedTypes.value.length) return true
  const ownTypes = pokemon.types || []
  return selectedTypes.value.every((typeName) => ownTypes.includes(typeName))
}

const visiblePokemon = computed(() => {
  const filtered = pokemonList.value.filter((pokemon) => {
    if (isPinned(pokemon.apiName)) return true
    return matchesSearch(pokemon) && matchesSelectedTypes(pokemon)
  })

  return [...filtered].sort((a, b) => {
    const pinDiff = Number(isPinned(b.apiName)) - Number(isPinned(a.apiName))
    if (pinDiff !== 0) return pinDiff
    return comparePokemonByFormId(a, b)
  })
})

const TYPE_IMMUNITY_ABILITIES = {
  levitate: ['ground'],
  'earth-eater': ['ground'],
  'flash-fire': ['fire'],
  'well-baked-body': ['fire'],
  'water-absorb': ['water'],
  'storm-drain': ['water'],
  'dry-skin': ['water'],
  'volt-absorb': ['electric'],
  'lightning-rod': ['electric'],
  'motor-drive': ['electric'],
  'sap-sipper': ['grass'],
}

function roundMultiplier(value) {
  return Math.round(value * 100) / 100
}

function applyAbilityToMatchup(baseMap, abilityName) {
  const result = { ...baseMap }
  const ability = String(abilityName || '').toLowerCase()

  for (const immuneType of TYPE_IMMUNITY_ABILITIES[ability] || []) {
    result[immuneType] = 0
  }

  if (ability === 'thick-fat') {
    result.fire = roundMultiplier(result.fire * 0.5)
    result.ice = roundMultiplier(result.ice * 0.5)
  }

  if (ability === 'heatproof') {
    result.fire = roundMultiplier(result.fire * 0.5)
  }

  if (ability === 'water-bubble') {
    result.fire = roundMultiplier(result.fire * 0.5)
  }

  if (ability === 'dry-skin') {
    result.fire = roundMultiplier(result.fire * 1.25)
  }

  if (ability === 'fluffy') {
    result.fire = roundMultiplier(result.fire * 2)
  }

  if (ability === 'purifying-salt') {
    result.ghost = roundMultiplier(result.ghost * 0.5)
  }

  if (ability === 'filter' || ability === 'solid-rock' || ability === 'prism-armor') {
    for (const typeName of ALL_TYPES) {
      if (result[typeName] > 1) {
        result[typeName] = roundMultiplier(result[typeName] * 0.75)
      }
    }
  }

  if (ability === 'wonder-guard') {
    for (const typeName of ALL_TYPES) {
      if (result[typeName] <= 1) {
        result[typeName] = 0
      }
    }
  }

  return result
}

function matchupSignature(matchupMap) {
  return ALL_TYPES.map((typeName) => `${typeName}:${matchupMap[typeName]}`).join('|')
}

function multiplierLabel(multiplier) {
  const rounded = roundMultiplier(multiplier)
  const text = Number.isInteger(rounded)
    ? String(rounded)
    : String(rounded).replace(/\.0+$/, '').replace(/(\.\d*[1-9])0+$/, '$1')
  return `${text}×`
}

function groupedMatchups(matchupMap) {
  const groups = {}
  for (const typeName of ALL_TYPES) {
    const multiplier = roundMultiplier(matchupMap[typeName])
    if (multiplier === 1) continue
    const key = String(multiplier)
    if (!groups[key]) groups[key] = []
    groups[key].push(typeName)
  }
  return groups
}

function abilityDisplayName(ability) {
  return ability?.chineseName || ability?.displayName || ability?.japaneseName || ability?.name || t('quickLookup.noAbilityInfo')
}

function buildAbilityVariants(pokemon) {
  const baseMatchup = getDefenseMultipliers(pokemon.types || [])
  const abilities = Array.isArray(abilitiesByPokemon.value[pokemon.apiName]) ? abilitiesByPokemon.value[pokemon.apiName] : []
  const sourceAbilities = abilities.length ? abilities : [{ name: 'no-ability-info' }]
  const groups = new Map()

  for (const ability of sourceAbilities) {
    const adjusted = applyAbilityToMatchup(baseMatchup, ability.name)
    const signature = matchupSignature(adjusted)
    if (!groups.has(signature)) {
      groups.set(signature, {
        abilityNames: [],
        matchupMap: adjusted,
        grouped: groupedMatchups(adjusted),
      })
    }
    groups.get(signature).abilityNames.push(abilityDisplayName(ability))
  }

  return [...groups.values()].map((entry) => ({
    abilityLabel: entry.abilityNames.join(' / '),
    matchupMap: entry.matchupMap,
    grouped: entry.grouped,
  }))
}

const rows = computed(() =>
  visiblePokemon.value.flatMap((pokemon) =>
    buildAbilityVariants(pokemon).map((variant, index) => ({
      pokemon,
      isPinned: isPinned(pokemon.apiName),
      rowKey: `${pokemon.apiName}-${index}-${variant.abilityLabel}`,
      abilityLabel: variant.abilityLabel,
      grouped: variant.grouped,
    })),
  ),
)

const multiplierColumns = computed(() => {
  const seen = new Set()
  for (const row of rows.value) {
    Object.keys(row.grouped).forEach((key) => seen.add(Number(key)))
  }
  return [...seen]
    .sort((a, b) => a - b)
    .map((value) => ({ key: String(value), value, label: multiplierLabel(value) }))
})
</script>

<template>
  <div class="container quick-lookup-page">
    <div class="page-header">
      <h1 class="page-title">{{ t('quickLookup.title') }}</h1>
      <span v-if="!loading && !error" class="result-badge">{{ t('common.resultCount', { count: rows.length }) }}</span>
    </div>

    <p class="page-subtitle">{{ t('quickLookup.subtitle') }}</p>

    <div class="filters-panel">
      <div class="search-box">
        <span class="material-symbols-rounded search-icon">search</span>
        <input v-model="search" type="text" :placeholder="t('quickLookup.searchPlaceholder')" class="search-input" />
        <button v-if="search" class="search-clear" @click="search = ''">
          <span class="material-symbols-rounded">close</span>
        </button>
      </div>

      <div class="type-filter-panel">
        <div class="type-filter-head">
          <div>
            <p class="type-filter-hint">{{ t('quickLookup.hint') }}</p>
            <p class="type-filter-note">{{ t('quickLookup.pinHint') }}</p>
          </div>
          <div class="type-filter-actions">
            <span class="selected-count">{{ t('quickLookup.selectedCount', { count: selectedTypes.length }) }}</span>
            <button
              v-if="search || selectedTypes.length"
              type="button"
              class="clear-btn"
              @click="clearFilters"
            >
              {{ t('quickLookup.clearFilters') }}
            </button>
          </div>
        </div>

        <div class="type-tab-bar">
          <button
            v-for="type in types"
            :key="type.name"
            type="button"
            :class="['type-tab', type.name, {
              selected: selectedTypes.includes(type.name),
              disabled: selectedTypes.length >= 2 && !selectedTypes.includes(type.name),
            }]"
            :disabled="selectedTypes.length >= 2 && !selectedTypes.includes(type.name)"
            @click="toggleType(type.name)"
          >
            {{ t('pokemon.types.' + type.name) }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="loading" class="loading">{{ t('common.loading') }}</div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>

    <template v-else>
      <div class="table-wrap">
        <table class="quick-table">
          <thead>
            <tr>
              <th>{{ t('quickLookup.colPokemon') }}</th>
              <th>{{ t('quickLookup.colAbility') }}</th>
              <th v-for="column in multiplierColumns" :key="column.key">{{ column.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.rowKey" :class="{ pinned: row.isPinned }">
              <td class="pokemon-cell">
                <div class="pokemon-thumb-wrap">
                  <router-link :to="localePath(`/pokemon/${row.pokemon.apiName}`)" class="pokemon-thumb-link" :title="localizedName(row.pokemon)">
                    <img
                      :src="getPokemonImageUrl(row.pokemon)"
                      :alt="localizedName(row.pokemon)"
                      loading="lazy"
                      class="pokemon-thumb"
                    />
                  </router-link>
                  <button
                    type="button"
                    class="pin-btn"
                    :class="{ active: row.isPinned }"
                    :title="row.isPinned ? t('quickLookup.unpin') : t('quickLookup.pin')"
                    @click="togglePin(row.pokemon.apiName)"
                  >
                    <span class="material-symbols-rounded">keep</span>
                  </button>
                </div>
              </td>
              <td class="ability-cell">{{ row.abilityLabel }}</td>
              <td v-for="column in multiplierColumns" :key="column.key">
                <div v-if="row.grouped[column.key]?.length" class="matchup-list">
                  <span
                    v-for="typeName in row.grouped[column.key]"
                    :key="typeName"
                    :class="['type-chip', ...typeBadgeClasses(typeName)]"
                  >
                    {{ t('pokemon.types.' + typeName) }}
                  </span>
                </div>
                <span v-else class="empty-matchup">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="rows.length === 0" class="empty-state">
        <span class="material-symbols-rounded empty-icon">search_off</span>
        <p>{{ t('quickLookup.empty') }}</p>
      </div>
    </template>
  </div>
</template>

<style scoped>
.quick-lookup-page {
  padding-bottom: 40px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.page-header .page-title {
  margin-bottom: 0;
}

.page-subtitle {
  margin-bottom: 20px;
  color: var(--text-secondary);
  line-height: 1.6;
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

.filters-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 22px;
}

.search-box {
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 0 14px;
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

.type-filter-panel {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 14px;
}

.type-filter-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.type-filter-hint {
  color: var(--text-secondary);
  font-size: 0.9rem;
}

.type-filter-note {
  color: var(--text-muted);
  font-size: 0.8rem;
  margin-top: 4px;
}

.type-filter-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.selected-count {
  font-size: 0.8rem;
  color: var(--text-muted);
  font-weight: 600;
}

.clear-btn {
  padding: 8px 12px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid var(--border);
  color: var(--text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
  transition: all 0.2s;
}

.clear-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-primary);
}

.type-tab-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.type-tab {
  padding: 9px 14px;
  border-radius: 999px;
  border: 2px solid transparent;
  color: #fff;
  font-size: 0.85rem;
  font-weight: 700;
  opacity: 0.85;
  transition: all 0.2s ease;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.25);
}

.type-tab:hover:not(:disabled) {
  opacity: 1;
  transform: translateY(-1px);
}

.type-tab.selected {
  opacity: 1;
  border-color: rgba(255, 255, 255, 0.8);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.12);
}

.type-tab.disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.type-tab.normal { background: linear-gradient(135deg, var(--type-normal), #8a8d97); }
.type-tab.fire { background: linear-gradient(135deg, var(--type-fire), #e07a2a); }
.type-tab.water { background: linear-gradient(135deg, var(--type-water), #3a78c0); }
.type-tab.electric { background: linear-gradient(135deg, var(--type-electric), #e6bc00); color: #1a1a1a; text-shadow: none; }
.type-tab.grass { background: linear-gradient(135deg, var(--type-grass), #4da44a); }
.type-tab.ice { background: linear-gradient(135deg, var(--type-ice), #5db8a8); color: #1a1a1a; text-shadow: none; }
.type-tab.fighting { background: linear-gradient(135deg, var(--type-fighting), #b03058); }
.type-tab.poison { background: linear-gradient(135deg, var(--type-poison), #9050b0); }
.type-tab.ground { background: linear-gradient(135deg, var(--type-ground), #c06030); }
.type-tab.flying { background: linear-gradient(135deg, var(--type-flying), #7090c8); }
.type-tab.psychic { background: linear-gradient(135deg, var(--type-psychic), #e05868); }
.type-tab.bug { background: linear-gradient(135deg, var(--type-bug), #78a020); }
.type-tab.rock { background: linear-gradient(135deg, var(--type-rock), #a89870); }
.type-tab.ghost { background: linear-gradient(135deg, var(--type-ghost), #405090); }
.type-tab.dragon { background: linear-gradient(135deg, var(--type-dragon), #0958a8); }
.type-tab.dark { background: linear-gradient(135deg, var(--type-dark), #464050); }
.type-tab.steel { background: linear-gradient(135deg, var(--type-steel), #487888); color: #e8e8e8; text-shadow: none; }
.type-tab.fairy { background: linear-gradient(135deg, var(--type-fairy), #d070d0); }

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-card);
}

.quick-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

.quick-table th,
.quick-table td {
  padding: 14px 12px;
  text-align: left;
  vertical-align: middle;
  border-bottom: 1px solid var(--border);
}

.quick-table th {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.03);
  white-space: nowrap;
}

.quick-table tbody tr:last-child td {
  border-bottom: none;
}

.quick-table tbody tr:hover {
  background: rgba(255, 255, 255, 0.03);
}

.quick-table tbody tr.pinned {
  background: rgba(255, 196, 0, 0.05);
}

.pokemon-cell {
  width: 88px;
  min-width: 88px;
}

.pokemon-thumb-wrap {
  position: relative;
  width: 64px;
  margin: 0 auto;
}

.pokemon-thumb-link {
  display: block;
}

.pokemon-thumb {
  width: 64px;
  height: 64px;
  object-fit: contain;
  filter: drop-shadow(0 4px 10px rgba(0, 0, 0, 0.35));
}

.pin-btn {
  position: absolute;
  right: -8px;
  top: -8px;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(18, 20, 40, 0.88);
  border: 1px solid var(--border);
  color: var(--text-muted);
  transition: all 0.2s ease;
}

.pin-btn .material-symbols-rounded {
  font-size: 16px;
  transform: rotate(45deg);
}

.pin-btn:hover {
  color: var(--text-primary);
  border-color: var(--border-light);
}

.pin-btn.active {
  color: #ffc107;
  border-color: rgba(255, 193, 7, 0.45);
  background: rgba(72, 54, 0, 0.95);
}

.ability-cell {
  min-width: 180px;
  color: var(--text-primary);
  font-weight: 600;
}

.matchup-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 92px;
}

.type-chip {
  white-space: nowrap;
}

.empty-matchup {
  color: var(--text-muted);
  font-size: 0.84rem;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 20px 20px;
  color: var(--text-muted);
}

.empty-icon {
  font-size: 48px;
  opacity: 0.5;
}

@media (max-width: 768px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .type-filter-panel {
    padding: 12px;
  }

  .type-tab-bar {
    gap: 6px;
  }

  .type-tab {
    padding: 8px 12px;
    font-size: 0.8rem;
  }

  .quick-table {
    min-width: 900px;
  }
}
</style>
