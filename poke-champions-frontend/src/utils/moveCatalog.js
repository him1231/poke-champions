import { moveRosterApi } from '../api/moveRoster'

let cachedCatalog = null
let inflight = null

export async function getMoveCatalog() {
  if (cachedCatalog) return cachedCatalog
  if (inflight) return inflight
  inflight = moveRosterApi
    .getMoves()
    .then((res) => {
      cachedCatalog = res.data
      inflight = null
      return cachedCatalog
    })
    .catch((e) => {
      inflight = null
      throw e
    })
  return inflight
}

export function mergeMoveChineseFromCatalog(moves, catalog) {
  if (!moves?.length) return moves || []
  if (!catalog?.length) return moves
  const byName = new Map(catalog.map((m) => [m.name, m]))
  return moves.map((m) => {
    const full = byName.get(m.name)
    if (!full) return m
    return {
      ...m,
      chineseName: m.chineseName || full.chineseName,
      chineseDescription: m.chineseDescription || full.chineseDescription,
    }
  })
}
