/**
 * Pokémon Showdown（Champions 賽制）貼上格式。
 * 依隊伍成員物件組字串；種族／招式／道具／特性以英文 displayName 或 apiName／name 推斷。
 */

const STAT_ORDER = ['hp', 'attack', 'defense', 'specialAttack', 'specialDefense', 'speed']

const SHOWDOWN_EV_ABBREV = {
  hp: 'HP',
  attack: 'Atk',
  defense: 'Def',
  specialAttack: 'SpA',
  specialDefense: 'SpD',
  speed: 'Spe',
}

/**
 * PokeAPI 式 apiName → Showdown 種族字串（例：feraligatr → Feraligatr，abomasnow-mega → Abomasnow-Mega）
 */
export function apiNameToShowdownSpecies(apiName) {
  if (!apiName || typeof apiName !== 'string') return 'Unknown'
  return apiName
    .split('-')
    .map((seg) => (seg ? seg.charAt(0).toUpperCase() + seg.slice(1).toLowerCase() : ''))
    .filter(Boolean)
    .join('-')
}

function natureIdToShowdownLabel(natureId) {
  if (!natureId || typeof natureId !== 'string') return 'Serious'
  return natureId.charAt(0).toUpperCase() + natureId.slice(1).toLowerCase()
}

function formatEvsLine(statPoints) {
  const pts = statPoints || {}
  const parts = []
  for (const key of STAT_ORDER) {
    const n = Number(pts[key] || 0)
    if (n <= 0) continue
    const abbr = SHOWDOWN_EV_ABBREV[key]
    if (abbr) parts.push(`${n} ${abbr}`)
  }
  if (parts.length === 0) return null
  return `EVs: ${parts.join(' / ')}`
}

function moveToShowdownName(move) {
  if (!move) return null
  const raw = (move.displayName || move.name || '').trim()
  if (!raw) return null
  return raw
}

function abilityToShowdownName(ability) {
  if (!ability) return null
  return (ability.displayName || ability.name || '').trim() || null
}

function itemToShowdownName(item) {
  if (!item) return null
  return (item.displayName || item.name || '').trim() || null
}

/**
 * 單一成員（含 pokemon、ability、heldItem、nature、statPoints、moves）→ Showdown 文字塊。
 */
export function memberToShowdownPaste(member) {
  const p = member?.pokemon
  if (!p) return ''

  const species = apiNameToShowdownSpecies(p.apiName)
  const itemName = itemToShowdownName(member.heldItem)
  const line0 = itemName ? `${species} @ ${itemName}` : species

  const lines = [line0]

  const abilityEn = abilityToShowdownName(member.ability)
  if (abilityEn) lines.push(`Ability: ${abilityEn}`)

  lines.push('Level: 50')

  if (member.teraType) {
    const tera = String(member.teraType).trim()
    if (tera) {
      lines.push(`Tera Type: ${tera.charAt(0).toUpperCase() + tera.slice(1).toLowerCase()}`)
    }
  }

  const evLine = formatEvsLine(member.statPoints)
  if (evLine) lines.push(evLine)

  lines.push(`${natureIdToShowdownLabel(member.nature)} Nature`)

  const moves = Array.isArray(member.moves) ? member.moves : []
  for (let i = 0; i < 4; i++) {
    const name = moveToShowdownName(moves[i])
    if (name) lines.push(`- ${name}`)
  }

  return lines.join('\n')
}

/**
 * 多隻成員（已篩選有 pokemon、且依出戰順序）→ 完整隊伍貼上文字。
 */
export function teamToShowdownPaste(members) {
  if (!Array.isArray(members) || members.length === 0) return ''
  return members.map((m) => memberToShowdownPaste(m)).filter(Boolean).join('\n\n')
}
