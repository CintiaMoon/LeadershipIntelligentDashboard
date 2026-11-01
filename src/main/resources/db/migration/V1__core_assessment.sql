-- Core tenant-owned records (specv3.md Sections 26.1, 25). Every tenant-owned table carries tenant_id.
create table tenant (
    id          varchar(64)  primary key,
    name        varchar(200) not null,
    created_at  timestamptz  not null default now()
);

create table organization (
    id          varchar(64)  primary key,
    tenant_id   varchar(64)  not null references tenant (id),
    name        varchar(200) not null,
    created_at  timestamptz  not null default now()
);
create index ix_organization_tenant on organization (tenant_id);

create table assessment_template (
    id          varchar(64)  primary key,
    tenant_id   varchar(64)  not null references tenant (id),
    name        varchar(200) not null,
    created_at  timestamptz  not null default now()
);
create index ix_assessment_template_tenant on assessment_template (tenant_id);

create table assessment_template_version (
    template_id varchar(64) not null references assessment_template (id),
    version     integer     not null check (version >= 1),
    tenant_id   varchar(64) not null references tenant (id),
    created_at  timestamptz not null default now(),
    primary key (template_id, version)
);

create table assessment (
    id               varchar(64) primary key,
    tenant_id        varchar(64) not null references tenant (id),
    organization_id  varchar(64) not null references organization (id),
    template_id      varchar(64) not null,
    template_version integer     not null,
    status           varchar(32) not null,
    completed_at     timestamptz,
    created_at       timestamptz not null default now(),
    foreign key (template_id, template_version) references assessment_template_version (template_id, version)
);
create index ix_assessment_tenant_org on assessment (tenant_id, organization_id);

create table assessment_snapshot (
    id            varchar(64) primary key,
    tenant_id     varchar(64) not null references tenant (id),
    assessment_id varchar(64) not null references assessment (id),
    revision      integer     not null check (revision >= 1),
    input_hash    varchar(80) not null,
    created_at    timestamptz not null default now(),
    unique (assessment_id, revision)
);
create index ix_assessment_snapshot_tenant on assessment_snapshot (tenant_id, assessment_id);

create table assessment_answer (
    snapshot_id varchar(64) not null references assessment_snapshot (id),
    tenant_id   varchar(64) not null references tenant (id),
    question_id varchar(64) not null,
    answer      jsonb       not null,
    primary key (snapshot_id, question_id)
);
