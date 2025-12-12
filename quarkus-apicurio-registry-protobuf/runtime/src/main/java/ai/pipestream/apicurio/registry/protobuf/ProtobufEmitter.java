package ai.pipestream.apicurio.registry.protobuf;

import com.google.protobuf.Message;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * A strongly-typed emitter for Protobuf messages that enforces UUID keys.
 *
 * <p>
 * This interface provides a type-safe way to send Protobuf messages to Kafka
 * while handling the complexity of UUID key generation and extraction automatically.
 * </p>
 *
 * <h2>Usage Scenarios</h2>
 * <ol>
 *   <li><strong>Implicit Key:</strong> {@code send(msg)} - Uses the registered {@link UuidKeyExtractor} for this type.</li>
 *   <li><strong>Explicit Key:</strong> {@code send(uuid, msg)} - Uses the provided UUID, bypassing the extractor.</li>
 *   <li><strong>Custom Extractor:</strong> {@code send(msg, extractor)} - Uses a specific extractor logic for this call.</li>
 * </ol>
 *
 * @param <T> the Protobuf message type
 */
public interface ProtobufEmitter<T extends Message> extends Emitter<T> {

    /**
     * Sends a message using the registered {@link UuidKeyExtractor} to derive the UUID key.
     *
     * @param message the message to send
     * @return a CompletionStage indicating success or failure
     * @throws IllegalStateException if no {@link UuidKeyExtractor} is registered for this type
     */
    CompletionStage<Void> send(T message);

    /**
     * Sends a message with a specific explicit UUID key.
     *
     * @param key     the UUID key to use
     * @param message the message to send
     * @return a CompletionStage indicating success or failure
     */
    CompletionStage<Void> send(UUID key, T message);

    /**
     * Sends a message using a custom {@link UuidKeyExtractor} for this specific call.
     *
     * @param message      the message to send
     * @param customExtractor the custom extractor to use
     * @return a CompletionStage indicating success or failure
     */
    CompletionStage<Void> send(T message, UuidKeyExtractor<T> customExtractor);
}
