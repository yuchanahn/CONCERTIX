create table reservations (
    id uuid primary key,
    member_id uuid not null references members(id) on delete cascade,
    session_id uuid not null references concert_sessions(id) on delete cascade,
    seat_id uuid not null references seats(id) on delete cascade,
    price bigint not null check (price >= 0),
    status varchar(30) not null check (status in ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELED', 'EXPIRED')),
    held_until timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version int not null
);

create index idx_reservations_member on reservations(member_id);
create index idx_reservations_session on reservations(session_id);
create index idx_reservations_seat on reservations(seat_id);
create index idx_reservations_seat_status on reservations(seat_id, status);
