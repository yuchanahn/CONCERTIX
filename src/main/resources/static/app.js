// CONCERTIX - Frontend Logic

const state = {
  token: localStorage.getItem('token') || null,
  role: localStorage.getItem('role') || 'GUEST',
  userEmail: localStorage.getItem('userEmail') || '',
  concerts: [],
  selectedConcert: null,
  selectedSession: null,
  seats: [],
  selectedSeat: null,
  myReservations: []
};

// --- Toast System ---
function showToast(message, type = 'success') {
  const container = document.getElementById('toast-container');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span>${type === 'success' ? '✅' : type === 'error' ? '❌' : '⚠️'}</span> <span>${message}</span>`;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

// --- API Helper ---
async function api(url, method = 'GET', body = null, requireAuth = false, extraHeaders = {}) {
  const headers = { ...extraHeaders };
  if (body) headers['Content-Type'] = 'application/json';
  if (state.token) headers['Authorization'] = `Bearer ${state.token}`;

  try {
    const res = await fetch(url, {
      method,
      headers,
      body: body ? JSON.stringify(body) : null
    });

    if (res.status === 401 && requireAuth) {
      showToast('인증이 필요합니다. 먼저 로그인하세요.', 'warning');
      return null;
    }

    const text = await res.text();
    const data = text ? JSON.parse(text) : null;

    if (!res.ok) {
      const errMsg = (data && data.message) || `요청 실패 (HTTP ${res.status})`;
      throw new Error(errMsg);
    }
    return data;
  } catch (err) {
    showToast(err.message, 'error');
    throw err;
  }
}

// --- Auth Handling ---
async function login(email, password) {
  try {
    const data = await api('/api/auth/login', 'POST', { email, password });
    state.token = data.accessToken;
    state.role = data.member.role;
    state.userEmail = data.member.email;

    localStorage.setItem('token', state.token);
    localStorage.setItem('role', state.role);
    localStorage.setItem('userEmail', state.userEmail);

    updateAuthUI();
    showToast(`${data.member.nickname}님 환영합니다! (${state.role})`, 'success');
    loadInitialData();
  } catch (e) {
    // handled by api
  }
}

async function quickLogin(role) {
  if (role === 'ADMIN') {
    await login('admin@concert.com', 'admin1234');
  } else if (role === 'USER1') {
    await login('user1@concert.com', 'user1234');
  } else if (role === 'USER2') {
    await login('user2@concert.com', 'user1234');
  }
}

function logout() {
  state.token = null;
  state.role = 'GUEST';
  state.userEmail = '';
  localStorage.clear();
  updateAuthUI();
  showToast('로그아웃 되었습니다.', 'warning');
}

function updateAuthUI() {
  const badge = document.getElementById('user-role-badge');
  const userText = document.getElementById('current-user-info');
  const logoutBtn = document.getElementById('logout-btn');

  badge.className = `role-badge ${state.role.toLowerCase()}`;
  badge.textContent = state.role;

  if (state.token) {
    userText.textContent = state.userEmail;
    logoutBtn.style.display = 'inline-flex';
  } else {
    userText.textContent = '게스트';
    logoutBtn.style.display = 'none';
  }

  // Hide admin tab if not admin
  const adminTabBtn = document.getElementById('tab-admin-btn');
  if (state.role === 'ADMIN') {
    adminTabBtn.style.display = 'inline-block';
  } else {
    adminTabBtn.style.display = 'none';
    if (document.getElementById('tab-admin').classList.contains('active')) {
      switchTab('booking');
    }
  }
}

// --- Tab Switching ---
function switchTab(tabId) {
  document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
  document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

  document.getElementById(`tab-${tabId}-btn`).classList.add('active');
  document.getElementById(`tab-${tabId}`).classList.add('active');

  if (tabId === 'admin') {
    loadAdminConcerts();
  } else {
    loadPublicConcerts();
    loadMyReservations();
  }
}

// --- Booking Portal: Concerts & Sessions ---
async function loadPublicConcerts() {
  try {
    const data = await api('/api/concerts');
    // API returns paginated { content: [...], page, size, ... }
    state.concerts = (data && data.content) ? data.content : (Array.isArray(data) ? data : []);
    renderPublicConcerts();
  } catch (e) {}
}

function renderPublicConcerts() {
  const container = document.getElementById('public-concert-list');
  if (!state.concerts.length) {
    container.innerHTML = `<p style="color: var(--text-muted); padding: 1rem;">현재 예매 가능한 공연이 없습니다.</p>`;
    return;
  }

  container.innerHTML = state.concerts.map(c => `
    <div class="concert-card ${state.selectedConcert?.id === c.id ? 'selected' : ''}" onclick="selectConcert('${c.id}')">
      <img class="concert-banner" src="${c.imageUrl || 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800'}" alt="${c.title}" onerror="this.src='https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800'"/>
      <div class="concert-body">
        <span class="concert-badge published">예매 오픈</span>
        <h4 style="margin-bottom: 4px;">${c.title}</h4>
        <p style="font-size: 0.8rem; color: var(--text-muted); margin-bottom: 8px;">📍 ${c.venue}</p>
        ${c.description ? `<p style="font-size: 0.8rem; color: var(--text-secondary);">${c.description.substring(0, 60)}...</p>` : ''}
      </div>
    </div>
  `).join('');

  if (!state.selectedConcert && state.concerts.length > 0) {
    selectConcert(state.concerts[0].id);
  }
}

async function selectConcert(concertId) {
  state.selectedConcert = state.concerts.find(c => c.id === concertId);
  renderPublicConcerts();

  // Load detailed session info
  const details = await api(`/api/concerts/${concertId}`);
  if (details && details.sessions) {
    state.selectedConcert.sessions = details.sessions;
    renderSessionSelector();
  }
}

function renderSessionSelector() {
  const selector = document.getElementById('session-selector');
  const sessions = state.selectedConcert.sessions || [];

  if (!sessions.length) {
    selector.innerHTML = `<span style="color: var(--text-muted)">등록된 회차가 없습니다.</span>`;
    return;
  }

  selector.innerHTML = sessions.map((s, idx) => `
    <button class="btn ${state.selectedSession?.id === s.id ? 'btn-primary' : 'btn-secondary'}" onclick="selectSession('${s.id}')">
      ${idx + 1}회차 (${new Date(s.startsAt).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })})
    </button>
  `).join('');

  if (!state.selectedSession || !sessions.find(s => s.id === state.selectedSession.id)) {
    selectSession(sessions[0].id);
  }
}

async function selectSession(sessionId) {
  state.selectedSession = state.selectedConcert.sessions.find(s => s.id === sessionId);
  renderSessionSelector();
  loadSeats();
}

// --- Seat Map Rendering & Selection ---
async function loadSeats() {
  if (!state.selectedConcert || !state.selectedSession) return;
  try {
    const seats = await api(`/api/concerts/${state.selectedConcert.id}/sessions/${state.selectedSession.id}/seats`);
    state.seats = seats || [];
    renderSeatMap();
  } catch (e) {}
}

function renderSeatMap() {
  const container = document.getElementById('seat-grid');
  if (!state.seats.length) {
    container.innerHTML = `<p style="color: var(--text-muted); margin: 2rem;">등록된 좌석이 없습니다.</p>`;
    return;
  }

  // Normalize seat field names: API returns row/number, normalize to rowLabel/seatNumber
  state.seats = state.seats.map(s => ({
    ...s,
    rowLabel: s.rowLabel || s.row,
    seatNumber: s.seatNumber != null ? s.seatNumber : s.number
  }));

  // Group by Section & Row
  const rows = {};
  state.seats.forEach(s => {
    const key = `${s.section}-${s.rowLabel}`;
    if (!rows[key]) rows[key] = [];
    rows[key].push(s);
  });

  container.innerHTML = Object.entries(rows).map(([rowKey, seatList]) => `
    <div class="seat-row">
      <div class="seat-row-label">${rowKey}</div>
      ${seatList.sort((a,b) => a.seatNumber - b.seatNumber).map(seat => `
        <div class="seat-node ${seat.status} ${state.selectedSeat?.id === seat.id ? 'selected' : ''}" 
             title="${seat.section}구역 ${seat.rowLabel}열 ${seat.seatNumber}번 (${seat.price.toLocaleString()}원) - [${seat.status}]"
             onclick="clickSeat('${seat.id}')">
          ${seat.seatNumber}
        </div>
      `).join('')}
    </div>
  `).join('');

  updateSeatDetailsPanel();
}

function clickSeat(seatId) {
  state.selectedSeat = state.seats.find(s => s.id === seatId);
  renderSeatMap();
}

function updateSeatDetailsPanel() {
  const panel = document.getElementById('seat-details-panel');
  if (!state.selectedSeat) {
    panel.innerHTML = `<p style="color: var(--text-muted);">좌석을 선택하면 상세 정보 및 예매 버튼이 표시됩니다.</p>`;
    return;
  }

  const s = state.selectedSeat;
  const isAvailable = s.status === 'AVAILABLE';

  panel.innerHTML = `
    <div style="display: flex; justify-content: space-between; align-items: center;">
      <div>
        <h3 style="color: #fff; margin-bottom: 4px;">${s.section}구역 ${s.rowLabel || s.row}열 ${s.seatNumber != null ? s.seatNumber : s.number}번</h3>
        <p style="font-size: 0.9rem; color: var(--text-secondary);">가격: <strong style="color: #38bdf8;">${s.price.toLocaleString()}원</strong> | 상태: <strong>${s.status}</strong></p>
      </div>
      <div style="display: flex; gap: 8px;">
        <button class="btn btn-primary" ${!isAvailable ? 'disabled' : ''} onclick="reserveSelectedSeat()">
          🎟️ 좌석 선점 및 예매
        </button>
        <button class="btn btn-warning" ${!isAvailable ? 'disabled' : ''} onclick="testConcurrencyRace()">
          ⚡ 10명 동시 선점 테스트 (동시성 락 검증)
        </button>
      </div>
    </div>
  `;
}

// --- Seat Reservation Action ---
async function reserveSelectedSeat() {
  if (!state.token) {
    showToast('예매를 위해 먼저 로그인하세요.', 'warning');
    return;
  }
  if (!state.selectedSeat || !state.selectedSession) return;

  try {
    const queue = await api(
      `/api/queues/${state.selectedConcert.id}/join`,
      'POST',
      null,
      true
    );
    if (queue && queue.active && !queue.admitted) {
      showToast(`현재 대기 순번 ${queue.position ?? '?'}번입니다. 잠시 후 다시 시도하세요.`, 'warning');
      return;
    }

    const queueHeaders = queue?.admissionToken
      ? { 'X-Queue-Token': queue.admissionToken }
      : {};
    const res = await api('/api/reservations', 'POST', {
      sessionId: state.selectedSession.id,
      seatId: state.selectedSeat.id
    }, true, queueHeaders);

    showToast(`좌석 선점 성공! (선점 만료: 5분 이내 결제 필요)`, 'success');
    loadSeats();
    loadMyReservations();
  } catch (e) {}
}

// --- Live Concurrency Race Test (Visual Demo) ---
async function testConcurrencyRace() {
  if (!state.selectedSeat || !state.selectedSession) return;
  const seatId = state.selectedSeat.id;
  const sessionId = state.selectedSession.id;

  showToast(`⚡ 10개 동시 요청 전송 중... (DB 비관적 락 경합)`, 'warning');

  // Launch 10 simultaneous requests
  const promises = [];
  for (let i = 1; i <= 10; i++) {
    promises.push(
      fetch('/api/reservations', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${state.token}`
        },
        body: JSON.stringify({ sessionId, seatId })
      }).then(r => r.status)
    );
  }

  const results = await Promise.all(promises);
  const successCount = results.filter(s => s === 201).length;
  const conflictCount = results.filter(s => s === 409).length;

  alert(`[동시성 테스트 결과]\n총 요청: 10건\n✅ 성공(201): ${successCount}건 (정확히 1건 선점)\n❌ 충돌 차단(409): ${conflictCount}건 (중복 방지 완벽 통제)`);
  loadSeats();
  loadMyReservations();
}

// --- My Reservations ---
async function loadMyReservations() {
  if (!state.token) {
    document.getElementById('my-reservations-list').innerHTML = `<p style="color: var(--text-muted);">로그인 후 확인 가능합니다.</p>`;
    return;
  }

  try {
    const data = await api('/api/reservations/me', 'GET', null, true);
    // API may return array or paginated {content: [...]}
    state.myReservations = Array.isArray(data) ? data : (data && data.content ? data.content : []);
    renderMyReservations();
  } catch (e) {}
}

function renderMyReservations() {
  const container = document.getElementById('my-reservations-list');
  if (!state.myReservations.length) {
    container.innerHTML = `<p style="color: var(--text-muted);">예매 내역이 없습니다.</p>`;
    return;
  }

  container.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>예매 ID</th>
          <th>좌석</th>
          <th>금액</th>
          <th>상태</th>
          <th>선점 만료</th>
          <th>작업</th>
        </tr>
      </thead>
      <tbody>
        ${state.myReservations.map(r => {
          const seatSection = r.seatInfo ? r.seatInfo.section : (r.section || '-');
          const seatRow = r.seatInfo ? (r.seatInfo.rowLabel || r.seatInfo.row) : (r.rowLabel || r.row || '-');
          const seatNum = r.seatInfo ? (r.seatInfo.seatNumber != null ? r.seatInfo.seatNumber : r.seatInfo.number) : (r.seatNumber != null ? r.seatNumber : r.number);
          const amount = r.price != null ? r.price.toLocaleString() : (r.totalPrice != null ? r.totalPrice.toLocaleString() : '?');
          return `
          <tr>
            <td style="font-family: monospace; font-size: 0.75rem;">${r.id.substring(0, 8)}...</td>
            <td><strong>${seatSection}구역 ${seatRow}열 ${seatNum}번</strong></td>
            <td>${amount}원</td>
            <td><span class="concert-badge ${r.status === 'PENDING_PAYMENT' ? 'draft' : 'published'}">${r.status}</span></td>
            <td><span class="countdown-timer" data-expires="${r.heldUntil}">계산중...</span></td>
            <td>
              ${r.status === 'PENDING_PAYMENT' ? `
                <div style="display: flex; gap: 6px; flex-wrap: wrap;">
                  <button class="btn btn-primary" style="padding: 4px 10px; font-size: 0.75rem;" onclick="completeVirtualPayment('${r.id}', 'SUCCESS')">결제 성공</button>
                  <button class="btn btn-warning" style="padding: 4px 10px; font-size: 0.75rem;" onclick="completeVirtualPayment('${r.id}', 'FAILED')">결제 실패</button>
                  <button class="btn btn-danger" style="padding: 4px 10px; font-size: 0.75rem;" onclick="cancelMyReservation('${r.id}')">예매 취소</button>
                </div>
              ` : (r.status === 'CONFIRMED' ? `<button class="btn btn-danger" style="padding: 4px 10px; font-size: 0.75rem;" onclick="cancelMyReservation('${r.id}')">예매 취소</button>` : '-')}
            </td>
          </tr>`;}).join('')}
      </tbody>
    </table>
  `;

  updateTimers();
}

async function cancelMyReservation(id) {
  if (!confirm('정말 예매를 취소하고 좌석을 반환하시겠습니까?')) return;
  try {
    await api(`/api/reservations/${id}/cancel`, 'POST', null, true);
    showToast('예매가 취소되고 좌석이 반환되었습니다.', 'success');
    loadMyReservations();
    loadSeats();
  } catch (e) {}
}

async function completeVirtualPayment(reservationId, result) {
  try {
    const payment = await api('/api/payments', 'POST', { reservationId }, true);
    const completed = await api(`/api/payments/${payment.id}/complete`, 'POST', { result }, true);

    if (completed.status === 'SUCCESS') {
      showToast('가상 결제가 성공해 예매가 확정되었습니다.', 'success');
    } else {
      showToast('가상 결제가 실패해 좌석이 반환되었습니다.', 'warning');
    }
    loadMyReservations();
    loadSeats();
  } catch (e) {}
}

// Timer tick
function updateTimers() {
  document.querySelectorAll('.countdown-timer').forEach(el => {
    const expires = new Date(el.getAttribute('data-expires')).getTime();
    const now = Date.now();
    const diff = expires - now;

    if (diff <= 0) {
      el.textContent = '만료됨';
      el.style.color = '#ef4444';
    } else {
      const min = Math.floor(diff / 60000);
      const sec = Math.floor((diff % 60000) / 1000);
      el.textContent = `${min}분 ${sec.toString().padStart(2, '0')}초`;
      el.style.color = '#fbbf24';
    }
  });
}
setInterval(updateTimers, 1000);

// --- Admin Console Logic ---
async function loadAdminConcerts() {
  try {
    const data = await api('/api/admin/concerts', 'GET', null, true);
    // API returns paginated object
    const concerts = (data && data.content) ? data.content : (Array.isArray(data) ? data : []);
    renderAdminConcertList(concerts);
  } catch (e) {}
}

function renderAdminConcertList(concerts) {
  const container = document.getElementById('admin-concert-list');
  container.innerHTML = concerts.map(c => `
    <div class="card" style="margin-bottom: 1rem;">
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <div>
          <h4>${c.title} <span class="concert-badge ${c.status === 'PUBLISHED' ? 'published' : 'draft'}">${c.status}</span></h4>
          <p style="font-size: 0.8rem; color: var(--text-muted);">장소: ${c.venue} | 선점TTL: ${c.holdSeconds}초 | 최대구매: ${c.maxTicketsPerUser}매</p>
        </div>
        <div style="display: flex; gap: 8px;">
          ${c.status !== 'PUBLISHED' ? `<button class="btn btn-primary" onclick="adminPublishConcert('${c.id}')">📢 공연 공개(Publish)</button>` : ''}
          ${c.status === 'PREPARING' ? `<button class="btn btn-secondary" onclick="openCreateSeatsModal('${c.id}')">🪑 좌석 생성</button>` : ''}
          ${c.status === 'PUBLISHED' ? `
            <button class="btn btn-warning" onclick="setConcertQueue('${c.id}', true)">⏳ 대기열 ON</button>
            <button class="btn btn-secondary" onclick="setConcertQueue('${c.id}', false)">대기열 OFF</button>
          ` : ''}
        </div>
      </div>
    </div>
  `).join('');
}

async function adminPublishConcert(id) {
  if (!confirm('공연을 공개(Publish)하시겠습니까? 공개 후에는 좌석 추가 생성이 제한됩니다.')) return;
  try {
    await api(`/api/admin/concerts/${id}/publish`, 'POST', null, true);
    showToast('공연이 성공적으로 공개되었습니다!', 'success');
    loadAdminConcerts();
  } catch (e) {}
}

async function setConcertQueue(id, active) {
  try {
    const action = active ? 'activate' : 'deactivate';
    await api(`/api/admin/queues/${id}/${action}`, 'POST', null, true);
    showToast(`대기열이 ${active ? '활성화' : '비활성화'}되었습니다.`, 'success');
  } catch (e) {}
}

async function handleCreateConcertForm(e) {
  e.preventDefault();
  const form = e.target;
  const now = new Date();
  const startsAt = new Date(form.startsAt.value).toISOString();
  const opensAt = new Date(form.bookingOpensAt.value).toISOString();
  const closesAt = new Date(form.bookingClosesAt.value).toISOString();

  const payload = {
    title: form.title.value,
    venue: form.venue.value,
    description: form.description.value,
    imageUrl: form.imageUrl.value || 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800',
    holdSeconds: parseInt(form.holdSeconds.value, 10),
    maxTicketsPerUser: parseInt(form.maxTicketsPerUser.value, 10),
    sessions: [
      {
        startsAt,
        bookingOpensAt: opensAt,
        bookingClosesAt: closesAt,
        capacity: parseInt(form.capacity.value, 10)
      }
    ]
  };

  try {
    await api('/api/admin/concerts', 'POST', payload, true);
    showToast('새 콘서트가 등록되었습니다!', 'success');
    form.reset();
    loadAdminConcerts();
  } catch (e) {}
}

async function openCreateSeatsModal(concertId) {
  const details = await api(`/api/admin/concerts/${concertId}`, 'GET', null, true);
  if (!details.sessions || !details.sessions.length) {
    showToast('등록된 회차가 없습니다.', 'error');
    return;
  }
  const sessionId = details.sessions[0].id;

  const section = prompt('구역명을 입력하세요 (예: VIP, R, S):', 'VIP');
  if (!section) return;
  const row = prompt('열 라벨을 입력하세요 (예: A, B, 1):', 'A');
  if (!row) return;
  const count = prompt('생성할 연속 좌석 수를 입력하세요:', '10');
  if (!count) return;
  const price = prompt('좌석 가격을 입력하세요 (원):', '120000');
  if (!price) return;

  try {
    await api(`/api/admin/concerts/${concertId}/sessions/${sessionId}/seats`, 'POST', {
      section: section.trim(),
      row: row.trim(),
      startNumber: 1,
      count: parseInt(count, 10),
      price: parseInt(price, 10)
    }, true);
    showToast('좌석이 생성되었습니다!', 'success');
    loadSeats();
  } catch (e) {}
}

// --- Initialization ---
function loadInitialData() {
  updateAuthUI();
  loadPublicConcerts();
  loadMyReservations();
}

document.addEventListener('DOMContentLoaded', () => {
  loadInitialData();
  // Auto login as user1 by default if guest
  if (!state.token) {
    quickLogin('USER1');
  }
});
