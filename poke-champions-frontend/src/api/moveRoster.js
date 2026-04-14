import { rosterClient } from './client'
import { IS_STATIC_DATA, getStaticMoveLearners, getStaticMoves } from './staticApi'

export const moveRosterApi = {
  getMoves: () =>
    IS_STATIC_DATA
      ? getStaticMoves()
      : rosterClient.get('/moves'),

  getMove: async (name) => {
    if (!IS_STATIC_DATA) return rosterClient.get(`/moves/${name}`)
    const { data } = await getStaticMoves()
    return { data: data.find((entry) => entry.name === name) || null }
  },

  getMovesByType: async (typeName) => {
    if (!IS_STATIC_DATA) return rosterClient.get(`/moves/type/${typeName}`)
    const { data } = await getStaticMoves()
    return { data: data.filter((entry) => entry.type?.name === typeName) }
  },

  getMovesByCategory: async (category) => {
    if (!IS_STATIC_DATA) return rosterClient.get(`/moves/category/${category}`)
    const { data } = await getStaticMoves()
    return { data: data.filter((entry) => entry.category === category) }
  },

  getMoveLearners: (name) =>
    IS_STATIC_DATA
      ? getStaticMoveLearners(name)
      : rosterClient.get(`/moves/${name}/pokemon`),
}
