create table seats (
 id uuid primary key,
 session_id uuid not null references concert_sessions(id) on delete cascade,
 section varchar(50) not null,
 row_label varchar(20) not null,
 seat_number int not null check (seat_number between 1 and 100000),
 price bigint not null check (price between 0 and 1000000000),
 status varchar(20) not null check (status in ('AVAILABLE', 'HELD', 'RESERVED', 'DISABLED')),
 version int not null
);

alter table seats add constraint uq_session_seat unique(session_id, section, row_label, seat_number);
create index idx_seats_session on seats(session_id);

