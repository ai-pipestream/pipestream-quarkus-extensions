package io.quarkus.apicurio.registry.protobuf.deployment;

import io.quarkus.apicurio.registry.protobuf.ApicurioRegistryProtobufRecorder;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Build-time processor for Apicurio Registry Protobuf extension.
 * <p>
 * This processor:
 * 1. Registers the extension feature
 * 2. Auto-detects Kafka channels using Protobuf types and configures serializer/deserializer
 * 3. Configures default properties for Protobuf serde
 * 4. Registers classes for reflection (native image support)
 */
class ApicurioRegistryProtobufProcessor {

    private static final Logger LOGGER = Logger.getLogger(ApicurioRegistryProtobufProcessor.class);

    private static final String FEATURE = "apicurio-registry-protobuf";

    // Protobuf base types (MessageLite is the root interface, GeneratedMessage is the base class)
    private static final DotName MESSAGE_LITE = DotName.createSimple("com.google.protobuf.MessageLite");
    private static final DotName GENERATED_MESSAGE = DotName.createSimple("com.google.protobuf.GeneratedMessage");

    // Reactive Messaging annotations
    private static final DotName INCOMING = DotName.createSimple("org.eclipse.microprofile.reactive.messaging.Incoming");
    private static final DotName OUTGOING = DotName.createSimple("org.eclipse.microprofile.reactive.messaging.Outgoing");
    private static final DotName CHANNEL = DotName.createSimple("org.eclipse.microprofile.reactive.messaging.Channel");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem enableSslInNative() {
        return new ExtensionSslNativeSupportBuildItem(FEATURE);
    }

    /**
     * Auto-detect Kafka channels using Protobuf types and configure serializer/deserializer.
     * This scans for @Incoming/@Outgoing/@Channel annotations and checks if the message type
     * extends MessageLite (Protobuf base class).
     *
     * Uses STATIC_INIT to configure the high-priority ConfigSource before config is read.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void autoConfigureProtobufChannels(
            CombinedIndexBuildItem combinedIndex,
            ApicurioRegistryProtobufRecorder recorder,
            BuildProducer<RunTimeConfigurationDefaultBuildItem> defaults) {

        IndexView index = combinedIndex.getIndex();
        Set<String> configuredIncoming = new HashSet<>();
        Set<String> configuredOutgoing = new HashSet<>();

        // Scan @Incoming methods
        for (AnnotationInstance annotation : index.getAnnotations(INCOMING)) {
            if (annotation.target().kind() == AnnotationTarget.Kind.METHOD) {
                MethodInfo method = annotation.target().asMethod();
                String channelName = annotation.value().asString();

                // Check method parameters for Protobuf types
                for (MethodParameterInfo param : method.parameters()) {
                    if (isProtobufType(index, param.type())) {
                        if (configuredIncoming.add(channelName)) {
                            LOGGER.debugf("Auto-configuring Protobuf deserializer for incoming channel: %s", channelName);
                        }
                        break;
                    }
                }
            }
        }

        // Scan @Outgoing methods
        for (AnnotationInstance annotation : index.getAnnotations(OUTGOING)) {
            if (annotation.target().kind() == AnnotationTarget.Kind.METHOD) {
                MethodInfo method = annotation.target().asMethod();
                String channelName = annotation.value().asString();

                // Check return type for Protobuf types
                if (isProtobufType(index, method.returnType())) {
                    if (configuredOutgoing.add(channelName)) {
                        LOGGER.debugf("Auto-configuring Protobuf serializer for outgoing channel: %s", channelName);
                    }
                }
            }
        }

        // Scan @Channel injection points (Emitter<ProtobufType>)
        for (AnnotationInstance annotation : index.getAnnotations(CHANNEL)) {
            String channelName = annotation.value().asString();
            AnnotationTarget target = annotation.target();

            if (target.kind() == AnnotationTarget.Kind.FIELD) {
                Type fieldType = target.asField().type();
                Type messageType = extractEmitterType(fieldType);
                if (messageType != null && isProtobufType(index, messageType)) {
                    if (configuredOutgoing.add(channelName)) {
                        LOGGER.debugf("Auto-configuring Protobuf serializer for @Channel Emitter: %s", channelName);
                    }
                }
            } else if (target.kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
                MethodParameterInfo param = target.asMethodParameter();
                Type messageType = extractEmitterType(param.type());
                if (messageType != null && isProtobufType(index, messageType)) {
                    if (configuredOutgoing.add(channelName)) {
                        LOGGER.debugf("Auto-configuring Protobuf serializer for @Channel parameter: %s", channelName);
                    }
                }
            }
        }

        LOGGER.infof("Auto-configured %d incoming and %d outgoing Protobuf channels",
                configuredIncoming.size(), configuredOutgoing.size());

        // Inject connector defaults via BuildItem (needed for DevServices to see at build time)
        for (String channelName : configuredIncoming) {
            String prefix = "mp.messaging.incoming." + channelName + ".";
            defaults.produce(new RunTimeConfigurationDefaultBuildItem(prefix + "connector", "smallrye-kafka"));
        }
        for (String channelName : configuredOutgoing) {
            String prefix = "mp.messaging.outgoing." + channelName + ".";
            defaults.produce(new RunTimeConfigurationDefaultBuildItem(prefix + "connector", "smallrye-kafka"));
        }

        // Configure the high-priority ConfigSource via recorder at STATIC_INIT
        // This sets serializer/deserializer with high priority to override SmallRye defaults
        if (!configuredIncoming.isEmpty() || !configuredOutgoing.isEmpty()) {
            recorder.configureProtobufChannels(configuredIncoming, configuredOutgoing);
        }
    }

    /**
     * Check if a type is a Protobuf message type (extends MessageLite).
     * Note: Only check MessageLite, NOT GeneratedMessageV3 as per user requirement.
     */
    private boolean isProtobufType(IndexView index, Type type) {
        if (type == null) {
            return false;
        }

        // Handle parameterized types (e.g., Multi<TestRecord>)
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType paramType = type.asParameterizedType();
            List<Type> args = paramType.arguments();
            if (!args.isEmpty()) {
                return isProtobufType(index, args.get(0));
            }
            return false;
        }

        if (type.kind() != Type.Kind.CLASS) {
            return false;
        }

        DotName typeName = type.name();

        // Direct match
        if (MESSAGE_LITE.equals(typeName)) {
            return true;
        }

        // Check if the class extends MessageLite
        ClassInfo classInfo = index.getClassByName(typeName);
        if (classInfo == null) {
            LOGGER.debugf("Class %s not found in Jandex index", typeName);
            return false;
        }

        // Check superclass hierarchy
        DotName superName = classInfo.superName();
        while (superName != null && !superName.equals(DotName.OBJECT_NAME)) {
            if (MESSAGE_LITE.equals(superName) || GENERATED_MESSAGE.equals(superName)) {
                return true;
            }
            ClassInfo superClass = index.getClassByName(superName);
            if (superClass == null) {
                break;
            }
            superName = superClass.superName();
        }

        // Check interfaces
        for (DotName iface : classInfo.interfaceNames()) {
            if (MESSAGE_LITE.equals(iface)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extract the type argument from Emitter<T> or similar generic types.
     */
    private Type extractEmitterType(Type type) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            ParameterizedType paramType = type.asParameterizedType();
            List<Type> args = paramType.arguments();
            if (!args.isEmpty()) {
                return args.get(0);
            }
        }
        return null;
    }

    /**
     * Register Apicurio and Protobuf classes for reflection (needed for native image).
     */
    @BuildStep
    void registerForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClasses) {
        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "io.apicurio.registry.serde.protobuf.ProtobufSerializer",
                "io.apicurio.registry.serde.protobuf.ProtobufDeserializer",
                "io.apicurio.registry.serde.protobuf.ProtobufSerdeHeaders",
                "io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy",
                "io.apicurio.registry.serde.strategy.TopicIdStrategy",
                "io.apicurio.registry.serde.strategy.RecordIdStrategy"
        ).methods().fields().build());

        reflectiveClasses.produce(ReflectiveClassBuildItem.builder(
                "com.google.protobuf.GeneratedMessageV3",
                "com.google.protobuf.DynamicMessage",
                "com.google.protobuf.Descriptors$Descriptor",
                "com.google.protobuf.Descriptors$FileDescriptor"
        ).methods().fields().build());
    }
}
