import axios from 'axios'

export const rosterClient = axios.create({
  baseURL: '/api/roster',
  timeout: 30000,
})
