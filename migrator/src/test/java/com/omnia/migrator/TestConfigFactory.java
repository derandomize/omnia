package com.omnia.migrator;

import com.omnia.common.config.AppConfig;
import com.omnia.common.config.Config;
import com.omnia.common.config.OpenSearch;
import com.omnia.common.config.db.Database;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.Map;

public class TestConfigFactory {

    public static AppConfig createTestConfig(PostgreSQLContainer<?> postgres, GenericContainer<?> opensearch) {
        AppConfig config = new AppConfig();

        // Database config
        Database database = new Database();
        database.setType("postgresql");
        Map<String, Object> dbParams = new HashMap<>();
        dbParams.put("host", postgres.getHost());
        dbParams.put("port", postgres.getFirstMappedPort());
        dbParams.put("database_name", postgres.getDatabaseName());
        dbParams.put("username", postgres.getUsername());
        dbParams.put("password", postgres.getPassword());
        database.setParams(dbParams);
        config.setDatabase(database);

        // OpenSearch config
        OpenSearch openSearch = new OpenSearch();
        openSearch.setHost(opensearch.getHost());
        openSearch.setPort(opensearch.getFirstMappedPort());
        config.setOpenSearch(openSearch);

        // General config
        Config generalConfig = new Config();
        generalConfig.setThreshold(100L); // Lower threshold for testing
        generalConfig.setConfigTableName("omnia_config");
        generalConfig.setFeatureName("omnia_id");
        config.setConfig(generalConfig);

        return config;
    }
}
