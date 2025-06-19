package com.omnia.migrator;

import com.omnia.common.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

class MigratorConfigIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Should load configuration correctly")
    void testConfigurationLoading() {
        // When
        AppConfig config = migrator.getConfig();

        // Then
        assertThat(config).isNotNull();
        assertThat(config.getConfig().getThreshold()).isEqualTo(100L);
        assertThat(config.getConfig().getConfigTableName()).isEqualTo("omnia_config");
        assertThat(config.getConfig().getFeatureName()).isEqualTo("omnia_id");

        assertThat(config.getOpenSearch()).isNotNull();
        assertThat(config.getOpenSearch().getHost()).isEqualTo(opensearch.getHost());
        assertThat(config.getOpenSearch().getPort()).isEqualTo(opensearch.getFirstMappedPort());

        assertThat(config.getDatabase()).isNotNull();
        assertThat(config.getDatabase().getType()).isEqualTo("postgresql");
    }

    @Test
    @DisplayName("Should update threshold from database")
    void testThresholdUpdateFromDatabase() throws Exception {
        // Given - Update threshold in database
        try (var connection = postgres.createConnection("")) {
            connection.createStatement().execute(
                    "UPDATE omnia_config SET value = '200' WHERE key = 'threshold'"
            );
        }

        // When - Create new migrator instance (should read updated threshold)
        try (Migrator newMigrator = new MigratorImpl(testConfig)) {
            // Create a commune with documents between old and new threshold
            String communeId = "commune_threshold_test";
            createTestCommune(communeId);

            for (int i = 0; i < 150; i++) {
                addDocumentToCommune(communeId, "threshold_index", "doc_" + i, "content " + i);
            }

            // Then - Should not be considered overflown with new threshold
            var overflownCommunes = newMigrator.scan();
            assertThat(overflownCommunes).isEmpty(); // 150 < 200 (new threshold)
        }
    }
}
