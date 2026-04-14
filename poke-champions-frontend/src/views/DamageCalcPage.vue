<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { pokemonRosterApi } from '../api/pokemonRoster'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { localizedName } from '../utils/localizedName'
import { comparePokemonByFormId } from '../utils/pokemonSort'
import { getDefenseMultipliers } from '../composables/useTeamStore'
import {
  calcDamageRange, isStatusLikeMove, computeModifiers, rankMultiplier,
  stealthRockDamage,
  ATK_ABILITIES, DEF_ABILITIES, TERRAINS, WEATHERS, SCREENS,
  ATK_ITEMS, DEF_ITEMS, STATUS_CONDITIONS,
} from '../utils/damageCalc'

const EV_PRESETS = [0, 4, 12, 20, 32]
const ATTACKER_EV_KEYS = ['attack', 'specialAttack']

const RANK_OPTIONS = [-6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6]
const TOTAL_EV = 66
const MAX_PER = 32

const { t } = useI18n()

const loading = ref(true)
const allPokemon = ref([])
const learnableMoves = ref([])

function freshStatPoints() {
  return { hp: 0, attack: 0, defense: 0, specialAttack: 0, specialDefense: 0, speed: 0 }
}

const attacker = reactive({
  pokemon: null, types: [],
  natureBoost: { attack: 0, specialAttack: 0 },
  statPoints: freshStatPoints(),
  ability: null, item: null, rank: 0,
  weather: null, terrain: null, crit: false, status: null,
})

const defender = reactive({
  pokemon: null, types: [],
  natureBoost: { defense: 0, specialDefense: 0 },
  statPoints: freshStatPoints(),
  ability: null, item: null, rank: 0,
  screen: null, doubleScreen: false, spread: false, stealthRock: false,
})

const selectedMove = ref(null)
const moveSearch = ref('')
const pokePickerOpen = ref(false)
const pokePickerTarget = ref('attacker')
const pokeSearch = ref('')

const baseForms = computed(() =>
  allPokemon.value.filter((p) => !p.mega && !p.isMega).sort(comparePokemonByFormId),
)

const filteredPokemon = computed(() => {
  const q = pokeSearch.value.toLowerCase().trim()
  const list = baseForms.value
  if (!q) return list
  return list.filter(
    (p) =>
      (p.displayName || '').toLowerCase().includes(q) ||
      (p.chineseName || '').toLowerCase().includes(q) ||
      (p.japaneseName || '').toLowerCase().includes(q) ||
      (p.apiName || '').toLowerCase().includes(q),
  )
})

const selectedMoveIsPhysical = computed(() => {
  const m = selectedMove.value
  if (!m) return null
  return String(m.category || 'PHYSICAL').toUpperCase() === 'PHYSICAL'
})

const activeDefKey = computed(() => {
  if (selectedMoveIsPhysical.value === null) return 'defense'
  return selectedMoveIsPhysical.value ? 'defense' : 'specialDefense'
})

const defenderEvKeys = computed(() => ['hp', activeDefKey.value])

const filteredMoves = computed(() => {
  let list = learnableMoves.value || []
  const q = moveSearch.value.toLowerCase().trim()
  if (q) {
    list = list.filter(
      (m) =>
        (m.displayName || '').toLowerCase().includes(q) ||
        (m.name || '').toLowerCase().includes(q) ||
        (m.apiName || '').toLowerCase().includes(q),
    )
  }
  return list.filter((m) => !isStatusLikeMove(m))
})

watch(
  () => attacker.pokemon?.apiName,
  async (apiName) => {
    learnableMoves.value = []
    selectedMove.value = null
    moveSearch.value = ''
    if (!apiName) return
    try {
      const { data } = await pokemonRosterApi.getPokemonMoves(apiName)
      learnableMoves.value = Array.isArray(data) ? data : []
    } catch { learnableMoves.value = [] }
  },
  { immediate: true },
)

function normalizeTypesFromPokemon(p) {
  if (!p) return []
  if (Array.isArray(p.types) && p.types.length && typeof p.types[0] === 'string')
    return p.types.map((x) => String(x).toLowerCase()).filter(Boolean)
  const info = p.typesInfo
  if (Array.isArray(info))
    return info.map((x) => (typeof x === 'string' ? x : x?.type?.name || x?.name || ''))
      .map((x) => String(x).toLowerCase()).filter(Boolean)
  return []
}

async function applyPokemon(side, p) {
  const box = side === 'attacker' ? attacker : defender
  box.pokemon = p
  box.types = normalizeTypesFromPokemon(p)
  if (!box.types.length && p?.apiName) {
    try {
      const { data } = await pokemonRosterApi.getPokemonTypes(p.apiName)
      const info = data?.typesInfo || data?.types || (Array.isArray(data) ? data : null)
      if (Array.isArray(info) && info.length)
        box.types = info.map((x) => (typeof x === 'string' ? x : x?.type?.name || x?.name || ''))
          .map((x) => String(x).toLowerCase()).filter(Boolean)
    } catch { /* ignore */ }
  }
  if (side === 'attacker') selectedMove.value = null
}

function openPokePicker(target) {
  pokePickerTarget.value = target
  pokePickerOpen.value = true
  pokeSearch.value = ''
}
function closePokePicker() { pokePickerOpen.value = false }

async function pickPokemon(p) {
  await applyPokemon(pokePickerTarget.value, p)
  closePokePicker()
}

function formOptionsFor(side) {
  const box = side === 'attacker' ? attacker : defender
  const p = box.pokemon
  if (!p) return []
  const dex = Number(p.nationalDexNumber)
  return allPokemon.value.filter((x) => Number(x.nationalDexNumber) === dex).sort(comparePokemonByFormId)
}

async function onFormSelect(side, apiName) {
  const p = allPokemon.value.find((x) => x.apiName === apiName)
  if (p) await applyPokemon(side, p)
}

function clearSide(side) {
  const box = side === 'attacker' ? attacker : defender
  box.pokemon = null
  box.types = []
  box.statPoints = freshStatPoints()
  box.ability = null
  box.item = null
  box.rank = 0
  if (side === 'attacker') {
    box.natureBoost = { attack: 0, specialAttack: 0 }
    box.weather = null; box.terrain = null; box.crit = false; box.status = null
    selectedMove.value = null
  } else {
    box.natureBoost = { defense: 0, specialDefense: 0 }
    box.screen = null; box.doubleScreen = false; box.spread = false; box.stealthRock = false
  }
}

function swapSides() {
  const aPoke = attacker.pokemon, aTypes = [...attacker.types]
  const dPoke = defender.pokemon, dTypes = [...defender.types]
  attacker.pokemon = dPoke; attacker.types = dTypes
  defender.pokemon = aPoke; defender.types = aTypes
  selectedMove.value = null
}

function cycleNatureBoost(side, key) {
  const box = side === 'attacker' ? attacker : defender
  const cur = box.natureBoost[key] || 0
  if (cur === 0) box.natureBoost[key] = 1
  else if (cur === 1) box.natureBoost[key] = -1
  else box.natureBoost[key] = 0
}

function natureMultiplier(side, key) {
  const box = side === 'attacker' ? attacker : defender
  const v = box.natureBoost[key] || 0
  if (v === 1) return 1.1
  if (v === -1) return 0.9
  return 1
}

function computeStat(pokemon, key, statPoints, side) {
  if (!pokemon) return 0
  const base = Number(pokemon[key] ?? 0)
  if (key === 'hp') return base + 75 + (statPoints.hp || 0)
  return Math.floor((base + 20 + (statPoints[key] || 0)) * natureMultiplier(side, key))
}

function usedEV(box, keys) {
  return keys.reduce((s, k) => s + (box.statPoints[k] || 0), 0)
}

function setEV(box, key, val, allowedKeys) {
  const others = allowedKeys.filter((k) => k !== key).reduce((s, k) => s + (box.statPoints[k] || 0), 0)
  box.statPoints[key] = Math.min(val, MAX_PER, TOTAL_EV - others)
}

function toggleRadio(obj, field, value) { obj[field] = obj[field] === value ? null : value }

function pokeLabel(p) { return localizedName(p) || p.displayName || p.apiName }
function moveLabel(m) { return localizedName(m) || m.displayName || m.name || m.apiName }
function moveTypeName(m) {
  const tp = m.type
  if (!tp) return ''
  return typeof tp === 'string' ? tp.toLowerCase() : String(tp.name || '').toLowerCase()
}

const damagePreview = computed(() => {
  const m = selectedMove.value
  const ap = attacker.pokemon
  const dp = defender.pokemon
  if (!m || !ap || !dp || isStatusLikeMove(m)) return null
  const pw = Number(m.power)
  if (!Number.isFinite(pw) || pw < 1) return null
  const cat = String(m.category || 'PHYSICAL').toUpperCase()
  const isPhysical = cat === 'PHYSICAL'
  const atkKey = isPhysical ? 'attack' : 'specialAttack'
  const defKey = isPhysical ? 'defense' : 'specialDefense'

  let A = computeStat(ap, atkKey, attacker.statPoints, 'attacker')
  let D = computeStat(dp, defKey, defender.statPoints, 'defender')
  const hp = computeStat(dp, 'hp', defender.statPoints, 'defender')
  const moveType = moveTypeName(m)
  if (!moveType) return null

  const multMap = getDefenseMultipliers(defender.types.length ? defender.types : ['normal'])
  const typeMult = multMap[moveType] ?? 1
  const atkTypes = attacker.types.length ? attacker.types : normalizeTypesFromPokemon(ap)
  const isStab = atkTypes.includes(moveType)
  let stab = isStab ? 1.5 : 1

  const { mod: otherMod, stabOverride, effectiveAtkRank, effectiveDefRank } = computeModifiers({
    atkAbility: attacker.ability,
    defAbility: defender.ability,
    terrain: attacker.terrain,
    weather: attacker.weather,
    screen: defender.screen,
    doubleScreen: defender.doubleScreen,
    atkItem: attacker.item,
    defItem: defender.item,
    atkRank: attacker.rank,
    defRank: defender.rank,
    crit: attacker.crit,
    status: attacker.status,
    spread: defender.spread,
    isPhysical, moveType, typeMult, isStab,
  })
  if (stabOverride && isStab) stab = stabOverride

  A = Math.max(1, Math.floor(A * rankMultiplier(effectiveAtkRank)))
  D = Math.max(1, Math.floor(D * rankMultiplier(effectiveDefRank)))

  const range = calcDamageRange({
    level: 50, power: pw, attack: A, defense: D,
    stab, typeMultiplier: typeMult, otherMultiplier: otherMod,
  })
  if (!range) return null

  const pctMin = hp > 0 ? Math.round((range.min / hp) * 1000) / 10 : 0
  const pctMax = hp > 0 ? Math.round((range.max / hp) * 1000) / 10 : 0

  let srDmg = null, srPct = null
  if (defender.stealthRock && hp > 0) {
    const dTypes = defender.types.length ? defender.types : ['normal']
    srDmg = stealthRockDamage(hp, dTypes)
    srPct = Math.round((srDmg / hp) * 1000) / 10
  }

  return {
    ...range, A, D, hp, typeMult, stab, atkKey, defKey,
    pctMin, pctMax, otherMod, srDmg, srPct,
  }
})

onMounted(async () => {
  loading.value = true
  try {
    const pokRes = await pokemonRosterApi.getPokemonList()
    allPokemon.value = Array.isArray(pokRes.data) ? pokRes.data : []
  } catch (e) { console.error(e) }
  finally { loading.value = false }
})
</script>

<template>
  <div class="container damage-calc-page">
    <header class="page-header">
      <h1 class="page-title">
        <span class="material-symbols-rounded title-icon">calculate</span>
        {{ t('damageCalc.title') }}
      </h1>
      <p class="page-desc">{{ t('damageCalc.subtitle') }}</p>
    </header>

    <div v-if="loading" class="loading-state">
      <span class="material-symbols-rounded spin">progress_activity</span>
      <span>{{ t('common.loading') }}</span>
    </div>

    <template v-else>
      <div class="calc-grid">
        <!-- ═══════════ 攻擊方 ═══════════ -->
        <section class="side-card">
          <h2 class="side-title atk">{{ t('damageCalc.attacker') }}</h2>

          <!-- 寶可夢 -->
          <div class="field-block">
            <label class="field-label">{{ t('damageCalc.selectPokemon') }}</label>
            <button v-if="!attacker.pokemon" type="button" class="btn-pick full" @click="openPokePicker('attacker')">
              <span class="material-symbols-rounded">add_circle</span>
              {{ t('damageCalc.selectPokemon') }}
            </button>
            <div v-else class="poke-row">
              <img :src="getPokemonImageUrl(attacker.pokemon)" class="poke-sprite" alt="" />
              <div class="poke-info">
                <div class="poke-name">{{ pokeLabel(attacker.pokemon) }}</div>
                <button type="button" class="linkish" @click="openPokePicker('attacker')">{{ t('common.change') }}</button>
                <button type="button" class="linkish danger" @click="clearSide('attacker')">{{ t('common.clear') }}</button>
              </div>
            </div>
          </div>

          <template v-if="attacker.pokemon">
            <!-- Mega -->
            <div v-if="formOptionsFor('attacker').length > 1" class="field-block">
              <label class="field-label">{{ t('damageCalc.megaForm') }}</label>
              <select class="field-select" :value="attacker.pokemon.apiName" @change="onFormSelect('attacker', $event.target.value)">
                <option v-for="opt in formOptionsFor('attacker')" :key="opt.apiName" :value="opt.apiName">
                  {{ pokeLabel(opt) }}{{ (opt.mega || opt.isMega) ? ' (Mega)' : '' }}
                </option>
              </select>
            </div>

            <!-- 特性 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.atkAbilityTitle') }}</label>
              <select class="field-select" v-model="attacker.ability">
                <option :value="null">--</option>
                <option v-for="a in ATK_ABILITIES" :key="a.id" :value="a.id">{{ t('damageCalc.' + a.label) }}</option>
              </select>
            </div>

            <!-- 道具 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.itemTitle') }}</label>
              <select class="field-select" v-model="attacker.item">
                <option :value="null">--</option>
                <option v-for="i in ATK_ITEMS" :key="i.id" :value="i.id">{{ t('damageCalc.' + i.label) }}</option>
              </select>
            </div>

            <!-- EV -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.evPoints', { used: usedEV(attacker, ATTACKER_EV_KEYS), total: TOTAL_EV }) }}</label>
              <div class="stat-rows">
                <div v-for="key in ATTACKER_EV_KEYS" :key="'a-' + key" class="stat-row">
                  <span class="stat-label">{{ t('pokemon.stats.' + key) }}</span>
                  <button type="button" class="nature-arrow" :class="{ up: attacker.natureBoost[key] === 1, down: attacker.natureBoost[key] === -1 }" @click="cycleNatureBoost('attacker', key)">
                    <span v-if="attacker.natureBoost[key] === 1" class="material-symbols-rounded">arrow_upward</span>
                    <span v-else-if="attacker.natureBoost[key] === -1" class="material-symbols-rounded">arrow_downward</span>
                    <span v-else class="nature-neutral">—</span>
                  </button>
                  <div class="ev-presets">
                    <button v-for="ev in EV_PRESETS" :key="ev" type="button" class="ev-btn" :class="{ active: attacker.statPoints[key] === ev }" @click="setEV(attacker, key, ev, ATTACKER_EV_KEYS)">{{ ev }}</button>
                  </div>
                  <span class="stat-val">{{ computeStat(attacker.pokemon, key, attacker.statPoints, 'attacker') }}</span>
                </div>
              </div>
            </div>

            <!-- Rank -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.rank') }}</label>
              <div class="rank-row">
                <span class="rank-edge">-6</span>
                <div class="rank-chips">
                  <button v-for="r in RANK_OPTIONS" :key="r" type="button" class="rank-chip" :class="{ active: attacker.rank === r, negative: r < 0, positive: r > 0 }" @click="attacker.rank = attacker.rank === r ? 0 : r">{{ r > 0 ? '+' + r : r }}</button>
                </div>
                <span class="rank-edge">+6</span>
              </div>
            </div>

            <!-- 招式 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.selectMove') }}</label>
              <p v-if="!learnableMoves.length" class="hint-muted small">{{ t('damageCalc.noLearnableMoves') }}</p>
              <template v-else>
                <input v-model="moveSearch" type="search" class="field-search" :placeholder="t('common.search')" />
                <div class="move-chips">
                  <button v-for="m in filteredMoves" :key="m.name" type="button" class="move-chip" :class="{ active: selectedMove?.name === m.name }" @click="selectedMove = m">
                    {{ moveLabel(m) }} <span class="move-pw">{{ m.power }}</span>
                  </button>
                </div>
              </template>
            </div>

            <!-- 天候 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.weatherTitle') }}</label>
              <div class="toggle-chips">
                <button v-for="w in WEATHERS" :key="w.id" type="button" class="toggle-chip" :class="{ active: attacker.weather === w.id }" @click="toggleRadio(attacker, 'weather', w.id)">{{ t('damageCalc.' + w.label) }}</button>
              </div>
            </div>

            <!-- 場地 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.terrainTitle') }}</label>
              <div class="toggle-chips">
                <button v-for="tr in TERRAINS" :key="tr.id" type="button" class="toggle-chip" :class="{ active: attacker.terrain === tr.id }" @click="toggleRadio(attacker, 'terrain', tr.id)">{{ t('damageCalc.' + tr.label) }}</button>
              </div>
            </div>

            <!-- 擊中要害 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.critTitle') }}</label>
              <div class="toggle-chips">
                <button type="button" class="toggle-chip" :class="{ active: attacker.crit }" @click="attacker.crit = !attacker.crit">{{ t('damageCalc.crit') }}</button>
              </div>
            </div>

            <!-- 狀態異常 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.statusTitle') }}</label>
              <div class="toggle-chips">
                <button type="button" class="toggle-chip" :class="{ active: !attacker.status }" @click="attacker.status = null">{{ t('damageCalc.statusNone') }}</button>
                <button v-for="s in STATUS_CONDITIONS" :key="s.id" type="button" class="toggle-chip" :class="{ active: attacker.status === s.id }" @click="toggleRadio(attacker, 'status', s.id)">{{ t('damageCalc.' + s.label) }}</button>
              </div>
            </div>
          </template>
        </section>

        <!-- ═══════════ ⇄ 交換按鈕 ═══════════ -->
        <div class="swap-col">
          <button type="button" class="btn-swap" :aria-label="t('damageCalc.swap')" @click="swapSides">⇄</button>
        </div>

        <!-- ═══════════ 防禦方 ═══════════ -->
        <section class="side-card">
          <h2 class="side-title def">{{ t('damageCalc.defender') }}</h2>

          <!-- 寶可夢 -->
          <div class="field-block">
            <label class="field-label">{{ t('damageCalc.selectPokemon') }}</label>
            <button v-if="!defender.pokemon" type="button" class="btn-pick full" @click="openPokePicker('defender')">
              <span class="material-symbols-rounded">add_circle</span>
              {{ t('damageCalc.selectPokemon') }}
            </button>
            <div v-else class="poke-row">
              <img :src="getPokemonImageUrl(defender.pokemon)" class="poke-sprite" alt="" />
              <div class="poke-info">
                <div class="poke-name">{{ pokeLabel(defender.pokemon) }}</div>
                <button type="button" class="linkish" @click="openPokePicker('defender')">{{ t('common.change') }}</button>
                <button type="button" class="linkish danger" @click="clearSide('defender')">{{ t('common.clear') }}</button>
              </div>
            </div>
          </div>

          <template v-if="defender.pokemon">
            <!-- Mega -->
            <div v-if="formOptionsFor('defender').length > 1" class="field-block">
              <label class="field-label">{{ t('damageCalc.megaForm') }}</label>
              <select class="field-select" :value="defender.pokemon.apiName" @change="onFormSelect('defender', $event.target.value)">
                <option v-for="opt in formOptionsFor('defender')" :key="opt.apiName" :value="opt.apiName">
                  {{ pokeLabel(opt) }}{{ (opt.mega || opt.isMega) ? ' (Mega)' : '' }}
                </option>
              </select>
            </div>

            <!-- 特性 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.defAbilityTitle') }}</label>
              <select class="field-select" v-model="defender.ability">
                <option :value="null">--</option>
                <option v-for="a in DEF_ABILITIES" :key="a.id" :value="a.id">{{ t('damageCalc.' + a.label) }}</option>
              </select>
            </div>

            <!-- 壁 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.screenTitle') }}</label>
              <div class="toggle-chips">
                <button v-for="s in SCREENS" :key="s.id" type="button" class="toggle-chip" :class="{ active: defender.screen === s.id }" @click="toggleRadio(defender, 'screen', s.id)">{{ t('damageCalc.' + s.label) }}</button>
              </div>
              <div class="toggle-chips sub">
                <button type="button" class="toggle-chip small" :class="{ active: defender.spread }" @click="defender.spread = !defender.spread">{{ t('damageCalc.spread') }}</button>
                <button type="button" class="toggle-chip small" :class="{ active: defender.doubleScreen }" @click="defender.doubleScreen = !defender.doubleScreen">{{ t('damageCalc.doubleScreen') }}</button>
              </div>
            </div>

            <!-- 隱形岩 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.hazardTitle') }}</label>
              <div class="toggle-chips">
                <button type="button" class="toggle-chip" :class="{ active: defender.stealthRock }" @click="defender.stealthRock = !defender.stealthRock">{{ t('damageCalc.stealthRock') }}</button>
              </div>
            </div>

            <!-- 道具 -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.itemTitle') }}</label>
              <select class="field-select" v-model="defender.item">
                <option :value="null">--</option>
                <option v-for="i in DEF_ITEMS" :key="i.id" :value="i.id">{{ t('damageCalc.' + i.label) }}</option>
              </select>
            </div>

            <!-- EV -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.evPoints', { used: usedEV(defender, defenderEvKeys), total: TOTAL_EV }) }}</label>
              <p class="hint-muted small def-ev-hint">{{ t('damageCalc.defEvHint', { stat: t('pokemon.stats.' + activeDefKey) }) }}</p>
              <div class="stat-rows">
                <div v-for="key in defenderEvKeys" :key="'d-' + key" class="stat-row">
                  <span class="stat-label">{{ t('pokemon.stats.' + key) }}</span>
                  <template v-if="key !== 'hp'">
                    <button type="button" class="nature-arrow" :class="{ up: defender.natureBoost[key] === 1, down: defender.natureBoost[key] === -1 }" @click="cycleNatureBoost('defender', key)">
                      <span v-if="defender.natureBoost[key] === 1" class="material-symbols-rounded">arrow_upward</span>
                      <span v-else-if="defender.natureBoost[key] === -1" class="material-symbols-rounded">arrow_downward</span>
                      <span v-else class="nature-neutral">—</span>
                    </button>
                  </template>
                  <span v-else class="nature-arrow placeholder" />
                  <div class="ev-presets">
                    <button v-for="ev in EV_PRESETS" :key="ev" type="button" class="ev-btn" :class="{ active: defender.statPoints[key] === ev }" @click="setEV(defender, key, ev, defenderEvKeys)">{{ ev }}</button>
                  </div>
                  <span class="stat-val">{{ computeStat(defender.pokemon, key, defender.statPoints, 'defender') }}</span>
                </div>
              </div>
            </div>

            <!-- Rank -->
            <div class="field-block">
              <label class="field-label">{{ t('damageCalc.rank') }}</label>
              <div class="rank-row">
                <span class="rank-edge">-6</span>
                <div class="rank-chips">
                  <button v-for="r in RANK_OPTIONS" :key="r" type="button" class="rank-chip" :class="{ active: defender.rank === r, negative: r < 0, positive: r > 0 }" @click="defender.rank = defender.rank === r ? 0 : r">{{ r > 0 ? '+' + r : r }}</button>
                </div>
                <span class="rank-edge">+6</span>
              </div>
            </div>
          </template>
        </section>
      </div>

    </template>

    <!-- ═══════════ 結果（sticky 底部）═══════════ -->
    <div class="result-sticky">
      <section v-if="damagePreview" class="result-card">
        <div class="result-top">
          <div class="result-left">
            <h2>{{ t('damageCalc.resultTitle') }}</h2>
            <p class="result-move">{{ moveLabel(selectedMove) }} · Lv.50</p>
          </div>
          <div class="result-center">
            <span class="dmg-range">{{ damagePreview.min }} — {{ damagePreview.max }}</span>
            <span class="dmg-label">{{ t('damageCalc.damageRange') }}</span>
          </div>
          <div class="result-right">
            <span class="result-hp">{{ t('damageCalc.hpPercent', { min: damagePreview.pctMin, max: damagePreview.pctMax }) }}</span>
          </div>
        </div>
        <div v-if="damagePreview.srDmg != null" class="sr-result">
          <span class="material-symbols-rounded sr-icon">landscape</span>
          <span>{{ t('damageCalc.srResult', { dmg: damagePreview.srDmg, pct: damagePreview.srPct }) }}</span>
        </div>
        <dl class="result-meta">
          <div><dt>{{ t('damageCalc.metaStab') }}</dt><dd>×{{ damagePreview.stab }}</dd></div>
          <div><dt>{{ t('damageCalc.metaType') }}</dt><dd>×{{ damagePreview.typeMult }}</dd></div>
          <div v-if="damagePreview.otherMod !== 1"><dt>{{ t('damageCalc.metaOther') }}</dt><dd>×{{ Math.round(damagePreview.otherMod * 1000) / 1000 }}</dd></div>
          <div><dt>{{ t('damageCalc.metaAtk') }}</dt><dd>{{ damagePreview.A }}</dd></div>
          <div><dt>{{ t('damageCalc.metaDef') }}</dt><dd>{{ damagePreview.D }}</dd></div>
          <div><dt>{{ t('damageCalc.metaHp') }}</dt><dd>{{ damagePreview.hp }}</dd></div>
        </dl>
      </section>
      <p v-else-if="attacker.pokemon && defender.pokemon && selectedMove" class="result-hint">{{ t('damageCalc.noDamage') }}</p>
      <p v-else class="result-hint">{{ t('damageCalc.needAll') }}</p>
    </div>

    <!-- Picker modal -->
    <Teleport to="body">
      <div v-if="pokePickerOpen" class="picker-overlay" @click.self="closePokePicker">
        <div class="picker-modal">
          <div class="picker-header">
            <h3>{{ t('damageCalc.selectPokemon') }}</h3>
            <button type="button" class="picker-close" :aria-label="t('common.close')" @click="closePokePicker">
              <span class="material-symbols-rounded">close</span>
            </button>
          </div>
          <input v-model="pokeSearch" type="search" class="picker-search" :placeholder="t('common.search')" />
          <div class="picker-list">
            <button v-for="p in filteredPokemon" :key="p.apiName + (p.formId ?? '')" type="button" class="picker-row" @click="pickPokemon(p)">
              <img :src="getPokemonImageUrl(p)" class="picker-sprite" alt="" />
              <span class="picker-name">{{ pokeLabel(p) }}</span>
            </button>
            <p v-if="!filteredPokemon.length" class="picker-empty">{{ t('common.noResults') }}</p>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.damage-calc-page { padding-bottom: 200px; }

.page-header { margin-bottom: 24px; }
.page-title { display: flex; align-items: center; gap: 10px; font-size: 1.65rem; margin-bottom: 8px; }
.title-icon { font-size: 1.75rem; color: var(--accent); }
.page-desc { color: var(--text-muted); font-size: 0.92rem; max-width: 760px; line-height: 1.55; margin: 0; }

.loading-state { display: flex; align-items: center; gap: 10px; padding: 48px; justify-content: center; color: var(--text-muted); }

/* ── Grid: atk | swap | def ── */
.calc-grid {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 0;
  align-items: start;
  margin-bottom: 28px;
}
@media (max-width: 900px) {
  .calc-grid { grid-template-columns: 1fr; }
  .swap-col {
    justify-content: center; padding: 4px 0;
    transform: rotate(90deg);
  }
}

.swap-col {
  display: flex; align-items: flex-start; justify-content: center;
  padding: 0 6px; padding-top: 52px;
}
.btn-swap {
  width: 38px; height: 38px; border-radius: 50%;
  border: 1px solid var(--border); background: var(--bg-glass);
  color: var(--accent); font-size: 1.2rem; font-weight: 700;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: background 0.15s, transform 0.15s;
}
.btn-swap:hover { background: var(--accent-soft); transform: scale(1.1); }

/* ── Side card ── */
.side-card {
  background: var(--bg-card); border: 1px solid var(--border);
  border-radius: var(--radius); padding: 18px 16px;
}
.side-title {
  margin: 0 0 14px; font-size: 1.05rem;
  padding-bottom: 8px; border-bottom: 1px solid var(--border);
}
.side-title.atk { color: #e57373; }
.side-title.def { color: #4a90d9; }

/* ── Field blocks ── */
.field-block { margin-bottom: 14px; }
.field-block:last-child { margin-bottom: 0; }
.field-label { display: block; font-size: 0.78rem; font-weight: 600; color: var(--text-muted); margin-bottom: 6px; }

.field-select {
  width: 100%; padding: 7px 10px; border-radius: 8px;
  border: 1px solid var(--border); background: var(--bg-card);
  color: var(--text-primary); font-size: 0.82rem; font-family: inherit;
}

.field-search {
  width: 100%; margin-bottom: 8px; padding: 7px 10px; border-radius: 8px;
  border: 1px solid var(--border); background: var(--bg-card); color: var(--text-primary);
  font-size: 0.82rem;
}

/* ── Pokemon row ── */
.btn-pick {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 16px; border-radius: 10px;
  border: 1px dashed var(--border); background: var(--bg-glass);
  color: var(--text-secondary); font-size: 0.85rem;
  cursor: pointer; font-family: inherit;
}
.btn-pick.full { width: 100%; justify-content: center; }
.btn-pick:hover { border-color: var(--accent); color: var(--accent); }

.poke-row { display: flex; gap: 12px; align-items: center; }
.poke-sprite { width: 56px; height: 56px; image-rendering: pixelated; flex-shrink: 0; }
.poke-info { flex: 1; min-width: 0; }
.poke-name { font-weight: 700; font-size: 0.9rem; margin-bottom: 4px; }
.linkish {
  display: inline-block; margin-right: 10px; padding: 0;
  border: none; background: none; color: var(--accent);
  font-size: 0.78rem; cursor: pointer; font-family: inherit; text-decoration: underline;
}
.linkish.danger { color: var(--text-muted); }

/* ── Stats ── */
.stat-rows { display: flex; flex-direction: column; gap: 8px; }
.stat-row { display: flex; align-items: center; gap: 6px; font-size: 0.8rem; }
.stat-label { width: 36px; flex-shrink: 0; font-weight: 600; white-space: nowrap; font-size: 0.72rem; }

.nature-arrow {
  width: 28px; height: 28px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  border-radius: 6px; border: 1px solid var(--border);
  background: var(--bg-glass); color: var(--text-muted);
  cursor: pointer; transition: background 0.12s, color 0.12s, border-color 0.12s;
}
.nature-arrow .material-symbols-rounded { font-size: 16px; }
.nature-arrow.up { background: rgba(229,83,79,0.15); border-color: rgba(229,83,79,0.4); color: #e57373; }
.nature-arrow.down { background: rgba(74,144,217,0.15); border-color: rgba(74,144,217,0.4); color: #6ba3e8; }
.nature-neutral { font-size: 12px; line-height: 1; }
.nature-arrow.placeholder { visibility: hidden; }

.ev-presets { display: flex; gap: 3px; flex-shrink: 0; }
.ev-btn {
  min-width: 28px; height: 26px; padding: 0 3px;
  border-radius: 5px; border: 1px solid var(--border);
  background: var(--bg-glass); color: var(--text-secondary);
  font-size: 0.68rem; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: background 0.12s, color 0.12s, border-color 0.12s;
}
.ev-btn.active { background: var(--accent-soft); color: var(--accent); border-color: rgba(98,188,90,0.4); }

.stat-val {
  min-width: 34px; text-align: right; font-weight: 700;
  font-variant-numeric: tabular-nums; color: var(--text-primary); font-size: 0.82rem;
}

/* ── Rank ── */
.rank-row { display: flex; align-items: center; gap: 4px; }
.rank-edge { font-size: 0.7rem; font-weight: 700; color: var(--text-muted); flex-shrink: 0; }
.rank-chips { display: flex; flex-wrap: wrap; gap: 3px; flex: 1; }
.rank-chip {
  min-width: 26px; height: 24px; padding: 0 2px;
  border-radius: 5px; border: 1px solid var(--border);
  background: var(--bg-glass); color: var(--text-muted);
  font-size: 0.65rem; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: background 0.12s;
}
.rank-chip.active.negative { background: rgba(74,144,217,0.2); color: #6ba3e8; border-color: rgba(74,144,217,0.4); }
.rank-chip.active.positive { background: rgba(229,83,79,0.2); color: #e57373; border-color: rgba(229,83,79,0.4); }
.rank-chip.active:not(.negative):not(.positive) { background: var(--accent-soft); color: var(--accent); border-color: rgba(98,188,90,0.4); }

/* ── Toggle chips (weather/terrain/screen/status) ── */
.toggle-chips { display: flex; flex-wrap: wrap; gap: 5px; }
.toggle-chips.sub { margin-top: 6px; }
.toggle-chip {
  display: inline-flex; align-items: center; padding: 4px 9px;
  border-radius: 999px; border: 1px solid var(--border);
  background: var(--bg-glass); color: var(--text-secondary);
  font-size: 0.72rem; font-weight: 600; font-family: inherit;
  cursor: pointer; transition: background 0.12s, color 0.12s, border-color 0.12s;
}
.toggle-chip:hover { border-color: var(--accent); }
.toggle-chip.active { background: var(--accent-soft); color: var(--accent); border-color: rgba(98,188,90,0.5); }
.toggle-chip.small { font-size: 0.68rem; padding: 3px 7px; }

/* ── Move chips ── */
.move-chips { display: flex; flex-wrap: wrap; gap: 5px; max-height: 180px; overflow-y: auto; padding: 2px 0; }
.move-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 8px; border-radius: 999px;
  border: 1px solid var(--border); background: var(--bg-card);
  color: var(--text-secondary); font-size: 0.72rem;
  cursor: pointer; font-family: inherit;
}
.move-chip.active { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); }
.move-pw { font-weight: 700; opacity: 0.85; }

/* ── Defender EV hint ── */
.def-ev-hint { margin: 0 0 6px; font-size: 0.72rem; }

/* ── Result (sticky bottom) ── */
.result-sticky {
  position: fixed; bottom: 0; left: 0; right: 0; z-index: 300;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border);
  box-shadow: 0 -4px 24px rgba(0,0,0,0.25);
  padding: 12px 24px;
  max-height: 45vh; overflow-y: auto;
}
.result-card {
  max-width: 1100px; margin: 0 auto;
}
.result-top {
  display: flex; align-items: center; gap: 20px; flex-wrap: wrap; margin-bottom: 8px;
}
.result-left h2 { margin: 0; font-size: 1rem; }
.result-move { margin: 0; color: var(--text-muted); font-size: 0.82rem; }
.result-center { display: flex; align-items: baseline; gap: 8px; }
.dmg-range { font-size: 1.6rem; font-weight: 800; color: var(--accent); font-variant-numeric: tabular-nums; }
.dmg-label { font-size: 0.76rem; color: var(--text-muted); }
.result-right { margin-left: auto; }
.result-hp { margin: 0; font-size: 0.84rem; color: var(--text-secondary); }

.sr-result {
  display: inline-flex; align-items: center; gap: 6px;
  margin-bottom: 8px; padding: 5px 10px;
  border-radius: 8px; background: rgba(193,125,56,0.12);
  font-size: 0.78rem; color: var(--text-secondary);
}
.sr-icon { font-size: 1rem; color: #b87a3d; }

.result-meta {
  margin: 0; display: flex; flex-wrap: wrap;
  gap: 4px 18px; font-size: 0.78rem;
}
.result-meta div { display: flex; gap: 4px; }
.result-meta dt { color: var(--text-muted); margin: 0; }
.result-meta dd { margin: 0; font-weight: 600; }

.result-hint {
  max-width: 1100px; margin: 0 auto;
  color: var(--text-muted); font-size: 0.82rem;
  text-align: center; padding: 4px 0;
}

.hint-muted { color: var(--text-muted); font-size: 0.86rem; }
.hint-muted.small { font-size: 0.78rem; }

@media (max-width: 900px) {
  .result-sticky { padding: 10px 14px; }
  .result-top { gap: 10px; }
  .dmg-range { font-size: 1.3rem; }
}

/* ── Picker ── */
.picker-overlay {
  position: fixed; inset: 0; z-index: 400;
  background: rgba(0,0,0,0.55);
  display: flex; align-items: flex-start; justify-content: center;
  padding: 48px 16px; overflow-y: auto;
}
.picker-modal {
  width: 100%; max-width: 400px; max-height: min(80vh,520px);
  display: flex; flex-direction: column;
  background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 14px;
}
.picker-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 14px; border-bottom: 1px solid var(--border);
}
.picker-header h3 { margin: 0; font-size: 0.95rem; }
.picker-close { border: none; background: transparent; color: var(--text-muted); cursor: pointer; }
.picker-search {
  margin: 10px 14px; padding: 8px 10px; border-radius: 8px;
  border: 1px solid var(--border); background: var(--bg-card); color: var(--text-primary);
}
.picker-list { overflow-y: auto; padding: 0 8px 12px; max-height: 360px; }
.picker-row {
  display: flex; align-items: center; gap: 10px;
  width: 100%; padding: 8px; border: none; border-radius: 8px;
  background: transparent; cursor: pointer; text-align: left;
  font-family: inherit; color: var(--text-primary);
}
.picker-row:hover { background: var(--bg-glass); }
.picker-sprite { width: 40px; height: 40px; image-rendering: pixelated; }
.picker-name { flex: 1; min-width: 0; font-weight: 600; font-size: 0.86rem; }
.picker-empty { text-align: center; color: var(--text-muted); padding: 20px; }

.spin { animation: spin 0.9s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 520px) {
  .stat-row { flex-wrap: wrap; }
  .stat-label { width: auto; }
  .rank-chips { gap: 2px; }
}
</style>
