package ai.pipestream.apicurio.registry.protobuf.runtime;

import ai.pipestream.apicurio.registry.protobuf.ProtobufEmitter;
import ai.pipestream.apicurio.registry.protobuf.UuidKeyExtractor;
import ai.pipestream.apicurio.registry.protobuf.UuidKeyExtractorRegistry;
import com.google.protobuf.Message;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Runtime implementation of {@link ProtobufEmitter}.
 * <p>
 * This class is not a CDI bean itself but is instantiated/configured by the
 * framework or synthetic beans to wrap an underlying SmallRye {@link MutinyEmitter}.
 * </p>
 */
public class ProtobufEmitterImpl<T extends Message> implements ProtobufEmitter<T> {

    private final MutinyEmitter<T> underlying;
    private final UuidKeyExtractorRegistry registry;
    private final Class<T> messageType;

    public ProtobufEmitterImpl(MutinyEmitter<T> underlying, UuidKeyExtractorRegistry registry, Class<T> messageType) {
        this.underlying = underlying;
        this.registry = registry;
        this.messageType = messageType;
    }

    @Override
    public CompletionStage<Void> send(T message) {
        UuidKeyExtractor<T> extractor = registry.getExtractor(messageType);
        if (extractor == null) {
            // Strict mode: fail if no extractor is registered
            throw new IllegalStateException("No UuidKeyExtractor registered for type: " + messageType.getName() +
                    ". Please define a bean implementing UuidKeyExtractor<" + messageType.getSimpleName() + ">.");
        }
        return send(message, extractor);
    }

    @Override
    public CompletionStage<Void> send(UUID key, T message) {
        OutgoingKafkaRecordMetadata<UUID> metadata = OutgoingKafkaRecordMetadata.<UUID>builder()
                .withKey(key)
                .build();
        return underlying.sendMessage(org.eclipse.microprofile.reactive.messaging.Message.of(message)
                .addMetadata(metadata)).subscribeAsCompletionStage();
    }

    @Override
    public CompletionStage<Void> send(T message, UuidKeyExtractor<T> customExtractor) {
        UUID key = customExtractor.extractKey(message);
        return send(key, message);
    }

    // --- Emitter<T> implementation ---

    @Override
    @SuppressWarnings("unchecked")
    public <M extends org.eclipse.microprofile.reactive.messaging.Message<? extends T>> void send(M msg) {
        underlying.sendMessage((org.eclipse.microprofile.reactive.messaging.Message<T>) msg).subscribeAsCompletionStage();
    }

    @Override
    public void complete() {
        underlying.complete();
    }

    @Override
    public void error(Exception e) {
        underlying.error(e);
    }

    @Override
    public boolean isCancelled() {
        return underlying.isCancelled();
    }

    @Override
    public boolean hasRequests() {
        return underlying.hasRequests();
    }
}
