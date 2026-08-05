# Architecture

클린 아키텍처 + DDD. 패키지 루트는 `com.runiverse.running_service`, 의존 방향은 항상 안쪽(domain)으로 향한다.

```
presentation → application(port/in) → domain
infrastructure → application(port/out) → domain
```

## 레이어별 규칙

- **domain/**: 프레임워크 의존 금지. VO는 생성 시점에 검증하고 도메인 예외를 던진다. 하위는 `aggregate`·`vo`·`exception`으로 나눈다.
- **application/**: 기능별 패키지에 `Command`/`Handler`/`Result` 3종 세트를 두고, `port/in`의 `*Usecase`를 구현한다. 트랜잭션 경계는 Handler(`@Transactional`)다.
- **port/out**: 단일 메서드 인터페이스로 잘게 쪼갠다 — 기존 포트에 메서드를 추가하지 말고 새 포트를 만든다. Handler는 필요한 포트만 주입받는다.
- **infrastructure/**: 아웃바운드 어댑터. 도메인 ↔ JPA 엔티티 변환은 어댑터 책임이다. 구현체는 `*Adapter`, 외부 API 클라이언트는 `*Client`로 명명한다. 같은 도메인의 여러 포트는 어댑터 하나가 함께 구현한다 — 포트마다 어댑터를 만들지 않는다.
- **presentation/**: 컨트롤러 + DTO. 예외는 `GlobalExceptionHandler`에서 일괄 변환한다.

## 구현 스타일 기준

기존 구현을 참고할 때는 `application/auth/command/signup`·`login` 패키지가 기준이다.
