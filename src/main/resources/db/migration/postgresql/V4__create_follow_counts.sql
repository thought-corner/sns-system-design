-- 팔로우 통계 행. 활성 관계(deleted_at IS NULL)의 COUNT 와 항상 같아야 한다(설계 문서 "2. 팔로워 수와 팔로잉 수 — 개정").
-- 관계 삽입·소프트 삭제가 실제로 일어난 트랜잭션에서만 증감하며, 행은 첫 증감 시 만들어진다(없으면 0).
-- postgresql/ 에 두는 이유: 백필이 postgresql/V3 의 deleted_at 에 의존한다(common/ 은 벤더 중립 DDL 만).
CREATE TABLE follow_counts
(
    user_id         BIGINT NOT NULL,
    follower_count  BIGINT NOT NULL DEFAULT 0,
    following_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_follow_counts PRIMARY KEY (user_id),
    CONSTRAINT fk_follow_counts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_follow_counts_non_negative CHECK (follower_count >= 0 AND following_count >= 0)
);

-- 기존 활성 관계로 백필. 관계가 하나도 없는 사용자는 행을 만들지 않는다.
INSERT INTO follow_counts (user_id, follower_count, following_count)
SELECT user_id, SUM(follower_delta), SUM(following_delta)
FROM (SELECT following_id AS user_id, 1 AS follower_delta, 0 AS following_delta
      FROM follows
      WHERE deleted_at IS NULL
      UNION ALL
      SELECT follower_id, 0, 1
      FROM follows
      WHERE deleted_at IS NULL) deltas
GROUP BY user_id;
