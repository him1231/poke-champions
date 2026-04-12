<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { pokemonRosterApi } from '../api/pokemonRoster'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { getMoveCatalog, mergeMoveChineseFromCatalog } from '../utils/moveCatalog'
import { pokemonTypesForDisplay, typeBadgeClasses } from '../utils/pokemonTypesDisplay'
import NatureGridSelector from '../components/NatureGridSelector.vue'
import { getNatureMultiplier, NATURE_DEFS } from '../constants/pokemonNatures'

const route = useRoute()

const loading = ref(true)
const error = ref('')
const pokemon = ref(null)
const matchup = ref(null)
const learnedMoves = ref([])

const tab = ref('stats')
const natureExpanded = ref(false)
const selectedMove = ref(null)

const CATEGORY_LABELS = { PHYSICAL: '物理', SPECIAL: '特殊', STATUS: '變化' }

function moveRowKey(m) {
  return m?.id ?? m?.name ?? ''
}

function toggleMoveDetail(m) {
  const k = moveRowKey(m)
  const cur = selectedMove.value
  if (cur && moveRowKey(cur) === k) selectedMove.value = null
  else selectedMove.value = m
}

function closeMoveDetail() {
  selectedMove.value = null
}

function categoryLabelZh(cat) {
  if (!cat) return '—'
  return CATEGORY_LABELS[cat] || String(cat)
}

function learnSourceLabel(m) {
  return m._learnSource ?? m.source ?? ''
}

function onMoveDetailKeydown(e) {
  if (e.key === 'Escape') closeMoveDetail()
}

onMounted(() => window.addEventListener('keydown', onMoveDetailKeydown))
onUnmounted(() => window.removeEventListener('keydown', onMoveDetailKeydown))

const STAT_KEYS = ['hp', 'attack', 'defense', 'specialAttack', 'specialDefense', 'speed']
const STAT_LABELS = {
  hp: 'HP',
  attack: '攻擊',
  defense: '防禦',
  specialAttack: '特攻',
  specialDefense: '特防',
  speed: '速度',
}
const STAT_COLORS = {
  hp: '#ff5555',
  attack: '#ff9741',
  defense: '#fcd000',
  specialAttack: '#4a90d9',
  specialDefense: '#62bc5a',
  speed: '#fa7179',
}
const TOTAL_EV = 66
const MAX_PER = 32

const currentNature = ref('serious')
const statPoints = ref(emptyPoints())

function emptyPoints() {
  return { hp: 0, attack: 0, defense: 0, specialAttack: 0, specialDefense: 0, speed: 0 }
}

const remainingPoints = computed(() => {
  const used = STAT_KEYS.reduce((s, k) => s + (statPoints.value[k] || 0), 0)
  return TOTAL_EV - used
})

function adjustStatPoint(key, delta) {
  if (!STAT_KEYS.includes(key)) return
  const cur = statPoints.value[key] || 0
  if (delta > 0) {
    if (remainingPoints.value <= 0) return
    if (cur >= MAX_PER) return
    statPoints.value[key] = cur + 1
  } else if (delta < 0) {
    if (cur <= 0) return
    statPoints.value[key] = cur - 1
  }
}

function maxStatPoint(key) {
  if (!STAT_KEYS.includes(key)) return
  const cur = statPoints.value[key] || 0
  const canAdd = Math.min(MAX_PER - cur, remainingPoints.value)
  if (canAdd > 0) statPoints.value[key] = cur + canAdd
}

function clearStatPoint(key) {
  if (!STAT_KEYS.includes(key)) return
  statPoints.value[key] = 0
}

function actualStatForPoints(p, pts, nature, key) {
  const b = (k) => Number(p[k] ?? 0)
  switch (key) {
    case 'hp':
      return b('hp') + 75 + (pts.hp || 0)
    case 'attack':
      return Math.floor((b('attack') + 20 + (pts.attack || 0)) * getNatureMultiplier(nature, 'attack'))
    case 'defense':
      return Math.floor((b('defense') + 20 + (pts.defense || 0)) * getNatureMultiplier(nature, 'defense'))
    case 'specialAttack':
      return Math.floor(
        (b('specialAttack') + 20 + (pts.specialAttack || 0)) * getNatureMultiplier(nature, 'specialAttack'),
      )
    case 'specialDefense':
      return Math.floor(
        (b('specialDefense') + 20 + (pts.specialDefense || 0)) * getNatureMultiplier(nature, 'specialDefense'),
      )
    case 'speed':
      return Math.floor((b('speed') + 20 + (pts.speed || 0)) * getNatureMultiplier(nature, 'speed'))
    default:
      return 0
  }
}

const actualStats = computed(() => {
  const p = pokemon.value
  if (!p) return null
  const pts = statPoints.value
  const n = currentNature.value
  const out = {}
  for (const key of STAT_KEYS) {
    out[key] = actualStatForPoints(p, pts, n, key)
  }
  return out
})

/** 加點段條形顏色（與種族色區隔） */
const STAT_BAR_POINTS_COLOR = '#38bdf8'

const currentNatureLabelZh = computed(() => {
  const id = currentNature.value
  return NATURE_DEFS[id]?.labelZh ?? id
})

function natureTrendForStat(natureId, statKey) {
  if (statKey === 'hp') return 'neutral'
  const m = NATURE_DEFS[natureId]?.mult?.[statKey]
  if (m == null) return 'neutral'
  if (m > 1) return 'up'
  if (m < 1) return 'down'
  return 'neutral'
}

const natureEffectSummary = computed(() => {
  const id = currentNature.value
  const def = NATURE_DEFS[id]
  if (!def) return ''
  const mult = def.mult || {}
  const keys = Object.keys(mult)
  if (!keys.length) {
    return `${def.labelZh}：五維（非 HP）性格倍率皆為 1.0，無增減`
  }
  const upKeys = keys.filter((k) => mult[k] > 1)
  const downKeys = keys.filter((k) => mult[k] < 1)
  const upStr = upKeys.map((k) => STAT_LABELS[k] || k).join('、')
  const downStr = downKeys.map((k) => STAT_LABELS[k] || k).join('、')
  return `${def.labelZh}：↑ ${upStr}　↓ ${downStr}`
})

const statRows = computed(() => {
  const p = pokemon.value
  const act = actualStats.value
  if (!p || !act) return []
  const BAR = {
    hp: 500,
    attack: 320,
    defense: 320,
    specialAttack: 320,
    specialDefense: 320,
    speed: 320,
  }
  const n = currentNature.value
  const pts = statPoints.value
  const natureTrend = (key) => natureTrendForStat(n, key)
  return STAT_KEYS.map((key) => {
    const base = Number(p[key] ?? 0)
    const actual = act[key]
    const ptsNoRow = { ...pts, [key]: 0 }
    const actualNoPoints = actualStatForPoints(p, ptsNoRow, n, key)
    const barMax = BAR[key] || 300
    const barPct = Math.min(100, (actual / barMax) * 100)
    const barPctBase = Math.min(100, (actualNoPoints / barMax) * 100)
    const barPctPoints = Math.max(0, barPct - barPctBase)
    const flexBase = barPctPoints > 0 ? Math.max(barPctBase, 0.0001) : Math.max(barPctBase, 0.0001)
    const flexPoints = Math.max(barPctPoints, 0)
    return {
      key,
      label: STAT_LABELS[key],
      color: STAT_COLORS[key],
      pointsBarColor: STAT_BAR_POINTS_COLOR,
      natureTrend: natureTrend(key),
      base,
      points: pts[key] || 0,
      actual,
      barPct,
      barPctBase,
      barPctPoints,
      barFlexBase: flexBase,
      barFlexPoints: flexPoints,
    }
  })
})

function normalizeMovesPayload(data) {
  if (!data) return []
  const arr = Array.isArray(data) ? data : data.moves || data.content || []
  return arr
    .map((row) => {
      if (row?.move) return { ...row.move, _learnSource: row.source, _verified: row.verified }
      return row
    })
    .filter(Boolean)
}

function typeNameOfMove(m) {
  if (typeof m.type === 'object') return m.type?.name || ''
  return m.type || m.typeName || ''
}

function typeChineseOfMove(m) {
  if (typeof m.type === 'object') return m.type?.chineseName || typeNameOfMove(m)
  return m.typeChinese || typeNameOfMove(m)
}

async function load() {
  loading.value = true
  error.value = ''
  pokemon.value = null
  matchup.value = null
  learnedMoves.value = []
  const apiName = route.params.apiName
  try {
    const { data: all } = await pokemonRosterApi.getPokemonList()
    const arr = Array.isArray(all) ? all : []
    const found = arr.find((x) => x.apiName === apiName)
    if (!found) {
      error.value = '找不到此寶可夢'
      return
    }

    const [typesRes, matchupRes, movesRes] = await Promise.allSettled([
      pokemonRosterApi.getPokemonTypes(apiName),
      pokemonRosterApi.getPokemonMatchup(apiName),
      pokemonRosterApi.getPokemonMoves(apiName),
    ])

    let merged = { ...found }
    if (typesRes.status === 'fulfilled') {
      const td = typesRes.value.data
      const info = td?.typesInfo || td?.types || (Array.isArray(td) ? td : null)
      if (Array.isArray(info) && info.length) {
        merged = { ...merged, typesInfo: info }
      }
    }
    pokemon.value = merged

    if (matchupRes.status === 'fulfilled') {
      matchup.value = matchupRes.value.data
    }

    let moves = []
    if (movesRes.status === 'fulfilled') {
      moves = normalizeMovesPayload(movesRes.value.data)
    }
    try {
      const catalog = await getMoveCatalog()
      moves = mergeMoveChineseFromCatalog(moves, catalog)
    } catch {
      /* catalog load failure still shows the list */
    }
    learnedMoves.value = moves
  } catch (e) {
    error.value = e?.response?.data?.message || e.message || '載入失敗'
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.apiName,
  () => {
    currentNature.value = 'serious'
    statPoints.value = emptyPoints()
    tab.value = 'stats'
    selectedMove.value = null
    load()
  },
  { immediate: true },
)

watch(tab, (v) => {
  if (v !== 'moves') selectedMove.value = null
})

const tabs = [
  { id: 'stats', label: '能力值', icon: 'bar_chart' },
  { id: 'moves', label: '可學招式', icon: 'bolt' },
  { id: 'matchup', label: '屬性相剋', icon: 'shield' },
]

const matchupSections = computed(() => {
  if (!matchup.value) return []
  const m = matchup.value
  const sections = []
  if (m.immunities?.length) sections.push({ title: '0× 無效', items: m.immunities, cls: 'immune' })
  if (m.quadResistances?.length) sections.push({ title: '¼× 抵抗', items: m.quadResistances, cls: 'great' })
  if (m.resistances?.length) sections.push({ title: '½× 抵抗', items: m.resistances, cls: 'good' })
  if (m.neutral?.length) sections.push({ title: '1× 普通', items: m.neutral, cls: 'neutral-cls' })
  if (m.weaknesses?.length) sections.push({ title: '2× 弱點', items: m.weaknesses, cls: 'warn' })
  if (m.quadWeaknesses?.length) sections.push({ title: '4× 弱點', items: m.quadWeaknesses, cls: 'danger' })
  return sections
})
</script>

<template>
  <div class="container page">
    <RouterLink to="/pokemon" class="back-link">
      <span class="material-symbols-rounded">arrow_back</span>
      <span>返回圖鑑</span>
    </RouterLink>

    <p v-if="loading" class="loading">載入中</p>
    <p v-else-if="error" class="error-msg">{{ error }}</p>

    <template v-else-if="pokemon">
      <header class="detail-hero">
        <div class="hero-art-wrap">
          <div class="hero-art-glow"></div>
          <img class="hero-art" :src="getPokemonImageUrl(pokemon)" :alt="pokemon.chineseName || pokemon.displayName" />
        </div>
        <div class="hero-info">
          <span class="hero-dex">#{{ String(pokemon.nationalDexNumber).padStart(4, '0') }}</span>
          <h1 class="hero-name">{{ pokemon.chineseName || pokemon.displayName }}</h1>
          <p class="hero-en">{{ pokemon.displayName }}</p>
          <div class="hero-types">
            <span
              v-for="t in pokemonTypesForDisplay(pokemon)"
              :key="t.name"
              :class="typeBadgeClasses(t.name)"
            >
              {{ t.chineseName || t.name }}
            </span>
          </div>
        </div>
      </header>

      <nav class="tab-bar" aria-label="詳情分頁">
        <button
          v-for="t in tabs"
          :key="t.id"
          type="button"
          class="tab-btn"
          :class="{ active: tab === t.id }"
          @click="tab = t.id"
        >
          <span class="material-symbols-rounded tab-icon">{{ t.icon }}</span>
          <span>{{ t.label }}</span>
        </button>
      </nav>

      <!-- Stats -->
      <section v-show="tab === 'stats'" class="panel stats-panel">
        <div class="nature-collapse">
          <button type="button" class="nature-toggle" @click="natureExpanded = !natureExpanded">
            <span class="material-symbols-rounded" style="font-size: 18px">psychology</span>
            <span>性格選擇</span>
            <span class="nature-current-wrap">
              <span class="nature-current">{{ currentNatureLabelZh }}</span>
              <span v-if="!natureExpanded" class="nature-current-id">（{{ currentNature }}）</span>
            </span>
            <span class="material-symbols-rounded toggle-arrow" :class="{ expanded: natureExpanded }">expand_more</span>
          </button>
          <div v-show="natureExpanded" class="nature-body">
            <NatureGridSelector v-model="currentNature" />
          </div>
        </div>

        <p class="nature-effect-summary">{{ natureEffectSummary }}</p>

        <div class="ev-bar">
          <div class="ev-bar-info">
            <span>剩餘點數</span>
            <strong :class="{ 'ev-zero': remainingPoints === 0 }">{{ remainingPoints }}</strong>
            <span class="ev-total">/ {{ TOTAL_EV }}</span>
          </div>
          <div class="ev-track">
            <div class="ev-fill" :style="{ width: ((TOTAL_EV - remainingPoints) / TOTAL_EV * 100) + '%' }"></div>
          </div>
        </div>

        <ul class="stat-list">
          <li v-for="row in statRows" :key="row.key" class="stat-row">
            <div class="stat-head">
              <span class="stat-label-wrap">
                <span class="stat-label" :style="{ color: row.color }">{{ row.label }}</span>
                <span v-if="row.natureTrend === 'up'" class="nature-trend nature-trend-up" title="性格：此能力 ×1.1">
                  <span class="material-symbols-rounded">trending_up</span>
                </span>
                <span v-else-if="row.natureTrend === 'down'" class="nature-trend nature-trend-down" title="性格：此能力 ×0.9">
                  <span class="material-symbols-rounded">trending_down</span>
                </span>
              </span>
              <span class="stat-actual">{{ row.actual }}</span>
              <div class="stepper">
                <button
                  type="button"
                  class="step-btn step-bulk"
                  :disabled="(statPoints[row.key] || 0) <= 0"
                  @click="clearStatPoint(row.key)"
                  title="全部減點"
                >
                  <span class="bulk-label">−−</span>
                </button>
                <button
                  type="button"
                  class="step-btn"
                  :disabled="(statPoints[row.key] || 0) <= 0"
                  @click="adjustStatPoint(row.key, -1)"
                >
                  <span class="material-symbols-rounded">remove</span>
                </button>
                <span class="pts">{{ row.points }}</span>
                <button
                  type="button"
                  class="step-btn"
                  :disabled="remainingPoints <= 0 || (statPoints[row.key] || 0) >= MAX_PER"
                  @click="adjustStatPoint(row.key, 1)"
                >
                  <span class="material-symbols-rounded">add</span>
                </button>
                <button
                  type="button"
                  class="step-btn step-bulk"
                  :disabled="remainingPoints <= 0 || (statPoints[row.key] || 0) >= MAX_PER"
                  @click="maxStatPoint(row.key)"
                  title="全部加點"
                >
                  <span class="bulk-label">++</span>
                </button>
              </div>
            </div>
            <div class="bar-track">
              <div class="bar-composite" :style="{ width: row.barPct + '%' }">
                <div
                  class="bar-seg bar-seg-base"
                  :style="{
                    flex: `${row.barFlexBase} 1 0%`,
                    background: `linear-gradient(90deg, ${row.color}, ${row.color}88)`,
                  }"
                />
                <div
                  v-if="row.barPctPoints > 0"
                  class="bar-seg bar-seg-points"
                  :style="{
                    flex: `${row.barFlexPoints} 1 0%`,
                    background: `linear-gradient(90deg, ${row.pointsBarColor}, ${row.pointsBarColor}aa)`,
                  }"
                />
              </div>
            </div>
          </li>
        </ul>
      </section>

      <!-- Moves -->
      <section v-show="tab === 'moves'" class="panel">
        <p v-if="!learnedMoves.length" class="empty-hint">
          <span class="material-symbols-rounded">info</span>
          尚無可學招式資料
        </p>
        <div v-else class="moves-table-wrap">
          <table class="detail-moves-table">
            <thead>
              <tr>
                <th>招式名稱</th>
                <th>屬性</th>
                <th>分類</th>
                <th>威力</th>
                <th>命中</th>
                <th>PP</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="m in learnedMoves"
                :key="m.id ?? m.name"
                class="move-row-click"
                :class="{ 'move-row-selected': selectedMove && moveRowKey(selectedMove) === moveRowKey(m) }"
                @click="toggleMoveDetail(m)"
              >
                <td class="move-name-cell">{{ m.chineseName || m.displayName || m.name }}</td>
                <td>
                  <span v-if="typeNameOfMove(m)" :class="typeBadgeClasses(typeNameOfMove(m))">
                    {{ typeChineseOfMove(m) }}
                  </span>
                </td>
                <td>
                  <span v-if="m.category" :class="'category-badge ' + (m.category || '').toLowerCase()">
                    {{ categoryLabelZh(m.category) }}
                  </span>
                </td>
                <td class="num-cell">{{ m.power ?? '—' }}</td>
                <td class="num-cell">{{ m.accuracy != null ? m.accuracy + '%' : '—' }}</td>
                <td class="num-cell">{{ m.pp ?? '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Matchup -->
      <section v-show="tab === 'matchup'" class="panel">
        <template v-if="matchupSections.length">
          <div class="matchup-grid">
            <div v-for="sec in matchupSections" :key="sec.title" class="matchup-group">
              <h3 :class="['matchup-title', sec.cls]">{{ sec.title }}</h3>
              <div class="matchup-badges">
                <span v-for="item in sec.items" :key="item.type" :class="`type-badge ${item.type}`">
                  {{ item.chineseName || item.type }}
                </span>
              </div>
            </div>
          </div>
        </template>
        <template v-else-if="matchup">
          <pre class="raw-json">{{ JSON.stringify(matchup, null, 2) }}</pre>
        </template>
        <p v-else class="empty-hint">
          <span class="material-symbols-rounded">info</span>
          無相剋資料或 API 未回傳
        </p>
      </section>
    </template>

    <Teleport to="body">
      <div
        v-if="selectedMove"
        class="move-detail-backdrop"
        aria-modal="true"
        role="dialog"
        aria-labelledby="move-detail-title"
        @click.self="closeMoveDetail"
      >
        <div class="move-detail-modal" @click.stop>
          <button type="button" class="move-detail-close" aria-label="關閉" @click="closeMoveDetail">
            <span class="material-symbols-rounded">close</span>
          </button>

          <h2 id="move-detail-title" class="move-detail-title">
            {{ selectedMove.chineseName || selectedMove.displayName || selectedMove.name }}
          </h2>
          <p v-if="selectedMove.chineseName && selectedMove.displayName" class="move-detail-sub">
            {{ selectedMove.displayName }}
          </p>
          <p class="move-detail-slug mono">{{ selectedMove.name }}</p>

          <div class="move-detail-stats">
            <div v-if="typeNameOfMove(selectedMove)" class="move-detail-stat">
              <span class="move-detail-stat-label">屬性</span>
              <span :class="typeBadgeClasses(typeNameOfMove(selectedMove))">
                {{ typeChineseOfMove(selectedMove) }}
              </span>
            </div>
            <div v-if="selectedMove.category" class="move-detail-stat">
              <span class="move-detail-stat-label">分類</span>
              <span :class="'category-badge ' + (selectedMove.category || '').toLowerCase()">
                {{ categoryLabelZh(selectedMove.category) }}
              </span>
            </div>
            <div class="move-detail-stat">
              <span class="move-detail-stat-label">威力</span>
              <span class="move-detail-stat-val">{{ selectedMove.power ?? '—' }}</span>
            </div>
            <div class="move-detail-stat">
              <span class="move-detail-stat-label">命中</span>
              <span class="move-detail-stat-val">{{
                selectedMove.accuracy != null ? selectedMove.accuracy + '%' : '—'
              }}</span>
            </div>
            <div class="move-detail-stat">
              <span class="move-detail-stat-label">PP</span>
              <span class="move-detail-stat-val">{{ selectedMove.pp ?? '—' }}</span>
            </div>
          </div>

          <div v-if="selectedMove.chineseDescription || selectedMove.description" class="move-detail-desc-block">
            <h3 class="move-detail-desc-heading">效果說明</h3>
            <p class="move-detail-desc">{{ selectedMove.chineseDescription || selectedMove.description }}</p>
          </div>
          
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 24px;
  font-size: 0.88rem;
  color: var(--text-secondary);
  transition: color 0.2s;
}

.back-link:hover {
  color: var(--text-primary);
}

.back-link .material-symbols-rounded {
  font-size: 18px;
}

.detail-hero {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  align-items: center;
  margin-bottom: 28px;
  padding: 32px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: 20px;
  backdrop-filter: var(--glass-blur);
  animation: fadeIn 0.4s ease;
}

.hero-art-wrap {
  position: relative;
  width: 140px;
  height: 140px;
  flex-shrink: 0;
}

.hero-art-glow {
  position: absolute;
  inset: -20px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(230, 83, 79, 0.15), transparent 70%);
  animation: float 6s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.hero-art {
  width: 100%;
  height: 100%;
  object-fit: contain;
  position: relative;
  z-index: 1;
  filter: drop-shadow(0 8px 24px rgba(0, 0, 0, 0.4));
}

.hero-info {
  flex: 1;
  min-width: 200px;
}

.hero-dex {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--text-muted);
}

.hero-name {
  font-size: 2rem;
  font-weight: 800;
  margin: 4px 0;
  letter-spacing: -0.03em;
}

.hero-en {
  color: var(--text-muted);
  font-size: 0.9rem;
  margin-bottom: 12px;
}

.hero-types {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tab-bar {
  display: flex;
  gap: 6px;
  margin-bottom: 20px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border);
  background: var(--bg-card);
  color: var(--text-muted);
  padding: 10px 18px;
  border-radius: 12px;
  font-size: 0.85rem;
  font-weight: 600;
  white-space: nowrap;
  transition: all 0.2s;
}

.tab-icon {
  font-size: 18px;
}

.tab-btn:hover {
  color: var(--text-secondary);
  border-color: var(--border-light);
}

.tab-btn.active {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.panel {
  padding: 24px;
  border-radius: var(--radius);
  border: 1px solid var(--border);
  background: var(--bg-card);
  backdrop-filter: var(--glass-blur);
  animation: fadeIn 0.3s ease;
}

.stats-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.nature-collapse {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.nature-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 14px 18px;
  background: rgba(255, 255, 255, 0.03);
  color: var(--text-primary);
  font-size: 0.9rem;
  font-weight: 600;
  text-align: left;
  transition: background 0.15s;
}

.nature-toggle:hover {
  background: rgba(255, 255, 255, 0.06);
}

.nature-current-wrap {
  margin-left: auto;
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px 6px;
  max-width: 55%;
}

.nature-current {
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--text-primary);
}

.nature-current-id {
  font-size: 0.72rem;
  font-weight: 500;
  color: var(--text-muted);
}

.toggle-arrow {
  font-size: 20px;
  color: var(--text-muted);
  transition: transform 0.25s ease;
}

.toggle-arrow.expanded {
  transform: rotate(180deg);
}

.nature-body {
  padding: 16px 18px 18px;
  border-top: 1px solid var(--border);
  animation: fadeIn 0.2s ease;
}

.nature-effect-summary {
  margin: 0;
  padding: 10px 14px;
  font-size: 0.82rem;
  line-height: 1.5;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.ev-bar {
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.ev-bar-info {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 0.88rem;
  margin-bottom: 10px;
  color: var(--text-secondary);
}

.ev-bar-info strong {
  font-size: 1.2rem;
  color: var(--text-primary);
}

.ev-zero {
  color: var(--accent) !important;
}

.ev-total {
  color: var(--text-muted);
  font-size: 0.82rem;
}

.ev-track {
  height: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.ev-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--accent), #ffb86b);
  transition: width 0.3s ease;
}

.stat-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-row {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--border);
}

.stat-row:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.stat-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.stat-label-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.stat-label {
  font-weight: 700;
  min-width: 3em;
  font-size: 0.88rem;
}

.nature-trend {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nature-trend .material-symbols-rounded {
  font-size: 18px;
  font-variation-settings: 'FILL' 1;
}

.nature-trend-up {
  color: #4ade80;
}

.nature-trend-down {
  color: #f87171;
}

.stat-base {
  font-size: 0.75rem;
  color: var(--text-muted);
  min-width: 2em;
}

.stepper {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
}

.step-btn {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-secondary);
  transition: all 0.15s;
}

.step-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
  border-color: var(--border-light);
}

.step-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.step-btn .material-symbols-rounded {
  font-size: 16px;
}

.step-bulk {
  background: rgba(255, 255, 255, 0.02);
  border-color: rgba(255, 255, 255, 0.05);
}

.step-bulk:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.06);
  border-color: var(--accent);
}

.bulk-label {
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: -1px;
  line-height: 1;
}

.pts {
  min-width: 2em;
  text-align: center;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  font-size: 0.9rem;
}

.stat-actual {
  font-weight: 800;
  font-size: 1.1rem;
  min-width: 3em;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.bar-track {
  height: 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.bar-composite {
  display: flex;
  height: 100%;
  min-width: 0;
  border-radius: 999px;
  overflow: hidden;
  transition: width 0.3s ease;
}

.bar-seg {
  min-width: 0;
  height: 100%;
  transition: flex 0.25s ease;
}

.bar-seg-points {
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
}

.stat-formula {
  margin: 0;
  font-size: 0.75rem;
  color: var(--text-muted);
  text-align: center;
  padding-top: 4px;
}

.move-table-hint {
  margin: 0 0 10px;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.moves-table-wrap {
  overflow-x: auto;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
}

.move-row-click {
  cursor: pointer;
  transition: background 0.15s ease;
}

.move-row-selected {
  background: rgba(230, 83, 79, 0.08) !important;
  box-shadow: inset 0 0 0 1px rgba(230, 83, 79, 0.25);
}

.detail-moves-table {
  width: 100%;
  border-collapse: collapse;
  background: rgba(255, 255, 255, 0.02);
}

.detail-moves-table th {
  text-align: left;
  padding: 12px 14px;
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border-bottom: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.02);
}

.detail-moves-table td {
  padding: 10px 14px;
  font-size: 0.85rem;
  border-bottom: 1px solid var(--border);
  white-space: nowrap;
}

.detail-moves-table tr:last-child td {
  border-bottom: none;
}

.detail-moves-table tr:hover {
  background: rgba(255, 255, 255, 0.03);
}

.detail-moves-table .move-name-cell {
  font-weight: 600;
}

.detail-moves-table .num-cell {
  font-variant-numeric: tabular-nums;
  font-weight: 500;
  color: var(--text-secondary);
}

.move-detail-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  animation: fadeIn 0.2s ease;
}

.move-detail-modal {
  position: relative;
  width: 100%;
  max-width: 440px;
  max-height: min(88vh, 640px);
  overflow-y: auto;
  padding: 24px 22px 20px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background: var(--bg-secondary);
  box-shadow: var(--shadow-lg);
  animation: slideUp 0.25s ease;
}

.move-detail-close {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--text-secondary);
  transition: background 0.15s, color 0.15s;
}

.move-detail-close:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-primary);
}

.move-detail-title {
  font-size: 1.35rem;
  font-weight: 800;
  margin: 0 40px 6px 0;
  letter-spacing: -0.02em;
  line-height: 1.25;
}

.move-detail-sub {
  margin: 0 0 4px;
  font-size: 0.88rem;
  color: var(--text-muted);
}

.move-detail-slug {
  margin: 0 0 18px;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.move-detail-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px 16px;
  margin-bottom: 16px;
}

.move-detail-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.move-detail-stat-label {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.4px;
}

.move-detail-stat-val {
  font-size: 1rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.move-detail-meta {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin-bottom: 16px;
  line-height: 1.5;
}

.move-detail-meta-label {
  font-weight: 600;
  margin-right: 6px;
  color: var(--text-secondary);
}

.move-detail-meta-sep {
  margin: 0 6px;
}

.move-detail-desc-block {
  margin-bottom: 18px;
}

.move-detail-desc-heading {
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 0 0 8px;
}

.move-detail-desc {
  margin: 0;
  font-size: 0.9rem;
  color: var(--text-secondary);
  line-height: 1.65;
}

.move-detail-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.88rem;
  font-weight: 600;
  color: var(--accent);
  transition: opacity 0.15s;
}

.move-detail-link:hover {
  opacity: 0.9;
}

.matchup-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.matchup-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.matchup-title {
  font-size: 0.82rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.matchup-title.danger { color: #ff4444; }
.matchup-title.warn { color: #ffaa33; }
.matchup-title.good { color: #44bb66; }
.matchup-title.great { color: #22ddaa; }
.matchup-title.immune { color: #9999bb; }
.matchup-title.neutral-cls { color: var(--text-muted); }

.matchup-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.empty-hint {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-muted);
  font-size: 0.88rem;
}

.empty-hint .material-symbols-rounded {
  font-size: 18px;
}

.raw-json {
  margin: 0;
  padding: 16px;
  border-radius: var(--radius-sm);
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid var(--border);
  font-size: 0.78rem;
  overflow-x: auto;
  color: var(--text-muted);
  font-family: 'SF Mono', ui-monospace, monospace;
}

@media (max-width: 768px) {
  .detail-hero {
    padding: 24px;
    gap: 20px;
  }

  .hero-art-wrap {
    width: 100px;
    height: 100px;
  }

  .hero-name {
    font-size: 1.5rem;
  }

  .tab-bar {
    gap: 4px;
  }

  .tab-btn {
    padding: 8px 14px;
    font-size: 0.8rem;
  }

  .panel {
    padding: 18px;
  }
}
</style>
