import axios from 'axios'

function adminBaseURL() {
  const root = (import.meta.env.VITE_API_BASE_URL || import.meta.env.BASE_URL || '').replace(/\/$/, '')
  return root ? `${root}/api/admin` : '/api/admin'
}

const adminClient = axios.create({
  baseURL: adminBaseURL(),
  timeout: 30000,
  headers: import.meta.env.VITE_API_KEY
    ? { 'X-Api-Key': import.meta.env.VITE_API_KEY }
    : {},
})

adminClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

export const adminApi = {
  login: (username, password) => adminClient.post('/login', { username, password }),

  getStats: () => adminClient.get('/stats'),

  getTeams: (params = {}) => adminClient.get('/teams', { params }),

  markExpired: (rentalCode, expired = true) =>
    adminClient.put(`/teams/${rentalCode}/mark-expired`, { expired }),

  forceDelete: (rentalCode) =>
    adminClient.delete(`/teams/${rentalCode}`),
}
