package com.omnia.sdk;

import com.omnia.common.config.AppConfig;
import com.omnia.common.config.Config;
import com.omnia.common.config.db.Database;
import com.omnia.common.config.db.PostgresqlParams;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
public class OmniaSDKPostgreSQLTest {

    private static final String FEATURE_NAME = "test-feature";
    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");
    @Container
    public static OpensearchContainer<?> opensearch = (OpensearchContainer<?>) new OpensearchContainer("opensearchproject/opensearch:2.9.0")
            .withStartupTimeout(java.time.Duration.ofMinutes(5));
    private static OmniaSDKPostgreSQL sdk;
    private static OpenSearchClient openSearchClient;

    @BeforeAll
    static void setUp() throws SQLException, IOException {
        // Setup PostgreSQL schema and data
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE \"INDEX_TO_COMMUNE\" (\"index\" VARCHAR PRIMARY KEY, commune VARCHAR)");
            stmt.execute("INSERT INTO \"INDEX_TO_COMMUNE\" (\"index\", commune) VALUES ('123', 'commune')");
            stmt.execute("INSERT INTO \"INDEX_TO_COMMUNE\" (\"index\", commune) VALUES ('456', 'commune')");
        }

        AppConfig appConfig = createTestAppConfig();
        sdk = new OmniaSDKPostgreSQL(appConfig);
        openSearchClient = createOpenSearchClient();

        createOpenSearchIndex();
        indexTestDocument("123", "1");
        indexTestDocument("456", "2");
    }

    private static AppConfig createTestAppConfig() {
        PostgresqlParams postgresqlParams = new PostgresqlParams();
        postgresqlParams.setHost(postgres.getHost());
        postgresqlParams.setPort(postgres.getFirstMappedPort());
        postgresqlParams.setDatabaseName(postgres.getDatabaseName());
        postgresqlParams.setUsername(postgres.getUsername());
        postgresqlParams.setPassword(postgres.getPassword());

        Database database = new Database();
        database.setType("postgresql");
        database.setParams(postgresqlParams.toParams());

        AppConfig appConfig = new AppConfig();
        appConfig.setConfig(new Config());
        appConfig.setDatabase(database);
        appConfig.getConfig().setFeatureName(FEATURE_NAME);

        return appConfig;
    }

    private static OpenSearchClient createOpenSearchClient() {
        RestClient restClient = RestClient.builder(
                new HttpHost(opensearch.getHost(), opensearch.getMappedPort(9200))
        ).build();

        OpenSearchTransport transport = new RestClientTransport(restClient, new org.opensearch.client.json.jackson.JacksonJsonpMapper());
        return new OpenSearchClient(transport);
    }

    private static void createOpenSearchIndex() throws IOException {
        CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder()
                .index("commune")
                .build();
        openSearchClient.indices().create(createIndexRequest);
    }

    private static void indexTestDocument(String name, String id) throws IOException {
        Map<String, String> document = new HashMap<>();
        document.put(FEATURE_NAME, name);

        IndexRequest<Map<String, String>> indexRequest = new IndexRequest.Builder<Map<String, String>>()
                .index("commune")
                .id(id)
                .document(document)
                .build();

        openSearchClient.index(indexRequest);
        openSearchClient.indices().refresh(b -> b.index("commune")); // Refresh to make document searchable
    }

    @Test
    void testTransformIndexId() {
        String communeId = sdk.transformIndexId("123");
        assertEquals("commune", communeId);
        communeId = sdk.transformIndexId("456");
        assertEquals("commune", communeId);
    }

    @Test
    void testCreateSearchRequestBuilder() throws IOException {
        SearchRequest.Builder requestBuilder = sdk.createSearchRequestBuilder("123");
        SearchRequest request = requestBuilder.build();

        SearchResponse<Object> response = openSearchClient.search(request, Object.class);
        assertEquals(1, response.hits().hits().size(), "Should find one document");
    }

    @Test
    void testAddIndexFilter() throws IOException {
        Query baseQuery = new Query.Builder().matchAll(m -> m).build();
        Query combinedQuery = sdk.addIndexFilter(baseQuery, "123");

        SearchRequest request = new SearchRequest.Builder()
                .index("commune")
                .query(combinedQuery)
                .build();

        SearchResponse<Object> response = openSearchClient.search(request, Object.class);
        assertEquals(1, response.hits().hits().size(), "Combined query should find one document");
    }

    @Test
    void testAutoIndexCreation() throws IOException {
        openSearchClient.indices().create(new CreateIndexRequest.Builder().index("666").build());
        assertEquals("commune_main", sdk.transformIndexId("666"));
    }

    @Test
    void testGetFilterField() {
        assertEquals(FEATURE_NAME, sdk.getFilterField(), "Filter field should match feature name from config");
    }
}