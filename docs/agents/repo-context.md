# Repo Context

## 리포 역할

`gateway`는 TogetherTrip의 API Gateway 서버다.

## 책임 범위

- app에서 들어오는 API/WebSocket 요청 라우팅
- 인증 전파, 공통 CORS, 공통 필터
- 서비스 경계 보호와 route 정책
- 공통 로그, 요청 식별자, rate limit

## 아키텍처 원칙

- Gateway는 도메인 로직을 소유하지 않는다.
- 라우팅, 필터, 인증 전파, 장애 격리 책임에 집중한다.
- downstream 서비스의 내부 모델에 직접 의존하지 않는다.
- 민감정보가 로그나 header로 누출되지 않도록 한다.

## 통신 규칙

- `app -> gateway -> main/chat/notification` 흐름을 기본으로 한다.
- 외부 클라이언트는 서비스에 직접 접근하지 않는 것을 원칙으로 한다.
- 다른 서비스의 DB에 접근하지 않는다.
- 인증 정보는 위조 가능 header와 구분되는 방식으로 전파한다.

## 핵심 도메인 주의점

- route 보호 정책이 서비스별 권한 전제를 약화하지 않아야 한다.
- CORS는 앱 클라이언트와 운영 환경 기준으로 제한한다.
- 장애 발생 시 민감한 downstream 오류를 그대로 노출하지 않는다.
