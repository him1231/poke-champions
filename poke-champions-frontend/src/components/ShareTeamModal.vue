<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useTeamStore } from '../composables/useTeamStore'
import { useTeamPin } from '../composables/useTeamPin'
import { useLocalePath } from '../composables/useLocalePath'
import { teamShareApi } from '../api/teamShareApi'
import { pokemonRosterApi } from '../api/pokemonRoster'

const { t } = useI18n()
const router = useRouter()
const { localePath } = useLocalePath()
const { teamMembers, refreshMemberData } = useTeamStore()
const { savePin } = useTeamPin()

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['close'])

const rentalCode = ref('')
const title = ref('')
const description = ref('')
const pin = ref('')
const pinConfirm = ref('')
const isPublic = ref(true)
/** singles | doubles */
const battleFormat = ref('singles')
const submitting = ref(false)
const errorMsg = ref('')
const successCode = ref('')

const filledMembers = computed(() => teamMembers.filter(m => m.pokemon))
const hasTeam = computed(() => filledMembers.value.length > 0)

const pinMismatch = computed(() =>
  pin.value && pinConfirm.value && pin.value !== pinConfirm.value
)

const canSubmit = computed(() =>
  rentalCode.value.trim() &&
  title.value.trim() &&
  pin.value.length >= 4 &&
  pin.value.length <= 8 &&
  pin.value === pinConfirm.value &&
  hasTeam.value &&
  !submitting.value
)

function serializeTeam() {
  return teamMembers.map(m => ({
    pokemon: m.pokemon,
    types: m.types,
    ability: m.ability,
    statPoints: { ...m.statPoints },
    nature: m.nature,
    moves: [...m.moves],
    heldItem: m.heldItem,
  }))
}

function getPreviewIds() {
  return filledMembers.value.map(m => ({
    apiName: m.pokemon.apiName,
    nationalDexNumber: m.pokemon.nationalDexNumber,
    formId: m.pokemon.formId,
    mega: m.pokemon.isMega || m.pokemon.mega || false,
  }))
}

async function onSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  errorMsg.value = ''

  try {
    const pokRes = await pokemonRosterApi.getPokemonList()
    await refreshMemberData(pokRes.data || [])

    const payload = {
      rentalCode: rentalCode.value.trim(),
      title: title.value.trim(),
      description: description.value.trim(),
      pin: pin.value,
      isPublic: isPublic.value,
      battleFormat: battleFormat.value,
      teamSnapshot: serializeTeam(),
      previewPokemonIds: getPreviewIds(),
    }
    await teamShareApi.create(payload)
    savePin(payload.rentalCode, pin.value)
    successCode.value = payload.rentalCode
  } catch (err) {
    const msg = err.response?.data?.error
    if (err.response?.status === 409) {
      errorMsg.value = t('teamShare.shareModal.duplicateError')
    } else {
      errorMsg.value = msg || t('teamShare.shareModal.genericError')
    }
  } finally {
    submitting.value = false
  }
}

function goToTeam() {
  emit('close')
  router.push(localePath(`/teams/${successCode.value}`))
}

function resetAndClose() {
  rentalCode.value = ''
  title.value = ''
  description.value = ''
  pin.value = ''
  pinConfirm.value = ''
  isPublic.value = true
  battleFormat.value = 'singles'
  errorMsg.value = ''
  successCode.value = ''
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="resetAndClose">
        <div class="modal-box">
          <!-- 成功畫面 -->
          <template v-if="successCode">
            <div class="success-content">
              <span class="material-symbols-rounded success-icon">check_circle</span>
              <h3>{{ t('teamShare.shareModal.successTitle') }}</h3>
              <p class="success-code">{{ successCode }}</p>
              <div class="success-actions">
                <button type="button" class="btn-go-team" @click="goToTeam">
                  <span class="material-symbols-rounded">open_in_new</span>
                  {{ t('teamShare.shareModal.viewTeam') }}
                </button>
                <button type="button" class="btn-done" @click="resetAndClose">
                  {{ t('common.close') }}
                </button>
              </div>
            </div>
          </template>

          <!-- 表單 -->
          <template v-else>
            <div class="modal-header">
              <span class="material-symbols-rounded modal-icon">share</span>
              <h3>{{ t('teamShare.shareModal.title') }}</h3>
              <button type="button" class="modal-close" @click="resetAndClose">
                <span class="material-symbols-rounded">close</span>
              </button>
            </div>

            <div v-if="!hasTeam" class="no-team-warn">
              <span class="material-symbols-rounded">warning</span>
              {{ t('teamShare.shareModal.noTeamWarning') }}
            </div>

            <form v-else class="share-form" @submit.prevent="onSubmit">
              <label class="form-label">
                <span>{{ t('teamShare.shareModal.rentalCode') }} *</span>
                <input v-model="rentalCode" type="text" maxlength="20" required />
              </label>

              <label class="form-label">
                <span>{{ t('teamShare.shareModal.teamTitle') }} *</span>
                <input v-model="title" type="text" maxlength="100" required />
              </label>

              <label class="form-label">
                <span>{{ t('teamShare.shareModal.teamDescription') }}</span>
                <textarea v-model="description" rows="3" maxlength="500"></textarea>
              </label>

              <div class="form-label battle-format-block">
                <span>{{ t('teamShare.battleFormat.label') }}</span>
                <div class="battle-format-options">
                  <label class="bf-option">
                    <input v-model="battleFormat" type="radio" value="singles" />
                    <span>{{ t('teamShare.shareModal.battleFormatSingles') }}</span>
                  </label>
                  <label class="bf-option">
                    <input v-model="battleFormat" type="radio" value="doubles" />
                    <span>{{ t('teamShare.shareModal.battleFormatDoubles') }}</span>
                  </label>
                </div>
                <p class="bf-hint">{{ t('teamShare.shareModal.battleFormatHint') }}</p>
              </div>

              <div class="form-row">
                <label class="form-label form-half">
                  <span>PIN {{ t('teamShare.shareModal.pinLabel') }} *</span>
                  <input v-model="pin" type="password" inputmode="numeric" minlength="4" maxlength="8" required />
                </label>
                <label class="form-label form-half">
                  <span>{{ t('teamShare.shareModal.pinConfirm') }} *</span>
                  <input v-model="pinConfirm" type="password" inputmode="numeric" minlength="4" maxlength="8" required />
                </label>
              </div>
              <p v-if="pinMismatch" class="field-error">{{ t('teamShare.shareModal.pinMismatch') }}</p>
              <p class="pin-hint">{{ t('teamShare.shareModal.pinHint') }}</p>

              <label class="form-check">
                <input v-model="isPublic" type="checkbox" />
                <span>{{ t('teamShare.shareModal.publicLabel') }}</span>
              </label>

              <p v-if="errorMsg" class="form-error">{{ errorMsg }}</p>

              <div class="modal-actions">
                <button type="button" class="btn-cancel" @click="resetAndClose">{{ t('common.close') }}</button>
                <button type="submit" class="btn-submit" :disabled="!canSubmit">
                  <span v-if="submitting" class="material-symbols-rounded spin">progress_activity</span>
                  {{ submitting ? t('common.loading') : t('teamShare.shareModal.submit') }}
                </button>
              </div>
            </form>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
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
  max-width: 520px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
}

.modal-header h3 { flex: 1; font-size: 1.1rem; font-weight: 700; }

.modal-icon { font-size: 22px; color: var(--accent); }

.modal-close {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.15s;
}

.modal-close:hover { background: var(--bg-glass); color: var(--text-primary); }

.no-team-warn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: rgba(251, 191, 36, 0.1);
  border: 1px solid rgba(251, 191, 36, 0.3);
  border-radius: var(--radius-sm);
  color: #fbbf24;
  font-size: 0.88rem;
}

.share-form { display: flex; flex-direction: column; gap: 14px; }

.form-label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.form-label input,
.form-label textarea {
  padding: 10px 12px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 0.9rem;
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s;
}

.form-label input:focus,
.form-label textarea:focus { border-color: var(--accent); }

.form-label textarea { resize: vertical; }

.form-row { display: flex; gap: 12px; }
.form-half { flex: 1; }

.field-error {
  font-size: 0.8rem;
  color: #f87171;
  margin-top: -8px;
}

.pin-hint {
  font-size: 0.78rem;
  color: var(--text-muted);
  margin-top: -8px;
}

.form-check {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.88rem;
  color: var(--text-secondary);
  cursor: pointer;
}

.form-check input[type="checkbox"] {
  width: 18px;
  height: 18px;
  accent-color: var(--accent);
}

.battle-format-block .battle-format-options {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 6px;
}

.bf-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.88rem;
  color: var(--text-secondary);
  cursor: pointer;
}

.bf-option input[type="radio"] {
  width: 16px;
  height: 16px;
  accent-color: var(--accent);
}

.bf-hint {
  font-size: 0.78rem;
  color: var(--text-muted);
  margin: 6px 0 0;
  line-height: 1.45;
}

.form-error {
  padding: 10px 14px;
  background: rgba(248, 113, 113, 0.1);
  border: 1px solid rgba(248, 113, 113, 0.3);
  border-radius: var(--radius-sm);
  color: #f87171;
  font-size: 0.85rem;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 6px;
}

.btn-cancel {
  padding: 10px 20px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-cancel:hover { background: var(--bg-elevated); color: var(--text-primary); }

.btn-submit {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 24px;
  background: var(--accent);
  border: none;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 0.88rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-submit:disabled { opacity: 0.5; cursor: not-allowed; }

/* 成功畫面 */
.success-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px 0;
  text-align: center;
}

.success-icon { font-size: 48px; color: #4ade80; }

.success-content h3 { font-size: 1.15rem; font-weight: 700; }

.success-code {
  font-size: 1.5rem;
  font-weight: 800;
  color: var(--accent);
  letter-spacing: 0.05em;
  padding: 8px 20px;
  background: var(--accent-soft);
  border-radius: var(--radius-sm);
}

.success-actions { display: flex; gap: 10px; margin-top: 8px; }

.btn-go-team {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: var(--accent);
  border: none;
  border-radius: var(--radius-sm);
  color: #fff;
  font-size: 0.88rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
}

.btn-done {
  padding: 10px 20px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: 0.88rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
}

.spin { animation: spin 1s linear infinite; font-size: 18px; }
@keyframes spin { to { transform: rotate(360deg); } }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
