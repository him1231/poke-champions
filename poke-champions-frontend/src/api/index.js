import { pokemonRosterApi } from './pokemonRoster'
import { typeRosterApi } from './typeRoster'
import { moveRosterApi } from './moveRoster'
import { seasonRosterApi } from './seasonRoster'
import { rosterAdminApi } from './rosterAdmin'
import { itemRosterApi } from './itemRoster'

const api = {
  ...pokemonRosterApi,
  ...typeRosterApi,
  ...moveRosterApi,
  ...seasonRosterApi,
  ...rosterAdminApi,
  ...itemRosterApi,
}

export default api

export { rosterClient } from './client'
export { pokemonRosterApi } from './pokemonRoster'
export { typeRosterApi } from './typeRoster'
export { moveRosterApi } from './moveRoster'
export { seasonRosterApi } from './seasonRoster'
export { rosterAdminApi } from './rosterAdmin'
export { itemRosterApi } from './itemRoster'
