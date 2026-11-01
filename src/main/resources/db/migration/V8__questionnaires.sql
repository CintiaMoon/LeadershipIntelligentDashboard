-- Questionnaire library (decisions D47, D50; specv3.md Section 4.14). A questionnaire is authored in the builder, stored here as a
-- document, and published as an ACTIVE TEMPLATE layer of the tenant's scoring configuration. The questionnaire id is also the
-- template id (assessment_template.id is one namespace for all tenants, so ids are generated, never chosen by the caller).
-- A published version is immutable; changing it publishes a new version and marks the previous one SUPERSEDED.
create table questionnaire (
    id          varchar(64)  primary key,
    tenant_id   varchar(64)  not null references tenant (id),
    name        varchar(200) not null,
    created_by  varchar(200) not null,
    created_at  timestamptz  not null default now(),
    archived_at timestamptz
);
create index ix_questionnaire_tenant on questionnaire (tenant_id, created_at);

create table questionnaire_version (
    questionnaire_id         varchar(64)  not null references questionnaire (id),
    version                  integer      not null check (version >= 1),
    tenant_id                varchar(64)  not null references tenant (id),
    status                   varchar(16)  not null check (status in ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
    definition               jsonb        not null,
    configuration_version_id uuid         references scoring_configuration_version (id),
    created_by               varchar(200) not null,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now(),
    published_by             varchar(200),
    published_at             timestamptz,
    primary key (questionnaire_id, version),
    check ((status = 'DRAFT') = (published_at is null))
);
create index ix_questionnaire_version_tenant on questionnaire_version (tenant_id, questionnaire_id);
-- At most one open draft per questionnaire.
create unique index ux_questionnaire_one_draft on questionnaire_version (questionnaire_id) where status = 'DRAFT';

-- Defense in depth for "a published version is immutable": only a DRAFT may change its content, and only the status
-- may move on afterwards (PUBLISHED to SUPERSEDED).
create function prevent_published_questionnaire_change() returns trigger as $$
begin
    if tg_op = 'DELETE' then
        if old.status <> 'DRAFT' then
            raise exception 'questionnaire % version % is % and cannot be deleted', old.questionnaire_id, old.version, old.status;
        end if;
        return old;
    end if;
    if old.status <> 'DRAFT' and (new.definition is distinct from old.definition
            or new.version is distinct from old.version
            or new.questionnaire_id is distinct from old.questionnaire_id
            or new.configuration_version_id is distinct from old.configuration_version_id
            or new.published_at is distinct from old.published_at) then
        raise exception 'questionnaire % version % is % and its content is immutable', old.questionnaire_id, old.version, old.status;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger trg_questionnaire_version_immutable
    before update or delete on questionnaire_version
    for each row execute function prevent_published_questionnaire_change();
