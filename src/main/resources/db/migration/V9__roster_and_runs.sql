-- Roster, assessment runs and the respondent flow (decision D48; specv3.md Section 4.14).
--
-- A team belongs to an organization. A person is a roster entry: the respondent is matched to it by the verified e-mail of the
-- token. An assessment run pins one published questionnaire version and its configuration version; starting it snapshots one
-- participant per active person of the chosen teams.
--
-- Anonymity: run_response holds the answers of a submitted respondent with the team and the question only. It has no reference to
-- a participant or a person, no timestamp and no sequence, and its id is random, so the table cannot say who answered. The
-- participant row says only whether a person has finished. A draft (answers so far) is linked to its participant until submit or
-- close and is deleted then.
create table team (
    id              varchar(64)  primary key,
    tenant_id       varchar(64)  not null references tenant (id),
    organization_id varchar(64)  not null references organization (id),
    name            varchar(200) not null,
    created_at      timestamptz  not null default now()
);
create index ix_team_tenant_org on team (tenant_id, organization_id);
create unique index ux_team_name on team (tenant_id, organization_id, lower(name));

create table person (
    id         uuid         primary key,
    tenant_id  varchar(64)  not null references tenant (id),
    team_id    varchar(64)  not null references team (id),
    name       varchar(200) not null,
    email      varchar(320) not null check (email = lower(email)),
    active     boolean      not null default true,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);
create index ix_person_team on person (tenant_id, team_id);
-- one person per e-mail in a tenant, so a token resolves to at most one roster entry
create unique index ux_person_email on person (tenant_id, email);

create table assessment_run (
    id                       uuid         primary key,
    tenant_id                varchar(64)  not null references tenant (id),
    organization_id          varchar(64)  not null references organization (id),
    name                     varchar(200) not null,
    questionnaire_id         varchar(64)  not null references questionnaire (id),
    questionnaire_version    integer      not null,
    configuration_version_id uuid         not null references scoring_configuration_version (id),
    status                   varchar(8)   not null check (status in ('OPEN', 'CLOSED')),
    due_date                 date,
    created_by               varchar(200) not null,
    created_at               timestamptz  not null default now(),
    closed_by                varchar(200),
    closed_at                timestamptz,
    assessment_id            varchar(64)  references assessment (id),
    scoring_run_id           uuid         references scoring_run (id),
    foreign key (questionnaire_id, questionnaire_version) references questionnaire_version (questionnaire_id, version),
    check ((status = 'CLOSED') = (closed_at is not null))
);
create index ix_assessment_run_tenant on assessment_run (tenant_id, organization_id, created_at);

create table assessment_run_team (
    run_id  uuid        not null references assessment_run (id),
    team_id varchar(64) not null references team (id),
    primary key (run_id, team_id)
);

create table run_participant (
    id        uuid        primary key,
    run_id    uuid        not null references assessment_run (id),
    tenant_id varchar(64) not null references tenant (id),
    person_id uuid        not null references person (id),
    team_id   varchar(64) not null references team (id),
    status    varchar(16) not null check (status in ('NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED')),
    unique (run_id, person_id)
);
create index ix_run_participant_person on run_participant (tenant_id, person_id);

create table run_draft (
    participant_id uuid        not null references run_participant (id),
    question_id    varchar(64) not null,
    answer         jsonb       not null,
    updated_at     timestamptz not null default now(),
    primary key (participant_id, question_id)
);

create table run_response (
    id          uuid        primary key,
    run_id      uuid        not null references assessment_run (id),
    tenant_id   varchar(64) not null references tenant (id),
    team_id     varchar(64) not null references team (id),
    question_id varchar(64) not null,
    answer      jsonb       not null
);
create index ix_run_response_run on run_response (run_id, team_id);
