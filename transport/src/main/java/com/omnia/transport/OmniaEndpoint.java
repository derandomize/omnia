package com.omnia.transport;


import org.jooq.Null;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.UnexpectedJsonEventException;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.*;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.transport.Endpoint;

import com.omnia.sdk.OmniaSDK;
import org.opensearch.client.transport.JsonEndpoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OmniaEndpoint<RequestT, ResponseT, ErrorT> implements Endpoint<RequestT, ResponseT, ErrorT>, JsonEndpoint<RequestT, ResponseT, ErrorT> {
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
        List<String> splitedPath = List.of(endpoint.requestUrl(request).split("/"));
        List<String> Indecies = parseUrl(endpoint.requestUrl(request));
        StringBuilder answer = new StringBuilder("/" + String.join("%2C", Indecies));
        if (splitedPath.size() <= 2) {
            return answer.toString();
        }
        answer.append("/");
        for (int i = 2; i < splitedPath.size() - 1; i++) {
            answer.append(splitedPath.get(i)).append("/");
        }
        answer.append(splitedPath.getLast());
        return answer.toString();
    }

    @Override
    public Map<String, String> queryParameters(RequestT request) {
        /*
        Map<String, String> a = endpoint.queryParameters(request);
        for(var x: a.keySet()){
            System.out.println(x+" "+a.get(x));
        }
        Map<String, String> params = endpoint.queryParameters(request);
        QueryMapper mapper = new QueryMapper();
        Query query = mapper.mapToQuery(params);
        Map<String, String> answer = new HashMap<>();
        List<String> parsePath = List.of(endpoint.requestUrl(request).split("/"));
        List<String> Indecies = List.of(parsePath.get(1).split("%2C"));
        for (var x : Indecies) {
            if(request instanceof SearchRequest) {
                query = sdk.addIndexFilter(query, x);
                // Проблема. Кажется, что endpoint.queryParameters не все параметры Query -> если добавлять в endpoint.queryParameters лишнее падаем с исключением от RestTransport
            }
        }

        mapper.queryToMap(query, answer);
        for(var x: answer.keySet()){
              System.out.println("Answer: "+x+" "+answer.get(x));
        }
        return answer;
         */
        return endpoint.queryParameters(request);
    }

    @Override
    public Map<String, String> headers(RequestT request) {
        if (endpoint instanceof JsonEndpoint<RequestT, ResponseT, ErrorT>) {
            return endpoint.headers(request);
        } else {
            throw new IllegalArgumentException("Expected JsonEndpooint");
        }
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

    public List<String> getIndex(String path) {
        List<String> parsePath = List.of(path.split("/"));
        if (parsePath.getFirst() == null) {
            throw new IllegalArgumentException();
        }
        return List.of(parsePath.get(1).split("%2C"));
    }

    private List<String> parseUrl(String path) {
        List<String> answer = new ArrayList<>();
        for (var x : getIndex(path)) {
            String newIndex = sdk.transformIndexId(x);
            if (newIndex == null) {
                answer.add(x);
                continue;
            }
            answer.add(newIndex);
        }
        return answer;
    }

    @Override
    public JsonpDeserializer<ResponseT> responseDeserializer() {
        if (endpoint instanceof JsonEndpoint<RequestT, ResponseT, ErrorT>) {
            return ((JsonEndpoint<RequestT, ResponseT, ErrorT>) endpoint).responseDeserializer();
        } else {
            throw new IllegalArgumentException("Expected JsonEndpooint");
        }
    }
}
