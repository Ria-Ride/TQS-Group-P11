-- US10 (KAN-62): maintenance tickets, opened from a fault reading or by an operator, with an SLA
-- deadline (BR19) and, once assigned, a field technician (BR20). Fault codes follow the telemetry
-- contract (FaultCode). description and opened_by are NULL for a ticket opened from a reading.
CREATE TABLE maintenance_tickets (
    id            UUID                     PRIMARY KEY,
    vehicle_id    UUID                     NOT NULL,
    fault_code    VARCHAR(64)              NOT NULL,
    description   VARCHAR(500),
    state         VARCHAR(20)              NOT NULL,
    opened_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    sla_deadline  TIMESTAMP WITH TIME ZONE NOT NULL,
    opened_by     UUID,
    technician_id UUID,
    CONSTRAINT fk_maintenance_tickets_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_maintenance_tickets_opened_by FOREIGN KEY (opened_by) REFERENCES users (id),
    CONSTRAINT fk_maintenance_tickets_technician FOREIGN KEY (technician_id) REFERENCES users (id),
    CONSTRAINT ck_maintenance_tickets_state CHECK (state IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS',
                                                            'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_maintenance_tickets_deadline CHECK (sla_deadline > opened_at),
    -- An Open ticket has no technician yet; from Assigned on, someone is responsible for it.
    CONSTRAINT ck_maintenance_tickets_technician CHECK (
        (state <> 'OPEN' OR technician_id IS NULL)
        AND (state NOT IN ('ASSIGNED', 'IN_PROGRESS', 'CLOSED') OR technician_id IS NOT NULL))
);

-- BR20: at most one open ticket (TicketState.isOpen) per vehicle and fault. A partial unique index,
-- so finished tickets do not count and the same fault can be opened again later. The database
-- checks it, so two readings of the same fault stored together cannot both open a ticket.
CREATE UNIQUE INDEX uk_maintenance_tickets_open_fault
    ON maintenance_tickets (vehicle_id, fault_code)
    WHERE state IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS');

-- US11: a technician's tickets, ordered by deadline.
CREATE INDEX ix_maintenance_tickets_technician ON maintenance_tickets (technician_id, sla_deadline);
