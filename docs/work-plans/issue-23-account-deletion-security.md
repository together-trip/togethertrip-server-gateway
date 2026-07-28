# Work Plan: 이슈 #23 계정 삭제 라우팅·인증 보안

## 작업

스토어 출시 API 중 계정 삭제와 notification 정리 경로가 Gateway의 보호 경로·헤더 위조 방지·오류
격리 정책을 따르는지 계약 테스트와 운영 설정으로 고정한다.

## 배경

Gateway는 `/api/**`, `/notification/**`를 라우팅하지만 계정 삭제 DELETE 요청과 탈퇴 직전 push token
정리 요청의 end-to-end 계약이 명시적으로 검증되지 않았다.

## 범위

- `DELETE /api/users/me`가 인증 필수 main route로 전달되는지 검증한다.
- `DELETE /notification/api/push-tokens`가 인증 필수 notification route로 전달되는지 검증한다.
- 클라이언트가 보낸 `X-User-Id`, `X-User-Role`을 제거하고 JWT claim 값만 전달하는지 검증한다.
- 토큰 없음·refresh token·위조/만료 access token을 401로 차단한다.
- downstream 오류 본문과 민감 인증정보가 Gateway 로그에 노출되지 않는지 점검한다.
- route·보안 계약과 운영 환경 문서를 갱신한다.

## 제외 범위

- 계정 상태와 데이터 삭제 도메인 로직은 Gateway에 두지 않는다.
- TLS 인증서와 DNS 프로비저닝은 infra 범위다.
- 로그인·신고 API 전체 rate limit 재설계는 계정 삭제 계약과 분리한다.

## 설계

- 기존 global authentication filter와 route 설정을 유지하고 WebTestClient 기반 통합 계약을 추가한다.
- Gateway는 JWT 유효성만 검증하며 삭제 완료 후 계정 상태 차단은 main과 notification tombstone이 담당한다.
- 공개 경로 목록에 계정 삭제나 push token 삭제 경로가 들어가지 않도록 단위 테스트로 고정한다.

## 테스트 계획

- main/notification mock downstream으로 method, path, Authorization, 내부 사용자 헤더를 검증한다.
- 인증 실패 요청이 downstream에 도달하지 않는지 검증한다.
- `./gradlew test`를 실행한다.

## 위험과 확인 사항

- access token은 자체 만료 전까지 암호학적으로 유효할 수 있으므로 downstream은 삭제 사용자 상태를 별도로 차단해야 한다.
- 서비스 직접 접근은 운영 네트워크에서 차단되어야 하며 해당 인프라 검증은 별도 범위다.

## 구현 상태

- 기존 global authentication filter가 삭제 경로에도 동일하게 적용됨을 확인했다.
- main/notification mock downstream 기반 DELETE method·path·Authorization·내부 사용자 header 계약 테스트를 추가했다.
- 토큰 없음, refresh token, 만료·위조 access token이 downstream에 도달하지 않는 테스트를 추가했다.
- `./gradlew test` 전체 검증을 통과했다.
