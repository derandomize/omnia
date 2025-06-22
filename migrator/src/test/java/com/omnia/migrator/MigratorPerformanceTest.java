package com.omnia.migrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class MigratorPerformanceTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Should handle large commune migration efficiently")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testLargeCommuneMigration() throws Exception {
        // Given
        String communeId = "commune_large_test";
        createTestCommune(communeId);

        // Add a large number of documents
        int documentCount = 1000;
        for (int i = 0; i < documentCount; i++) {
            addDocumentToCommune(communeId, "large_index_" + (i % 5), "doc_" + i, "content " + i);
        }

        // Add index mappings
        for (int i = 0; i < 5; i++) {
            addIndexToCommuneMapping("large_index_" + i, communeId);
        }

        long startTime = System.currentTimeMillis();

        // When
        migrator.migrate(new CommuneId(communeId));

        // Then
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Should complete within reasonable time
        assertThat(duration).isLessThan(25000); // Less than 25 seconds

        // Verify migration was successful
        long totalCommunes = migrator.countCommunes();
        assertThat(totalCommunes).isGreaterThan(1);
    }

    @Test
    @DisplayName("Should handle multiple concurrent operations")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void testConcurrentOperations() throws Exception {
        // Given
        String commune1 = "commune_concurrent_1";
        String commune2 = "commune_concurrent_2";

        createTestCommune(commune1);
        createTestCommune(commune2);

        // Add documents to both communes
        for (int i = 0; i < 200; i++) {
            addDocumentToCommune(commune1, "concurrent_index_1", "doc1_" + i, "content " + i);
            addDocumentToCommune(commune2, "concurrent_index_2", "doc2_" + i, "content " + i);
        }

        addIndexToCommuneMapping("concurrent_index_1", commune1);
        addIndexToCommuneMapping("concurrent_index_2", commune2);

        // When - Perform multiple operations
        long startTime = System.currentTimeMillis();

        // Scan for overflown communes
        var overflownCommunes = migrator.scan();
        assertThat(overflownCommunes).hasSize(2);

        // Migrate all
        migrator.migrateAll();

        long endTime = System.currentTimeMillis();

        // Then
        assertThat(endTime - startTime).isLessThan(15000); // Less than 15 seconds
        assertThat(migrator.countCommunes()).isGreaterThan(2);
    }
}
