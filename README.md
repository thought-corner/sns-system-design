# SNS

시스템 설계 결정과 근거는 다음 문서에서 관리한다.

- [`docs/서버 세션과 Redis 중앙 저장소 설계.md`](docs/서버%20세션과%20Redis%20중앙%20저장소%20설계.md)
- [`docs/팔로우 시스템 설계.md`](docs/팔로우%20시스템%20설계.md)
- [`docs/게시글 설계.md`](docs/게시글%20설계.md)

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

Redis 접속 정보는 `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`로 변경한다. 세션 정책은
`RedisSessionConfig`에서 관리하며 유효 시간은 30분, Redis 키 네임스페이스는 `sns:session`이다. 정책 변경은 코드 리뷰와 재배포를 거친다.

한 사용자에게는 동시에 하나의 로그인 세션만 허용한다. 이미 로그인된 사용자가 다른 브라우저나 기기에서 다시 로그인하면 기존 세션은 유지되고 새 로그인은
`409 SESSION_LIMIT_EXCEEDED`로 거부된다. 동시에 도착한 로그인 요청도 Redis 원자 잠금으로 직렬화한다. 기존 세션에서 로그아웃한 뒤에는 다시 로그인할 수 있다.

미디어 (이미지) 바이트는 **S3** 에 저장한다. 버킷·리전은 `MEDIA_S3_BUCKET`(기본 `sns-media`), `AWS_REGION`(기본 `ap-northeast-2`)으로 정한다 (실 S3
전용 — 호환 엔드포인트 설정은 없다). **자격증명은 설정 파일에 두지 않는다** — AWS SDK 기본 자격증명 체인 (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` env,
`~/.aws/credentials`, 인스턴스 역할)만 쓴다. 버킷은 비공개여야 하며 (presigned URL 로만 읽고 쓴다), 앱의 IAM 권한은 객체에 대한 `s3:PutObject`·
`s3:GetObject`·`s3:DeleteObject` 와 **버킷 ARN 에 대한 `s3:ListBucket`** 이 필요하다 —
`s3:ListBucket` 이 없으면 S3 가 없는 객체의 HEAD/GET 을 404 가 아니라 403 으로 돌려줘 서버가 "아직 안 올라옴"(`409 MEDIA_NOT_UPLOADED`)을 판정하지 못하고 500
이 난다. Presigned URL 유효 시간 (업로드 10분·읽기 15분)과 파일 크기 상한 (10MB)은 `application.yaml` 의 `sns.media.*` 다.

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

## 팔로우 API

팔로우 API는 인증된 세션이 필요하며 `PUT`과 `DELETE` 요청에는 CSRF 토큰을 함께 전송해야 한다.

| 메서드   | 경로                               | 설명                       |
|----------|------------------------------------|----------------------------|
| `PUT`    | `/api/users/{followingId}/follow`  | 사용자를 멱등하게 팔로우   |
| `DELETE` | `/api/users/{followingId}/follow`  | 사용자를 멱등하게 언팔로우 |
| `GET`    | `/api/users/{userId}/follow-stats` | 팔로워·팔로잉 수 조회      |

언팔로우는 관계 행을 물리 삭제하지 않고 삭제 시각을 기록한다. 집계에는 현재 활성 관계만 반영되며, 다시 팔로우하면 새 이력이 생성된다. 팔로워·팔로잉 수는 사용자별 팔로우 통계 행 (`follow_counts`)
에서 읽으며, 관계가 실제로 바뀐 같은 트랜잭션에서만 갱신되므로 활성 관계 수와 같다. 조회 비용은 팔로워 수와 무관하다 (실측: 팔로워 100만 사용자 기준 `COUNT` 집계 대비 지연 −99.8%, 처리량
+578% — `docs/팔로우 시스템 설계.md` "2. 팔로워 수와 팔로잉 수 — 개정").

팔로우·언팔로우는 성공 시 본문 없이 `204`로 응답하며, 이미 같은 상태여도 `204`다. 자기 자신을 대상으로 하면 `400 SELF_FOLLOW_NOT_ALLOWED`, 대상 사용자가 없으면
`404 USER_NOT_FOUND`로 응답한다. `follow-stats` 응답은 `userId`, `followerCount`, `followingCount`를 담으며, 대상 사용자가 없으면 마찬가지로
`404 USER_NOT_FOUND`다.

## 게시글 API

게시글 API는 모두 인증된 세션이 필요하며 `POST`·`PUT`·`DELETE` 요청에는 CSRF 토큰을 함께 전송해야 한다.

| 메서드   | 경로                          | 설명                          |
|----------|-------------------------------|-------------------------------|
| `POST`   | `/api/posts`                  | 게시글 작성                   |
| `GET`    | `/api/posts/{postId}`         | 게시글 조회(통계 포함)        |
| `DELETE` | `/api/posts/{postId}`         | 게시글 삭제(작성자만, 멱등)   |
| `POST`   | `/api/posts/{postId}/replies` | 답글 작성                     |
| `POST`   | `/api/posts/{postId}/quotes`  | 인용 작성                     |
| `PUT`    | `/api/posts/{postId}/repost`  | 멱등하게 리포스트             |
| `DELETE` | `/api/posts/{postId}/repost`  | 멱등하게 리포스트 취소        |
| `PUT`    | `/api/posts/{postId}/like`    | 멱등하게 좋아요               |
| `DELETE` | `/api/posts/{postId}/like`    | 멱등하게 좋아요 취소          |
| `POST`   | `/api/posts/{postId}/views`   | 조회 기록(사용자당 1회, 멱등) |

- 작성·답글·인용 요청 본문은 `{content}` 이며 서버가 앞뒤 공백 (전각 공백·NBSP 포함)을 제거한 뒤 1~500자를 검증한다 — 공백만이거나 없거나 제거 후 500자를 넘거나 NUL 문자
  (U+0000)나 짝 없는 서로게이트를 포함하면 `400 INVALID_REQUEST`(`fieldErrors.content`). 자릿수는 **코드포인트 기준**(DB `VARCHAR(500)` 과 같은 단위 —
  단일 코드포인트 이모지는 1자, ZWJ 조합·피부색 수식 이모지는 코드포인트 수만큼)이며 정상 이모지는 허용된다. 저장·응답의 `content` 는 공백 제거본이다. 성공은 `201` 로 게시글 응답을 돌려준다.
- 작성·답글·인용 요청 본문에 `mediaIds`(선택, 최대 4개, 중복·`null` 원소 불가 — 어기면 `400 INVALID_REQUEST` + `fieldErrors.mediaIds`)를 넣으면 그
  순서대로 미디어가 첨부된다. 본인이 완료 (`READY`)한 미첨부 미디어만 붙는다 — 없거나 남의 것이면 `404 MEDIA_NOT_FOUND`, 완료 전이면 `409 MEDIA_NOT_READY`, 이미 다른
  게시글에 붙었으면
  `409 MEDIA_ALREADY_ATTACHED` 이고 그 경우 게시글도 만들어지지 않는다. 본문은 미디어가 있어도 필수다 (미디어만 있는 게시글은 아직 없다).
- 게시글 응답은 `id`, `authorId`, `content`, `parentPostId`(답글이면 원본), `quotedPostId`(인용이면 원본), `repostOfId`(리포스트 행이면 원본),
  `createdAt`, `counts{replyCount, quoteCount, repostCount, likeCount, viewCount}`,
  `media[]{id, contentType, width, height, url}` 를 담는다. `media` 는 첨부 순서이고
  `url` 은 유효 시간 15분의 presigned GET 이라 만료되면 게시글을 다시 조회해야 한다. 리포스트 행의 `media` 는 항상 빈 목록이다 (원본을 조회한다).
- 답글은 `parentPostId`, 인용은 `quotedPostId`, 리포스트는 `repostOfId` 가 있는 게시글이다 (통합형 — 타임라인이 한 테이블을 읽는다). 리포스트는 본문이 빈 게시글 행이며
  `GET /api/posts/{postId}` 로 조회하면 `content` 가 `""`, `repostOfId` 가 원본이다. 리포스트 행을 대상으로 한 답글·인용·리포스트·좋아요·조회 기록은 **원본에
  귀속**된다 (리포스트 행 자신의 `counts` 는 항상 0).
- 삭제는 소프트 삭제이며 작성자만 할 수 있다. 다른 사용자가 지우려 하면 (이미 삭제된 글이어도) `403 NOT_POST_AUTHOR`, 없는 게시글이면 `404 POST_NOT_FOUND`, 이미 삭제한
  게시글을 작성자가 다시 지우면 `204`. 답글·인용·리포스트 행을 삭제하면 원본의 답글 수·인용 수·리포스트 수가 함께 줄어든다 (리포스트 행 삭제는 `DELETE …/repost` 와 같은 결과). 원본을
  삭제하면 그 원본의 활성 리포스트 행도 함께 삭제된다 (이후 리포스트 행 조회는 `404`).
- 삭제됐거나 없는 게시글은 조회·답글·인용·리포스트·좋아요·조회 기록 모두 `404 POST_NOT_FOUND`.
- 리포스트·좋아요는 성공 시 본문 없이 `204` 이고 이미 같은 상태여도 `204` 다. 리포스트 취소 뒤 다시 리포스트하면 새 게시글 행이 생긴다 (이력 보존). 조회 기록은 같은 사용자가 여러 번 보내도 한
  번만 세며 항상 `204`.
- `counts` 는 게시글별 통계 행 (`post_counts`)에서 읽으며 관계가 실제로 바뀐 같은 트랜잭션에서만 갱신되므로 활성 답글·인용·리포스트·좋아요 수와 조회 사용자 수에 일치한다. 목록·피드·타임라인
  API 는 제공하지 않는다.

## 미디어 API

미디어 API 는 인증된 세션과 CSRF 토큰이 필요하다. 업로드는 **클라이언트가 S3 에 직접 PUT** 하고, 서버는 URL 발급과 완료 검증만 한다.

| 메서드 | 경로                            | 설명                                     |
|--------|---------------------------------|------------------------------------------|
| `POST` | `/api/media/uploads`            | 업로드 URL 발급(presigned PUT)           |
| `POST` | `/api/media/{mediaId}/complete` | 업로드 완료 — 객체 검증 후 `READY`(멱등) |

1. `POST /api/media/uploads` 본문 `{contentType, sizeBytes}` → `201 {mediaId, uploadUrl, expiresAt}`. `contentType` 은
   `image/jpeg`·`image/png`·`image/gif`·`image/webp`
   만 (`400 MEDIA_TYPE_NOT_ALLOWED`), `sizeBytes` 는 1 이상 10MB 이하 (`400 MEDIA_TOO_LARGE`; 없거나 0 이하면
   `400 INVALID_REQUEST`).
2. 클라이언트가 `uploadUrl` 로 `PUT` 한다 — 요청 헤더 `Content-Type`·`Content-Length` 가 발급 때 선언한 값과 **정확히 같아야** S3 가 받는다 (서명에 묶여 있다).
   `expiresAt`
   이 지나면 URL 은 무효이고 다시 발급받는다.
3. `POST /api/media/{mediaId}/complete` → `200 {id, status, contentType, sizeBytes, width, height}`. 서버가 S3 에서 객체를 확인해
   선언한 타입·크기와 같고 실제로 그 형식의 이미지인지 (매직 넘버·헤더) 검증한 뒤 `READY` 로 바꾼다. 객체가 아직 없으면 `409 MEDIA_NOT_UPLOADED`(다시 올린 뒤 재호출), 선언과
   다르거나 이미지가 아니면 `400 MEDIA_INVALID`
   이고 객체와 미디어는 폐기된다 (새로 발급). 남의 미디어나 없는 id 는 `404 MEDIA_NOT_FOUND`. 이미 `READY` 면 같은 응답으로 `200`. `width`/`height` 는
   jpeg·png·gif 만 채우고 webp 는 `null` 이다.
4. 게시글 작성·답글·인용의 `mediaIds` 에 넣어 첨부한다 (게시글 API 참고). 한 미디어는 한 게시글에만 붙고 되돌리지 않는다.

`READY` 가 됐지만 어느 게시글에도 붙지 않은 미디어와, 완료되지 않은 `PENDING` 미디어의 정리 (배치·S3 수명 주기)는 아직 없다. 미디어 삭제 API 도 없다 — 게시글을 삭제해도 S3 객체는
남는다.

## 테스트

```shell
./gradlew test
```

테스트 DB 는 운영과 같은 PostgreSQL 17 이며 Testcontainers 가 Docker 로 자동 기동한다 (H2 를 쓰지 않는다). Docker 데몬이 없으면 DB·Redis 가 필요한 테스트는 실패가
아니라 **건너뛴다** — 서비스 단위 테스트만 실행되므로 결과의 `skipped` 수를 확인해야 한다.
