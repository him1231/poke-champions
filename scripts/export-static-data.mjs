#!/usr/bin/env node
import fs from 'node:fs/promises'
import path from 'node:path'

const API_BASE_URL = (process.env.POKE_API_BASE_URL || 'https://poke-api-878796745474.asia-east1.run.app').replace(/\/$/, '')
const API_KEY = process.env.POKE_API_KEY || process.env.POKE_CHAMPIONS_API_KEY
const OUTPUT_DIR = path.resolve(process.cwd(), 'poke-champions-frontend/public/static-data')
const CONCURRENCY = Number(process.env.EXPORT_CONCURRENCY || 8)

if (!API_KEY) {
  console.error('Missing POKE_API_KEY (or POKE_CHAMPIONS_API_KEY).')
  process.exit(1)
}

async function fetchJson(apiPath) {
  const res = await fetch(`${API_BASE_URL}${apiPath}`, {
    headers: {
      'X-Api-Key': API_KEY,
      Accept: 'application/json',
    },
  })

  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new Error(`HTTP ${res.status} for ${apiPath}: ${body.slice(0, 200)}`)
  }

  return res.json()
}

async function mapLimit(items, limit, mapper) {
  const results = new Array(items.length)
  let index = 0

  async function worker() {
    while (true) {
      const current = index
      index += 1
      if (current >= items.length) return
      results[current] = await mapper(items[current], current)
    }
  }

  await Promise.all(Array.from({ length: Math.min(limit, items.length || 1) }, () => worker()))
  return results
}

function matchupKey(type1, type2) {
  if (!type2) return type1
  return [type1, type2].sort().join('__')
}

async function writeJson(filename, data) {
  const target = path.join(OUTPUT_DIR, filename)
  await fs.writeFile(target, `${JSON.stringify(data, null, 2)}\n`, 'utf8')
  console.log(`wrote ${filename}`)
}

async function main() {
  await fs.mkdir(OUTPUT_DIR, { recursive: true })

  console.log('Fetching base datasets...')
  const [pokemon, moves, items, types] = await Promise.all([
    fetchJson('/api/roster/pokemon'),
    fetchJson('/api/roster/moves'),
    fetchJson('/api/roster/items'),
    fetchJson('/api/roster/types'),
  ])

  await Promise.all([
    writeJson('pokemon.json', pokemon),
    writeJson('moves.json', moves),
    writeJson('items.json', items),
    writeJson('types.json', types),
  ])

  const pokemonAbilities = {}
  const pokemonMoves = {}
  const pokemonMatchups = {}

  console.log(`Fetching per-Pokémon datasets (${pokemon.length})...`)
  await mapLimit(pokemon, CONCURRENCY, async (entry, idx) => {
    const apiName = entry.apiName
    if (!apiName) return
    const [abilities, learnableMoves, matchup] = await Promise.all([
      fetchJson(`/api/roster/pokemon/${apiName}/abilities`),
      fetchJson(`/api/roster/pokemon/${apiName}/moves`),
      fetchJson(`/api/roster/pokemon/${apiName}/matchup`),
    ])
    pokemonAbilities[apiName] = abilities
    pokemonMoves[apiName] = learnableMoves
    pokemonMatchups[apiName] = matchup
    if ((idx + 1) % 25 === 0 || idx === pokemon.length - 1) {
      console.log(`  Pokémon ${idx + 1}/${pokemon.length}`)
    }
  })

  await Promise.all([
    writeJson('pokemon-abilities.json', pokemonAbilities),
    writeJson('pokemon-moves.json', pokemonMoves),
    writeJson('pokemon-matchups.json', pokemonMatchups),
  ])

  const typeNames = types.map((type) => type.name).filter(Boolean)
  const typeMatchups = {}
  const typeCombos = []

  for (const type1 of typeNames) {
    typeCombos.push([type1])
  }
  for (let i = 0; i < typeNames.length; i += 1) {
    for (let j = i + 1; j < typeNames.length; j += 1) {
      typeCombos.push([typeNames[i], typeNames[j]])
    }
  }

  console.log(`Fetching type matchup datasets (${typeCombos.length})...`)
  await mapLimit(typeCombos, CONCURRENCY, async ([type1, type2], idx) => {
    const query = type2
      ? `/api/roster/types/matchup?type1=${encodeURIComponent(type1)}&type2=${encodeURIComponent(type2)}`
      : `/api/roster/types/matchup?type1=${encodeURIComponent(type1)}`
    typeMatchups[matchupKey(type1, type2)] = await fetchJson(query)
    if ((idx + 1) % 25 === 0 || idx === typeCombos.length - 1) {
      console.log(`  Type matchup ${idx + 1}/${typeCombos.length}`)
    }
  })

  await writeJson('type-matchups.json', typeMatchups)

  console.log('Static export complete.')
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
