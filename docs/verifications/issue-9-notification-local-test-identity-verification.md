# Verification Report: 이슈 #9 Notification local-test identity

## 검증 대상

- `local-test:user:<positiveUserId>`, `local-test:admin:<positiveUserId>` 파싱
- 잘못되거나 모호한 Notification local-test 토큰의 401 처리
- 외부 `X-User-Id`, `X-User-Role` 제거와 검증 identity 재주입
- `GET /notification/api/notifications` path 보존과 사용자 헤더 전달
- `POST /notification/api/push-tokens` path 보존과 사용자 헤더 전달
- JWT 기반 기존 Notification 라우팅 회귀

## 실행한 명령

```bash
./gradlew test --tests '*LocalTestIdentityTest' \
  --tests '*GatewayAuthenticationFilterTest' \
  --tests '*GatewayNotificationRoutingIntegrationTest'
./gradlew test
git diff --check
```

## 결과

- 집중 인증·라우팅 테스트 성공
- 전체 Gateway 테스트 성공
- 알림 목록과 push token 요청이 원래 path 그대로 Notification route에 전달됨
- 클라이언트가 보낸 `999/ADMIN` 헤더가 토큰의 `321/USER` identity로 교체됨
- 별칭형 `local-test:verified:swagger`는 Notification 보호 경로에서 401로 차단됨
- JWT access token 기반 기존 라우팅과 미인증 401 테스트 통과

## 실패 또는 미검증 항목

- 실제 Notification 프로세스와 DB를 연결한 수동 local E2E는 수행하지 않았다.
- 운영 profile은 local-test가 비활성화되므로 local-test 토큰으로 검증하지 않았다.

## 다음 조치

1. 로컬 Notification 데이터의 사용자 ID를 확인해 identity 토큰에 명시한다.
2. 실제 운영 요청은 기존과 같이 JWT access token을 사용한다.
