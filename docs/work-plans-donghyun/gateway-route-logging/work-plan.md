# Work Plan

## 작업

이슈 #3 `feat: 게이트웨이 공통 라우팅 로그 구현`을 진행한다.

- GitHub Issue: https://github.com/together-trip/togethertrip-server-gateway/issues/3

Gateway 서버 특성에 맞춰 WebFlux/Gateway `GlobalFilter` 기반 요청 식별자, 라우팅, downstream 응답 로그를 구현한다.

## 배경

`gateway` 서버는 `app -> gateway -> main/chat/notification` 흐름의 외부 진입점이다.

Gateway는 Servlet MVC가 아니라 WebFlux 기반이므로 main/chat/notification 서버의 `OncePerRequestFilter` 또는 Service AOP를 그대로 복사하면 안 된다.

운영 중에는 다음 정보를 일관되게 확인할 수 있어야 한다.

- 클라이언트 요청이 어떤 routeId로 전달되었는지
- downstream 응답 상태와 latency가 어떤지
- requestId가 downstream 서버까지 전달되었는지
- 클라이언트가 보낸 spoofing 가능 header가 내부 인증 정보처럼 취급되지 않았는지
- Authorization, cookie, token, 개인정보성 query가 로그에 노출되지 않았는지

## 범위

- WebFlux/Gateway `GlobalFilter` 기반 요청 로그를 추가한다.
- `X-Request-Id`를 생성/유지한다.
- requestId를 downstream 요청과 gateway 응답 헤더에 반영한다.
- routeId, method, path, status, elapsedMs를 요청 완료 시점에 기록한다.
- downstream 실패 또는 예외 발생 시 실패 로그를 남긴다.
- 클라이언트가 보낸 spoofing 가능 `X-User-Id` header를 제거한다.
- token, cookie, Authorization, 전화번호, 이메일을 마스킹한다.
- 관련 단위 테스트를 추가한다.

## 제외 범위

- main/chat/notification 서버 내부 Service AOP 구현.
- 실제 rate limit 정책 구현.
- 인증 토큰 검증 정책 변경.
- downstream retry/timeout 정책 변경.
- 외부 관측 도구 연동.
- downstream 응답 body 전문 로그 출력.

## 설계

- `global/logging` 패키지에 Gateway 로깅 컴포넌트를 둔다.
- `GatewayRequestLoggingFilter`는 `GlobalFilter`와 `Ordered`를 구현한다.
- 요청 시작 시 requestId를 검증하고 없으면 새로 생성한다.
- downstream 요청 header와 gateway response header에 requestId를 설정한다.
- 클라이언트가 보낸 `X-User-Id`는 spoofing 가능 header로 보고 downstream 전달 전에 제거한다.
- route 정보는 `ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR`에서 읽는다.
- Servlet MDC 대신 요청 완료 시점의 exchange 정보를 기반으로 로그를 남긴다.
- `SensitiveDataMasker`는 query/header 성격의 민감정보를 원문 그대로 남기지 않는다.

## 테스트 계획

- requestId가 없는 요청은 새 requestId를 생성하고 downstream/response에 반영하는지 검증한다.
- requestId가 있는 요청은 기존 값을 유지하는지 검증한다.
- `X-User-Id` spoofing header가 downstream 요청에서 제거되는지 검증한다.
- route 정보를 가진 요청이 정상 완료되는지 검증한다.
- token, cookie, Authorization, 전화번호, 이메일이 마스킹되는지 검증한다.
- 최종 검증 명령은 `./gradlew test`다.

## 위험과 확인 사항

- Gateway는 reactive 환경이므로 ThreadLocal MDC를 무리하게 쓰면 로그 컨텍스트 누수/손실이 생길 수 있다.
- downstream 오류 body는 민감정보를 포함할 수 있으므로 전문 로그를 금지한다.
- requestId/header 정책은 main/chat/notification 서버와 호환되도록 유지한다.
