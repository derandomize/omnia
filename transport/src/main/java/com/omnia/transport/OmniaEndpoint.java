package com.omnia.transport;


import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.transport.Endpoint;

import com.omnia.sdk.OmniaSDK;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OmniaEndpoint<RequestT, ResponseT, ErrorT> implements Endpoint<RequestT, ResponseT, ErrorT> {
    private final Endpoint<RequestT, ResponseT, ErrorT> endpoint;
    private final OmniaSDK sdk;

    public OmniaEndpoint(Endpoint<RequestT, ResponseT, ErrorT> endpoint, OmniaSDK sdk) {
        this.endpoint = endpoint;
        this.sdk = sdk;
    }

    @Override
    public String method(RequestT request) {
        return endpoint.method(request);
    }

    @Override
    public String requestUrl(RequestT request) throws IllegalArgumentException {
        List<String> Indecies = parseUrl(endpoint.requestUrl(request));
        Indecies = Indecies.stream().map(index -> sdk.transformIndexId(index)).collect(Collectors.toList());
        return "/" + String.join("%2C", Indecies);
    }

    @Override
    public Map<String, String> queryParameters(RequestT request) {
        Map<String, String> params = endpoint.queryParameters(request);
        QueryMapper mapper = new QueryMapper();
        Query query = mapper.mapToQuery(params);
        Map<String, String> answer = new HashMap<>();
        query = sdk.addIndexFilter(query, endpoint.requestUrl(request));
        mapper.queryToMap(query, answer);
        return answer;
    }

    @Override
    public boolean hasRequestBody() {
        return endpoint.hasRequestBody();
    }

    @Override
    public boolean isError(int statusCode) {
        return endpoint.isError(statusCode);
    }

    @Override
    public JsonpDeserializer<ErrorT> errorDeserializer(int statusCode) {
        return endpoint.errorDeserializer(statusCode);
    }

    //Вероятно это не правда... я не уверен
    private List<String> parseUrl(String path) {
        List<String> parsePath = List.of(path.split("/"));
        if (parsePath.getFirst() == null) {
            throw new IllegalArgumentException();
        }
        return List.of(parsePath.getFirst().split("%2C"));
    }
}
