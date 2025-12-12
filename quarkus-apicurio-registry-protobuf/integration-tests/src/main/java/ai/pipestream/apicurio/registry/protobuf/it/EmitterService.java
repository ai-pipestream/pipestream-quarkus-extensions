package ai.pipestream.apicurio.registry.protobuf.it;

import ai.pipestream.apicurio.registry.protobuf.ProtobufChannel;
import ai.pipestream.apicurio.registry.protobuf.ProtobufEmitter;
import ai.pipestream.apicurio.registry.protobuf.it.proto.TestRecord;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Service that produces messages using the new ProtobufEmitter wrapper.
 */
@ApplicationScoped
public class EmitterService {

    @Inject
    @ProtobufChannel("emitter-out-safe")
    ProtobufEmitter<TestRecord> emitter;

    public CompletionStage<Void> sendImplicit(TestRecord record) {
        return emitter.send(record);
    }

    public CompletionStage<Void> sendExplicit(UUID key, TestRecord record) {
        return emitter.send(key, record);
    }
}
