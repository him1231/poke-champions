import { useI18n } from 'vue-i18n'

export function useLocalePath() {
  const { locale } = useI18n()

  function localePath(path) {
    if (path === '/') return `/${locale.value}`
    const p = path.startsWith('/') ? path : `/${path}`
    return `/${locale.value}${p}`
  }

  return { localePath }
}
