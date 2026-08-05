# Review Report: 이슈 #9 Notification local-test identity

## 요약

Code Reviewer와 Security Reviewer 관점에서 local-test 토큰 해석, 필터 순서, 사용자 헤더 위조 방지,
Notification 경로 경계와 운영 profile 영향을 검토했다. Gateway가 main 또는 Notification DB를 조회하지 않고,
local/test에서만 토큰에 명시된 identity를 전달하도록 범위를 제한했다.

## 발견 사항

| 심각도 | 파일 | 내용 | 조치 |
| --- | --- | --- | --- |
| 높음 | `GatewayAuthenticationFilter.kt` | 기존 local-test 분기는 클라이언트 사용자 헤더를 제거한 뒤 Notification에 대체 identity를 전달하지 않았다. | Notification local-test 토큰을 엄격하게 해석해 검증한 헤더를 다시 주입했다. |
| 중간 | `GatewayAuthenticationFilter.kt` | 기존 별칭 토큰을 임의의 사용자 ID로 추측하면 main DB 사용자와 다른 identity로 처리될 수 있다. | `user/admin + positiveUserId` 형식만 허용하고 모호한 토큰은 Notification 경로에서 401로 거부했다. |
| 낮음 | `GatewayNotificationRoutingIntegrationTest.kt` | 기존 통합 테스트는 임의 경로와 JWT만 사용해 실제 알림함/push token local-test 계약을 고정하지 못했다. | 두 정확한 API 경로, HTTP method, path 보존, spoofing 헤더 덮어쓰기를 검증했다. |

## 보안 경계 확인

- `GatewayRequestLoggingFilter`가 외부 사용자 헤더를 제거한 뒤 인증 필터가 검증한 identity만 주입한다.
- `auth.local-test.enabled=false`인 운영 profile에서는 기존 JWT 검증 경로만 사용한다.
- `/notification`과 `/notification/**` 외 경로의 기존 local-test 호환 동작은 변경하지 않는다.
- Gateway는 downstream DB나 도메인 모델에 의존하지 않는다.

## 확인한 명령

- `./gradlew test --tests '*LocalTestIdentityTest' --tests '*GatewayAuthenticationFilterTest' --tests '*GatewayNotificationRoutingIntegrationTest'`
- `./gradlew test`
- `git diff --check`

## 남은 위험

- local-test identity의 사용자 ID는 호출자가 로컬 Notification 데이터와 일치하도록 선택해야 한다.
- 이 형식은 로컬 검증 전용이며 실제 앱/운영 요청은 JWT access token을 사용해야 한다.
