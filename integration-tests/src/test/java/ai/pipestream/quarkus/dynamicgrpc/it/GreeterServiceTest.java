package ai.pipestream.quarkus.dynamicgrpc.it;

import ai.pipestream.quarkus.dynamicgrpc.GrpcClientFactory;
import ai.pipestream.quarkus.dynamicgrpc.it.proto.Greeter;
import ai.pipestream.quarkus.dynamicgrpc.it.proto.GreeterGrpc;
import ai.pipestream.quarkus.dynamicgrpc.it.proto.HelloReply;
import ai.pipestream.quarkus.dynamicgrpc.it.proto.HelloRequest;
import ai.pipestream.quarkus.dynamicgrpc.it.proto.MutinyGreeterGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for the Greeter gRPC service following Quarkus gRPC testing standards.
 * <p>
 * These tests verify both blocking and Mutiny stub functionality as per:
 * https://quarkus.io/guides/grpc-service-implementation#testing-your-services
 * </p>
 */
@QuarkusTest
public class GreeterServiceTest {

    /**
     * Test with Mutiny stub using @GrpcClient annotation (Quarkus standard approach).
     */
    @GrpcClient
    Greeter greeter;

    @Test
    public void testMutinyStub() {
        HelloReply reply = greeter.sayHello(
                HelloRequest.newBuilder()
                        .setName("Mutiny World")
                        .build()
        ).await().indefinitely();

        assertNotNull(reply);
        assertEquals("Hello Mutiny World", reply.getMessage());
    }

    @Test
    public void testBlockingStub() {
        // Create a blocking channel for testing
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9001)
                .usePlaintext()
                .build();

        try {
            GreeterGrpc.GreeterBlockingStub blockingStub = GreeterGrpc.newBlockingStub(channel);

            HelloReply reply = blockingStub.sayHello(
                    HelloRequest.newBuilder()
                            .setName("Blocking World")
                            .build()
            );

            assertNotNull(reply);
            assertEquals("Hello Blocking World", reply.getMessage());
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    public void testMultipleRequests() {
        // Test that the service handles multiple consecutive requests
        for (int i = 0; i < 5; i++) {
            String name = "User" + i;
            HelloReply reply = greeter.sayHello(
                    HelloRequest.newBuilder()
                            .setName(name)
                            .build()
            ).await().indefinitely();

            assertNotNull(reply);
            assertEquals("Hello " + name, reply.getMessage());
        }
    }
}
