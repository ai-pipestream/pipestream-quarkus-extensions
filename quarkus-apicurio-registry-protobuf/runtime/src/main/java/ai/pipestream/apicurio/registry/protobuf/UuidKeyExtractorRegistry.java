package ai.pipestream.apicurio.registry.protobuf;

import com.google.protobuf.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that discovers and provides access to registered {@link UuidKeyExtractor} beans.
 *
 * <p>
 * This registry allows the framework to automatically locate the correct key extractor
 * for a given Protobuf message type. It is populated by CDI discovery.
 * </p>
 */
@ApplicationScoped
public class UuidKeyExtractorRegistry {

    private static final Logger LOG = Logger.getLogger(UuidKeyExtractorRegistry.class);

    private final Map<Class<?>, UuidKeyExtractor<?>> registry = new ConcurrentHashMap<>();

    // We inject Instance<UuidKeyExtractor<?>> to lazily resolve all beans
    @Inject
    @Any
    Instance<UuidKeyExtractor<?>> extractors;

    /**
     * Looks up a registered key extractor for the given message type.
     *
     * @param messageType the Protobuf message class
     * @param <T>         the Protobuf message type
     * @return the registered extractor, or null if none found
     */
    @SuppressWarnings("unchecked")
    public <T extends Message> UuidKeyExtractor<T> getExtractor(Class<T> messageType) {
        return (UuidKeyExtractor<T>) registry.computeIfAbsent(messageType, this::findExtractor);
    }

    private UuidKeyExtractor<?> findExtractor(Class<?> messageType) {
        // Iterate through all discovered extractors to find one that supports this type
        // Note: This is a simple implementation. In a real scenario, we might want to
        // handle type arguments more robustly or use qualifiers.
        // However, since UuidKeyExtractor<T> is generic, CDI doesn't easily expose 'T' 
        // without some reflection tricks or type literal qualifiers.
        
        // Strategy: We rely on the user to implement UuidKeyExtractor<SpecificProto>.
        // We will scan the *instances* to see if we can match them.
        // A better approach in Quarkus/CDI is often to use the bean metadata, 
        // but for now, let's try to resolve by checking the generic interface of the implementation.
        
        for (UuidKeyExtractor<?> extractor : extractors) {
            if (canHandle(extractor, messageType)) {
                return extractor;
            }
        }
        
        // Fallback: Check for a "raw" or "default" extractor if we wanted one, 
        // but for now we return null to indicate "no specific extractor found".
        return null;
    }

    private boolean canHandle(UuidKeyExtractor<?> extractor, Class<?> messageType) {
        Class<?> clazz = extractor.getClass();
        
        // Handle Quarkus/CDI proxies
        if (clazz.getName().endsWith("_ClientProxy") || clazz.getName().contains("$$")) {
            clazz = clazz.getSuperclass();
        }

        while (clazz != null && clazz != Object.class) {
            for (java.lang.reflect.Type iface : clazz.getGenericInterfaces()) {
                if (iface instanceof java.lang.reflect.ParameterizedType) {
                    java.lang.reflect.ParameterizedType pt = (java.lang.reflect.ParameterizedType) iface;
                    if (pt.getRawType().equals(UuidKeyExtractor.class)) {
                        java.lang.reflect.Type typeArg = pt.getActualTypeArguments()[0];
                        if (typeArg instanceof Class) {
                            return ((Class<?>) typeArg).isAssignableFrom(messageType);
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }
}
