package ai.pipestream.quarkus.dynamicgrpc.it;

import ai.pipestream.quarkus.dynamicgrpc.it.proto.Greeter;
import ai.pipestream.quarkus.dynamicgrpc.it.proto.HelloReply;
import ai.pipestream.quarkus.dynamicgrpc.it.proto.HelloRequest;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;

/**
 * Implementation of the Greeter gRPC service for testing.
 */
@GrpcService
public class GreeterServiceImpl implements Greeter {

    @Override
    public Uni<HelloReply> sayHello(HelloRequest request) {
        String message = "Hello " + request.getName();
        return Uni.createFrom().item(
                HelloReply.newBuilder()
                        .setMessage(message)
                        .build()
        );
    }
}
