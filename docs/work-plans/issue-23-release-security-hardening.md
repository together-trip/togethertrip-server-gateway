# Work Plan: 이슈 #23 출시 보안 강화

## 작업

스토어 출시 API의 운영 진입점 정책을 Gateway 책임 안에서 고정한다. CORS allowlist, trusted proxy 이후의
HTTPS 강제, 로그인·신고 rate limit, downstream 5xx 오류 격리, 신규 API route/auth 계약을 코드·설정·테스트로
검증한다.

## 범위

- 운영 CORS는 명시한 origin, method, header만 허용하고 wildcard를 금지한다.
- 운영 환경은 `server.forward-headers-strategy=native`로 프록시가 정규화한 scheme을 사용한다.
- 운영 HTTPS 강제 필터는 request URI 또는 TLS 정보가 HTTPS일 때만 통과시킨다.
- 클라이언트가 직접 보낸 `X-Forwarded-Proto`만으로 HTTP 요청을 신뢰하지 않는다.
- 카카오·Apple 로그인과 refresh는 IP 단위, 신고 생성은 인증 사용자 단위로 rate limit한다.
- rate limit은 Gateway 인스턴스별 fail-closed 고정 윈도우로 적용하고, 다중 인스턴스 전체 한도는 ingress/WAF에서
  별도로 적용해야 함을 운영 문서에 명시한다.
- downstream 5xx 응답 본문과 예외 상세를 공통 안전 응답으로 교체한다.
- OAuth code/token, Authorization, 사용자 식별자가 요청 로그에 남지 않게 마스킹한다.
- 로그인·신고·차단·회원 탈퇴·push token 삭제의 공개/보호 route 계약을 테스트한다.

## 제외 범위

- main의 신고·차단·계정 삭제 도메인 로직
- TLS 인증서, DNS, load balancer/ingress 프로비저닝
- 분산 rate limit 저장소 구축
- 운영 인프라에서의 실제 인증서 체인·proxy header 정규화 E2E

## 아키텍처 판단

- 기존 `/api/**`, `/notification/**` route를 유지하고 전역 필터로 공통 경계 정책을 적용한다.
- rate limit은 경로와 인증 주체만 판단하며 요청 본문이나 downstream 모델을 해석하지 않는다.
- forwarded header는 필터에서 직접 파싱하지 않는다. Reactor Netty가 운영의 신뢰 프록시 경계에서 정규화한
  request URI/TLS 정보만 HTTPS 판단에 사용한다.
- downstream 4xx는 도메인 계약이므로 그대로 전달하고, 내부정보 위험이 큰 5xx 본문만 Gateway가 격리한다.

## TDD 시나리오

1. 허용 origin preflight만 CORS 응답을 받고 임의 origin은 거부된다.
2. 운영 HTTPS 강제 시 HTTP 요청과 위조 `X-Forwarded-Proto=https` 요청은 거부되고 HTTPS 요청은 통과한다.
3. 로그인 IP와 신고 사용자가 설정된 한도를 넘으면 429이며 다른 key는 영향받지 않는다.
4. downstream 5xx 상세 본문과 filter exception message는 앱 응답에 포함되지 않는다.
5. 신규 출시 API의 공개/보호 정책과 위조 사용자 header 제거가 고정된다.
6. 로그 path/query의 OAuth code/token과 사용자 ID가 마스킹된다.

## 검증

- `./gradlew test`
- 설정 계약 테스트로 운영 CORS, forwarded header, HTTPS 기본값 확인
- Security Reviewer 관점에서 spoofing, 인증 전파, rate limit key, 오류·로그 노출 검토
- Code Reviewer 관점에서 필터 순서, 메모리 정리, 응답 커밋 경쟁 검토

## 운영에서만 가능한 미검증

- TLS 인증서 체인, DNS, load balancer/ingress의 HTTP→HTTPS redirect
- ingress가 외부 forwarded header를 제거하고 새 값으로 덮어쓰는지 여부
- 다중 Gateway 인스턴스 전체 rate limit과 WAF 정책
- 실제 main/notification 배포본을 연결한 운영 HTTPS E2E
