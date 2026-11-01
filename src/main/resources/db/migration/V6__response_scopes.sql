-- Response scopes (specv3.md Sections 4.10-4.13, decision D45).
--
-- Answers: a question may now have several answers in one snapshot. A MEMBER answer has no identity; its position
-- in the set is only an ordinal. team_id is '' when an answer names no team. Existing rows become
-- ORGANIZATION answers at ordinal 0, so every earlier snapshot reads back exactly as before.
alter table assessment_answer
    add column scope_level          varchar(16) not null default 'ORGANIZATION'
        check (scope_level in ('MEMBER', 'TEAM', 'ORGANIZATION')),
    add column team_id              varchar(64) not null default '',
    add column ordinal              integer     not null default 0 check (ordinal >= 0),
    add column declared_respondents integer check (declared_respondents is null or declared_respondents >= 1);
alter table assessment_answer drop constraint assessment_answer_pkey;
alter table assessment_answer add primary key (snapshot_id, question_id, scope_level, team_id, ordinal);

-- Runs: one ORGANIZATION run per scoring, plus one TEAM run for each team with answers. A team run points to the
-- organization run it was produced with. Earlier runs are ORGANIZATION runs. response_summaries records how each
-- question's answers were combined (method, respondent count and where it came from); it never holds an answer.
-- History stays immutable: these are new columns of rows that are only ever inserted.
alter table scoring_run
    add column scope_type         varchar(16) not null default 'ORGANIZATION'
        check (scope_type in ('ORGANIZATION', 'TEAM')),
    add column scope_id           varchar(64),
    add column parent_run_id      uuid references scoring_run (id),
    add column response_summaries jsonb,
    add check ((scope_type = 'TEAM') = (scope_id is not null)),
    add check ((scope_type = 'TEAM') = (parent_run_id is not null));
create index ix_scoring_run_parent on scoring_run (tenant_id, parent_run_id) where parent_run_id is not null;

-- The identity of a run now includes its scope, so an organization run and a team run of one snapshot never collide.
drop index ux_scoring_run_identity;
create unique index ux_scoring_run_identity
    on scoring_run (tenant_id, snapshot_id, scope_type, coalesce(scope_id, ''), input_hash, configuration_hash,
                    engine_version, algorithm_version)
    where status = 'COMPLETED' and source <> 'RECALCULATE';
