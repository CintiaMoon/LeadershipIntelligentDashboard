-- A withdrawn invitation to someone who never created an account is a REMOVED membership without a user. V12 allowed a missing user
-- only while the membership was INVITED, so withdrawing such an invitation was refused by the database.
alter table company_membership drop constraint ck_membership_user_when_active;
alter table company_membership add constraint ck_membership_user_when_active check (status in ('INVITED', 'REMOVED') or user_id is not null);
