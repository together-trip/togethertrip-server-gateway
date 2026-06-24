# Gateway Auth Forwarding

Issue: https://github.com/together-trip/togethertrip-server-gateway/issues/5

## 작업

- gateway가 main, chat, notification 앞단에서 공통 진입점 역할을 하도록 인증 체크를 추가한다.
- main 서비스에 있던 JWT 검증 계약을 gateway WebFlux 환경에 맞춰 이전한다.
- 인증이 필요한 요청은 gateway에서 먼저 검증하고, 검증된 사용자 정보만 downstream 서비스로 전달한다.
- docker compose로 gateway, main, chat, notification, PostgreSQL, Redis를 함께 띄울 수 있게 구성한다.

## 배경

기존 구조에서는 main 서비스가 자체적으로 JWT 인증을 처리했다. 앞으로 gateway가 가장 앞단에 위치하고 main, chat, notification으로 요청을 포워딩해야 하므로, 인증 체크도 gateway에서 먼저 수행해야 한다.

gateway는 Spring Cloud Gateway WebFlux 기반이므로 servlet `OncePerRequestFilter`가 아니라 reactive `GlobalFilter`로 인증 흐름을 구성한다.

## 범위

- 보호 경로 요청에서 `Authorization: Bearer` 토큰을 읽는다.
- JWT secret, issuer, role, token type claim을 main과 같은 계약으로 검증한다.
- `tokenType=ACCESS` 토큰만 gateway 인증에 사용한다.
- 인증 성공 시 downstream 요청에 `X-User-Id`, `X-User-Role` 헤더를 주입한다.
- 클라이언트가 보낸 `X-User-Id`, `X-User-Role`은 gateway에서 제거해 스푸핑을 막는다.
- local profile에서는 기존 main 로컬 테스트 토큰인 `local-test:*`를 통과시킨다.
- 공개 인증 API와 swagger, actuator 경로는 gateway 인증 없이 포워딩한다.
- `docker-compose.yml`에 PostgreSQL, Redis, main, chat, notification, gateway 서비스를 정의한다.

## 제외 범위

- main, chat, notification 서비스의 컨트롤러 또는 비즈니스 로직 변경은 포함하지 않는다.
- gateway에서 세션을 만들거나 Spring Security context를 유지하지 않는다.
- refresh token 재발급 로직은 main의 기존 `/api/auth/refresh`로 포워딩한다.

## 설계

`GatewayAuthenticationFilter`는 모든 요청에서 먼저 신뢰할 수 없는 사용자 헤더를 제거한다. 공개 경로가 아니면 bearer token을 검증하고, 유효한 access token일 때만 사용자 식별 헤더를 다시 추가한다.

JWT 관련 클래스는 main의 구조를 gateway 패키지로 옮긴다.

- `JwtProperties`
- `JwtTokenProvider`
- `JwtClaims`
- `TokenType`
- `UserRole`

`GatewayRequestLoggingFilter`도 인증 헤더 스푸핑 방지 정책과 충돌하지 않도록 동일한 사용자 헤더를 제거한다.

compose 환경에서는 서비스 이름으로 통신한다.

- gateway -> `main:8081`
- gateway -> `notification:8082`
- gateway -> `chat:8083`
- app services -> `postgres:5432`
- app services -> `redis:6379`

## 테스트 계획

- `./gradlew test`
- `docker compose config`
- `git diff --check`

## 확인 결과

- gateway 단위 테스트 통과.
- compose yaml 해석 성공.
- diff whitespace 검사 통과.

## 위험과 확인 사항

- 실제 `docker compose up --build`는 이미지 pull과 각 서비스 bootJar 빌드가 필요하므로 네트워크 상태에 영향을 받을 수 있다.
- downstream 서비스들이 앞으로 `X-User-Id`, `X-User-Role`을 신뢰하도록 변경될 때, 직접 서비스 포트 접근을 운영 환경에서 막아야 한다.
- public path 목록은 main의 인증 공개 API가 바뀌면 gateway에서도 같이 갱신해야 한다.
