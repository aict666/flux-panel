CREATE TABLE IF NOT EXISTS forward (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  user_name VARCHAR(100) NOT NULL,
  name VARCHAR(100) NOT NULL,
  tunnel_id BIGINT NOT NULL,
  remote_addr TEXT NOT NULL,
  strategy VARCHAR(100) NOT NULL DEFAULT 'fifo',
  in_flow BIGINT NOT NULL DEFAULT 0,
  out_flow BIGINT NOT NULL DEFAULT 0,
  created_time BIGINT NOT NULL,
  updated_time BIGINT NOT NULL,
  status INTEGER NOT NULL,
  inx INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS forward_port (
  id BIGSERIAL PRIMARY KEY,
  forward_id BIGINT NOT NULL,
  node_id BIGINT NOT NULL,
  port INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS node (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  secret VARCHAR(100) NOT NULL,
  server_ip VARCHAR(100) NOT NULL,
  port TEXT NOT NULL,
  install_service_name VARCHAR(100),
  interface_name VARCHAR(200),
  version VARCHAR(100),
  http INTEGER NOT NULL DEFAULT 0,
  tls INTEGER NOT NULL DEFAULT 0,
  socks INTEGER NOT NULL DEFAULT 0,
  created_time BIGINT NOT NULL,
  updated_time BIGINT,
  status INTEGER NOT NULL,
  tcp_listen_addr VARCHAR(100) NOT NULL DEFAULT '[::]',
  udp_listen_addr VARCHAR(100) NOT NULL DEFAULT '[::]'
);

CREATE TABLE IF NOT EXISTS speed_limit (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  speed INTEGER NOT NULL,
  tunnel_id BIGINT NOT NULL,
  tunnel_name VARCHAR(100) NOT NULL,
  created_time BIGINT NOT NULL,
  updated_time BIGINT,
  status INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS statistics_flow (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  in_flow BIGINT NOT NULL DEFAULT 0,
  out_flow BIGINT NOT NULL DEFAULT 0,
  flow BIGINT NOT NULL DEFAULT 0,
  total_in_flow BIGINT NOT NULL DEFAULT 0,
  total_out_flow BIGINT NOT NULL DEFAULT 0,
  total_flow BIGINT NOT NULL DEFAULT 0,
  hour_time BIGINT NOT NULL,
  time VARCHAR(100) NOT NULL,
  created_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS forward_statistics_flow (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  forward_id BIGINT NOT NULL,
  forward_name VARCHAR(100) NOT NULL,
  tunnel_id BIGINT NOT NULL,
  tunnel_name VARCHAR(100),
  in_flow BIGINT NOT NULL DEFAULT 0,
  out_flow BIGINT NOT NULL DEFAULT 0,
  flow BIGINT NOT NULL DEFAULT 0,
  total_in_flow BIGINT NOT NULL DEFAULT 0,
  total_out_flow BIGINT NOT NULL DEFAULT 0,
  total_flow BIGINT NOT NULL DEFAULT 0,
  hour_time BIGINT NOT NULL,
  time VARCHAR(100) NOT NULL,
  created_time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS tunnel (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  traffic_ratio NUMERIC(10,4) NOT NULL DEFAULT 1.0,
  type INTEGER NOT NULL,
  protocol VARCHAR(10) NOT NULL DEFAULT 'tls',
  flow INTEGER NOT NULL,
  created_time BIGINT NOT NULL,
  updated_time BIGINT NOT NULL,
  status INTEGER NOT NULL,
  in_ip TEXT,
  topology_json TEXT
);

CREATE TABLE IF NOT EXISTS chain_tunnel (
  id BIGSERIAL PRIMARY KEY,
  tunnel_id BIGINT NOT NULL,
  chain_type INTEGER NOT NULL,
  node_id BIGINT NOT NULL,
  port INTEGER,
  strategy VARCHAR(10),
  inx INTEGER,
  protocol VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  username VARCHAR(100) NOT NULL,
  pwd VARCHAR(100) NOT NULL,
  role_id INTEGER NOT NULL,
  exp_time BIGINT NOT NULL,
  flow BIGINT NOT NULL,
  in_flow BIGINT NOT NULL DEFAULT 0,
  out_flow BIGINT NOT NULL DEFAULT 0,
  flow_reset_time BIGINT NOT NULL,
  num INTEGER NOT NULL,
  created_time BIGINT NOT NULL,
  updated_time BIGINT,
  status INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS user_tunnel (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  tunnel_id BIGINT NOT NULL,
  speed_id BIGINT,
  num INTEGER NOT NULL,
  flow BIGINT NOT NULL,
  in_flow BIGINT NOT NULL DEFAULT 0,
  out_flow BIGINT NOT NULL DEFAULT 0,
  flow_reset_time BIGINT NOT NULL,
  exp_time BIGINT NOT NULL,
  status INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS vite_config (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(200) NOT NULL UNIQUE,
  value VARCHAR(200) NOT NULL,
  time BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_client (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  agent_type VARCHAR(50) NOT NULL,
  description VARCHAR(500),
  scope_json TEXT NOT NULL,
  created_time BIGINT NOT NULL,
  updated_time BIGINT NOT NULL,
  status INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS agent_api_key (
  id BIGSERIAL PRIMARY KEY,
  client_id BIGINT NOT NULL,
  key_prefix VARCHAR(50) NOT NULL,
  key_hash VARCHAR(128) NOT NULL,
  expires_time BIGINT,
  last_used_time BIGINT,
  last_used_ip VARCHAR(100),
  created_time BIGINT NOT NULL,
  updated_time BIGINT NOT NULL,
  status INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS agent_api_audit_log (
  id BIGSERIAL PRIMARY KEY,
  client_id BIGINT,
  client_name VARCHAR(100),
  request_path VARCHAR(255) NOT NULL,
  http_method VARCHAR(20) NOT NULL,
  action VARCHAR(100) NOT NULL,
  status_code INTEGER NOT NULL,
  success BOOLEAN NOT NULL DEFAULT FALSE,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  request_ip VARCHAR(100),
  error_message VARCHAR(1000),
  created_time BIGINT NOT NULL,
  updated_time BIGINT NOT NULL,
  status INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_forward_user_id ON forward(user_id);
CREATE INDEX IF NOT EXISTS idx_forward_tunnel_id ON forward(tunnel_id);
CREATE INDEX IF NOT EXISTS idx_forward_port_forward_id ON forward_port(forward_id);
CREATE INDEX IF NOT EXISTS idx_user_tunnel_user_id ON user_tunnel(user_id);
CREATE INDEX IF NOT EXISTS idx_statistics_flow_user_hour ON statistics_flow(user_id, hour_time);
CREATE UNIQUE INDEX IF NOT EXISTS uk_statistics_flow_user_hour ON statistics_flow(user_id, hour_time);
CREATE INDEX IF NOT EXISTS idx_forward_statistics_flow_user_hour ON forward_statistics_flow(user_id, hour_time);
CREATE INDEX IF NOT EXISTS idx_forward_statistics_flow_forward_hour ON forward_statistics_flow(forward_id, hour_time);
CREATE UNIQUE INDEX IF NOT EXISTS uk_forward_statistics_flow_forward_hour ON forward_statistics_flow(forward_id, hour_time);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_client_name ON agent_client(name);
CREATE INDEX IF NOT EXISTS idx_agent_api_key_client_id ON agent_api_key(client_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_api_key_prefix ON agent_api_key(key_prefix);
CREATE INDEX IF NOT EXISTS idx_agent_api_audit_log_client_created ON agent_api_audit_log(client_id, created_time);
