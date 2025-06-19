package com.omnia.benchmark;

import com.omnia.common.config.AppConfig;
import com.omnia.common.config.Config;
import com.omnia.common.config.db.Database;
import com.omnia.common.config.db.PostgresqlParams;
import com.omnia.sdk.OmniaSDKPostgreSQL;
import com.omnia.transport.OmniaTransport;
import org.apache.http.HttpHost;
import org.openjdk.jmh.annotations.*;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 10)
@Fork(1)
public class OmniaBenchmark {

    private static final String FEATURE_NAME = "benchmark-feature";
    private static final int DOC_COUNT_PER_INDEX = 10;

    // --- JMH Parameters ---
    @Param({"10", "400", "800", "2000", "5000", "10000", "50000", "100000"}) // Start with 5k, 10k+ can take very long
    public int indexCount;

    @Param({"STANDARD", "OMNIA"})
    public String clientType;


    // --- State managed by JMH ---
    private PostgreSQLContainer<?> postgres;
    private OpensearchContainer<?> opensearch;
    private OpenSearchClient client;
    private RestClient restClient;
    private OpenSearchTransport transport;

    private List<String> logicalIndexNames;
    private final Random random = new Random();
    public Exception setupException = null;

    @Setup(Level.Trial)
    public void setup() {
        System.out.printf("--- Setting up for: %s client with %d indices ---\n", clientType, indexCount);
        setupException = null; // Reset for the new trial
        try {
            postgres = new PostgreSQLContainer<>("postgres:13")
                    .withDatabaseName("benchdb")
                    .withUsername("benchuser")
                    .withPassword("benchpass");
            opensearch = new OpensearchContainer<>("opensearchproject/opensearch:2.9.0")
                    .withStartupTimeout(java.time.Duration.ofMinutes(5));
            postgres.start();
            opensearch.start();

            logicalIndexNames = IntStream.range(0, indexCount)
                    .mapToObj(i -> "logical-index-" + i)
                    .toList();

            if ("OMNIA".equals(clientType)) {
                setupOmniaClient();
            } else {
                setupStandardClient();
            }

            populateData();

            System.out.println("--- Setup complete. ---");

        } catch (Exception e) {
            System.err.println("!!! SETUP FAILED: " + e.getMessage());
            e.printStackTrace();
            this.setupException = e;
        }
    }

    private void setupStandardClient() {
        restClient = RestClient.builder(HttpHost.create(opensearch.getHttpHostAddress())).build();
        transport = new RestClientTransport(restClient, new org.opensearch.client.json.jackson.JacksonJsonpMapper());
        client = new OpenSearchClient(transport);
    }

    private void setupOmniaClient() throws Exception {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE \"INDEX_TO_COMMUNE\" (\"index\" VARCHAR PRIMARY KEY, commune VARCHAR)");
        }

        PostgresqlParams pgParams = new PostgresqlParams();
        pgParams.setHost(postgres.getHost());
        pgParams.setPort(postgres.getFirstMappedPort());
        pgParams.setDatabaseName(postgres.getDatabaseName());
        pgParams.setUsername(postgres.getUsername());
        pgParams.setPassword(postgres.getPassword());

        Database database = new Database();
        database.setType("postgresql");
        database.setParams(pgParams.toParams());

        AppConfig appConfig = new AppConfig();
        appConfig.setDatabase(database);
        appConfig.setConfig(new Config());
        appConfig.getConfig().setFeatureName(FEATURE_NAME);

        OmniaSDKPostgreSQL sdk = new OmniaSDKPostgreSQL(appConfig);

        restClient = RestClient.builder(HttpHost.create(opensearch.getHttpHostAddress())).build();
        OpenSearchTransport baseTransport = new RestClientTransport(restClient, new org.opensearch.client.json.jackson.JacksonJsonpMapper());
        transport = new OmniaTransport(baseTransport, sdk);
        client = new OpenSearchClient(transport);
    }

    private void populateData() throws Exception {
        System.out.printf("Populating data (%d) for %s client...\n", indexCount, clientType);

        for (String indexName : logicalIndexNames) {
            client.indices().create(new CreateIndexRequest.Builder().index(indexName).build());

            for (int i = 0; i < DOC_COUNT_PER_INDEX; i++) {
                Map<String, String> doc = new HashMap<>();
                doc.put("doc_id", "doc-" + i);
                doc.put("payload", UUID.randomUUID().toString());
                client.index(b -> b.index(indexName).document(doc));
            }
        }
        client.indices().refresh(r -> r.index("*"));
        System.out.println("Data population complete.");
    }

    @TearDown(Level.Trial)
    public void teardown() {
        System.out.println("--- Tearing down... ---");

        if (client != null) {
            try {
                client._transport().close();
            } catch (IOException e) {
                System.err.println("Error closing client transport: " + e.getMessage());
            }
        }

        if (opensearch != null && opensearch.isRunning()) {
            opensearch.stop();
        }
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
        System.out.println("--- Teardown complete. ---");
    }

    @Benchmark
    public SearchResponse<Map> randomSearch() throws IOException {
        if (setupException != null) {
            return null;
        }

        String targetIndex = logicalIndexNames.get(random.nextInt(indexCount));

        SearchRequest request = new SearchRequest.Builder()
                .index(targetIndex)
                .query(q -> q.matchAll(m -> m))
                .size(DOC_COUNT_PER_INDEX)
                .build();

        return client.search(request, Map.class);
    }
}
