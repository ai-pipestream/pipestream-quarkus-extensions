package io.quarkus.apicurio.registry.protobuf.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "quarkus.apicurio-registry.protobuf")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface ApicurioRegistryProtobufBuildTimeConfig {

    /**
     * Dev Services configuration.
     */
    DevServicesConfig devservices();

    interface DevServicesConfig {
        /**
         * If Dev Services for Apicurio Registry has been explicitly enabled or disabled.
         */
        Optional<Boolean> enabled();

        /**
         * The Apicurio Registry image to use.
         */
        Optional<String> imageName();

        /**
         * Optional fixed port the dev service will listen to.
         * If not defined, the port will be chosen randomly.
         */
        Optional<Integer> port();

        /**
         * Indicates if the Apicurio Registry instance managed by Quarkus Dev Services is shared.
         */
        @WithDefault("true")
        boolean shared();

        /**
         * The value of the quarkus-dev-service-apicurio-registry label.
         */
        @WithDefault("apicurio-registry")
        String serviceName();

        /**
         * Environment variables that are passed to the container.
         */
        Map<String, String> containerEnv();
    }
}
