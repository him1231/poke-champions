<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { setLocale, SUPPORTED_LOCALES } from './i18n'
import { useLocalePath } from './composables/useLocalePath'
import ChangelogBell from './components/ChangelogBell.vue'
import { TEAM_SHARING_ENABLED } from './api/staticApi'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const { localePath } = useLocalePath()
const mobileMenuOpen = ref(false)
const teamMenuOpen = ref(false)
const toolsMenuOpen = ref(false)
const teamDropdownRef = ref(null)
const toolsDropdownRef = ref(null)

function switchLocale(newLocale) {
  const currentPath = route.path
  const stripped = currentPath.replace(/^\/(zh-TW|en|ja)/, '') || '/'
  setLocale(newLocale)
  router.push(`/${newLocale}${stripped === '/' ? '' : stripped}`)
}

const isAdminRoute = computed(() => route.path.startsWith('/admin'))

const pathTail = computed(() => {
  const m = route.path.match(/^\/(zh-TW|en|ja)(\/.*)?$/)
  return m && m[2] ? m[2] : '/'
})

const isTeamNavActive = computed(() => {
  const p = pathTail.value
  return p.startsWith('/team-builder') || p.startsWith('/teams')
})

const isToolsNavActive = computed(() => {
  const p = pathTail.value
  return (
    p.startsWith('/types') ||
    p.startsWith('/speed-tiers') ||
    p.startsWith('/speed-compare') ||
    p.startsWith('/damage-calc')
  )
})

const simpleNav = computed(() => [
  { name: t('nav.home'), path: localePath('/'), icon: 'home', exact: true },
  { name: t('nav.pokedex'), path: localePath('/pokemon'), icon: 'menu_book', exact: false },
  { name: t('nav.moves'), path: localePath('/moves'), icon: 'bolt', exact: false },
])

const teamNavChildren = computed(() => {
  const items = [
    { name: t('nav.teamBuilder'), path: localePath('/team-builder'), icon: 'groups' },
  ]
  if (TEAM_SHARING_ENABLED) {
    items.push({ name: t('nav.teams'), path: localePath('/teams'), icon: 'explore' })
  }
  return items
})

const toolsNavChildren = computed(() => [
  { name: t('nav.types'), path: localePath('/types'), icon: 'shield' },
  { name: t('nav.speedTiers'), path: localePath('/speed-tiers'), icon: 'speed' },
  { name: t('nav.speedCompare'), path: localePath('/speed-compare'), icon: 'compare_arrows' },
  { name: t('nav.damageCalc'), path: localePath('/damage-calc'), icon: 'calculate' },
])

const footerLinks = computed(() => [
  ...simpleNav.value,
  ...teamNavChildren.value,
  ...toolsNavChildren.value,
])

function closeMenus() {
  teamMenuOpen.value = false
  toolsMenuOpen.value = false
}

function toggleTeamMenu() {
  toolsMenuOpen.value = false
  teamMenuOpen.value = !teamMenuOpen.value
}

function toggleToolsMenu() {
  teamMenuOpen.value = false
  toolsMenuOpen.value = !toolsMenuOpen.value
}

function afterNavClick() {
  mobileMenuOpen.value = false
  closeMenus()
}

function onGlobalPointerDown(e) {
  if (!teamMenuOpen.value && !toolsMenuOpen.value) return
  const el = e.target
  if (teamDropdownRef.value?.contains(el)) return
  if (toolsDropdownRef.value?.contains(el)) return
  closeMenus()
}

onMounted(() => document.addEventListener('mousedown', onGlobalPointerDown))
onUnmounted(() => document.removeEventListener('mousedown', onGlobalPointerDown))
</script>

<template>
  <div class="app">
    <header v-if="!isAdminRoute" class="navbar">
      <div class="container navbar-inner">
        <router-link to="/" class="logo" @click="afterNavClick">
          <div class="logo-pokeball">
            <div class="pokeball-top"></div>
            <div class="pokeball-center"></div>
          </div>
          <span class="logo-text">Poké Champions</span>
        </router-link>

        <nav class="nav-links" :class="{ open: mobileMenuOpen }">
          <router-link
            v-for="link in simpleNav"
            :key="link.path"
            :to="link.path"
            custom
            v-slot="{ href, navigate, isExactActive, isActive }"
          >
            <a
              :href="href"
              class="nav-link"
              :class="link.exact
                ? { 'router-link-exact-active': isExactActive }
                : { 'router-link-active': isActive }"
              @click.prevent="navigate(); afterNavClick()"
            >
              <span class="material-symbols-rounded nav-icon">{{ link.icon }}</span>
              <span>{{ link.name }}</span>
            </a>
          </router-link>

          <div ref="teamDropdownRef" class="nav-dropdown">
            <button
              type="button"
              class="nav-dropdown-trigger"
              :class="{ active: isTeamNavActive, open: teamMenuOpen }"
              @click.stop="toggleTeamMenu"
            >
              <span class="material-symbols-rounded nav-icon">groups</span>
              <span>{{ t('nav.teamMenu') }}</span>
              <span class="material-symbols-rounded nav-chevron">expand_more</span>
            </button>
            <div v-show="teamMenuOpen" class="nav-dropdown-panel">
              <router-link
                v-for="c in teamNavChildren"
                :key="c.path"
                :to="c.path"
                class="nav-dropdown-link"
                @click="afterNavClick"
              >
                <span class="material-symbols-rounded nav-icon">{{ c.icon }}</span>
                <span>{{ c.name }}</span>
              </router-link>
            </div>
          </div>

          <div ref="toolsDropdownRef" class="nav-dropdown">
            <button
              type="button"
              class="nav-dropdown-trigger"
              :class="{ active: isToolsNavActive, open: toolsMenuOpen }"
              @click.stop="toggleToolsMenu"
            >
              <span class="material-symbols-rounded nav-icon">construction</span>
              <span>{{ t('nav.toolsMenu') }}</span>
              <span class="material-symbols-rounded nav-chevron">expand_more</span>
            </button>
            <div v-show="toolsMenuOpen" class="nav-dropdown-panel">
              <router-link
                v-for="c in toolsNavChildren"
                :key="c.path"
                :to="c.path"
                class="nav-dropdown-link"
                @click="afterNavClick"
              >
                <span class="material-symbols-rounded nav-icon">{{ c.icon }}</span>
                <span>{{ c.name }}</span>
              </router-link>
            </div>
          </div>
        </nav>

        <div class="nav-actions">
          <ChangelogBell @panel-open="closeMenus" />
          <select :value="locale" @change="switchLocale($event.target.value)" class="lang-select">
          <option v-for="loc in SUPPORTED_LOCALES" :key="loc" :value="loc">{{ t(`langSwitcher.${loc}`) }}</option>
        </select>
        </div>

        <button type="button" class="menu-toggle" :aria-label="t('common.close')" @click="mobileMenuOpen = !mobileMenuOpen">
          <span class="hamburger" :class="{ open: mobileMenuOpen }"></span>
        </button>
      </div>
    </header>

    <main :class="isAdminRoute ? '' : 'main-content'">
      <router-view />
    </main>

    <footer v-if="!isAdminRoute" class="footer">
      <div class="container footer-inner">
        <div class="footer-brand">
          <span class="footer-logo">Poké Champions</span>
          <p>{{ t('footer.subtitle') }}</p>
        </div>
        <div class="footer-links">
          <router-link v-for="link in footerLinks" :key="link.path + link.name" :to="link.path">{{ link.name }}</router-link>
          <router-link :to="localePath('/about')">{{ t('footer.about') }}</router-link>
          <router-link :to="localePath('/privacy')">{{ t('footer.privacy') }}</router-link>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(15, 16, 35, 0.75);
  border-bottom: 1px solid var(--border);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
}

.navbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  position: relative;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 800;
  font-size: 1.15rem;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.logo-pokeball {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2.5px solid var(--text-muted);
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
}

.pokeball-top {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 50%;
  background: var(--accent);
  border-bottom: 2px solid var(--text-muted);
}

.pokeball-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--bg-primary);
  border: 2px solid var(--text-muted);
  z-index: 1;
}

.nav-links {
  display: flex;
  gap: 4px;
  align-items: stretch;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 12px;
  font-size: 0.88rem;
  font-weight: 500;
  color: var(--text-secondary);
  transition: all 0.2s ease;
  text-decoration: none;
}

.nav-icon {
  font-size: 18px;
}

.nav-link:hover {
  background: var(--bg-glass);
  color: var(--text-primary);
}

.nav-link.router-link-exact-active,
.nav-link.router-link-active {
  background: var(--accent-soft);
  color: var(--accent);
}

.nav-dropdown {
  position: relative;
}

.nav-dropdown-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px 8px 16px;
  border-radius: 12px;
  font-size: 0.88rem;
  font-weight: 500;
  color: var(--text-secondary);
  background: transparent;
  border: none;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s ease;
}

.nav-dropdown-trigger:hover {
  background: var(--bg-glass);
  color: var(--text-primary);
}

.nav-dropdown-trigger.active {
  background: var(--accent-soft);
  color: var(--accent);
}

.nav-dropdown-trigger.open {
  background: var(--bg-glass);
  color: var(--text-primary);
}

.nav-chevron {
  font-size: 18px;
  margin-left: -2px;
  transition: transform 0.2s ease;
}

.nav-dropdown-trigger.open .nav-chevron {
  transform: rotate(180deg);
}

.nav-dropdown-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  min-width: 200px;
  padding: 6px;
  background: rgba(22, 24, 48, 0.98);
  border: 1px solid var(--border);
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
  z-index: 200;
}

.nav-dropdown-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 0.86rem;
  font-weight: 500;
  color: var(--text-secondary);
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}

.nav-dropdown-link:hover {
  background: var(--bg-glass);
  color: var(--text-primary);
}

.nav-dropdown-link.router-link-active {
  background: var(--accent-soft);
  color: var(--accent);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.lang-select {
  padding: 6px 10px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 0.82rem;
  outline: none;
  cursor: pointer;
}
.lang-select option {
  background: var(--bg-secondary);
}

.menu-toggle {
  display: none;
  width: 40px;
  height: 40px;
  background: none;
  border-radius: 10px;
  position: relative;
  transition: background 0.2s;
}

.menu-toggle:hover {
  background: var(--bg-glass);
}

.hamburger,
.hamburger::before,
.hamburger::after {
  display: block;
  width: 20px;
  height: 2px;
  background: var(--text-primary);
  position: absolute;
  left: 10px;
  transition: all 0.3s ease;
  border-radius: 2px;
}

.hamburger { top: 19px; }
.hamburger::before { content: ''; top: -6px; }
.hamburger::after { content: ''; top: 6px; }
.hamburger.open { background: transparent; }
.hamburger.open::before { top: 0; transform: rotate(45deg); }
.hamburger.open::after { top: 0; transform: rotate(-45deg); }

.main-content {
  flex: 1;
  padding: 40px 0;
  animation: fadeIn 0.4s ease;
}

.footer {
  border-top: 1px solid var(--border);
  padding: 36px 0;
  margin-top: 40px;
}

.footer-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.footer-brand {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.footer-logo {
  font-weight: 700;
  font-size: 0.95rem;
  color: var(--text-secondary);
}

.footer-brand p {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.footer-links {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 20px;
}

.footer-links a {
  font-size: 0.82rem;
  color: var(--text-muted);
  transition: color 0.2s;
}

.footer-links a:hover {
  color: var(--text-primary);
}

@media (max-width: 768px) {
  .menu-toggle {
    display: block;
  }

  .nav-links {
    display: none;
    position: absolute;
    top: 64px;
    left: 0;
    right: 0;
    flex-direction: column;
    background: rgba(15, 16, 35, 0.95);
    backdrop-filter: var(--glass-blur);
    -webkit-backdrop-filter: var(--glass-blur);
    border-bottom: 1px solid var(--border);
    padding: 12px 20px;
    gap: 4px;
    align-items: stretch;
  }

  .nav-links.open {
    display: flex;
    animation: slideUp 0.25s ease;
  }

  .nav-dropdown-panel {
    position: static;
    margin-top: 4px;
    margin-bottom: 8px;
    box-shadow: none;
    border: 1px solid var(--border);
    background: rgba(15, 16, 35, 0.6);
  }

  .nav-dropdown-trigger {
    width: 100%;
    justify-content: flex-start;
  }

  .main-content {
    padding: 24px 0;
  }

  .footer-inner {
    flex-direction: column;
    text-align: center;
  }

  .footer-links {
    justify-content: center;
  }
}
</style>
