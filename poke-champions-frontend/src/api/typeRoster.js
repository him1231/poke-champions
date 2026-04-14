import { rosterClient } from './client'
import { IS_STATIC_DATA, getStaticTypeMatchup, getStaticTypes } from './staticApi'

export const typeRosterApi = {
  getTypes: () =>
    IS_STATIC_DATA
      ? getStaticTypes()
      : rosterClient.get('/types'),

  getTypeEffectiveness: (typeName) => rosterClient.get(`/types/${typeName}/effectiveness`),

  getTypeMatchup: (type1, type2) => {
    if (IS_STATIC_DATA) return getStaticTypeMatchup(type1, type2)
    const params = { type1 }
    if (type2) params.type2 = type2
    return rosterClient.get('/types/matchup', { params })
  },
}
