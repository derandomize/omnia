package com.omnia.sdk;

import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;


import java.io.File;

public interface OmniaSDK {
    /**
     * Creates OmniaSDK instance from config file.
     * Throws exception if failed to connect to Postgresql
     *
     * @param config Config file
     * @return Instance of OmniaSDK with this config file
     */
    OmniaSDK fromConfig(File config);

    /**
     * Transforms an index ID to commune ID
     * @param indexId The original index identifier to transform
     * @return Transformed commune ID
     */
    String transformIndexId(String indexId);

    /**
     * Returns filter field where original index will be stored
     * @return Field in OpenSearch
     */
    String getFilterField();

    /**
     * Retrieves the base filter query for the specified index
     * @param indexId Target index to get filter for
     * @return  {@link Query} containing index-specific filter
     */
    Query getIndexFilter(String indexId);

    /**
     * Combines an existing query with index-specific filter
     * @param query Base query to add filtering to
     * @param indexId Target index to get filter from
     * @return New {@link Query} that combines both input query and index filter
     */
    default Query addIndexFilter(Query query, String indexId) {
        Query filter = getIndexFilter(indexId);
        BoolQuery boolQuery = new BoolQuery.Builder().must(query).must(filter).build();
        return new Query.Builder().bool(boolQuery).build();
    }

    /**
     * Creates a pre-configured search request builder for the specified index
     * IMPORTANT: to add query to builder use .query(sdk.addIndexFilter(userQuery, indexId))
     * instead of .query(userQuery)
     * @param indexId Target index identifier to build request for
     * @return {@link SearchRequest.Builder} initialized with commune index and filter
     */
    default SearchRequest.Builder createSearchRequestBuilder(String indexId) {
        String communeIndexId = transformIndexId(indexId);
        Query filter = getIndexFilter(communeIndexId);
        return new SearchRequest.Builder()
                .index(communeIndexId)
                .query(filter);
    }
}
