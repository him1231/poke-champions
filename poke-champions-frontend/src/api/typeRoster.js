import { rosterClient } from './client'

export const typeRosterApi = {
  getTypes: () => rosterClient.get('/types'),
  getTypeEffectiveness: (typeName) => rosterClient.get(`/types/${typeName}/effectiveness`),
  getTypeMatchup: (type1, type2) => {
    const params = { type1 }
    if (type2) params.type2 = type2
    return rosterClient.get('/types/matchup', { params })
  },
}
