package ai.pipestream.quarkus.dynamicgrpc;

import io.smallrye.mutiny.Uni;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.Service;
import io.smallrye.stork.api.ServiceDefinition;
import io.smallrye.stork.api.ServiceInstance;
import io.smallrye.stork.spi.config.SimpleServiceConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manages dynamic definition of Stork services backed by Consul discovery
 * and provides access to discovered ServiceInstance lists.
 */
@ApplicationScoped
public class ServiceDiscoveryManager {

    /**
     * Default constructor for CDI frameworks.
     */
    public ServiceDiscoveryManager() {
    }

    private static final Logger LOG = Logger.getLogger(ServiceDiscoveryManager.class);

    @ConfigProperty(name = "quarkus.dynamic-grpc.consul.host", defaultValue = "localhost")
    String consulHost;

    @ConfigProperty(name = "quarkus.dynamic-grpc.consul.port", defaultValue = "8500")
    String consulPort;

    @ConfigProperty(name = "quarkus.dynamic-grpc.consul.refresh-period", defaultValue = "10s")
    String consulRefreshPeriod;

    @ConfigProperty(name = "quarkus.dynamic-grpc.consul.use-health-checks", defaultValue = "false")
    boolean consulUseHealthChecks;

    /**
     * Ensures a service is defined in Stork for discovery using the same Consul application name.
     *
     * @param serviceName the logical service name to define and discover via Consul
     * @return a Uni that completes when the service is defined
     */
    public Uni<Void> ensureServiceDefined(String serviceName) {
        return ensureServiceDefinedFor(serviceName, serviceName);
    }

    /**
     * Ensures a service is defined in Stork under the given storkServiceName but discovers
     * Consul instances registered under consulApplicationName.
     *
     * @param storkServiceName      the logical service name as it will be known to Stork
     * @param consulApplicationName the Consul service name to query for instances
     * @return a Uni that completes when the service is defined
     */
    public Uni<Void> ensureServiceDefinedFor(String storkServiceName, String consulApplicationName) {
        Optional<Service> existingService = Stork.getInstance().getServiceOptional(storkServiceName);
        if (existingService.isPresent()) {
            LOG.debugf("Service %s already defined in Stork", storkServiceName);
            return Uni.createFrom().voidItem();
        }

        LOG.infof("Defining new Stork service for dynamic discovery: %s (consul application: %s)", storkServiceName, consulApplicationName);

        Map<String, String> consulParams = new HashMap<>();
        consulParams.put("consul-host", consulHost);
        consulParams.put("consul-port", consulPort);
        consulParams.put("refresh-period", consulRefreshPeriod);
        consulParams.put("use-health-checks", String.valueOf(consulUseHealthChecks));

        final Config config = ConfigProvider.getConfig();
        final String overrideKey = "quarkus.dynamic-grpc.consul.application-name." + storkServiceName;
        final String applicationToDiscover = config.getOptionalValue(overrideKey, String.class)
                .orElse(consulApplicationName);
        consulParams.put("application", applicationToDiscover);

        var consulConfig = new SimpleServiceConfig.SimpleServiceDiscoveryConfig("consul", consulParams);
        ServiceDefinition definition = ServiceDefinition.of(consulConfig);

        try {
            Stork.getInstance().defineIfAbsent(storkServiceName, definition);
            LOG.infof("Successfully defined Stork service: %s with Consul discovery", storkServiceName);

            LOG.debugf("Stork will look for Consul service named: %s (overrideKey=%s, use-health-checks=%s)",
                    applicationToDiscover, overrideKey, String.valueOf(consulUseHealthChecks));

            return Uni.createFrom().voidItem();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to define Stork service: %s", storkServiceName);
            return Uni.createFrom().failure(e);
        }
    }

    /**
     * Gets service instances for a given service name.
     *
     * @param serviceName the service name to look up
     * @return a Uni containing the list of service instances
     */
    public Uni<List<ServiceInstance>> getServiceInstances(String serviceName) {
        try {
            Service service = Stork.getInstance().getService(serviceName);
            return service.getInstances();
        } catch (Exception e) {
            LOG.errorf(e, "Failed to get service instances for %s", serviceName);
            return Uni.createFrom().failure(e);
        }
    }
}
