import { reactive, watch } from 'vue'
import { getNatureMultiplier, NATURE_DEFS } from '../constants/pokemonNatures'

// ═══════════════════════════════════════════════════
//  常數
// ═══════════════════════════════════════════════════
const STORAGE_KEY = 'poke-champions-team'

export const ALL_TYPES = [
  'normal','fire','water','electric','grass','ice',
  'fighting','poison','ground','flying','psychic','bug',
  'rock','ghost','dragon','dark','steel','fairy',
]

export const TYPE_ZH = {
  normal:'一般',fire:'火',water:'水',electric:'電',grass:'草',ice:'冰',
  fighting:'格鬥',poison:'毒',ground:'地面',flying:'飛行',psychic:'超能力',bug:'蟲',
  rock:'岩石',ghost:'幽靈',dragon:'龍',dark:'惡',steel:'鋼',fairy:'妖精',
}

export const STAT_KEYS = ['hp','attack','defense','specialAttack','specialDefense','speed']
export const STAT_LABELS = { hp:'HP', attack:'攻擊', defense:'防禦', specialAttack:'特攻', specialDefense:'特防', speed:'速度' }
export const STAT_COLORS = { hp:'#ff5555', attack:'#ff9741', defense:'#fcd000', specialAttack:'#4a90d9', specialDefense:'#62bc5a', speed:'#fa7179' }
export const TOTAL_EV = 66
export const MAX_PER = 32
export const CATEGORY_LABELS = { PHYSICAL: '物理', SPECIAL: '特殊', STATUS: '變化' }

// ═══════════════════════════════════════════════════
//  屬性相剋表
// ═══════════════════════════════════════════════════
const EFF = buildEffectivenessTable()

function buildEffectivenessTable() {
  const t = {}
  ALL_TYPES.forEach(a => { t[a] = {}; ALL_TYPES.forEach(d => { t[a][d] = 1 }) })
  const data = {
    normal:   { rock:0.5, ghost:0, steel:0.5 },
    fire:     { fire:0.5, water:0.5, grass:2, ice:2, bug:2, rock:0.5, dragon:0.5, steel:2 },
    water:    { fire:2, water:0.5, grass:0.5, ground:2, rock:2, dragon:0.5 },
    electric: { water:2, electric:0.5, grass:0.5, ground:0, flying:2, dragon:0.5 },
    grass:    { fire:0.5, water:2, grass:0.5, poison:0.5, ground:2, flying:0.5, bug:0.5, rock:2, dragon:0.5, steel:0.5 },
    ice:      { fire:0.5, water:0.5, grass:2, ice:0.5, ground:2, flying:2, dragon:2, steel:0.5 },
    fighting: { normal:2, ice:2, poison:0.5, flying:0.5, psychic:0.5, bug:0.5, rock:2, ghost:0, dark:2, steel:2, fairy:0.5 },
    poison:   { grass:2, poison:0.5, ground:0.5, rock:0.5, ghost:0.5, steel:0, fairy:2 },
    ground:   { fire:2, electric:2, grass:0.5, poison:2, flying:0, bug:0.5, rock:2, steel:2 },
    flying:   { electric:0.5, grass:2, fighting:2, bug:2, rock:0.5, steel:0.5 },
    psychic:  { fighting:2, poison:2, psychic:0.5, dark:0, steel:0.5 },
    bug:      { fire:0.5, grass:2, fighting:0.5, poison:0.5, flying:0.5, psychic:2, ghost:0.5, dark:2, steel:0.5, fairy:0.5 },
    rock:     { fire:2, ice:2, fighting:0.5, ground:0.5, flying:2, bug:2, steel:0.5 },
    ghost:    { normal:0, psychic:2, ghost:2, dark:0.5 },
    dragon:   { dragon:2, fairy:0 },
    dark:     { fighting:0.5, psychic:2, ghost:2, dark:0.5, fairy:0.5 },
    steel:    { fire:0.5, water:0.5, electric:0.5, ice:2, rock:2, steel:0.5, fairy:2 },
    fairy:    { fire:0.5, poison:0.5, fighting:2, dragon:2, dark:2, steel:0.5 },
  }
  for (const [atk, map] of Object.entries(data)) {
    for (const [def, mult] of Object.entries(map)) {
      t[atk][def] = mult
    }
  }
  return t
}

export function getDefenseMultipliers(types) {
  const result = {}
  ALL_TYPES.forEach(atk => {
    let mult = 1
    types.forEach(def => { mult *= (EFF[atk]?.[def] ?? 1) })
    result[atk] = mult
  })
  return result
}

// ═══════════════════════════════════════════════════
//  能力值計算
// ═══════════════════════════════════════════════════
export function calcStat(pokemon, key, pts, nature) {
  if (!pokemon) return 0
  const base = Number(pokemon[key] ?? 0)
  if (key === 'hp') return base + 75 + (pts.hp || 0)
  return Math.floor((base + 20 + (pts[key] || 0)) * getNatureMultiplier(nature, key))
}

// ═══════════════════════════════════════════════════
//  單例隊伍狀態（含 localStorage 持久化）
// ═══════════════════════════════════════════════════
function emptyMember() {
  return {
    pokemon: null,
    types: [],
    statPoints: { hp:0, attack:0, defense:0, specialAttack:0, specialDefense:0, speed:0 },
    nature: 'serious',
    moves: [null, null, null, null],
    heldItem: null,
    _learnableMoves: [],
  }
}

function loadFromStorage() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed) || parsed.length !== 6) return null
    return parsed.map(m => ({
      ...emptyMember(),
      ...m,
      moves: Array.isArray(m.moves) ? m.moves.slice(0, 4) : [null, null, null, null],
      _learnableMoves: [],
    }))
  } catch {
    return null
  }
}

function serializeForStorage(members) {
  return members.map(m => ({
    pokemon: m.pokemon,
    types: m.types,
    statPoints: { ...m.statPoints },
    nature: m.nature,
    moves: [...m.moves],
    heldItem: m.heldItem,
  }))
}

const saved = loadFromStorage()
const teamMembers = reactive(saved || Array.from({ length: 6 }, emptyMember))

let saveTimer = null
watch(teamMembers, () => {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(serializeForStorage(teamMembers)))
  }, 300)
}, { deep: true })

export function useTeamStore() {
  function clearSlot(idx) {
    Object.assign(teamMembers[idx], emptyMember())
  }

  function pokemonDisplayName(p) {
    if (!p) return ''
    return p.chineseName || p.displayName || p.apiName
  }

  return {
    teamMembers,
    clearSlot,
    pokemonDisplayName,
    emptyMember,
  }
}
