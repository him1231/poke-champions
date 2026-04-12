import { rosterClient } from './client'

export const rosterAdminApi = {
  getHealth: () => rosterClient.get('/health'),
  scrape: (pageId = '751') => rosterClient.get('/scrape', { params: { pageId } }),
  syncStats: () => rosterClient.post('/sync-stats'),
  fullSync: (pageId = '751') => rosterClient.post('/full-sync', null, { params: { pageId } }),
  syncTypes: () => rosterClient.post('/sync-types'),
  syncMoves: () => rosterClient.post('/sync-moves'),
  syncPokemonMoves: (forceRefresh = false) =>
    rosterClient.post('/sync-pokemon-moves', null, { params: { forceRefresh } }),
  translateMoveDescriptions: (forceRefresh = false) =>
    rosterClient.post('/translate-move-descriptions', null, { params: { forceRefresh } }),
  syncMoveNames: (forceRefresh = false) =>
    rosterClient.post('/sync-move-names', null, { params: { forceRefresh } }),
}
