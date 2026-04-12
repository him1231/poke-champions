import { rosterClient } from './client'

export const pokemonRosterApi = {
  getPokemonList: () => rosterClient.get('/pokemon'),
  getMegaPokemon: () => rosterClient.get('/pokemon/mega'),
  getPokemonTypes: (apiName) => rosterClient.get(`/pokemon/${apiName}/types`),
  getPokemonMatchup: (apiName) => rosterClient.get(`/pokemon/${apiName}/matchup`),
  getPokemonMoves: (apiName) => rosterClient.get(`/pokemon/${apiName}/moves`),
  getNameMapping: () => rosterClient.get('/name-mapping'),
}
