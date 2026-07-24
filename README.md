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
