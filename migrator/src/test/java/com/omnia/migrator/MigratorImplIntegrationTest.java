package com.omnia.migrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class MigratorImplIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Should create new commune successfully")
    void testNewCommune() throws Exception {
        // When
        CommuneId newCommuneId = migrator.newCommune();

        // Then
        assertThat(newCommuneId).isNotNull();
        assertThat(newCommuneId.value()).startsWith("commune_");

        // Verify commune exists in OpenSearch
        boolean exists = openSearchClient.indices()
                .exists(ExistsRequest.of(b -> b.index(newCommuneId.value())))
                .value();
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should count communes correctly")
    void testCountCommunes() throws Exception {
        // Given
        createTestCommune("commune_test1");
        createTestCommune("commune_test2");

        // When
        long count = migrator.countCommunes();

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Should scan and identify overflown communes")
    void testScanOverflownCommunes() throws Exception {
        // Given
        String communeId = "commune_overflow_test";
        createTestCommune(communeId);

        // Add documents exceeding threshold (100)
        for (int i = 0; i < 150; i++) {
            addDocumentToCommune(communeId, "test_index", "doc_" + i, "content " + i);
        }

        // When
        List<CommuneId> overflownCommunes = migrator.scan();

        // Then
        assertThat(overflownCommunes).hasSize(1);
        assertThat(overflownCommunes.get(0).value()).isEqualTo(communeId);
    }

    @Test
    @DisplayName("Should not identify communes below threshold as overflown")
    void testScanNormalCommunes() throws Exception {
        // Given
        String communeId = "commune_normal_test";
        createTestCommune(communeId);

        // Add documents below threshold
        for (int i = 0; i < 50; i++) {
            addDocumentToCommune(communeId, "test_index", "doc_" + i, "content " + i);
        }

        // When
        List<CommuneId> overflownCommunes = migrator.scan();

        // Then
        assertThat(overflownCommunes).isEmpty();
    }

    @Test
    @DisplayName("Should transfer index between communes successfully")
    void testTransfer() throws Exception {
        // Given
        String sourceCommuneId = "commune_source";
        String targetCommuneId = "commune_target";
        String indexId = "test_index_transfer";

        createTestCommune(sourceCommuneId);
        createTestCommune(targetCommuneId);

        // Add documents to source commune
        for (int i = 0; i < 10; i++) {
            addDocumentToCommune(sourceCommuneId, indexId, "doc_" + i, "content " + i);
        }

        // Add index to commune mapping
        addIndexToCommuneMapping(indexId, sourceCommuneId);

        // When
        migrator.transfer(new CommuneId(sourceCommuneId), new CommuneId(targetCommuneId), new IndexId(indexId));

        // Then
        // Verify documents moved to target commune
        Thread.sleep(2000);
        long targetCount = openSearchClient.count(CountRequest.of(b -> b
                .index(targetCommuneId)
                .query(q -> q.term(t -> t.field("real_index").value(FieldValue.of(indexId))))
        )).count();
        assertThat(targetCount).isEqualTo(10);

        // Verify documents removed from source commune
        long sourceCount = openSearchClient.count(CountRequest.of(b -> b
                .index(sourceCommuneId)
                .query(q -> q.term(t -> t.field("real_index").value(FieldValue.of(indexId))))
        )).count();
        assertThat(sourceCount).isEqualTo(0);

        // Verify database mapping updated
        try (var connection = postgres.createConnection("")) {
            var result = connection.createStatement().executeQuery(
                    String.format("SELECT commune FROM index_to_commune WHERE \"index\" = '%s'", indexId));
            assertThat(result.next()).isTrue();
            assertThat(result.getString("commune")).isEqualTo(targetCommuneId);
        }
    }

    @Test
    @DisplayName("Should migrate overflown commune successfully")
    void testMigrate() throws Exception {
        // Given
        String communeId = "commune_migrate_test";
        createTestCommune(communeId);

        // Create multiple indexes with documents exceeding threshold
        String index1 = "migrate_index_1";
        String index2 = "migrate_index_2";

        // Add documents for first index
        for (int i = 0; i < 75; i++) {
            addDocumentToCommune(communeId, index1, "doc1_" + i, "content " + i);
        }

        // Add documents for second index
        for (int i = 0; i < 75; i++) {
            addDocumentToCommune(communeId, index2, "doc2_" + i, "content " + i);
        }

        // Add index mappings
        addIndexToCommuneMapping(index1, communeId);
        addIndexToCommuneMapping(index2, communeId);

        long initialCommuneCount = migrator.countCommunes();

        // When
        migrator.migrate(new CommuneId(communeId));

        // Then
        // Should have created a new commune
        long finalCommuneCount = migrator.countCommunes();
        assertThat(finalCommuneCount).isEqualTo(initialCommuneCount + 1);

        // Total documents should remain the same but distributed
        long totalDocsAfter = openSearchClient.count(CountRequest.of(b -> b.index("commune_*"))).count();
        assertThat(totalDocsAfter).isEqualTo(150);
    }

    @Test
    @DisplayName("Should not migrate commune below threshold")
    void testMigrateSkipsSmallCommune() throws Exception {
        // Given
        String communeId = "commune_small_test";
        createTestCommune(communeId);

        // Add documents below threshold
        for (int i = 0; i < 50; i++) {
            addDocumentToCommune(communeId, "small_index", "doc_" + i, "content " + i);
        }

        long initialCommuneCount = migrator.countCommunes();

        // When
        migrator.migrate(new CommuneId(communeId));

        // Then
        // Should not create new commune
        long finalCommuneCount = migrator.countCommunes();
        assertThat(finalCommuneCount).isEqualTo(initialCommuneCount);
    }

    @Test
    @DisplayName("Should migrate all overflown communes")
    void testMigrateAll() throws Exception {
        // Given
        String commune1 = "commune_overflow_1";
        String commune2 = "commune_overflow_2";
        String commune3 = "commune_normal";

        createTestCommune(commune1);
        createTestCommune(commune2);
        createTestCommune(commune3);

        // Make commune1 and commune2 overflow
        for (int i = 0; i < 120; i++) {
            addDocumentToCommune(commune1, "index1", "doc1_" + i, "content " + i);
            addDocumentToCommune(commune2, "index2", "doc2_" + i, "content " + i);
        }

        // Keep commune3 normal
        for (int i = 0; i < 30; i++) {
            addDocumentToCommune(commune3, "index3", "doc3_" + i, "content " + i);
        }

        // Add mappings
        addIndexToCommuneMapping("index1", commune1);
        addIndexToCommuneMapping("index2", commune2);
        addIndexToCommuneMapping("index3", commune3);

        long initialCommuneCount = migrator.countCommunes();

        // When
        migrator.migrateAll();

        // Then
        // Should have created new communes for the 2 overflown ones
        long finalCommuneCount = migrator.countCommunes();
        assertThat(finalCommuneCount).isGreaterThanOrEqualTo(initialCommuneCount + 2);
    }

    @Test
    @DisplayName("Should handle transfer of non-existent index gracefully")
    void testTransferNonExistentIndex() throws Exception {
        // Given
        String sourceCommuneId = "commune_source_empty";
        String targetCommuneId = "commune_target_empty";

        createTestCommune(sourceCommuneId);
        createTestCommune(targetCommuneId);

        // When & Then
        assertThatCode(() -> {
            migrator.transfer(
                    new CommuneId(sourceCommuneId),
                    new CommuneId(targetCommuneId),
                    new IndexId("non_existent_index")
            );
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should preserve document structure during transfer")
    void testTransferPreservesDocumentStructure() throws Exception {
        // Given
        String sourceCommuneId = "commune_structure_source";
        String targetCommuneId = "commune_structure_target";
        String indexId = "structure_test_index";

        createTestCommune(sourceCommuneId);
        createTestCommune(targetCommuneId);

        // Add a document with specific structure
        Map<String, Object> originalDoc = new HashMap<>();
        originalDoc.put("real_index", indexId);
        originalDoc.put("omnia_id", "structure_test_doc");
        originalDoc.put("title", "Test Document");
        originalDoc.put("tags", List.of("tag1", "tag2"));
        originalDoc.put("metadata", Map.of("author", "Test Author", "version", 1));

        openSearchClient.index(IndexRequest.of(b -> b
                .index(sourceCommuneId)
                .id("structure_test_doc")
                .document(originalDoc)
                .refresh(Refresh.True)
        ));

        addIndexToCommuneMapping(indexId, sourceCommuneId);

        // When
        migrator.transfer(new CommuneId(sourceCommuneId), new CommuneId(targetCommuneId), new IndexId(indexId));

        // Then
        Thread.sleep(2000);
        var searchResponse = openSearchClient.search(SearchRequest.of(b -> b
                .index(targetCommuneId)
                .query(q -> q.term(t -> t.field("omnia_id").value(FieldValue.of("structure_test_doc"))))
        ), Map.class);

        assertThat(searchResponse.hits().hits()).hasSize(1);
        Map<String, Object> transferredDoc = searchResponse.hits().hits().get(0).source();

        assertThat(transferredDoc.get("title")).isEqualTo("Test Document");
        assertThat(transferredDoc.get("real_index")).isEqualTo(indexId);
    }
}
