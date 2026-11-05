-- Company memberships (decision D69; docs/implementation/IDENTITY_PROPOSAL.md). A company is the existing tenant. A membership
-- gives one user a set of roles and organizations in one company; it is the only thing that grants access to a company, and
-- it is read on every request, so suspending or removing it takes effect at once.
--
-- A membership is created INVITED, before the invited person may have an account: it then names the invited email and has no
-- user yet. It becomes ACTIVE when the person accepts the invitation with the emailed token.

create table company_membership (
    id             uuid         primary key,
    tenant_id      varchar(64)  not null references tenant (id),
    user_id        uuid         references app_user (id),
    invited_email  varchar(254),
    status         varchar(16)  not null check (status in ('INVITED', 'ACTIVE', 'SUSPENDED', 'REMOVED')),
    invited_by     varchar(64),
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    -- every membership names a person: by account, or by the email it was sent to
    constraint ck_membership_person check (user_id is not null or invited_email is not null),
    -- only an invitation can still lack an account
    constraint ck_membership_user_when_active check (status = 'INVITED' or user_id is not null)
);
create unique index ux_membership_tenant_user on company_membership (tenant_id, user_id) where user_id is not null;
create unique index ux_membership_tenant_invited on company_membership (tenant_id, lower(invited_email)) where invited_email is not null and user_id is null;
create index ix_membership_user on company_membership (user_id, status);

-- The roles of a membership: the five tenant roles, or RESPONDENT on its own (the respondent role is deliberately not in the
-- Role enum). A role name is checked here so a typo cannot create a role nobody handles.
create table company_membership_role (
    membership_id  uuid        not null references company_membership (id) on delete cascade,
    role           varchar(24) not null check (role in ('OWNER', 'ADMIN', 'ENGINEERING_LEADER', 'ANALYST', 'VIEWER', 'RESPONDENT')),
    primary key (membership_id, role)
);

-- The organizations a membership is limited to; none means every organization of the company, as an empty org_ids claim did.
create table company_membership_organization (
    membership_id    uuid        not null references company_membership (id) on delete cascade,
    organization_id  varchar(64) not null,
    primary key (membership_id, organization_id)
);

-- Single-use invitation tokens (7 days). Only the SHA-256 of the random token is stored.
create table membership_invitation (
    id             uuid        primary key,
    membership_id  uuid        not null references company_membership (id) on delete cascade,
    token_hash     char(64)    not null,
    expires_at     timestamptz not null,
    used_at        timestamptz,
    created_at     timestamptz not null
);
create unique index ux_membership_invitation_hash on membership_invitation (token_hash);
create index ix_membership_invitation_membership on membership_invitation (membership_id);
