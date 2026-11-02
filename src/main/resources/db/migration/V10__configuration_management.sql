-- Managing scoring configuration in the product (decisions D52 to D57; specv3.md Sections 23.6, 42.2 and 42.3).
--
-- configuration_review is the calibration and approval record of one version: who submitted it, what the validation and the
-- preview showed, who approved it (a different person from everyone who worked on it, unless the rule is switched off) and the
-- calibration reference and known limitations the approver stated. A version with no reference is shown as unvalidated. It holds
-- numbers and ids only: never an answer.
create table configuration_review (
    version_id            uuid         primary key references scoring_configuration_version (id),
    tenant_id             varchar(64)  references tenant (id),
    submitted_by          varchar(200) not null,
    submitted_at          timestamptz  not null,
    validation_result     jsonb        not null,
    preview_summary       jsonb,
    fixtures_result       jsonb,
    approved_by           varchar(200),
    approved_at           timestamptz,
    approval_note         text,
    calibration_reference text,
    known_limitations     text,
    rejected_by           varchar(200),
    rejected_at           timestamptz,
    rejection_comment     text,
    check ((approved_by is null) = (approved_at is null))
);
create index ix_configuration_review_tenant on configuration_review (tenant_id);

-- Once a version is approved its record is part of the history and does not change.
create function prevent_approved_review_change() returns trigger as $$
begin
    if old.approved_at is not null then
        raise exception 'the review of configuration version % is approved and cannot change', old.version_id;
    end if;
    return new;
end;
$$ language plpgsql;

create trigger trg_configuration_review_immutable
    before update on configuration_review
    for each row execute function prevent_approved_review_change();

-- Representative test cases of a configuration slot (specv3.md Section 23.6): answers and what the scoring must show. Approval is
-- refused while one fails.
create table configuration_fixture (
    id               uuid         primary key,
    configuration_id uuid         not null references scoring_configuration (id),
    tenant_id        varchar(64)  references tenant (id),
    name             varchar(200) not null,
    answers          jsonb        not null,
    expectations     jsonb        not null,
    created_by       varchar(200) not null,
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    unique (configuration_id, name)
);
create index ix_configuration_fixture_tenant on configuration_fixture (tenant_id);
