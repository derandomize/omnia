package com.omnia.common.config;

import com.omnia.common.config.db.Database;
import com.omnia.common.config.db.PostgresqlParams;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

public class YamlParser {
    public static AppConfig parseYaml(InputStream stream) {
        Yaml yaml = new Yaml();
        AppConfig config = yaml.loadAs(stream, AppConfig.class);
        postProcess(config);
        validate(config);
        return config;
    }

    public static void postProcess(AppConfig config) {
        if (config.getDatabase() != null) {
            config.getDatabase().resolveParams();
        }
    }

    public static void validate(AppConfig config) {
        if (config.getOpenSearch() == null) {
            throw new IllegalArgumentException("OpenSearch section is missing");
        }
        OpenSearch openSearch = config.getOpenSearch();
        validateField(openSearch.getHost(), "open_search", "host");
        validateField(openSearch.getPort(), "open_search", "port");
        validateField(openSearch.getUsername(), "open_search", "username");
        validateField(openSearch.getPassword(), "open_search", "password");

        if (config.getDatabase() == null) {
            throw new IllegalArgumentException("OpenSearch section is missing");
        }
        Database db = config.getDatabase();
        if (db.getType().equalsIgnoreCase("postgresql")) {
            PostgresqlParams params = (PostgresqlParams) db.getResolvedParams();
            validateField(params.getHost(), "database", "host");
            validateField(params.getPort(), "database", "port");
            validateField(params.getUsername(), "database", "username");
            validateField(params.getPassword(), "database", "password");
        } else {
            throw new IllegalArgumentException("Only postgresql is supported");
        }
    }

    public static void validateField(Object value, String section, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(String.format("Missing required argument: %s in the %s", fieldName, section));
        }
    }
}