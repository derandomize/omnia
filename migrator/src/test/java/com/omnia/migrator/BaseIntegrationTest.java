package com.omnia.migrator;

import com.omnia.common.config.AppConfig;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;

@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    protected static final GenericContainer<?> opensearch = new GenericContainer<>(
            DockerImageName.parse("opensearchproject/opensearch:2.8.0"))
            .withExposedPorts(9200)
            .withEnv("discovery.type", "single-node")
            .withEnv("plugins.security.disabled", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .waitingFor(Wait.forHttp("/_cluster/health").forPort(9200));

    protected AppConfig testConfig;
    protected Migrator migrator;
    protected OpenSearchClient openSearchClient;
    protected DSLContext dslContext;

    @BeforeEach
    void setUp() throws Exception {
        testConfig = TestConfigFactory.createTestConfig(postgres, opensearch);

        // Initialize database schema
        initializeDatabase();

        // Initialize OpenSearch client
        initializeOpenSearchClient();

        // Initialize migrator
        migrator = new MigratorImpl(testConfig);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (migrator != null) {
            migrator.close();
        }
        if (openSearchClient != null) {
            cleanupOpenSearchIndices();
        }
        if (dslContext != null) {
            cleanupDatabase();
        }
    }

    private void initializeDatabase() throws Exception {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName());

        try (Connection connection = DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword())) {
            dslContext = DSL.using(connection, SQLDialect.POSTGRES);

            // Create tables
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS index_to_commune (
                    "index" VARCHAR PRIMARY KEY,
                    "commune" VARCHAR NOT NULL
                )
                """);

            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS omnia_config (
                    key VARCHAR PRIMARY KEY,
                    value VARCHAR NOT NULL
                )
                """);

            // Insert test config
            connection.createStatement().execute("""
                INSERT INTO omnia_config (key, value) VALUES ('threshold', '100')
                ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value
                """);
        }
    }

    private void initializeOpenSearchClient() throws Exception {
        RestClient restClient = RestClient.builder(
                        new HttpHost(opensearch.getHost(), opensearch.getFirstMappedPort(), "http"))
                .build();

        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        openSearchClient = new OpenSearchClient(transport);
    }

    private void cleanupOpenSearchIndices() throws Exception {
        try {
            // Delete all test indices
            var indices = openSearchClient.cat().indices();
            for (var index : indices.valueBody()) {
                if (index.index() != null && (index.index().startsWith("commune_") || index.index().startsWith("test_"))) {
                    openSearchClient.indices().delete(DeleteIndexRequest.of(b -> b.index(index.index())));
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private void cleanupDatabase() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                String.format("jdbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()),
                postgres.getUsername(), postgres.getPassword())) {

            connection.createStatement().execute("DELETE FROM index_to_commune");
            connection.createStatement().execute("DELETE FROM omnia_config WHERE key != 'threshold'");
        }
    }

    protected void createTestCommune(String communeId) throws Exception {
        CreateIndexRequest request = CreateIndexRequest.of(builder -> builder
                .index(communeId)
                .mappings(mappingBuilder -> mappingBuilder
                        .properties("real_index", propertyBuilder -> propertyBuilder
                                .keyword(keywordBuilder -> keywordBuilder))
                        .properties("omnia_id", propertyBuilder -> propertyBuilder
                                .keyword(keywordBuilder -> keywordBuilder))
                        .properties("content", propertyBuilder -> propertyBuilder
                                .text(textBuilder -> textBuilder))
                )
        );

        openSearchClient.indices().create(request);
    }

    protected void addDocumentToCommune(String communeId, String realIndex, String docId, String content) throws Exception {
        Map<String, Object> document = new HashMap<>();
        document.put("real_index", realIndex);
        document.put("omnia_id", docId);
        document.put("content", content);

        IndexRequest<Map<String, Object>> request = IndexRequest.of(builder -> builder
                .index(communeId)
                .id(docId)
                .document(document)
                .refresh(Refresh.True)
        );

        openSearchClient.index(request);
    }

    protected void addIndexToCommuneMapping(String index, String commune) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                String.format("jdbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()),
                postgres.getUsername(), postgres.getPassword())) {

            connection.createStatement().execute(
                    String.format("INSERT INTO index_to_commune (\"index\", commune) VALUES ('%s', '%s') " +
                            "ON CONFLICT (\"index\") DO UPDATE SET commune = EXCLUDED.commune", index, commune));
        }
    }
}
