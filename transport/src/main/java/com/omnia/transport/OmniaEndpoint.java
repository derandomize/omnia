package com.omnia.transport;


import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.transport.Endpoint;

import com.omnia.sdk.OmniaSDK;
import org.opensearch.client.transport.JsonEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
