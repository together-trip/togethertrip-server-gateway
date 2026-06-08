# Work: 게이트웨이 공통 라우팅 로그 구현

작성일: 2026-06-08
브랜치: `feature/issue-3-gateway-route-logging`
이슈: https://github.com/together-trip/togethertrip-server-gateway/issues/3
PR: https://github.com/together-trip/togethertrip-server-gateway/pull/4

## 작업

Gateway 서버에 WebFlux/Gateway 특화 공통 라우팅 로그를 추가했다.

Gateway는 Servlet MVC가 아니라 WebFlux 기반이므로 `OncePerRequestFilter`나 Service AOP 대신 `GlobalFilter`에서 requestId, routeId, downstream 응답 상태, latency를 기록하도록 구성했다.

## 배경

Gateway 서버는 `app -> gateway -> main/chat/notification` 흐름의 외부 진입점이다.

운영 중에는 다음 정보를 빠르게 확인해야 한다.

- 클라이언트 요청이 어떤 routeId로 전달되었는지
- downstream 응답 상태와 latency가 어떤지
- requestId가 downstream 서버까지 전달되었는지
- 클라이언트가 보낸 spoofing 가능 header가 내부 인증 정보처럼 전달되지 않았는지
- Authorization, cookie, token, 개인정보성 query가 로그에 남지 않았는지

## 수정

- Gateway `GlobalFilter` 기반 요청 로그를 추가했다.
- `X-Request-Id` 생성/유지 로직을 추가했다.
- requestId를 downstream 요청 header와 gateway 응답 header에 반영했다.
- 요청 완료 시 routeId, method, path, status, elapsedMs를 로그로 남기도록 했다.
- downstream 처리 중 예외가 발생하면 실패 로그를 남기도록 했다.
- 클라이언트가 보낸 spoofing 가능 `X-User-Id` header를 downstream 전달 전에 제거했다.
- token, cookie, Authorization, 전화번호, 이메일 마스킹 유틸을 추가했다.
- Gateway filter와 마스킹 단위 테스트를 추가했다.

## 변경 파일

- `build.gradle.kts`
  - `reactor-test` 테스트 의존성 추가
- `src/main/kotlin/com/togethertrip/gateway/global/logging/GatewayRequestLoggingFilter.kt`
  - requestId 전파, route 로그, spoofing header 제거
- `src/main/kotlin/com/togethertrip/gateway/global/logging/SensitiveDataMasker.kt`
  - query/header 성격 민감정보 마스킹
- `src/main/kotlin/com/togethertrip/gateway/global/logging/RequestIdGenerator.kt`
  - requestId 생성
- `src/test/kotlin/com/togethertrip/gateway/global/logging/*`
  - Gateway 로깅 단위 테스트

## 테스트

```bash
./gradlew test
```

검증 결과:

- 전체 테스트 통과

## 위험과 확인 사항

- Gateway는 reactive 환경이므로 ThreadLocal MDC를 무리하게 쓰지 않았다.
- downstream 오류 body는 민감정보를 포함할 수 있으므로 전문 로그를 남기지 않는다.
- requestId/header 정책은 main/chat/notification 서버와 호환되도록 유지해야 한다.

## 관련 이슈

- https://github.com/together-trip/togethertrip-server-gateway/issues/3
