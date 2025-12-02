package ai.pipestream.quarkus.dynamicgrpc.discovery;

import io.smallrye.stork.api.LoadBalancer;
import io.smallrye.stork.api.ServiceInstance;

import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Simple random load balancer implementation for Stork.
 */
public class RandomLoadBalancer implements LoadBalancer {

    /**
     * Creates a new RandomLoadBalancer.
     */
    public RandomLoadBalancer() {
    }

    private final Random random = new Random();

    @Override
    public ServiceInstance selectServiceInstance(Collection<ServiceInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            throw new IllegalArgumentException("No instances available for selection");
        }

        List<ServiceInstance> instanceList = instances instanceof List ?
                (List<ServiceInstance>) instances : new java.util.ArrayList<>(instances);

        if (instanceList.size() == 1) {
            return instanceList.get(0);
        }

        int index = random.nextInt(instanceList.size());
        return instanceList.get(index);
    }
}
