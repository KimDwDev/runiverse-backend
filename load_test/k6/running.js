// 러닝 WebSocket 부하 시나리오 — VU 1개 = 러너 1명.
// RUNNING_START → 1초마다 좌표 생성, 10초마다 RUNNING_LOCATION_UPDATE → RUNNING_FINISH(forced=true)
// 방 안 자리(slot)마다 역할이 다르다.
//   0, 1: 나란히 달리기(3m 간격) — 콤보가 쌓인다
//   2   : 신호 대기 — 중간에 제자리에 서 있는다
//   3   : 일시정지 후 재개, 이어서 연결을 끊고 재연결(마지막 배치 재전송 포함)
//
// 필수 환경 변수: JWT_SECRET(서버의 JWT_ACCESS_SECRET과 같은 값)
// 선택: WS_URL, RUN_ID, ROOMS, RUN_SECONDS, TZ_OFFSET_MINUTES

import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { mintAccessToken } from './lib/token.js';
import { roomIdOf, slotOf, userIdOf, RUNNERS_PER_ROOM } from './lib/ids.js';
import { createTrack, holdPosition, nextPoint } from './lib/track.js';

const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/api/v1/ws/running';
const JWT_SECRET = __ENV.JWT_SECRET;
const RUN_ID = Number(__ENV.RUN_ID || 1);
const ROOMS = Number(__ENV.ROOMS || 1);
const RUN_SECONDS = Number(__ENV.RUN_SECONDS || 120);

const BATCH_SECONDS = 10;
const PAUSE_SECONDS = 20;
const RECONNECT_GAP_SECONDS = 5;
const FINISH_TIMEOUT_MS = 30 * 1000;
// 정해진 시간보다 이만큼 넘게 걸리면 이상 상황으로 보고 끊는다
const HARD_LIMIT_SECONDS = RUN_SECONDS + 90;

// 역할별 시점 — 전체 러닝 시간에 대한 비율
const WAIT_FROM = 0.40;
const WAIT_UNTIL = 0.55;
const PAUSE_AT = 0.30;
const RECONNECT_AT = 0.65;

const startRtt = new Trend('running_start_rtt', true);
const batchRtt = new Trend('running_batch_rtt', true);
const finishRtt = new Trend('running_finish_rtt', true);
const wsErrors = new Counter('running_ws_errors');
const comboUpdates = new Counter('running_combo_updates');
const finishTimeouts = new Counter('running_finish_timeouts');
const finishCompleted = new Rate('running_finish_completed');

export const options = {
  scenarios: {
    smoke: {
      executor: 'per-vu-iterations',
      vus: ROOMS * RUNNERS_PER_ROOM,
      iterations: 1,
      maxDuration: `${HARD_LIMIT_SECONDS + 60}s`,
    },
  },
  thresholds: {
    running_ws_errors: ['count==0'],
    running_finish_completed: ['rate==1'],
    checks: ['rate==1'],
  },
};

export function setup() {
  if (!JWT_SECRET) {
    throw new Error('JWT_SECRET이 비어 있습니다 — 서버의 JWT_ACCESS_SECRET과 같은 값을 넣으세요');
  }
}

function send(socket, event, data) {
  socket.send(JSON.stringify({ event, data }));
}

function flush(socket, runner) {
  if (runner.buffer.length === 0) {
    return;
  }
  send(socket, 'RUNNING_LOCATION_UPDATE', { locations: runner.buffer });
  runner.lastBatch = runner.buffer;
  runner.buffer = [];
  runner.batchSentAt = Date.now();
}

function sendFinish(socket, runner) {
  flush(socket, runner);
  runner.finishSentAt = Date.now();
  send(socket, 'RUNNING_FINISH', { forced: true });
}

// 1초마다 — 역할에 따라 상태를 바꾸고 점을 하나 쌓는다
function tick(socket, runner, connection) {
  const now = Date.now();
  const elapsed = (now - runner.startedAtMs) / 1000;

  if (elapsed > HARD_LIMIT_SECONDS) {
    console.error(`[${runner.userId}] 제한 시간 초과 — 연결을 끊습니다`);
    socket.close();
    return;
  }
  if (runner.finishSentAt !== null) {
    if (now - runner.finishSentAt > FINISH_TIMEOUT_MS) {
      finishTimeouts.add(1);
      console.error(`[${runner.userId}] RUNNING_FINISHED 응답 없음`);
      socket.close();
    }
    return;
  }
  if (!connection.started) {
    return;
  }
  if (elapsed >= RUN_SECONDS) {
    sendFinish(socket, runner);
    return;
  }

  if (runner.slot === 3) {
    if (!runner.pauseDone && runner.pausedAt === null && elapsed >= RUN_SECONDS * PAUSE_AT) {
      flush(socket, runner);
      send(socket, 'RUNNING_PAUSE', {});
      runner.pausedAt = now;
    }
    if (runner.pausedAt !== null && now - runner.pausedAt >= PAUSE_SECONDS * 1000) {
      send(socket, 'RUNNING_RESUME', {});
      runner.pausedAt = null;
      runner.pauseDone = true;
    }
    if (!runner.reconnectDone && elapsed >= RUN_SECONDS * RECONNECT_AT) {
      flush(socket, runner);
      runner.reconnectDone = true;
      runner.needReconnect = true;
      socket.close();
      return;
    }
  }

  if (runner.pausedAt !== null) {
    holdPosition(runner.track, now);
    return;
  }

  const standing = runner.slot === 2
    && elapsed >= RUN_SECONDS * WAIT_FROM
    && elapsed < RUN_SECONDS * WAIT_UNTIL;
  runner.buffer.push(nextPoint(runner.track, now, standing));
}

function onMessage(socket, runner, connection, raw) {
  const message = JSON.parse(raw);
  const now = Date.now();

  switch (message.event) {
    case 'RUNNING_STARTED':
      if (!connection.started) {
        startRtt.add(now - connection.startSentAt);
        connection.started = true;
        // 재연결 직후 마지막 배치를 다시 보낸다 — 서버가 sequence로 중복을 걸러야 한다
        if (connection.resendLastBatch && runner.lastBatch.length > 0) {
          send(socket, 'RUNNING_LOCATION_UPDATE', { locations: runner.lastBatch });
        }
      }
      break;
    case 'RUNNING_PROGRESS_UPDATED':
      if (message.data.userId === runner.userId && runner.batchSentAt !== null) {
        batchRtt.add(now - runner.batchSentAt);
        runner.batchSentAt = null;
      }
      break;
    case 'RUNNING_COMBO_UPDATED':
      comboUpdates.add(1);
      break;
    case 'RUNNING_FINISHED':
      if (runner.finishSentAt !== null) {
        finishRtt.add(now - runner.finishSentAt);
      }
      runner.finished = true;
      // 바로 끊으면 방 브로드캐스트가 닫힌 소켓에 부딪혀 서버에 전송 오류 WARN이 쌓인다
      socket.setTimeout(() => socket.close(), 1000);
      break;
    case 'ERROR':
      wsErrors.add(1, { code: message.data.code });
      console.warn(`[${runner.userId}] ERROR ${message.data.code} - ${message.data.message}`);
      break;
    default:
      break;
  }
}

function connect(runner, resendLastBatch) {
  const connection = { started: false, startSentAt: null, resendLastBatch };
  // 끊기 직전 배치의 응답은 오지 않는다 — 재연결 뒤 진행 알림과 짝지어 재지 않게 비운다
  runner.batchSentAt = null;
  const params = { headers: { Authorization: `Bearer ${runner.token}` } };

  const response = ws.connect(WS_URL, params, (socket) => {
    socket.on('open', () => {
      connection.startSentAt = Date.now();
      send(socket, 'RUNNING_START', { runningRoomId: runner.roomId });
      socket.setInterval(() => tick(socket, runner, connection), 1000);
      socket.setInterval(() => {
        if (connection.started && runner.pausedAt === null && runner.finishSentAt === null) {
          flush(socket, runner);
        }
      }, BATCH_SECONDS * 1000);
    });
    socket.on('message', (raw) => onMessage(socket, runner, connection, raw));
    socket.on('error', (e) => {
      wsErrors.add(1, { code: 'SOCKET_ERROR' });
      console.error(`[${runner.userId}] 소켓 오류 - ${e.error()}`);
    });
  });

  check(response, { 'WS 핸드셰이크 101': (r) => r && r.status === 101 });
}

export default function () {
  const userIndex = __VU - 1;
  const slot = slotOf(userIndex);
  const userId = userIdOf(userIndex);
  const roomId = roomIdOf(RUN_ID, userIndex);

  const runner = {
    userId,
    roomId,
    slot,
    token: mintAccessToken(JWT_SECRET, userId),
    track: createTrack(Math.floor(userIndex / RUNNERS_PER_ROOM), slot),
    startedAtMs: Date.now(),
    buffer: [],
    lastBatch: [],
    batchSentAt: null,
    pausedAt: null,
    pauseDone: false,
    reconnectDone: false,
    needReconnect: false,
    finishSentAt: null,
    finished: false,
  };

  connect(runner, false);
  while (runner.needReconnect && !runner.finished) {
    runner.needReconnect = false;
    sleep(RECONNECT_GAP_SECONDS);
    connect(runner, true);
  }

  finishCompleted.add(runner.finished);
}
