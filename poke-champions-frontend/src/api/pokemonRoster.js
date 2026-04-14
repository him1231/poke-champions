import { rosterClient } from './client'
import {
  IS_STATIC_DATA,
  getStaticPokemonAbilities,
  getStaticPokemonList,
  getStaticPokemonMatchup,
  getStaticPokemonMoves,
  getStaticPokemonTypes,
} from './staticApi'

export const pokemonRosterApi = {
  getPokemonList: () =>
    IS_STATIC_DATA
      ? getStaticPokemonList()
      : rosterClient.get('/pokemon'),

  getMegaPokemon: async () => {
    if (!IS_STATIC_DATA) return rosterClient.get('/pokemon/mega')
    const { data } = await getStaticPokemonList()
    return { data: data.filter((entry) => !!entry.mega) }
  },

  getPokemonTypes: (apiName) =>
    IS_STATIC_DATA
      ? getStaticPokemonTypes(apiName)
      : rosterClient.get(`/pokemon/${apiName}/types`),

  getPokemonMatchup: (apiName) =>
    IS_STATIC_DATA
      ? getStaticPokemonMatchup(apiName)
      : rosterClient.get(`/pokemon/${apiName}/matchup`),

  getPokemonMoves: (apiName) =>
    IS_STATIC_DATA
      ? getStaticPokemonMoves(apiName)
      : rosterClient.get(`/pokemon/${apiName}/moves`),

  getPokemonAbilities: (apiName) =>
    IS_STATIC_DATA
      ? getStaticPokemonAbilities(apiName)
      : rosterClient.get(`/pokemon/${apiName}/abilities`),

  getNameMapping: () => rosterClient.get('/name-mapping'),
}
