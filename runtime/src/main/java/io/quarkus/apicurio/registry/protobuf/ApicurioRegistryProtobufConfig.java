package io.quarkus.apicurio.registry.protobuf;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration for Apicurio Registry Protobuf extension.
 */
@ConfigMapping(prefix = "quarkus.apicurio-registry.protobuf")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface ApicurioRegistryProtobufConfig {

    /**
     * Whether to derive the Java class from the protobuf schema at runtime.
     * When true, avoids classloading issues by using schema metadata instead of
     * requiring the class to be on the application classpath.
     *
     * Default is true to avoid Quarkus classloader visibility issues.
     */
    @WithDefault("true")
    boolean deriveClass();

    /**
     * Whether to automatically register artifacts with the registry.
     */
    @WithDefault("true")
    boolean autoRegister();

    /**
     * The artifact resolver strategy to use.
     * Common values: TopicIdStrategy, RecordIdStrategy, SimpleTopicIdStrategy
     */
    @WithDefault("io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy")
    String artifactResolverStrategy();

    /**
     * Whether to find the latest artifact version.
     */
    @WithDefault("true")
    boolean findLatest();

    /**
     * Optional explicit group ID for artifacts.
     */
    Optional<String> explicitGroupId();
}
