package transport;

import org.opensearch.client.json.JsonpMapper;

//import com.omnia.sdk;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class CustomTransport implements  Transport {
    private final Transport delegate;
    public CustomTransport(Transport delegate){
        this.delegate = delegate;
    }
    @Override
    public <RequestT, ResponseT, ErrorT> ResponseT performRequest(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, @Nullable TransportOptions options) throws IOException {
        final CustomEndpoint<RequestT, ResponseT,ErrorT> customEndpoint = new CustomEndpoint<>(endpoint);
        return delegate.performRequest(request, customEndpoint,options);
    }

    @Override
    public <RequestT, ResponseT, ErrorT> CompletableFuture<ResponseT> performRequestAsync(RequestT request, Endpoint<RequestT, ResponseT, ErrorT> endpoint, @Nullable TransportOptions options) {
        final CustomEndpoint<RequestT, ResponseT, ErrorT> customEndpoint =  new CustomEndpoint<>(endpoint);
        return  delegate.performRequestAsync(request,customEndpoint,options);
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
