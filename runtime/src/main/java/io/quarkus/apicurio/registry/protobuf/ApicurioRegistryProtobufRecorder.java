package io.quarkus.apicurio.registry.protobuf;

import io.quarkus.runtime.annotations.Recorder;

/**
 * Recorder for Apicurio Registry Protobuf configuration.
 * This runs at runtime to configure the Protobuf serializers/deserializers.
 */
@Recorder
public class ApicurioRegistryProtobufRecorder {

    /**
     * Called at runtime to perform any necessary initialization.
     */
    public void configureProtobufSerde() {
        // Configuration is primarily done via application.properties
        // This recorder can be extended for programmatic configuration if needed
    }
}
