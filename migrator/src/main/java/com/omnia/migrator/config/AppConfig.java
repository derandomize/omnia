package com.omnia.migrator.config;

import com.omnia.migrator.config.db.Database;

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

    public void setOpen_search(OpenSearch openSearch) {
        this.openSearch = openSearch;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }
}
