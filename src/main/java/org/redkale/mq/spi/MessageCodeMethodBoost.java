/*
 *
 */
package org.redkale.mq.spi;

import static java.lang.classfile.ClassFile.*;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassSignature;
import java.lang.classfile.Label;
import java.lang.classfile.MethodSignature;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Nonnull;
import org.redkale.bytecode.ByteCodes;
import org.redkale.bytecode.CodeMethodBean;
import org.redkale.bytecode.CodeMethodBoost;
import org.redkale.bytecode.CodeNewMethod;
import org.redkale.convert.Convert;
import org.redkale.convert.ConvertFactory;
import org.redkale.inject.ResourceFactory;
import org.redkale.mq.MessageConsumer;
import org.redkale.mq.MessageEvent;
import org.redkale.mq.Messaged;
import org.redkale.mq.ResourceConsumer;
import org.redkale.mq.spi.DynForMessaged.DynForMessageds;
import org.redkale.service.LoadMode;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/** @author zhangjx */
public class MessageCodeMethodBoost extends CodeMethodBoost<Object> {

    private static final List<Class<? extends Annotation>> FILTER_ANN = List.of(Messaged.class);

    private final AtomicInteger index = new AtomicInteger();

    private final MessageModuleEngine messageEngine;

    private Map<String, CodeMethodBean> methodBeans;

    Map<String, byte[]> consumerBytes;

    public MessageCodeMethodBoost(boolean remote, Class serviceType, MessageModuleEngine messageEngine) {
        super(remote, serviceType);
        this.messageEngine = messageEngine;
    }

    @Override
    public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
        return FILTER_ANN;
    }

    @Override
    public CodeNewMethod doMethod(
            RedkaleClassLoader classLoader,
            ClassBuilder cw,
            Class serviceImplClass,
            String newDynName,
            String fieldPrefix,
            List filterAnns,
            Method method,
            CodeNewMethod newMethod) {
        if (serviceType.getAnnotation(DynForMessaged.class) != null) {
            return newMethod;
        }
        Messaged messaged = method.getAnnotation(Messaged.class);
        if (messaged == null) {
            return newMethod;
        }
        if (Utility.isEmpty(messaged.regexTopic()) && Utility.isEmpty(messaged.topics())) {
            throw new RedkaleException(
                    "@" + Messaged.class.getSimpleName() + " regexTopic and topics both empty on " + method);
        }
        if (Utility.isNotEmpty(messaged.regexTopic()) && Utility.isNotEmpty(messaged.topics())) {
            throw new RedkaleException(
                    "@" + Messaged.class.getSimpleName() + " regexTopic and topics both not empty on " + method);
        }
        if (!LoadMode.matches(remote, messaged.mode())) {
            return newMethod;
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new RedkaleException(
                    "@" + Messaged.class.getSimpleName() + " cannot on static method, but on " + method);
        }
        if (Modifier.isProtected(method.getModifiers()) && Modifier.isFinal(method.getModifiers())) {
            throw new RedkaleException(
                    "@" + Messaged.class.getSimpleName() + " cannot on protected final method, but on " + method);
        }
        if (!Modifier.isProtected(method.getModifiers()) && !Modifier.isPublic(method.getModifiers())) {
            throw new RedkaleException(
                    "@" + Messaged.class.getSimpleName() + " must on protected or public method, but on " + method);
        }
        if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != MessageEvent[].class) {
            throw new RedkaleException("@" + Messaged.class.getSimpleName()
                    + " must on one parameter(type: MessageEvent[]) method, but on " + method);
        }
        Type messageType = getMethodMessageType(method);
        Convert convert = ConvertFactory.findConvert(messaged.convertType());
        convert.getFactory().loadDecoder(messageType);
        if (Modifier.isProtected(method.getModifiers())) {
            createMessageMethod(cw, method, serviceImplClass, filterAnns, newMethod);
        }
        createInnerConsumer(cw, serviceImplClass, method, messageType, messaged, newDynName, newMethod);
        return newMethod;
    }

    private void createMessageMethod(
            ClassBuilder cw, Method method, Class serviceImplClass, List filterAnns, CodeNewMethod newMethod) {
        final CodeMethodBean methodBean = getMethodBean(method);
        createMethod(cw, method, newMethod, methodBean, mb -> {
            List<java.lang.classfile.Annotation> annotations = new ArrayList<>();
            visitRawAnnotation(method, newMethod, mb, Messaged.class, filterAnns, annotations);
            if (!annotations.isEmpty()) mb.with(RuntimeVisibleAnnotationsAttribute.of(annotations));
            mb.withCode(code -> {
                Label start = code.newLabel();
                code.labelBinding(start).aload(0);
                List<Integer> slots = visitVarInsnParamTypes(code, method, 0);
                code.invokespecial(
                        ByteCodes.constantType(serviceImplClass),
                        method.getName(),
                        MethodTypeDesc.ofDescriptor(ByteCodes.methodDescriptor(method)));
                visitInsnReturn(code, method, start, slots, methodBean);
            });
        });
    }

    protected static Type getMethodMessageType(Method method) {
        Type paramType = method.getGenericParameterTypes()[0];
        if (!(paramType instanceof GenericArrayType)) {
            throw new RedkaleException("@" + Messaged.class.getSimpleName()
                    + " must on one generic type parameter method, but on " + method);
        }
        GenericArrayType arrayType = (GenericArrayType) paramType;
        Type omponentType = arrayType.getGenericComponentType();
        return ((ParameterizedType) omponentType).getActualTypeArguments()[0];
    }

    protected void createInnerConsumer(
            ClassBuilder pcw,
            @Nonnull Class serviceImplClass,
            @Nonnull Method method,
            Type messageType,
            Messaged messaged,
            String newDynName,
            CodeNewMethod newMethod) {
        final String newDynDesc = pcw == null ? ByteCodes.descriptor(serviceImplClass) : ("L" + newDynName + ";");
        final String innerClassName = "Dyn" + MessageConsumer.class.getSimpleName() + index.incrementAndGet();
        final String innerFullName = newDynName + (pcw == null ? "" : "$") + innerClassName;
        final Class msgTypeClass = TypeToken.typeToClass(messageType);
        final String msgTypeDesc = ByteCodes.descriptor(msgTypeClass);
        final String messageConsumerName = MessageConsumer.class.getName().replace('.', '/');
        final String messageConsumerDesc = ByteCodes.descriptor(MessageConsumer.class);
        final String messageEventsDesc = ByteCodes.descriptor(MessageEvent[].class);
        final boolean throwFlag =
                Utility.contains(method.getExceptionTypes(), e -> !RuntimeException.class.isAssignableFrom(e));

        if (methodBeans == null) {
            methodBeans = CodeMethodBoost.getMethodBeans(serviceType);
        }
        CodeMethodBean methodBean = CodeMethodBean.get(methodBeans, method);
        String genericMsgTypeDesc0 = msgTypeDesc;
        if (Utility.isNotEmpty(methodBean.getSignature())) {
            String methodSignature = methodBean.getSignature();
            methodSignature = methodSignature.substring(0, methodSignature.lastIndexOf(')') + 1) + "V";
            int start = methodSignature.indexOf('<') + 1;
            genericMsgTypeDesc0 = methodSignature.substring(start, methodSignature.lastIndexOf('>')); // 获取<>中的值
        }
        if (pcw != null) { // 不一定是关联类
        }

        //
        final String genericMsgTypeDesc = genericMsgTypeDesc0;
        byte[] classBytes = ClassFile.of().build(ByteCodes.classDesc(innerFullName), cw -> {
            cw.withVersion(JAVA_11_VERSION, 0)
                    .withFlags(ACC_PUBLIC + ACC_SUPER)
                    .withSuperclass(ByteCodes.classDesc("java/lang/Object"));
            List<java.lang.classfile.Annotation> cwAnnotations = new ArrayList<>();
            List<InnerClassInfo> cwInnerClasses = new ArrayList<>();
            cw.with(SignatureAttribute.of(ClassSignature.parseFrom(
                    "Ljava/lang/Object;" + messageConsumerDesc.replace(";", "<" + genericMsgTypeDesc + ">;"))));
            cw.withInterfaceSymbols(Arrays.stream(new String[] {messageConsumerName})
                    .map(ByteCodes::classDesc)
                    .toList());
            {
                cwAnnotations.add(ByteCodes.annotation(ResourceConsumer.class, messaged));
            }
            { // 设置DynForConsumer
                {
                    List<AnnotationElement> av = new ArrayList<>();
                    String group = messaged.group();
                    if (Utility.isBlank(group)) {
                        group = serviceImplClass.getName().replace('$', '.');
                    }
                    av.add(AnnotationElement.of("group", ByteCodes.annotationValue(group)));
                    cwAnnotations.add(java.lang.classfile.Annotation.of(
                            ClassDesc.ofDescriptor(ByteCodes.descriptor(DynForConsumer.class)), av));
                }
            }
            { // 必须设置成@AutoLoad(false)， 否则预编译打包后会被自动加载
                {
                    List<AnnotationElement> av = new ArrayList<>();
                    av.add(AnnotationElement.of("value", ByteCodes.annotationValue(false)));
                    cwAnnotations.add(java.lang.classfile.Annotation.of(
                            ClassDesc.ofDescriptor(ByteCodes.descriptor(AutoLoad.class)), av));
                }
            }
            if (pcw != null) { // 不一定是关联类
                cwInnerClasses.add(InnerClassInfo.of(
                        ByteCodes.classDesc(innerFullName),
                        Optional.ofNullable(newDynName).map(ByteCodes::classDesc),
                        Optional.ofNullable(innerClassName),
                        ACC_PUBLIC + ACC_STATIC));
            }
            {
                cw.withField("service", ClassDesc.ofDescriptor(newDynDesc), ACC_PRIVATE);
            }
            {
                cw.withMethodBody("<init>", MethodTypeDesc.ofDescriptor("(" + newDynDesc + ")V"), ACC_PUBLIC, mv -> {
                    Label l0 = mv.newLabel();
                    mv.labelBinding(l0);
                    mv.aload(0);
                    mv.invokespecial(
                            ByteCodes.classDesc("java/lang/Object"),
                            "<init>",
                            MethodTypeDesc.ofDescriptor("()V"),
                            false);
                    Label l1 = mv.newLabel();
                    mv.labelBinding(l1);
                    mv.aload(0);
                    mv.aload(1);
                    mv.putfield(ByteCodes.classDesc(innerFullName), "service", ClassDesc.ofDescriptor(newDynDesc));
                    Label l2 = mv.newLabel();
                    mv.labelBinding(l2);
                    mv.return_();
                    Label l3 = mv.newLabel();
                    mv.labelBinding(l3);
                    mv.localVariable(0, "this", ClassDesc.ofDescriptor("L" + innerFullName + ";"), l0, l3);
                    mv.localVariable(1, "service", ClassDesc.ofDescriptor(newDynDesc), l0, l3);
                });
            }
            {
                String methodName = newMethod == null ? method.getName() : newMethod.getMethodName();
                cw.withMethod(
                        "onMessage", MethodTypeDesc.ofDescriptor("(" + messageEventsDesc + ")V"), ACC_PUBLIC, mb -> {
                            if (!msgTypeDesc.equals(genericMsgTypeDesc))
                                mb.with(SignatureAttribute.of(MethodSignature.parseFrom("("
                                        + messageEventsDesc.replace(";", ("<" + genericMsgTypeDesc + ">;")) + ")V")));

                            mb.withCode(mv -> {
                                Label l0 = mv.newLabel();
                                Label l1 = mv.newLabel();
                                Label l2 = mv.newLabel();
                                mv.labelBinding(l0);
                                if (throwFlag) {
                                    mv.exceptionCatch(l0, l1, l2, ByteCodes.classDesc("java/lang/Throwable"));
                                }
                                mv.aload(0);
                                mv.getfield(
                                        ByteCodes.classDesc(innerFullName),
                                        "service",
                                        ClassDesc.ofDescriptor(newDynDesc));
                                mv.aload(1);
                                String methodDesc = ByteCodes.methodDescriptor(method);
                                String owner =
                                        pcw == null ? serviceImplClass.getName().replace('.', '/') : newDynName;
                                mv.invokevirtual(
                                        ByteCodes.classDesc(owner),
                                        methodName,
                                        MethodTypeDesc.ofDescriptor(methodDesc));
                                if (method.getReturnType() != void.class) {
                                    mv.pop();
                                }
                                mv.labelBinding(l1);
                                Label l3 = null, l4 = null;
                                if (throwFlag) {
                                    l3 = mv.newLabel();
                                    mv.goto_(l3);
                                    mv.labelBinding(l2);

                                    mv.astore(3);
                                    l4 = mv.newLabel();
                                    mv.labelBinding(l4);
                                    mv.new_(ByteCodes.classDesc("java/lang/RuntimeException"));
                                    mv.dup();
                                    mv.aload(3);
                                    mv.invokespecial(
                                            ByteCodes.classDesc("java/lang/RuntimeException"),
                                            "<init>",
                                            MethodTypeDesc.ofDescriptor("(Ljava/lang/Throwable;)V"),
                                            false);
                                    mv.athrow();
                                    mv.labelBinding(l3);
                                }
                                mv.return_();
                                Label l5 = mv.newLabel();
                                mv.labelBinding(l5);
                                mv.localVariable(0, "this", ClassDesc.ofDescriptor("L" + innerFullName + ";"), l0, l5);
                                mv.localVariable(1, "events", ClassDesc.ofDescriptor(messageEventsDesc), l0, l5);
                                if (throwFlag) {
                                    mv.localVariable(3, "e", ClassDesc.ofDescriptor("Ljava/lang/Throwable;"), l4, l3);
                                }
                            });
                        });
            }
            if (!cwAnnotations.isEmpty()) cw.with(RuntimeVisibleAnnotationsAttribute.of(cwAnnotations));
            if (!cwInnerClasses.isEmpty()) cw.with(InnerClassesAttribute.of(cwInnerClasses));
        });

        byte[] bytes = classBytes;
        if (consumerBytes == null) {
            consumerBytes = new LinkedHashMap<>();
        }
        consumerBytes.put(innerFullName, bytes);
    }

    @Override
    public void doAfterMethods(
            RedkaleClassLoader classLoader,
            ClassBuilder cw,
            String newDynName,
            String fieldPrefix,
            List<java.lang.classfile.Annotation> cwAnnotations) {
        if (Utility.isNotEmpty(consumerBytes)) {
            {
                List<AnnotationElement> av0 = new ArrayList<>();
                {
                    List<AnnotationValue> av1 = new ArrayList<>();
                    consumerBytes.forEach((innerFullName, bytes) -> {
                        String clzName = innerFullName.replace('/', '.');
                        Class clazz = classLoader.loadClass(clzName, bytes);
                        RedkaleClassLoader.putReflectionPublicConstructors(clazz, clzName);
                        {
                            List<AnnotationElement> av2 = new ArrayList<>();
                            av2.add(AnnotationElement.of(
                                    "consumer",
                                    ByteCodes.annotationValue(ByteCodes.constantType("L" + innerFullName + ";"))));
                            av1.add(AnnotationValue.ofAnnotation(java.lang.classfile.Annotation.of(
                                    ClassDesc.ofDescriptor(ByteCodes.descriptor(DynForMessaged.class)), av2)));
                        }
                    });
                    av0.add(AnnotationElement.of("value", AnnotationValue.ofArray(av1)));
                }
                cwAnnotations.add(java.lang.classfile.Annotation.of(
                        ClassDesc.ofDescriptor(ByteCodes.descriptor(DynForMessageds.class)), av0));
            }
        }
    }

    @Override
    public void doInstance(RedkaleClassLoader classLoader, ResourceFactory resourceFactory, Object service) {
        DynForMessaged[] dyns = service.getClass().getAnnotationsByType(DynForMessaged.class);
        if (Utility.isNotEmpty(dyns)) {
            try {
                for (DynForMessaged item : dyns) {
                    Class<? extends MessageConsumer> clazz = item.consumer();
                    MessageConsumer consumer = (MessageConsumer) clazz.getConstructors()[0].newInstance(service);
                    messageEngine.addMessageConsumer(consumer);
                }
            } catch (Exception e) {
                throw new RedkaleException(e);
            }
        }
    }
}
