-- Platform administration (decision D46): an administrator of the platform, who belongs to no tenant, creates and lists
-- tenants. Those events belong to no tenant (a list read), so the audit may now hold a row without one. A row that names
-- a tenant is still visible only in that tenant's own queries; rows without one are never returned by a tenant query.
alter table scoring_access_audit alter column tenant_id drop not null;
