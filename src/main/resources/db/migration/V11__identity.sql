-- Built-in accounts and sessions (decisions D65 to D68, D73, D74; docs/implementation/IDENTITY_PROPOSAL.md).
-- A user is a person who signs in; a session represents the user and never a company. Companies (tenants) and
-- memberships come in a later phase, so nothing here references a tenant except an audit row that names one.

create table app_user (
    id                  uuid         primary key,
    email               varchar(254) not null,
    display_name        varchar(200) not null,
    password_hash       varchar(200) not null,
    status              varchar(24)  not null check (status in ('PENDING_VERIFICATION', 'ACTIVE', 'DISABLED')),
    email_verified_at   timestamptz,
    platform_admin      boolean      not null default false,
    password_changed_at timestamptz  not null,
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    -- an ACTIVE account is always a verified one
    constraint ck_app_user_active_verified check (status <> 'ACTIVE' or email_verified_at is not null)
);
-- emails are stored normalized (trimmed, lower case); the index makes that a rule of the database too
create unique index ux_app_user_email on app_user (lower(email));

-- One-time tokens for email verification and password reset (later also invitations). Only a SHA-256 hash of the
-- random token is stored, so a copy of the table cannot be used to verify or reset anything.
create table auth_token (
    id          uuid        primary key,
    user_id     uuid        not null references app_user (id) on delete cascade,
    purpose     varchar(32) not null,
    token_hash  char(64)    not null,
    expires_at  timestamptz not null,
    used_at     timestamptz,
    created_at  timestamptz not null
);
create unique index ux_auth_token_hash on auth_token (token_hash);
create index ix_auth_token_user on auth_token (user_id, purpose);

-- Append-only record of security events. It never holds a password, a token or a cookie. An email that belongs to no
-- account is stored only as a keyed hash (subject_hash).
create table security_audit_event (
    id           bigserial    primary key,
    occurred_at  timestamptz  not null,
    event_type   varchar(48)  not null,
    outcome      varchar(16)  not null,
    user_id      uuid,
    tenant_id    varchar(64),
    subject_hash char(64),
    client_ip    varchar(64),
    user_agent   varchar(200),
    detail       jsonb
);
create index ix_security_audit_time on security_audit_event (occurred_at);
create index ix_security_audit_user on security_audit_event (user_id, occurred_at);
create index ix_security_audit_tenant on security_audit_event (tenant_id, occurred_at);
create trigger trg_security_audit_event_immutable before update or delete on security_audit_event
    for each row execute function prevent_history_change();

-- Spring Session (JDBC): the server-side session store. The browser holds only an opaque, HttpOnly session id.
create table spring_session (
    primary_id            char(36)     not null,
    session_id            char(36)     not null,
    creation_time         bigint       not null,
    last_access_time      bigint       not null,
    max_inactive_interval int          not null,
    expiry_time           bigint       not null,
    principal_name        varchar(100),
    constraint spring_session_pk primary key (primary_id)
);
create unique index spring_session_ix1 on spring_session (session_id);
create index spring_session_ix2 on spring_session (expiry_time);
create index spring_session_ix3 on spring_session (principal_name);

create table spring_session_attributes (
    session_primary_id char(36)     not null,
    attribute_name     varchar(200) not null,
    attribute_bytes    bytea        not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    constraint spring_session_attributes_fk foreign key (session_primary_id) references spring_session (primary_id) on delete cascade
);
