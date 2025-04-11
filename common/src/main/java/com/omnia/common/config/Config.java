package com.omnia.common.config;

public class Config {
    private Boolean preferLocal = true;
    private String configTableName = "omnia_config";
    private String featureName = "omnia_id";
    private long threshold = 10000;

    public long getThreshold() { 
        return threshold; 
    }

    public void setThreshold(long threshold) { 
        this.threshold = threshold; 
    }

    public Boolean getPreferLocal() {
        return preferLocal;
    }

    public void setPreferLocal(Boolean preferLocal) {
        this.preferLocal = preferLocal;
    }

    public String getConfigTableName() {
        return configTableName;
    }

    public void setConfigTableName(String configTableName) {
        this.configTableName = configTableName;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeatureName(String featureName) {
        this.featureName = featureName;
    }
}
