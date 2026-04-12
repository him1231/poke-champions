/**
 * 性格網格：列 = 提升的能力，欄 = 降低的能力（與朱／紫介面一致，不含 HP）。
 * 鍵名與後端 / statPoints 一致。
 */

export const NATURE_GRID_STAT_KEYS = [
  'attack',
  'defense',
  'specialAttack',
  'specialDefense',
  'speed',
]

/** 頂部「降低」欄標題（對應欄 index） */
export const NATURE_GRID_HEADERS_DEC = ['攻擊', '防禦', '特攻', '特防', '速度']

/** 左側「提升」列標題（對應列 index） */
export const NATURE_GRID_HEADERS_INC = ['攻擊', '防禦', '特攻', '特防', '速度']

/**
 * 5×5 網格：grid[row][col] = 性格 id（英文）或 null（對角無按鈕格）
 * row = 提升、col = 降低
 */
export const NATURE_GRID = [
  ['serious', 'lonely', 'adamant', 'naughty', 'brave'],
  ['bold', null, 'impish', 'lax', 'relaxed'],
  ['modest', 'mild', null, 'rash', 'quiet'],
  ['calm', 'gentle', 'careful', null, 'sassy'],
  ['timid', 'hasty', 'jolly', 'naive', null],
]

/** 各性格倍率（未列能力視為 1.0）；HP 不受性格影響 */
export const NATURE_DEFS = {
  serious: { labelZh: '認真', mult: {} },
  lonely: { labelZh: '怕寂寞', mult: { attack: 1.1, defense: 0.9 } },
  adamant: { labelZh: '固執', mult: { attack: 1.1, specialAttack: 0.9 } },
  naughty: { labelZh: '任性', mult: { attack: 1.1, specialDefense: 0.9 } },
  brave: { labelZh: '勇敢', mult: { attack: 1.1, speed: 0.9 } },
  bold: { labelZh: '大膽', mult: { defense: 1.1, attack: 0.9 } },
  impish: { labelZh: '頑皮', mult: { defense: 1.1, specialAttack: 0.9 } },
  lax: { labelZh: '悠閒', mult: { defense: 1.1, specialDefense: 0.9 } },
  relaxed: { labelZh: '懶惰', mult: { defense: 1.1, speed: 0.9 } },
  modest: { labelZh: '內斂', mult: { specialAttack: 1.1, attack: 0.9 } },
  mild: { labelZh: '慢吞吞', mult: { specialAttack: 1.1, defense: 0.9 } },
  rash: { labelZh: '馬虎', mult: { specialAttack: 1.1, specialDefense: 0.9 } },
  quiet: { labelZh: '害羞', mult: { specialAttack: 1.1, speed: 0.9 } },
  calm: { labelZh: '溫和', mult: { specialDefense: 1.1, attack: 0.9 } },
  gentle: { labelZh: '溫順', mult: { specialDefense: 1.1, defense: 0.9 } },
  careful: { labelZh: '慎重', mult: { specialDefense: 1.1, specialAttack: 0.9 } },
  sassy: { labelZh: '自大', mult: { specialDefense: 1.1, speed: 0.9 } },
  timid: { labelZh: '膽小', mult: { speed: 1.1, attack: 0.9 } },
  hasty: { labelZh: '急躁', mult: { speed: 1.1, defense: 0.9 } },
  jolly: { labelZh: '爽朗', mult: { speed: 1.1, specialAttack: 0.9 } },
  naive: { labelZh: '天真', mult: { speed: 1.1, specialDefense: 0.9 } },
}

/** 舊下拉用：依網格內出現 id 產生列表（可選） */
export const NATURES_FLAT = Object.entries(NATURE_DEFS).map(([id, def]) => ({
  id,
  labelZh: def.labelZh,
  mult: def.mult,
}))

export function getNatureMultiplier(natureId, statKey) {
  if (statKey === 'hp') return 1
  const def = NATURE_DEFS[natureId]
  if (!def) return 1
  return def.mult?.[statKey] ?? 1
}
