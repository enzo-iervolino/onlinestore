-- PostgreSQL: additive migration for installations using ddl-auto=validate/none.
-- With ddl-auto=update these tables are created by Hibernate.
-- Take a database backup first. Does not alter/delete legacy orders or products.
BEGIN;
CREATE TABLE IF NOT EXISTS web_checkout_attempts (
    id varchar(36) PRIMARY KEY,
    owner_token varchar(64) NOT NULL,
    active_owner varchar(64) UNIQUE,
    provider varchar(20) NOT NULL,
    provider_reference varchar(255) UNIQUE,
    redirect_url varchar(2048),
    state varchar(24) NOT NULL,
    payload text NOT NULL,
    order_id bigint,
    created_at timestamp(6) NOT NULL,
    expires_at timestamp(6) NOT NULL,
    last_checked_at timestamp(6),
    gateway_expires_at bigint NOT NULL,
    reserved boolean NOT NULL DEFAULT false,
    recovery_note varchar(250)
);
CREATE INDEX IF NOT EXISTS idx_web_attempt_state ON web_checkout_attempts(state, created_at);
CREATE TABLE IF NOT EXISTS web_order_mail_jobs (
    order_id bigint PRIMARY KEY,
    attempts integer NOT NULL DEFAULT 0,
    delivered boolean NOT NULL DEFAULT false,
    next_attempt timestamp(6) NOT NULL,
    last_error varchar(200)
);
COMMIT;
