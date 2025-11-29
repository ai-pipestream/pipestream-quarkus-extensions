package io.quarkus.apicurio.registry.protobuf.runtime;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * High-priority ConfigSource that provides Protobuf serializer/deserializer configuration
 * for detected Kafka channels.
 *
 * This runs with ordinal 275 (higher than application.properties at 250) to ensure
 * our protobuf config takes precedence over defaults.
 */
public class ProtobufChannelConfigSource implements ConfigSource {

    private static final String PROTOBUF_SERIALIZER = "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer";
    private static final String PROTOBUF_DESERIALIZER = "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer";

    // These are set at static init time by the recorder
    private static volatile Map<String, String> incomingChannels = new HashMap<>();
    private static volatile Map<String, String> outgoingChannels = new HashMap<>();
    private static volatile boolean enabled = false;

    private final Map<String, String> properties = new HashMap<>();

    public ProtobufChannelConfigSource() {
        // Properties are built lazily when first accessed
    }

    /**
     * Called by the recorder at static init time to register channels.
     */
    public static void registerIncomingChannel(String channelName) {
        incomingChannels.put(channelName, channelName);
    }

    /**
     * Called by the recorder at static init time to register channels.
     */
    public static void registerOutgoingChannel(String channelName) {
        outgoingChannels.put(channelName, channelName);
    }

    /**
     * Called by the recorder to enable/disable this config source.
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * Called by the recorder to set all channels at once.
     */
    public static void setChannels(Set<String> incoming, Set<String> outgoing) {
        incomingChannels = new HashMap<>();
        outgoingChannels = new HashMap<>();
        for (String channel : incoming) {
            incomingChannels.put(channel, channel);
        }
        for (String channel : outgoing) {
            outgoingChannels.put(channel, channel);
        }
        enabled = true;
    }

    private void buildProperties() {
        if (!enabled || properties.size() > 0) {
            return;
        }

        // Configure incoming channels
        for (String channelName : incomingChannels.keySet()) {
            String prefix = "mp.messaging.incoming." + channelName + ".";
            properties.put(prefix + "connector", "smallrye-kafka");
            properties.put(prefix + "value.deserializer", PROTOBUF_DESERIALIZER);
            properties.put(prefix + "auto.offset.reset", "earliest");
        }

        // Configure outgoing channels
        for (String channelName : outgoingChannels.keySet()) {
            String prefix = "mp.messaging.outgoing." + channelName + ".";
            properties.put(prefix + "connector", "smallrye-kafka");
            properties.put(prefix + "value.serializer", PROTOBUF_SERIALIZER);
        }

        // Connector-level defaults for Apicurio
        if (!incomingChannels.isEmpty() || !outgoingChannels.isEmpty()) {
            properties.put("mp.messaging.connector.smallrye-kafka.apicurio.protobuf.derive.class", "true");
            properties.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.auto-register", "true");
            properties.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.artifact-resolver-strategy",
                    "io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy");
            properties.put("mp.messaging.connector.smallrye-kafka.apicurio.registry.find-latest", "true");
        }
    }

    @Override
    public Map<String, String> getProperties() {
        buildProperties();
        return properties;
    }

    @Override
    public Set<String> getPropertyNames() {
        buildProperties();
        return properties.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        buildProperties();
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return "ProtobufChannelConfigSource";
    }

    @Override
    public int getOrdinal() {
        // Very high priority (500) - higher than system properties (400), application.properties (250)
        // This ensures our protobuf config takes precedence over SmallRye defaults
        // Users can still override via a custom ConfigSource with ordinal > 500
        return 500;
    }
}
