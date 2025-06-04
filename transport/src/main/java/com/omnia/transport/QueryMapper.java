package com.omnia.transport;

import org.opensearch.client.Request;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;

import java.lang.reflect.Field;
import java.util.Map;

public class QueryMapper {

    public Query mapToQuery(Map<String, String> params) {
        return Query.of(q -> q
                .bool(builder -> {
                    for (Map.Entry<String, String> entry : params.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        builder.filter(f -> f.term(t -> t
                                .field(key)
                                .value(v -> v.stringValue(value))
                        ));
                    }
                    return builder;
                })
        );
    }

    public void queryToMap(Query query, Map<String, String> result) {
        if (query.isTerm()) {
            TermQuery term = query.term();
            String value = term.value().stringValue();
            result.put(term.field(), value);
        } else if (query.isMatch()) {
            MatchQuery match = query.match();
            result.put(match.field(), match.query().stringValue());
        } else if (query.isBool()) {
            BoolQuery bool = query.bool();
            bool.must().forEach(q -> queryToMap(q, result));
            bool.should().forEach(q -> queryToMap(q, result));
            bool.filter().forEach(q -> queryToMap(q, result));
        }
    }

    public void updatePrivateFields(Object target, Query fieldValues)
            throws NoSuchFieldException, IllegalAccessException {
        Class<?> clazz = target.getClass();
        Field field = findField(clazz);
            if (field == null) {
                throw new NoSuchFieldException(
                        "Field '" + field.getName() + "' not found in class hierarchy");
            }
            try {
                field.setAccessible(true);
                field.set(target, fieldValues);
            } catch (SecurityException e) {
                throw new IllegalAccessException("Security manager blocked access to field: " + e.getMessage());
            }
        }

    private static Field findField(Class<?> clazz) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField("query");
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

}
