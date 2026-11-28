-- US11 (KAN-63): closing a maintenance ticket records when and the work done (BR20). The time to
-- repair of the KPIs (BR19) is closed_at - opened_at.
ALTER TABLE maintenance_tickets
    ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN work_done VARCHAR(1000),
    -- Both are set exactly when the ticket is Closed.
    ADD CONSTRAINT ck_maintenance_tickets_closing CHECK (
        (state = 'CLOSED') = (closed_at IS NOT NULL)
        AND (state = 'CLOSED') = (work_done IS NOT NULL)),
    ADD CONSTRAINT ck_maintenance_tickets_closed_after_opening CHECK (
        closed_at IS NULL OR closed_at >= opened_at);
