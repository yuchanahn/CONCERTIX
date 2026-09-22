create table concerts (
 id uuid primary key,
 title varchar(200) not null,
 description varchar(5000),
 image_url varchar(2048),
 venue varchar(200) not null,
 hold_seconds int not null check (hold_seconds between 1 and 3600),
 max_tickets_per_user int not null check (max_tickets_per_user between 1 and 100),
 status varchar(20) not null check (status in ('PREPARING', 'PUBLISHED')),
 created_at timestamp with time zone not null,
 version int not null
);
create table concert_sessions (
 id uuid primary key,
 concert_id uuid not null references concerts(id) on delete cascade,
 starts_at timestamp with time zone not null,
 booking_opens_at timestamp with time zone not null,
 booking_closes_at timestamp with time zone not null,
 capacity int not null check (capacity between 1 and 100000),
 check (booking_opens_at < booking_closes_at),
 check (booking_closes_at <= starts_at)
);
alter table concert_sessions add constraint uq_concert_start unique(concert_id, starts_at);
