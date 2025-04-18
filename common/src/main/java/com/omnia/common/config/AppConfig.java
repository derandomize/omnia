package com.omnia.common.config;

import com.omnia.common.config.db.Database;

public class AppConfig {
    private Config config;
    private OpenSearch openSearch;
    private Database database;

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public OpenSearch getOpenSearch() {
        return openSearch;
    }

    public void setOpenSearch(OpenSearch openSearch) {
        this.openSearch = openSearch;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }
}
