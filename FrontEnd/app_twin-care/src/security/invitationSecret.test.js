import assert from 'node:assert/strict'
import test from 'node:test'

import { captureInvitationSecret, InvitationCaptureKind } from './invitationSecret.js'

const TOKEN = 'A'.repeat(43)

function location(overrides = {}) {
  return {
    pathname: '/preparation',
    search: '',
    hash: '',
    ...overrides,
  }
}

function recordingHistory() {
  const calls = []
  return {
    calls,
    replaceState(...arguments_) {
      calls.push(arguments_)
    },
  }
}

test('captures an exact fragment token and removes it immediately', () => {
  const history = recordingHistory()

  const result = captureInvitationSecret(
    location({ search: '?language=fr', hash: `#token=${TOKEN}` }),
    history,
  )

  assert.deepEqual(result, { kind: InvitationCaptureKind.TOKEN, token: TOKEN })
  assert.deepEqual(history.calls, [[null, '', '/preparation?language=fr']])
})

test('does not touch a location without any invitation secret', () => {
  const history = recordingHistory()

  assert.deepEqual(captureInvitationSecret(location(), history), {
    kind: InvitationCaptureKind.NONE,
  })
  assert.equal(history.calls.length, 0)
})

test('rejects and removes a token supplied in the query string', () => {
  const history = recordingHistory()

  const result = captureInvitationSecret(
    location({ search: `?token=${TOKEN}&language=fr` }),
    history,
  )

  assert.deepEqual(result, { kind: InvitationCaptureKind.INVALID })
  assert.deepEqual(history.calls, [[null, '', '/preparation?language=fr']])
})

for (const fragment of [
  `#token=${TOKEN}&token=${TOKEN}`,
  '#other=value',
  '#token=too-short',
  '#token=%ZZ',
  `#token=${'A'.repeat(42)}!`,
]) {
  test(`rejects and scrubs an ambiguous or malformed fragment: ${fragment.slice(0, 24)}`, () => {
    const history = recordingHistory()

    assert.deepEqual(captureInvitationSecret(location({ hash: fragment }), history), {
      kind: InvitationCaptureKind.INVALID,
    })
    assert.deepEqual(history.calls, [[null, '', '/preparation']])
  })
}
