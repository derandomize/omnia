package com.omnia.migrator.config;

public class Config {
    private Boolean preferLocal = true;
    private String configTableName = "omnia_config";
    private String featureName = "omnia_id";

    public Boolean getPreferLocal() {
        return preferLocal;
    }

    public void setPrefer_local(Boolean preferLocal) {
        this.preferLocal = preferLocal;
    }

    public String getConfigTableName() {
        return configTableName;
    }

    public void setConfig_table_name(String configTableName) {
        this.configTableName = configTableName;
    }

    public String getFeatureName() {
        return featureName;
    }

    public void setFeature_name(String featureName) {
        this.featureName = featureName;
    }
}
