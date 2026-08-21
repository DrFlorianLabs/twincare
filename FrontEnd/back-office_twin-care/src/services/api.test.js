import test from 'node:test'
import assert from 'node:assert/strict'

import {
  clearProfessionalSession,
  professionalToken,
  storeProfessionalSession
} from './api.js'

function memoryStorage(initial = {}) {
  const values = new Map(Object.entries(initial))
  return {
    getItem: (key) => values.get(key) || null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key)
  }
}

test('professional token is kept only in the supplied session storage', () => {
  const session = memoryStorage()
  storeProfessionalSession('synthetic-token', session)
  assert.equal(professionalToken(session), 'synthetic-token')
})

test('clearing a professional session also removes historical prototype keys', () => {
  const session = memoryStorage()
  const legacy = memoryStorage({ token: 'old', role: 'MEDECIN', userId: '1' })
  storeProfessionalSession('current', session)
  clearProfessionalSession(session, legacy)
  assert.equal(professionalToken(session), '')
  assert.equal(legacy.getItem('token'), null)
  assert.equal(legacy.getItem('role'), null)
  assert.equal(legacy.getItem('userId'), null)
})

test('blocked browser storage fails closed without exposing a token', () => {
  const blocked = {
    getItem: () => { throw new Error('blocked') },
    setItem: () => { throw new Error('blocked') },
    removeItem: () => { throw new Error('blocked') }
  }
  assert.equal(professionalToken(blocked), '')
  assert.throws(() => storeProfessionalSession('synthetic-token', blocked))
  assert.doesNotThrow(() => clearProfessionalSession(blocked, blocked))
})
