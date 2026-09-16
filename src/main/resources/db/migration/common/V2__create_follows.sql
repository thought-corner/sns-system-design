CREATE TABLE follows
(
    follower_id  BIGINT                   NOT NULL,
    following_id BIGINT                   NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_follows PRIMARY KEY (follower_id, following_id),
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> following_id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_follows_following FOREIGN KEY (following_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_follows_following_follower ON follows (following_id, follower_id);
