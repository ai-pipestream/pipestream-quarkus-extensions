package io.quarkus.apicurio.registry.protobuf.deployment;

import io.quarkus.apicurio.registry.protobuf.ApicurioRegistryProtobufConfig;
import io.quarkus.apicurio.registry.protobuf.ApicurioRegistryProtobufRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

/**
 * Build-time processor for Apicurio Registry Protobuf extension.
 *
 * This processor:
 * 1. Registers the extension feature
 * 2. Configures default properties for Protobuf serde
 * 3. Registers classes for reflection (native image support)
 */
class ApicurioRegistryProtobufProcessor {

    private static final String FEATURE = "apicurio-registry-protobuf";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem enableSslInNative() {
        return new ExtensionSslNativeSupportBuildItem(FEATURE);
    }

    /**
     * Configure default Kafka properties for Protobuf serde.
     * These can be overridden by application.properties.
     */
    @BuildStep
    void configureDefaultProperties(BuildProducer<RunTimeConfigurationDefaultBuildItem> defaults) {
        // Enable derive-class-from-schema to avoid Quarkus classloader issues
        defaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "mp.messaging.connector.smallrye-kafka.apicurio.protobuf.derive.class", "true"));

        // Auto-register artifacts by default
        defaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "mp.messaging.connector.smallrye-kafka.apicurio.registry.auto-register", "true"));

        // Use SimpleTopicIdStrategy by default
        defaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "mp.messaging.connector.smallrye-kafka.apicurio.registry.artifact-resolver-strategy",
                "io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy"));

        // Find latest artifact version
        defaults.produce(new RunTimeConfigurationDefaultBuildItem(
                "mp.messaging.connector.smallrye-kafka.apicurio.registry.find-latest", "true"));
    }

    /**
     * Register Apicurio and Protobuf classes for reflection (needed for native image).
     */
    @BuildStep
    void registerForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        // Apicurio Protobuf serde classes
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "io.apicurio.registry.serde.protobuf.ProtobufSerializer",
                "io.apicurio.registry.serde.protobuf.ProtobufDeserializer",
                "io.apicurio.registry.serde.protobuf.ProtobufSerdeHeaders",
                "io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy",
                "io.apicurio.registry.serde.strategy.TopicIdStrategy",
                "io.apicurio.registry.serde.strategy.RecordIdStrategy"
        ).methods().fields().build());

        // Google Protobuf classes
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "com.google.protobuf.GeneratedMessageV3",
                "com.google.protobuf.DynamicMessage",
                "com.google.protobuf.Descriptors$Descriptor",
                "com.google.protobuf.Descriptors$FileDescriptor"
        ).methods().fields().build());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void configureRuntime(ApicurioRegistryProtobufRecorder recorder) {
        recorder.configureProtobufSerde();
    }
}
