package ai.pipestream.apicurio.registry.protobuf.deployment;

import ai.pipestream.apicurio.registry.protobuf.ApicurioRegistryProtobufRecorder;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.FieldCreator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Build steps for processing Protobuf Emitters.
 * <p>
 * This class handles:
 * 1. Scanning for {@code @ProtobufChannel} injection points.
 * 2. Registering synthetic {@code ProtobufEmitter} beans for them.
 * 3. Generating a "Keeper" class to ensure SmallRye creates the underlying channels.
 * </p>
 */
public class ProtobufEmitterProcessor {

    private static final Logger LOGGER = Logger.getLogger(ProtobufEmitterProcessor.class);

    private static final DotName PROTOBUF_EMITTER = DotName.createSimple("ai.pipestream.apicurio.registry.protobuf.ProtobufEmitter");
    private static final DotName PROTOBUF_CHANNEL = DotName.createSimple("ai.pipestream.apicurio.registry.protobuf.ProtobufChannel");
    // Standard SmallRye Channel annotation
    private static final String CHANNEL_ANNOTATION = "org.eclipse.microprofile.reactive.messaging.Channel";
    // Mutiny Emitter class name
    private static final String MUTINY_EMITTER_CLASS = "io.smallrye.reactive.messaging.MutinyEmitter";

    /**
     * Scan for injection points of ProtobufEmitter and register synthetic beans.
     */
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void registerProtobufEmitters(
            CombinedIndexBuildItem combinedIndex,
            ApicurioRegistryProtobufRecorder recorder,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans) {
        
        IndexView index = combinedIndex.getIndex();
        Map<String, String> channelToType = new HashMap<>();
        
        // Scan for @ProtobufChannel annotations
        for (AnnotationInstance annotation : index.getAnnotations(PROTOBUF_CHANNEL)) {
            Type injectionType = null;
            AnnotationTarget target = annotation.target();
            
            if (target.kind() == AnnotationTarget.Kind.FIELD) {
                injectionType = target.asField().type();
            } else if (target.kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
                injectionType = target.asMethodParameter().type();
            }
            
            if (injectionType != null && injectionType.name().equals(PROTOBUF_EMITTER)) {
                if (injectionType.kind() == Type.Kind.PARAMETERIZED_TYPE) {
                    ParameterizedType paramType = injectionType.asParameterizedType();
                    Type messageTypeArg = paramType.arguments().get(0);
                    String channelName = annotation.value().asString();
                    
                    // Sanity check: is it a class?
                    if (messageTypeArg.kind() == Type.Kind.CLASS) {
                        String messageClassName = messageTypeArg.name().toString();
                        Class<?> messageClass;
                        try {
                            messageClass = Class.forName(messageClassName, false, Thread.currentThread().getContextClassLoader());
                        } catch (ClassNotFoundException e) {
                            LOGGER.warnf("Could not load class %s for ProtobufEmitter synthetic bean", messageClassName);
                            continue;
                        }

                        LOGGER.debugf("Registering synthetic ProtobufEmitter bean for channel '%s' and type '%s'", channelName, messageClassName);
                        
                        syntheticBeans.produce(SyntheticBeanBuildItem.configure(PROTOBUF_EMITTER)
                                .addType(paramType) // Register as ProtobufEmitter<T>
                                .addQualifier(annotation) // Add @ProtobufChannel("name") qualifier
                                // Add Default qualifier fallback if needed, though proper qualifier handling is preferred
                                .addQualifier(AnnotationInstance.create(DotName.createSimple(Default.class.getName()), null, new AnnotationValue[0]))
                                .scope(ApplicationScoped.class)
                                .unremovable()
                                .createWith((java.util.function.Function) recorder.createProtobufEmitter(channelName, messageClass))
                                .done());
                                
                        // Collect for keeper bean generation
                        channelToType.put(channelName, messageClassName);
                        LOGGER.infof("Collected channel '%s' for Keeper generation (Type: %s)", channelName, messageClassName);
                    }
                }
            }
        }
        
        // Generate Keeper Class to force SmallRye to create the channels
        if (!channelToType.isEmpty()) {
            LOGGER.infof("Generating ProtobufChannelKeeper with %d channels", channelToType.size());
            generateKeeperClass(generatedBeans, channelToType);
        } else {
            LOGGER.info("No channels collected for ProtobufChannelKeeper generation");
        }
    }

    private void generateKeeperClass(BuildProducer<GeneratedBeanBuildItem> generatedBeans, Map<String, String> channelToType) {
        String keeperClassName = "ai.pipestream.apicurio.registry.protobuf.deployment.ProtobufChannelKeeper";
        
        ClassCreator cc = ClassCreator.builder()
                .classOutput((name, data) -> generatedBeans.produce(new GeneratedBeanBuildItem(name, data)))
                .className(keeperClassName)
                .build();
        
        cc.addAnnotation(ApplicationScoped.class.getName());
        cc.addAnnotation(Unremovable.class.getName());

        io.quarkus.gizmo.MethodCreator getEmitter = cc.getMethodCreator("getEmitter", MUTINY_EMITTER_CLASS, String.class);
        getEmitter.setModifiers(org.objectweb.asm.Opcodes.ACC_PUBLIC);
        
        io.quarkus.gizmo.ResultHandle nameParam = getEmitter.getMethodParam(0);
        
        int i = 0;
        for (Map.Entry<String, String> entry : channelToType.entrySet()) {
            String channel = entry.getKey();
            String type = entry.getValue();
            // Lcom/example/MyType;
            String typeDesc = "L" + type.replace('.', '/') + ";";
            // Lio/smallrye/reactive/messaging/MutinyEmitter<Lcom/example/MyType;>;
            String signature = "Lio/smallrye/reactive/messaging/MutinyEmitter<" + typeDesc + ">;";

            String fieldName = "emitter" + i++;
            FieldCreator fc = cc.getFieldCreator(fieldName, MUTINY_EMITTER_CLASS);
            fc.setModifiers(org.objectweb.asm.Opcodes.ACC_PUBLIC);
            fc.setSignature(signature);
            fc.addAnnotation(Inject.class.getName());
            fc.addAnnotation(CHANNEL_ANNOTATION).addValue("value", channel);
            
            // if (channel.equals(nameParam)) return field;
            io.quarkus.gizmo.ResultHandle channelConst = getEmitter.load(channel);
            // Call String.equals (or Object.equals)
            io.quarkus.gizmo.ResultHandle equals = getEmitter.invokeVirtualMethod(
                io.quarkus.gizmo.MethodDescriptor.ofMethod(Object.class, "equals", boolean.class, Object.class),
                channelConst,
                nameParam
            );
            
            io.quarkus.gizmo.BytecodeCreator trueBranch = getEmitter.ifTrue(equals).trueBranch();
            io.quarkus.gizmo.ResultHandle fieldVal = trueBranch.readInstanceField(
                io.quarkus.gizmo.FieldDescriptor.of(keeperClassName, fieldName, MUTINY_EMITTER_CLASS),
                trueBranch.getThis()
            );
            trueBranch.returnValue(fieldVal);
        }
        
        getEmitter.returnValue(getEmitter.loadNull());
        cc.close();
    }
}
