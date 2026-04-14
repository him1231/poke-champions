/**
 * Lv.50 冠軍賽傷害計算核心。
 *
 * 支援：STAB、屬性倍率、隨機亂數 0.85–1.0、
 *       特性（攻／防）、場地、天候、壁（含雙打壁）、
 *       道具（攻／防）、能力等級 Rank、擊中要害、燒傷、
 *       全體技、隱形岩。
 */

/* ═══ 基礎公式 ═══ */

export function damageBase(level, power, attack, defense) {
  const L = Number(level) || 50
  const P = Number(power) || 0
  const A = Number(attack) || 0
  const D = Math.max(1, Number(defense) || 1)
  if (P < 1) return 0
  const t = Math.floor((2 * L) / 5 + 2)
  const u = Math.floor((t * P * A) / D)
  return Math.floor(u / 50) + 2
}

export function calcDamageRange({
  level = 50, power, attack, defense,
  stab = 1, typeMultiplier = 1,
  otherMultiplier = 1,
}) {
  const P = Number(power)
  if (!P || P < 1) return null
  const base = damageBase(level, P, attack, defense)
  if (base < 1) return null
  const mod = stab * typeMultiplier * otherMultiplier
  const min = Math.max(1, Math.floor(base * mod * 0.85))
  const max = Math.max(1, Math.floor(base * mod * 1.0))
  return { min, max, base }
}

export function isStatusLikeMove(move) {
  if (!move) return true
  const cat = String(move.category || '').toUpperCase()
  if (cat === 'STATUS') return true
  const pw = move.power
  if (pw == null || pw === '—' || pw === '') return true
  const n = Number(pw)
  return !Number.isFinite(n) || n < 1
}

/* ═══ 能力等級 Rank ═══ */

const RANK_TABLE = {
  '-6': 2 / 8, '-5': 2 / 7, '-4': 2 / 6, '-3': 2 / 5,
  '-2': 2 / 4, '-1': 2 / 3, '0': 1,
  '1': 3 / 2, '2': 4 / 2, '3': 5 / 2,
  '4': 6 / 2, '5': 7 / 2, '6': 8 / 2,
}

export function rankMultiplier(rank) {
  return RANK_TABLE[String(rank)] ?? 1
}

/* ═══ 攻擊方特性 ═══ */

export const ATK_ABILITIES = [
  { id: 'hugePower',    mult: 2,    physical: true,  label: 'atkAbilityHugePower' },
  { id: 'adaptability', stab: 2,                     label: 'atkAbilityAdaptability' },
  { id: 'toughClaws',   mult: 1.3,                   label: 'atkAbilityToughClaws' },
  { id: 'sheerForce',   mult: 1.3,                   label: 'atkAbilitySheerForce' },
  { id: 'ateAbility',   mult: 1.2,                   label: 'atkAbilityAte' },
]

/* ═══ 防禦方特性 ═══ */

export const DEF_ABILITIES = [
  { id: 'multiscale',  mult: 0.5,                     label: 'defAbilityMultiscale' },
  { id: 'furCoat',     mult: 0.5, physical: true,     label: 'defAbilityFurCoat' },
  { id: 'iceScales',   mult: 0.5, special: true,      label: 'defAbilityIceScales' },
  { id: 'solidRock',   mult: 0.75, superEffective: true, label: 'defAbilitySolidRock' },
  { id: 'thickFat',    mult: 0.5, types: ['fire', 'ice'], label: 'defAbilityThickFat' },
]

/* ═══ 天候 ═══ */

export const WEATHERS = [
  { id: 'sun',  label: 'weatherSun' },
  { id: 'rain', label: 'weatherRain' },
  { id: 'sand', label: 'weatherSand' },
  { id: 'snow', label: 'weatherSnow' },
]

function weatherMultiplier(weatherId, moveType) {
  if (!weatherId || !moveType) return 1
  if (weatherId === 'sun') {
    if (moveType === 'fire') return 1.5
    if (moveType === 'water') return 0.5
  }
  if (weatherId === 'rain') {
    if (moveType === 'water') return 1.5
    if (moveType === 'fire') return 0.5
  }
  return 1
}

/* ═══ 場地 ═══ */

export const TERRAINS = [
  { id: 'electric', boostType: 'electric', mult: 1.3, label: 'terrainElectric' },
  { id: 'grassy',   boostType: 'grass',    mult: 1.3, label: 'terrainGrassy' },
  { id: 'psychic',  boostType: 'psychic',  mult: 1.3, label: 'terrainPsychic' },
  { id: 'misty',    boostType: 'dragon',   mult: 0.5, label: 'terrainMisty' },
]

/* ═══ 壁 ═══ */

export const SCREENS = [
  { id: 'reflect',     mult: 0.5, physical: true,  label: 'screenReflect' },
  { id: 'lightScreen', mult: 0.5, special: true,   label: 'screenLightScreen' },
  { id: 'auroraVeil',  mult: 0.5,                  label: 'screenAuroraVeil' },
]

/* ═══ 攻擊方道具 ═══ */

export const ATK_ITEMS = [
  { id: 'lifeOrb',     mult: 1.3,                        label: 'itemLifeOrb' },
  { id: 'choiceBand',  mult: 1.5, physical: true,        label: 'itemChoiceBand' },
  { id: 'choiceSpecs', mult: 1.5, special: true,         label: 'itemChoiceSpecs' },
  { id: 'expertBelt',  mult: 1.2, superEffective: true,  label: 'itemExpertBelt' },
]

/* ═══ 防禦方道具 ═══ */

export const DEF_ITEMS = [
  { id: 'assaultVest', mult: 1.5, special: true,    label: 'defItemAssaultVest' },
  { id: 'eviolite',    mult: 1.5,                   label: 'defItemEviolite' },
]

/* ═══ 狀態異常 ═══ */

export const STATUS_CONDITIONS = [
  { id: 'burn',     label: 'statusBurn' },
  { id: 'poison',   label: 'statusPoison' },
  { id: 'paralyze', label: 'statusParalyze' },
  { id: 'sleep',    label: 'statusSleep' },
  { id: 'freeze',   label: 'statusFreeze' },
]

/* ═══ 隱形岩 ═══ */

const SR_EFF = {
  normal: 1, fire: 0.5, water: 1, electric: 1, grass: 1,
  ice: 2, fighting: 0.5, poison: 1, ground: 0.5, flying: 2,
  psychic: 1, bug: 2, rock: 1, ghost: 1, dragon: 1,
  dark: 1, steel: 0.5, fairy: 1,
}

export function stealthRockDamage(hp, defenderTypes) {
  let mult = 1
  for (const t of defenderTypes) {
    mult *= (SR_EFF[t] ?? 1)
  }
  return Math.max(1, Math.floor(hp * mult / 8))
}

/**
 * 根據所有啟用 toggle 計算最終乘數。
 */
export function computeModifiers({
  atkAbility = null,
  defAbility = null,
  terrain = null,
  weather = null,
  screen = null,
  doubleScreen = false,
  atkItem = null,
  defItem = null,
  atkRank = 0,
  defRank = 0,
  crit = false,
  status = null,
  spread = false,
  isPhysical = true,
  moveType = '',
  typeMult = 1,
  isStab = false,
}) {
  let mod = 1
  let stabOverride = null
  let effectiveAtkRank = Number(atkRank) || 0
  let effectiveDefRank = Number(defRank) || 0

  if (crit) {
    mod *= 1.5
    if (effectiveAtkRank < 0) effectiveAtkRank = 0
    if (effectiveDefRank > 0) effectiveDefRank = 0
  }

  if (atkAbility) {
    const def = ATK_ABILITIES.find((a) => a.id === atkAbility)
    if (def) {
      if (def.stab) {
        stabOverride = def.stab
      } else if (def.physical && !isPhysical) {
        /* Huge Power physical only */
      } else {
        mod *= def.mult
      }
    }
  }

  if (defAbility) {
    const def = DEF_ABILITIES.find((a) => a.id === defAbility)
    if (def) {
      let applies = true
      if (def.physical && !isPhysical) applies = false
      if (def.special && isPhysical) applies = false
      if (def.superEffective && typeMult <= 1) applies = false
      if (def.types && !def.types.includes(moveType)) applies = false
      if (applies) mod *= def.mult
    }
  }

  mod *= weatherMultiplier(weather, moveType)

  if (terrain) {
    const def = TERRAINS.find((t) => t.id === terrain)
    if (def && def.boostType === moveType) mod *= def.mult
  }

  if (screen) {
    const def = SCREENS.find((s) => s.id === screen)
    if (def) {
      let applies = true
      if (def.physical && !isPhysical) applies = false
      if (def.special && isPhysical) applies = false
      if (applies) {
        mod *= doubleScreen ? (2 / 3) : def.mult
      }
    }
  }

  if (atkItem) {
    const def = ATK_ITEMS.find((i) => i.id === atkItem)
    if (def) {
      let applies = true
      if (def.physical && !isPhysical) applies = false
      if (def.special && isPhysical) applies = false
      if (def.superEffective && typeMult <= 1) applies = false
      if (applies) mod *= def.mult
    }
  }

  if (defItem) {
    const def = DEF_ITEMS.find((i) => i.id === defItem)
    if (def) {
      let applies = true
      if (def.special && isPhysical) applies = false
      if (applies) {
        mod /= def.mult
      }
    }
  }

  if (status === 'burn' && isPhysical) {
    mod *= 0.5
  }

  if (spread) {
    mod *= 0.75
  }

  return { mod, stabOverride, effectiveAtkRank, effectiveDefRank }
}
