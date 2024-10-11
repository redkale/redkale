/*
 *
 */
package org.redkale.mq.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.annotation.AutoLoad;
import org.redkale.annotation.Nonnull;
import org.redkale.asm.AnnotationVisitor;
import org.redkale.asm.AsmMethodBean;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.AsmNewMethod;
import org.redkale.asm.Asms;
import org.redkale.asm.ClassWriter;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.FieldVisitor;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import org.redkale.asm.Opcodes;
import static org.redkale.asm.Opcodes.ACC_PRIVATE;
import static org.redkale.asm.Opcodes.ACC_PUBLIC;
import static org.redkale.asm.Opcodes.ACC_STATIC;
import static org.redkale.asm.Opcodes.ACC_SUPER;
import static org.redkale.asm.Opcodes.ALOAD;
import static org.redkale.asm.Opcodes.ASTORE;
import static org.redkale.asm.Opcodes.ATHROW;
import static org.redkale.asm.Opcodes.DUP;
import static org.redkale.asm.Opcodes.GETFIELD;
import static org.redkale.asm.Opcodes.GOTO;
import static org.redkale.asm.Opcodes.INVOKESPECIAL;
import static org.redkale.asm.Opcodes.INVOKEVIRTUAL;
import static org.redkale.asm.Opcodes.NEW;
import static org.redkale.asm.Opcodes.POP;
import static org.redkale.asm.Opcodes.PUTFIELD;
import static org.redkale.asm.Opcodes.RETURN;
import static org.redkale.asm.Opcodes.V11;
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
public class MessageAsmMethodBoost extends AsmMethodBoost {

    private static final List<Class<? extends Annotation>> FILTER_ANN = List.of(Messaged.class);

    private final AtomicInteger index = new AtomicInteger();

    private final MessageModuleEngine messageEngine;

    private Map<String, AsmMethodBean> methodBeans;

    Map<String, byte[]> consumerBytes;

    public MessageAsmMethodBoost(boolean remote, Class serviceType, MessageModuleEngine messageEngine) {
        super(remote, serviceType);
        this.messageEngine = messageEngine;
    }

    @Override
    public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
        return FILTER_ANN;
    }

    @Override
    public AsmNewMethod doMethod(
            RedkaleClassLoader classLoader,
            ClassWriter cw,
            Class serviceImplClass,
            String newDynName,
            String fieldPrefix,
            List filterAnns,
            Method method,
            AsmNewMethod newMethod) {
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
            ClassWriter cw, Method method, Class serviceImplClass, List filterAnns, AsmNewMethod newMethod) {
        final String serviceName = serviceImplClass.getName().replace('.', '/');
        final AsmMethodBean methodBean = getMethodBean(method);
        final MethodVisitor mv = createMethodVisitor(cw, method, newMethod, methodBean);
        visitRawAnnotation(method, newMethod, mv, Messaged.class, filterAnns);
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 0);
        List<Integer> insns = visitVarInsnParamTypes(mv, method, 0);
        String methodDesc = org.redkale.asm.Type.getMethodDescriptor(method);
        mv.visitMethodInsn(INVOKESPECIAL, serviceName, method.getName(), methodDesc, false);
        visitInsnReturn(mv, method, l0, insns, methodBean);
        int max = method.getParameterCount() + 1;
        mv.visitMaxs(max, max);
        mv.visitEnd();
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
            ClassWriter pcw,
            @Nonnull Class serviceImplClass,
            @Nonnull Method method,
            Type messageType,
            Messaged messaged,
            String newDynName,
            AsmNewMethod newMethod) {
        final String newDynDesc =
                pcw == null ? org.redkale.asm.Type.getDescriptor(serviceImplClass) : ("L" + newDynName + ";");
        final String innerClassName = "Dyn" + MessageConsumer.class.getSimpleName() + index.incrementAndGet();
        final String innerFullName = newDynName + (pcw == null ? "" : "$") + innerClassName;
        final Class msgTypeClass = TypeToken.typeToClass(messageType);
        final String msgTypeDesc = org.redkale.asm.Type.getDescriptor(msgTypeClass);
        final String messageConsumerName = MessageConsumer.class.getName().replace('.', '/');
        final String messageConsumerDesc = org.redkale.asm.Type.getDescriptor(MessageConsumer.class);
        final String messageEventsDesc = org.redkale.asm.Type.getDescriptor(MessageEvent[].class);
        final boolean throwFlag =
                Utility.contains(method.getExceptionTypes(), e -> !RuntimeException.class.isAssignableFrom(e));

        if (methodBeans == null) {
            methodBeans = AsmMethodBoost.getMethodBeans(serviceType);
        }
        AsmMethodBean methodBean = AsmMethodBean.get(methodBeans, method);
        String genericMsgTypeDesc = msgTypeDesc;
        if (Utility.isNotEmpty(methodBean.getSignature())) {
            String methodSignature = methodBean.getSignature();
            methodSignature = methodSignature.substring(0, methodSignature.lastIndexOf(')') + 1) + "V";
            int start = methodSignature.indexOf('<') + 1;
            genericMsgTypeDesc = methodSignature.substring(start, methodSignature.lastIndexOf('>')); // 获取<>中的值
        }
        if (pcw != null) { // 不一定是关联类
            pcw.visitInnerClass(innerFullName, newDynName, innerClassName, ACC_PUBLIC + ACC_STATIC);
        }
        MethodVisitor mv;
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        //
        cw.visit(
                V11,
                ACC_PUBLIC + ACC_SUPER,
                innerFullName,
                "Ljava/lang/Object;" + messageConsumerDesc.replace(";", "<" + genericMsgTypeDesc + ">;"),
                "java/lang/Object",
                new String[] {messageConsumerName});
        {
            AnnotationVisitor av = cw.visitAnnotation(org.redkale.asm.Type.getDescriptor(ResourceConsumer.class), true);
            Asms.visitAnnotation(av, ResourceConsumer.class, messaged);
            av.visitEnd();
        }
        { // 设置DynForConsumer
            AnnotationVisitor av = cw.visitAnnotation(org.redkale.asm.Type.getDescriptor(DynForConsumer.class), true);
            String group = messaged.group();
            if (Utility.isBlank(group)) {
                group = serviceImplClass.getName().replace('$', '.');
            }
            av.visit("group", group);
            av.visitEnd();
        }
        { // 必须设置成@AutoLoad(false)， 否则预编译打包后会被自动加载
            AnnotationVisitor av = cw.visitAnnotation(org.redkale.asm.Type.getDescriptor(AutoLoad.class), true);
            av.visit("value", false);
            av.visitEnd();
        }
        if (pcw != null) { // 不一定是关联类
            cw.visitInnerClass(innerFullName, newDynName, innerClassName, ACC_PUBLIC + ACC_STATIC);
        }
        {
            FieldVisitor fv = cw.visitField(ACC_PRIVATE, "service", newDynDesc, null, null);
            fv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "(" + newDynDesc + ")V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            Label l1 = new Label();
            mv.visitLabel(l1);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(PUTFIELD, innerFullName, "service", newDynDesc);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitInsn(RETURN);
            Label l3 = new Label();
            mv.visitLabel(l3);
            mv.visitLocalVariable("this", "L" + innerFullName + ";", null, l0, l3, 0);
            mv.visitLocalVariable("service", newDynDesc, null, l0, l3, 1);
            mv.visitMaxs(2, 2);
            mv.visitEnd();
        }
        {
            String methodName = newMethod == null ? method.getName() : newMethod.getMethodName();
            mv = cw.visitMethod(
                    ACC_PUBLIC,
                    "onMessage",
                    "(" + messageEventsDesc + ")V",
                    msgTypeDesc.equals(genericMsgTypeDesc)
                            ? null
                            : ("(" + messageEventsDesc.replace(";", ("<" + genericMsgTypeDesc + ">;")) + ")V"),
                    null);
            Label l0 = new Label();
            Label l1 = new Label();
            Label l2 = new Label();
            if (throwFlag) {
                mv.visitTryCatchBlock(l0, l1, l2, "java/lang/Throwable");
                mv.visitLabel(l0);
            }
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, innerFullName, "service", newDynDesc);
            mv.visitVarInsn(ALOAD, 1);
            String methodDesc = org.redkale.asm.Type.getMethodDescriptor(method);
            String owner = pcw == null ? serviceImplClass.getName().replace('.', '/') : newDynName;
            mv.visitMethodInsn(INVOKEVIRTUAL, owner, methodName, methodDesc, false);
            if (method.getReturnType() != void.class) {
                mv.visitInsn(POP);
            }
            mv.visitLabel(l1);
            Label l3 = null, l4 = null;
            if (throwFlag) {
                l3 = new Label();
                mv.visitJumpInsn(GOTO, l3);
                mv.visitLabel(l2);
                mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {"java/lang/Throwable"});
                mv.visitVarInsn(ASTORE, 3);
                l4 = new Label();
                mv.visitLabel(l4);
                mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
                mv.visitInsn(DUP);
                mv.visitVarInsn(ALOAD, 3);
                mv.visitMethodInsn(
                        INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V", false);
                mv.visitInsn(ATHROW);
                mv.visitLabel(l3);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitInsn(RETURN);
            Label l5 = new Label();
            mv.visitLabel(l5);
            mv.visitLocalVariable("this", "L" + innerFullName + ";", null, l0, l5, 0);
            mv.visitLocalVariable("events", messageEventsDesc, null, l0, l5, 1);
            if (throwFlag) {
                mv.visitLocalVariable("e", "Ljava/lang/Throwable;", null, l4, l3, 3);
            }
            mv.visitMaxs(4, 4);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        if (consumerBytes == null) {
            consumerBytes = new LinkedHashMap<>();
        }
        consumerBytes.put(innerFullName, bytes);
    }

    @Override
    public void doAfterMethods(RedkaleClassLoader classLoader, ClassWriter cw, String newDynName, String fieldPrefix) {
        if (Utility.isNotEmpty(consumerBytes)) {
            AnnotationVisitor av0 = cw.visitAnnotation(org.redkale.asm.Type.getDescriptor(DynForMessageds.class), true);
            AnnotationVisitor av1 = av0.visitArray("value");
            consumerBytes.forEach((innerFullName, bytes) -> {
                String clzName = innerFullName.replace('/', '.');
                Class clazz = classLoader.loadClass(clzName, bytes);
                RedkaleClassLoader.putDynClass(clzName, bytes, clazz);
                RedkaleClassLoader.putReflectionPublicConstructors(clazz, clzName);
                AnnotationVisitor av2 =
                        av1.visitAnnotation(null, org.redkale.asm.Type.getDescriptor(DynForMessaged.class));
                av2.visit("consumer", org.redkale.asm.Type.getType("L" + innerFullName + ";"));
                av2.visitEnd();
            });
            av1.visitEnd();
            av0.visitEnd();
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
