package io.quarkus.apicurio.registry.protobuf.runtime;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * ConfigSource that provides Protobuf serializer/deserializer configuration
 * for detected Kafka channels.
 *
 * <p>
 * This extension enforces:
 * </p>
 * <ul>
 * <li><strong>UUID keys:</strong> via Kafka's built-in
 * {@code UUIDSerializer}/{@code UUIDDeserializer}.</li>
 * <li><strong>Protobuf values:</strong> via Apicurio Registry serde.</li>
 * </ul>
 *
 * <p>
 * The ordinal is set to 200 (lower than {@code application.properties} at 250)
 * to allow
 * users to override these defaults in their {@code application.properties}.
 * </p>
 *
 * <h3>Priority Chain</h3>
 * <p>
 * Configuration sources are loaded in the following order (higher ordinal
 * wins):
 * </p>
 * <ol>
 * <li>System properties: 400</li>
 * <li>Environment variables: 300</li>
 * <li>{@code application.properties}: 250</li>
 * <li><strong>This ConfigSource: 200</strong> (can be overridden)</li>
 * <li>Default values: ~100</li>
 * </ol>
 */
@SuppressWarnings("unused")
public class ProtobufChannelConfigSource implements ConfigSource {

    // Value serializers (Protobuf via Apicurio Registry)
    private static final String PROTOBUF_SERIALIZER = "io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer";
    private static final String PROTOBUF_DESERIALIZER = "io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer";

    // Key serializers (UUID - enforced by this extension)
    private static final String UUID_SERIALIZER = "org.apache.kafka.common.serialization.UUIDSerializer";
    private static final String UUID_DESERIALIZER = "org.apache.kafka.common.serialization.UUIDDeserializer";

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
        if (!enabled || !properties.isEmpty()) {
            return;
        }

        // Configure incoming channels (UUID keys + Protobuf values)
        for (String channelName : incomingChannels.keySet()) {
            String prefix = "mp.messaging.incoming." + channelName + ".";
            properties.put(prefix + "connector", "smallrye-kafka");
            properties.put(prefix + "key.deserializer", UUID_DESERIALIZER);
            properties.put(prefix + "value.deserializer", PROTOBUF_DESERIALIZER);
            properties.put(prefix + "auto.offset.reset", "earliest");
        }

        // Configure outgoing channels (UUID keys + Protobuf values)
        for (String channelName : outgoingChannels.keySet()) {
            String prefix = "mp.messaging.outgoing." + channelName + ".";
            properties.put(prefix + "connector", "smallrye-kafka");
            properties.put(prefix + "key.serializer", UUID_SERIALIZER);
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
        // Lower than application.properties (250) so users can easily override
        // Still higher than most default configs to ensure Protobuf serializers are
        // used
        return 200;
    }
}
