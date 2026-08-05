# Verification Report: 이슈 #23 출시 보안 강화

## 검증 대상

- 운영 CORS allowlist와 HTTPS origin
- trusted proxy CIDR, forwarded header 정규화, XFF 위조 방어
- HTTPS 강제와 HSTS
- 로그인·refresh·신고 rate limit
- 공통 오류 응답과 downstream 5xx 내부정보 격리
- OAuth code/token·Authorization·사용자 식별자 로그 비노출
- 출시 로그인·신고·차단·계정 삭제·push token 삭제 route/auth 계약

## 실행한 명령

```bash
./gradlew test
git diff --check
```

## 결과

- 전체 74개 테스트 성공(실패 0, 오류 0)
- prod profile 환경변수의 CORS origin·trusted proxy CIDR list binding 성공
- 허용/거부 CORS preflight, HTTP/HTTPS, trusted/untrusted forwarded header, IPv4/IPv6 CIDR 성공
- XFF prefix 위조가 client IP rate limit key를 변경하지 못함을 확인
- 로그인·신고 한도 초과 `429`, 다른 key 독립성, 일반 API 비대상 확인
- downstream 4xx 유지, 본문 유무와 예외 방식의 5xx 안전 응답 확인
- 출시 API 공개/보호 정책과 JWT 사용자 header 전달 확인

## 실패 또는 미검증 항목

- 실제 운영 TLS 인증서·DNS·HTTP redirect
- ingress의 외부 forwarded header 제거·덮어쓰기 설정
- 여러 Gateway replica와 ingress/WAF를 합친 전체 rate limit
- 배포된 main/notification을 연결한 운영 HTTPS E2E

## 다음 조치

1. 운영 배포 전에 README의 환경변수와 trusted CIDR을 secret/config에 설정한다.
2. ingress 보안 그룹과 forwarded header 처리 정책을 운영 체크리스트대로 확인한다.
3. 실제 운영 HTTPS E2E 후 이슈 #23의 마지막 운영 검증 항목을 완료 처리한다.
