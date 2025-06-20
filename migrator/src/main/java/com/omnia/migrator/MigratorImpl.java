package com.omnia.migrator;

import com.omnia.common.config.AppConfig;
import com.omnia.common.config.OpenSearch;
import com.omnia.common.config.db.Database;
import com.omnia.common.config.db.PostgresqlParams;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.cat.IndicesResponse;
import org.opensearch.client.opensearch.cat.indices.IndicesRecord;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MigratorImpl implements Migrator {
    private static final int MAX_DOCUMENTS_PER_COMMUNE = 1000;
    private static final int HIKARI_MAXIMUM_POOL_SIZE = 10;
    private static final int HIKARI_MINIMUM_IDLE = 2;
    private static final int HIKARI_CONNECTION_TIMEOUT = 30000;
    private static final int HIKARI_IDLE_TIMEOUT = 600000;
    private static final int HIKARI_MAX_LIFETIME = 1800000;


    private static final Logger logger = LoggerFactory.getLogger(MigratorImpl.class);

    private final AppConfig config;
    private final HikariDataSource dataSource;
    private final OpenSearchClient openSearchClient;
    private final RestClient restClient;
    private final OpenSearchTransport transport;

    public MigratorImpl(AppConfig config) {
        this.config = config;

        // Initialize database connection
        config.getDatabase().resolveParams();
        this.dataSource = createDataSource(config.getDatabase());

        // Initialize OpenSearch client
        this.restClient = createRestClient(config.getOpenSearch());
        this.transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.openSearchClient = new OpenSearchClient(transport);

        // Update threshold from database if needed
        updateThresholdFromDatabase();
    }

    private HikariDataSource createDataSource(Database database) {
        PostgresqlParams params = (PostgresqlParams) database.getResolvedParams();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s",
                params.getHost(), params.getPort(), params.getDatabaseName()));
        hikariConfig.setUsername(params.getUsername());
        hikariConfig.setPassword(params.getPassword());
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setMaximumPoolSize(HIKARI_MAXIMUM_POOL_SIZE);
        hikariConfig.setMinimumIdle(HIKARI_MINIMUM_IDLE);
        hikariConfig.setConnectionTimeout(HIKARI_CONNECTION_TIMEOUT);
        hikariConfig.setIdleTimeout(HIKARI_IDLE_TIMEOUT);
        hikariConfig.setMaxLifetime(HIKARI_MAX_LIFETIME);

        return new HikariDataSource(hikariConfig);
    }

    private RestClient createRestClient(OpenSearch openSearchConfig) {
        HttpHost host = new HttpHost(openSearchConfig.getHost(), openSearchConfig.getPort(), "http");

        RestClientBuilder builder = RestClient.builder(host);

        if (openSearchConfig.getUsername() != null && openSearchConfig.getPassword() != null) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(openSearchConfig.getUsername(), openSearchConfig.getPassword()));

            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        }

        return builder.build();
    }

    private void updateThresholdFromDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            DSLContext context = DSL.using(connection, SQLDialect.POSTGRES);

            // Try to get threshold from config table
            var result = context.selectFrom(config.getConfig().getConfigTableName())
                    .where(DSL.field("key").eq("threshold"))
                    .fetchOne();

            if (result != null) {
                Long threshold = result.getValue("value", Long.class);
                if (threshold != null) {
                    config.getConfig().setThreshold(threshold);
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to update threshold from database", e);
        }
    }

    @Override
    public AppConfig getConfig() {
        return config;
    }

    @Override
    public void migrate(CommuneId id) {
        try {
            long documentCount = getCommuneDocumentCount(id);
            long threshold = config.getConfig().getThreshold();

            if (documentCount <= threshold) {
                logger.info("Commune {} has {} documents, below threshold {}", id, documentCount, threshold);
                return;
            }

            logger.info("Migrating commune {} with {} documents", id, documentCount);

            // Get all indexes in this commune
            List<IndexId> indexes = getIndexesInCommune(id);

            // Create new commune for migration
            CommuneId newCommuneId = newCommune();

            // Migrate half of the indexes to the new commune
            int indexesToMigrate = indexes.size() / 2;
            for (int i = 0; i < indexesToMigrate; i++) {
                transfer(id, newCommuneId, indexes.get(i));
            }

            logger.info("Successfully migrated {} indexes from commune {} to {}",
                    indexesToMigrate, id, newCommuneId);

        } catch (Exception e) {
            logger.error("Failed to migrate commune {}", id, e);
            throw new RuntimeException("Migration failed", e);
        }
    }

    @Override
    public void transfer(CommuneId from, CommuneId to, IndexId indexId) {
        try {
            logger.info("Transferring index {} from commune {} to {}", indexId, from, to);

            List<Map<String, Object>> documents = getDocumentsForIndex(from, indexId);

            if (documents.isEmpty()) {
                logger.info("No documents found for index {} in commune {}", indexId, from);
                return;
            }

            addDocumentsToCommune(to, indexId, documents);
            removeDocumentsFromCommune(from, indexId);
            updateIndexCommuneMapping(indexId, to);

            logger.info("Successfully transferred {} documents for index {} from {} to {}",
                    documents.size(), indexId, from, to);

        } catch (Exception e) {
            logger.error("Failed to transfer index {} from {} to {}", indexId, from, to, e);
            throw new RuntimeException("Transfer failed", e);
        }
    }

    @Override
    public CommuneId newCommune() {
        try {
            String communeId = "commune_" + UUID.randomUUID().toString().replace("-", "");

            // Create the commune index in OpenSearch
            CreateIndexRequest request = CreateIndexRequest.of(builder -> builder
                    .index(communeId)
                    .mappings(mappingBuilder -> mappingBuilder
                            .properties("real_index", propertyBuilder -> propertyBuilder
                                    .keyword(keywordBuilder -> keywordBuilder))
                            .properties(config.getConfig().getFeatureName(), propertyBuilder -> propertyBuilder
                                    .keyword(keywordBuilder -> keywordBuilder))
                    )
            );

            openSearchClient.indices().create(request);

            logger.info("Created new commune: {}", communeId);
            return new CommuneId(communeId);

        } catch (Exception e) {
            logger.error("Failed to create new commune", e);
            throw new RuntimeException("Failed to create commune", e);
        }
    }

    @Override
    public List<CommuneId> scan() {
        try {
            updateThresholdFromDatabase();

            List<CommuneId> overflownCommunes = new ArrayList<>();
            long threshold = config.getConfig().getThreshold();

            List<CommuneId> allCommunes = getAllCommunes();

            for (CommuneId commune : allCommunes) {
                long documentCount = getCommuneDocumentCount(commune);
                if (documentCount > threshold) {
                    overflownCommunes.add(commune);
                    logger.info("Found overflown commune: {} with {} documents", commune, documentCount);
                }
            }

            return overflownCommunes;

        } catch (Exception e) {
            logger.error("Failed to scan for overflown communes", e);
            throw new RuntimeException("Scan failed", e);
        }
    }

    @Override
    public long countCommunes() {
        try {
            return getAllCommunes().size();
        } catch (Exception e) {
            logger.error("Failed to count communes", e);
            throw new RuntimeException("Failed to count communes", e);
        }
    }

    private List<CommuneId> getAllCommunes() throws IOException {
        IndicesResponse response = openSearchClient.cat().indices();
        return response.valueBody().stream()
                .map(IndicesRecord::index)
                .filter(index -> index != null && index.startsWith("commune_"))
                .map(CommuneId::new)
                .collect(Collectors.toList());
    }

    private long getCommuneDocumentCount(CommuneId communeId) throws IOException {
        try {
            CountRequest request = CountRequest.of(builder -> builder.index(communeId.value()));
            CountResponse response = openSearchClient.count(request);
            return response.count();
        } catch (OpenSearchException e) {
            if (e.status() == 404) {
                return 0;
            }
            throw e;
        }
    }

    private List<IndexId> getIndexesInCommune(CommuneId communeId) {
        try (Connection connection = dataSource.getConnection()) {
            DSLContext context = DSL.using(connection, SQLDialect.POSTGRES);

            return context.select(DSL.field(DSL.quotedName("index"), String.class))
                    .from(DSL.table("index_to_commune"))
                    .where(DSL.field(DSL.quotedName("commune"), String.class).eq(communeId.value()))
                    .fetch(record -> new IndexId(record.getValue(0, String.class)));

        } catch (SQLException e) {
            logger.error("Failed to get indexes for commune {}", communeId, e);
            throw new RuntimeException("Database query failed", e);
        }
    }

    private void updateIndexCommuneMapping(IndexId indexId, CommuneId newCommuneId) {
        try (Connection connection = dataSource.getConnection()) {
            DSLContext context = DSL.using(connection, SQLDialect.POSTGRES);

            // Update or insert the mapping using quoted field names
            int updated = context.update(DSL.table("index_to_commune"))
                    .set(DSL.field(DSL.quotedName("commune"), String.class), newCommuneId.value())
                    .where(DSL.field(DSL.quotedName("index"), String.class).eq(indexId.value()))
                    .execute();

            if (updated == 0) {
                context.insertInto(DSL.table("index_to_commune"))
                        .set(DSL.field(DSL.quotedName("index"), String.class), indexId.value())
                        .set(DSL.field(DSL.quotedName("commune"), String.class), newCommuneId.value())
                        .execute();
            }

        } catch (SQLException e) {
            logger.error("Failed to update index-commune mapping for index {}", indexId, e);
            throw new RuntimeException("Database update failed", e);
        }
    }

    private List<Map<String, Object>> getDocumentsForIndex(CommuneId communeId, IndexId indexId) throws IOException {
        SearchRequest request = SearchRequest.of(builder -> builder
                .index(communeId.value())
                .query(queryBuilder -> queryBuilder
                        .term(termBuilder -> termBuilder
                                .field("real_index")
                                .value(FieldValue.of(indexId.value()))
                        )
                )
                .size(MAX_DOCUMENTS_PER_COMMUNE)
        );


        SearchResponse<Map> response = openSearchClient.search(request, Map.class);

        List result = response.hits().hits().stream()
                .map(Hit::source)
                .collect(Collectors.toList());

        return result;
    }

    private void addDocumentsToCommune(CommuneId communeId, IndexId indexId, List<Map<String, Object>> documents) throws IOException {
        if (documents.isEmpty()) {
            return;
        }

        List<BulkOperation> operations = new ArrayList<>();

        for (Map<String, Object> document : documents) {
            // Ensure the document has the correct real_index label
            document.put("real_index", indexId.value());

            IndexOperation<Map<String, Object>> indexOp = IndexOperation.of(builder -> builder
                    .index(communeId.value())
                    .document(document)
            );

            operations.add(BulkOperation.of(builder -> builder.index(indexOp)));
        }

        BulkRequest request = BulkRequest.of(builder -> builder.operations(operations));
        BulkResponse response = openSearchClient.bulk(request);

        if (response.errors()) {
            logger.error("Bulk operation had errors while adding documents to commune {}", communeId);
            throw new RuntimeException("Bulk operation failed");
        }
    }

    private void removeDocumentsFromCommune(CommuneId communeId, IndexId indexId) throws IOException {
        DeleteByQueryRequest request = DeleteByQueryRequest.of(builder -> builder
                .index(communeId.value())
                .query(queryBuilder -> queryBuilder
                        .term(termBuilder -> termBuilder
                                .field("real_index")
                                .value(FieldValue.of(indexId.value()))
                        )
                )
        );

        openSearchClient.deleteByQuery(request);
    }

    @Override
    public void close() throws Exception {
        try {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            if (transport != null) {
                transport.close();
            }
            if (restClient != null) {
                restClient.close();
            }
        } catch (Exception e) {
            logger.error("Error closing migrator resources", e);
            throw e;
        }
    }
}
