<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  visible: Boolean,
  loading: Boolean,
  error: { type: String, default: '' },
})

const emit = defineEmits(['confirm', 'close'])

const pin = ref('')

function onConfirm() {
  if (pin.value.length < 4) return
  emit('confirm', pin.value)
}

function onClose() {
  pin.value = ''
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" @click.self="onClose">
        <div class="modal-box">
          <div class="modal-header">
            <span class="material-symbols-rounded modal-icon">lock</span>
            <h3>{{ t('teamShare.pinModal.title') }}</h3>
            <button type="button" class="modal-close" @click="onClose">
              <span class="material-symbols-rounded">close</span>
            </button>
          </div>
          <p class="modal-desc">{{ t('teamShare.pinModal.description') }}</p>
          <input
            v-model="pin"
            type="password"
            inputmode="numeric"
            maxlength="8"
            :placeholder="t('teamShare.pinModal.placeholder')"
            class="pin-input"
            @keyup.enter="onConfirm"
          />
          <p v-if="error" class="modal-error">{{ error }}</p>
          <div class="modal-actions">
            <button type="button" class="btn-cancel" @click="onClose">{{ t('common.close') }}</button>
            <button
              type="button"
              class="btn-confirm"
              :disabled="pin.length < 4 || loading"
              @click="onConfirm"
            >
              <span v-if="loading" class="material-symbols-rounded spin">progress_activity</span>
              {{ loading ? t('common.loading') : t('teamShare.pinModal.confirm') }}
            </button>
          </div>
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
  max-width: 400px;
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.modal-header h3 {
  flex: 1;
  font-size: 1.1rem;
  font-weight: 700;
}

.modal-icon {
  font-size: 22px;
  color: var(--accent);
}

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

.modal-desc {
  font-size: 0.85rem;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.pin-input {
  width: 100%;
  padding: 12px 14px;
  background: var(--bg-glass);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  color: var(--text-primary);
  font-size: 1.1rem;
  font-family: inherit;
  letter-spacing: 0.2em;
  text-align: center;
  outline: none;
  transition: border-color 0.2s;
}

.pin-input:focus { border-color: var(--accent); }

.modal-error {
  margin-top: 8px;
  font-size: 0.82rem;
  color: #f87171;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
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

.btn-confirm {
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
  transition: opacity 0.2s;
}

.btn-confirm:disabled { opacity: 0.5; cursor: not-allowed; }

.spin { animation: spin 1s linear infinite; font-size: 18px; }

@keyframes spin { to { transform: rotate(360deg); } }

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
