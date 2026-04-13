<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { setLocale, SUPPORTED_LOCALES } from './i18n'
import { useLocalePath } from './composables/useLocalePath'

const { t, locale } = useI18n()
const router = useRouter()
const route = useRoute()
const { localePath } = useLocalePath()
const mobileMenuOpen = ref(false)

function switchLocale(newLocale) {
  const currentPath = route.path
  const stripped = currentPath.replace(/^\/(zh-TW|en|ja)/, '') || '/'
  setLocale(newLocale)
  router.push(`/${newLocale}${stripped === '/' ? '' : stripped}`)
}

const isAdminRoute = computed(() => route.path.startsWith('/admin'))

const navLinks = computed(() => [
  { name: t('nav.home'), path: localePath('/'), icon: 'home' },
  { name: t('nav.pokedex'), path: localePath('/pokemon'), icon: 'menu_book' },
  { name: t('nav.moves'), path: localePath('/moves'), icon: 'bolt' },
  { name: t('nav.types'), path: localePath('/types'), icon: 'shield' },
  { name: t('nav.teamBuilder'), path: localePath('/team-builder'), icon: 'groups' },
  { name: t('nav.teams'), path: localePath('/teams'), icon: 'explore' },
])
</script>

<template>
  <div class="app">
    <header v-if="!isAdminRoute" class="navbar">
      <div class="container navbar-inner">
        <router-link to="/" class="logo" @click="mobileMenuOpen = false">
          <div class="logo-pokeball">
            <div class="pokeball-top"></div>
            <div class="pokeball-center"></div>
          </div>
          <span class="logo-text">Poké Champions</span>
        </router-link>

        <nav class="nav-links" :class="{ open: mobileMenuOpen }">
          <router-link
            v-for="link in navLinks"
            :key="link.path"
            :to="link.path"
            class="nav-link"
            @click="mobileMenuOpen = false"
          >
            <span class="material-symbols-rounded nav-icon">{{ link.icon }}</span>
            <span>{{ link.name }}</span>
          </router-link>
        </nav>

        <select :value="locale" @change="switchLocale($event.target.value)" class="lang-select">
          <option v-for="loc in SUPPORTED_LOCALES" :key="loc" :value="loc">{{ t(`langSwitcher.${loc}`) }}</option>
        </select>

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
          <router-link v-for="link in navLinks" :key="link.path" :to="link.path">{{ link.name }}</router-link>
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
}

.nav-icon {
  font-size: 18px;
}

.nav-link:hover {
  background: var(--bg-glass);
  color: var(--text-primary);
}

.nav-link.router-link-exact-active {
  background: var(--accent-soft);
  color: var(--accent);
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
  gap: 20px;
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
  }

  .nav-links.open {
    display: flex;
    animation: slideUp 0.25s ease;
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
