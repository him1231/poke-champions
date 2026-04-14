import axios from 'axios'
import { IS_STATIC_DATA, staticTeamListResponse, staticUnavailable } from './staticApi'

function teamBaseURL() {
  const root = (import.meta.env.VITE_API_BASE_URL || import.meta.env.BASE_URL || '').replace(/\/$/, '')
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
  create: (payload) =>
    IS_STATIC_DATA
      ? staticUnavailable()
      : teamClient.post('', payload),

  list: (sort = 'latest', page = 0, size = 20) =>
    IS_STATIC_DATA
      ? staticTeamListResponse(sort, page, size)
      : teamClient.get('', { params: { sort, page, size } }),

  getByRentalCode: (rentalCode) =>
    IS_STATIC_DATA
      ? staticUnavailable()
      : teamClient.get(`/${rentalCode}`),

  update: (rentalCode, payload) =>
    IS_STATIC_DATA
      ? staticUnavailable()
      : teamClient.put(`/${rentalCode}`, payload),

  delete: (rentalCode, pin) =>
    IS_STATIC_DATA
      ? staticUnavailable()
      : teamClient.delete(`/${rentalCode}`, { data: { pin } }),

  verifyPin: (rentalCode, pin) =>
    IS_STATIC_DATA
      ? staticUnavailable()
      : teamClient.post(`/${rentalCode}/verify-pin`, { pin }),

  report: (rentalCode) =>
    IS_STATIC_DATA
      ? staticUnavailable()
      : teamClient.post(`/${rentalCode}/report`),
}
