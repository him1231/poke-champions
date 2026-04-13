import { teamShareApi } from '../api/teamShareApi'

const PIN_PREFIX = 'pin_'

export function useTeamPin() {
  function getSavedPin(rentalCode) {
    try {
      return localStorage.getItem(PIN_PREFIX + rentalCode) || null
    } catch {
      return null
    }
  }

  function savePin(rentalCode, pin) {
    try {
      localStorage.setItem(PIN_PREFIX + rentalCode, pin)
    } catch { /* quota exceeded — ignore */ }
  }

  function removePin(rentalCode) {
    try {
      localStorage.removeItem(PIN_PREFIX + rentalCode)
    } catch { /* ignore */ }
  }

  async function verifyAndSave(rentalCode, pin) {
    const { data } = await teamShareApi.verifyPin(rentalCode, pin)
    if (data.valid) {
      savePin(rentalCode, pin)
      return true
    }
    return false
  }

  function hasSavedPin(rentalCode) {
    return !!getSavedPin(rentalCode)
  }

  return { getSavedPin, savePin, removePin, verifyAndSave, hasSavedPin }
}
