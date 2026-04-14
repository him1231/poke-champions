<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { pokemonRosterApi } from '../api/pokemonRoster'
import { typeRosterApi } from '../api/typeRoster'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { comparePokemonByFormId } from '../utils/pokemonSort'
import { pokemonTypesForDisplay, typeBadgeClasses } from '../utils/pokemonTypesDisplay'
import { localizedName } from '../utils/localizedName'
import { useLocalePath } from '../composables/useLocalePath'
import { ALL_TYPES, getDefenseMultipliers } from '../composables/useTeamStore'

const { t } = useI18n()
const { localePath } = useLocalePath()

const pokemonList = ref([])
const types = ref([])
const loading = ref(true)
const error = ref(null)
const search = ref('')
const selectedTypes = ref([])

onMounted(async () => {
  try {
    const [pokemonRes, typesRes] = await Promise.all([
      pokemonRosterApi.getPokemonList(),
      typeRosterApi.getTypes(),
    ])
    const arr = Array.isArray(pokemonRes.data) ? pokemonRes.data : []
    pokemonList.value = [...arr].sort(comparePokemonByFormId)
    types.value = Array.isArray(typesRes.data) ? typesRes.data : []
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

function matchesSelectedTypes(pokemon) {
  if (!selectedTypes.value.length) return true
  const ownTypes = pokemon.types || []
  return selectedTypes.value.every((typeName) => ownTypes.includes(typeName))
}

const filteredPokemon = computed(() => {
  let list = pokemonList.value

  if (search.value) {
    const raw = search.value.trim()
    const q = raw.toLowerCase()
    list = list.filter(
      (pokemon) =>
        (pokemon.displayName && pokemon.displayName.toLowerCase().includes(q)) ||
        (pokemon.chineseName && pokemon.chineseName.toLowerCase().includes(q)) ||
        (pokemon.japaneseName && pokemon.japaneseName.toLowerCase().includes(q)) ||
        (pokemon.apiName && pokemon.apiName.toLowerCase().includes(q)) ||
        String(pokemon.nationalDexNumber).includes(raw),
    )
  }

  return list.filter(matchesSelectedTypes)
})

const rows = computed(() =>
  filteredPokemon.value.map((pokemon) => {
    const multipliers = getDefenseMultipliers(pokemon.types || [])
    const weaknessEntries = Object.entries(multipliers)
      .filter(([, multiplier]) => multiplier > 1)
      .sort((a, b) => {
        if (b[1] !== a[1]) return b[1] - a[1]
        return ALL_TYPES.indexOf(a[0]) - ALL_TYPES.indexOf(b[0])
      })

    return {
      pokemon,
      quadWeaknesses: weaknessEntries
        .filter(([, multiplier]) => multiplier >= 4)
        .map(([type]) => type),
      weaknesses: weaknessEntries
        .filter(([, multiplier]) => multiplier > 1 && multiplier < 4)
        .map(([type]) => type),
    }
  }),
)

function pokemonSecondaryName(pokemon) {
  const primary = localizedName(pokemon)
  const candidates = [pokemon.displayName, pokemon.chineseName, pokemon.japaneseName]
  return candidates.find((name) => name && name !== primary) || ''
}
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
          <p class="type-filter-hint">{{ t('quickLookup.hint') }}</p>
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
              <th>{{ t('quickLookup.colQuadWeak') }}</th>
              <th>{{ t('quickLookup.colWeak') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.pokemon.id ?? `${row.pokemon.apiName}-${row.pokemon.formId}`">
              <td class="pokemon-cell">
                <router-link :to="localePath(`/pokemon/${row.pokemon.apiName}`)" class="pokemon-link">
                  <img
                    :src="getPokemonImageUrl(row.pokemon)"
                    :alt="localizedName(row.pokemon)"
                    loading="lazy"
                    class="pokemon-thumb"
                  />
                  <div class="pokemon-main">
                    <div class="pokemon-name-row">
                      <span class="pokemon-name">{{ localizedName(row.pokemon) }}</span>
                      <span class="pokemon-dex">#{{ String(row.pokemon.nationalDexNumber).padStart(4, '0') }}</span>
                    </div>
                    <p v-if="pokemonSecondaryName(row.pokemon)" class="pokemon-subname">{{ pokemonSecondaryName(row.pokemon) }}</p>
                    <div class="pokemon-types">
                      <span
                        v-for="type in pokemonTypesForDisplay(row.pokemon)"
                        :key="type.name"
                        :class="typeBadgeClasses(type.name)"
                      >
                        {{ t('pokemon.types.' + type.name) }}
                      </span>
                    </div>
                  </div>
                </router-link>
              </td>
              <td>
                <div v-if="row.quadWeaknesses.length" class="weakness-list">
                  <span
                    v-for="type in row.quadWeaknesses"
                    :key="type"
                    :class="typeBadgeClasses(type)"
                  >
                    {{ t('pokemon.types.' + type) }}
                  </span>
                </div>
                <span v-else class="empty-weakness">{{ t('quickLookup.noQuadWeak') }}</span>
              </td>
              <td>
                <div v-if="row.weaknesses.length" class="weakness-list">
                  <span
                    v-for="type in row.weaknesses"
                    :key="type"
                    :class="typeBadgeClasses(type)"
                  >
                    {{ t('pokemon.types.' + type) }}
                  </span>
                </div>
                <span v-else class="empty-weakness">{{ row.quadWeaknesses.length ? t('quickLookup.none') : t('quickLookup.noWeak') }}</span>
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
  min-width: 760px;
  border-collapse: collapse;
}

.quick-table th,
.quick-table td {
  padding: 14px 16px;
  text-align: left;
  vertical-align: top;
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

.pokemon-cell {
  min-width: 300px;
}

.pokemon-link {
  display: flex;
  align-items: center;
  gap: 14px;
  color: inherit;
  text-decoration: none;
}

.pokemon-thumb {
  width: 64px;
  height: 64px;
  object-fit: contain;
  flex-shrink: 0;
  filter: drop-shadow(0 4px 10px rgba(0, 0, 0, 0.35));
}

.pokemon-main {
  min-width: 0;
}

.pokemon-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}

.pokemon-name {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-primary);
}

.pokemon-dex {
  font-size: 0.74rem;
  font-weight: 700;
  color: var(--text-muted);
}

.pokemon-subname {
  margin-bottom: 8px;
  color: var(--text-muted);
  font-size: 0.78rem;
}

.pokemon-types,
.weakness-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.empty-weakness {
  color: var(--text-muted);
  font-size: 0.85rem;
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

  .pokemon-link {
    gap: 10px;
  }

  .pokemon-thumb {
    width: 54px;
    height: 54px;
  }
}
</style>
