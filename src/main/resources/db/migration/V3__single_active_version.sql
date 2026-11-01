-- At most one ACTIVE version per configuration slot (decision D33). A new activation archives the previous one first.
create unique index ux_scoring_configuration_one_active
    on scoring_configuration_version (configuration_id) where status = 'ACTIVE';
