-- Audit of access to restricted explanations (specv3.md Section 33.4). It records who looked, never what was seen.
create table scoring_access_audit (
    id          bigserial    primary key,
    tenant_id   varchar(64)  not null references tenant (id),
    actor       varchar(200) not null,
    action      varchar(32)  not null,
    run_id      uuid         references scoring_run (id),
    detail      jsonb,
    occurred_at timestamptz  not null default now()
);
create index ix_scoring_access_audit_tenant on scoring_access_audit (tenant_id, occurred_at);
create trigger trg_scoring_access_audit_immutable before update or delete on scoring_access_audit
    for each row execute function prevent_history_change();
