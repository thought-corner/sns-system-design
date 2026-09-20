CREATE INDEX idx_posts_active_author_timeline
    ON posts (author_id, id DESC) WHERE deleted_at IS NULL AND parent_post_id IS NULL;
