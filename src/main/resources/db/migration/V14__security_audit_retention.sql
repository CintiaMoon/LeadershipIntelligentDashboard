-- Retention of the security audit (decision D74: a setting, 400 days by default). The table stays append-only: an UPDATE is never
-- allowed, and a DELETE is allowed only inside the transaction of the retention job, which says so with the local setting
-- lis.audit_purge. Anything else that tries to change or remove a row still fails. (This guards against mistakes and ordinary
-- code paths; someone who can run arbitrary SQL as the application's database user could set the same value, as with any
-- application-level guard.)

create or replace function prevent_security_audit_change() returns trigger as $$
begin
    if tg_op = 'DELETE' and current_setting('lis.audit_purge', true) = 'on' then
        return old;
    end if;
    raise exception 'security audit events are immutable and append-only (% on %)', tg_op, tg_table_name;
end;
$$ language plpgsql;

drop trigger trg_security_audit_event_immutable on security_audit_event;
create trigger trg_security_audit_event_immutable before update or delete on security_audit_event
    for each row execute function prevent_security_audit_change();
