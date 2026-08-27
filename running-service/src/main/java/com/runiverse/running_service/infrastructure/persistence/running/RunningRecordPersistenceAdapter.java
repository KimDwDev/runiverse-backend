package com.runiverse.running_service.infrastructure.persistence.running;

import com.runiverse.running_service.application.running.port.out.CreateRunningRecordPort;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.record.RunningSplit;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RunningRecordPersistenceAdapter implements CreateRunningRecordPort {

    // 방당 수천 행이라 영속성 컨텍스트를 비워가며 넣는다.
    // hibernate.jdbc.batch_size와 맞춰야 실제로 묶여 나간다
    private static final int BATCH_SIZE = 100;
    private final EntityManager entityManager;

    @Override
    public void create(RunningRecord record) {
        if (!record.isNew()) {
            throw new IllegalStateException("이미 저장된 기록이다 — 기록은 write-once다");
        }
        // 구간이 참조할 방 프록시. 실제 SELECT 없이 FK 값만 쓴다
        RunningRoomJpaEntity room = entityManager.getReference(
                RunningRoomJpaEntity.class, record.getRunningRoomId().value());
        RunningRecordJpaEntity recordEntity = RunningRecordJpaEntity.create(
                room,
                record.getUserId().value(),
                record.getAvgPace().secondsPerKm(),
                record.getTotalDistance().meters(),
                record.getTotalDuration().seconds(),
                record.getAvgCadence().map(cadence -> cadence.stepsPerMinute()).orElse(null),
                record.getTotalElevationGain().map(gain -> gain.meters()).orElse(null),
                record.getTotalCalories().kcal(),
                record.getGpsTrackKey().value(),
                record.getRoutePolyline().value(),
                record.getWeatherCode().value(),
                record.getTemperature().celsius(),
                record.getPeriod().startAt(),
                record.getPeriod().endAt());
        // IDENTITY 전략이라 여기서 INSERT가 나가고 ID가 채워진다 — 구간이 이 ID를 참조한다
        entityManager.persist(recordEntity);
        int persisted = 0;
        for (RunningSplit split : record.getSplits()) {
            entityManager.persist(toEntity(recordEntity, split));
            // 쌓아두면 방 하나에 수천 개가 컨텍스트에 남아 메모리와 flush 비용이 같이 커진다
            if (++persisted % BATCH_SIZE == 0) {
                entityManager.flush();
                entityManager.clear();
                // clear가 recordEntity까지 준영속으로 만든다 — 다음 구간이 참조할 프록시를 다시 얻는다
                recordEntity = entityManager.getReference(
                        RunningRecordJpaEntity.class, recordEntity.getRunningRecordId());
            }
        }
    }

    private RunningSplitJpaEntity toEntity(RunningRecordJpaEntity record, RunningSplit split) {
        return RunningSplitJpaEntity.create(
                record,
                split.getSplitNumber().value(),
                split.getAvgPace().secondsPerKm(),
                split.getDistance().meters(),
                split.getDuration().seconds(),
                split.getAvgCadence().map(cadence -> cadence.stepsPerMinute()).orElse(null),
                split.getElevationChange().map(change -> change.meters()).orElse(null),
                split.getCalories().kcal(),
                split.getRouteRange().startIndex(),
                split.getRouteRange().endIndex(),
                split.getPeriod().startAt(),
                split.getPeriod().endAt());
    }
}
