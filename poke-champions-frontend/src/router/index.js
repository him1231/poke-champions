import { createRouter, createWebHistory } from 'vue-router'
import i18n from '../i18n'
import { SUPPORTED_LOCALES, detectLocale, setLocale } from '../i18n'
import HomePage from '../views/HomePage.vue'
import PokemonListPage from '../views/PokemonListPage.vue'
import PokemonDetailPage from '../views/PokemonDetailPage.vue'
import MovesPage from '../views/MovesPage.vue'
import TypesPage from '../views/TypesPage.vue'
import SpeedTiersPage from '../views/SpeedTiersPage.vue'
import TeamBuilder from '../views/TeamBuilder.vue'
import TeamOverview from '../views/TeamOverview.vue'
import TeamListPage from '../views/TeamListPage.vue'
import TeamDetailPage from '../views/TeamDetailPage.vue'
import AboutPage from '../views/AboutPage.vue'
import PrivacyPage from '../views/PrivacyPage.vue'

const SITE = 'Poké Champions'
const ORIGIN = 'https://victorpoke-champions.com'
const GA_MEASUREMENT_ID = 'G-MN3EEBL4Y1'
const LOCALE_RE = SUPPORTED_LOCALES.map(l => l.replace('-', '\\-')).join('|')

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/',
      redirect: () => `/${detectLocale()}`,
    },
    {
      path: `/:locale(${LOCALE_RE})`,
      children: [
        {
          path: '',
          name: 'home',
          component: HomePage,
          meta: { titleKey: 'routes.home.title', descKey: 'routes.home.description' },
        },
        {
          path: 'pokemon',
          name: 'pokemon-list',
          component: PokemonListPage,
          meta: { titleKey: 'routes.pokemonList.title', descKey: 'routes.pokemonList.description' },
        },
        {
          path: 'pokemon/:apiName',
          name: 'pokemon-detail',
          component: PokemonDetailPage,
          meta: { titleKey: 'routes.pokemonDetail.title', descKey: 'routes.pokemonDetail.description' },
        },
        {
          path: 'moves',
          name: 'moves',
          component: MovesPage,
          meta: { titleKey: 'routes.moves.title', descKey: 'routes.moves.description' },
        },
        {
          path: 'types',
          name: 'types',
          component: TypesPage,
          meta: { titleKey: 'routes.types.title', descKey: 'routes.types.description' },
        },
        {
          path: 'speed-tiers',
          name: 'speed-tiers',
          component: SpeedTiersPage,
          meta: { titleKey: 'routes.speedTiers.title', descKey: 'routes.speedTiers.description' },
        },
        {
          path: 'team-builder',
          name: 'team-builder',
          component: TeamBuilder,
          meta: { titleKey: 'routes.teamBuilder.title', descKey: 'routes.teamBuilder.description' },
        },
        {
          path: 'team-overview',
          name: 'team-overview',
          component: TeamOverview,
          meta: { titleKey: 'routes.teamOverview.title', descKey: 'routes.teamOverview.description' },
        },
        {
          path: 'teams',
          name: 'team-list',
          component: TeamListPage,
          meta: { titleKey: 'routes.teamList.title', descKey: 'routes.teamList.description' },
        },
        {
          path: 'teams/:rentalCode',
          name: 'team-detail',
          component: TeamDetailPage,
          meta: { titleKey: 'routes.teamDetail.title', descKey: 'routes.teamDetail.description' },
        },
        {
          path: 'about',
          name: 'about',
          component: AboutPage,
          meta: { titleKey: 'routes.about.title', descKey: 'routes.about.description' },
        },
        {
          path: 'privacy',
          name: 'privacy',
          component: PrivacyPage,
          meta: { titleKey: 'routes.privacy.title', descKey: 'routes.privacy.description' },
        },
      ],
    },
    // ── Admin routes (no locale prefix) ──
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('../views/admin/AdminLogin.vue'),
      meta: { isAdmin: true },
    },
    {
      path: '/admin',
      component: () => import('../views/admin/AdminLayout.vue'),
      meta: { isAdmin: true },
      beforeEnter: (to, from, next) => {
        if (!sessionStorage.getItem('admin_token')) {
          next('/admin/login')
        } else {
          next()
        }
      },
      children: [
        {
          path: '',
          name: 'admin-dashboard',
          component: () => import('../views/admin/AdminDashboard.vue'),
        },
        {
          path: 'teams',
          name: 'admin-teams',
          component: () => import('../views/admin/AdminTeams.vue'),
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: (to) => {
        const segments = to.path.split('/').filter(Boolean)
        if (segments.length > 0 && SUPPORTED_LOCALES.includes(segments[0])) {
          return `/${segments[0]}`
        }
        return `/${detectLocale()}${to.path}`
      },
    },
  ],
})

router.beforeEach((to) => {
  if (to.path.startsWith('/admin')) return
  const locale = to.params.locale
  if (locale && SUPPORTED_LOCALES.includes(locale)) {
    if (i18n.global.locale.value !== locale) {
      setLocale(locale)
    }
  }
})

function setMeta(name, content) {
  let el = document.querySelector(`meta[name="${name}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute('name', name)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

function setProperty(property, content) {
  let el = document.querySelector(`meta[property="${property}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute('property', property)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

const OG_LOCALE_MAP = { 'zh-TW': 'zh_TW', en: 'en_US', ja: 'ja_JP' }

function updateHreflang(routePath) {
  document.querySelectorAll('link[rel="alternate"][hreflang]').forEach((el) => el.remove())

  const localePrefix = new RegExp(`^/(${SUPPORTED_LOCALES.map(l => l.replace('-', '\\-')).join('|')})`)
  const pathWithoutLocale = routePath.replace(localePrefix, '') || '/'

  SUPPORTED_LOCALES.forEach((loc) => {
    const link = document.createElement('link')
    link.rel = 'alternate'
    link.hreflang = loc
    link.href = `${ORIGIN}/${loc}${pathWithoutLocale === '/' ? '' : pathWithoutLocale}`
    document.head.appendChild(link)
  })

  const xDefault = document.createElement('link')
  xDefault.rel = 'alternate'
  xDefault.hreflang = 'x-default'
  xDefault.href = `${ORIGIN}/en${pathWithoutLocale === '/' ? '' : pathWithoutLocale}`
  document.head.appendChild(xDefault)
}

router.afterEach((to) => {
  if (to.path.startsWith('/admin')) {
    document.title = 'Admin — Poké Champions'
    return
  }
  const { t } = i18n.global
  const title = to.meta.titleKey ? t(to.meta.titleKey) : SITE
  const description = to.meta.descKey ? t(to.meta.descKey) : ''
  const locale = to.params.locale || detectLocale()

  document.documentElement.lang = locale

  if (typeof window.gtag === 'function') {
    window.gtag('event', 'page_view', {
      page_title: title,
      page_path: to.fullPath,
      page_location: `${window.location.origin}${to.fullPath}`,
    })
  }

  document.title = title
  setMeta('description', description)

  setProperty('og:title', title)
  setProperty('og:description', description)
  setProperty('og:url', `${ORIGIN}${to.path}`)
  setProperty('og:locale', OG_LOCALE_MAP[locale] || locale)

  SUPPORTED_LOCALES.filter((l) => l !== locale).forEach((alt) => {
    setProperty('og:locale:alternate', OG_LOCALE_MAP[alt] || alt)
  })

  setMeta('twitter:title', title)
  setMeta('twitter:description', description)

  updateHreflang(to.path)
})

export default router
