# Issue #9 Notification local-test 인증 전달 계획

## 배경

Gateway는 클라이언트가 보낸 `X-User-Id`, `X-User-Role`을 제거한 뒤 검증한 인증 정보만 downstream에 전달한다.
현재 local profile의 `local-test:*` 토큰은 JWT 검증만 우회하고 사용자 헤더를 다시 주입하지 않아,
`X-User-Id`를 인증 경계로 사용하는 Notification 보호 API를 Gateway 경유로 검증할 수 없다.

## 정책

- Notification 보호 API의 local-test 토큰은 다음 형식만 사용자 identity로 인정한다.
  - 일반 사용자: `local-test:user:<positiveUserId>`
  - 관리자: `local-test:admin:<positiveUserId>`
- Gateway는 토큰의 숫자 ID와 명시된 역할을 `X-User-Id`, `X-User-Role`에 주입한다.
- 클라이언트가 직접 보낸 사용자 헤더는 항상 먼저 제거한다.
- 빈 값, 0 이하 ID, 숫자가 아닌 ID, 알 수 없는 역할은 401로 거부한다.
- 기존 main 로컬 테스트 별칭 토큰은 main이 자체적으로 해석하므로 기존 통과 정책을 유지한다.
  단, 자체 인증 기능이 없는 `/notification/**`에서는 identity 형식이 아니면 401로 거부한다.
- 이 정책은 `auth.local-test.enabled=true`인 local/test 환경에만 적용한다.

Gateway는 도메인 DB를 조회하지 않으므로 별칭을 사용자 ID로 추측하거나 main DB와 결합하지 않는다.

## 구현 범위

1. local-test identity 토큰을 엄격하게 파싱하는 값 객체를 추가한다.
2. Notification local-test 요청에 파싱된 사용자 헤더를 주입한다.
3. 잘못된 Notification local-test identity는 downstream 전에 차단한다.
4. `/notification/api/notifications`, `/notification/api/push-tokens`의 path 보존과 헤더 전달을 통합 테스트로 고정한다.
5. spoofing 헤더가 토큰 identity로 덮어써지는지 검증한다.

## 검증

- `./gradlew test`
- `git diff --check`
- Code Reviewer/Security Reviewer 관점에서 local profile 한정, 헤더 위조 방지, 잘못된 토큰 거부를 확인한다.

## 기준 브랜치

작업 시작 시점의 최신 추적 기준인 `origin/develop` (`4ce626b`)에서 분기한다.

