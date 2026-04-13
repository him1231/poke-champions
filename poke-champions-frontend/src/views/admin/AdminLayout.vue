<script setup>
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

function logout() {
  sessionStorage.removeItem('admin_token')
  router.replace('/admin/login')
}

const navItems = [
  { path: '/admin', label: '儀表板', icon: 'dashboard', exact: true },
  { path: '/admin/teams', label: '隊伍管理', icon: 'list_alt' },
]

function isActive(item) {
  if (item.exact) return route.path === item.path
  return route.path.startsWith(item.path)
}
</script>

<template>
  <div class="admin-layout">
    <aside class="admin-sidebar">
      <div class="sidebar-brand">
        <span class="material-symbols-rounded">admin_panel_settings</span>
        <span class="brand-text">Admin</span>
      </div>

      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          :class="['nav-item', { active: isActive(item) }]"
        >
          <span class="material-symbols-rounded">{{ item.icon }}</span>
          {{ item.label }}
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <router-link to="/" class="nav-item">
          <span class="material-symbols-rounded">home</span>
          返回前台
        </router-link>
        <button class="nav-item logout-btn" @click="logout">
          <span class="material-symbols-rounded">logout</span>
          登出
        </button>
      </div>
    </aside>

    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: var(--bg-base, #0f172a);
}

.admin-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: var(--bg-card, #1e293b);
  border-right: 1px solid var(--border, #334155);
  display: flex;
  flex-direction: column;
  padding: 20px 12px;
  position: sticky;
  top: 0;
  height: 100vh;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin-bottom: 24px;
}
.sidebar-brand .material-symbols-rounded {
  font-size: 28px;
  color: var(--accent, #f97316);
}
.brand-text {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-primary, #f1f5f9);
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--text-muted, #94a3b8);
  font-size: 0.88rem;
  text-decoration: none;
  transition: all 0.15s;
  cursor: pointer;
  border: none;
  background: none;
  font-family: inherit;
  width: 100%;
  text-align: left;
}
.nav-item .material-symbols-rounded { font-size: 20px; }
.nav-item:hover {
  background: rgba(255,255,255,0.05);
  color: var(--text-primary, #f1f5f9);
}
.nav-item.active {
  background: var(--accent-soft, rgba(249,115,22,0.12));
  color: var(--accent, #f97316);
}

.sidebar-footer {
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-top: 1px solid var(--border, #334155);
  padding-top: 12px;
  margin-top: 12px;
}

.logout-btn:hover { color: #ef4444; }

.admin-main {
  flex: 1;
  padding: 32px;
  min-width: 0;
  overflow-x: hidden;
}

@media (max-width: 768px) {
  .admin-layout { flex-direction: column; }
  .admin-sidebar {
    width: 100%;
    height: auto;
    position: relative;
    flex-direction: row;
    align-items: center;
    padding: 12px;
    gap: 8px;
    overflow-x: auto;
  }
  .sidebar-brand { margin-bottom: 0; }
  .sidebar-nav { flex-direction: row; flex: unset; }
  .sidebar-footer {
    flex-direction: row;
    border-top: none;
    border-left: 1px solid var(--border, #334155);
    padding-top: 0;
    padding-left: 12px;
    margin-top: 0;
    margin-left: 12px;
  }
  .admin-main { padding: 20px; }
}
</style>
