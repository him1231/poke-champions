import { createI18n } from 'vue-i18n'
import zhTW from './locales/zh-TW.json'
import en from './locales/en.json'
import ja from './locales/ja.json'

const STORAGE_KEY = 'poke-lang'
const SUPPORTED_LOCALES = ['zh-TW', 'en', 'ja']

function detectLocale() {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved && SUPPORTED_LOCALES.includes(saved)) return saved

  const nav = navigator.language || navigator.userLanguage || ''
  if (nav.startsWith('ja')) return 'ja'
  if (nav.startsWith('en')) return 'en'
  return 'zh-TW'
}

const i18n = createI18n({
  legacy: false,
  locale: detectLocale(),
  fallbackLocale: 'zh-TW',
  messages: { 'zh-TW': zhTW, en, ja },
})

export function setLocale(locale) {
  if (!SUPPORTED_LOCALES.includes(locale)) return
  i18n.global.locale.value = locale
  localStorage.setItem(STORAGE_KEY, locale)
  document.documentElement.lang = locale
}

export { SUPPORTED_LOCALES }
export default i18n
