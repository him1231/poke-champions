import { rosterClient } from './client'

export const seasonRosterApi = {
  getSeasonPokemon: (seasonId) => rosterClient.get(`/seasons/${seasonId}/pokemon`),
  getSeasonMegaPokemon: (seasonId) => rosterClient.get(`/seasons/${seasonId}/pokemon/mega`),
}
