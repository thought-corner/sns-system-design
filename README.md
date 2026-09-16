# SNS

## 로컬 PostgreSQL 실행

Docker Compose로 PostgreSQL을 실행한다.

```shell
docker compose up -d postgres
./gradlew bootRun
```

기본 접속 정보는 다음과 같다.

| 항목         | 기본값 |
|--------------|--------|
| 데이터베이스 | `sns`  |
| 사용자       | `sns`  |
| 비밀번호     | `sns`  |
| 포트         | `5432` |

컨테이너 설정은 `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`로 변경할 수 있다. 애플리케이션 접속 정보는 `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`로 변경한다.

예를 들어 컨테이너 비밀번호를 변경했다면 애플리케이션에도 같은 값을 전달한다.

```shell
POSTGRES_PASSWORD=local-secret docker compose up -d postgres
DB_PASSWORD=local-secret ./gradlew bootRun
```

상태 확인과 종료 명령은 다음과 같다.

```shell
docker compose ps
docker compose down
```

`docker compose down`은 데이터 볼륨을 보존한다. 로컬 데이터를 함께 삭제해야 할 때만 `docker compose down -v`를 사용한다.
