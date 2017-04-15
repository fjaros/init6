
alter table users add column alias_id int(11) after id;
update users set alias_id=id;

alter table users add column created bigint not null;
alter table users add column last_logged_in bigint not null;

update users set created=unix_timestamp(now());
update users set last_logged_in=unix_timestamp(now());
