package com.omnia.migrator.config.db;

import java.util.Map;

public class Database {
    private String type;
    private Map<String, Object> params; // Raw params during deserialization
    private DatabaseParams resolvedParams; // Type-specific params after conversion

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }

    public void resolveParams() {
        if ("postgresql".equalsIgnoreCase(type)) {
            resolvedParams = PostgresqlParams.fromParams(params);
        }
    }

    public DatabaseParams getResolvedParams() {
        return resolvedParams;
    }
}