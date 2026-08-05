# Together-Trip Server Kotlin

## 로컬 백엔드 실행

gateway, main, notification, chat, PostgreSQL, Redis를 함께 빌드하고 실행한다.
로컬 Compose 설정에서는 AWS SQS 발행과 소비를 비활성화한다.
`main`과 `notification`은 각 저장소의 `src/main/resources/.env`를 컨테이너 환경변수로 읽는다.
gateway와 chat 저장소에는 별도의 로컬 `.env`가 없으므로 Compose의 로컬 기본값을 사용한다.
macOS에서 Docker daemon이 꺼져 있으면 Docker Desktop을 자동으로 실행하고 준비될 때까지 최대 2분간 기다린다.

```bash
./scripts/run_backend.sh
```

백그라운드 실행 등 `docker compose up` 옵션을 그대로 전달할 수 있다.

```bash
./scripts/run_backend.sh -d
```

종료할 때는 gateway 저장소에서 다음 명령을 실행한다.

```bash
docker compose down
```

## 운영 보안 설정

`prod` profile은 다음 환경변수가 없으면 시작하지 않는다.

| 환경변수 | 예시 | 설명 |
| --- | --- | --- |
| `MAIN_SERVICE_URL` | `http://main.internal:8081` | main 내부 주소 |
| `NOTIFICATION_SERVICE_URL` | `http://notification.internal:8082` | notification 내부 주소 |
| `CHAT_SERVICE_URL` | `http://chat.internal:8083` | chat 내부 주소 |
| `JWT_SECRET` | secret manager 주입값 | 32 byte 이상의 운영 JWT secret |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | `https://app.togethertrip.co.kr,https://admin.togethertrip.co.kr` | 쉼표로 구분한 CORS allowlist. `*`는 거부된다. |
| `GATEWAY_TRUSTED_PROXY_CIDRS` | `10.0.0.0/8,2001:db8:1234::/48` | Gateway에 직접 연결하는 load balancer/ingress CIDR |

다음 rate limit 값은 선택적으로 조정할 수 있다.

| 환경변수 | 기본값 | 설명 |
| --- | ---: | --- |
| `GATEWAY_LOGIN_RATE_LIMIT_PER_MINUTE` | `20` | 동일 client IP의 카카오·Apple 로그인/refresh 분당 한도 |
| `GATEWAY_REPORT_RATE_LIMIT_PER_MINUTE` | `5` | 동일 인증 사용자의 신고 생성 분당 한도 |
| `GATEWAY_RATE_LIMIT_MAX_TRACKED_KEYS` | `20000` | 인스턴스가 보관할 rate limit key 상한 |

### TLS와 trusted proxy 경계

- 외부 TLS 종료는 load balancer/ingress가 담당한다.
- load balancer/ingress는 외부 요청의 `Forwarded`, `X-Forwarded-*`를 제거한 뒤 자신이 계산한 값으로 덮어쓴다.
- Gateway 보안 그룹/네트워크 정책은 `GATEWAY_TRUSTED_PROXY_CIDRS`에서 오는 요청만 허용한다.
- Gateway는 trusted CIDR에서 직접 연결된 요청의 forwarded header만 적용한다. 나머지 요청에서는 해당 header를
  제거한다.
- trusted proxy가 정규화한 scheme 또는 Gateway 자체 TLS 정보가 HTTPS가 아니면 `426 HTTPS_REQUIRED`로 거부한다.
- `/health`, `/actuator/health`만 내부 HTTP probe를 허용한다. 외부 라우팅에서는 health endpoint를 노출하지 않는다.
- HTTPS 응답에는 `Strict-Transport-Security: max-age=31536000; includeSubDomains`를 추가한다.

### Rate limit 경계

애플리케이션 rate limit은 Gateway 인스턴스별 고정 윈도우다. 저장소 장애 없이 fail-closed로 동작하고 추적 key
수를 제한하지만, 여러 Gateway replica의 합산 한도는 제공하지 않는다. 운영 ingress/WAF에도 동일 endpoint의
전체 한도를 설정하고 부하·우회 테스트를 수행해야 한다.

### 운영 검증 체크리스트

1. 외부 HTTP 요청이 ingress에서 HTTPS로 redirect되거나 Gateway에서 `426`으로 거부되는지 확인한다.
2. 외부에서 위조한 `Forwarded`, `X-Forwarded-Proto`, `X-Forwarded-For`가 ingress에서 제거되는지 확인한다.
3. 허용하지 않은 CORS origin의 preflight가 `403`인지 확인한다.
4. 로그인·신고 한도 초과가 `429`와 `Retry-After`를 반환하는지 확인한다.
5. main/notification 장애 시 내부 host, exception, SQL/stack trace가 응답·로그에 없는지 확인한다.
6. 카카오·Apple 로그인, refresh, 신고·차단, 계정 삭제, push token 삭제를 실제 운영 HTTPS 경로로 E2E 검증한다.
