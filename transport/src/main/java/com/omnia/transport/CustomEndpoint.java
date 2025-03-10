package com.omnia.transport;

//import com.omnia.sdk;

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.transport.Endpoint;

import java.util.Map;

public class CustomEndpoint<RequestT, ResponseT, ErrorT> implements Endpoint<RequestT, ResponseT, ErrorT> {
    Endpoint<RequestT, ResponseT, ErrorT> endpoint;

    public CustomEndpoint(Endpoint<RequestT, ResponseT, ErrorT> endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public String method(RequestT request) {
        return endpoint.method(request);
    }

    @Override
    public String requestUrl(RequestT request) {
        return transformIndexId(endpoint.requestUrl(request));
    }

    @Override
    public Map<String, String> queryParameters(RequestT request) {
        return addIndexFilter(endpoint.queryParameters(request), endpoint.requestUrl(request));
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

}
