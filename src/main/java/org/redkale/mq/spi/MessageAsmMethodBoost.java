/*
 *
 */
package org.redkale.mq.spi;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.ClassWriter;
import org.redkale.convert.ConvertFactory;
import org.redkale.inject.ResourceFactory;
import org.redkale.mq.MessageConext;
import org.redkale.mq.Messaged;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
public class MessageAsmMethodBoost extends AsmMethodBoost {

    private static final List<Class<? extends Annotation>> FILTER_ANN = List.of(Messaged.class);

    public MessageAsmMethodBoost(boolean remote, Class serviceType) {
        super(remote, serviceType);
    }

    @Override
    public List<Class<? extends Annotation>> filterMethodAnnotations(Method method) {
        return FILTER_ANN;
    }

    @Override
    public String doMethod(ClassWriter cw, String newDynName, String fieldPrefix, List filterAnns, Method method, String newMethodName) {
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
        return newMethodName;
    }

    @Override
    public void doInstance(ResourceFactory resourceFactory, Object service) {
    }

}
