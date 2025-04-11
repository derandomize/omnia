package com.omnia.common.config;

public class Config {
    private Boolean preferLocal = true;
    private String configTableName = "omnia_config";
    private String featureName = "omnia_id";

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
