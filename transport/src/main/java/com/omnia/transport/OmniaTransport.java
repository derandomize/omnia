package com.omnia.transport;

import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.Transport;
import org.opensearch.client.transport.TransportOptions;

import com.omnia.sdk.OmniaSDK;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
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
        Query combinedQuery = (Query) mapper.executeQuery(request);
        if (combinedQuery == null) {
            try {
                return delegate.performRequest(request, customEndpoint, options);
            } catch (OpenSearchException e) {
                if (Objects.equals(e.error().type(), "resource_already_exists_exception")) {
                    return null;
                }
                throw new OpenSearchException(e.response());
            }

        }
        List<String> Indexes = customEndpoint.getIndex(endpoint.requestUrl(request));
        for (var x : Indexes) {
            combinedQuery = sdk.addIndexFilter(combinedQuery, x);
        }
        try {
            mapper.updatePrivateFields(request, combinedQuery);
            return delegate.performRequest(request, customEndpoint, options);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (OpenSearchException e) {
            if (Objects.equals(e.error().type(), "resource_already_exists_exception")) {
                return null;
            }
            throw new OpenSearchException(e.response());
        }
    }

    @Override
    public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, @Nullable TransportOptions options) {
        final OmniaEndpoint<RequestT, ResponseT, ErrorT> customEndpoint = new OmniaEndpoint<>(endpoint, sdk);
        QueryMapper mapper = new QueryMapper();
        Query combinedQuery = (Query) mapper.executeQuery(request);
        if (combinedQuery == null) {
            try {
                return delegate.performRequestAsync(request, customEndpoint, options);
            } catch (OpenSearchException e) {
                if (Objects.equals(e.error().type(), "resource_already_exists_exception")) {
                    return null;
                }
                throw new OpenSearchException(e.response());
            }
        }
        List<String> Indexes = customEndpoint.getIndex(endpoint.requestUrl(request));
        for (var x : Indexes) {
            combinedQuery = sdk.addIndexFilter(combinedQuery, x);
        }
        try {
            mapper.updatePrivateFields(request, combinedQuery);
            return delegate.performRequestAsync(request, customEndpoint, options);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (OpenSearchException e) {
            if (Objects.equals(e.error().type(), "resource_already_exists_exception")) {
                return null;
            }
            throw new OpenSearchException(e.response());
        }
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
