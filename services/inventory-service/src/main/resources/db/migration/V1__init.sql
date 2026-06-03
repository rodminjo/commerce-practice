CREATE SCHEMA IF NOT EXISTS inventory_svc;
SET search_path TO inventory_svc;

-- Week 3-5: inventory, reservations, outbox tables land here.
-- The atomic conditional UPDATE (no separate lock) ships in Week 5.
