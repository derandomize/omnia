package com.omnia.common.config.db;

import java.util.HashMap;
import java.util.Map;

public class PostgresqlParams implements DatabaseParams {
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;

    static public DatabaseParams fromParams(Map<String, Object> params) {
        PostgresqlParams postgresqlParams = new PostgresqlParams();
        postgresqlParams.setHost((String) params.get("host"));
        postgresqlParams.setPort((Integer) params.get("port"));
        postgresqlParams.setDatabaseName((String) params.get("database_name"));
        postgresqlParams.setUsername((String) params.get("username"));
        postgresqlParams.setPassword((String) params.get("password"));
        return postgresqlParams;
    }

    public Map<String, Object> toParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("host", getHost());
        params.put("port", getPort());
        params.put("database_name", getDatabaseName());
        params.put("username", getUsername());
        params.put("password", getPassword());
        return params;
    }

    @Override
    public String type() {
        return "postgresql";
    }

    // Getters and setters
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getDatabaseName() { return databaseName; }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
