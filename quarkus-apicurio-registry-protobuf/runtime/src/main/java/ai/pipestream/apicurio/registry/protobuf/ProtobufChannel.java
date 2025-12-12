package ai.pipestream.apicurio.registry.protobuf;

import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Qualifier for injecting a {@link ProtobufEmitter}.
 *
 * <p>
 * Use this annotation to inject a type-safe emitter for Protobuf messages that automatically
 * handles UUID key generation and schema registration.
 * </p>
 *
 * <pre>{@code
 * @Inject
 * @ProtobufChannel("orders")
 * ProtobufEmitter<OrderEventProto> orderEmitter;
 *
 * public void sendOrder(OrderEventProto event) {
 *     orderEmitter.send(event);
 * }
 * }</pre>
 *
 * @see ProtobufEmitter
 */
@Qualifier
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtobufChannel {

    /**
     * The name of the channel (topic).
     *
     * @return the channel name
     */
    String value();
}
