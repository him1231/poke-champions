<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { pokemonRosterApi } from '../api/pokemonRoster'
import { moveRosterApi } from '../api/moveRoster'
import { itemRosterApi } from '../api/itemRoster'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { typeBadgeClasses } from '../utils/pokemonTypesDisplay'
import { localizedName, localizedTypeName, localizedDescription, localizedEffect } from '../utils/localizedName'
import NatureGridSelector from '../components/NatureGridSelector.vue'
import { NATURE_DEFS } from '../constants/pokemonNatures'
import {
  useTeamStore, ALL_TYPES, TYPE_ZH,
  STAT_KEYS, STAT_LABELS, STAT_COLORS, TOTAL_EV, MAX_PER, CATEGORY_LABELS,
  getDefenseMultipliers, calcStat,
} from '../composables/useTeamStore'
import { useLocalePath } from '../composables/useLocalePath'

const { t } = useI18n()
const { localePath } = useLocalePath()
const { teamMembers, clearSlot, pokemonDisplayName, refreshMemberData } = useTeamStore()

// ═══════════════════════════════════════════════════
//  資料載入
// ═══════════════════════════════════════════════════
const allPokemon = ref([])
const allMoves = ref([])
const allItems = ref([])
const dataLoading = ref(true)

onMounted(async () => {
  try {
    const [pokRes, movRes, itmRes] = await Promise.all([
      pokemonRosterApi.getPokemonList(),
      moveRosterApi.getMoves(),
      itemRosterApi.getItems(),
    ])
    allPokemon.value = pokRes.data || []
    allMoves.value = movRes.data || []
    allItems.value = itmRes.data || []

    await refreshMemberData(allPokemon.value)

    await Promise.all(
      teamMembers.map(async (member) => {
        if (!member.pokemon) return
        try {
          const [abRes, movRes] = await Promise.allSettled([
            pokemonRosterApi.getPokemonAbilities(member.pokemon.apiName),
            pokemonRosterApi.getPokemonMoves(member.pokemon.apiName),
          ])
          if (abRes.status === 'fulfilled') {
            const raw = abRes.value.data
            member._availableAbilities = Array.isArray(raw) ? raw : []
            const sel = member.ability
            if (sel?.name && member._availableAbilities.length) {
              const still = member._availableAbilities.find((x) => x.name === sel.name)
              member.ability = still || (member._availableAbilities.length === 1 ? member._availableAbilities[0] : null)
            } else if (member._availableAbilities.length === 1) {
              member.ability = member._availableAbilities[0]
            }
          }
          if (movRes.status === 'fulfilled') {
            const md = movRes.value.data
            member._learnableMoves = Array.isArray(md) ? md : []
          }
        } catch (e) {
          console.error('補齊隊伍成員招式/特性失敗', e)
        }
      }),
    )
  } catch (e) {
    console.error('TeamBuilder 資料載入失敗', e)
  } finally {
    dataLoading.value = false
  }
})

// ═══════════════════════════════════════════════════
//  編輯狀態
// ═══════════════════════════════════════════════════
const activeSlot = ref(0)
const active = computed(() => teamMembers[activeSlot.value])

// ═══════════════════════════════════════════════════
//  寶可夢搜尋 / 選擇
// ═══════════════════════════════════════════════════
const pokemonSearch = ref('')
const pokemonPickerOpen = ref(false)

const nonMegaPokemon = computed(() =>
  allPokemon.value.filter(p => !p.mega)
)

const filteredPokemon = computed(() => {
  const q = pokemonSearch.value.toLowerCase().trim()
  const list = nonMegaPokemon.value
  if (!q) return list
  return list.filter(p =>
    (p.displayName || '').toLowerCase().includes(q) ||
    (p.chineseName || '').includes(q) ||
    (p.apiName || '').toLowerCase().includes(q)
  )
})

async function selectPokemon(p) {
  const member = teamMembers[activeSlot.value]
  member.pokemon = p
  member.statPoints = { hp:0, attack:0, defense:0, specialAttack:0, specialDefense:0, speed:0 }
  member.nature = 'serious'
  member.moves = [null, null, null, null]
  member.heldItem = null
  member.ability = null
  member._availableAbilities = []
  pokemonPickerOpen.value = false
  pokemonSearch.value = ''

  const [typesRes, movesRes, abilitiesRes] = await Promise.allSettled([
    pokemonRosterApi.getPokemonTypes(p.apiName),
    pokemonRosterApi.getPokemonMoves(p.apiName),
    pokemonRosterApi.getPokemonAbilities(p.apiName),
  ])

  if (typesRes.status === 'fulfilled') {
    const td = typesRes.value.data
    const arr = Array.isArray(td) ? td : []
    member.types = arr.map((t) => t.type).filter(Boolean)
  } else {
    member.types = []
    console.error('載入屬性失敗', typesRes.reason)
  }

  if (movesRes.status === 'fulfilled') {
    const md = movesRes.value.data
    member._learnableMoves = Array.isArray(md) ? md : []
  } else {
    member._learnableMoves = []
    console.error('載入招式失敗', movesRes.reason)
  }

  if (abilitiesRes.status === 'fulfilled') {
    const ad = abilitiesRes.value.data
    member._availableAbilities = Array.isArray(ad) ? ad : []
    if (member._availableAbilities.length === 1) {
      member.ability = member._availableAbilities[0]
    }
  } else {
    member._availableAbilities = []
    console.error('載入特性失敗', abilitiesRes.reason)
  }
}

// ═══════════════════════════════════════════════════
//  能力值
// ═══════════════════════════════════════════════════
const remainingPoints = computed(() => {
  const pts = active.value.statPoints
  const used = STAT_KEYS.reduce((s, k) => s + (pts[k] || 0), 0)
  return TOTAL_EV - used
})

function adjustStatPoint(key, delta) {
  const pts = active.value.statPoints
  const cur = pts[key] || 0
  if (delta > 0) {
    if (remainingPoints.value <= 0 || cur >= MAX_PER) return
    pts[key] = cur + 1
  } else if (delta < 0) {
    if (cur <= 0) return
    pts[key] = cur - 1
  }
}

function maxStatPoint(key) {
  const pts = active.value.statPoints
  const cur = pts[key] || 0
  const add = Math.min(MAX_PER - cur, remainingPoints.value)
  if (add > 0) pts[key] = cur + add
}

function clearStatPoint(key) {
  active.value.statPoints[key] = 0
}

function natureTrendForStat(natureId, statKey) {
  if (statKey === 'hp') return 'neutral'
  const mult = NATURE_DEFS[natureId]?.mult?.[statKey]
  if (mult == null) return 'neutral'
  if (mult > 1) return 'up'
  if (mult < 1) return 'down'
  return 'neutral'
}

const statRows = computed(() => {
  const p = active.value.pokemon
  if (!p) return []
  const pts = active.value.statPoints
  const nature = active.value.nature

  return STAT_KEYS.map(key => {
    const base = Number(p[key] ?? 0)
    const actual = calcStat(p, key, pts, nature)
    const maxStat = 260
    const barPct = Math.min((actual / maxStat) * 100, 100)
    return {
      key,
      label: t('pokemon.stats.' + key),
      base,
      actual,
      barPct,
      color: STAT_COLORS[key],
      natureTrend: natureTrendForStat(nature, key),
    }
  })
})

// ═══════════════════════════════════════════════════
//  性格
// ═══════════════════════════════════════════════════
const natureExpanded = ref(false)

const currentNatureLabelZh = computed(() => {
  return t('pokemon.natures.' + active.value.nature)
})

const natureEffectSummary = computed(() => {
  const id = active.value.nature
  const def = NATURE_DEFS[id]
  if (!def) return ''
  const mult = def.mult || {}
  const keys = Object.keys(mult)
  const name = t('pokemon.natures.' + id)
  if (!keys.length) {
    return t('pokemon.natureNeutral', { name })
  }
  const upKeys = keys.filter((k) => mult[k] > 1)
  const downKeys = keys.filter((k) => mult[k] < 1)
  const upStr = upKeys.map((k) => t('pokemon.stats.' + k)).join('、')
  const downStr = downKeys.map((k) => t('pokemon.stats.' + k)).join('、')
  return t('pokemon.natureEffect', { name, up: upStr, down: downStr })
})

// ═══════════════════════════════════════════════════
//  招式選擇
// ═══════════════════════════════════════════════════
const movePickerSlot = ref(-1)
const moveSearch = ref('')

const learnableMoves = computed(() => active.value._learnableMoves || [])

const filteredMoves = computed(() => {
  const q = moveSearch.value.toLowerCase().trim()
  let list = learnableMoves.value
  if (q) {
    list = list.filter(m =>
      (m.chineseName || '').includes(q) ||
      (m.displayName || '').toLowerCase().includes(q) ||
      (m.name || '').toLowerCase().includes(q)
    )
  }
  return list.slice(0, 50)
})

function openMovePicker(slotIdx) {
  movePickerSlot.value = slotIdx
  moveSearch.value = ''
}

function selectMove(move) {
  if (movePickerSlot.value < 0) return
  active.value.moves[movePickerSlot.value] = move
  movePickerSlot.value = -1
}

function clearMove(slotIdx) {
  active.value.moves[slotIdx] = null
}

// ═══════════════════════════════════════════════════
//  持有物品
// ═══════════════════════════════════════════════════
const itemSearch = ref('')
const itemPickerOpen = ref(false)

const filteredItems = computed(() => {
  const q = itemSearch.value.toLowerCase().trim()
  if (!q) return allItems.value.slice(0, 40)
  return allItems.value.filter(i =>
    (i.chineseName || '').includes(q) ||
    (i.displayName || '').toLowerCase().includes(q) ||
    (i.name || '').toLowerCase().includes(q)
  ).slice(0, 40)
})

function selectItem(item) {
  active.value.heldItem = item
  itemPickerOpen.value = false
  itemSearch.value = ''
}

function clearItem() {
  active.value.heldItem = null
}

// ═══════════════════════════════════════════════════
//  隊伍防禦分析
// ═══════════════════════════════════════════════════
const teamDefenseAnalysis = computed(() => {
  const members = teamMembers.filter(m => m.pokemon)
  if (members.length === 0) return { perType: [], warnings: [], memberCount: 0 }

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

  const warnings = perType.filter(tp => tp.weakCount >= 2).sort((a, b) => b.weakCount - a.weakCount)
  return { perType, warnings, memberCount: members.length }
})

// ═══════════════════════════════════════════════════
//  分頁
// ═══════════════════════════════════════════════════
const editTab = ref('stats')
</script>

<template>
  <div class="container team-builder" v-if="!dataLoading">
    <div class="page-header">
      <h1 class="page-title">
        <span class="material-symbols-rounded" style="font-size:1.6rem;vertical-align:middle;margin-right:8px">groups</span>
        {{ t('teamBuilder.title') }}
      </h1>
      <router-link :to="localePath('/team-overview')" class="btn-overview" v-if="teamMembers.some(m => m.pokemon)">
        <span class="material-symbols-rounded">summarize</span>
        {{ t('teamBuilder.viewOverview') }}
      </router-link>
    </div>

    <!-- ═══ 頂部 6 Slot 卡片 ═══ -->
    <section class="team-slots">
      <div
        v-for="(member, idx) in teamMembers"
        :key="idx"
        class="slot-card"
        :class="{ active: activeSlot === idx, empty: !member.pokemon }"
        @click="activeSlot = idx"
      >
        <template v-if="member.pokemon">
          <img
            :src="getPokemonImageUrl(member.pokemon)"
            :alt="pokemonDisplayName(member.pokemon)"
            class="slot-img"
          />
          <span class="slot-name">{{ pokemonDisplayName(member.pokemon) }}</span>
          <div class="slot-types">
            <span
              v-for="tp in member.types"
              :key="tp"
              :class="typeBadgeClasses(tp)"
              class="slot-type-badge"
            >{{ t('pokemon.types.' + tp) }}</span>
          </div>
          <span v-if="member.ability" class="slot-ability">{{ localizedName(member.ability) }}</span>
          <button class="slot-clear" @click.stop="clearSlot(idx)" :title="t('common.remove')">
            <span class="material-symbols-rounded">close</span>
          </button>
        </template>
        <template v-else>
          <span class="material-symbols-rounded slot-plus">add</span>
          <span class="slot-label">{{ t('teamBuilder.position', { n: idx + 1 }) }}</span>
        </template>
      </div>
    </section>

    <!-- ═══ 主內容區：左側編輯 + 右側分析 ═══ -->
    <div class="builder-body">
      <!-- 左側 -->
      <section class="editor-panel">
        <template v-if="active.pokemon">
          <!-- 寶可夢基本資訊 -->
          <div class="editor-header">
            <img :src="getPokemonImageUrl(active.pokemon)" class="editor-avatar" />
            <div>
              <h2 class="editor-pokemon-name">{{ pokemonDisplayName(active.pokemon) }}</h2>
              <div class="editor-types">
                <span v-for="tp in active.types" :key="tp" :class="typeBadgeClasses(tp)">{{ t('pokemon.types.' + tp) }}</span>
              </div>
            </div>
            <button class="btn-change-pokemon" @click="pokemonPickerOpen = true">
              <span class="material-symbols-rounded">swap_horiz</span>
              {{ t('common.change') }}
            </button>
          </div>

          <!-- 特性選擇 -->
          <div v-if="active._availableAbilities && active._availableAbilities.length" class="ability-section">
            <span class="ability-section-label">{{ t('teamBuilder.ability') }}</span>
            <div class="ability-options">
              <button
                v-for="a in active._availableAbilities"
                :key="`${a.name}-${a.slot ?? ''}`"
                class="ability-option"
                :class="{ selected: active.ability && active.ability.name === a.name }"
                @click="active.ability = a"
                :title="localizedDescription(a)"
              >
                {{ localizedName(a) }}
              </button>
            </div>
          </div>

          <!-- 編輯分頁 -->
          <nav class="edit-tabs">
            <button
              v-for="tab in [{id:'stats',label:t('teamBuilder.tabs.stats'),icon:'bar_chart'},{id:'moves',label:t('teamBuilder.tabs.moves'),icon:'bolt'},{id:'item',label:t('teamBuilder.tabs.items'),icon:'inventory_2'}]"
              :key="tab.id"
              class="edit-tab"
              :class="{ active: editTab === tab.id }"
              @click="editTab = tab.id"
            >
              <span class="material-symbols-rounded">{{ tab.icon }}</span>
              {{ tab.label }}
            </button>
          </nav>

          <!-- TAB: 能力值 -->
          <div v-if="editTab === 'stats'" class="tab-panel">
            <!-- 性格 -->
            <div class="nature-section">
              <button class="nature-toggle" @click="natureExpanded = !natureExpanded">
                <span class="nature-toggle-label">{{ t('teamBuilder.nature') }}</span>
                <span class="nature-current-tag">{{ currentNatureLabelZh }}</span>
                <span class="nature-effect-tag">{{ natureEffectSummary }}</span>
                <span class="material-symbols-rounded toggle-arrow" :class="{ open: natureExpanded }">expand_more</span>
              </button>
              <div v-show="natureExpanded" class="nature-body">
                <NatureGridSelector v-model="active.nature" />
              </div>
            </div>

            <!-- 剩餘點數 -->
            <div class="ev-remaining">
              {{ t('teamBuilder.remainingPoints') }}<strong :class="{ zero: remainingPoints === 0 }">{{ remainingPoints }}</strong> / {{ TOTAL_EV }}
            </div>

            <!-- 能力值條 -->
            <div class="stat-rows">
              <div v-for="row in statRows" :key="row.key" class="stat-row">
                <span class="stat-label-wrap">
                  <span class="stat-label" :style="{ color: row.color }">{{ row.label }}</span>
                  <span v-if="row.natureTrend === 'up'" class="nature-trend nature-trend-up" :title="t('pokemonDetail.natureUp')">
                    <span class="material-symbols-rounded">trending_up</span>
                  </span>
                  <span v-else-if="row.natureTrend === 'down'" class="nature-trend nature-trend-down" :title="t('pokemonDetail.natureDown')">
                    <span class="material-symbols-rounded">trending_down</span>
                  </span>
                </span>
                <span class="stat-base"></span>
                <div class="stat-bar-track">
                  <div class="stat-bar-fill" :style="{ width: row.barPct + '%', background: row.color }"></div>
                </div>
                <span class="stat-actual">{{ row.actual }}</span>
                <div class="stat-stepper">
                  <button class="step-btn" @click="clearStatPoint(row.key)" :title="t('pokemonDetail.clearAll')">--</button>
                  <button class="step-btn" @click="adjustStatPoint(row.key, -1)">−</button>
                  <span class="step-val">{{ active.statPoints[row.key] }}</span>
                  <button class="step-btn" @click="adjustStatPoint(row.key, 1)">+</button>
                  <button class="step-btn" @click="maxStatPoint(row.key)" :title="t('pokemonDetail.fillAll')">++</button>
                </div>
              </div>
            </div>
          </div>

          <!-- TAB: 招式 -->
          <div v-if="editTab === 'moves'" class="tab-panel">
            <div class="move-slots">
              <div v-for="(move, mi) in active.moves" :key="mi" class="move-slot" @click="openMovePicker(mi)">
                <template v-if="move">
                  <span :class="typeBadgeClasses(move.type || move.typeName)" class="move-type-dot"></span>
                  <div class="move-slot-info">
                    <span class="move-slot-name">{{ localizedName(move) }}</span>
                    <span class="move-slot-meta">
                      {{ move.category ? t('pokemon.categories.' + move.category) : '' }}
                      <template v-if="move.power">{{ t('teamBuilder.movePower', { power: move.power }) }}</template>
                    </span>
                  </div>
                  <button class="move-slot-clear" @click.stop="clearMove(mi)">
                    <span class="material-symbols-rounded">close</span>
                  </button>
                </template>
                <template v-else>
                  <span class="material-symbols-rounded move-slot-empty-icon">add</span>
                  <span class="move-slot-empty-text">{{ t('teamBuilder.moveSlot', { n: mi + 1 }) }}</span>
                </template>
              </div>
            </div>

            <!-- 招式選擇器 -->
            <div v-if="movePickerSlot >= 0" class="picker-panel">
              <div class="picker-header">
                <h3>{{ t('teamBuilder.selectMove', { n: movePickerSlot + 1 }) }}</h3>
                <button class="picker-close" @click="movePickerSlot = -1">
                  <span class="material-symbols-rounded">close</span>
                </button>
              </div>
              <input
                v-model="moveSearch"
                class="picker-search"
                :placeholder="t('teamBuilder.searchMove')"
                autofocus
              />
              <div class="picker-list">
                <div
                  v-for="m in filteredMoves"
                  :key="m.name"
                  class="picker-item"
                  @click="selectMove(m)"
                >
                  <span :class="typeBadgeClasses(m.type || m.typeName)" class="picker-item-type">{{ t('pokemon.types.' + (m.type || m.typeName)) }}</span>
                  <span class="picker-item-name">{{ localizedName(m) }}</span>
                  <span class="picker-item-cat">{{ m.category ? t('pokemon.categories.' + m.category) : '' }}</span>
                  <span class="picker-item-power">{{ m.power ?? '—' }}</span>
                  <span class="picker-item-pp">PP {{ m.pp ?? '—' }}</span>
                </div>
                <div v-if="filteredMoves.length === 0" class="picker-empty">
                  {{ learnableMoves.length === 0 ? t('teamBuilder.noLearnableMoves') : t('common.noResults') }}
                </div>
              </div>
            </div>
          </div>

          <!-- TAB: 持有物 -->
          <div v-if="editTab === 'item'" class="tab-panel">
            <div class="item-current" v-if="active.heldItem">
              <div class="item-current-info">
                <span class="item-current-name">{{ localizedName(active.heldItem) }}</span>
                <span class="item-current-en">{{ active.heldItem.displayName }}</span>
                <p class="item-current-effect">{{ localizedEffect(active.heldItem) }}</p>
              </div>
              <button class="item-clear-btn" @click="clearItem">
                <span class="material-symbols-rounded">delete</span>
              </button>
            </div>
            <div v-else class="item-none">{{ t('teamBuilder.noItem') }}</div>

            <button class="btn-pick-item" @click="itemPickerOpen = true">
              <span class="material-symbols-rounded">search</span>
              {{ active.heldItem ? t('teamBuilder.changeItem') : t('teamBuilder.selectItem') }}
            </button>

            <div v-if="itemPickerOpen" class="picker-panel">
              <div class="picker-header">
                <h3>{{ t('teamBuilder.selectItemTitle') }}</h3>
                <button class="picker-close" @click="itemPickerOpen = false">
                  <span class="material-symbols-rounded">close</span>
                </button>
              </div>
              <input
                v-model="itemSearch"
                class="picker-search"
                :placeholder="t('teamBuilder.searchItem')"
                autofocus
              />
              <div class="picker-list">
                <div
                  v-for="item in filteredItems"
                  :key="item.name"
                  class="picker-item item-picker-row"
                  @click="selectItem(item)"
                >
                  <span class="picker-item-name">{{ localizedName(item) }}</span>
                  <span class="picker-item-cat item-cat-tag">{{ item.category }}</span>
                </div>
                <div v-if="filteredItems.length === 0" class="picker-empty">{{ t('common.noResults') }}</div>
              </div>
            </div>
          </div>
        </template>

        <!-- 空位：寶可夢選擇 -->
        <template v-else>
          <div class="empty-editor">
            <button class="btn-add-pokemon" @click="pokemonPickerOpen = true">
              <span class="material-symbols-rounded">add_circle</span>
              {{ t('teamBuilder.selectPokemon') }}
            </button>
          </div>
        </template>
      </section>

      <!-- 右側分析區 -->
      <aside class="synergy-panel">
        <h3 class="synergy-title">
          <span class="material-symbols-rounded">analytics</span>
          {{ t('teamBuilder.defenseAnalysis') }}
        </h3>

        <template v-if="teamDefenseAnalysis.memberCount === 0">
          <p class="synergy-empty">{{ t('teamBuilder.addPokemonFirst') }}</p>
        </template>
        <template v-else>
          <!-- 警告 -->
          <div v-if="teamDefenseAnalysis.warnings.length" class="synergy-warnings">
            <div v-for="w in teamDefenseAnalysis.warnings" :key="w.type" class="synergy-warn-item">
              <span class="material-symbols-rounded warn-icon">warning</span>
              <span>{{ t('teamBuilder.teamWeakness', { count: w.weakCount }) }}
                <span :class="typeBadgeClasses(w.type)" class="warn-type-badge">{{ w.typeZh }}</span>
              </span>
            </div>
          </div>
          <div v-else class="synergy-ok">
            <span class="material-symbols-rounded">check_circle</span>
            {{ t('teamBuilder.noCommonWeakness') }}
          </div>

          <!-- 完整表 -->
          <div class="synergy-grid">
            <div
              v-for="entry in teamDefenseAnalysis.perType"
              :key="entry.type"
              class="synergy-cell"
              :class="{
                'cell-danger': entry.weakCount >= 2,
                'cell-warn': entry.weakCount === 1 && entry.resistCount === 0 && entry.immuneCount === 0,
                'cell-good': entry.resistCount >= 2 || entry.immuneCount >= 1,
              }"
            >
              <span :class="typeBadgeClasses(entry.type)" class="synergy-type-badge">{{ entry.typeZh }}</span>
              <div class="synergy-counts">
                <span v-if="entry.weakCount" class="cnt cnt-weak" :title="t('teamBuilder.weakCount', { count: entry.weakCount })">{{ entry.weakCount }}{{ t('teamBuilder.weak') }}</span>
                <span v-if="entry.resistCount" class="cnt cnt-resist" :title="t('teamBuilder.resistCount', { count: entry.resistCount })">{{ entry.resistCount }}{{ t('teamBuilder.resist') }}</span>
                <span v-if="entry.immuneCount" class="cnt cnt-immune" :title="t('teamBuilder.immuneCount', { count: entry.immuneCount })">{{ entry.immuneCount }}{{ t('teamBuilder.immune') }}</span>
              </div>
            </div>
          </div>
        </template>
      </aside>
    </div>

    <!-- ═══ 寶可夢選擇彈窗 ═══ -->
    <Teleport to="body">
      <div v-if="pokemonPickerOpen" class="modal-backdrop" @click.self="pokemonPickerOpen = false">
        <div class="modal-content pokemon-picker-modal">
          <div class="picker-header">
            <h3>{{ t('teamBuilder.selectPokemon') }}</h3>
            <button class="picker-close" @click="pokemonPickerOpen = false">
              <span class="material-symbols-rounded">close</span>
            </button>
          </div>
          <input
            v-model="pokemonSearch"
            class="picker-search"
            :placeholder="t('teamBuilder.searchPokemon')"
            autofocus
          />
          <div class="picker-list pokemon-picker-list">
            <div
              v-for="p in filteredPokemon"
              :key="p.apiName"
              class="picker-item pokemon-picker-item"
              @click="selectPokemon(p)"
            >
              <img :src="getPokemonImageUrl(p)" class="picker-pokemon-img" />
              <div class="picker-pokemon-info">
                <span class="picker-pokemon-name">{{ localizedName(p) }}</span>
                <span class="picker-pokemon-sub">{{ p.displayName }}</span>
              </div>
            </div>
            <div v-if="filteredPokemon.length === 0" class="picker-empty">{{ t('common.noResults') }}</div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>

  <div v-else class="loading">{{ t('common.loading') }}</div>
</template>

<style scoped>
/* ═══ 頁首 ═══ */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}

.page-header .page-title { margin-bottom: 0; }

.btn-overview {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--accent);
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 0.88rem;
  font-weight: 600;
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-overview:hover {
  background: var(--accent-hover);
  box-shadow: 0 4px 16px var(--accent-glow);
}

.btn-overview .material-symbols-rounded { font-size: 18px; }

/* ═══ 頂部 Slots ═══ */
.team-slots {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-bottom: 28px;
}

.slot-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 16px 8px 12px;
  background: var(--bg-card);
  border: 2px solid transparent;
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.2s ease;
  min-height: 120px;
  justify-content: center;
}

.slot-card:hover {
  background: var(--bg-card-hover);
  border-color: var(--border-light);
}

.slot-card.active {
  border-color: var(--accent);
  box-shadow: 0 0 20px var(--accent-glow);
}

.slot-card.empty {
  border-style: dashed;
  border-color: var(--border-light);
}

.slot-img {
  width: 52px;
  height: 52px;
  object-fit: contain;
  image-rendering: pixelated;
}

.slot-name {
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--text-primary);
  text-align: center;
  line-height: 1.2;
}

.slot-types {
  display: flex;
  gap: 3px;
}

.slot-type-badge {
  font-size: 0.58rem !important;
  padding: 1px 6px !important;
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
  border-radius: 50%;
  background: rgba(255,60,60,0.15);
  color: var(--danger);
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.15s;
}

.slot-card:hover .slot-clear { opacity: 1; }

.slot-clear .material-symbols-rounded { font-size: 14px; }

.slot-plus {
  font-size: 32px;
  color: var(--text-muted);
}

.slot-label {
  font-size: 0.72rem;
  color: var(--text-muted);
}

/* ═══ 主內容 ═══ */
.builder-body {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: 24px;
  align-items: start;
}

/* ═══ 左側編輯面板 ═══ */
.editor-panel {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 24px;
  min-height: 400px;
}

.editor-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.editor-avatar {
  width: 72px;
  height: 72px;
  object-fit: contain;
  image-rendering: pixelated;
  background: var(--bg-glass);
  border-radius: var(--radius-sm);
  padding: 4px;
}

.editor-pokemon-name {
  font-size: 1.35rem;
  font-weight: 700;
  margin-bottom: 4px;
}

.editor-types {
  display: flex;
  gap: 6px;
}

.btn-change-pokemon {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.82rem;
  font-weight: 500;
  transition: all 0.2s;
}

.btn-change-pokemon:hover {
  background: var(--bg-elevated);
  color: var(--text-primary);
}

/* ═══ 特性選擇 ═══ */
.ability-section {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 10px 14px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
}

.ability-section-label {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-secondary);
  flex-shrink: 0;
}

.ability-options {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.ability-option {
  padding: 5px 14px;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 500;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid var(--border);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.2s;
}

.ability-option:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-primary);
  border-color: var(--border-light);
}

.ability-option.selected {
  background: var(--accent-soft);
  border-color: var(--accent);
  color: var(--accent);
  font-weight: 600;
}

.slot-ability {
  font-size: 0.64rem;
  color: var(--text-muted);
  text-align: center;
  line-height: 1.2;
}

/* ═══ 編輯分頁 ═══ */
.edit-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 12px;
}

.edit-tab {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-size: 0.84rem;
  font-weight: 500;
  color: var(--text-muted);
  background: none;
  transition: all 0.2s;
}

.edit-tab:hover { color: var(--text-secondary); background: var(--bg-glass); }
.edit-tab.active { color: var(--accent); background: var(--accent-soft); }
.edit-tab .material-symbols-rounded { font-size: 18px; }

/* ═══ 性格 ═══ */
.nature-section {
  margin-bottom: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.nature-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 14px;
  background: var(--bg-glass);
  color: var(--text-primary);
  font-size: 0.84rem;
}

.nature-toggle-label { font-weight: 600; }

.nature-current-tag {
  padding: 2px 10px;
  border-radius: 20px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 0.78rem;
  font-weight: 600;
}

.nature-effect-tag {
  color: var(--text-muted);
  font-size: 0.76rem;
  margin-left: auto;
}

.toggle-arrow {
  transition: transform 0.2s;
  font-size: 20px;
}

.toggle-arrow.open { transform: rotate(180deg); }

.nature-body {
  padding: 12px;
  border-top: 1px solid var(--border);
}

/* ═══ EV ═══ */
.ev-remaining {
  font-size: 0.82rem;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.ev-remaining strong { color: var(--accent); }
.ev-remaining strong.zero { color: var(--text-muted); }

.stat-rows { display: flex; flex-direction: column; gap: 8px; }

.stat-row {
  display: grid;
  grid-template-columns: minmax(52px, auto) 34px 1fr 38px auto;
  align-items: center;
  gap: 8px;
}

.stat-label-wrap {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.stat-label { font-size: 0.78rem; font-weight: 600; }

.nature-trend {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nature-trend .material-symbols-rounded {
  font-size: 16px;
  font-variation-settings: 'FILL' 1;
}

.nature-trend-up { color: #4ade80; }

.nature-trend-down { color: #f87171; }
.stat-base { font-size: 0.72rem; color: var(--text-muted); text-align: right; }

.stat-bar-track {
  height: 10px;
  background: rgba(255,255,255,0.05);
  border-radius: 5px;
  overflow: hidden;
}

.stat-bar-fill {
  height: 100%;
  border-radius: 5px;
  transition: width 0.3s ease;
}

.stat-actual {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--text-primary);
  text-align: right;
}

.stat-stepper {
  display: flex;
  align-items: center;
  gap: 3px;
}

.step-btn {
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  color: var(--text-secondary);
  font-size: 0.72rem;
  font-weight: 700;
  transition: all 0.15s;
}

.step-btn:hover { background: var(--bg-elevated); color: var(--text-primary); }

.step-val {
  width: 26px;
  text-align: center;
  font-size: 0.78rem;
  font-weight: 600;
  color: var(--accent);
}

/* ═══ 招式插槽 ═══ */
.move-slots {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 16px;
}

.move-slot {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.2s;
  min-height: 60px;
}

.move-slot:hover {
  border-color: var(--border-light);
  background: var(--bg-elevated);
}

.move-type-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  padding: 0 !important;
  font-size: 0 !important;
}

.move-slot-info { flex: 1; min-width: 0; }

.move-slot-name {
  display: block;
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.move-slot-meta {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.move-slot-clear {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(255,60,60,0.1);
  color: var(--danger);
}

.move-slot-clear .material-symbols-rounded { font-size: 14px; }

.move-slot-empty-icon {
  font-size: 24px;
  color: var(--text-muted);
}

.move-slot-empty-text {
  font-size: 0.8rem;
  color: var(--text-muted);
}

/* ═══ 持有物 ═══ */
.item-current {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  margin-bottom: 12px;
}

.item-current-name {
  display: block;
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--text-primary);
}

.item-current-en {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.item-current-effect {
  margin-top: 6px;
  font-size: 0.78rem;
  color: var(--text-secondary);
  line-height: 1.5;
}

.item-current-info { flex: 1; }

.item-clear-btn {
  padding: 6px;
  border-radius: 8px;
  background: rgba(255,60,60,0.1);
  color: var(--danger);
}

.item-none {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 0.88rem;
}

.btn-pick-item {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 10px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.84rem;
  font-weight: 500;
  justify-content: center;
  transition: all 0.2s;
}

.btn-pick-item:hover {
  background: var(--bg-elevated);
  color: var(--text-primary);
}

/* ═══ Picker 共用 ═══ */
.picker-panel {
  margin-top: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-secondary);
  overflow: hidden;
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
}

.picker-header h3 {
  font-size: 0.88rem;
  font-weight: 600;
}

.picker-close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: none;
  color: var(--text-muted);
}

.picker-close:hover { background: var(--bg-glass); color: var(--text-primary); }
.picker-close .material-symbols-rounded { font-size: 18px; }

.picker-search {
  width: 100%;
  padding: 10px 14px;
  background: var(--bg-glass);
  border: none;
  border-bottom: 1px solid var(--border);
  color: var(--text-primary);
  font-size: 0.84rem;
  outline: none;
}

.picker-search::placeholder { color: var(--text-muted); }

.picker-list {
  max-height: 280px;
  overflow-y: auto;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  cursor: pointer;
  transition: background 0.15s;
  font-size: 0.82rem;
}

.picker-item:hover { background: var(--bg-glass); }

.picker-item-type {
  font-size: 0.62rem !important;
  padding: 1px 8px !important;
  flex-shrink: 0;
}

.picker-item-name { flex: 1; font-weight: 500; color: var(--text-primary); }
.picker-item-cat { color: var(--text-muted); font-size: 0.72rem; }
.picker-item-power { color: var(--text-secondary); font-size: 0.72rem; width: 28px; text-align: right; }
.picker-item-pp { color: var(--text-muted); font-size: 0.68rem; width: 42px; }

.picker-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
  font-size: 0.82rem;
}

.item-cat-tag {
  font-size: 0.66rem !important;
  background: var(--bg-glass);
  padding: 2px 8px;
  border-radius: 10px;
  color: var(--text-muted);
}

/* ═══ 彈窗 ═══ */
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0,0,0,0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal-content {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  width: 100%;
  max-width: 480px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: var(--shadow-lg);
}

.pokemon-picker-list { max-height: 60vh; }

.pokemon-picker-item {
  gap: 12px;
  padding: 10px 16px;
}

.picker-pokemon-img {
  width: 44px;
  height: 44px;
  object-fit: contain;
  image-rendering: pixelated;
}

.picker-pokemon-info { display: flex; flex-direction: column; }
.picker-pokemon-name { font-weight: 600; font-size: 0.88rem; color: var(--text-primary); }
.picker-pokemon-sub { font-size: 0.72rem; color: var(--text-muted); }

/* ═══ 空位編輯 ═══ */
.empty-editor {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

.btn-add-pokemon {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 28px;
  background: var(--accent-soft);
  border: 2px dashed var(--accent);
  border-radius: var(--radius);
  color: var(--accent);
  font-size: 1rem;
  font-weight: 600;
  transition: all 0.2s;
}

.btn-add-pokemon:hover {
  background: var(--accent);
  color: #fff;
  border-style: solid;
}

/* ═══ 右側 Synergy ═══ */
.synergy-panel {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
  position: sticky;
  top: 88px;
}

.synergy-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1rem;
  font-weight: 700;
  margin-bottom: 16px;
}

.synergy-title .material-symbols-rounded { font-size: 22px; color: var(--accent); }

.synergy-empty {
  text-align: center;
  color: var(--text-muted);
  padding: 20px;
  font-size: 0.84rem;
}

.synergy-warnings {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.synergy-warn-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: rgba(255, 60, 60, 0.08);
  border: 1px solid rgba(255, 60, 60, 0.2);
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
  color: #ff8888;
  line-height: 1.4;
}

.warn-icon { font-size: 18px; color: var(--danger); flex-shrink: 0; }

.warn-type-badge {
  font-size: 0.62rem !important;
  padding: 1px 8px !important;
  vertical-align: middle;
}

.synergy-ok {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: rgba(98, 188, 90, 0.08);
  border: 1px solid rgba(98, 188, 90, 0.2);
  border-radius: var(--radius-sm);
  font-size: 0.82rem;
  color: #8ce68c;
  margin-bottom: 16px;
}

.synergy-ok .material-symbols-rounded { font-size: 20px; }

.synergy-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}

.synergy-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 4px;
  border-radius: var(--radius-xs);
  background: var(--bg-glass);
  border: 1px solid transparent;
  transition: all 0.15s;
}

.synergy-cell.cell-danger {
  background: rgba(255, 60, 60, 0.1);
  border-color: rgba(255, 60, 60, 0.25);
}

.synergy-cell.cell-good {
  background: rgba(98, 188, 90, 0.06);
  border-color: rgba(98, 188, 90, 0.15);
}

.synergy-type-badge {
  font-size: 0.56rem !important;
  padding: 1px 7px !important;
}

.synergy-counts {
  display: flex;
  gap: 3px;
  flex-wrap: wrap;
  justify-content: center;
}

.cnt {
  font-size: 0.62rem;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
}

.cnt-weak { background: rgba(255,60,60,0.15); color: #ff8888; }
.cnt-resist { background: rgba(74,144,217,0.15); color: #88bbff; }
.cnt-immune { background: rgba(180,180,180,0.12); color: #aaa; }

/* ═══ 響應式 ═══ */
@media (max-width: 1024px) {
  .builder-body {
    grid-template-columns: 1fr;
  }

  .synergy-panel {
    position: static;
  }
}

@media (max-width: 768px) {
  .team-slots {
    grid-template-columns: repeat(3, 1fr);
  }

  .move-slots {
    grid-template-columns: 1fr;
  }

  .stat-row {
    grid-template-columns: minmax(48px, auto) 30px 1fr 34px;
    gap: 6px;
  }

  .stat-stepper {
    grid-column: 1 / -1;
    justify-content: center;
    margin-top: 2px;
  }

  .synergy-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
