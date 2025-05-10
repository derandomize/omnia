package com.omnia.transport;

import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class QueryMapperTest<RequestT, ResponseT, ErrorT> {
    QueryMapper mapper = new QueryMapper();

    @Test
    void mapToQuery_simpleTest() {
        Map<String, String> params = new HashMap<>();
        params.put("name", "Alice");
        params.put("age", "18");
        Query query = mapper.mapToQuery(params);
        BoolQuery boolQuery = query.bool();
        assertEquals(2, boolQuery.filter().size());
        Query firstFilter = boolQuery.filter().get(0);
        assertTrue(firstFilter.isTerm());
        TermQuery firstTerm = firstFilter.term();
        assertEquals("name", firstTerm.field());
        assertEquals("Alice", firstTerm.value().stringValue());
        Query secondFilter = boolQuery.filter().get(1);
        assertTrue(secondFilter.isTerm());
        TermQuery secondTerm = secondFilter.term();
        assertEquals("age", secondTerm.field());
        assertEquals("18", secondTerm.value().stringValue());
    }

    @Test
    void mapToQuery_emptyTest() {
        Map<String, String> params = new HashMap<>();
        QueryMapper mapper = new QueryMapper();
        Query query = mapper.mapToQuery(params);
        assertTrue(query.isBool());
        assertTrue(query.bool().filter().isEmpty());
    }


    @Test
    void queryToMap_simpleBoolQueries() {
        QueryMapper mapper = new QueryMapper();
        Map<String, String> result = new HashMap<>();
        Query nestedBool = Query.of(b -> b.bool(bb -> bb
                .must(m -> m.term(t -> t.field("level").value(FieldValue.of("debug"))))
        ));
        Query mainQuery = Query.of(b -> b.bool(bb -> bb
                .must(nestedBool)
                .filter(f -> f.match(m -> m.field("type").query(FieldValue.of("log"))))
        ));

        mapper.queryToMap(mainQuery, result);
        assertEquals(2, result.size());
        assertEquals("debug", result.get("level"));
        assertEquals("log", result.get("type"));
    }

    @Test
    void queryToMap_MatchQueryTest() {
        MatchQuery matchQuery = new MatchQuery.Builder()
                .field("title")
                .query(q -> q.stringValue("OpenSearch"))
                .build();
        Query query = new Query.Builder()
                .match(matchQuery)
                .build();
        Map<String, String> resultMap = new HashMap<>();
        mapper.queryToMap(query, resultMap);
        assertEquals(1, resultMap.size());
        assertEquals("OpenSearch", resultMap.get("title"));
    }

    @Test
    void queryToMap_termQuerytest() {
        Query query = new Query.Builder()
                .term(t -> t
                        .field("title")
                        .value(v -> v.stringValue("OpenSearch"))
                )
                .build();

        Map<String, String> resultMap = new HashMap<>();
        mapper.queryToMap(query, resultMap);
        assertEquals(1, resultMap.size());
        assertEquals("OpenSearch", resultMap.get("title"));
    }
}
