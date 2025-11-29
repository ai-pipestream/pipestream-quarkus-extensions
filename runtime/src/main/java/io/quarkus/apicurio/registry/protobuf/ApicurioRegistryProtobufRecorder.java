package io.quarkus.apicurio.registry.protobuf;

import io.quarkus.apicurio.registry.protobuf.runtime.ProtobufChannelConfigSource;
import io.quarkus.runtime.annotations.Recorder;

import java.util.Set;

/**
 * Recorder for Apicurio Registry Protobuf configuration.
 * This runs at STATIC_INIT to configure the high-priority ConfigSource
 * with the detected Protobuf channels.
 */
@Recorder
public class ApicurioRegistryProtobufRecorder {

    /**
     * Called at STATIC_INIT to configure the Protobuf channels.
     * This sets up the high-priority ConfigSource with serializer/deserializer config.
     */
    public void configureProtobufChannels(Set<String> incomingChannels, Set<String> outgoingChannels) {
        ProtobufChannelConfigSource.setChannels(incomingChannels, outgoingChannels);
    }
}
