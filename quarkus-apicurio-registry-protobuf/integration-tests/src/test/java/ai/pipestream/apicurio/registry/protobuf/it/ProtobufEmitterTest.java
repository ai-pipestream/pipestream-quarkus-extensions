package ai.pipestream.apicurio.registry.protobuf.it;

import ai.pipestream.apicurio.registry.protobuf.it.proto.TestRecord;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class ProtobufEmitterTest {

    @Inject
    EmitterService service;

    @Inject
    EmitterConsumer consumer;

    @AfterEach
    public void cleanup() {
        consumer.clear();
    }

    @Test
    public void testImplicitKeyExtraction() {
        // ID is a valid UUID string
        String uuidStr = UUID.randomUUID().toString();
        TestRecord record = TestRecord.newBuilder()
                .setId(uuidStr)
                .setName("Implicit Key")
                .build();

        service.sendImplicit(record);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> !consumer.getReceived().isEmpty());

        Record<UUID, TestRecord> received = consumer.getReceived().get(0);
        assertEquals(UUID.fromString(uuidStr), received.key());
        assertEquals("Implicit Key", received.value().getName());
    }

    @Test
    public void testExplicitKey() {
        UUID explicitKey = UUID.randomUUID();
        TestRecord record = TestRecord.newBuilder()
                .setId("some-id") // This would extract to name-based UUID if implicit
                .setName("Explicit Key")
                .build();

        service.sendExplicit(explicitKey, record);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> !consumer.getReceived().isEmpty());

        Record<UUID, TestRecord> received = consumer.getReceived().get(0);
        assertEquals(explicitKey, received.key());
        assertEquals("Explicit Key", received.value().getName());
    }


    @jakarta.enterprise.context.ApplicationScoped
    public static class EmitterConsumer {
        private final CopyOnWriteArrayList<Record<UUID, TestRecord>> received = new CopyOnWriteArrayList<>();

        @Incoming("emitter-in-safe")
        public void consume(Record<UUID, TestRecord> record) {
            received.add(record);
        }

        public CopyOnWriteArrayList<Record<UUID, TestRecord>> getReceived() {
            return received;
        }

        public void clear() {
            received.clear();
        }
    }
}
