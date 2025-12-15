package ai.pipestream.apicurio.registry.protobuf.runtime;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigSourceInterceptorFactory;
import io.smallrye.config.RelocateConfigSourceInterceptor;

import java.util.Map;

/**
 * Interceptor to bridge 'apicurio.registry.url' to 'mp.messaging.connector.smallrye-kafka.apicurio.registry.url'.
 *
 * This allows users to set the simple 'apicurio.registry.url' property (e.g. via Dev Services or manually)
 * and have it automatically apply to the Kafka connector configuration.
 */
public class ApicurioRegistryRelocateInterceptor implements ConfigSourceInterceptorFactory {

    @Override
    public ConfigSourceInterceptor getInterceptor(final ConfigSourceInterceptorContext context) {
        // Map the "target" property (what the code asks for) to the "source" property (what the user set)
        return new RelocateConfigSourceInterceptor(Map.of(
                "mp.messaging.connector.smallrye-kafka.apicurio.registry.url", "apicurio.registry.url"
        ));
    }
}
