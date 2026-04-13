import i18n from '../i18n'

export function localizedName(item) {
  if (!item) return ''
  const locale = i18n.global.locale.value
  if (locale === 'ja') return item.japaneseName || item.chineseName || item.displayName || ''
  if (locale === 'zh-TW') return item.chineseName || item.displayName || ''
  return item.displayName || item.chineseName || ''
}

export function localizedDescription(item) {
  if (!item) return ''
  const locale = i18n.global.locale.value
  if (locale === 'ja') return item.japaneseDescription || item.chineseDescription || item.description || ''
  if (locale === 'zh-TW') return item.chineseDescription || item.description || ''
  return item.description || item.chineseDescription || ''
}

export function localizedEffect(item) {
  if (!item) return ''
  const locale = i18n.global.locale.value
  if (locale === 'ja') return item.japaneseEffect || item.chineseEffect || item.effect || ''
  if (locale === 'zh-TW') return item.chineseEffect || item.effect || ''
  return item.effect || item.chineseEffect || ''
}

export function localizedTypeName(typeObj) {
  if (!typeObj) return ''
  const locale = i18n.global.locale.value
  if (locale === 'ja') return typeObj.japaneseName || typeObj.chineseName || typeObj.name || ''
  if (locale === 'zh-TW') return typeObj.chineseName || typeObj.name || ''
  return typeObj.name || ''
}
