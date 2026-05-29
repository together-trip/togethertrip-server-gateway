# Quality Gates

## 기본 검증 명령

```bash
./gradlew test
```

## 보안 체크리스트

- 인증 전파와 route 보호
- CORS 설정
- header spoofing 방지
- rate limit
- 로그 민감정보 마스킹

## 완료 기준

- 관련 테스트 또는 분석 명령을 실행했다.
- 실행하지 못한 검증은 이유를 남겼다.
- 사용자 흐름, 권한, 입력 제한이 기획과 충돌하지 않는다.
- 문서 변경이 필요한 경우 함께 갱신했다.
