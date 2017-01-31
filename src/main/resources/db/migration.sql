
alter table users add column alias_id int(11) after id;
update users set alias_id=id;

