FROM eclipse-temurin:21-jre AS builder

WORKDIR /builder

COPY build/libs/*.jar application.jar

RUN java -Djarmode=tools -jar application.jar \
    extract --layers --destination extracted

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring spring \
    && mkdir -p /app/logs \
    && chown spring:spring /app/logs

COPY --from=builder --chown=spring:spring /builder/extracted/dependencies/ ./
COPY --from=builder --chown=spring:spring /builder/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=spring:spring /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=spring:spring /builder/extracted/application/ ./

USER spring:spring

EXPOSE 8080

# 힙 상한을 서버 메모리의 비율로 정한다(F8, D-66). docker run에 --memory 제한이 없어 서버 전체 메모리가 기준이다 —
# 지정하지 않으면 JVM 기본값 25%(t3.small에서 약 512MB)다. 배포 env 파일에 JVM_MAX_RAM_PERCENTAGE를 넣어 바꾼다.
# 75%처럼 높이면 힙 밖(스레드 · 메타스페이스 · 코드 캐시)과 OS · Docker · Filebeat를 합쳐 서버 메모리를 넘을 수 있다
ENV JVM_MAX_RAM_PERCENTAGE=50.0

# exec로 셸을 java로 바꾼다 — 셸이 PID 1로 남으면 SIGTERM이 java에 가지 않아 정상 종료(계획 종료)가 돌지 않는다
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=${JVM_MAX_RAM_PERCENTAGE} -jar application.jar"]
