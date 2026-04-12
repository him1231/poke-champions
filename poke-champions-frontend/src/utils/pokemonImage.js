import pokemonImages from '../data/pokemonImages.js'
import megaMapping from '../data/megaMapping.js'

export function getPokemonImageUrl(pokemon) {
  const dex4 = String(pokemon.nationalDexNumber).padStart(4, '0')

  if (pokemon.mega) {
    const api = pokemon.apiName || pokemon.formId || ''
    const info = megaMapping[dex4]
    if (info) {
      let sub
      if (api.endsWith('-mega-x')) sub = info.x
      else if (api.endsWith('-mega-y')) sub = info.y
      else sub = info.mega ?? info.x

      if (sub !== undefined) {
        const key = `${dex4}_${sub}`
        if (pokemonImages[key]) return pokemonImages[key]
      }
    }

    const normalKey = `${dex4}_0`
    if (pokemonImages[normalKey]) return pokemonImages[normalKey]
  } else if (pokemon.formId) {
    const parts = pokemon.formId.split('-')
    if (parts.length === 2 && parts[0].match(/^\d+$/) && parts[1].match(/^\d+$/)) {
      const formNum = parseInt(parts[1], 10)
      const key = `${dex4}_${formNum}`
      if (pokemonImages[key]) return pokemonImages[key]
    }

    const baseKey = `${dex4}_0`
    if (pokemonImages[baseKey]) return pokemonImages[baseKey]
  }

  const fallbackKey = `${dex4}_0`
  if (pokemonImages[fallbackKey]) return pokemonImages[fallbackKey]

  return `https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${pokemon.nationalDexNumber}.png`
}
