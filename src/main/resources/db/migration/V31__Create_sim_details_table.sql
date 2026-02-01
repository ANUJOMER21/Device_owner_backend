-- SIM details reported by configure app (device). Used by dealer/admin to view SIM info.
CREATE TABLE IF NOT EXISTS sim_details (
    id BIGSERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    device_id VARCHAR(100),
    sim_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_sim_details_customer FOREIGN KEY (customer_id) REFERENCES customers(customer_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sim_details_customer_id ON sim_details(customer_id);
CREATE INDEX IF NOT EXISTS idx_sim_details_created_at ON sim_details(created_at DESC);

COMMENT ON TABLE sim_details IS 'SIM details reported by configure app when GET_SIM_DETAILS command is executed';
