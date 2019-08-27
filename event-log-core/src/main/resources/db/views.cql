CREATE MATERIALIZED VIEW events_by_cat AS
SELECT * FROM events
WHERE
category IS NOT null and
year is not null and
month is not null and
day is not null and
hour is not null and
id is not null
PRIMARY KEY ((category, year, month), day, hour, id);

CREATE MATERIALIZED VIEW events_by_customer AS
SELECT * FROM events
WHERE
customer_id IS NOT null and
category IS NOT null and
year is not null and
month is not null and
day is not null and
hour is not null and
id is not null
PRIMARY KEY ((customer_id, category, year, month), day, hour, id);


CREATE MATERIALIZED VIEW lookups_by_category AS
SELECT * FROM lookups
WHERE
value IS NOT null and
name IS NOT null and
category is not null
PRIMARY KEY ((category), value, name);