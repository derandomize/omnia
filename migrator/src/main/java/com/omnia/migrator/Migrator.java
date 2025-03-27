package com.omnia.migrator;

import com.omnia.common.config.AppConfig;

import java.util.List;

public interface Migrator extends AutoCloseable {
    /**
     * Gets Migrator instance from AppConfig
     *
     * @param config config
     * @return Migrator
     */
    static Migrator getInstance(AppConfig config) {
        return null;
    }

    /**
     * Returns config of this file
     *
     * @return app config
     */
    AppConfig getConfig();

    /**
     * Migrates extensive data from commune
     * does nothing if id less than current Threshold
     *
     * @param id Id of commune with extensive data
     */
    void migrate(CommuneId id);

    /**
     * Migrates all overflown communes
     */
    default void migrateAll() {
        List<CommuneId> ids = scan();
        for (CommuneId id : ids) {
            migrate(id);
        }
    }

    /**
     * Transfers index from one commune to other
     *
     * @param from Id of commune where index is located
     * @param to   Id of commune where to move index
     * @param id   Id of index to move
     */
    void transfer(CommuneId from, CommuneId to, IndexId id);

    /**
     * Creates empty commune
     *
     * @return Created communeId
     */
    CommuneId newCommune();

    /**
     * Searches overflown communes.
     * Also updates Threshold if it was changed
     *
     * @return List of overflown communes indexes
     */
    List<CommuneId> scan();

    /**
     * Counts number of communes in openSearch
     *
     * @return number of communes
     */
    long countCommunes();
}
