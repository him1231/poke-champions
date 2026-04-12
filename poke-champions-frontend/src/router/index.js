import { createRouter, createWebHistory } from 'vue-router'
import HomePage from '../views/HomePage.vue'
import PokemonListPage from '../views/PokemonListPage.vue'
import PokemonDetailPage from '../views/PokemonDetailPage.vue'
import MovesPage from '../views/MovesPage.vue'
import TypesPage from '../views/TypesPage.vue'
import TeamBuilder from '../views/TeamBuilder.vue'
import TeamOverview from '../views/TeamOverview.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomePage },
    { path: '/pokemon', name: 'pokemon-list', component: PokemonListPage },
    { path: '/pokemon/:apiName', name: 'pokemon-detail', component: PokemonDetailPage },
    { path: '/moves', name: 'moves', component: MovesPage },
    { path: '/types', name: 'types', component: TypesPage },
    { path: '/team-builder', name: 'team-builder', component: TeamBuilder },
    { path: '/team-overview', name: 'team-overview', component: TeamOverview },
  ],
})
