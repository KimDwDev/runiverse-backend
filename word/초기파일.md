## Backend Dependencies

Runiverse 백엔드 서버는 Spring Boot 기반으로 개발하며, 다음 의존성을 사용합니다.

| 의존성                            | 용도                                                    |
| ------------------------------ | ----------------------------------------------------- |
| Spring Web                     | REST API 개발과 HTTP 요청 및 응답 처리를 위해 사용합니다.               |
| Validation                     | 요청 데이터의 필수값, 길이, 범위, 형식 등을 검증하기 위해 사용합니다.             |
| Spring Data JPA                | JPA 기반으로 데이터베이스에 접근하고 엔티티를 관리하기 위해 사용합니다.             |
| PostgreSQL Driver              | Spring Boot 애플리케이션과 PostgreSQL 데이터베이스를 연결하기 위해 사용합니다. |
| Lombok                         | Getter, 생성자, Builder 등의 반복적인 코드를 줄이기 위해 사용합니다.        |
| Spring Security                | 인증 및 인가 처리와 애플리케이션 보안 설정을 위해 사용합니다.                   |
| Spring Data Redis              | Redis를 활용한 캐시, 토큰, 매칭 대기열 및 임시 데이터 관리를 위해 사용합니다.      |
| WebSocket                      | 러닝 세션과 매칭 상태 등의 실시간 양방향 통신을 구현하기 위해 사용합니다.            |
| Spring Boot Actuator           | 서버 상태, 헬스 체크, 메트릭 등 애플리케이션 운영 정보를 확인하기 위해 사용합니다.      |
| Spring Configuration Processor | 커스텀 설정 프로퍼티의 자동완성과 메타데이터 생성을 지원하기 위해 사용합니다.           |

## 주요 활용 범위

* **REST API**: 회원, 프로필, 매칭 예약, 러닝 기록 조회
* **WebSocket**: 매칭 상태 변경, 참가자 상태, 실시간 러닝 데이터 전달
* **PostgreSQL**: 회원, 매칭, 러닝 세션, 러닝 기록 등의 영구 데이터 저장
* **Redis**: 리프레시 토큰, 캐시, 매칭 대기열, 실시간 러닝 데이터의 임시 저장
* **Spring Security 및 OAuth2**: 소셜 로그인 기반 인증과 API 접근 권한 관리
* **Actuator**: 서버 헬스 체크와 모니터링 시스템 연동

## Dependency Summary

```text
Spring Web
Validation
Spring Data JPA
PostgreSQL Driver
Lombok
Spring Security
Spring Data Redis
WebSocket
Spring Boot Actuator
Spring Configuration Processor
```