/*
 *
 */
package org.redkale.locked.spi;

import static java.lang.classfile.ClassFile.*;

import java.lang.annotation.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;
import org.redkale.bytecode.ByteCodes;
import org.redkale.bytecode.CodeMethodBean;
import org.redkale.bytecode.CodeMethodBoost;
import org.redkale.bytecode.CodeNewMethod;
import org.redkale.inject.ResourceFactory;
import org.redkale.locked.Locked;
import org.redkale.service.LoadMode;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.RedkaleException;

/** @author zhangjx */
public class LockedCodeMethodBoost extends CodeMethodBoost<Object> {

    private static final List<Class<? extends Annotation>> FILTER_ANN = List.of(Locked.class, DynForLocked.class);

    public LockedCodeMethodBoost(boolean remote, Class serviceType) {
        super(remote, serviceType);
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
            final CodeNewMethod newMethod) {
        Locked locked = method.getAnnotation(Locked.class);
        if (locked == null) {
            return newMethod;
        }
        if (!LoadMode.matches(remote, locked.mode())) {
            return newMethod;
        }
        if (method.getAnnotation(DynForLocked.class) != null) {
            return newMethod;
        }
        if (Modifier.isFinal(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
            throw new RedkaleException(
                    "@" + Locked.class.getSimpleName() + " can not on final or static method, but on " + method);
        }
        if (!Modifier.isProtected(method.getModifiers()) && !Modifier.isPublic(method.getModifiers())) {
            throw new RedkaleException(
                    "@" + Locked.class.getSimpleName() + " must on protected or public method, but on " + method);
        }

        final String rsMethodName = method.getName() + "_afterLocked";
        final String dynFieldName = fieldPrefix + "_" + method.getName() + LockedAction.class.getSimpleName()
                + fieldIndex.incrementAndGet();
        final CodeMethodBean methodBean = getMethodBean(method);
        createMethod(cw, method, newMethod, methodBean, mb -> {
            List<java.lang.classfile.Annotation> annotations = new ArrayList<>();
            List<AnnotationElement> elements = new ArrayList<>(
                    ByteCodes.annotation(DynForLocked.class, locked).elements());
            elements.add(AnnotationElement.ofString("dynField", dynFieldName));
            annotations.add(java.lang.classfile.Annotation.of(ByteCodes.constantType(DynForLocked.class), elements));
            visitRawAnnotation(method, newMethod, mb, Locked.class, filterAnns, annotations);
            mb.with(RuntimeVisibleAnnotationsAttribute.of(annotations));
            mb.withCode(code -> {
                Label start = code.newLabel();
                code.labelBinding(start).aload(0);
                List<Integer> slots = visitVarInsnParamTypes(code, method, 0);
                code.invokespecial(
                        ByteCodes.classDesc(newDynName),
                        rsMethodName,
                        MethodTypeDesc.ofDescriptor(ByteCodes.methodDescriptor(method)));
                visitInsnReturn(code, method, start, slots, methodBean);
            });
        });
        return new CodeNewMethod(rsMethodName, ACC_PRIVATE);
    }

    @Override
    public void doAfterMethods(
            RedkaleClassLoader classLoader,
            ClassBuilder cw,
            String newDynName,
            String fieldPrefix,
            List<java.lang.classfile.Annotation> cwAnnotations) {
        // do nothing
    }

    @Override
    public void doInstance(RedkaleClassLoader classLoader, ResourceFactory resourceFactory, Object service) {
        // do nothing
    }
}
