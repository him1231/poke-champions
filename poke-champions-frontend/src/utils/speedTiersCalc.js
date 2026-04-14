/**
 * 與速度線速查頁相同假設：無努力 +20、滿速 +52、急速 floor(滿速×1.1)。
 */

export function calcSpeeds(baseSpd) {
  const b = Number(baseSpd) || 0
  const noEv = b + 20
  const maxSpd = b + 52
  const extreme = Math.floor(maxSpd * 1.1)
  return { base: b, noEv, maxSpd, extreme }
}

export function calcDoubled(baseSpd) {
  const { maxSpd } = calcSpeeds(baseSpd)
  return {
    maxDoubled: maxSpd * 2,
    extremeDoubled: Math.floor(maxSpd * 2 * 1.1),
  }
}

/**
 * 分組依「種族速度」區間（非計算後急速）。
 */
export function tierIndexByBase(baseSpd) {
  const b = Number(baseSpd) || 0
  if (b >= 120) return 0
  if (b >= 110) return 1
  if (b >= 100) return 2
  if (b >= 90) return 3
  if (b >= 80) return 4
  if (b >= 70) return 5
  if (b >= 60) return 6
  if (b >= 42) return 7
  return 8
}
