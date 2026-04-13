import { createRouter, createWebHistory } from 'vue-router'
import i18n from '../i18n'
import HomePage from '../views/HomePage.vue'
import PokemonListPage from '../views/PokemonListPage.vue'
import PokemonDetailPage from '../views/PokemonDetailPage.vue'
import MovesPage from '../views/MovesPage.vue'
import TypesPage from '../views/TypesPage.vue'
import TeamBuilder from '../views/TeamBuilder.vue'
import TeamOverview from '../views/TeamOverview.vue'
import AboutPage from '../views/AboutPage.vue'
import PrivacyPage from '../views/PrivacyPage.vue'

const SITE = 'Poké Champions'
const GA_MEASUREMENT_ID = 'G-MN3EEBL4Y1'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomePage,
      meta: { titleKey: 'routes.home.title', descKey: 'routes.home.description' },
    },
    {
      path: '/pokemon',
      name: 'pokemon-list',
      component: PokemonListPage,
      meta: { titleKey: 'routes.pokemonList.title', descKey: 'routes.pokemonList.description' },
    },
    {
      path: '/pokemon/:apiName',
      name: 'pokemon-detail',
      component: PokemonDetailPage,
      meta: { titleKey: 'routes.pokemonDetail.title', descKey: 'routes.pokemonDetail.description' },
    },
    {
      path: '/moves',
      name: 'moves',
      component: MovesPage,
      meta: { titleKey: 'routes.moves.title', descKey: 'routes.moves.description' },
    },
    {
      path: '/types',
      name: 'types',
      component: TypesPage,
      meta: { titleKey: 'routes.types.title', descKey: 'routes.types.description' },
    },
    {
      path: '/team-builder',
      name: 'team-builder',
      component: TeamBuilder,
      meta: { titleKey: 'routes.teamBuilder.title', descKey: 'routes.teamBuilder.description' },
    },
    {
      path: '/team-overview',
      name: 'team-overview',
      component: TeamOverview,
      meta: { titleKey: 'routes.teamOverview.title', descKey: 'routes.teamOverview.description' },
    },
    {
      path: '/about',
      name: 'about',
      component: AboutPage,
      meta: { titleKey: 'routes.about.title', descKey: 'routes.about.description' },
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: PrivacyPage,
      meta: { titleKey: 'routes.privacy.title', descKey: 'routes.privacy.description' },
    },
  ],
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

router.afterEach((to) => {
  const { t } = i18n.global
  const title = to.meta.titleKey ? t(to.meta.titleKey) : SITE
  const description = to.meta.descKey ? t(to.meta.descKey) : ''

  document.documentElement.lang = i18n.global.locale.value

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
  setProperty('og:url', `https://victorpoke-champions.com${to.path}`)

  setMeta('twitter:title', title)
  setMeta('twitter:description', description)
})

export default router
