import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const CONCURRENCY = Number(__ENV.CONCURRENCY || 50);
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

const reservationCreated = new Counter('reservation_created');
const reservationUnexpected = new Counter('reservation_unexpected');

export const options = {
  vus: 1,
  iterations: 1,
  batch: CONCURRENCY,
  batchPerHost: CONCURRENCY,
  thresholds: {
    'http_req_duration{name:reservation}': ['p(95)<2000'],
    checks: ['rate==1'],
  },
};

function jsonParams(token, expectedStatuses, name) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    responseCallback: http.expectedStatuses(...expectedStatuses),
    tags: { name },
  };
}

function requireStatus(response, status, description) {
  if (response.status !== status) {
    fail(`${description}: expected HTTP ${status}, got ${response.status}`);
  }
  return response;
}

function login(email, password) {
  const response = requireStatus(
    http.post(
      `${BASE_URL}/api/auth/login`,
      JSON.stringify({ email, password }),
      { headers: { 'Content-Type': 'application/json' } },
    ),
    200,
    'admin login',
  );
  return response.json('accessToken');
}

export function setup() {
  const adminToken = login('admin@concert.com', 'admin1234');
  const now = Date.now();
  const concert = requireStatus(
    http.post(
      `${BASE_URL}/api/admin/concerts`,
      JSON.stringify({
        title: `Throughput benchmark ${RUN_ID}`,
        description: 'k6 throughput benchmark data',
        imageUrl: 'https://example.com/benchmark.jpg',
        venue: 'Local benchmark',
        holdSeconds: 300,
        maxTicketsPerUser: 1,
        sessions: [{
          startsAt: new Date(now + 86400000).toISOString(),
          bookingOpensAt: new Date(now - 3600000).toISOString(),
          bookingClosesAt: new Date(now + 3600000).toISOString(),
          capacity: CONCURRENCY,
        }],
      }),
      jsonParams(adminToken, [201], 'benchmark_setup'),
    ),
    201,
    'create benchmark concert',
  );
  const concertId = concert.json('id');

  const detail = requireStatus(
    http.get(
      `${BASE_URL}/api/admin/concerts/${concertId}`,
      jsonParams(adminToken, [200], 'benchmark_setup'),
    ),
    200,
    'load benchmark concert',
  );
  const sessionId = detail.json('sessions.0.id');

  requireStatus(
    http.post(
      `${BASE_URL}/api/admin/concerts/${concertId}/sessions/${sessionId}/seats`,
      JSON.stringify({
        section: 'BENCH',
        row: 'A',
        startNumber: 1,
        count: CONCURRENCY,
        price: 1000,
      }),
      jsonParams(adminToken, [201], 'benchmark_setup'),
    ),
    201,
    'create benchmark seats',
  );

  requireStatus(
    http.post(
      `${BASE_URL}/api/admin/concerts/${concertId}/publish`,
      null,
      jsonParams(adminToken, [200], 'benchmark_setup'),
    ),
    200,
    'publish benchmark concert',
  );

  const seats = requireStatus(
    http.get(
      `${BASE_URL}/api/concerts/${concertId}/sessions/${sessionId}/seats`,
      { tags: { name: 'benchmark_setup' } },
    ),
    200,
    'load benchmark seats',
  ).json();
  if (seats.length !== CONCURRENCY) {
    fail(`expected ${CONCURRENCY} benchmark seats, got ${seats.length}`);
  }

  const reservations = seats.map((seat, index) => {
    const email = `benchmark-${RUN_ID}-${index}@example.com`;
    const registered = requireStatus(
      http.post(
        `${BASE_URL}/api/auth/register`,
        JSON.stringify({ email, password: 'benchmark1234', nickname: `Benchmark${index}` }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'benchmark_setup' } },
      ),
      201,
      `register benchmark user ${index}`,
    );
    return {
      token: registered.json('accessToken'),
      sessionId,
      seatId: seat.id,
    };
  });

  return { reservations };
}

export default function (data) {
  const responses = http.batch(data.reservations.map((reservation) => ({
    method: 'POST',
    url: `${BASE_URL}/api/reservations`,
    body: JSON.stringify({ sessionId: reservation.sessionId, seatId: reservation.seatId }),
    params: jsonParams(reservation.token, [201], 'reservation'),
  })));

  let created = 0;
  let unexpected = 0;
  for (const response of responses) {
    if (response.status === 201) {
      created += 1;
      reservationCreated.add(1);
    } else {
      unexpected += 1;
      reservationUnexpected.add(1);
    }
  }

  check(
    { created, unexpected },
    {
      'every distinct-seat reservation succeeds': (result) => result.created === CONCURRENCY,
      'no unexpected reservation status': (result) => result.unexpected === 0,
    },
  );
}
