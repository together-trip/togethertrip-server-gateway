# 작업 내용

- Gateway WebFlux `GlobalFilter` 기반 요청 로그를 추가했습니다.
- `X-Request-Id` 생성/유지 및 downstream/response 반영을 추가했습니다.
- routeId, method, path, status, elapsedMs 로그를 추가했습니다.
- spoofing 가능 `X-User-Id` header 제거를 추가했습니다.
- token, cookie, Authorization, 전화번호, 이메일 마스킹을 추가했습니다.
- 로깅 관련 단위 테스트를 추가했습니다.

# 변경 유형

- [x] 기능 추가
- [ ] 버그 수정
- [ ] 리팩토링
- [ ] 설정 변경
- [x] 문서 수정
- [x] 테스트 추가/수정

# 확인 사항

- [x] 로컬에서 빌드가 성공했습니다.
- [x] 테스트가 성공했습니다.
- [x] 불필요한 로그/주석을 제거했습니다.
- [x] 민감 정보가 포함되지 않았습니다.
- [ ] API 변경 사항이 있다면 문서 또는 요청 예시를 함께 수정했습니다.

# 테스트 방법

```bash
./gradlew test
```

Closes #3
