create table payments (
    id uuid primary key,
    reservation_id uuid not null unique references reservations(id) on delete cascade,
    amount bigint not null check (amount >= 0),
    status varchar(20) not null check (status in ('PENDING', 'SUCCESS', 'FAILED', 'CANCELED')),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version int not null
);

create index idx_payments_status on payments(status);
