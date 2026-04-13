import axios from 'axios'

function rosterBaseURL() {
  const root = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
  return root ? `${root}/api/roster` : '/api/roster'
}

export const rosterClient = axios.create({
  baseURL: rosterBaseURL(),
  timeout: 30000,
  headers: import.meta.env.VITE_API_KEY
    ? { 'X-Api-Key': import.meta.env.VITE_API_KEY }
    : {},
})
