export function pokemonTypesForDisplay(p) {
  if (!p) return []
  if (p.typesInfo?.length) return p.typesInfo
  return (p.types || []).map((name) => ({ name, chineseName: name }))
}

/** 英文屬性名 → 與全域 style 一致：.type-badge.fire */
export function typeBadgeClasses(typeName) {
  const slug = String(typeName || '')
    .toLowerCase()
    .replace(/\s+/g, '-')
  return ['type-badge', slug]
}
