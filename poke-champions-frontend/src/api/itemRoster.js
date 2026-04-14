import { rosterClient } from './client'
import { IS_STATIC_DATA, getStaticItems } from './staticApi'

export const itemRosterApi = {
  getItems: () =>
    IS_STATIC_DATA
      ? getStaticItems()
      : rosterClient.get('/items'),

  getItem: async (name) => {
    if (!IS_STATIC_DATA) return rosterClient.get(`/items/${name}`)
    const { data } = await getStaticItems()
    return { data: data.find((entry) => entry.name === name) || null }
  },

  getItemsByCategory: async (category) => {
    if (!IS_STATIC_DATA) return rosterClient.get(`/items/category/${category}`)
    const { data } = await getStaticItems()
    return { data: data.filter((entry) => entry.category === category) }
  },
}
