<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '../../api/adminApi'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function onSubmit() {
  if (!username.value.trim() || !password.value.trim()) return
  loading.value = true
  error.value = ''
  try {
    const { data } = await adminApi.login(username.value, password.value)
    sessionStorage.setItem('admin_token', data.token)
    router.replace('/admin')
  } catch (err) {
    if (err.response?.status === 401) {
      error.value = '帳號或密碼錯誤'
    } else if (err.response?.status === 503) {
      error.value = '後台管理未設定'
    } else {
      error.value = '連線失敗'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="admin-login-page">
    <form class="login-card" @submit.prevent="onSubmit">
      <div class="login-icon">
        <span class="material-symbols-rounded">admin_panel_settings</span>
      </div>
      <h1>Poké Champions Admin</h1>

      <div v-if="error" class="login-error">{{ error }}</div>

      <label class="login-field">
        <input
          v-model="username"
          type="text"
          placeholder="帳號"
          autocomplete="username"
          autofocus
        />
      </label>

      <label class="login-field">
        <input
          v-model="password"
          type="password"
          placeholder="密碼"
          autocomplete="current-password"
        />
      </label>

      <button type="submit" class="login-btn" :disabled="loading || !username.trim() || !password.trim()">
        <span v-if="loading" class="material-symbols-rounded spin">progress_activity</span>
        <span v-else class="material-symbols-rounded">login</span>
        登入
      </button>
    </form>
  </div>
</template>

<style scoped>
.admin-login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-base, #0f172a);
  padding: 20px;
}

.login-card {
  width: 100%;
  max-width: 380px;
  background: var(--bg-card, #1e293b);
  border: 1px solid var(--border, #334155);
  border-radius: 16px;
  padding: 40px 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.login-icon .material-symbols-rounded {
  font-size: 48px;
  color: var(--accent, #f97316);
}

.login-card h1 {
  font-size: 1.2rem;
  color: var(--text-primary, #f1f5f9);
  margin: 0;
}

.login-error {
  width: 100%;
  padding: 10px 14px;
  border-radius: 8px;
  background: rgba(239, 68, 68, 0.15);
  color: #ef4444;
  font-size: 0.85rem;
  text-align: center;
}

.login-field {
  width: 100%;
}

.login-field input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--border, #334155);
  border-radius: 10px;
  background: var(--bg-base, #0f172a);
  color: var(--text-primary, #f1f5f9);
  font-size: 0.95rem;
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.login-field input:focus {
  border-color: var(--accent, #f97316);
}

.login-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border: none;
  border-radius: 10px;
  background: var(--accent, #f97316);
  color: #fff;
  font-size: 0.95rem;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: opacity 0.2s;
}

.login-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.login-btn .material-symbols-rounded { font-size: 20px; }

@keyframes spin {
  to { transform: rotate(360deg); }
}
.spin { animation: spin 1s linear infinite; }
</style>
