-- Immutable scoring runs and their findings (specv3.md Sections 26.3-26.7, 27). Every row carries tenant_id.
create table scoring_run (
    id                           uuid         primary key,
    seq                          bigint       generated always as identity unique,
    tenant_id                    varchar(64)  not null references tenant (id),
    organization_id              varchar(64)  not null references organization (id),
    assessment_id                varchar(64)  not null references assessment (id),
    snapshot_id                  varchar(64)  not null references assessment_snapshot (id),
    source                       varchar(16)  not null check (source in ('CALCULATE', 'SCORE', 'RECALCULATE')),
    configuration_selection      varchar(16)  not null check (configuration_selection in ('DEFAULT', 'EXPLICIT')),
    engine_version               varchar(40)  not null,
    algorithm_version            varchar(40)  not null,
    configuration_version        varchar(400) not null,
    configuration_hash           varchar(80)  not null,
    input_hash                   varchar(80)  not null,
    rounding_policy_version      varchar(40),
    confidence_algorithm_version varchar(40),
    status                       varchar(16)  not null check (status in ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    score_status                 varchar(24)  check (score_status in ('VALID', 'INSUFFICIENT_DATA', 'PARTIAL')),
    final_score                  numeric(5, 2) check (final_score between 0 and 100),
    weighted_adjusted_score      numeric(5, 2) check (weighted_adjusted_score between 0 and 100),
    overall_penalty              numeric(8, 2) check (overall_penalty >= 0),
    interpretation               varchar(64),
    layers                       jsonb        not null,
    trace                        jsonb,
    error_code                   varchar(80),
    error_message                varchar(500),
    started_at                   timestamptz  not null,
    completed_at                 timestamptz  not null,
    check ((status = 'COMPLETED') = (score_status is not null and trace is not null)),
    check ((status = 'FAILED') = (error_code is not null)),
    check ((score_status = 'VALID') = (final_score is not null) or score_status is null)
);
create index ix_scoring_run_assessment on scoring_run (tenant_id, assessment_id, completed_at desc);
-- specv3.md Section 27.4 identity; a recalculation always creates a new run, and a failed run does not block a retry.
create unique index ux_scoring_run_identity
    on scoring_run (tenant_id, snapshot_id, input_hash, configuration_hash, engine_version, algorithm_version)
    where status = 'COMPLETED' and source <> 'RECALCULATE';

-- An idempotency key is bound to the first request that used it (specv3.md Section 7.5), whether that request
-- created a run or reused one. A different request with the same key is rejected.
create table scoring_idempotency_key (
    tenant_id       varchar(64)  not null references tenant (id),
    idempotency_key varchar(200) not null,
    request_hash    varchar(80)  not null,
    run_id          uuid         not null references scoring_run (id),
    created_at      timestamptz  not null default now(),
    primary key (tenant_id, idempotency_key)
);

create table scoring_dimension_result (
    run_id                uuid          not null references scoring_run (id),
    tenant_id             varchar(64)   not null,
    dimension             varchar(32)   not null,
    status                varchar(24)   not null,
    raw_score             numeric(5, 2),
    dimension_penalty     numeric(8, 2) not null check (dimension_penalty >= 0),
    adjusted_score        numeric(5, 2),
    weight                numeric(14, 10) not null,
    weighted_contribution numeric(18, 10),
    label                 varchar(64),
    coverage              numeric(14, 10) not null,
    required_coverage     numeric(14, 10) not null,
    coverage_status       varchar(16)   not null,
    applicable_questions  integer       not null,
    answered_questions    integer       not null,
    valid_contributions   integer       not null,
    expected_weight       numeric(18, 10) not null,
    observed_weight       numeric(18, 10) not null,
    primary key (run_id, dimension),
    check (adjusted_score is null or raw_score is null or adjusted_score <= raw_score)
);

create table scoring_contribution (
    run_id            uuid         not null references scoring_run (id),
    tenant_id         varchar(64)  not null,
    dimension         varchar(32)  not null,
    question_id       varchar(64)  not null,
    status            varchar(24)  not null,
    value             numeric(14, 10),
    weight            numeric(18, 10) not null,
    normalized_weight numeric(14, 10),
    contribution      numeric(18, 10),
    primary key (run_id, dimension, question_id)
);

create table scoring_pattern_evaluation (
    run_id               uuid         not null references scoring_run (id),
    tenant_id            varchar(64)  not null,
    pattern_id           varchar(64)  not null,
    version              integer      not null,
    code                 varchar(120) not null,
    category             varchar(32)  not null,
    severity             varchar(16)  not null,
    status               varchar(16)  not null,
    reason               varchar(64)  not null,
    confidence           numeric(4, 2) check (confidence between 0 and 1),
    confidence_band      varchar(16),
    penalty_eligible     boolean      not null,
    missing_operands     jsonb        not null,
    action_keys          jsonb        not null,
    condition_result     jsonb,
    confidence_breakdown jsonb,
    primary key (run_id, pattern_id, version)
);

create table scoring_pattern_evidence (
    run_id           uuid         not null references scoring_run (id),
    tenant_id        varchar(64)  not null,
    pattern_id       varchar(64)  not null,
    version          integer      not null,
    ordinal          integer      not null,
    operand_key      varchar(200) not null,
    kind             varchar(24)  not null,
    numeric_value    numeric(14, 10),
    flag             boolean,
    source_questions jsonb        not null,
    correlation_group varchar(200) not null,
    reliability      numeric(14, 10) not null,
    primary key (run_id, pattern_id, version, ordinal)
);

create table scoring_imbalance_result (
    run_id             uuid         not null references scoring_run (id),
    tenant_id          varchar(64)  not null,
    rule_id            varchar(64)  not null,
    version            integer      not null,
    status             varchar(16)  not null,
    reason             varchar(32)  not null,
    left_dimension     varchar(32)  not null,
    right_dimension    varchar(32)  not null,
    left_value         numeric(5, 2),
    right_value        numeric(5, 2),
    gap                numeric(8, 2),
    direction          varchar(24)  not null,
    gap_operator       varchar(16)  not null,
    threshold          numeric(8, 2) not null,
    severity           varchar(16)  not null,
    basis              varchar(32)  not null,
    explanation_key    varchar(200),
    recommendation_key varchar(200),
    evidence_refs      jsonb        not null,
    primary key (run_id, rule_id, version)
);

create table scoring_penalty_result (
    run_id                  uuid         not null references scoring_run (id),
    tenant_id               varchar(64)  not null,
    penalty_id              varchar(64)  not null,
    version                 integer      not null,
    code                    varchar(120) not null,
    target                  varchar(32)  not null,
    strategy                varchar(24)  not null,
    category                varchar(64)  not null,
    exclusive_group         varchar(64),
    priority                integer      not null,
    rule_order              integer      not null,
    stacking_policy         varchar(24)  not null,
    trigger_pattern_id      varchar(64)  not null,
    trigger_pattern_version integer      not null,
    status                  varchar(16)  not null,
    reason                  varchar(64)  not null,
    candidate_deduction     numeric(8, 2) not null check (candidate_deduction >= 0),
    effective_deduction     numeric(8, 2) not null check (effective_deduction >= 0),
    suppressed_amount       numeric(8, 2) not null check (suppressed_amount >= 0),
    winning_penalty_id      varchar(64),
    score_base              varchar(120),
    score_base_value        numeric(14, 10),
    evidence_refs           jsonb        not null,
    explanation_key         varchar(200),
    primary key (run_id, penalty_id, version),
    check (candidate_deduction = effective_deduction + suppressed_amount)
);

-- Historical scoring results are immutable (specv3.md Sections 26.8, 39 #20); retention is a platform policy.
create function prevent_history_change() returns trigger as $$
begin
    raise exception 'scoring history is immutable (% on %)', tg_op, tg_table_name;
end;
$$ language plpgsql;

create trigger trg_scoring_run_immutable before update or delete on scoring_run
    for each row execute function prevent_history_change();
create trigger trg_scoring_dimension_result_immutable before update or delete on scoring_dimension_result
    for each row execute function prevent_history_change();
create trigger trg_scoring_contribution_immutable before update or delete on scoring_contribution
    for each row execute function prevent_history_change();
create trigger trg_scoring_pattern_evaluation_immutable before update or delete on scoring_pattern_evaluation
    for each row execute function prevent_history_change();
create trigger trg_scoring_pattern_evidence_immutable before update or delete on scoring_pattern_evidence
    for each row execute function prevent_history_change();
create trigger trg_scoring_imbalance_result_immutable before update or delete on scoring_imbalance_result
    for each row execute function prevent_history_change();
create trigger trg_scoring_penalty_result_immutable before update or delete on scoring_penalty_result
    for each row execute function prevent_history_change();
create trigger trg_scoring_idempotency_key_immutable before update or delete on scoring_idempotency_key
    for each row execute function prevent_history_change();
