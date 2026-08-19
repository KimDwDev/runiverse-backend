package com.runiverse.running_service.domain.running.aggregate;

import com.runiverse.running_service.domain.running.exception.CaloriesRequiredException;
import com.runiverse.running_service.domain.running.vo.Cadence;
import com.runiverse.running_service.domain.running.vo.Calories;
import com.runiverse.running_service.domain.running.vo.Distance;
import com.runiverse.running_service.domain.running.vo.ElapsedTime;
import com.runiverse.running_service.domain.running.vo.ElevationChange;
import com.runiverse.running_service.domain.running.vo.Pace;
import com.runiverse.running_service.domain.running.vo.RouteRange;
import com.runiverse.running_service.domain.running.vo.RunningPeriod;
import com.runiverse.running_service.domain.running.vo.SplitNumber;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Optional;

@Getter
public class RunningSplit {

    private final SplitNumber splitNumber;
    private final Pace avgPace;
    private final Distance distance;
    private final ElapsedTime duration;
    private final RouteRange routeRange;
    private final RunningPeriod period;
    private final Calories calories;
    // 선택 항목
    private final Cadence avgCadence;
    private final ElevationChange elevationChange;

    @Builder(access = AccessLevel.PACKAGE)
    private RunningSplit(int splitNumber, int avgPace, int distance, int duration,
                         int routeStartIndex, int routeEndIndex,
                         LocalDateTime startAt, LocalDateTime endAt,
                         Integer calories, // calories만 0이 나올가능성이 가장 크다
                         Integer avgCadence, Integer elevationChange) {
        this.splitNumber = new SplitNumber(splitNumber);
        this.avgPace = new Pace(avgPace);
        this.distance = new Distance(distance);
        this.duration = new ElapsedTime(duration);
        this.routeRange = new RouteRange(routeStartIndex, routeEndIndex);
        this.period = new RunningPeriod(startAt, endAt);
        if (calories == null) {
            throw new CaloriesRequiredException();
        }
        this.calories = new Calories(calories);
        this.avgCadence = avgCadence == null ? null : new Cadence(avgCadence);
        this.elevationChange = elevationChange == null ? null : new ElevationChange(elevationChange);
    }

    // cadence는 있을 수도 없을 수도
    public Optional<Cadence> getAvgCadence() {
        return Optional.ofNullable(avgCadence);
    }

    public Optional<ElevationChange> getElevationChange() {
        return Optional.ofNullable(elevationChange);
    }
}
