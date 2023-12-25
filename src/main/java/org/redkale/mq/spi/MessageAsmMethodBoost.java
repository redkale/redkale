/*
 *
 */
package org.redkale.mq.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.redkale.asm.AnnotationVisitor;
import org.redkale.asm.AsmMethodBean;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.Asms;
import org.redkale.asm.ClassWriter;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.FieldVisitor;
import org.redkale.asm.Label;
import org.redkale.asm.MethodVisitor;
import org.redkale.asm.Opcodes;
import static org.redkale.asm.Opcodes.ACC_BRIDGE;
import static org.redkale.asm.Opcodes.ACC_PRIVATE;
import static org.redkale.asm.Opcodes.ACC_PUBLIC;
import static org.redkale.asm.Opcodes.ACC_STATIC;
import static org.redkale.asm.Opcodes.ACC_SUPER;
import static org.redkale.asm.Opcodes.ACC_SYNTHETIC;
import static org.redkale.asm.Opcodes.ALOAD;
import static org.redkale.asm.Opcodes.ASTORE;
import static org.redkale.asm.Opcodes.ATHROW;
import static org.redkale.asm.Opcodes.CHECKCAST;
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
import org.redkale.convert.ConvertFactory;
import org.redkale.inject.ResourceFactory;
import org.redkale.mq.MessageConext;
import org.redkale.mq.MessageConsumer;
import org.redkale.mq.Messaged;
import org.redkale.mq.ResourceConsumer;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class MessageAsmMethodBoost extends AsmMethodBoost {

    private static final List<Class<? extends Annotation>> FILTER_ANN = List.of(Messaged.class);

    private final AtomicInteger index = new AtomicInteger();

    private final MessageModuleEngine messageEngine;

    private Map<String, AsmMethodBean> methodBeans;

    private RedkaleClassLoader.DynBytesClassLoader newLoader;

    private List<Class<? extends MessageConsumer>> consumers;

    public MessageAsmMethodBoost(boolean remote, Class serviceType, MessageModuleEngine messageEngine) {
        super(remote, serviceType);
        this.messageEngine = messageEngine;
    }

    @Override
    public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
        return FILTER_ANN;
    }

    @Override
    public String doMethod(ClassLoader classLoader, ClassWriter cw,
        String newDynName, String fieldPrefix, List filterAnns, Method method, String newMethodName) {
        if (serviceType.getAnnotation(DynForMessage.class) != null) {
            return newMethodName;
        }
        Messaged messaged = method.getAnnotation(Messaged.class);
        if (messaged == null) {
            return newMethodName;
        }
        if (Modifier.isFinal(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            throw new RedkaleException("@" + Messaged.class.getSimpleName() + " cannot on final or static method, but on " + method);
        }
        if (!Modifier.isProtected(method.getModifiers()) && !Modifier.isPublic(method.getModifiers())) {
            throw new RedkaleException("@" + Messaged.class.getSimpleName() + " must on protected or public method, but on " + method);
        }
        int paramCount = method.getParameterCount();
        if (paramCount != 1 && paramCount != 2) {
            throw new RedkaleException("@" + Messaged.class.getSimpleName() + " must on one or two parameter method, but on " + method);
        }
        int paramKind = 1; // 1:单个MessageType;  2: MessageConext & MessageType; 3: MessageType & MessageConext;
        Type messageType;
        Type[] paramTypes = method.getGenericParameterTypes();
        if (paramCount == 1) {
            messageType = paramTypes[0];
            paramKind = 1;
        } else {
            if (paramTypes[0] == MessageConext.class) {
                messageType = paramTypes[1];
                paramKind = 2;
            } else if (paramTypes[1] == MessageConext.class) {
                messageType = paramTypes[0];
                paramKind = 3;
            } else {
                throw new RedkaleException("@" + Messaged.class.getSimpleName() + " on two-parameter method must contains "
                    + MessageConext.class.getSimpleName() + " parameter type, but on " + method);
            }
        }
        ConvertFactory factory = ConvertFactory.findConvert(messaged.convertType()).getFactory();
        factory.loadDecoder(messageType);
        if (newLoader == null) {
            newLoader = new RedkaleClassLoader.DynBytesClassLoader(classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader);
        }
        createInnerConsumer(cw, method, paramKind, TypeToken.typeToClass(messageType), messaged, newDynName, newMethodName);
        return newMethodName;
    }

    //paramKind:  1:单个MessageType;  2: MessageConext & MessageType; 3: MessageType & MessageConext;
    private void createInnerConsumer(ClassWriter parentCW, Method method, int paramKind,
        Class msgType, Messaged messaged, String newDynName, String newMethodName) {
        final String newDynDesc = "L" + newDynName + ";";
        final String innerClassName = "Dyn" + MessageConsumer.class.getSimpleName() + index.incrementAndGet();
        final String innerFullName = newDynName + "$" + innerClassName;
        final String msgTypeDesc = org.redkale.asm.Type.getDescriptor(TypeToken.primitiveToWrapper(msgType));
        final String messageConsumerName = MessageConsumer.class.getName().replace('.', '/');
        final String messageConsumerDesc = org.redkale.asm.Type.getDescriptor(MessageConsumer.class);
        final String messageConextDesc = org.redkale.asm.Type.getDescriptor(MessageConext.class);
        final boolean throwFlag = Utility.contains(method.getExceptionTypes(), e -> !RuntimeException.class.isAssignableFrom(e));

        if (methodBeans == null) {
            methodBeans = AsmMethodBoost.getMethodBeans(serviceType);
        }
        AsmMethodBean methodBean = AsmMethodBean.get(methodBeans, method);
        String genericMsgTypeDesc = msgTypeDesc;
        if (!msgType.isPrimitive()) {
            String methodSignature = methodBean.getSignature().replace(messageConextDesc, "");
            genericMsgTypeDesc = methodSignature.substring(1, methodSignature.lastIndexOf(')')); //获取()中的值
        }

        parentCW.visitInnerClass(innerFullName, newDynName, innerClassName, ACC_PUBLIC + ACC_STATIC);

        MethodVisitor mv;
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        //
        cw.visit(V11, ACC_PUBLIC + ACC_SUPER, innerFullName, "Ljava/lang/Object;" + messageConsumerDesc.replace(";", "<" + genericMsgTypeDesc + ">;"),
            "java/lang/Object", new String[]{messageConsumerName});
        {
            AnnotationVisitor av = cw.visitAnnotation(org.redkale.asm.Type.getDescriptor(ResourceConsumer.class), true);
            Asms.visitAnnotation(av, messaged);
            av.visitEnd();
        }
        cw.visitInnerClass(innerFullName, newDynName, innerClassName, ACC_PUBLIC + ACC_STATIC);
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
            String methodName = newMethodName == null ? method.getName() : newMethodName;
            mv = cw.visitMethod(ACC_PUBLIC, "onMessage", "(" + messageConextDesc + msgTypeDesc + ")V",
                msgTypeDesc.equals(genericMsgTypeDesc) ? null : ("(" + messageConextDesc + genericMsgTypeDesc + ")V"), null);
            Label l0 = new Label();
            Label l1 = new Label();
            Label l2 = new Label();
            if (throwFlag) {
                mv.visitTryCatchBlock(l0, l1, l2, "java/lang/Throwable");
                mv.visitLabel(l0);
            }
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, innerFullName, "service", newDynDesc);
            if (paramKind == 1) { //1: 单个MessageType; 
                mv.visitVarInsn(ALOAD, 2);
                Asms.visitPrimitiveVirtual(mv, msgType);
            } else if (paramKind == 2) { //2: MessageConext & MessageType;
                mv.visitVarInsn(ALOAD, 1);
                mv.visitVarInsn(ALOAD, 2);
                Asms.visitPrimitiveVirtual(mv, msgType);
            } else { //3: MessageType & MessageConext;
                mv.visitVarInsn(ALOAD, 2);
                Asms.visitPrimitiveVirtual(mv, msgType);
                mv.visitVarInsn(ALOAD, 1);
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, methodName, org.redkale.asm.Type.getMethodDescriptor(method), false);
            if (method.getReturnType() != void.class) {
                mv.visitInsn(POP);
            }
            mv.visitLabel(l1);
            Label l3 = null, l4 = null;
            if (throwFlag) {
                l3 = new Label();
                mv.visitJumpInsn(GOTO, l3);
                mv.visitLabel(l2);
                mv.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[]{"java/lang/Throwable"});
                mv.visitVarInsn(ASTORE, 3);
                l4 = new Label();
                mv.visitLabel(l4);
                mv.visitTypeInsn(NEW, "java/lang/RuntimeException");
                mv.visitInsn(DUP);
                mv.visitVarInsn(ALOAD, 3);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V", false);
                mv.visitInsn(ATHROW);
                mv.visitLabel(l3);
                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }
            mv.visitInsn(RETURN);
            Label l5 = new Label();
            mv.visitLabel(l5);
            mv.visitLocalVariable("this", "L" + innerFullName + ";", null, l0, l5, 0);
            mv.visitLocalVariable("context", messageConextDesc, null, l0, l5, 1);
            mv.visitLocalVariable("message", msgTypeDesc, msgTypeDesc.equals(genericMsgTypeDesc) ? null : genericMsgTypeDesc, l0, l5, 2);
            if (throwFlag) {
                mv.visitLocalVariable("e", "Ljava/lang/Throwable;", null, l4, l3, 3);
            }
            mv.visitMaxs(4, 4);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC, "onMessage", "(" + messageConextDesc + "Ljava/lang/Object;)V", null, null);
            mv.visitCode();
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitVarInsn(ALOAD, 2);
            mv.visitTypeInsn(CHECKCAST, "java/lang/String");
            mv.visitMethodInsn(INVOKEVIRTUAL, innerFullName, "onMessage", "(" + messageConextDesc + msgTypeDesc + ")V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(3, 3);
            mv.visitEnd();
        }
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        Class cz = newLoader.loadClass((innerFullName).replace('/', '.'), bytes);
        if (consumers == null) {
            consumers = new ArrayList<>();
        }
        consumers.add(cz);
        RedkaleClassLoader.putDynClass((innerFullName).replace('/', '.'), bytes, cz);
    }

    @Override
    public void doInstance(ResourceFactory resourceFactory, Object service) {
        DynForMessage[] dyns = service.getClass().getAnnotationsByType(DynForMessage.class);
        if (dyns.length < 1) {
            return;
        }
        try {
            if (Utility.isNotEmpty(consumers)) {
                for (Class<? extends MessageConsumer> clazz : consumers) {
                    MessageConsumer consumer = (MessageConsumer) clazz.getConstructors()[0].newInstance(service);
                    messageEngine.addMessageConsumer(consumer);
                }
            } else {
                for (DynForMessage item : dyns) {
                    Class<? extends MessageConsumer> clazz = item.value();
                    MessageConsumer consumer = (MessageConsumer) clazz.getConstructors()[0].newInstance(service);
                    messageEngine.addMessageConsumer(consumer);
                }
            }
        } catch (Exception e) {
            throw new RedkaleException(e);
        }
    }

}
