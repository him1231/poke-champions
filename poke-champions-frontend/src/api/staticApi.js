export const IS_STATIC_DATA = import.meta.env.VITE_STATIC_DATA === 'true'
export const TEAM_SHARING_ENABLED = import.meta.env.VITE_ENABLE_TEAM_SHARING !== 'false'

const dataCache = new Map()
let moveLearnersCache = null

function ok(data) {
  return { data }
}

async function fetchJson(name) {
  const url = `${import.meta.env.BASE_URL}static-data/${name}.json`
  const res = await fetch(url)
  if (!res.ok) {
    throw new Error(`Failed to load static data: ${name} (${res.status})`)
  }
  return res.json()
}

async function load(name) {
  if (!dataCache.has(name)) {
    dataCache.set(name, fetchJson(name))
  }
  return dataCache.get(name)
}

function typeMatchupKey(type1, type2) {
  if (!type2) return type1
  return [type1, type2].sort().join('__')
}

export async function getStaticPokemonList() {
  return ok(await load('pokemon'))
}

export async function getStaticMoves() {
  return ok(await load('moves'))
}

export async function getStaticItems() {
  return ok(await load('items'))
}

export async function getStaticTypes() {
  return ok(await load('types'))
}

export async function getStaticPokemonTypes(apiName) {
  const pokemon = await load('pokemon')
  const found = pokemon.find((entry) => entry.apiName === apiName)
  return ok(found?.typeSlots || [])
}

export async function getStaticPokemonMoves(apiName) {
  const pokemonMoves = await load('pokemon-moves')
  return ok(pokemonMoves[apiName] || [])
}

export async function getStaticPokemonAbilities(apiName) {
  const pokemonAbilities = await load('pokemon-abilities')
  return ok(pokemonAbilities[apiName] || [])
}

export async function getStaticPokemonMatchup(apiName) {
  const pokemonMatchups = await load('pokemon-matchups')
  return ok(pokemonMatchups[apiName] || null)
}

export async function getStaticTypeMatchup(type1, type2) {
  const typeMatchups = await load('type-matchups')
  return ok(typeMatchups[typeMatchupKey(type1, type2)] || null)
}

async function getMoveLearnersMap() {
  if (!moveLearnersCache) {
    moveLearnersCache = Promise.all([load('pokemon'), load('pokemon-moves')]).then(([pokemon, pokemonMoves]) => {
      const pokemonMap = new Map(pokemon.map((entry) => [entry.apiName, entry]))
      const learnersMap = new Map()

      for (const [apiName, moves] of Object.entries(pokemonMoves)) {
        const info = pokemonMap.get(apiName)
        if (!info || !Array.isArray(moves)) continue
        for (const move of moves) {
          if (!move?.name) continue
          if (!learnersMap.has(move.name)) learnersMap.set(move.name, [])
          learnersMap.get(move.name).push({
            apiName: info.apiName,
            displayName: info.displayName,
            chineseName: info.chineseName,
            japaneseName: info.japaneseName,
            isMega: !!info.mega,
            mega: !!info.mega,
          })
        }
      }

      return learnersMap
    })
  }

  return moveLearnersCache
}

export async function getStaticMoveLearners(name) {
  const learnersMap = await getMoveLearnersMap()
  return ok(learnersMap.get(name) || [])
}

export function staticTeamListResponse() {
  return ok({
    content: [],
    totalPages: 0,
    totalElements: 0,
    number: 0,
    size: 12,
  })
}

export function staticUnavailable(message = 'This feature is unavailable in the static GitHub Pages build.') {
  const error = new Error(message)
  error.code = 'STATIC_UNAVAILABLE'
  throw error
}
