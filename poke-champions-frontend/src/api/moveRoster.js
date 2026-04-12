import { rosterClient } from './client'

export const moveRosterApi = {
  getMoves: () => rosterClient.get('/moves'),
  getMove: (name) => rosterClient.get(`/moves/${name}`),
  getMovesByType: (typeName) => rosterClient.get(`/moves/type/${typeName}`),
  getMovesByCategory: (category) => rosterClient.get(`/moves/category/${category}`),
  getMoveLearners: (name) => rosterClient.get(`/moves/${name}/pokemon`),
}
