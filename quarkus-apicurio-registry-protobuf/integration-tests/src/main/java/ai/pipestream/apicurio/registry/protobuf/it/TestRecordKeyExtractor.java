package ai.pipestream.apicurio.registry.protobuf.it;

import ai.pipestream.apicurio.registry.protobuf.UuidKeyExtractor;
import ai.pipestream.apicurio.registry.protobuf.it.proto.TestRecord;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

/**
 * Key extractor for TestRecord.
 */
@ApplicationScoped
public class TestRecordKeyExtractor implements UuidKeyExtractor<TestRecord> {

    @Override
    public UUID extractKey(TestRecord message) {
        // Deterministic UUID for testing derived from ID
        // In real app, this might be stored in the message or derived differently
        // Here we just parse the ID if it's a UUID string, or generate name-based UUID
        try {
            return UUID.fromString(message.getId());
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(message.getId().getBytes());
        }
    }
}
