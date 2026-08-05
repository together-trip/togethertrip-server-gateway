# Review Report: 이슈 #23 출시 보안 강화

## 요약

Gateway 책임인 외부 진입점 정책만 변경했으며 main 도메인 모델이나 요청 본문을 해석하는 로직은 추가하지 않았다.
Code Reviewer와 Security Reviewer 관점에서 CORS, forwarded header spoofing, HTTPS, rate limit key, 인증 전파,
오류·로그 노출, 필터 순서를 검토했다. 리뷰 중 발견한 운영 HTTPS origin 미검증과 XFF prefix 위조 가능성은
구현·테스트로 보정했다.

## 발견 사항

| 심각도 | 파일 | 내용 | 조치 |
| --- | --- | --- | --- |
| 높음 | `GatewayCorsConfiguration.kt` | 운영 설정값이 HTTP origin이어도 시작할 수 있었다. | `requireHttpsOrigins`와 URI origin 검증을 추가했다. |
| 높음 | `TrustedProxyForwardedHeaderConfiguration.kt` | 일반 transformer는 XFF 첫 값을 사용해 proxy append 환경에서 공격자가 prefix를 위조할 수 있다. | chain을 오른쪽부터 검사해 trusted proxy가 아닌 첫 주소를 client IP로 고정했다. |
| 중간 | `DownstreamErrorSanitizingFilter.kt` | 5xx 본문을 모아서 폐기하면 큰 응답이 메모리를 점유하고, 본문 없는 5xx는 공통 응답이 없을 수 있었다. | buffer를 즉시 release하고 `setComplete`도 안전 응답으로 치환했다. |
| 중간 | `GatewayRequestLoggingFilter.kt` | 예외 객체 전체와 사용자 식별 path, OAuth code query가 로그에 남을 수 있었다. | 예외 class만 기록하고 path/query 마스킹을 추가했다. |
| 낮음 | `GatewaySecurityProperties.kt` | 제거된 전화번호 인증 endpoint가 계속 공개 경로였다. | 공개 목록에서 제거하고 회귀 테스트를 추가했다. |

## API 계약 검토

- `/api/auth/oauth/kakao`, `/api/auth/oauth/apple`, `/api/auth/refresh`는 token 없이 main route로 전달한다.
- `/api/trips/{tripId}/reports`, `/api/users/{userId}/blocks`, `/api/users/me/blocks`, `/api/users/me`,
  `/notification/api/push-tokens`는 access token이 필요하다.
- 보호 API는 클라이언트의 `X-User-Id`, `X-User-Role`을 제거하고 JWT claim 값만 downstream에 전달한다.
- Gateway는 신고·차단·삭제의 도메인 요청/응답을 해석하지 않는다.
- downstream 4xx 계약은 유지하고 5xx만 공통 안전 오류로 격리한다.

## 확인한 명령

- `./gradlew test --tests '*GatewayCorsConfigurationTest' ...`
- `./gradlew test --tests '*TrustedProxyForwardedHeaderConfigurationTest' ...`
- `./gradlew test --tests '*DownstreamErrorSanitizingFilterTest' ...`
- `./gradlew test`
- `git diff --check`

## 남은 위험

- rate limit은 인스턴스별이므로 replica 전체 한도는 ingress/WAF가 담당해야 한다.
- 신뢰 proxy CIDR은 실제 load balancer/ingress 주소 범위와 일치해야 한다.
- proxy가 외부 `Forwarded`, `X-Forwarded-*`를 제거·덮어쓰는지는 운영 인프라에서 검증해야 한다.
- TLS 인증서 체인과 실제 서비스 연동 E2E는 로컬 자동 테스트로 검증할 수 없다.
