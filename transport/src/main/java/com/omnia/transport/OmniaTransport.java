package com.omnia.transport;

import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.Transport;
import org.opensearch.client.transport.TransportOptions;

import com.omnia.sdk.OmniaSDK;

import javax.annotation.Nullable;
import javax.crypto.SealedObject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OmniaTransport implements OpenSearchTransport {
    private final Transport delegate;
    private final OmniaSDK sdk;

    public OmniaTransport(Transport delegate, OmniaSDK sdk) {
        this.delegate = delegate;
        this.sdk = sdk;
    }

    @Override
    public <RequestT, ResponseT, ErrorT> ResponseT performRequest(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, @Nullable TransportOptions options) throws IOException {
        final OmniaEndpoint<RequestT, ResponseT, ErrorT> customEndpoint = new OmniaEndpoint<>(endpoint, sdk);
        QueryMapper mapper = new QueryMapper();
        if (request instanceof SearchRequest) {
            Query combinedQuery = ((SearchRequest) request).query();
            List<String> Indexes = customEndpoint.getIndex(endpoint.requestUrl(request));
            for (var x : Indexes) {
                combinedQuery = sdk.addIndexFilter(combinedQuery, x);
            }
            try {
                mapper.updatePrivateFields(request, combinedQuery);
                return delegate.performRequest(request, customEndpoint, options);
            } catch (IllegalAccessException |NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        return delegate.performRequest(request, customEndpoint, options);
    }

    @Override
    public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, @Nullable TransportOptions options) {
        final OmniaEndpoint<RequestT, ResponseT, ErrorT> customEndpoint = new OmniaEndpoint<>(endpoint, sdk);
        QueryMapper mapper = new QueryMapper();
        if (request instanceof SearchRequest) {
            Query combinedQuery = ((SearchRequest) request).query();
            List<String> Indexes = customEndpoint.getIndex(endpoint.requestUrl(request));
            for (var x : Indexes) {
                combinedQuery = sdk.addIndexFilter(combinedQuery, x);
            }
            try {
                mapper.updatePrivateFields(request, combinedQuery);
                return delegate.performRequestAsync(request, customEndpoint, options);
            } catch (IllegalAccessException |NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        return delegate.performRequestAsync(request, customEndpoint, options);
    }

    @Override
    public JsonpMapper jsonpMapper() {
        return delegate.jsonpMapper();
    }

    @Override
    public TransportOptions options() {
        return delegate.options();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
