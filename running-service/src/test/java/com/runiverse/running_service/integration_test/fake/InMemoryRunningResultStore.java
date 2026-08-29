package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.LoadRunningResultPlayersPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultRecordPort;
import com.runiverse.running_service.application.running.port.out.RunningResultPlayer;
import com.runiverse.running_service.application.running.port.out.RunningResultRecord;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Cadence;
import com.runiverse.running_service.domain.running.metric.vo.ElevationGain;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.List;
import java.util.Optional;

// RunningPersistenceAdapter의 조회 두 건을 대신한다 —
// 실제 어댑터처럼 참가자에 기록을 LEFT JOIN하고, 아직 안 끝난 참가자는 지표를 비워 준다
public class InMemoryRunningResultStore
        implements LoadRunningResultPlayersPort, LoadRunningResultRecordPort {

    private final InMemoryRunningStore runningStore;
    private final InMemoryRunningRecordStore recordStore;

    public InMemoryRunningResultStore(InMemoryRunningStore runningStore,
                                      InMemoryRunningRecordStore recordStore) {
        this.runningStore = runningStore;
        this.recordStore = recordStore;
    }

    @Override
    public List<RunningResultPlayer> loadPlayers(RunningRoomId runningRoomId) {
        // 완주·이탈한 참가자도 남긴다 — deletedAt으로 거르면 대시보드가 통째로 빈다
        return runningStore.findRoom(runningRoomId.value()).stream()
                .flatMap(room -> room.getSessions().stream())
                .map(session -> runningStore.findPlayer(session.getRunningPlayerId().value()))
                .flatMap(Optional::stream)
                .map(player -> toResultPlayer(runningRoomId, player))
                .toList();
    }

    @Override
    public Optional<RunningResultRecord> loadRecord(RunningRoomId runningRoomId, UserId userId) {
        return recordStore.find(runningRoomId.value(), userId)
                .map(record -> new RunningResultRecord(
                        record.getRoutePolyline().value(),
                        record.getPeriod().startAt(),
                        record.getPeriod().endAt()));
    }

    private RunningResultPlayer toResultPlayer(RunningRoomId runningRoomId, RunningPlayer player) {
        Optional<RunningRecord> record = recordStore.find(runningRoomId.value(), player.getUserId());
        if (record.isEmpty()) {
            return new RunningResultPlayer(player.getUserId().value(), player.getStatus(),
                    null, null, null, null, null, null);
        }
        RunningRecord found = record.get();
        return new RunningResultPlayer(
                player.getUserId().value(),
                player.getStatus(),
                found.getTotalDistance().meters(),
                found.getTotalDuration().seconds(),
                found.getTotalCalories().kcal(),
                found.getAvgPace().secondsPerKm(),
                found.getAvgCadence().map(Cadence::stepsPerMinute).orElse(null),
                found.getTotalElevationGain().map(ElevationGain::meters).orElse(null));
    }
}
