package io.quarkus.apicurio.registry.protobuf.deployment;

import io.quarkus.deployment.IsProduction;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.DockerStatusBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.console.ConsoleInstalledBuildItem;
import io.quarkus.deployment.console.StartupLogCompressor;
import io.quarkus.deployment.dev.devservices.DevServicesConfig;
import io.quarkus.deployment.logging.LoggingSetupBuildItem;
import io.quarkus.runtime.LaunchMode;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Starts Apicurio Registry as a Dev Service for the Protobuf extension.
 *
 * <p>
 * This processor handles the automatic provisioning of an Apicurio Registry
 * container
 * when running in dev or test mode. It ensures that a registry is available for
 * Protobuf schema registration and lookup.
 * </p>
 *
 * <h3>Features</h3>
 * <ul>
 * <li><strong>Automatic Startup:</strong> Starts an Apicurio Registry container
 * if no URL is configured.</li>
 * <li><strong>Version Support:</strong> Defaults to Apicurio Registry v3
 * ({@code apicurio/apicurio-registry:3.1.4}).</li>
 * <li><strong>Configuration Injection:</strong> Automatically configures the
 * application to use the started registry.</li>
 * <li><strong>Container Sharing:</strong> Supports sharing the registry
 * container across multiple Quarkus applications.</li>
 * </ul>
 */
@BuildSteps(onlyIfNot = IsProduction.class, onlyIf = DevServicesConfig.Enabled.class)
public class DevServicesApicurioRegistryProtobufProcessor {

    private static final Logger log = Logger.getLogger(DevServicesApicurioRegistryProtobufProcessor.class);

    private static final String DEV_SERVICE_NAME = "apicurio-registry-protobuf";
    private static final int APICURIO_REGISTRY_PORT = 8080;
    private static final String APICURIO_REGISTRY_URL_CONFIG = "mp.messaging.connector.smallrye-kafka.apicurio.registry.url";
    private static final String DEV_SERVICE_LABEL = "quarkus-dev-service-apicurio-registry-protobuf";
    private static final String DEFAULT_IMAGE = "apicurio/apicurio-registry:3.1.4";

    // Container state for lifecycle management
    static volatile ApicurioRegistryContainer runningContainer;
    static volatile Map<String, String> runningConfig;
    static volatile ApicurioRegistryDevServiceCfg cfg;
    static volatile boolean first = true;

    @BuildStep
    public DevServicesResultBuildItem startApicurioRegistryDevService(
            LaunchModeBuildItem launchMode,
            DockerStatusBuildItem dockerStatusBuildItem,
            ApicurioRegistryProtobufBuildTimeConfig config,
            Optional<ConsoleInstalledBuildItem> consoleInstalledBuildItem,
            CuratedApplicationShutdownBuildItem closeBuildItem,
            LoggingSetupBuildItem loggingSetupBuildItem,
            DevServicesConfig devServicesConfig) {

        ApicurioRegistryDevServiceCfg configuration = getConfiguration(config.devservices());

        if (runningContainer != null) {
            boolean restartRequired = !configuration.equals(cfg);
            if (!restartRequired) {
                return DevServicesResultBuildItem.discovered()
                        .name(DEV_SERVICE_NAME)
                        .containerId(runningContainer.getContainerId())
                        .config(runningConfig)
                        .build();
            }
            shutdownApicurioRegistry();
            cfg = null;
        }

        StartupLogCompressor compressor = new StartupLogCompressor(
                (launchMode.isTest() ? "(test) " : "") + "Apicurio Registry Protobuf Dev Services Starting:",
                consoleInstalledBuildItem, loggingSetupBuildItem);
        try {
            StartResult result = startApicurioRegistry(dockerStatusBuildItem, configuration, launchMode,
                    devServicesConfig.timeout());
            compressor.close();

            if (result == null) {
                return null;
            }

            runningContainer = result.container;
            runningConfig = result.config;
            cfg = configuration;

            log.infof("Dev Services for Apicurio Registry (Protobuf) started. The registry is available at %s",
                    runningConfig.get(APICURIO_REGISTRY_URL_CONFIG));

            if (first) {
                first = false;
                Runnable closeTask = () -> {
                    shutdownApicurioRegistry();
                    first = true;
                    runningContainer = null;
                    runningConfig = null;
                    cfg = null;
                };
                closeBuildItem.addCloseTask(closeTask, true);
            }

            return DevServicesResultBuildItem.discovered()
                    .name(DEV_SERVICE_NAME)
                    .containerId(runningContainer.getContainerId())
                    .config(runningConfig)
                    .build();

        } catch (Throwable t) {
            compressor.closeAndDumpCaptured();
            throw new RuntimeException(t);
        }
    }

    private Map<String, String> getRegistryUrlConfigs(String baseUrl) {
        // Use v3 API endpoint for Apicurio v3
        return Map.of(APICURIO_REGISTRY_URL_CONFIG, baseUrl + "/apis/registry/v3");
    }

    private void shutdownApicurioRegistry() {
        if (runningContainer != null) {
            try {
                runningContainer.stop();
            } catch (Throwable e) {
                log.error("Failed to stop Apicurio Registry", e);
            } finally {
                runningContainer = null;
                runningConfig = null;
            }
        }
    }

    private StartResult startApicurioRegistry(
            DockerStatusBuildItem dockerStatusBuildItem,
            ApicurioRegistryDevServiceCfg config,
            LaunchModeBuildItem launchMode,
            Optional<Duration> timeout) {

        if (!config.devServicesEnabled) {
            log.debug("Not starting dev services for Apicurio Registry, as it has been disabled in the config.");
            return null;
        }

        if (isPropertySet(APICURIO_REGISTRY_URL_CONFIG)) {
            log.debug("Not starting dev services for Apicurio Registry, " + APICURIO_REGISTRY_URL_CONFIG
                    + " is configured.");
            return null;
        }

        if (!hasKafkaChannelWithoutRegistry()) {
            log.debug(
                    "Not starting dev services for Apicurio Registry, all the channels have a registry URL configured.");
            return null;
        }

        if (!dockerStatusBuildItem.isContainerRuntimeAvailable()) {
            log.warn("Docker isn't working, please run Apicurio Registry yourself.");
            return null;
        }

        ApicurioRegistryContainer container = new ApicurioRegistryContainer(
                DockerImageName.parse(config.imageName),
                config.fixedExposedPort,
                launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT ? config.serviceName : null);
        timeout.ifPresent(container::withStartupTimeout);
        container.withEnv(config.containerEnv);
        container.start();

        return new StartResult(container, getRegistryUrlConfigs(container.getUrl()));
    }

    private boolean isPropertySet(String propertyName) {
        Config config = ConfigProvider.getConfig();
        return config.getOptionalValue(propertyName, String.class)
                .filter(s -> !s.isEmpty())
                .isPresent();
    }

    private boolean hasKafkaChannelWithoutRegistry() {
        Config config = ConfigProvider.getConfig();
        for (String name : config.getPropertyNames()) {
            boolean isIncoming = name.startsWith("mp.messaging.incoming.");
            boolean isOutgoing = name.startsWith("mp.messaging.outgoing.");
            boolean isConnector = name.endsWith(".connector");
            boolean isKafka = isConnector
                    && "smallrye-kafka".equals(config.getOptionalValue(name, String.class).orElse("ignored"));
            if ((isIncoming || isOutgoing) && isKafka) {
                String registryUrlProp = name.replace(".connector", ".apicurio.registry.url");
                if (!isPropertySet(registryUrlProp)) {
                    return true;
                }
            }
        }
        return false;
    }

    private ApicurioRegistryDevServiceCfg getConfiguration(
            ApicurioRegistryProtobufBuildTimeConfig.DevServicesConfig cfg) {
        return new ApicurioRegistryDevServiceCfg(cfg);
    }

    /**
     * Result of starting the Apicurio Registry container.
     */
    private record StartResult(ApicurioRegistryContainer container, Map<String, String> config) {
    }

    private static final class ApicurioRegistryDevServiceCfg {
        private final boolean devServicesEnabled;
        private final String imageName;
        private final Integer fixedExposedPort;
        private final boolean shared;
        private final String serviceName;
        private final Map<String, String> containerEnv;

        public ApicurioRegistryDevServiceCfg(ApicurioRegistryProtobufBuildTimeConfig.DevServicesConfig config) {
            this.devServicesEnabled = config.enabled().orElse(true);
            this.imageName = config.imageName().orElse(DEFAULT_IMAGE);
            this.fixedExposedPort = config.port().orElse(0);
            this.shared = config.shared();
            this.serviceName = config.serviceName();
            this.containerEnv = config.containerEnv();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            ApicurioRegistryDevServiceCfg that = (ApicurioRegistryDevServiceCfg) o;
            return devServicesEnabled == that.devServicesEnabled
                    && Objects.equals(imageName, that.imageName)
                    && Objects.equals(fixedExposedPort, that.fixedExposedPort)
                    && shared == that.shared
                    && Objects.equals(serviceName, that.serviceName)
                    && Objects.equals(containerEnv, that.containerEnv);
        }

        @Override
        public int hashCode() {
            return Objects.hash(devServicesEnabled, imageName, fixedExposedPort, shared, serviceName, containerEnv);
        }
    }

    private static final class ApicurioRegistryContainer extends GenericContainer<ApicurioRegistryContainer> {
        private final int fixedExposedPort;

        private ApicurioRegistryContainer(DockerImageName dockerImageName, int fixedExposedPort, String serviceName) {
            super(dockerImageName);
            this.fixedExposedPort = fixedExposedPort;

            if (serviceName != null) {
                withLabel(DEV_SERVICE_LABEL, serviceName);
            }
            withEnv("QUARKUS_PROFILE", "prod");
        }

        @Override
        protected void configure() {
            super.configure();
            if (fixedExposedPort > 0) {
                addFixedExposedPort(fixedExposedPort, APICURIO_REGISTRY_PORT);
            } else {
                addExposedPorts(APICURIO_REGISTRY_PORT);
            }
        }

        public String getUrl() {
            return String.format("http://%s:%s", getHost(), getMappedPort(APICURIO_REGISTRY_PORT));
        }
    }
}
