package com.runiverse.running_service.presentation.match.request;

import jakarta.validation.constraints.AssertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

// 쿼리 파라미터라 글자로 받는다 — 날짜 타입으로 받으면 변환 실패 문구(내부 타입명·입력값)가 그대로 400 본문에 실린다.
// 거리는 받지 않는다 — 고를 수 있는 거리를 전부 내려 준다
public record MatchSlotsRequest(
        String date
) {

    private static final Pattern DATE_FORMAT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    // 앞 검사에서 걸린 값은 뒤 검사가 통과시킨다 — 한 가지 잘못에 문구가 둘 나가지 않게
    @AssertTrue(message = "입력값이 올바르지 않습니다.")
    public boolean isFormatValid() {
        return date == null || toDate(date) != null;
    }

    // "오늘"은 JVM 기본 타임존(KST, DefaultTimeZoneInitializer) 기준이다
    @AssertTrue(message = "조회 날짜는 오늘 이전일 수 없습니다.")
    public boolean isDateNotPast() {
        LocalDate parsed = toDate(date);
        return parsed == null || !parsed.isBefore(LocalDate.now());
    }

    // 생략하면 오늘. 검증을 통과한 뒤에만 부른다
    public LocalDate dateOrToday() {
        return date == null ? LocalDate.now() : LocalDate.parse(date);
    }

    // 형식이 다르거나 없는 날짜(2월 30일)면 null
    private static LocalDate toDate(String value) {
        if (value == null || !DATE_FORMAT.matcher(value).matches()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
