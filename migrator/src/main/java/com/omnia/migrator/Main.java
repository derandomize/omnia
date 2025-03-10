package com.omnia.migrator;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.omnia.migrator.config.AppConfig;
import com.omnia.migrator.config.YamlParser;
import com.omnia.migrator.config.db.PostgresqlParams;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.FileInputStream;
import java.sql.Connection;

public class Main {
    private static Args parseArgs(String[] args) {
        Args parsedArgs = new Args();
        JCommander jCommander = JCommander.newBuilder().addObject(parsedArgs).build();
        jCommander.parse(args);
        jCommander.setProgramName("Migrator");
        if (parsedArgs.help) {
            jCommander.usage();
            System.exit(0);
        }
        return parsedArgs;

    }

    public static void main(String[] args) {
        Args parsedArgs = parseArgs(args);
        AppConfig config;
        try (FileInputStream configFile = new FileInputStream(parsedArgs.configFile)) {
            config = YamlParser.parseYaml(configFile);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return;
        }

        PostgresqlParams dbParams = (PostgresqlParams) config.getDatabase().getResolvedParams();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDataSourceClassName(org.postgresql.ds.PGSimpleDataSource.class.getName());
        hikariConfig.addDataSourceProperty("server", dbParams.getHost());
        hikariConfig.addDataSourceProperty("portNumber", dbParams.getPort());
        hikariConfig.addDataSourceProperty("databaseName", dbParams.getDatabaseName());
        hikariConfig.addDataSourceProperty("user", dbParams.getUsername());
        hikariConfig.addDataSourceProperty("password", dbParams.getPassword());

        try (HikariDataSource dataSource = new HikariDataSource(hikariConfig)) {
            Connection connection = dataSource.getConnection();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static class Args {
        @Parameter(names = {"--config", "-c"}, description = "Config file for app")
        String configFile;

        @Parameter(names = "--help", help = true)
        private final boolean help = true;
    }
}
