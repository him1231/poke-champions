<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { pokemonRosterApi } from '../api/pokemonRoster'
import { typeRosterApi } from '../api/typeRoster'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { comparePokemonByFormId } from '../utils/pokemonSort'
import { pokemonTypesForDisplay } from '../utils/pokemonTypesDisplay'
import { localizedName, localizedTypeName } from '../utils/localizedName'
import { useLocalePath } from '../composables/useLocalePath'

const { t } = useI18n()
const { localePath } = useLocalePath()

const pokemonList = ref([])
const types = ref([])
const loading = ref(true)
const error = ref(null)
const search = ref('')
const selectedType = ref('')
const showMegaOnly = ref(false)

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
    error.value = t('pokemonList.loadError')
  } finally {
    loading.value = false
  }
})

const filtered = computed(() => {
  let list = pokemonList.value

  if (search.value) {
    const q = search.value.toLowerCase()
    list = list.filter(
      (p) =>
        (p.displayName && p.displayName.toLowerCase().includes(q)) ||
        (p.chineseName && p.chineseName.toLowerCase().includes(q)) ||
        (p.apiName && p.apiName.toLowerCase().includes(q)) ||
        String(p.nationalDexNumber).includes(q),
    )
  }

  if (selectedType.value) {
    list = list.filter((p) => p.types && p.types.includes(selectedType.value))
  }

  if (showMegaOnly.value) {
    list = list.filter((p) => p.mega === true || p.isMega === true)
  }

  return list
})

function getTypeClass(typeName) {
  const slug = String(typeName || '').toLowerCase().replace(/\s+/g, '-')
  return `type-badge ${slug}`
}
</script>

<template>
  <div class="container">
    <div class="page-header">
      <h1 class="page-title">{{ t('pokemonList.title') }}</h1>
      <span class="result-badge" v-if="!loading && !error">{{ t('common.resultCount', { count: filtered.length }) }}</span>
    </div>

    <div class="filters-bar">
      <div class="search-box">
        <span class="material-symbols-rounded search-icon">search</span>
        <input v-model="search" type="text" :placeholder="t('pokemonList.searchPlaceholder')" class="search-input" />
        <button v-if="search" class="search-clear" @click="search = ''">
          <span class="material-symbols-rounded">close</span>
        </button>
      </div>

      <div class="filter-group">
        <select v-model="selectedType" class="filter-select">
          <option value="">{{ t('common.allTypes') }}</option>
          <option v-for="tp in types" :key="tp.name" :value="tp.name">{{ localizedTypeName(tp) }} ({{ tp.name }})</option>
        </select>

        <button
          class="mega-btn"
          :class="{ active: showMegaOnly }"
          @click="showMegaOnly = !showMegaOnly"
        >
          <span class="material-symbols-rounded" style="font-size: 16px">auto_awesome</span>
          <span>Mega</span>
        </button>
      </div>
    </div>

    <div v-if="loading" class="loading">{{ t('common.loading') }}</div>
    <div v-else-if="error" class="error-msg">{{ error }}</div>

    <div v-else class="pokemon-grid">
      <router-link
        v-for="p in filtered"
        :key="p.id ?? `${p.apiName}-${p.formId}`"
        :to="localePath(`/pokemon/${p.apiName}`)"
        class="pokemon-card"
      >
        <div class="card-header">
          <span class="dex-num">#{{ String(p.nationalDexNumber).padStart(4, '0') }}</span>
          <span v-if="p.mega || p.isMega" class="mega-tag">
            <span class="material-symbols-rounded" style="font-size: 12px">auto_awesome</span>
            MEGA
          </span>
        </div>

        <div class="card-img-wrap">
          <div class="card-img-bg"></div>
          <img :src="getPokemonImageUrl(p)" :alt="localizedName(p)" loading="lazy" />
        </div>

        <div class="card-info">
          <h3 class="poke-name">{{ localizedName(p) }}</h3>
          <p v-if="p.chineseName" class="poke-name-en">{{ p.displayName }}</p>
          <div class="poke-types">
            <span v-for="tp in pokemonTypesForDisplay(p)" :key="tp.name" :class="getTypeClass(tp.name)">
              {{ localizedTypeName(tp) || tp.name }}
            </span>
          </div>
        </div>
      </router-link>
    </div>

    <div v-if="!loading && !error && filtered.length === 0" class="empty-state">
      <span class="material-symbols-rounded empty-icon">search_off</span>
      <p>{{ t('pokemonList.empty') }}</p>
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

.mega-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 18px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 14px;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--text-muted);
  white-space: nowrap;
  transition: all 0.2s;
}

.mega-btn:hover {
  border-color: var(--border-light);
  color: var(--text-secondary);
}

.mega-btn.active {
  background: linear-gradient(135deg, rgba(255, 107, 107, 0.15), rgba(255, 165, 0, 0.1));
  border-color: rgba(255, 107, 107, 0.3);
  color: #ffa500;
}

.pokemon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(185px, 1fr));
  gap: 16px;
  animation: fadeIn 0.4s ease;
}

.pokemon-card {
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 16px;
  transition: all 0.25s ease;
  text-align: center;
  backdrop-filter: var(--glass-blur);
  overflow: hidden;
}

.pokemon-card:hover {
  background: var(--bg-card-hover);
  transform: translateY(-4px);
  box-shadow: var(--shadow);
  border-color: var(--border-light);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.dex-num {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.mega-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: 0.6rem;
  font-weight: 800;
  padding: 2px 8px;
  border-radius: 20px;
  background: linear-gradient(135deg, #ff6b6b, #ffa500);
  color: #fff;
  letter-spacing: 0.5px;
}

.card-img-wrap {
  width: 100px;
  height: 100px;
  margin: 8px auto;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.card-img-bg {
  position: absolute;
  inset: -10px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.03), transparent 70%);
}

.card-img-wrap img {
  max-width: 100%;
  max-height: 100%;
  image-rendering: auto;
  filter: drop-shadow(0 4px 12px rgba(0, 0, 0, 0.4));
  position: relative;
  z-index: 1;
  transition: transform 0.3s ease;
}

.pokemon-card:hover .card-img-wrap img {
  transform: scale(1.08);
}

.card-info {
  flex: 1;
}

.poke-name {
  font-size: 0.92rem;
  font-weight: 700;
  margin-bottom: 2px;
  color: var(--text-primary);
}

.poke-name-en {
  font-size: 0.72rem;
  color: var(--text-muted);
  margin-bottom: 10px;
}

.poke-types {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 4px;
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
  .pokemon-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
    gap: 12px;
  }

  .filters-bar {
    gap: 8px;
  }

  .search-box {
    min-width: 100%;
  }

  .filter-group {
    width: 100%;
  }

  .filter-select,
  .mega-btn {
    flex: 1;
  }
}
</style>
