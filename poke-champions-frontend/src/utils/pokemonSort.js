export function comparePokemonByFormId(a, b) {
  const da = Number(a.nationalDexNumber ?? 0)
  const db = Number(b.nationalDexNumber ?? 0)
  if (da !== db) return da - db

  const fa = a.formId || ''
  const fb = b.formId || ''
  const na = /^(\d+)-(\d+)$/.exec(fa)
  const nb = /^(\d+)-(\d+)$/.exec(fb)

  if (na && nb) {
    const dexA = parseInt(na[1], 10)
    const dexB = parseInt(nb[1], 10)
    if (dexA !== dexB) return dexA - dexB
    return parseInt(na[2], 10) - parseInt(nb[2], 10)
  }
  if (na && !nb) return -1
  if (!na && nb) return 1
  return fa.localeCompare(fb, undefined, { numeric: true, sensitivity: 'base' })
}
