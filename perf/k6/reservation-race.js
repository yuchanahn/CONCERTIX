import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const CONCURRENCY = Number(__ENV.CONCURRENCY || 50);

const reservationCreated = new Counter('reservation_created');
const reservationConflict = new Counter('reservation_conflict');
const reservationUnexpected = new Counter('reservation_unexpected');

export const options = {
  vus: 1,
  iterations: 1,
  batch: CONCURRENCY,
  batchPerHost: CONCURRENCY,
  thresholds: {
    http_req_failed: ['rate<0.99'],
    http_req_duration: ['p(95)<2000'],
    checks: ['rate==1'],
  },
};

function jsonHeaders(token) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    responseCallback: http.expectedStatuses(201, 409),
  };
}

function login(email, password) {
  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (response.status !== 200) {
    fail(`login failed: HTTP ${response.status}`);
  }
  return response.json('accessToken');
}

export function setup() {
  const token = login('user1@concert.com', 'user1234');

  const concerts = http.get(`${BASE_URL}/api/concerts?page=0&size=20`);
  if (concerts.status !== 200) {
    fail(`concert list failed: HTTP ${concerts.status}`);
  }

  const content = concerts.json('content') || [];
  for (const concert of content) {
    const detail = http.get(`${BASE_URL}/api/concerts/${concert.id}`);
    if (detail.status !== 200) continue;

    const sessions = detail.json('sessions') || [];
    for (const session of sessions) {
      const seats = http.get(
        `${BASE_URL}/api/concerts/${concert.id}/sessions/${session.id}/seats`,
      );
      if (seats.status !== 200) continue;

      const available = (seats.json() || []).find((seat) => seat.status === 'AVAILABLE');
      if (available) {
        return {
          token,
          sessionId: session.id,
          seatId: available.id,
        };
      }
    }
  }

  fail('no AVAILABLE seat found; prepare at least one published concert with an available seat');
}

export default function (data) {
  const payload = JSON.stringify({
    sessionId: data.sessionId,
    seatId: data.seatId,
  });

  const requests = Array.from({ length: CONCURRENCY }, () => ({
    method: 'POST',
    url: `${BASE_URL}/api/reservations`,
    body: payload,
    params: jsonHeaders(data.token),
  }));

  const responses = http.batch(requests);
  let created = 0;
  let conflicts = 0;
  let unexpected = 0;

  for (const response of responses) {
    if (response.status === 201) {
      created += 1;
      reservationCreated.add(1);
    } else if (response.status === 409) {
      conflicts += 1;
      reservationConflict.add(1);
    } else {
      unexpected += 1;
      reservationUnexpected.add(1);
    }
  }

  check(
    { created, conflicts, unexpected },
    {
      'exactly one reservation succeeds': (result) => result.created === 1,
      'all remaining requests conflict': (result) => result.conflicts === CONCURRENCY - 1,
      'no unexpected status': (result) => result.unexpected === 0,
    },
  );
}
