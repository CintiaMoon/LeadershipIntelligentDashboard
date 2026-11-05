-- API tokens for non-browser callers (decision D70; docs/implementation/IDENTITY_PROPOSAL.md). A token belongs to one company
-- and to the user who created it, carries one role (never OWNER) and optionally a list of organizations, and is an opaque random
-- value: only its SHA-256 is stored, so a copy of this table cannot be used to call the API. The secret is shown once, when the
-- token is created. A token is checked on every request, so revoking it, letting it expire, or the creator losing access takes
-- effect at once.

create table api_token (
    id            uuid         primary key,
    tenant_id     varchar(64)  not null references tenant (id),
    created_by    uuid         not null references app_user (id),
    name          varchar(100) not null,
    role          varchar(24)  not null check (role in ('ADMIN', 'ENGINEERING_LEADER', 'ANALYST', 'VIEWER')),
    token_hash    char(64)     not null,
    -- the first characters of the secret, so a person can tell tokens apart in a list; not enough to use the token
    token_prefix  varchar(16)  not null,
    expires_at    timestamptz,
    revoked_at    timestamptz,
    revoked_by    uuid         references app_user (id),
    last_used_at  timestamptz,
    created_at    timestamptz  not null
);
create unique index ux_api_token_hash on api_token (token_hash);
create index ix_api_token_tenant on api_token (tenant_id, created_at);
create index ix_api_token_creator on api_token (created_by);

-- The organizations a token is limited to; none means every organization of the company (only a creator who has that reach can issue one).
create table api_token_organization (
    token_id         uuid        not null references api_token (id) on delete cascade,
    organization_id  varchar(64) not null,
    primary key (token_id, organization_id)
);
