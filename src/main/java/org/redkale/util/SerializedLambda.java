/*
 *
 */
package org.redkale.util;

import java.io.*;
import java.lang.invoke.MethodHandleInfo;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 完全复制java.lang.invoke.SerializedLambda类源码，必须保持字段信息一样
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 *
 */
public class SerializedLambda implements Serializable {

    private static final long serialVersionUID = 8025925345765570181L;

    /**
     * The capturing class.
     */
    private final Class<?> capturingClass;

    /**
     * The functional interface class.
     */
    private final String functionalInterfaceClass;

    /**
     * The functional interface method name.
     */
    private final String functionalInterfaceMethodName;

    /**
     * The functional interface method signature.
     */
    private final String functionalInterfaceMethodSignature;

    /**
     * The implementation class.
     */
    private final String implClass;

    /**
     * The implementation method name.
     */
    private final String implMethodName;

    /**
     * The implementation method signature.
     */
    private final String implMethodSignature;

    /**
     * The implementation method kind.
     */
    private final int implMethodKind;

    /**
     * The instantiated method type.
     */
    private final String instantiatedMethodType;

    /**
     * The captured arguments.
     */
    @SuppressWarnings("serial") // Not statically typed as Serializable
    private final Object[] capturedArgs;

    /**
     * Create a {@code SerializedLambda} from the low-level information present
     * at the lambda factory site.
     *
     * @param capturingClass                     The class in which the lambda expression appears
     * @param functionalInterfaceClass           Name, in slash-delimited form, of static
     *                                           type of the returned lambda object
     * @param functionalInterfaceMethodName      Name of the functional interface
     *                                           method for the present at the
     *                                           lambda factory site
     * @param functionalInterfaceMethodSignature Signature of the functional
     *                                           interface method present at
     *                                           the lambda factory site
     * @param implMethodKind                     Method handle kind for the implementation method
     * @param implClass                          Name, in slash-delimited form, for the class holding
     *                                           the implementation method
     * @param implMethodName                     Name of the implementation method
     * @param implMethodSignature                Signature of the implementation method
     * @param instantiatedMethodType             The signature of the primary functional
     *                                           interface method after type variables
     *                                           are substituted with their instantiation
     *                                           from the capture site
     * @param capturedArgs                       The dynamic arguments to the lambda factory site,
     *                                           which represent variables captured by
     *                                           the lambda
     */
    public SerializedLambda(Class<?> capturingClass,
        String functionalInterfaceClass,
        String functionalInterfaceMethodName,
        String functionalInterfaceMethodSignature,
        int implMethodKind,
        String implClass,
        String implMethodName,
        String implMethodSignature,
        String instantiatedMethodType,
        Object[] capturedArgs) {
        this.capturingClass = capturingClass;
        this.functionalInterfaceClass = functionalInterfaceClass;
        this.functionalInterfaceMethodName = functionalInterfaceMethodName;
        this.functionalInterfaceMethodSignature = functionalInterfaceMethodSignature;
        this.implMethodKind = implMethodKind;
        this.implClass = implClass;
        this.implMethodName = implMethodName;
        this.implMethodSignature = implMethodSignature;
        this.instantiatedMethodType = instantiatedMethodType;
        this.capturedArgs = Objects.requireNonNull(capturedArgs).clone();
    }

    /**
     * Get the name of the class that captured this lambda.
     *
     * @return the name of the class that captured this lambda
     */
    public String getCapturingClass() {
        return capturingClass.getName().replace('.', '/');
    }

    /**
     * Get the name of the invoked type to which this
     * lambda has been converted
     *
     * @return the name of the functional interface class to which
     *         this lambda has been converted
     */
    public String getFunctionalInterfaceClass() {
        return functionalInterfaceClass;
    }

    /**
     * Get the name of the primary method for the functional interface
     * to which this lambda has been converted.
     *
     * @return the name of the primary methods of the functional interface
     */
    public String getFunctionalInterfaceMethodName() {
        return functionalInterfaceMethodName;
    }

    /**
     * Get the signature of the primary method for the functional
     * interface to which this lambda has been converted.
     *
     * @return the signature of the primary method of the functional
     *         interface
     */
    public String getFunctionalInterfaceMethodSignature() {
        return functionalInterfaceMethodSignature;
    }

    /**
     * Get the name of the class containing the implementation
     * method.
     *
     * @return the name of the class containing the implementation
     *         method
     */
    public String getImplClass() {
        return implClass;
    }

    /**
     * Get the name of the implementation method.
     *
     * @return the name of the implementation method
     */
    public String getImplMethodName() {
        return implMethodName;
    }

    /**
     * Get the signature of the implementation method.
     *
     * @return the signature of the implementation method
     */
    public String getImplMethodSignature() {
        return implMethodSignature;
    }

    /**
     * Get the method handle kind (see {@link MethodHandleInfo}) of
     * the implementation method.
     *
     * @return the method handle kind of the implementation method
     */
    public int getImplMethodKind() {
        return implMethodKind;
    }

    /**
     * Get the signature of the primary functional interface method
     * after type variables are substituted with their instantiation
     * from the capture site.
     *
     * @return the signature of the primary functional interface method
     *         after type variable processing
     */
    public final String getInstantiatedMethodType() {
        return instantiatedMethodType;
    }

    /**
     * Get the count of dynamic arguments to the lambda capture site.
     *
     * @return the count of dynamic arguments to the lambda capture site
     */
    public int getCapturedArgCount() {
        return capturedArgs.length;
    }

    /**
     * Get a dynamic argument to the lambda capture site.
     *
     * @param i the argument to capture
     *
     * @return a dynamic argument to the lambda capture site
     */
    public Object getCapturedArg(int i) {
        return capturedArgs[i];
    }

    @Override
    public String toString() {
        String implKind = MethodHandleInfo.referenceKindToString(implMethodKind);
        return String.format("SerializedLambda[%s=%s, %s=%s.%s:%s, "
            + "%s=%s %s.%s:%s, %s=%s, %s=%d]",
            "capturingClass", capturingClass,
            "functionalInterfaceMethod", functionalInterfaceClass,
            functionalInterfaceMethodName,
            functionalInterfaceMethodSignature,
            "implementation",
            implKind,
            implClass, implMethodName, implMethodSignature,
            "instantiatedMethodType", instantiatedMethodType,
            "numCaptured", capturedArgs.length);
    }

    private static final ConcurrentHashMap<Class, SerializedLambda> cache = new ConcurrentHashMap();

    public static <T> String readColumn(Serializable func) {
        return readFieldName(readLambda(func).getImplMethodName());
    }

    public static SerializedLambda readLambda(Serializable func) {
        if (!func.getClass().isSynthetic()) { //必须是Lambda表达式的合成类
            throw new RedkaleException("Not a synthetic lambda class");
        }
        return cache.computeIfAbsent(func.getClass(), clazz -> {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(out);
                oos.writeObject(func);
                ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray())) {
                    @Override
                    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                        Class<?> clazz = super.resolveClass(desc);
                        return clazz == java.lang.invoke.SerializedLambda.class ? SerializedLambda.class : clazz;
                    }
                };
                return (SerializedLambda) in.readObject();
            } catch (Exception e) {
                throw new RedkaleException(e);
            }
        });
    }

    public static String readFieldName(String methodName) {
        String name;
        if (methodName.startsWith("is")) {
            name = methodName.substring(2);
        } else if (methodName.startsWith("get") || methodName.startsWith("set")) {
            name = methodName.substring(3);
        } else {
            name = methodName;
        }
        if (name.length() < 2) {
            return name.toLowerCase(Locale.ENGLISH);
        } else if (Character.isUpperCase(name.charAt(1))) {
            return name;
        } else {
            return name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        }
    }
}
