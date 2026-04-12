<script setup>
import { ref } from 'vue'

const mobileMenuOpen = ref(false)

const navLinks = [
  { name: '首頁', path: '/', icon: 'home' },
  { name: '圖鑑', path: '/pokemon', icon: 'menu_book' },
  { name: '招式', path: '/moves', icon: 'bolt' },
  { name: '屬性', path: '/types', icon: 'shield' },
  { name: '組隊', path: '/team-builder', icon: 'groups' },
]
</script>

<template>
  <div class="app">
    <header class="navbar">
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

        <button type="button" class="menu-toggle" aria-label="選單" @click="mobileMenuOpen = !mobileMenuOpen">
          <span class="hamburger" :class="{ open: mobileMenuOpen }"></span>
        </button>
      </div>
    </header>

    <main class="main-content">
      <router-view />
    </main>

    <footer class="footer">
      <div class="container footer-inner">
        <div class="footer-brand">
          <span class="footer-logo">Poké Champions</span>
          <p>寶可夢冠軍賽資料查詢工具</p>
        </div>
        <div class="footer-links">
          <router-link v-for="link in navLinks" :key="link.path" :to="link.path">{{ link.name }}</router-link>
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
