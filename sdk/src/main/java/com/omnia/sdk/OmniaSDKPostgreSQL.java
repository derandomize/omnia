package com.omnia.sdk;

import com.omnia.common.config.db.Database;
import com.omnia.common.config.db.PostgresqlParams;
import com.omnia.jooq.tables.IndexToCommune;
import com.omnia.common.config.AppConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

public class OmniaSDKPostgreSQL implements OmniaSDK {
    private final DSLContext dsl;
    private final String featureName;

    public OmniaSDKPostgreSQL(AppConfig  config) {
        featureName = config.getConfig().getFeatureName();
        Database dbConfig = config.getDatabase();
        if (!"postgresql".equalsIgnoreCase(dbConfig.getType())) {
            throw new IllegalArgumentException("Unsupported database type: " + dbConfig.getType());
        }

        dbConfig.resolveParams();
        PostgresqlParams params = (PostgresqlParams) dbConfig.getResolvedParams();

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(buildJdbcUrl(params));
        dataSource.setUsername(params.getUsername());
        dataSource.setPassword(params.getPassword());
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);

        this.dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    private String buildJdbcUrl(PostgresqlParams params) {
        return String.format("jdbc:postgresql://%s:%d/%s",
                params.getHost(),
                params.getPort(),
                params.getDatabaseName()
        );
    }

    @Override
    public String transformIndexId(String indexId) {
        String commune = dsl.select(IndexToCommune.INDEX_TO_COMMUNE.COMMUNE)
                .from(IndexToCommune.INDEX_TO_COMMUNE)
                .where(IndexToCommune.INDEX_TO_COMMUNE.INDEX.eq(indexId))
                .fetchOneInto(String.class);

        if (commune != null) {
            return commune;
        } else {
            final String defaultCommune = "commune_main";

            dsl.insertInto(IndexToCommune.INDEX_TO_COMMUNE)
                    .set(IndexToCommune.INDEX_TO_COMMUNE.INDEX, indexId)
                    .set(IndexToCommune.INDEX_TO_COMMUNE.COMMUNE, defaultCommune)
                    .onConflictDoNothing()
                    .execute();

            return defaultCommune;
        }
    }

    @Override
    public String getFilterField() {
        return featureName;
    }
}
