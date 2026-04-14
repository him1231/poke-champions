<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'

/** 發布新版本：在 i18n `changelog.versions` 陣列「最前」新增一筆；小紅點會比對該筆的 `version` 與此鍵的已讀紀錄。 */
const LAST_SEEN_KEY = 'pokeChangelogLastSeenVersion'

const emit = defineEmits(['panel-open'])

const { t, tm } = useI18n()
const open = ref(false)
const wrapRef = ref(null)
const hasUnread = ref(false)

const versions = computed(() => {
  const raw = tm('changelog.versions')
  if (!Array.isArray(raw)) return []
  return raw
    .map((entry, idx) => {
      if (!entry || typeof entry !== 'object') return null
      const items = Array.isArray(entry.items)
        ? entry.items.filter((x) => typeof x === 'string')
        : []
      const n = raw.length
      let avatarKind = 'patch'
      if (idx === 0) avatarKind = 'latest'
      else if (idx === n - 1) avatarKind = 'launch'
      return {
        key: `${entry.version || idx}-${entry.date || idx}`,
        version: typeof entry.version === 'string' ? entry.version : '',
        headline: typeof entry.headline === 'string' ? entry.headline : '',
        date: typeof entry.date === 'string' ? entry.date : '',
        items,
        isLatest: idx === 0,
        avatarKind,
      }
    })
    .filter(Boolean)
})

const latestVersionId = computed(() => versions.value[0]?.version || '')

function refreshUnread() {
  const id = latestVersionId.value
  if (!id) {
    hasUnread.value = false
    return
  }
  try {
    const seen = localStorage.getItem(LAST_SEEN_KEY)
    hasUnread.value = seen !== id
  } catch {
    hasUnread.value = true
  }
}

function markSeen() {
  const id = latestVersionId.value
  if (!id) return
  try {
    localStorage.setItem(LAST_SEEN_KEY, id)
  } catch {
    /* ignore */
  }
  hasUnread.value = false
}

function onDocPointerDown(e) {
  if (!open.value) return
  const el = e.target
  if (wrapRef.value?.contains(el)) return
  open.value = false
}

function togglePanel() {
  open.value = !open.value
  if (open.value) {
    emit('panel-open')
    markSeen()
  }
}

function closePanel() {
  open.value = false
}

function avatarIcon(kind) {
  if (kind === 'latest') return 'bolt'
  if (kind === 'launch') return 'rocket_launch'
  return 'update'
}

onMounted(() => {
  refreshUnread()
  document.addEventListener('mousedown', onDocPointerDown)
})

onUnmounted(() => document.removeEventListener('mousedown', onDocPointerDown))

watch(latestVersionId, () => {
  refreshUnread()
})
</script>

<template>
  <div ref="wrapRef" class="changelog-bell-wrap">
    <button
      type="button"
      class="bell-btn"
      :class="{ active: open }"
      :aria-label="t('changelog.bellAria')"
      :aria-expanded="open"
      aria-haspopup="true"
      @click.stop="togglePanel"
    >
      <span class="material-symbols-rounded bell-icon">notifications</span>
      <span v-if="hasUnread" class="bell-dot" aria-hidden="true" />
    </button>

    <Transition name="changelog-pop">
      <div
        v-show="open"
        class="changelog-panel"
        role="dialog"
        :aria-label="t('changelog.title')"
      >
        <div class="panel-header">
          <h2 class="panel-title">{{ t('changelog.title') }}</h2>
          <button type="button" class="panel-close" :aria-label="t('common.close')" @click="closePanel">
            <span class="material-symbols-rounded">close</span>
          </button>
        </div>
        <!-- <p class="panel-sub">{{ t('changelog.panelHint') }}</p> -->
        <div class="panel-scroll">
          <article
            v-for="ver in versions"
            :key="ver.key"
            class="notif-card"
            :class="{ latest: ver.isLatest }"
          >
            <div
              class="notif-avatar"
              :class="{
                'avatar-latest': ver.avatarKind === 'latest',
                'avatar-patch': ver.avatarKind === 'patch',
                'avatar-launch': ver.avatarKind === 'launch',
              }"
              aria-hidden="true"
            >
              <span class="material-symbols-rounded">{{ avatarIcon(ver.avatarKind) }}</span>
            </div>
            <div class="notif-body">
              <div class="notif-top">
                <div class="notif-titles">
                  <span v-if="ver.isLatest" class="notif-badge">{{ t('changelog.badgeNew') }}</span>
                  <h3 class="notif-headline">{{ ver.headline }}</h3>
                </div>
                <div class="notif-meta">
                  <span class="notif-version">{{ ver.version }}</span>
                  <time class="notif-date" :datetime="ver.date">{{ ver.date }}</time>
                </div>
              </div>
              <ul v-if="ver.items.length" class="notif-list">
                <li v-for="(line, li) in ver.items" :key="li">{{ line }}</li>
              </ul>
            </div>
          </article>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.changelog-bell-wrap {
  position: relative;
  flex-shrink: 0;
}

.bell-btn {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.bell-btn:hover,
.bell-btn.active {
  background: var(--bg-glass);
  color: var(--text-primary);
}

.bell-btn.active {
  color: var(--accent);
}

.bell-icon {
  font-size: 22px;
}

.bell-dot {
  position: absolute;
  top: 7px;
  right: 7px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e53935;
  box-shadow: 0 0 0 2px rgba(15, 16, 35, 0.95);
}

.changelog-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: min(380px, calc(100vw - 32px));
  max-height: min(72vh, 520px);
  display: flex;
  flex-direction: column;
  background: rgba(22, 24, 48, 0.98);
  border: 1px solid var(--border);
  border-radius: 14px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.45);
  z-index: 250;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px 10px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.panel-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-primary);
}

.panel-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.panel-close:hover {
  background: var(--bg-glass);
  color: var(--text-primary);
}

.panel-close .material-symbols-rounded {
  font-size: 20px;
}

.panel-sub {
  margin: 0;
  padding: 8px 14px 10px;
  font-size: 0.75rem;
  line-height: 1.45;
  color: var(--text-muted);
  flex-shrink: 0;
}

.panel-scroll {
  overflow-y: auto;
  padding: 8px 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notif-card {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 10px;
  border-radius: 12px;
  background: var(--bg-card);
  border: 1px solid var(--border);
}

.notif-card.latest {
  border-color: rgba(98, 188, 90, 0.35);
}

.notif-avatar {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.notif-avatar .material-symbols-rounded {
  font-size: 20px;
  color: #fff;
}

.avatar-latest {
  background: linear-gradient(145deg, #62bc5a, #3d8b45);
  box-shadow: 0 0 0 2px rgba(98, 188, 90, 0.2);
}

.avatar-patch {
  background: linear-gradient(145deg, #7e6bc4, #5347a0);
  box-shadow: 0 0 0 2px rgba(126, 107, 196, 0.2);
}

.avatar-launch {
  background: linear-gradient(145deg, #4a90d9, #2e5f96);
  box-shadow: 0 0 0 2px rgba(74, 144, 217, 0.2);
}

.notif-body {
  flex: 1;
  min-width: 0;
}

.notif-top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 6px 10px;
  margin-bottom: 6px;
}

.notif-titles {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  min-width: 0;
}

.notif-badge {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent);
}

.notif-headline {
  margin: 0;
  font-size: 0.86rem;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.35;
}

.notif-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 1px;
  flex-shrink: 0;
}

.notif-version {
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--text-muted);
}

.notif-date {
  font-size: 0.68rem;
  color: var(--text-muted);
}

.notif-list {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 0.78rem;
  line-height: 1.5;
  color: var(--text-secondary);
}

.notif-list li {
  position: relative;
  padding-left: 12px;
  margin-bottom: 4px;
}

.notif-list li:last-child {
  margin-bottom: 0;
}

.notif-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0.55em;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--accent);
  opacity: 0.7;
}

.changelog-pop-enter-active,
.changelog-pop-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.changelog-pop-enter-from,
.changelog-pop-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 768px) {
  .changelog-panel {
    position: fixed;
    top: 72px;
    right: 12px;
    left: 12px;
    width: auto;
    max-height: min(75vh, 480px);
  }
}
</style>
