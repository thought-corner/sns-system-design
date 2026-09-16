# SNS

시스템 설계 결정과 근거는 [`docs/서버 세션과 Redis 중앙 저장소 설계.md`](docs/서버%20세션과%20Redis%20중앙%20저장소%20설계.md)에서 관리한다.

## 로컬 인프라 실행

Docker Compose로 PostgreSQL과 Redis를 실행한다. HTTP 세션은 Redis에 저장되므로 여러 애플리케이션 인스턴스에서 공유할 수 있다.

```shell
docker compose up -d postgres redis
./gradlew bootRun
```

기본 접속 정보는 다음과 같다.

| 항목            | 기본값 |
|-----------------|--------|
| 데이터베이스    | `sns`  |
| 사용자          | `sns`  |
| 비밀번호        | `sns`  |
| PostgreSQL 포트 | `5432` |
| Redis 포트      | `6379` |

컨테이너 설정은 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`로 변경할 수 있다. 애플리케이션 접속 정보는 `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`로 변경한다.

Redis 접속 정보는 `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`로 변경한다. 세션 정책은
`RedisSessionConfig`에서 관리하며 유효 시간은 30분, Redis 키 네임스페이스는 `sns:session`이다. 정책 변경은 코드 리뷰와 재배포를 거친다.

한 사용자에게는 동시에 하나의 로그인 세션만 허용한다. 이미 로그인된 사용자가 다른 브라우저나 기기에서 다시 로그인하면 기존 세션은 유지되고 새 로그인은
`409 SESSION_LIMIT_EXCEEDED`로 거부된다. 동시에 도착한 로그인 요청도 Redis 원자 잠금으로 직렬화한다. 기존 세션에서 로그아웃한 뒤에는 다시 로그인할 수 있다.

## 인증 API

| 메서드 | 경로                | 설명                        |
|--------|---------------------|-----------------------------|
| `GET`  | `/api/auth/csrf`    | CSRF 토큰 조회              |
| `POST` | `/api/auth/signup`  | 회원가입                    |
| `POST` | `/api/auth/login`   | 로그인 및 Redis 세션 생성   |
| `GET`  | `/api/auth/session` | 현재 로그인 사용자 조회     |
| `POST` | `/api/auth/logout`  | 로그아웃 및 Redis 세션 삭제 |

상태를 변경하는 `POST` 요청은 먼저 `/api/auth/csrf`를 호출해 세션 쿠키와 토큰을 받은 뒤, 응답의 `headerName`에 `token` 값을 넣어 전송해야 한다. 회원가입 요청은 `email`,
`password`, `nickname`을 받고, 로그인 요청은 `email`, `password`를 받는다.

예를 들어 컨테이너 비밀번호를 변경했다면 애플리케이션에도 같은 값을 전달한다.

```shell
POSTGRES_PASSWORD=local-secret docker compose up -d postgres redis
DB_PASSWORD=local-secret ./gradlew bootRun
```

상태 확인과 종료 명령은 다음과 같다.

```shell
docker compose ps
docker compose down
```

`docker compose down`은 데이터 볼륨을 보존한다. 로컬 데이터를 함께 삭제해야 할 때만 `docker compose down -v`를 사용한다.
