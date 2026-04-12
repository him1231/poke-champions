import { rosterClient } from './client'

export const itemRosterApi = {
  getItems: () => rosterClient.get('/items'),
  getItem: (name) => rosterClient.get(`/items/${name}`),
  getItemsByCategory: (category) => rosterClient.get(`/items/category/${category}`),
}
