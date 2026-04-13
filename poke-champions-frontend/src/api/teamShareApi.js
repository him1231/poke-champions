import axios from 'axios'

function teamBaseURL() {
  const root = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  return root ? `${root}/api/teams` : '/api/teams'
}

const teamClient = axios.create({
  baseURL: teamBaseURL(),
  timeout: 30000,
  headers: import.meta.env.VITE_API_KEY
    ? { 'X-Api-Key': import.meta.env.VITE_API_KEY }
    : {},
})

export const teamShareApi = {
  create: (payload) => teamClient.post('', payload),

  list: (sort = 'latest', page = 0, size = 20) =>
    teamClient.get('', { params: { sort, page, size } }),

  getByRentalCode: (rentalCode) => teamClient.get(`/${rentalCode}`),

  update: (rentalCode, payload) => teamClient.put(`/${rentalCode}`, payload),

  delete: (rentalCode, pin) =>
    teamClient.delete(`/${rentalCode}`, { data: { pin } }),

  verifyPin: (rentalCode, pin) =>
    teamClient.post(`/${rentalCode}/verify-pin`, { pin }),

  report: (rentalCode) =>
    teamClient.post(`/${rentalCode}/report`),
}
