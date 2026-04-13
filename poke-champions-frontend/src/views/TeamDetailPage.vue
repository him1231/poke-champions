<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useLocalePath } from '../composables/useLocalePath'
import { useTeamPin } from '../composables/useTeamPin'
import { teamShareApi } from '../api/teamShareApi'
import { pokemonRosterApi } from '../api/pokemonRoster'
import { moveRosterApi } from '../api/moveRoster'
import { itemRosterApi } from '../api/itemRoster'
import { getPokemonImageUrl } from '../utils/pokemonImage'
import { typeBadgeClasses } from '../utils/pokemonTypesDisplay'
import { localizedName } from '../utils/localizedName'
import { NATURE_DEFS } from '../constants/pokemonNatures'
import {
  STAT_KEYS, STAT_COLORS, calcStat,
} from '../composables/useTeamStore'
import PinInputModal from '../components/PinInputModal.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const { localePath } = useLocalePath()
const { getSavedPin, verifyAndSave, hasSavedPin, removePin } = useTeamPin()

const rentalCode = computed(() => route.params.rentalCode)

const team = ref(null)
const loading = ref(true)
const notFound = ref(false)

const isEditing = ref(false)
const pinModalVisible = ref(false)
const pinError = ref('')
const pinLoading = ref(false)

const editTitle = ref('')
const editDescription = ref('')
const editIsPublic = ref(true)
const saving = ref(false)
const saveError = ref('')

const deleteConfirm = ref(false)
const deleting = ref(false)
const copied = ref(false)
let copiedTimer = null

const reportConfirmVisible = ref(false)
const reporting = ref(false)
const reported = ref(false)

const filledMembers = computed(() => {
  if (!team.value?.teamSnapshot) return []
  const snapshot = team.value.teamSnapshot
  return (Array.isArray(snapshot) ? snapshot : [])
    .map((m, idx) => ({ ...m, slotIdx: idx }))
    .filter(m => m.pokemon)
})

async function copyRentalCode() {
  try {
    await navigator.clipboard.writeText(team.value.rentalCode)
    copied.value = true
    clearTimeout(copiedTimer)
    copiedTimer = setTimeout(() => { copied.value = false }, 2000)
  } catch {}
}

const REPORT_KEY_PREFIX = 'reported_'
function hasReported(code) {
  return localStorage.getItem(REPORT_KEY_PREFIX + code) === '1'
}

function openReportConfirm() {
  if (hasReported(rentalCode.value) || reported.value) return
  reportConfirmVisible.value = true
}

async function submitReport() {
  reporting.value = true
  try {
    await teamShareApi.report(rentalCode.value)
    localStorage.setItem(REPORT_KEY_PREFIX + rentalCode.value, '1')
    reported.value = true
    reportConfirmVisible.value = false
  } catch {
    reportConfirmVisible.value = false
  } finally {
    reporting.value = false
  }
}

async function fetchTeam() {
  loading.value = true
  notFound.value = false
  try {
    const { data } = await teamShareApi.getByRentalCode(rentalCode.value)
    team.value = data
    enrichSnapshot()
  } catch (err) {
    if (err.response?.status === 404) notFound.value = true
    else console.error('Failed to fetch team', err)
  } finally {
    loading.value = false
  }
}

async function enrichSnapshot() {
  if (!team.value?.teamSnapshot) return
  try {
    const [pokRes, movRes, itmRes] = await Promise.all([
      pokemonRosterApi.getPokemonList(),
      moveRosterApi.getMoves(),
      itemRosterApi.getItems(),
    ])
    const pokByApi = Object.fromEntries(
      (pokRes.data || []).map(p => [p.apiName, p])
    )
    const movByName = Object.fromEntries(
      (movRes.data || []).map(m => [m.name, m])
    )
    const itmByName = Object.fromEntries(
      (itmRes.data || []).map(i => [i.name, i])
    )
    const snap = team.value.teamSnapshot
    if (!Array.isArray(snap)) return
    for (const member of snap) {
      if (member.pokemon) {
        const fp = pokByApi[member.pokemon.apiName]
        if (fp) Object.assign(member.pokemon, fp)
      }
      if (Array.isArray(member.moves)) {
        for (let i = 0; i < member.moves.length; i++) {
          const mv = member.moves[i]
          if (mv?.name) {
            const fm = movByName[mv.name]
            if (fm) Object.assign(mv, fm)
          }
        }
      }
      if (member.heldItem?.name) {
        const fi = itmByName[member.heldItem.name]
        if (fi) Object.assign(member.heldItem, fi)
      }
    }
    const abilityPromises = snap
      .filter(m => m.ability?.name && m.pokemon?.apiName)
      .map(async (m) => {
        try {
          const abRes = await pokemonRosterApi.getPokemonAbilities(m.pokemon.apiName)
          const fa = (abRes.data || []).find(a => a.name === m.ability.name)
          if (fa) Object.assign(m.ability, fa)
        } catch {}
      })
    await Promise.all(abilityPromises)
    team.value = { ...team.value }
  } catch (e) {
    console.error('enrichSnapshot failed', e)
  }
}

function requestEdit() {
  const saved = getSavedPin(rentalCode.value)
  if (saved) {
    startEditing(saved)
  } else {
    pinError.value = ''
    pinModalVisible.value = true
  }
}

async function startEditing(pin) {
  if (!hasSavedPin(rentalCode.value)) {
    pinLoading.value = true
    pinError.value = ''
    try {
      const ok = await verifyAndSave(rentalCode.value, pin)
      if (!ok) {
        pinError.value = t('teamShare.pinModal.invalidPin')
        pinLoading.value = false
        return
      }
    } catch {
      pinError.value = t('teamShare.pinModal.verifyError')
      pinLoading.value = false
      return
    }
    pinLoading.value = false
  }
  pinModalVisible.value = false
  editTitle.value = team.value.title
  editDescription.value = team.value.description || ''
  editIsPublic.value = team.value.isPublic
  isEditing.value = true
}

async function saveEdit() {
  saving.value = true
  saveError.value = ''
  const pin = getSavedPin(rentalCode.value)
  try {
    const { data } = await teamShareApi.update(rentalCode.value, {
      pin,
      title: editTitle.value.trim(),
      description: editDescription.value.trim(),
      isPublic: editIsPublic.value,
    })
    team.value = data
    isEditing.value = false
  } catch (err) {
    if (err.response?.status === 403) {
      removePin(rentalCode.value)
      saveError.value = t('teamShare.detailPage.pinExpired')
      isEditing.value = false
    } else {
      saveError.value = err.response?.data?.error || t('teamShare.shareModal.genericError')
    }
  } finally {
    saving.value = false
  }
}

async function doDelete() {
  deleting.value = true
  const pin = getSavedPin(rentalCode.value)
  try {
    await teamShareApi.delete(rentalCode.value, pin)
    removePin(rentalCode.value)
    router.push(localePath('/teams'))
  } catch (err) {
    if (err.response?.status === 403) {
      removePin(rentalCode.value)
      saveError.value = t('teamShare.detailPage.pinExpired')
      isEditing.value = false
    }
  } finally {
    deleting.value = false
    deleteConfirm.value = false
  }
}

function cancelEdit() {
  isEditing.value = false
  saveError.value = ''
}

function natureLabelAndEffect(id) {
  const label = t('pokemon.natures.' + id)
  const def = NATURE_DEFS[id]
  if (!def?.mult || Object.keys(def.mult).length === 0) return label
  const parts = []
  for (const [k, v] of Object.entries(def.mult)) {
    if (v > 1) parts.push(`${t('pokemon.stats.' + k)}↑`)
    else if (v < 1) parts.push(`${t('pokemon.stats.' + k)}↓`)
  }
  return `${label} (${parts.join(' ')})`
}

function memberStats(m) {
  if (!m.pokemon) return []
  return STAT_KEYS.map(key => {
    const actual = calcStat(m.pokemon, key, m.statPoints, m.nature)
    return {
      key,
      label: t('pokemon.stats.' + key),
      actual,
      barPct: Math.min((actual / 260) * 100, 100),
      color: STAT_COLORS[key],
      evPts: m.statPoints[key] || 0,
    }
  })
}

function pokemonName(p) {
  return localizedName(p) || p.apiName
}

onMounted(() => {
  reported.value = hasReported(rentalCode.value)
  fetchTeam()
})
</script>

<template>
  <div class="container team-detail-page">
    <!-- 載入中 -->
    <div v-if="loading" class="loading-state">
      <span class="material-symbols-rounded spin">progress_activity</span>
      <span>{{ t('common.loading') }}</span>
    </div>

    <!-- 找不到 -->
    <div v-else-if="notFound" class="empty-state">
      <span class="material-symbols-rounded empty-icon">search_off</span>
      <p>{{ t('teamShare.detailPage.notFound') }}</p>
      <router-link :to="localePath('/teams')" class="btn-go-list">
        {{ t('teamShare.detailPage.backToList') }}
      </router-link>
    </div>

    <!-- 隊伍內容 -->
    <template v-else-if="team">
      <!-- 失效警告 -->
      <div v-if="team.expired" class="expired-banner">
        <span class="material-symbols-rounded">warning</span>
        {{ t('teamShare.detailPage.expiredWarning') }}
      </div>

      <!-- 頁首 -->
      <div class="page-header">
        <div class="header-left">
          <router-link :to="localePath('/teams')" class="back-link">
            <span class="material-symbols-rounded">arrow_back</span>
            {{ t('teamShare.detailPage.backToList') }}
          </router-link>
          <h1 class="page-title">
            <template v-if="!isEditing">{{ team.title }}</template>
            <input
              v-else
              v-model="editTitle"
              class="edit-title-input"
              maxlength="100"
            />
          </h1>
          <div class="team-meta">
            <span class="meta-badge rental-badge copyable" @click="copyRentalCode" :title="t('teamShare.detailPage.copyId')">
              <span class="material-symbols-rounded">confirmation_number</span>
              {{ team.rentalCode }}
              <span class="material-symbols-rounded copy-icon">{{ copied ? 'check' : 'content_copy' }}</span>
            </span>
            <span class="meta-item">
              <span class="material-symbols-rounded">visibility</span>
              {{ team.viewCount }}
            </span>
            <span class="meta-item">
              <span class="material-symbols-rounded">schedule</span>
              {{ new Date(team.createdAt).toLocaleDateString() }}
            </span>
          </div>
        </div>
        <div class="header-actions">
          <template v-if="!isEditing">
            <button
              type="button"
              class="btn-report"
              :class="{ reported }"
              :disabled="reported"
              @click="openReportConfirm"
              :title="reported ? t('teamShare.detailPage.alreadyReported') : t('teamShare.detailPage.reportBtn')"
            >
              <span class="material-symbols-rounded">{{ reported ? 'check_circle' : 'flag' }}</span>
              {{ reported ? t('teamShare.detailPage.alreadyReported') : t('teamShare.detailPage.reportBtn') }}
            </button>
            <button type="button" class="btn-edit" @click="requestEdit">
              <span class="material-symbols-rounded">edit</span>
              {{ t('teamShare.detailPage.edit') }}
            </button>
          </template>
          <template v-else>
            <button type="button" class="btn-save" :disabled="saving || !editTitle.trim()" @click="saveEdit">
              <span v-if="saving" class="material-symbols-rounded spin">progress_activity</span>
              <span v-else class="material-symbols-rounded">save</span>
              {{ t('teamShare.detailPage.save') }}
            </button>
            <button type="button" class="btn-delete" @click="deleteConfirm = true">
              <span class="material-symbols-rounded">delete</span>
            </button>
            <button type="button" class="btn-cancel" @click="cancelEdit">
              {{ t('common.close') }}
            </button>
          </template>
        </div>
      </div>

      <!-- 編輯模式欄位 -->
      <div v-if="isEditing" class="edit-fields">
        <label class="edit-label">
          <span>{{ t('teamShare.shareModal.teamDescription') }}</span>
          <textarea v-model="editDescription" rows="3" maxlength="500"></textarea>
        </label>
        <label class="edit-check">
          <input v-model="editIsPublic" type="checkbox" />
          <span>{{ t('teamShare.shareModal.publicLabel') }}</span>
        </label>
      </div>

      <p v-if="saveError" class="save-error">{{ saveError }}</p>

      <!-- 說明 -->
      <p v-if="!isEditing && team.description" class="team-description">{{ team.description }}</p>

      <!-- 成員卡片 -->
      <section class="members-grid">
        <article v-for="m in filledMembers" :key="m.slotIdx" class="member-card">
          <div class="mc-header">
            <img :src="getPokemonImageUrl(m.pokemon)" class="mc-avatar" />
            <div class="mc-info">
              <h3 class="mc-name">{{ pokemonName(m.pokemon) }}</h3>
              <div class="mc-types">
                <span v-for="tp in m.types" :key="tp" :class="typeBadgeClasses(tp)">{{ t('pokemon.types.' + tp) }}</span>
              </div>
              <div class="mc-nature">{{ natureLabelAndEffect(m.nature) }}</div>
              <div v-if="m.ability" class="mc-ability">
                <span class="material-symbols-rounded">auto_awesome</span>
                {{ localizedName(m.ability) }}
              </div>
            </div>
            <div v-if="m.heldItem" class="mc-held">
              <span class="material-symbols-rounded">inventory_2</span>
              <span class="mc-held-name">{{ localizedName(m.heldItem) }}</span>
            </div>
          </div>

          <!-- 能力值 -->
          <div class="mc-stats">
            <div v-for="s in memberStats(m)" :key="s.key" class="mc-stat-row">
              <span class="mc-stat-label" :style="{ color: s.color }">{{ s.label }}</span>
              <div class="mc-stat-bar-track">
                <div class="mc-stat-bar-fill" :style="{ width: s.barPct + '%', background: s.color }"></div>
              </div>
              <span class="mc-stat-val">{{ s.actual }}</span>
              <span v-if="s.evPts" class="mc-stat-ev">+{{ s.evPts }}</span>
            </div>
          </div>

          <!-- 招式 -->
          <div class="mc-moves">
            <div v-for="(mv, mi) in m.moves" :key="mi" class="mc-move-chip" :class="{ empty: !mv }">
              <template v-if="mv">
                <span :class="typeBadgeClasses(mv.type || mv.typeName)" class="mc-move-dot"></span>
                <span class="mc-move-name">{{ localizedName(mv) }}</span>
                <span class="mc-move-cat">{{ mv.category ? t('pokemon.categories.' + mv.category) : '' }}</span>
              </template>
              <template v-else><span class="mc-move-empty">—</span></template>
            </div>
          </div>
        </article>
      </section>

      <!-- 刪除確認 -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="deleteConfirm" class="modal-overlay" @click.self="deleteConfirm = false">
            <div class="modal-box">
              <h3 class="delete-title">{{ t('teamShare.detailPage.deleteConfirmTitle') }}</h3>
              <p class="delete-msg">{{ t('teamShare.detailPage.deleteConfirmMsg') }}</p>
              <div class="modal-actions">
                <button type="button" class="btn-cancel" @click="deleteConfirm = false">{{ t('common.close') }}</button>
                <button type="button" class="btn-danger" :disabled="deleting" @click="doDelete">
                  <span v-if="deleting" class="material-symbols-rounded spin">progress_activity</span>
                  {{ t('teamShare.detailPage.deleteBtn') }}
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>
    </template>

      <!-- 回報確認 -->
      <Teleport to="body">
        <Transition name="modal">
          <div v-if="reportConfirmVisible" class="modal-overlay" @click.self="reportConfirmVisible = false">
            <div class="modal-box">
              <h3 class="report-title">
                <span class="material-symbols-rounded">flag</span>
                {{ t('teamShare.detailPage.reportTitle') }}
              </h3>
              <p class="report-msg">{{ t('teamShare.detailPage.reportMsg') }}</p>
              <div class="modal-actions">
                <button type="button" class="btn-cancel" @click="reportConfirmVisible = false">{{ t('common.close') }}</button>
                <button type="button" class="btn-danger" :disabled="reporting" @click="submitReport">
                  <span v-if="reporting" class="material-symbols-rounded spin">progress_activity</span>
                  {{ t('teamShare.detailPage.reportConfirm') }}
                </button>
              </div>
            </div>
          </div>
        </Transition>
      </Teleport>

    <!-- PIN Modal -->
    <PinInputModal
      :visible="pinModalVisible"
      :loading="pinLoading"
      :error="pinError"
      @confirm="startEditing"
      @close="pinModalVisible = false"
    />
  </div>
</template>

<style scoped>
/* ═══ 失效警告 ═══ */
.expired-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  margin-bottom: 20px;
  background: rgba(245, 158, 11, 0.12);
  border: 1px solid rgba(245, 158, 11, 0.3);
  border-radius: var(--radius-sm, 10px);
  color: #f59e0b;
  font-size: 0.9rem;
  font-weight: 600;
}
.expired-banner .material-symbols-rounded { font-size: 22px; }

/* ═══ 頁首 ═══ */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24px;
  gap: 16px;
  flex-wrap: wrap;
}

.header-left { flex: 1; min-width: 0; }

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.82rem;
  color: var(--text-muted);
  margin-bottom: 8px;
  transition: color 0.2s;
}

.back-link:hover { color: var(--accent); }

.back-link .material-symbols-rounded { font-size: 18px; }

.page-title {
  font-size: 1.5rem;
  font-weight: 800;
  margin-bottom: 10px;
}

.edit-title-input {
  width: 100%;
  padding: 8px 12px;
  background: var(--bg-glass);
  border: 1px solid var(--accent-glow);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 1.3rem;
  font-weight: 800;
  font-family: inherit;
  outline: none;
}

.team-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.rental-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 12px;
  background: var(--accent-soft);
  border-radius: var(--radius-xs);
  color: var(--accent);
  font-size: 0.82rem;
  font-weight: 700;
}

.rental-badge.copyable {
  cursor: pointer;
  transition: background 0.2s;
}
.rental-badge.copyable:hover {
  background: var(--accent);
  color: #fff;
}

.rental-badge .material-symbols-rounded { font-size: 16px; }
.rental-badge .copy-icon { font-size: 14px; opacity: 0.6; }
.rental-badge.copyable:hover .copy-icon { opacity: 1; }

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.meta-item .material-symbols-rounded { font-size: 16px; }

.header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.btn-report {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 0.82rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
  background: transparent;
  color: var(--text-muted);
}
.btn-report:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.1);
  border-color: #ef4444;
  color: #ef4444;
}
.btn-report.reported {
  cursor: default;
  opacity: 0.6;
  color: var(--text-muted);
}
.btn-report .material-symbols-rounded { font-size: 18px; }

.report-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.1rem;
  color: #ef4444;
}
.report-title .material-symbols-rounded { font-size: 22px; }
.report-msg {
  color: var(--text-muted);
  font-size: 0.9rem;
  line-height: 1.6;
  margin: 8px 0 16px;
}

.btn-edit, .btn-save, .btn-cancel, .btn-delete {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 0.88rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-edit {
  background: var(--accent-soft);
  border-color: var(--accent-glow);
  color: var(--accent);
}

.btn-edit:hover { background: var(--accent); color: #fff; }

.btn-save {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}

.btn-save:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-delete {
  background: rgba(248, 113, 113, 0.1);
  border-color: rgba(248, 113, 113, 0.3);
  color: #f87171;
  padding: 10px 12px;
}

.btn-delete:hover { background: #f87171; color: #fff; }

.btn-cancel {
  background: var(--bg-glass);
  color: var(--text-secondary);
}

.btn-cancel:hover { background: var(--bg-elevated); color: var(--text-primary); }

.btn-edit .material-symbols-rounded,
.btn-save .material-symbols-rounded,
.btn-delete .material-symbols-rounded { font-size: 18px; }

/* ═══ 編輯欄位 ═══ */
.edit-fields {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
  background: var(--bg-card);
  border: 1px solid var(--accent-glow);
  border-radius: var(--radius);
  margin-bottom: 24px;
}

.edit-label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.edit-label textarea {
  padding: 10px 12px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 0.9rem;
  font-family: inherit;
  resize: vertical;
  outline: none;
}

.edit-label textarea:focus { border-color: var(--accent); }

.edit-check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.88rem;
  color: var(--text-secondary);
  cursor: pointer;
}

.edit-check input[type="checkbox"] {
  width: 18px;
  height: 18px;
  accent-color: var(--accent);
}

.save-error {
  padding: 10px 14px;
  background: rgba(248, 113, 113, 0.1);
  border: 1px solid rgba(248, 113, 113, 0.3);
  border-radius: var(--radius-sm);
  color: #f87171;
  font-size: 0.85rem;
  margin-bottom: 20px;
}

.team-description {
  font-size: 0.92rem;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 24px;
  padding: 16px;
  background: var(--bg-glass);
  border-radius: var(--radius-sm);
}

/* ═══ 成員卡片（與 TeamOverview 一致） ═══ */
.members-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.member-card {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mc-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mc-avatar {
  width: 56px;
  height: 56px;
  object-fit: contain;
  image-rendering: pixelated;
  background: var(--bg-glass);
  border-radius: var(--radius-sm);
  padding: 4px;
  flex-shrink: 0;
}

.mc-info { flex: 1; min-width: 0; }

.mc-name {
  font-size: 1rem;
  font-weight: 700;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mc-types { display: flex; gap: 4px; margin-bottom: 4px; }

.mc-nature {
  font-size: 0.74rem;
  color: var(--text-muted);
}

.mc-ability {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 3px;
  font-size: 0.74rem;
  color: var(--text-secondary);
}

.mc-ability .material-symbols-rounded {
  font-size: 14px;
  color: var(--accent);
}

.mc-held {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 8px 10px;
  background: var(--bg-glass);
  border-radius: var(--radius-sm);
  flex-shrink: 0;
  max-width: 110px;
}

.mc-held .material-symbols-rounded {
  font-size: 18px;
  color: var(--text-muted);
}

.mc-held-name {
  font-size: 0.68rem;
  font-weight: 700;
  text-align: center;
  word-break: break-word;
  line-height: 1.2;
}

/* 能力值 */
.mc-stats { display: flex; flex-direction: column; gap: 4px; }

.mc-stat-row {
  display: grid;
  grid-template-columns: 42px 1fr 30px 24px;
  align-items: center;
  gap: 6px;
}

.mc-stat-label { font-size: 0.68rem; font-weight: 600; }

.mc-stat-bar-track {
  height: 6px;
  background: rgba(255,255,255,0.04);
  border-radius: 3px;
  overflow: hidden;
}

.mc-stat-bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}

.mc-stat-val {
  font-size: 0.72rem;
  font-weight: 700;
  text-align: right;
}

.mc-stat-ev {
  font-size: 0.62rem;
  color: #38bdf8;
  font-weight: 600;
}

/* 招式 */
.mc-moves {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.mc-move-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--bg-glass);
  border-radius: var(--radius-xs);
  font-size: 0.76rem;
}

.mc-move-chip.empty { justify-content: center; color: var(--text-muted); }

.mc-move-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  padding: 0 !important;
  font-size: 0 !important;
}

.mc-move-name {
  flex: 1;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mc-move-cat { font-size: 0.64rem; color: var(--text-muted); }

/* ═══ 狀態 ═══ */
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 80px 0;
  color: var(--text-muted);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 80px 20px;
  color: var(--text-muted);
}

.empty-icon { font-size: 56px; }

.btn-go-list {
  padding: 10px 24px;
  background: var(--accent);
  border-radius: var(--radius-sm);
  color: #fff;
  font-weight: 600;
  font-size: 0.9rem;
}

/* ═══ Modal ═══ */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
}

.modal-box {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 28px;
  width: 90%;
  max-width: 400px;
}

.delete-title {
  font-size: 1.1rem;
  font-weight: 700;
  margin-bottom: 10px;
}

.delete-msg {
  font-size: 0.88rem;
  color: var(--text-secondary);
  margin-bottom: 20px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn-danger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: #f87171;
  border: none;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 0.88rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
}

.btn-danger:disabled { opacity: 0.5; cursor: not-allowed; }

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }

/* ═══ 響應式 ═══ */
@media (max-width: 1024px) {
  .members-grid { grid-template-columns: repeat(2, 1fr); }
}

@media (max-width: 768px) {
  .members-grid { grid-template-columns: 1fr; }
  .mc-moves { grid-template-columns: 1fr; }
  .page-header { flex-direction: column; }
  .header-actions { width: 100%; }
}
</style>
