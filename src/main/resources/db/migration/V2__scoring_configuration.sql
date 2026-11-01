-- Versioned scoring configuration (specv3.md Sections 23, 26.2). Platform defaults have no tenant.
create table scoring_configuration (
    id          uuid         primary key,
    tenant_id   varchar(64)  references tenant (id),
    scope_type  varchar(32)  not null check (scope_type in ('PLATFORM_DEFAULT', 'TEMPLATE', 'ORGANIZATION', 'TENANT')),
    scope_id    varchar(64),
    name        varchar(200) not null,
    created_at  timestamptz  not null default now(),
    check ((scope_type = 'PLATFORM_DEFAULT') = (tenant_id is null))
);
create unique index ux_scoring_configuration_scope
    on scoring_configuration (coalesce(tenant_id, ''), scope_type, coalesce(scope_id, ''));
create index ix_scoring_configuration_tenant on scoring_configuration (tenant_id);

create table scoring_configuration_version (
    id               uuid         primary key,
    configuration_id uuid         not null references scoring_configuration (id),
    tenant_id        varchar(64)  references tenant (id),
    version          integer      not null check (version >= 1),
    status           varchar(16)  not null check (status in ('DRAFT', 'VALIDATING', 'ACTIVE', 'PAUSED', 'ARCHIVED')),
    payload          jsonb        not null,
    config_hash      varchar(80)  not null,
    created_by       varchar(200) not null,
    created_at       timestamptz  not null default now(),
    approved_by      varchar(200),
    approved_at      timestamptz,
    effective_from   timestamptz,
    unique (configuration_id, version)
);
create index ix_scoring_configuration_version_tenant on scoring_configuration_version (tenant_id, status);

-- Defense in depth for "active configuration versions are immutable" (specv3.md Sections 23.5, 39 #17).
create function prevent_non_draft_payload_change() returns trigger as $$
begin
    if old.status <> 'DRAFT' and (new.payload is distinct from old.payload
            or new.config_hash is distinct from old.config_hash
            or new.version is distinct from old.version
            or new.configuration_id is distinct from old.configuration_id) then
        raise exception 'configuration version % is % and its content is immutable', old.id, old.status;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger trg_scoring_configuration_version_immutable
    before update on scoring_configuration_version
    for each row execute function prevent_non_draft_payload_change();

create table scoring_configuration_audit (
    id               bigserial    primary key,
    tenant_id        varchar(64),
    configuration_id uuid         not null,
    version_id       uuid,
    action           varchar(32)  not null,
    actor            varchar(200) not null,
    occurred_at      timestamptz  not null default now(),
    detail           jsonb
);
create index ix_scoring_configuration_audit_config on scoring_configuration_audit (configuration_id, occurred_at);
