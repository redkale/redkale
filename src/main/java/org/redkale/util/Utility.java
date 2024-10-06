/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.util;

import java.io.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.math.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.channels.CompletionHandler;
import java.nio.charset.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import org.redkale.annotation.*;
import org.redkale.convert.json.JsonConvert;

/**
 * 常见操作的工具类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public final class Utility {

    private static final char hex[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private static final int CPUS =
            Integer.getInteger("redkale.cpus", Runtime.getRuntime().availableProcessors());

    private static final int MAX_POW2 = 1 << 30;

    private static final ConcurrentHashMap<Class, String> lambdaFieldNameCache = new ConcurrentHashMap();

    private static final ConcurrentHashMap<Class, Class> lambdaClassNameCache = new ConcurrentHashMap();

    private static final Class JAVA_RECORD_CLASS;

    private static final SecureRandom random = new SecureRandom();

    private static final IntFunction<CompletableFuture[]> futureArrayFunc = c -> new CompletableFuture[c];

    private static final IntFunction<Serializable[]> serialArrayFunc = v -> new Serializable[v];

    static {
        Class clz = null;
        try {
            clz = Thread.currentThread().getContextClassLoader().loadClass("java.lang.Record");
        } catch (Throwable t) { // JDK14以下版本会异常
        }
        JAVA_RECORD_CLASS = clz;
    }

    private static final MethodHandles.Lookup defaultLookup;

    static {
        MethodHandles.Lookup defaultLookup0 = null;
        try {
            Field implLookup = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            implLookup.setAccessible(true);
            defaultLookup0 = (MethodHandles.Lookup) implLookup.get(null);
            RedkaleClassLoader.putReflectionField(MethodHandles.Lookup.class.getName(), implLookup);
        } catch (Throwable e) {
            defaultLookup0 = MethodHandles.lookup();
        }
        defaultLookup = defaultLookup0;
    }

    private static final Executor defaultExecutorConsumer = command -> command.run();

    // org.redkale.util.JDK21VirtualThreadLocal
    private static final Function<Supplier, ThreadLocal> virtualThreadLocalFunction;

    // org.redkale.util.JDK21VirtualThreadFactory
    private static final Function<String, ThreadFactory> virtualThreadFactoryFunction;

    // org.redkale.util.JDK21VirtualPoolFunction
    private static final Function<String, ExecutorService> virtualPoolFunction;

    // org.redkale.util.JDK21VirtualExecutor
    private static final Executor virtualExecutorConsumer;

    // org.redkale.util.SignalShutDown
    private static final String consumerSignalShutdownBinary =
            "cafebabe00000037006b0a0019003a090018003b07003c08003d08003e08003f0800400800410800420800430800440800450700460a000d00470a000d004807004907004a0a000d004b0a000d004c12000000500b001600510700520a0018005307005407005507005601001073687574646f776e436f6e73756d657201001d4c6a6176612f7574696c2f66756e6374696f6e2f436f6e73756d65723b0100095369676e61747572650100314c6a6176612f7574696c2f66756e6374696f6e2f436f6e73756d65723c4c6a6176612f6c616e672f537472696e673b3e3b0100063c696e69743e010003282956010004436f646501000f4c696e654e756d6265725461626c650100124c6f63616c5661726961626c655461626c65010004746869730100214c6f72672f7265646b616c652f7574696c2f5369676e616c53687574446f776e3b010006616363657074010020284c6a6176612f7574696c2f66756e6374696f6e2f436f6e73756d65723b29560100037369670100124c6a6176612f6c616e672f537472696e673b010004736967730100135b4c6a6176612f6c616e672f537472696e673b010008636f6e73756d65720100164c6f63616c5661726961626c65547970655461626c6501000d537461636b4d61705461626c6507002b0100104d6574686f64506172616d6574657273010034284c6a6176612f7574696c2f66756e6374696f6e2f436f6e73756d65723c4c6a6176612f6c616e672f537472696e673b3e3b295601000668616e646c65010014284c73756e2f6d6973632f5369676e616c3b29560100114c73756e2f6d6973632f5369676e616c3b010006736967737472010015284c6a6176612f6c616e672f4f626a6563743b295601007a4c6a6176612f6c616e672f4f626a6563743b4c6a6176612f7574696c2f66756e6374696f6e2f436f6e73756d65723c4c6a6176612f7574696c2f66756e6374696f6e2f436f6e73756d65723c4c6a6176612f6c616e672f537472696e673b3e3b3e3b4c73756e2f6d6973632f5369676e616c48616e646c65723b01000a536f7572636546696c650100135369676e616c53687574446f776e2e6a6176610c001f00200c001b001c0100106a6176612f6c616e672f537472696e670100034855500100045445524d010003494e54010004515549540100044b494c4c01000454535450010004555352310100045553523201000453544f5001000f73756e2f6d6973632f5369676e616c0c001f00570c003200580100136a6176612f6c616e672f457863657074696f6e0100136a6176612f6c616e672f5468726f7761626c650c0059005a0c005b005c010010426f6f7473747261704d6574686f64730f06005d08005e0c005f00600c0026003601001b6a6176612f7574696c2f66756e6374696f6e2f436f6e73756d65720c0026002701001f6f72672f7265646b616c652f7574696c2f5369676e616c53687574446f776e0100106a6176612f6c616e672f4f626a65637401001673756e2f6d6973632f5369676e616c48616e646c6572010015284c6a6176612f6c616e672f537472696e673b2956010043284c73756e2f6d6973632f5369676e616c3b4c73756e2f6d6973632f5369676e616c48616e646c65723b294c73756e2f6d6973632f5369676e616c48616e646c65723b0100076765744e616d6501001428294c6a6176612f6c616e672f537472696e673b0100096765744e756d6265720100032829490a00610062010005012c012c010100176d616b65436f6e63617457697468436f6e7374616e7473010038284c73756e2f6d6973632f5369676e616c3b4c6a6176612f6c616e672f537472696e673b49294c6a6176612f6c616e672f537472696e673b0700630c005f00670100246a6176612f6c616e672f696e766f6b652f537472696e67436f6e636174466163746f72790700690100064c6f6f6b757001000c496e6e6572436c6173736573010098284c6a6176612f6c616e672f696e766f6b652f4d6574686f6448616e646c6573244c6f6f6b75703b4c6a6176612f6c616e672f537472696e673b4c6a6176612f6c616e672f696e766f6b652f4d6574686f64547970653b4c6a6176612f6c616e672f537472696e673b5b4c6a6176612f6c616e672f4f626a6563743b294c6a6176612f6c616e672f696e766f6b652f43616c6c536974653b07006a0100256a6176612f6c616e672f696e766f6b652f4d6574686f6448616e646c6573244c6f6f6b757001001e6a6176612f6c616e672f696e766f6b652f4d6574686f6448616e646c657300210018001900020016001a00010002001b001c0001001d00000002001e00040001001f0020000100210000002f00010001000000052ab70001b10000000200220000000600010000000c00230000000c000100000005002400250000000100260027000300210000014a000400080000006f2a2bb500021009bd0003590312045359041205535905120653590612075359071208535908120953591006120a53591007120b53591008120c534d2c4e2dbe360403360515051504a200222d1505323a06bb000d591906b7000e2ab8000f57a700053a07840501a7ffdda700044db100020051005f006200100005006a006d0011000400220000002a000a0000001200050014003b001500510017005f00190062001800640015006a001c006d001b006e001d00230000002a000400510013002800290006003b002f002a002b00020000006f0024002500000000006f002c001c0001002d0000000c00010000006f002c001e0001002e000000470006ff0044000607001807001607002f07002f01010000ff001d000707001807001607002f07002f01010700030001070010fa0001ff000500020700180700160000420700110000300000000501002c0000001d0000000200310021003200330002002100000060000300030000001a2b2bb600122bb60013ba001400004d2ab400022cb900150200b10000000200220000000e000300000021000f00220019002300230000002000030000001a0024002500000000001a002800340001000f000b0035002900020030000000050100280000104100260036000200210000003300020002000000092a2bc00016b60017b10000000200220000000600010000000c00230000000c00010000000900240025000000300000000501002c10000004001d000000020037003800000002003900660000000a00010064006800650019004d000000080001004e0001004f";

    private static final Consumer<Consumer<String>> signalShutdownConsumer;

    // -------------------------------------------------------------------------------

    private static final Function<String, byte[]> strByteFunction;

    private static final Predicate<String> strLatin1Function;

    private static final ReentrantLock clientLock = new ReentrantLock();

    // 是否native-image运行环境
    private static final boolean NATIVE_IMAGE_ENV =
            "executable".equals(System.getProperty("org.graalvm.nativeimage.kind"));

    private static HttpClient httpClient;

    private static final ScheduledThreadPoolExecutor delayer;

    // private static final javax.net.ssl.SSLContext DEFAULTSSL_CONTEXT;
    // private static final javax.net.ssl.HostnameVerifier defaultVerifier = (s, ss) -> true;
    //
    static {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");

        (delayer = new ScheduledThreadPoolExecutor(1, r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("RedkaleFutureDelayScheduler");
                    return t;
                }))
                .setRemoveOnCancelPolicy(true);

        Function<String, byte[]> strByteFunction0 = null;
        Predicate<String> strLatin1Function0 = null;

        Consumer<Consumer<String>> signalShutdownConsumer0 = null;

        Executor virtualExecutorConsumer0 = null;
        Function<String, ExecutorService> virtualPoolFunction0 = null;
        Function<Supplier, ThreadLocal> virtualThreadLocalFunction0 = null;
        Function<String, ThreadFactory> virtualThreadFactoryFunction0 = null;

        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try { // Jdk21Inners
            String virtualName = "org.redkale.util.Jdk21Inners";
            Class virtualClazz = loader.loadClass(virtualName);
            Method method = virtualClazz.getMethod("createExecutor");
            virtualExecutorConsumer0 = (Executor) method.invoke(null);
            RedkaleClassLoader.putReflectionMethod(virtualName, method);

            method = virtualClazz.getMethod("createPoolFunction");
            virtualPoolFunction0 = (Function) method.invoke(null);
            RedkaleClassLoader.putReflectionMethod(virtualName, method);

            method = virtualClazz.getMethod("createThreadLocalFunction");
            virtualThreadLocalFunction0 = (Function) method.invoke(null);
            RedkaleClassLoader.putReflectionMethod(virtualName, method);

            method = virtualClazz.getMethod("createThreadFactoryFunction");
            virtualThreadFactoryFunction0 = (Function) method.invoke(null);
            RedkaleClassLoader.putReflectionMethod(virtualName, method);
        } catch (Throwable t) {
            // do nothing
        }
        try {
            // String-LATIN1
            MethodHandles.Lookup lookup = defaultLookup;
            VarHandle compactHandle = lookup.findStaticVarHandle(String.class, "COMPACT_STRINGS", boolean.class);
            final boolean compact = (Boolean) compactHandle.get(null);
            VarHandle coderHandle = lookup.findVarHandle(String.class, "coder", byte.class);
            VarHandle valueHandle = lookup.findVarHandle(String.class, "value", byte[].class);
            // LATIN1:0  UTF16:1
            strLatin1Function0 = compact ? (String t) -> (Byte) coderHandle.get(t) == 0 : (String t) -> false;
            strByteFunction0 = (String t) -> (byte[]) valueHandle.get(t);

            // signalShutdown
            Class<Consumer<Consumer<String>>> shutdownClazz1 = null;
            try {
                shutdownClazz1 = (Class) loader.loadClass("org.redkale.util.SignalShutDown");
            } catch (Throwable t) {
                // do nothing
            }
            if (shutdownClazz1 == null) {
                byte[] classBytes = hexToBin(consumerSignalShutdownBinary);
                shutdownClazz1 = (Class<Consumer<Consumer<String>>>)
                        new ClassLoader(loader) {
                            public final Class<?> loadClass(String name, byte[] b) {
                                return defineClass(name, b, 0, b.length);
                            }
                        }.loadClass("org.redkale.util.SignalShutDown", classBytes);
                RedkaleClassLoader.putDynClass(shutdownClazz1.getName(), classBytes, shutdownClazz1);
                RedkaleClassLoader.putReflectionDeclaredConstructors(shutdownClazz1, shutdownClazz1.getName());
                signalShutdownConsumer0 = shutdownClazz1.getConstructor().newInstance();
            }
        } catch (Throwable e) { // 不会发生
            // do nothing
            e.printStackTrace();
        }
        strByteFunction = strByteFunction0;
        strLatin1Function = strLatin1Function0;
        signalShutdownConsumer = signalShutdownConsumer0;
        virtualPoolFunction = virtualPoolFunction0;
        virtualThreadLocalFunction = virtualThreadLocalFunction0;
        virtualThreadFactoryFunction = virtualThreadFactoryFunction0;
        virtualExecutorConsumer = virtualExecutorConsumer0;

        //        try {
        //            DEFAULTSSL_CONTEXT = javax.net.ssl.SSLContext.getInstance("SSL");
        //            DEFAULTSSL_CONTEXT.init(null, new javax.net.ssl.TrustManager[]{new
        // javax.net.ssl.X509TrustManager() {
        //                @Override
        //                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        //                    return null;
        //                }
        //
        //                @Override
        //                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType)
        // throws java.security.cert.CertificateException {
        //                }
        //
        //                @Override
        //                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType)
        // throws java.security.cert.CertificateException {
        //                }
        //            }}, null);
        //        } catch (Exception e) {
        //            throw new RedkaleException(e); //不会发生
        //        }
    }

    private Utility() {}

    public static int cpus() {
        return CPUS;
    }

    public static MethodHandles.Lookup lookup() {
        return defaultLookup;
    }

    public static boolean inNativeImage() {
        return NATIVE_IMAGE_ENV;
    }

    public static Function<String, ExecutorService> virtualExecutorFunction() {
        return virtualPoolFunction;
    }

    public static <T> ThreadLocal<T> withInitialThreadLocal(Supplier<T> supplier) {
        return virtualThreadLocalFunction == null
                ? ThreadLocal.withInitial(supplier)
                : virtualThreadLocalFunction.apply(supplier);
    }

    public static Function<String, ThreadFactory> virtualFactoryFunction() {
        return virtualThreadFactoryFunction;
    }

    public static ThreadFactory newThreadFactory(final String name) {
        if (virtualThreadFactoryFunction == null) {
            if (isEmpty(name) || !name.contains("%s")) {
                return (Runnable r) -> {
                    final Thread t = isEmpty(name) ? new Thread(r) : new Thread(r, name);
                    t.setDaemon(true);
                    return t;
                };
            } else {
                AtomicInteger counter = new AtomicInteger();
                return (Runnable r) -> {
                    final Thread t = new Thread(r, String.format(name, counter.incrementAndGet()));
                    t.setDaemon(true);
                    return t;
                };
            }
        } else { // 虚拟线程工厂
            return virtualThreadFactoryFunction.apply(
                    isEmpty(name)
                            ? null
                            : (name.contains("%s") ? String.format(name, "Virtual") : (name + "-Virtual")));
        }
    }

    public static ScheduledThreadPoolExecutor newScheduledExecutor(int corePoolSize) {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(corePoolSize, newThreadFactory(null));
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    public static ScheduledThreadPoolExecutor newScheduledExecutor(int corePoolSize, String name) {
        ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(corePoolSize, newThreadFactory(name));
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    public static ScheduledThreadPoolExecutor newScheduledExecutor(
            int corePoolSize, String name, RejectedExecutionHandler handler) {
        ScheduledThreadPoolExecutor scheduler =
                new ScheduledThreadPoolExecutor(corePoolSize, newThreadFactory(name), handler);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    public static Consumer<Consumer<String>> signalShutdownConsumer() {
        return signalShutdownConsumer;
    }

    public static IntFunction<Serializable[]> serialArrayFunc() {
        return serialArrayFunc;
    }

    public static IntFunction<CompletableFuture[]> futureArrayFunc() {
        return futureArrayFunc;
    }

    public static Executor defaultExecutor() {
        return virtualExecutorConsumer == null ? defaultExecutorConsumer : virtualExecutorConsumer;
    }

    /**
     * 构建method的唯一key，用于遍历类及父类的所有方法，key过滤重载方法
     *
     * @param method 方法
     * @return key
     */
    public static String methodKey(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getName());
        sb.append('-').append(method.getParameterCount());
        for (Class c : method.getParameterTypes()) {
            sb.append('-').append(c.getName());
        }
        return sb.toString();
    }

    /**
     * 返回第一个不为null的对象
     *
     * @param <T> 泛型
     * @param val1 对象1
     * @param val2 对象2
     * @return 可用对象，可能返回null
     */
    public static <T> T orElse(T val1, T val2) {
        return val1 == null ? val2 : val1;
    }

    /**
     * 返回第一个不为null的对象
     *
     * @param <T> 泛型
     * @param vals 对象集合
     * @return 可用对象，可能返回null
     */
    public static <T> T orElse(T... vals) {
        for (T t : vals) {
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    public static void execute(Runnable task) {
        if (virtualExecutorConsumer != null) {
            virtualExecutorConsumer.execute(task);
        } else {
            task.run();
        }
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public static boolean isAbstractOrInterface(Class clazz) {
        return clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers());
    }

    public static String readFieldName(LambdaFunction func) {
        return readLambdaFieldName(func);
    }

    public static String readFieldName(LambdaBiConsumer consumer) {
        return readLambdaFieldName(consumer);
    }

    public static Class readClassName(LambdaBiConsumer consumer) {
        return readLambdaClassName(consumer);
    }

    public static String readFieldName(LambdaSupplier func) {
        return readLambdaFieldName(func);
    }

    public static Class readClassName(LambdaSupplier func) {
        return readLambdaClassName(func);
    }

    private static String readLambdaFieldName(Serializable func) {
        if (!func.getClass().isSynthetic()) { // 必须是Lambda表达式的合成类
            throw new RedkaleException("Not a synthetic lambda class");
        }
        return lambdaFieldNameCache.computeIfAbsent(func.getClass(), clazz -> {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(func.getClass(), MethodHandles.lookup());
                MethodHandle mh =
                        lookup.findVirtual(func.getClass(), "writeReplace", MethodType.methodType(Object.class));
                String methodName = ((java.lang.invoke.SerializedLambda) mh.invoke(func)).getImplMethodName();
                return readFieldName(methodName);
            } catch (Throwable e) {
                return readLambdaFieldNameFromBytes(func);
            }
        });
    }

    private static String readLambdaFieldNameFromBytes(Serializable func) {
        try {
            ObjectWriteStream out = new ObjectWriteStream(new ByteArrayOutputStream());
            out.writeObject(func);
            out.close();
            String methodName = out.methodNameReference.get();
            if (methodName != null) {
                return readFieldName(methodName);
            } else {
                // native-image环境下获取不到methodName
                throw new RedkaleException("cannot found method-name from lambda " + func);
            }
        } catch (IOException e) {
            throw new RedkaleException(e);
        }
    }

    private static Class readLambdaClassName(Serializable func) {
        if (!func.getClass().isSynthetic()) { // 必须是Lambda表达式的合成类
            throw new RedkaleException("Not a synthetic lambda class");
        }
        return lambdaClassNameCache.computeIfAbsent(func.getClass(), clazz -> {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(func.getClass(), MethodHandles.lookup());
                MethodHandle mh =
                        lookup.findVirtual(func.getClass(), "writeReplace", MethodType.methodType(Object.class));
                String methodName = ((java.lang.invoke.SerializedLambda) mh.invoke(func)).getImplMethodName();
                String className = methodName.contains("lambda$")
                        ? org.redkale.asm.Type.getReturnType(((java.lang.invoke.SerializedLambda) mh.invoke(func))
                                        .getInstantiatedMethodType())
                                .getClassName()
                        : ((java.lang.invoke.SerializedLambda) mh.invoke(func))
                                .getImplClass()
                                .replace('/', '.');
                return (Class) Thread.currentThread().getContextClassLoader().loadClass(className);
            } catch (ClassNotFoundException ex) {
                throw new RedkaleException(ex);
            } catch (Throwable e) {
                return readLambdaClassNameFromBytes(func);
            }
        });
    }

    private static Class readLambdaClassNameFromBytes(Serializable func) {
        try {
            ObjectWriteStream out = new ObjectWriteStream(new ByteArrayOutputStream());
            out.writeObject(func);
            out.close();
            String className = out.classNameReference.get();
            if (className != null) {
                return (Class) Thread.currentThread().getContextClassLoader().loadClass(className);
            } else {
                // native-image环境下获取不到methodName
                throw new RedkaleException("cannot found method-name from lambda " + func);
            }
        } catch (Exception e) {
            throw new RedkaleException(e);
        }
    }

    static String readFieldName(String methodName) {
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

    static class ObjectWriteStream extends ObjectOutputStream {

        public final ObjectRef<String> methodNameReference = new ObjectRef<>();

        public final ObjectRef<String> classNameReference = new ObjectRef<>();

        public ObjectWriteStream(OutputStream out) throws IOException {
            super(out);
        }

        @Override
        protected Object replaceObject(Object obj) throws IOException {
            if (obj instanceof java.lang.invoke.SerializedLambda) {
                String methodName = ((java.lang.invoke.SerializedLambda) obj).getImplMethodName();
                methodNameReference.set(methodName);
                java.lang.invoke.SerializedLambda sl = (java.lang.invoke.SerializedLambda) obj;
                String className = methodName.contains("lambda$")
                        ? org.redkale.asm.Type.getReturnType(sl.getInstantiatedMethodType())
                                .getClassName()
                        : sl.getImplClass().replace('/', '.');
                classNameReference.set(className);
            }
            return super.replaceObject(obj);
        }

        @Override
        public boolean enableReplaceObject(boolean enable) throws SecurityException {
            return super.enableReplaceObject(enable);
        }
    }

    /**
     * @param value from which next positive power of two will be found.
     * @return the next positive power of 2, this value if it is a power of 2. Negative values are mapped to 1.
     * @throws IllegalArgumentException is value is more than MAX_POW2 or less than 0
     */
    public static int roundToPowerOfTwo(final int value) {
        if (value > MAX_POW2) {
            throw new IllegalArgumentException(
                    "There is no larger power of 2 int for value:" + value + " since it exceeds 2^31.");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Given value:" + value + ". Expecting value >= 0.");
        }
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    public static boolean isRecordGetter(Method method) {
        return isRecordGetter(method.getDeclaringClass(), method);
    }

    public static boolean isRecordGetter(Class clazz, Method method) {
        if (JAVA_RECORD_CLASS == null) {
            return false;
        }
        if (method.getReturnType() == void.class) {
            return false;
        }
        if (method.getParameterCount() != 0) {
            return false;
        }
        if (method.getName().equals("getClass")) {
            return false;
        }
        Class clz = (clazz == null ? method.getDeclaringClass() : clazz);
        if (!JAVA_RECORD_CLASS.isAssignableFrom(clz)) {
            return false;
        }
        try {
            return clz.getDeclaredField(method.getName()).getType() == method.getReturnType();
        } catch (Throwable t) {
            return false;
        }
    }

    public static <T> CompletableFuture<T> orTimeout(
            CompletableFuture future, Supplier<String> errMsgFunc, Duration timeout) {
        return orTimeout(future, errMsgFunc, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static <T> CompletableFuture<T> orTimeout(
            CompletableFuture future, Supplier<String> errMsgFunc, long timeout, TimeUnit unit) {
        if (future == null) {
            return future;
        }
        final ScheduledFuture<?> sf = delayer.schedule(
                () -> {
                    if (!future.isDone()) {
                        String msg = errMsgFunc == null ? null : errMsgFunc.get();
                        future.completeExceptionally(msg == null ? new TimeoutException(msg) : new TimeoutException());
                    }
                },
                timeout,
                unit);
        return future.whenComplete((v, t) -> {
            if (t == null && !sf.isDone()) {
                sf.cancel(false);
            }
        });
    }

    public static <T> CompletableFuture<T> completeOnTimeout(CompletableFuture future, T value, Duration timeout) {
        return completeOnTimeout(future, value, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public static <T> CompletableFuture<T> completeOnTimeout(
            CompletableFuture future, T value, long timeout, TimeUnit unit) {
        return future.completeOnTimeout(value, timeout, unit);
    }

    public static <T> CompletableFuture<T[]> allOfFutures(List<CompletableFuture<T>> list, IntFunction<T[]> func) {
        CompletableFuture<T>[] futures = list.toArray(futureArrayFunc);
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            T[] array = func.apply(size);
            for (int i = 0; i < size; i++) {
                array[i] = futures[i].join();
            }
            return array;
        });
    }

    public static <T> CompletableFuture<T[]> allOfFutures(Stream<CompletableFuture<T>> stream, IntFunction<T[]> func) {
        CompletableFuture<T>[] futures = stream.toArray(futureArrayFunc);
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            T[] array = func.apply(size);
            for (int i = 0; i < size; i++) {
                array[i] = futures[i].join();
            }
            return array;
        });
    }

    public static <T> CompletableFuture<T[]> allOfFutures(CompletableFuture<T>[] futures, IntFunction<T[]> func) {
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            T[] array = func.apply(size);
            for (int i = 0; i < size; i++) {
                array[i] = futures[i].join();
            }
            return array;
        });
    }

    public static <T> CompletableFuture<T[]> allOfFutures(
            List<CompletableFuture<T>> list, IntFunction<T[]> func, BiConsumer<Integer, T> consumer) {
        CompletableFuture<T>[] futures = list.toArray(futureArrayFunc);
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            T[] array = func.apply(size);
            for (int i = 0; i < size; i++) {
                T val = futures[i].join();
                consumer.accept(i, val);
                array[i] = val;
            }
            return array;
        });
    }

    public static <T> CompletableFuture<T[]> allOfFutures(
            Stream<CompletableFuture<T>> stream, IntFunction<T[]> func, BiConsumer<Integer, T> consumer) {
        CompletableFuture<T>[] futures = stream.toArray(futureArrayFunc);
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            T[] array = func.apply(size);
            for (int i = 0; i < size; i++) {
                T val = futures[i].join();
                consumer.accept(i, val);
                array[i] = val;
            }
            return array;
        });
    }

    public static <T> CompletableFuture<T[]> allOfFutures(
            CompletableFuture<T>[] futures, IntFunction<T[]> func, BiConsumer<Integer, T> consumer) {
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            T[] array = func.apply(size);
            for (int i = 0; i < size; i++) {
                T val = futures[i].join();
                consumer.accept(i, val);
                array[i] = val;
            }
            return array;
        });
    }

    public static <T> CompletableFuture<List<T>> allOfFutures(List<CompletableFuture<T>> list) {
        CompletableFuture<T>[] futures = list.toArray(futureArrayFunc);
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            List<T> rs = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                rs.add(futures[i].join());
            }
            return rs;
        });
    }

    public static <T> CompletableFuture<List<T>> allOfFutures(Stream<CompletableFuture<T>> stream) {
        CompletableFuture<T>[] futures = stream.toArray(futureArrayFunc);
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            List<T> rs = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                rs.add(futures[i].join());
            }
            return rs;
        });
    }

    public static <T> CompletableFuture<List<T>> allOfFutures(CompletableFuture<T>[] futures) {
        if (futures.length == 1) {
            return futures[0].thenApply(v -> {
                List<T> rs = new ArrayList<>(1);
                rs.add(v);
                return rs;
            });
        }
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            List<T> rs = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                rs.add(futures[i].join());
            }
            return rs;
        });
    }

    public static <T> CompletableFuture<List<T>> allOfFutures(
            List<CompletableFuture<T>> list, BiConsumer<Integer, T> consumer) {
        CompletableFuture<T>[] futures = list.toArray(futureArrayFunc);
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            List<T> rs = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                T val = futures[i].join();
                consumer.accept(i, val);
                rs.add(val);
            }
            return rs;
        });
    }

    public static <T> CompletableFuture<List<T>> allOfFutures(
            Stream<CompletableFuture<T>> stream, BiConsumer<Integer, T> consumer) {
        CompletableFuture<T>[] futures = stream.toArray(futureArrayFunc);
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            List<T> rs = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                T val = futures[i].join();
                consumer.accept(i, val);
                rs.add(val);
            }
            return rs;
        });
    }

    public static <T> CompletableFuture<List<T>> allOfFutures(
            CompletableFuture<T>[] futures, BiConsumer<Integer, T> consumer) {
        if (futures.length == 1) {
            return futures[0].thenApply(v -> {
                List<T> rs = new ArrayList<>(1);
                consumer.accept(0, v);
                rs.add(v);
                return rs;
            });
        }
        return CompletableFuture.allOf(futures).thenApply(v -> {
            int size = futures.length;
            List<T> rs = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                T val = futures[i].join();
                consumer.accept(i, val);
                rs.add(val);
            }
            return rs;
        });
    }

    /**
     * 是否为数字字符串
     *
     * @param str 字符串
     * @return 是否为数字字符串
     */
    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        int size = str.length();
        for (int i = 0; i < size; i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否为空白
     *
     * @param str 字符串
     * @return 是否为空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.isEmpty() || str.isBlank();
    }

    /**
     * 是否为空白
     *
     * @param str 字符串
     * @param fromIndex 起始位置
     * @param toIndex 结束位置
     * @return 是否为空白
     */
    public static boolean isBlank(String str, int fromIndex, int toIndex) {
        if (str == null || str.isEmpty()) {
            return true;
        }
        for (int i = fromIndex; i < toIndex; i++) {
            char ch = str.charAt(i);
            if (ch != ' ' && ch != '\t' && !Character.isWhitespace(ch)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 字符串是否至少一个为空白
     *
     * @param strs 字符串集合
     * @return 是否为空白
     */
    public static boolean isAnyBlank(String... strs) {
        if (strs == null || strs.length == 0) {
            return false;
        }
        for (String str : strs) {
            if (isBlank(str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否不为空白
     *
     * @param str 字符串
     * @return 是否不为空白
     */
    public static boolean isNotBlank(String str) {
        return str != null && !str.isEmpty() && !str.isBlank();
    }

    /**
     * 是否不为空白
     *
     * @param str 字符串
     * @param fromIndex 起始位置
     * @param toIndex 结束位置
     * @return 是否为空白
     */
    public static boolean isNotBlank(String str, int fromIndex, int toIndex) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        for (int i = fromIndex; i < toIndex; i++) {
            char ch = str.charAt(i);
            if (ch != ' ' && ch != '\t' && !Character.isWhitespace(ch)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否为空
     *
     * @param str 字符串
     * @return 是否为空
     */
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    /**
     * 字符串是否至少一个为空
     *
     * @param strs 字符串集合
     * @return 是否为空
     */
    public static boolean isAnyEmpty(CharSequence... strs) {
        if (strs == null || strs.length == 0) {
            return false;
        }
        for (CharSequence str : strs) {
            if (isEmpty(str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否不为空
     *
     * @param str 字符串
     * @return 是否不为空
     */
    public static boolean isNotEmpty(CharSequence str) {
        return str != null && str.length() > 0;
    }

    /**
     * 是否为空
     *
     * @param map Map
     * @return 是否为空
     */
    public static boolean isEmpty(Map map) {
        return map == null || map.isEmpty();
    }

    /**
     * 是否不为空
     *
     * @param map Map
     * @return 是否不为空
     */
    public static boolean isNotEmpty(Map map) {
        return map != null && !map.isEmpty();
    }

    /**
     * 是否为空
     *
     * @param collection Collection
     * @return 是否为空
     */
    public static boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 是否不为空
     *
     * @param collection Collection
     * @return 是否不为空
     */
    public static boolean isNotEmpty(Collection collection) {
        return collection != null && !collection.isEmpty();
    }

    /**
     * 是否为空
     *
     * @param <T> 泛型
     * @param array 数组
     * @return 是否为空
     */
    public static <T> boolean isEmpty(T[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 是否不为空
     *
     * @param <T> 泛型
     * @param array 数组
     * @return 是否不为空
     */
    public static <T> boolean isNotEmpty(T[] array) {
        return array != null && array.length > 0;
    }

    /**
     * 是否为空
     *
     * @param array 数组
     * @return 是否为空
     */
    public static boolean isEmpty(byte[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 是否不为空
     *
     * @param array 数组
     * @return 是否不为空
     */
    public static boolean isNotEmpty(byte[] array) {
        return array != null && array.length > 0;
    }

    /**
     * 是否为空
     *
     * @param array 数组
     * @return 是否为空
     */
    public static boolean isEmpty(short[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 是否不为空
     *
     * @param array 数组
     * @return 是否不为空
     */
    public static boolean isNotEmpty(short[] array) {
        return array != null && array.length > 0;
    }

    /**
     * 是否为空
     *
     * @param array 数组
     * @return 是否为空
     */
    public static boolean isEmpty(int[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 是否不为空
     *
     * @param array 数组
     * @return 是否不为空
     */
    public static boolean isNotEmpty(int[] array) {
        return array != null && array.length > 0;
    }

    /**
     * 是否为空
     *
     * @param array 数组
     * @return 是否为空
     */
    public static boolean isEmpty(long[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 是否不为空
     *
     * @param array 数组
     * @return 是否不为空
     */
    public static boolean isNotEmpty(long[] array) {
        return array != null && array.length > 0;
    }

    /**
     * 是否为空
     *
     * @param array ByteArray
     * @return 是否为空
     */
    public static boolean isEmpty(ByteArray array) {
        return array == null || array.isEmpty();
    }

    /**
     * 是否不为空
     *
     * @param array ByteArray
     * @return 是否不为空
     */
    public static boolean isNotEmpty(ByteArray array) {
        return array != null && !array.isEmpty();
    }

    /**
     * 将字符串首字母大写
     *
     * @param str 字符串
     * @return 首字母大写
     */
    public static String firstCharUpperCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        if (Character.isUpperCase(str.charAt(0))) {
            return str;
        }
        char[] chs = str.toCharArray();
        chs[0] = Character.toUpperCase(chs[0]);
        return new String(chs);
    }

    /**
     * 将字符串首字母小写
     *
     * @param str 字符串
     * @return 首字母小写
     */
    public static String firstCharLowerCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        if (Character.isLowerCase(str.charAt(0))) {
            return str;
        }
        char[] chs = str.toCharArray();
        chs[0] = Character.toLowerCase(chs[0]);
        return new String(chs);
    }

    public static boolean[] box(Boolean[] array) {
        if (array == null) {
            return null;
        }
        boolean[] rs = new boolean[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i] != null && array[i];
        }
        return rs;
    }

    public static Boolean[] box(boolean[] array) {
        if (array == null) {
            return null;
        }
        Boolean[] rs = new Boolean[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i];
        }
        return rs;
    }

    public static byte[] box(Byte[] array) {
        if (array == null) {
            return null;
        }
        byte[] rs = new byte[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i] == null ? 0 : array[i];
        }
        return rs;
    }

    public static Byte[] box(byte[] array) {
        if (array == null) {
            return null;
        }
        Byte[] rs = new Byte[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i];
        }
        return rs;
    }

    public static char[] box(Character[] array) {
        if (array == null) {
            return null;
        }
        char[] rs = new char[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i] == null ? 0 : array[i];
        }
        return rs;
    }

    public static Character[] box(char[] array) {
        if (array == null) {
            return null;
        }
        Character[] rs = new Character[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i];
        }
        return rs;
    }

    public static short[] box(Short[] array) {
        if (array == null) {
            return null;
        }
        short[] rs = new short[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i] == null ? 0 : array[i];
        }
        return rs;
    }

    public static Short[] box(short[] array) {
        if (array == null) {
            return null;
        }
        Short[] rs = new Short[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i];
        }
        return rs;
    }

    public static int[] box(Integer[] array) {
        if (array == null) {
            return null;
        }
        int[] rs = new int[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i] == null ? 0 : array[i];
        }
        return rs;
    }

    public static Integer[] box(int[] array) {
        if (array == null) {
            return null;
        }
        Integer[] rs = new Integer[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i];
        }
        return rs;
    }

    public static long[] box(Long[] array) {
        if (array == null) {
            return null;
        }
        long[] rs = new long[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i] == null ? 0 : array[i];
        }
        return rs;
    }

    public static Long[] box(long[] array) {
        if (array == null) {
            return null;
        }
        Long[] rs = new Long[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i];
        }
        return rs;
    }

    public static float[] box(Float[] array) {
        if (array == null) {
            return null;
        }
        float[] rs = new float[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i] == null ? 0 : array[i];
        }
        return rs;
    }

    public static Float[] box(float[] array) {
        if (array == null) {
            return null;
        }
        Float[] rs = new Float[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i];
        }
        return rs;
    }

    public static double[] box(Double[] array) {
        if (array == null) {
            return null;
        }
        double[] rs = new double[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i] == null ? 0 : array[i];
        }
        return rs;
    }

    public static Double[] box(double[] array) {
        if (array == null) {
            return null;
        }
        Double[] rs = new Double[array.length];
        for (int i = 0; i < rs.length; i++) {
            rs[i] = array[i];
        }
        return rs;
    }

    public static <T> boolean[] reverse(boolean[] array) {
        if (array == null) {
            return array;
        }
        boolean[] arr = array;
        int start = 0;
        int end = arr.length - 1;
        while (start < end) {
            boolean temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;
            start++;
            end--;
        }
        return arr;
    }

    public static <T> byte[] reverse(byte[] array) {
        if (array == null) {
            return array;
        }
        byte[] arr = array;
        int start = 0;
        int end = arr.length - 1;
        while (start < end) {
            byte temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;
            start++;
            end--;
        }
        return arr;
    }

    public static <T> char[] reverse(char[] array) {
        if (array == null) {
            return array;
        }
        char[] arr = array;
        int start = 0;
        int end = arr.length - 1;
        while (start < end) {
            char temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;
            start++;
            end--;
        }
        return arr;
    }

    public static <T> short[] reverse(short[] array) {
        if (array == null) {
            return array;
        }
        short[] arr = array;
        int start = 0;
        int end = arr.length - 1;
        while (start < end) {
            short temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;
            start++;
            end--;
        }
        return arr;
    }

    public static <T> int[] reverse(int[] array) {
        if (array == null) {
            return array;
        }
        int[] arr = array;
        int start = 0;
        int end = arr.length - 1;
        while (start < end) {
            int temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;
            start++;
            end--;
        }
        return arr;
    }

    public static <T> long[] reverse(long[] array) {
        if (array == null) {
            return array;
        }
        long[] arr = array;
        int start = 0;
        int end = arr.length - 1;
        while (start < end) {
            long temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;
            start++;
            end--;
        }
        return arr;
    }

    public static <T> float[] reverse(float[] array) {
        if (array == null) {
            return array;
        }
        float[] arr = array;
        int start = 0;
        int end = arr.length - 1;
        while (start < end) {
            float temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;
            start++;
            end--;
        }
        return arr;
    }

    public static <T> double[] reverse(double[] array) {
        if (array == null) {
            return array;
        }
        double[] arr = array;
        int start = 0;
        int end = arr.length - 1;
        while (start < end) {
            double temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;
            start++;
            end--;
        }
        return arr;
    }

    public static <T> T[] reverse(T[] array) {
        if (array == null) {
            return array;
        }
        T[] arr = array;
        int start = 0;
        int end = arr.length - 1;
        while (start < end) {
            T temp = arr[start];
            arr[start] = arr[end];
            arr[end] = temp;
            start++;
            end--;
        }
        return arr;
    }

    public static <T> List<T> reverse(List<T> list) {
        if (list != null) {
            Collections.reverse(list);
        }
        return list;
    }

    /**
     * 将多个key:value对应值组合成一个Map，items长度必须是偶数, 参数个数若是奇数的话，最后一个会被忽略 类似 JDK9中的 Map.of 方法
     *
     * @param <K> 泛型
     * @param <V> 泛型
     * @param items 键值对
     * @return Map
     */
    @ClassDepends
    public static <K, V> HashMap<K, V> ofMap(Object... items) {
        HashMap<K, V> map = new LinkedHashMap<>(Math.max(1, items.length / 2));
        int len = items.length / 2;
        for (int i = 0; i < len; i++) {
            map.put((K) items[i * 2], (V) items[i * 2 + 1]);
        }
        return map;
    }

    /**
     * 将多个Map合并到第一个Map中
     *
     * @param <K> 泛型
     * @param <V> 泛型
     * @param maps Map
     * @return Map
     */
    public static <K, V> Map<K, V> merge(Map<K, V>... maps) {
        Map<K, V> map = null;
        for (Map<K, V> m : maps) {
            if (map == null) {
                map = m;
            } else if (m != null) {
                map.putAll(m);
            }
        }
        return map;
    }

    /**
     * 将多个元素组合成一个Set
     *
     * @param <T> 泛型
     * @param items 元素
     * @return Set
     */
    public static <T> Set<T> ofSet(T... items) {
        Set<T> set = new LinkedHashSet<>(items.length);
        for (T item : items) set.add(item);
        return set;
    }

    /**
     * 将多个元素组合成一个List <br>
     * 类似 JDK9中的 List.of 方法
     *
     * @param <T> 泛型
     * @param items 元素
     * @return List
     */
    public static <T> List<T> ofList(T... items) {
        List<T> list = new ArrayList<>(items.length);
        for (T item : items) list.add(item);
        return list;
    }

    /**
     * 将多个元素组合成一个Array
     *
     * @param <T> 泛型
     * @param items 元素
     * @return Array
     */
    public static <T> T[] ofArray(T... items) {
        return items;
    }

    /**
     * 裁剪List，使其size不超过limit大小 <br>
     *
     * @param <T> 泛型
     * @param list 集合
     * @param limit 大小
     * @return List
     */
    public static <T> List<T> limit(List<T> list, int limit) {
        if (list == null || list.isEmpty() || list.size() <= limit) {
            return list;
        }
        return list.subList(0, limit);
    }

    /**
     * 获取不带"-"的UUID值
     *
     * @return 不带"-"UUID值
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 比较两个版本号的大小，ver1小于ver2返回 -1
     *
     * @param version1 版本号
     * @param version2 版本号
     * @return 版本大小
     */
    public static int compareVersion(String version1, String version2) {
        if (isEmpty(version1)) {
            return isEmpty(version2) ? 0 : -1;
        }
        if (isEmpty(version2)) {
            return 1;
        }
        String[] ver1 = version1.split("\\.");
        String[] ver2 = version2.split("\\.");
        int len = Math.min(ver1.length, ver2.length);
        for (int i = 0; i < len; i++) {
            if (ver1[i].length() > ver2[i].length()) {
                return 1;
            }
            if (ver1[i].length() < ver2[i].length()) {
                return -1;
            }
            int v = Integer.parseInt(ver1[i]) - Integer.parseInt(ver2[i]);
            if (v != 0) {
                return v > 0 ? 1 : -1;
            }
        }
        return 0;
    }

    /**
     * 排序, 值大排前面
     * @param <P> 泛型
     * @param list 集合
     * @return  排序后的集合
     */
    public static <P> List<P> sortPriority(List<P> list) {
        Collections.sort(list, (a, b) -> {
            Priority p1 = a == null ? null : a.getClass().getAnnotation(Priority.class);
            Priority p2 = b == null ? null : b.getClass().getAnnotation(Priority.class);
            return (p2 == null ? 0 : p2.value()) - (p1 == null ? 0 : p1.value());
        });
        return list;
    }

    /**
     * 将一个或多个新元素添加到数组开始，数组中的元素自动后移
     *
     * @param <T> 泛型
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static <T> T[] unshift(final T[] array, final T... objs) {
        if (array == null || array.length == 0) {
            return objs;
        }
        final T[] news = (T[]) Creator.newArray(array.getClass().getComponentType(), array.length + objs.length);
        System.arraycopy(objs, 0, news, 0, objs.length);
        System.arraycopy(array, 0, news, objs.length, array.length);
        return news;
    }

    /**
     * 将一个或多个新元素添加到数组开始，数组中的元素自动后移
     *
     * @param <T> 泛型
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static <T> T[] unshift(final T[] array, final Collection<T> objs) {
        if (objs == null || objs.isEmpty()) {
            return array;
        }
        if (array == null) {
            T one = null;
            for (T t : objs) {
                if (t != null) {
                    one = t;
                }
                break;
            }
            if (one == null) {
                return array;
            }
            T[] news = (T[]) Creator.newArray(one.getClass(), objs.size());
            return objs.toArray(news);
        }
        T[] news = (T[]) Creator.newArray(array.getClass().getComponentType(), array.length + objs.size());
        int index = -1;
        for (T t : objs) {
            news[(++index)] = t;
        }
        System.arraycopy(array, 0, news, objs.size(), array.length);
        return news;
    }

    /**
     * 获取int数组之和, 空数组返回0
     *
     * @param array 数组
     * @return int
     */
    public static int sum(final int... array) {
        return sum(false, array);
    }

    /**
     * 获取int数组之和
     *
     * @param check 是否检测空
     * @param array 数组
     * @return int
     */
    public static int sum(boolean check, final int... array) {
        if (array == null || array.length == 0) {
            if (!check) {
                return 0;
            }
            throw new NullPointerException("array is null or empty");
        }
        int sum = 0;
        for (int i : array) {
            sum += i;
        }
        return sum;
    }

    /**
     * 获取long数组之和, 空数组返回0
     *
     * @param array 数组
     * @return long
     */
    public static long sum(final long... array) {
        return sum(false, array);
    }

    /**
     * 获取long数组之和
     *
     * @param check 是否检测空
     * @param array 数组
     * @return long
     */
    public static long sum(boolean check, final long... array) {
        if (array == null || array.length == 0) {
            if (!check) {
                return 0;
            }
            throw new NullPointerException("array is null or empty");
        }
        long sum = 0L;
        for (long i : array) {
            sum += i;
        }
        return sum;
    }

    /**
     * 获取int数组最大值
     *
     * @param array 数组
     * @return int
     */
    public static int max(final int... array) {
        if (array == null || array.length == 0) {
            throw new NullPointerException("array is null or empty");
        }
        int max = array[0];
        for (int i : array) {
            if (i > max) {
                i = max;
            }
        }
        return max;
    }

    /**
     * 获取long数组最大值
     *
     * @param array 数组
     * @return long
     */
    public static long max(final long... array) {
        if (array == null || array.length == 0) {
            throw new NullPointerException("array is null or empty");
        }
        long max = array[0];
        for (long i : array) {
            if (i > max) {
                i = max;
            }
        }
        return max;
    }

    /**
     * 获取int数组最小值
     *
     * @param array 数组
     * @return int
     */
    public static long min(final int... array) {
        if (array == null || array.length == 0) {
            throw new NullPointerException("array is null or empty");
        }
        int min = array[0];
        for (int i : array) {
            if (i < min) {
                i = min;
            }
        }
        return min;
    }

    /**
     * 获取long数组最小值
     *
     * @param array 数组
     * @return long
     */
    public static long min(final long... array) {
        if (array == null || array.length == 0) {
            throw new NullPointerException("array is null or empty");
        }
        long min = array[0];
        for (long i : array) {
            if (i < min) {
                i = min;
            }
        }
        return min;
    }

    /**
     * 将char数组用分隔符拼接成字符串
     *
     * @param array 数组
     * @param delimiter 分隔符
     * @return String
     */
    public static String joining(final char[] array, final String delimiter) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char i : array) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(i);
        }
        return sb.toString();
    }

    /**
     * 将int数组用分隔符拼接成字符串
     *
     * @param array 数组
     * @param delimiter 分隔符
     * @return String
     */
    public static String joining(final int[] array, final String delimiter) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i : array) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(i);
        }
        return sb.toString();
    }

    /**
     * 将long数组用分隔符拼接成字符串
     *
     * @param array 数组
     * @param delimiter 分隔符
     * @return String
     */
    public static String joining(final long[] array, final String delimiter) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (long i : array) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(i);
        }
        return sb.toString();
    }

    /**
     * 将对象数组用分隔符拼接成字符串
     *
     * @param <T> 泛型
     * @param array 数组
     * @param delimiter 分隔符
     * @return String
     */
    public static <T> String joining(final T[] array, final String delimiter) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (T i : array) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(i);
        }
        return sb.toString();
    }

    /**
     * 将对象集合用分隔符拼接成字符串
     *
     * @param <T> 泛型
     * @param stream 集合
     * @param delimiter 分隔符
     * @return String
     */
    public static <T> String joining(final Stream<T> stream, final String delimiter) {
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        stream.forEach(i -> {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(i);
        });
        return sb.toString();
    }

    /**
     * 将对象数组用分隔符拼接成字符串
     *
     * @param <T> 泛型
     * @param array 数组
     * @param delimiter 分隔符
     * @return String
     */
    public static <T> String joining(final String[] array, final char delimiter) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String i : array) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(i);
        }
        return sb.toString();
    }

    /**
     * 将对象数组用分隔符拼接成字符串
     *
     * @param <T> 泛型
     * @param array 数组
     * @param delimiter 分隔符
     * @return String
     */
    public static <T> String joiningHex(final byte[] array, final char delimiter) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte i : array) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            String s = Integer.toHexString(i & 0xff);
            sb.append(s.length() > 1 ? "0x" : "0x0").append(s);
        }
        return sb.toString();
    }

    /**
     * 将对象数组用分隔符拼接成字符串
     *
     * @param <T> 泛型
     * @param array 数组
     * @param offset 偏移量
     * @param length 长度
     * @param delimiter 分隔符
     * @return String
     */
    public static <T> String joiningHex(final byte[] array, int offset, int length, final char delimiter) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int len = offset + length;
        for (int i = offset; i < len; i++) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            String s = Integer.toHexString(array[i] & 0xff);
            sb.append(s.length() > 1 ? "0x" : "0x0").append(s);
        }
        return sb.toString();
    }

    /**
     * 将一个或多个byte新元素添加到byte数组结尾
     *
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static byte[] append(final byte[] array, final byte... objs) {
        if (array == null || array.length == 0) {
            return objs;
        }
        if (objs == null || objs.length == 0) {
            return array;
        }
        final byte[] news = new byte[array.length + objs.length];
        System.arraycopy(array, 0, news, 0, array.length);
        System.arraycopy(objs, 0, news, array.length, objs.length);
        return news;
    }

    /**
     * 将一个或多个byte新元素添加到byte数组结尾
     *
     * @param array 原数组
     * @param objs 待追加数据
     * @param offset 待追加数据偏移量
     * @param length 待追加数据的长度
     * @return 新数组
     */
    public static byte[] append(final byte[] array, final byte[] objs, int offset, int length) {
        if (array == null || array.length == 0) {
            if (objs != null && offset == 0 && objs.length == length) {
                return objs;
            }
            final byte[] news = new byte[length];
            System.arraycopy(objs, 0, news, 0, length);
            return news;
        }
        if (objs == null || length == 0) {
            return array;
        }
        final byte[] news = new byte[array.length + length];
        System.arraycopy(array, 0, news, 0, array.length);
        System.arraycopy(objs, offset, news, array.length, length);
        return news;
    }

    /**
     * 将一个或多个short新元素添加到short数组结尾
     *
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static short[] append(final short[] array, final short... objs) {
        if (array == null || array.length == 0) {
            return objs;
        }
        if (objs == null || objs.length == 0) {
            return array;
        }
        final short[] news = new short[array.length + objs.length];
        System.arraycopy(array, 0, news, 0, array.length);
        System.arraycopy(objs, 0, news, array.length, objs.length);
        return news;
    }

    /**
     * 将一个或多个char新元素添加到char数组结尾
     *
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static char[] append(final char[] array, final char... objs) {
        if (array == null || array.length == 0) {
            return objs;
        }
        if (objs == null || objs.length == 0) {
            return array;
        }
        final char[] news = new char[array.length + objs.length];
        System.arraycopy(array, 0, news, 0, array.length);
        System.arraycopy(objs, 0, news, array.length, objs.length);
        return news;
    }

    /**
     * 将一个或多个int新元素添加到int数组结尾
     *
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static int[] append(final int[] array, final int... objs) {
        if (array == null || array.length == 0) {
            return objs;
        }
        if (objs == null || objs.length == 0) {
            return array;
        }
        final int[] news = new int[array.length + objs.length];
        System.arraycopy(array, 0, news, 0, array.length);
        System.arraycopy(objs, 0, news, array.length, objs.length);
        return news;
    }

    /**
     * 将一个或多个long新元素添加到long数组结尾
     *
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static long[] append(final long[] array, final long... objs) {
        if (array == null || array.length == 0) {
            return objs;
        }
        if (objs == null || objs.length == 0) {
            return array;
        }
        final long[] news = new long[array.length + objs.length];
        System.arraycopy(array, 0, news, 0, array.length);
        System.arraycopy(objs, 0, news, array.length, objs.length);
        return news;
    }

    /**
     * 将一个或多个新元素添加到数组结尾
     *
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static String[] append(final String[] array, final String... objs) {
        if (array == null || array.length == 0) {
            return objs;
        }
        if (objs == null || objs.length == 0) {
            return array;
        }
        final String[] news = new String[array.length + objs.length];
        System.arraycopy(array, 0, news, 0, array.length);
        System.arraycopy(objs, 0, news, array.length, objs.length);
        return news;
    }

    /**
     * 将一个或多个新元素添加到数组结尾
     *
     * @param one 单个对象
     * @param objs 待追加数据
     * @return 新数组
     */
    public static String[] append(final String one, final String... objs) {
        if (one == null) {
            return objs;
        }
        final String[] news = new String[1 + objs.length];
        news[0] = one;
        System.arraycopy(objs, 0, news, 1, objs.length);
        return news;
    }

    /**
     * 将一个或多个新元素添加到数组结尾
     *
     * @param one 单个对象
     * @param two 单个对象
     * @param objs 待追加数据
     * @return 新数组
     */
    public static String[] append(final String one, String two, final String... objs) {
        final String[] news = new String[2 + objs.length];
        news[0] = one;
        news[1] = two;
        System.arraycopy(objs, 0, news, 2, objs.length);
        return news;
    }

    /**
     * 将一个或多个新元素添加到数组结尾
     *
     * @param <T> 泛型
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static <T> T[] append(final T[] array, final T... objs) {
        if (array == null || array.length == 0) {
            return objs;
        }
        if (objs == null || objs.length == 0) {
            return array;
        }
        final T[] news = (T[]) Creator.newArray(array.getClass().getComponentType(), array.length + objs.length);
        System.arraycopy(array, 0, news, 0, array.length);
        System.arraycopy(objs, 0, news, array.length, objs.length);
        return news;
    }

    /**
     * 将一个或多个新元素添加到数组结尾
     *
     * @param <T> 泛型
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static <T> Object[][] append(final Object[][] array, final Object[]... objs) {
        if (array == null || array.length == 0) {
            return objs;
        }
        if (objs == null || objs.length == 0) {
            return array;
        }
        final Object[][] news = new Object[array.length + objs.length][];
        System.arraycopy(array, 0, news, 0, array.length);
        System.arraycopy(objs, 0, news, array.length, objs.length);
        return news;
    }

    /**
     * 将一个或多个新元素添加到数组结尾
     *
     * @param <T> 泛型
     * @param array 原数组
     * @param objs 待追加数据
     * @return 新数组
     */
    public static <T> T[] append(final T[] array, final Collection<T> objs) {
        if (objs == null || objs.isEmpty()) {
            return array;
        }
        if (array == null) {
            T one = null;
            for (T t : objs) {
                if (t != null) {
                    one = t;
                }
                break;
            }
            if (one == null) {
                return array;
            }
            T[] news = (T[]) Creator.newArray(one.getClass(), objs.size());
            return objs.toArray(news);
        }
        T[] news = (T[]) Creator.newArray(array.getClass().getComponentType(), array.length + objs.size());
        System.arraycopy(array, 0, news, 0, array.length);
        int index = -1;
        for (T t : objs) {
            news[array.length + (++index)] = t;
        }
        return news;
    }

    /**
     * 将元素从数组中删除
     *
     * @param <T> 泛型
     * @param array 原数组
     * @param item 元素
     * @return 新数组
     */
    public static <T> T[] remove(final T[] array, final T item) {
        return remove(array, (i) -> Objects.equals(i, item));
    }

    /**
     * 将符合条件的元素从数组中删除
     *
     * @param <T> 泛型
     * @param array 原数组
     * @param filter Predicate
     * @return 新数组
     */
    public static <T> T[] remove(final T[] array, final Predicate filter) {
        if (array == null || array.length == 0 || filter == null) {
            return array;
        }
        final T[] news = (T[]) Creator.newArray(array.getClass().getComponentType(), array.length);
        int index = 0;
        for (int i = 0; i < news.length; i++) {
            if (!filter.test(array[i])) {
                news[index++] = array[i];
            }
        }
        if (index == array.length) {
            return array;
        }
        final T[] rs = (T[]) Creator.newArray(array.getClass().getComponentType(), index);
        System.arraycopy(news, 0, rs, 0, index);
        return rs;
    }

    /**
     * 将符合条件的元素从数组中删除
     *
     * @param array 原数组
     * @param item 元素
     * @return 新数组
     */
    public static String[] remove(final String[] array, final String item) {
        if (array == null || array.length == 0) {
            return array;
        }
        final String[] news = new String[array.length];
        int index = 0;
        for (int i = 0; i < news.length; i++) {
            if (item != null && !item.equals(array[i])) {
                news[index++] = array[i];
            } else if (item == null && array[i] != null) {
                news[index++] = array[i];
            }
        }
        if (index == array.length) {
            return array;
        }
        final String[] rs = new String[index];
        System.arraycopy(news, 0, rs, 0, index);
        return rs;
    }

    /**
     * 将指定的long元素从数组中删除, 相同的元素会根据items里重复次数来执行删除 <br>
     * 例如: <br>
     * remove(new short[]{1, 1, 1, 2, 2, 3, 3, 3}, false, 1, 1, 2, 3, 3) = [1,2,3]<br>
     *
     * @param array 原数组
     * @param items short[]
     * @return 新数组
     */
    public static short[] removeMatch(final short[] array, final short... items) {
        return remove(array, false, items);
    }

    /**
     * 将指定的int元素从数组中删除, repeat=true时相同的元素会根据items里重复次数来执行删除 <br>
     * 例如: <br>
     * remove(new short[]{1, 1, 1, 2, 2, 3, 3, 3}, true, 1, 1, 2, 3, 3) = [] <br>
     * remove(new short[]{1, 1, 1, 2, 2, 3, 3, 3}, false, 1, 1, 2, 3, 3) = [1,2,3]
     *
     * @param array 原数组
     * @param repeat 是否重复删除相同的元素
     * @param items short[]
     * @return 新数组
     */
    public static short[] remove(final short[] array, boolean repeat, final short... items) {
        if (array == null || array.length == 0 || items == null || items.length == 0) {
            return array;
        }
        final short[] news = new short[array.length];
        short[] subs = items;
        int index = 0;
        for (int i = 0; i < news.length; i++) {
            if (subs.length > 0 && contains(subs, array[i])) {
                if (!repeat) {
                    short[] newsubs = new short[subs.length - 1];
                    int k = 0;
                    boolean done = false;
                    for (short v : subs) {
                        if (done) {
                            newsubs[k++] = v;
                        } else if (v == array[i]) {
                            done = true;
                        } else {
                            newsubs[k++] = v;
                        }
                    }
                    subs = newsubs;
                }
            } else {
                news[index++] = array[i];
            }
        }
        if (index == array.length) {
            return array;
        }
        final short[] rs = new short[index];
        System.arraycopy(news, 0, rs, 0, index);
        return rs;
    }

    /**
     * 将指定的long元素从数组中删除, 相同的元素会根据items里重复次数来执行删除 <br>
     * 例如: <br>
     * remove(new int[]{1, 1, 1, 2, 2, 3, 3, 3}, false, 1, 1, 2, 3, 3) = [1,2,3]<br>
     *
     * @param array 原数组
     * @param items int[]
     * @return 新数组
     */
    public static int[] removeMatch(final int[] array, final int... items) {
        return remove(array, false, items);
    }

    /**
     * 将指定的int元素从数组中删除, repeat=false时相同的元素会根据items里重复次数来执行删除 <br>
     * 例如: <br>
     * remove(new int[]{1, 1, 1, 2, 2, 3, 3, 3}, true, 1, 1, 2, 3, 3) = [] <br>
     * remove(new int[]{1, 1, 1, 2, 2, 3, 3, 3}, false, 1, 1, 2, 3, 3) = [1,2,3]
     *
     * @param array 原数组
     * @param repeat 是否重复删除相同的元素
     * @param items int[]
     * @return 新数组
     */
    public static int[] remove(final int[] array, boolean repeat, final int... items) {
        if (array == null || array.length == 0 || items == null || items.length == 0) {
            return array;
        }
        final int[] news = new int[array.length];
        int[] subs = items;
        int index = 0;
        for (int i = 0; i < news.length; i++) {
            if (subs.length > 0 && contains(subs, array[i])) {
                if (!repeat) {
                    int[] newsubs = new int[subs.length - 1];
                    int k = 0;
                    boolean done = false;
                    for (int v : subs) {
                        if (done) {
                            newsubs[k++] = v;
                        } else if (v == array[i]) {
                            done = true;
                        } else {
                            newsubs[k++] = v;
                        }
                    }
                    subs = newsubs;
                }
            } else {
                news[index++] = array[i];
            }
        }
        if (index == array.length) {
            return array;
        }
        final int[] rs = new int[index];
        System.arraycopy(news, 0, rs, 0, index);
        return rs;
    }

    /**
     * 将指定的long元素从数组中删除, 相同的元素会根据items里重复次数来执行删除 <br>
     * 例如: <br>
     * remove(new long[]{1, 1, 1, 2, 2, 3, 3, 3}, false, 1, 1, 2, 3, 3) = [1,2,3]<br>
     *
     * @param array 原数组
     * @param items long[]
     * @return 新数组
     */
    public static long[] removeMatch(final long[] array, final long... items) {
        return remove(array, false, items);
    }

    /**
     * 将指定的long元素从数组中删除, repeat=false时相同的元素会根据items里重复次数来执行删除 <br>
     * 例如: <br>
     * remove(new long[]{1, 1, 1, 2, 2, 3, 3, 3}, true, 1, 1, 2, 3, 3) = [] <br>
     * remove(new long[]{1, 1, 1, 2, 2, 3, 3, 3}, false, 1, 1, 2, 3, 3) = [1,2,3]<br>
     *
     * @param array 原数组
     * @param repeat 是否重复删除相同的元素
     * @param items long[]
     * @return 新数组
     */
    public static long[] remove(final long[] array, boolean repeat, final long... items) {
        if (array == null || array.length == 0 || items == null || items.length == 0) {
            return array;
        }
        final long[] news = new long[array.length];
        long[] subs = items;
        int index = 0;
        for (int i = 0; i < news.length; i++) {
            if (subs.length > 0 && contains(subs, array[i])) {
                if (!repeat) {
                    long[] newsubs = new long[subs.length - 1];
                    int k = 0;
                    boolean done = false;
                    for (long v : subs) {
                        if (done) {
                            newsubs[k++] = v;
                        } else if (v == array[i]) {
                            done = true;
                        } else {
                            newsubs[k++] = v;
                        }
                    }
                    subs = newsubs;
                }
            } else {
                news[index++] = array[i];
            }
        }
        if (index == array.length) {
            return array;
        }
        final long[] rs = new long[index];
        System.arraycopy(news, 0, rs, 0, index);
        return rs;
    }

    /**
     * 将符合条件的元素从集合中删除
     *
     * @param <T> 泛型
     * @param objs 原集合
     * @param filter Predicate
     * @return 新集合
     */
    public static <T> Collection<T> remove(final Collection<T> objs, Predicate filter) {
        if (objs == null || filter == null) {
            return objs;
        }
        List<T> list = new ArrayList<>();
        for (T t : objs) {
            if (filter.test(t)) {
                list.add(t);
            }
        }
        if (!list.isEmpty()) {
            objs.removeAll(list);
        }
        return objs;
    }

    /**
     * 判断字符串是否包含指定的字符，包含返回true
     *
     * @param string 字符串
     * @param values 字符集合
     * @return boolean
     */
    public static boolean contains(String string, char... values) {
        if (string == null) {
            return false;
        }
        for (char ch : Utility.charArray(string)) {
            for (char ch2 : values) {
                if (ch == ch2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 比较两集合元素是否一样， 顺序不要求一样
     *
     * @param <T> 泛型
     * @param array1 集合
     * @param array2 集合
     * @return 元素是否完全相同
     */
    public static <T> boolean equalsElement(T[] array1, T[] array2) {
        if (array1 == null && array2 == null) {
            return true;
        }
        if (array1 == null || array2 == null) {
            return false;
        }
        if (array1.length != array2.length) {
            return false;
        }
        return equalsElement(ofList(array1), ofList(array2));
    }

    /**
     * 比较两集合元素是否一样， 顺序不要求一样
     *
     * @param <T> 泛型
     * @param col1 集合
     * @param col2 集合
     * @return 元素是否完全相同
     */
    public static <T> boolean equalsElement(Collection<T> col1, Collection<T> col2) {
        if (col1 == null && col2 == null) {
            return true;
        }
        if (col1 == null || col2 == null) {
            return false;
        }
        if (col1.size() != col2.size()) {
            return false;
        }
        // {1,2,2}, {1,1,2}
        List<T> list = new ArrayList<>(col2);
        for (T item : col1) {
            if (!list.remove(item)) {
                return false;
            }
        }
        return list.isEmpty();
    }

    /**
     * 比较两集合元素是否一样， 顺序不要求一样
     *
     * @param <K> 泛型
     * @param <V> 泛型
     * @param map1 集合
     * @param map2 集合
     * @return 元素是否完全相同
     */
    public static <K, V> boolean equalsElement(Map<K, V> map1, Map<K, V> map2) {
        if (map1 == null && map2 == null) {
            return true;
        }
        if (map1 == null || map2 == null) {
            return false;
        }
        if (map1.size() != map2.size()) {
            return false;
        }
        for (Map.Entry<K, V> en : map1.entrySet()) {
            if (!Objects.equals(en.getValue(), map2.get(en.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断指定值是否包含指定的数组中，包含返回true
     *
     * @param values 集合
     * @param value 单值
     * @return boolean
     */
    public static boolean contains(char[] values, char value) {
        if (values == null) {
            return false;
        }
        for (char v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断指定值是否包含指定的数组中，包含返回true
     *
     * @param values 集合
     * @param value 单值
     * @return boolean
     */
    public static boolean contains(short[] values, short value) {
        if (values == null) {
            return false;
        }
        for (short v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断指定值(不要包含相同的元素)是否包含指定的数组中，包含返回true
     *
     * @param values 集合
     * @param items 多值
     * @return boolean
     */
    public static boolean contains(short[] values, short... items) {
        if (values == null) {
            return false;
        }
        for (short item : items) {
            if (!contains(values, item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断指定值是否包含指定的数组中，包含返回true
     *
     * @param values 集合
     * @param value 单值
     * @return boolean
     */
    public static boolean contains(int[] values, int value) {
        if (values == null) {
            return false;
        }
        for (int v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断指定值(不要包含相同的元素)是否包含指定的数组中，包含返回true
     *
     * @param values 集合
     * @param items 多值
     * @return boolean
     */
    public static boolean contains(int[] values, int... items) {
        if (values == null) {
            return false;
        }
        for (int item : items) {
            if (!contains(values, item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断指定值是否包含指定的数组中，包含返回true
     *
     * @param values 集合
     * @param value 单值
     * @return boolean
     */
    public static boolean contains(long[] values, long value) {
        if (values == null) {
            return false;
        }
        for (long v : values) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断指定值(不要包含相同的元素)是否包含指定的数组中，包含返回true
     *
     * @param values 集合
     * @param items 多值
     * @return boolean
     */
    public static boolean contains(long[] values, long... items) {
        if (values == null) {
            return false;
        }
        for (long item : items) {
            if (!contains(values, item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断指定值是否包含指定的数组中，包含返回true
     *
     * @param <T> 泛型
     * @param values 集合
     * @param value 单值
     * @return boolean
     */
    public static <T> boolean contains(T[] values, T value) {
        if (values == null) {
            return false;
        }
        for (T v : values) {
            if (Objects.equals(v, value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断指定值是否包含指定的数组中，包含返回true
     *
     * @param <T> 泛型
     * @param values 集合
     * @param predicate 过滤条件
     * @return boolean
     */
    public static <T> boolean contains(T[] values, Predicate<T> predicate) {
        if (values == null) {
            return false;
        }
        for (T v : values) {
            if (predicate.test(v)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断指定值是否包含指定的数组中，包含返回true
     *
     * @param <T> 泛型
     * @param values 集合
     * @param predicate 过滤条件
     * @return boolean
     */
    public static <T> boolean contains(Collection<T> values, Predicate<T> predicate) {
        if (values == null) {
            return false;
        }
        for (T v : values) {
            if (predicate.test(v)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将指定的short元素是否数组中完全包含，重复元素的次数也要相同 <br>
     * 例如: <br>
     * containsMatch(new short[]{1, 2, 2, 3, 3, 3}, 1, 2, 3, 3) = true <br>
     * containsMatch(new short[]{1, 2, 2, 3, 3, 3}, 1, 1, 2, 3, 3) = false <br>
     *
     * @param array 原数组
     * @param items short[]
     * @return 是否完全包含
     */
    public static boolean containsMatch(final short[] array, final short... items) {
        if (array == null) {
            return false;
        }
        if (items == null || items.length == 0) {
            return true;
        }
        if (array.length == 0 && items.length == 0) {
            return true;
        }
        if (array.length < items.length) {
            return false;
        }

        short[] subs = array;
        for (short item : items) {
            if (!contains(subs, item)) {
                return false;
            }
            short[] newsubs = new short[subs.length - 1];
            int k = 0;
            boolean done = false;
            for (short v : subs) {
                if (done) {
                    newsubs[k++] = v;
                } else if (v == item) {
                    done = true;
                } else {
                    newsubs[k++] = v;
                }
            }
            subs = newsubs;
        }
        return true;
    }

    /**
     * 将指定的int元素是否数组中完全包含，重复元素的次数也要相同 <br>
     * 例如: <br>
     * containsMatch(new int[]{1, 2, 2, 3, 3, 3}, 1, 2, 3, 3) = true <br>
     * containsMatch(new int[]{1, 2, 2, 3, 3, 3}, 1, 1, 2, 3, 3) = false <br>
     *
     * @param array 原数组
     * @param items int[]
     * @return 是否完全包含
     */
    public static boolean containsMatch(final int[] array, final int... items) {
        if (array == null) {
            return false;
        }
        if (items == null || items.length == 0) {
            return true;
        }
        if (array.length == 0 && items.length == 0) {
            return true;
        }
        if (array.length < items.length) {
            return false;
        }

        int[] subs = array;
        for (int item : items) {
            if (!contains(subs, item)) {
                return false;
            }
            int[] newsubs = new int[subs.length - 1];
            int k = 0;
            boolean done = false;
            for (int v : subs) {
                if (done) {
                    newsubs[k++] = v;
                } else if (v == item) {
                    done = true;
                } else {
                    newsubs[k++] = v;
                }
            }
            subs = newsubs;
        }
        return true;
    }

    /**
     * 将指定的long元素是否数组中完全包含，重复元素的次数也要相同 <br>
     * 例如: <br>
     * containsMatch(new long[]{1, 2, 2, 3, 3, 3}, 1, 2, 3, 3) = true <br>
     * containsMatch(new long[]{1, 2, 2, 3, 3, 3}, 1, 1, 2, 3, 3) = false <br>
     *
     * @param array 原数组
     * @param items long[]
     * @return 是否完全包含
     */
    public static boolean containsMatch(final long[] array, final long... items) {
        if (array == null) {
            return false;
        }
        if (items == null || items.length == 0) {
            return true;
        }
        if (array.length == 0 && items.length == 0) {
            return true;
        }
        if (array.length < items.length) {
            return false;
        }

        long[] subs = array;
        for (long item : items) {
            if (!contains(subs, item)) {
                return false;
            }
            long[] newsubs = new long[subs.length - 1];
            int k = 0;
            boolean done = false;
            for (long v : subs) {
                if (done) {
                    newsubs[k++] = v;
                } else if (v == item) {
                    done = true;
                } else {
                    newsubs[k++] = v;
                }
            }
            subs = newsubs;
        }
        return true;
    }

    /**
     * 删除掉字符串数组中包含指定的字符串
     *
     * @param columns 待删除数组
     * @param cols 需排除的字符串
     * @return 新字符串数组
     */
    public static String[] exclude(final String[] columns, final String... cols) {
        if (columns == null || columns.length == 0 || cols == null || cols.length == 0) {
            return columns;
        }
        int count = 0;
        for (String column : columns) {
            boolean flag = false;
            for (String col : cols) {
                if (column != null && column.equals(col)) {
                    flag = true;
                    break;
                }
            }
            if (flag) {
                count++;
            }
        }
        if (count == 0) {
            return columns;
        }
        if (count == columns.length) {
            return new String[0];
        }
        final String[] newcols = new String[columns.length - count];
        count = 0;
        for (String column : columns) {
            boolean flag = false;
            for (String col : cols) {
                if (column != null && column.equals(col)) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                newcols[count++] = column;
            }
        }
        return newcols;
    }

    /**
     * 查询指定对象, 没有返回null
     *
     * @param <T> 泛型
     * @param array 数组
     * @param predicate 查找器
     * @return 对象
     */
    public static <T> T find(final T[] array, final Predicate<T> predicate) {
        if (array == null) {
            return null;
        }
        for (T item : array) {
            if (item != null && predicate.test(item)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 查询指定对象, 没有返回null
     *
     * @param <T> 泛型
     * @param array 数组
     * @param predicate 查找器
     * @return 对象
     */
    public static <T> T find(final Collection<T> array, final Predicate<T> predicate) {
        if (array == null) {
            return null;
        }
        for (T item : array) {
            if (item != null && predicate.test(item)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 查询指定对象位置, 没有返回-1
     *
     * @param <T> 泛型
     * @param array 数组
     * @param predicate 查找器
     * @return 位置
     */
    public static <T> int indexOf(final T[] array, final Predicate<T> predicate) {
        if (array == null) {
            return -1;
        }
        int index = -1;
        for (T item : array) {
            ++index;
            if (item != null && predicate.test(item)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 查询指定对象位置, 没有返回-1
     *
     * @param <T> 泛型
     * @param array 数组
     * @param predicate 查找器
     * @return 位置
     */
    public static <T> int indexOf(final Collection<T> array, final Predicate<T> predicate) {
        if (array == null) {
            return -1;
        }
        int index = -1;
        for (T item : array) {
            ++index;
            if (item != null && predicate.test(item)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final byte[] array, final byte element) {
        if (array == null) {
            return -1;
        }
        for (int i = 0; i < array.length; ++i) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param fromIndex 起始位置，从0开始
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final byte[] array, int fromIndex, final byte element) {
        if (array == null) {
            return -1;
        }
        for (int i = fromIndex; i < array.length; ++i) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final short[] array, final short element) {
        return indexOf(array, 0, element);
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param fromIndex 起始位置，从0开始
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final short[] array, int fromIndex, final short element) {
        if (array == null) {
            return -1;
        }
        for (int i = fromIndex; i < array.length; ++i) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final char[] array, final char element) {
        return indexOf(array, 0, element);
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param fromIndex 起始位置，从0开始
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final char[] array, int fromIndex, final char element) {
        if (array == null) {
            return -1;
        }
        for (int i = fromIndex; i < array.length; ++i) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final int[] array, final int element) {
        return indexOf(array, 0, element);
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param fromIndex 起始位置，从0开始
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final int[] array, int fromIndex, final int element) {
        if (array == null) {
            return -1;
        }
        for (int i = fromIndex; i < array.length; ++i) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final long[] array, final long element) {
        return indexOf(array, 0, element);
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param fromIndex 起始位置，从0开始
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final long[] array, int fromIndex, final long element) {
        if (array == null) {
            return -1;
        }
        for (int i = fromIndex; i < array.length; ++i) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final float[] array, final float element) {
        return indexOf(array, 0, element);
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param fromIndex 起始位置，从0开始
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final float[] array, int fromIndex, final float element) {
        if (array == null) {
            return -1;
        }
        for (int i = fromIndex; i < array.length; ++i) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final double[] array, final double element) {
        return indexOf(array, 0, element);
    }

    /**
     * 查询指定值位置, 没有返回-1
     *
     * @param array 数组
     * @param fromIndex 起始位置，从0开始
     * @param element 指定值
     * @return 位置
     */
    public static int indexOf(final double[] array, int fromIndex, final double element) {
        if (array == null) {
            return -1;
        }
        for (int i = fromIndex; i < array.length; ++i) {
            if (array[i] == element) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 将源对象转换成目标类型
     *
     * @param <T> 泛型
     * @param type 目标类型
     * @param value 源对象
     * @return 对象
     */
    @ClassDepends(Copier.class)
    public static <T> T convertValue(Type type, Object value) {
        if (type == null) {
            return (T) value;
        }
        final Class typeClazz = TypeToken.typeToClass(type);
        if (value == null) {
            if (typeClazz == boolean.class) {
                return (T) Boolean.FALSE;
            } else if (typeClazz == byte.class) {
                return (T) (Byte) (byte) 0;
            } else if (typeClazz == char.class) {
                return (T) (Character) (char) 0;
            } else if (typeClazz == short.class) {
                return (T) (Short) (short) 0;
            } else if (typeClazz == int.class) {
                return (T) (Integer) 0;
            } else if (typeClazz == long.class) {
                return (T) (Long) 0L;
            } else if (typeClazz == float.class) {
                return (T) (Float) 0F;
            } else if (typeClazz == double.class) {
                return (T) (Double) 0D;
            }
            return (T) value;
        }

        final Class valClazz = value.getClass();
        if (typeClazz == valClazz || typeClazz.isAssignableFrom(valClazz)) {
            return (T) value;
        } else if (typeClazz == String.class) {
            return (T) value.toString();
        } else if (typeClazz == double.class || typeClazz == Double.class) {
            if (value instanceof Number) {
                return (T) (Number) ((Number) value).doubleValue();
            } else if (valClazz == String.class) {
                return (T) (Number) Double.parseDouble(value.toString());
            }
        } else if (typeClazz == float.class || typeClazz == Float.class) {
            if (value instanceof Number) {
                return (T) (Number) ((Number) value).floatValue();
            } else if (valClazz == String.class) {
                return (T) (Number) Float.parseFloat(value.toString());
            }
        } else if (typeClazz == long.class || typeClazz == Long.class) {
            if (value instanceof Number) {
                return (T) (Number) ((Number) value).longValue();
            } else if (valClazz == String.class) {
                return (T) (Number) Long.parseLong(value.toString());
            }
        } else if (typeClazz == int.class || typeClazz == Integer.class) {
            if (value instanceof Number) {
                return (T) (Number) ((Number) value).intValue();
            } else if (valClazz == String.class) {
                return (T) (Number) Integer.parseInt(value.toString());
            }
        } else if (typeClazz == short.class || typeClazz == Short.class) {
            if (value instanceof Number) {
                return (T) (Number) ((Number) value).shortValue();
            } else if (valClazz == String.class) {
                return (T) (Number) Short.parseShort(value.toString());
            }
        } else if (typeClazz == char.class || typeClazz == Character.class) {
            if (value instanceof Number) {
                char ch = (char) ((Number) value).intValue();
                return (T) (Object) ch;
            }
        } else if (typeClazz == byte.class || typeClazz == Byte.class) {
            if (value instanceof Number) {
                return (T) (Number) ((Number) value).byteValue();
            }
        } else if (typeClazz == boolean.class || typeClazz == Boolean.class) {
            if (value instanceof Number) {
                return (T) (Object) (((Number) value).intValue() > 0);
            }
        } else if (typeClazz == BigInteger.class && valClazz == String.class) {
            return (T) new BigInteger(value.toString());
        } else if (typeClazz == BigDecimal.class && valClazz == String.class) {
            return (T) new BigDecimal(value.toString());
        }
        JsonConvert convert = JsonConvert.root();
        if (CharSequence.class.isAssignableFrom(valClazz)) {
            return convert.convertFrom(type, value.toString());
        } else {
            return convert.convertFrom(type, convert.convertToBytes(value));
        }
    }

    /**
     * 将buffer的内容转换成字符串, string参数不为空时会追加在buffer内容字符串之前
     *
     * @param string 字符串前缀
     * @param buffer ByteBuffer
     * @return 字符串
     */
    public static String toString(String string, ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) {
            return string;
        }
        int pos = buffer.position();
        int limit = buffer.limit();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.position(pos);
        buffer.limit(limit);
        if (string == null) {
            return new String(bytes, UTF_8);
        }
        return string + new String(bytes, UTF_8);
    }

    /**
     * 将buffer的内容转换成字符串并打印到控制台, string参数不为空时会追加在buffer内容字符串之前
     *
     * @param string 字符串前缀
     * @param buffer ByteBuffer
     * @return 字符串
     */
    public static String println(String string, ByteBuffer buffer) {
        if (buffer == null || !buffer.hasRemaining()) {
            return string;
        }
        int pos = buffer.position();
        int limit = buffer.limit();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        buffer.position(pos);
        buffer.limit(limit);
        return println(string, bytes);
    }

    /**
     * 将字节数组的内容转换成字符串并打印到控制台, string参数不为空时会追加在字节数组内容字符串之前
     *
     * @param string 字符串前缀
     * @param bytes 字节数组
     * @return 字符串
     */
    public static String println(String string, byte... bytes) {
        return println(string, bytes, 0, bytes.length);
    }

    /**
     * 将字节数组的内容转换成字符串并打印到控制台, string参数不为空时会追加在字节数组内容字符串之前
     *
     * @param string 字符串前缀
     * @param bytes 字节数组
     * @param start 起始位置
     * @param len 长度
     * @return 字符串
     */
    public static String println(String string, byte[] bytes, int start, int len) {
        if (bytes == null) {
            return string;
        }
        StringBuilder sb = new StringBuilder();
        if (string != null) {
            sb.append(string);
        }
        sb.append(len).append(".[");
        boolean last = false;
        for (int i = 0; i < len; i++) {
            byte b = bytes[start + i];
            if (last) {
                sb.append(',');
            }
            int v = b & 0xff;
            sb.append("0x");
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
            last = true;
        }
        sb.append(']');
        (System.out).println(sb);
        return sb.toString();
    }

    /**
     * 返回本机的第一个内网IPv4地址， 没有则返回null
     *
     * @return IPv4地址
     */
    public static InetAddress localInetAddress() {
        InetAddress back = null;
        try {
            Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
            while (nifs.hasMoreElements()) {
                NetworkInterface nif = nifs.nextElement();
                if (!nif.isUp()) {
                    continue;
                }
                Enumeration<InetAddress> eis = nif.getInetAddresses();
                while (eis.hasMoreElements()) {
                    InetAddress ia = eis.nextElement();
                    if (ia.isLoopbackAddress() && ia instanceof Inet4Address) {
                        back = ia;
                    }
                    if (ia.isSiteLocalAddress() && ia instanceof Inet4Address) {
                        return ia;
                    }
                }
            }
        } catch (Exception e) {
            // do nothing
        }
        return back;
    }

    /**
     * 创建 CompletionHandler 对象
     *
     * @param <V> 结果对象的泛型
     * @param <A> 附件对象的泛型
     * @param success 成功的回调函数
     * @param fail 失败的回调函数
     * @return CompletionHandler
     */
    public static <V, A> CompletionHandler<V, A> createAsyncHandler(
            final BiConsumer<V, A> success, final BiConsumer<Throwable, A> fail) {
        return new CompletionHandler<V, A>() {
            @Override
            public void completed(V result, A attachment) {
                if (success != null) {
                    success.accept(result, attachment);
                }
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                if (fail != null) {
                    fail.accept(exc, attachment);
                }
            }
        };
    }

    /**
     * 创建没有返回结果的 CompletionHandler 对象
     *
     * @param <A> 附件对象的泛型
     * @param success 成功的回调函数
     * @param fail 失败的回调函数
     * @return CompletionHandler
     */
    public static <A> CompletionHandler<Void, A> createAsyncHandler(
            final Consumer<A> success, final BiConsumer<Throwable, A> fail) {
        return new CompletionHandler<Void, A>() {
            @Override
            public void completed(Void result, A attachment) {
                if (success != null) {
                    success.accept(attachment);
                }
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                if (fail != null) {
                    fail.accept(exc, attachment);
                }
            }
        };
    }

    /**
     * 创建没有附件对象的 CompletionHandler 对象
     *
     * @param <V> 结果对象的泛型
     * @param success 成功的回调函数
     * @param fail 失败的回调函数
     * @return CompletionHandler
     */
    public static <V> CompletionHandler<V, Void> createAsyncHandler(
            final Consumer<V> success, final Consumer<Throwable> fail) {
        return new CompletionHandler<V, Void>() {
            @Override
            public void completed(V result, Void attachment) {
                if (success != null) {
                    success.accept(result);
                }
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                if (fail != null) {
                    fail.accept(exc);
                }
            }
        };
    }

    /**
     * 获取格式为yyyy-MM-dd HH:mm:ss的当前时间
     *
     * @return 格式为yyyy-MM-dd HH:mm:ss的时间值
     */
    @Deprecated(since = "2.8.0")
    public static String now() {
        return Times.now();
    }

    /**
     * 获取格式为yyyy-MM-dd HH:mm:ss.fff的当前时间
     *
     * @return 格式为yyyy-MM-dd HH:mm:ss.fff的时间值
     */
    @Deprecated(since = "2.8.0")
    public static String nowMillis() {
        return Times.nowMillis();
    }

    /**
     * 将指定时间格式化为 yyyy-MM-dd HH:mm:ss
     *
     * @param time 待格式化的时间
     * @return 格式为yyyy-MM-dd HH:mm:ss的时间值
     */
    @Deprecated(since = "2.8.0")
    public static String formatTime(long time) {
        return Times.formatTime(time);
    }

    /**
     * 将时间值转换为长度为9的36进制值
     *
     * @param time 时间值
     * @return 36进制时间值
     */
    @Deprecated(since = "2.8.0")
    public static String format36time(long time) {
        return Times.format36time(time);
    }

    /**
     * 获取当天凌晨零点的格林时间
     *
     * @return 毫秒数
     */
    @Deprecated(since = "2.8.0")
    public static long midnight() {
        return Times.midnight();
    }

    /**
     * 获取指定时间当天凌晨零点的格林时间
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    @Deprecated(since = "2.8.0")
    public static long midnight(long time) {
        return Times.midnight(time);
    }

    /**
     * 获取当天20151231格式的int值
     *
     * @return 20151231格式的int值
     */
    @Deprecated(since = "2.8.0")
    public static int today() {
        return Times.today();
    }

    /**
     * 获取当天151231格式的int值
     *
     * @return 151231格式的int值
     */
    @Deprecated(since = "2.8.0")
    public static int todayYYMMDD() {
        return Times.todayYYMMDD();
    }

    /**
     * 获取当天1512312359格式的int值
     *
     * @return 1512312359格式的int值
     */
    @Deprecated(since = "2.8.0")
    public static int todayYYMMDDHHmm() {
        return Times.todayYYMMDDHHmm();
    }

    /**
     * 获取当天20151231235959格式的int值
     *
     * @return 20151231235959格式的int值
     */
    @Deprecated(since = "2.8.0")
    public static long todayYYYYMMDDHHmmss() {
        return Times.todayYYYYMMDDHHmmss();
    }

    /**
     * 获取当天151231235959格式的int值
     *
     * @return 151231235959格式的int值
     */
    @Deprecated(since = "2.8.0")
    public static long todayYYMMDDHHmmss() {
        return Times.todayYYMMDDHHmmss();
    }

    /**
     * 获取明天20151230格式的int值
     *
     * @return 20151230格式的int值
     */
    @Deprecated(since = "2.8.0")
    public static int tomorrow() {
        return Times.tomorrow();
    }

    /**
     * 获取明天151230格式的int值
     *
     * @return 151230格式的int值
     */
    @Deprecated(since = "2.8.0")
    public static int tomorrowYYMMDD() {
        return Times.tomorrowYYMMDD();
    }

    /**
     * 获取昨天20151230格式的int值
     *
     * @return 20151230格式的int值
     */
    @Deprecated(since = "2.8.0")
    public static int yesterday() {
        return Times.yesterday();
    }

    /**
     * 获取昨天151230格式的int值
     *
     * @return 151230格式的int值
     */
    @Deprecated(since = "2.8.0")
    public static int yesterdayYYMMDD() {
        return Times.yesterdayYYMMDD();
    }

    /**
     * 获取指定时间的20160202格式的int值
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    @Deprecated(since = "2.8.0")
    public static int yyyyMMdd(long time) {
        return Times.yyyyMMdd(time);
    }

    /**
     * 获取指定时间的160202格式的int值
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    @Deprecated(since = "2.8.0")
    public static int yyMMdd(long time) {
        return Times.yyMMdd(time);
    }

    /**
     * 获取当天16020223格式的int值
     *
     * @param time 指定时间
     * @return 16020223格式的int值
     */
    @Deprecated(since = "2.8.0")
    public static int yyMMDDHHmm(long time) {
        return Times.yyMMDDHHmm(time);
    }

    /**
     * 获取时间点所在星期的周一
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    @Deprecated(since = "2.8.0")
    public static long monday(long time) {
        return Times.monday(time);
    }

    /**
     * 获取时间点所在星期的周日
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    @Deprecated(since = "2.8.0")
    public static long sunday(long time) {
        return Times.sunday(time);
    }

    /**
     * 获取时间点所在月份的1号
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    @Deprecated(since = "2.8.0")
    public static long monthFirstDay(long time) {
        return Times.monthFirstDay(time);
    }

    /**
     * 获取时间点所在月份的最后一天
     *
     * @param time 指定时间
     * @return 毫秒数
     */
    @Deprecated(since = "2.8.0")
    public static long monthLastDay(long time) {
        return Times.monthLastDay(time);
    }

    /**
     * 将时间格式化, 支持%1$ty 和 %ty两种格式
     *
     * @param format 格式
     * @param size 带%t的个数，值小于0则需要计算
     * @param time 时间
     * @since 2.7.0
     * @return 时间格式化
     */
    @Deprecated(since = "2.8.0")
    public static String formatTime(String format, int size, Object time) {
        return Times.formatTime(format, size, time);
    }

    /**
     * 将int[]强制转换成byte[]
     *
     * @param value int[]
     * @return byte[]
     */
    public static byte[] intsToBytes(int[] value) {
        if (value == null) {
            return null;
        }
        byte[] bs = new byte[value.length];
        for (int i = 0; i < bs.length; i++) {
            bs[i] = (byte) value[i];
        }
        return bs;
    }

    /**
     * MD5加密
     *
     * @param str 待加密数据
     * @return md5值
     */
    public static String md5Hex(String str) {
        return binToHexString(md5(str));
    }

    /**
     * MD5加密
     *
     * @param input 待加密数据
     * @return md5值
     */
    public static String md5Hex(byte[] input) {
        return binToHexString(md5(input));
    }

    /**
     * MD5加密
     *
     * @param str 待加密数据
     * @return md5值
     */
    public static byte[] md5(String str) {
        if (str == null) {
            return null;
        }
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            throw new RedkaleException("Couldn't find a MD5 provider", ex);
        }
        return md5.digest(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * MD5加密
     *
     * @param input 待加密数据
     * @return md5值
     */
    public static byte[] md5(byte[] input) {
        if (input == null) {
            return null;
        }
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            throw new RedkaleException("Couldn't find a MD5 provider", ex);
        }
        return md5.digest(input);
    }

    /**
     * MD5加密
     *
     * @param input 待加密数据
     * @param offset 偏移量
     * @param len 长度
     * @return md5值
     */
    public static byte[] md5(byte[] input, int offset, int len) {
        if (input == null) {
            return null;
        }
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            md5.update(input, offset, len);
            return md5.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new RedkaleException("Couldn't find a MD5 provider", ex);
        }
    }

    /**
     * SHA-256
     *
     * @param str 待hash数据
     * @return hash值
     */
    public static String sha256Hex(String str) {
        return binToHexString(sha256(str));
    }

    /**
     * SHA-256
     *
     * @param input 待hash数据
     * @return hash值
     */
    public static String sha256Hex(byte[] input) {
        return binToHexString(sha256(input));
    }

    /**
     * SHA-256
     *
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String sha256Hex(byte[] input, int offset, int len) {
        return binToHexString(sha256(input, offset, len));
    }

    /**
     * 以0x开头的 SHA-256
     *
     * @param input 待hash数据
     * @return hash值
     */
    public static String sha256Hex0x(byte[] input) {
        return binTo0xHexString(sha256(input));
    }

    /**
     * 以0x开头的 SHA-256
     *
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String sha256Hex0x(byte[] input, int offset, int len) {
        return binTo0xHexString(sha256(input, offset, len));
    }

    /**
     * SHA-256
     *
     * @param str 待hash数据
     * @return hash值
     */
    public static byte[] sha256(String str) {
        if (str == null) {
            return null;
        }
        MessageDigest digester;
        try {
            digester = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new RedkaleException("Couldn't find a SHA-256 provider", ex);
        }
        return digester.digest(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA-256
     *
     * @param input 待hash数据
     * @return hash值
     */
    public static byte[] sha256(byte[] input) {
        if (input == null) {
            return null;
        }
        MessageDigest digester;
        try {
            digester = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new RedkaleException(ex);
        }
        return digester.digest(input);
    }

    /**
     * SHA-256
     *
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static byte[] sha256(byte[] input, int offset, int len) {
        if (input == null) {
            return null;
        }
        MessageDigest digester;
        try {
            digester = MessageDigest.getInstance("SHA-256");
            digester.update(input, offset, len);
            return digester.digest();
        } catch (Exception ex) {
            throw new RedkaleException(ex);
        }
    }

    /**
     * HmacSHA1
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha1Base64(String key, String input) {
        return Base64.getEncoder().encodeToString(hmacSha1(key.getBytes(UTF_8), input.getBytes(UTF_8)));
    }

    /**
     * HmacSHA1
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha1Base64(byte[] key, byte[] input) {
        return Base64.getEncoder().encodeToString(hmacSha1(key, input));
    }

    /**
     * HmacSHA1
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String hmacSha1Base64(byte[] key, byte[] input, int offset, int len) {
        return Base64.getEncoder().encodeToString(hmacSha1(key, input, offset, len));
    }

    /**
     * HmacSHA1
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha1Hex(byte[] key, byte[] input) {
        return binToHexString(hmacSha1(key, input));
    }

    /**
     * HmacSHA1
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String hmacSha1Hex(byte[] key, byte[] input, int offset, int len) {
        return binToHexString(hmacSha1(key, input, offset, len));
    }

    /**
     * 以0x开头的 HmacSHA1
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha1Hex0x(byte[] key, byte[] input) {
        return binTo0xHexString(hmacSha1(key, input));
    }

    /**
     * 以0x开头的 HmacSHA1
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String hmacSha1Hex0x(byte[] key, byte[] input, int offset, int len) {
        return binTo0xHexString(hmacSha1(key, input, offset, len));
    }

    /**
     * HmacSHA1
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static byte[] hmacSha1(byte[] key, byte[] input) {
        if (input == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(input);
        } catch (InvalidKeyException | NoSuchAlgorithmException ex) {
            throw new RedkaleException(ex);
        }
    }

    /**
     * HmacSHA1
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static byte[] hmacSha1(byte[] key, byte[] input, int offset, int len) {
        if (input == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            mac.update(input, offset, len);
            return mac.doFinal();
        } catch (InvalidKeyException | NoSuchAlgorithmException ex) {
            throw new RedkaleException(ex);
        }
    }

    /**
     * HmacSHA256
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha256Base64(String key, String input) {
        return Base64.getEncoder().encodeToString(hmacSha256(key.getBytes(UTF_8), input.getBytes(UTF_8)));
    }

    /**
     * HmacSHA256
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha256Base64(byte[] key, byte[] input) {
        return Base64.getEncoder().encodeToString(hmacSha256(key, input));
    }

    /**
     * HmacSHA256
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String hmacSha256Base64(byte[] key, byte[] input, int offset, int len) {
        return Base64.getEncoder().encodeToString(hmacSha256(key, input, offset, len));
    }

    /**
     * HmacSHA256
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha256Hex(byte[] key, byte[] input) {
        return binToHexString(hmacSha256(key, input));
    }

    /**
     * HmacSHA256
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String hmacSha256Hex(byte[] key, byte[] input, int offset, int len) {
        return binToHexString(hmacSha256(key, input, offset, len));
    }

    /**
     * 以0x开头的 HmacSHA256
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha256Hex0x(byte[] key, byte[] input) {
        return binTo0xHexString(hmacSha256(key, input));
    }

    /**
     * 以0x开头的 HmacSHA256
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String hmacSha256Hex0x(byte[] key, byte[] input, int offset, int len) {
        return binTo0xHexString(hmacSha256(key, input, offset, len));
    }

    /**
     * HmacSHA256
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static byte[] hmacSha256(byte[] key, byte[] input) {
        if (input == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(input);
        } catch (InvalidKeyException | NoSuchAlgorithmException ex) {
            throw new RedkaleException(ex);
        }
    }

    /**
     * HmacSHA256
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static byte[] hmacSha256(byte[] key, byte[] input, int offset, int len) {
        if (input == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update(input, offset, len);
            return mac.doFinal();
        } catch (InvalidKeyException | NoSuchAlgorithmException ex) {
            throw new RedkaleException(ex);
        }
    }

    /**
     * HmacSHA512
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha512Base64(String key, String input) {
        return Base64.getEncoder().encodeToString(hmacSha512(key.getBytes(UTF_8), input.getBytes(UTF_8)));
    }

    /**
     * HmacSHA512
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha512Base64(byte[] key, byte[] input) {
        return Base64.getEncoder().encodeToString(hmacSha512(key, input));
    }

    /**
     * HmacSHA512
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String hmacSha512Base64(byte[] key, byte[] input, int offset, int len) {
        return Base64.getEncoder().encodeToString(hmacSha512(key, input, offset, len));
    }

    /**
     * HmacSHA512
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha512Hex(byte[] key, byte[] input) {
        return binToHexString(hmacSha512(key, input));
    }

    /**
     * HmacSHA512
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String hmacSha512Hex(byte[] key, byte[] input, int offset, int len) {
        return binToHexString(hmacSha512(key, input, offset, len));
    }

    /**
     * 以0x开头的 HmacSHA512
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static String hmacSha512Hex0x(byte[] key, byte[] input) {
        return binTo0xHexString(hmacSha512(key, input));
    }

    /**
     * 以0x开头的 HmacSHA512
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static String hmacSha512Hex0x(byte[] key, byte[] input, int offset, int len) {
        return binTo0xHexString(hmacSha512(key, input, offset, len));
    }

    /**
     * HmacSHA512
     *
     * @param key 密钥
     * @param input 待hash数据
     * @return hash值
     */
    public static byte[] hmacSha512(byte[] key, byte[] input) {
        if (input == null) {
            return null;
        }
        try {
            SecretKey sk = new SecretKeySpec(key, "HmacSHA512");
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(sk);
            return mac.doFinal(input);
        } catch (InvalidKeyException | NoSuchAlgorithmException ex) {
            throw new RedkaleException(ex);
        }
    }

    /**
     * HmacSHA512
     *
     * @param key 密钥
     * @param input 待hash数据
     * @param offset 偏移量
     * @param len 长度
     * @return hash值
     */
    public static byte[] hmacSha512(byte[] key, byte[] input, int offset, int len) {
        if (input == null) {
            return null;
        }
        try {
            SecretKey sk = new SecretKeySpec(key, "HmacSHA512");
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(sk);
            mac.update(input, offset, len);
            return mac.doFinal();
        } catch (InvalidKeyException | NoSuchAlgorithmException ex) {
            throw new RedkaleException(ex);
        }
    }

    /**
     * 根据指定算法进行hash
     *
     * @param algorithm 算法名
     * @param input 待hash数据
     * @return hash值
     */
    public static byte[] hash(String algorithm, byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.toUpperCase());
            return digest.digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new RedkaleException("Couldn't find a " + algorithm + " provider", ex);
        }
    }

    /**
     * 根据指定算法进行hash
     *
     * @param algorithm 算法名
     * @param input 待hash数据
     * @param offset 偏移量
     * @param length 长度
     * @return hash值
     */
    public static byte[] hash(String algorithm, byte[] input, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.toUpperCase());
            digest.update(input, offset, length);
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new RedkaleException("Couldn't find a " + algorithm + " provider", ex);
        }
    }

    /**
     * 随机
     *
     * @return 随机
     */
    public static SecureRandom secureRandom() {
        return random;
    }

    /**
     * 生成随机数
     *
     * @param size 随机数长度
     * @return 随机数
     */
    public static byte[] generateRandomBytes(int size) {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return bytes;
    }

    /**
     * 将字节数组转换为以0x开头的16进制字符串
     *
     * @param bytes 字节数组
     * @return 16进制字符串
     */
    public static String binTo0xHexString(byte[] bytes) {
        return "0x" + new String(binToHex(bytes));
    }

    /**
     * 将字节数组转换为以0x开头的16进制字符串
     *
     * @param bytes 字节数组
     * @param offset 偏移量
     * @param len 长度
     * @return 16进制字符串
     */
    public static String binTo0xHexString(byte[] bytes, int offset, int len) {
        return "0x" + new String(binToHex(bytes, offset, len));
    }

    /**
     * 将字节数组转换为16进制字符串
     *
     * @param bytes 字节数组
     * @return 16进制字符串
     */
    public static String binToHexString(byte[] bytes) {
        return new String(binToHex(bytes));
    }

    /**
     * 将字节数组转换为16进制字符数组
     *
     * @param bytes 字节数组
     * @return 16进制字符串的字符数组
     */
    public static char[] binToHex(byte[] bytes) {
        return binToHex(bytes, 0, bytes.length);
    }

    /**
     * 将字节数组转换为16进制字符串
     *
     * @param bytes 字节数组
     * @param offset 偏移量
     * @param len 长度
     * @return 16进制字符串
     */
    public static String binToHexString(byte[] bytes, int offset, int len) {
        return new String(binToHex(bytes, offset, len));
    }

    /**
     * 将字节数组转换为16进制字符数组
     *
     * @param bytes 字节数组
     * @param offset 偏移量
     * @param len 长度
     * @return 16进制字符串的字符数组
     */
    public static char[] binToHex(byte[] bytes, int offset, int len) {
        final char[] sb = new char[len * 2];
        final int end = offset + len;
        int index = 0;
        final char[] hexs = hex;
        for (int i = offset; i < end; i++) {
            byte b = bytes[i];
            sb[index++] = (hexs[((b >> 4) & 0xF)]);
            sb[index++] = hexs[((b) & 0xF)];
        }
        return sb;
    }

    /**
     * 将16进制字符串转换成字节数组
     *
     * @param src 16进制字符串
     * @return 字节数组
     */
    public static byte[] hexToBin(CharSequence src) {
        return hexToBin(src, 0, src.length());
    }

    /**
     * 将16进制字符串转换成字节数组
     *
     * @param src 16进制字符串
     * @param offset 偏移量
     * @param len 长度
     * @return 字节数组
     */
    public static byte[] hexToBin(CharSequence src, int offset, int len) {
        if (offset == 0 && src.length() > 2 && src.charAt(0) == '0' && (src.charAt(1) == 'x' || src.charAt(1) == 'X')) {
            offset += 2;
            len -= 2;
        }
        final int size = (len + 1) / 2;
        final byte[] bytes = new byte[size];
        String digits = "0123456789abcdef";
        for (int i = 0; i < size; i++) {
            int ch1 = src.charAt(offset + i * 2);
            if ('A' <= ch1 && 'F' >= ch1) {
                ch1 = ch1 - 'A' + 'a';
            }
            int ch2 = src.charAt(offset + i * 2 + 1);
            if ('A' <= ch2 && 'F' >= ch2) {
                ch2 = ch2 - 'A' + 'a';
            }
            int pos1 = digits.indexOf(ch1);
            if (pos1 < 0) {
                throw new NumberFormatException();
            }
            int pos2 = digits.indexOf(ch2);
            if (pos2 < 0) {
                throw new NumberFormatException();
            }
            bytes[i] = (byte) (pos1 * 0x10 + pos2);
        }
        return bytes;
    }

    /**
     * 将16进制字符串转换成字节数组
     *
     * @param str 16进制字符串
     * @return 字节数组
     */
    public static byte[] hexToBin(String str) {
        return hexToBin(charArray(str));
    }

    /**
     * 将16进制字符数组转换成字节数组
     *
     * @param src 16进制字符数组
     * @return 字节数组
     */
    public static byte[] hexToBin(char[] src) {
        return hexToBin(src, 0, src.length);
    }

    /**
     * 将16进制字符数组转换成字节数组
     *
     * @param src 16进制字符数组
     * @param offset 偏移量
     * @param len 长度
     * @return 字节数组
     */
    public static byte[] hexToBin(char[] src, int offset, int len) {
        if (offset == 0 && src.length > 2 && src[0] == '0' && (src[1] == 'x' || src[1] == 'X')) {
            offset += 2;
            len -= 2;
        }
        final int size = (len + 1) / 2;
        final byte[] bytes = new byte[size];
        String digits = "0123456789abcdef";
        for (int i = 0; i < size; i++) {
            int ch1 = src[offset + i * 2];
            if ('A' <= ch1 && 'F' >= ch1) {
                ch1 = ch1 - 'A' + 'a';
            }
            int ch2 = src[offset + i * 2 + 1];
            if ('A' <= ch2 && 'F' >= ch2) {
                ch2 = ch2 - 'A' + 'a';
            }
            int pos1 = digits.indexOf(ch1);
            if (pos1 < 0) {
                throw new NumberFormatException();
            }
            int pos2 = digits.indexOf(ch2);
            if (pos2 < 0) {
                throw new NumberFormatException();
            }
            bytes[i] = (byte) (pos1 * 0x10 + pos2);
        }
        return bytes;
    }

    // -----------------------------------------------------------------------------
    /**
     * 使用UTF-8编码将byte[]转换成char[]
     *
     * @param array byte[]
     * @return char[]
     */
    public static char[] decodeUTF8(final byte[] array) {
        return decodeUTF8(array, 0, array.length);
    }

    public static char[] decodeUTF8(final byte[] array, final int start, final int len) {
        byte b;
        int size = len;
        final byte[] bytes = array;
        final int limit = start + len;
        for (int i = start; i < limit; i++) {
            b = bytes[i];
            if ((b >> 5) == -2) { // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                size--;
            } else if ((b >> 4) == -2) { // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                size -= 2;
            } else if ((b >> 3) == -2) { // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                size -= 2;
            }
        }
        final char[] text = new char[size];
        size = 0;
        for (int i = start; i < limit; ) {
            b = bytes[i++];
            if (b >= 0) { // 1 byte, 7 bits: 0xxxxxxx
                text[size++] = (char) b;
            } else if ((b >> 5) == -2) { // 2 bytes, 11 bits: 110xxxxx 10xxxxxx
                text[size++] = (char) (((b << 6) ^ bytes[i++]) ^ (((byte) 0xC0 << 6) ^ ((byte) 0x80)));
            } else if ((b >> 4) == -2) { // 3 bytes, 16 bits: 1110xxxx 10xxxxxx 10xxxxxx
                text[size++] = (char) ((b << 12)
                        ^ (bytes[i++] << 6)
                        ^ (bytes[i++] ^ (((byte) 0xE0 << 12) ^ ((byte) 0x80 << 6) ^ ((byte) 0x80))));
            } else if ((b >> 3) == -2) { // 4 bytes, 21 bits: 11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
                int uc = ((b << 18)
                        ^ (bytes[i++] << 12)
                        ^ (bytes[i++] << 6)
                        ^ (bytes[i++]
                                ^ (((byte) 0xF0 << 18) ^ ((byte) 0x80 << 12) ^ ((byte) 0x80 << 6) ^ ((byte) 0x80))));
                text[size++] = Character.highSurrogate(uc);
                text[size++] = Character.lowSurrogate(uc);
                // 测试代码 byte[] bs = {(byte)34, (byte)76, (byte)105, (byte)108, (byte)121, (byte)240, (byte)159,
                // (byte)146, (byte)171, (byte)34};
            }
        }
        return text;
    }

    public static byte[] encodeUTF8(final String value) {
        if (value == null) {
            return new byte[0];
        }
        char c;
        int size = 0;
        final String str = value;
        final int limit = str.length();
        for (int i = 0; i < limit; i++) {
            c = str.charAt(i);
            if (c < 0x80) {
                size++;
            } else if (c < 0x800) {
                size += 2;
            } else if (Character.isSurrogate(c)) {
                size += 2;
            } else {
                size += 3;
            }
        }
        final byte[] bytes = new byte[size];
        size = 0;
        for (int i = 0; i < limit; i++) {
            c = str.charAt(i);
            if (c < 0x80) {
                bytes[size++] = (byte) c;
            } else if (c < 0x800) {
                bytes[size++] = (byte) (0xc0 | (c >> 6));
                bytes[size++] = (byte) (0x80 | (c & 0x3f));
            } else if (Character.isSurrogate(c)) { // 连取两个
                int uc = Character.toCodePoint(c, str.charAt(i + 1));
                bytes[size++] = (byte) (0xf0 | (uc >> 18));
                bytes[size++] = (byte) (0x80 | ((uc >> 12) & 0x3f));
                bytes[size++] = (byte) (0x80 | ((uc >> 6) & 0x3f));
                bytes[size++] = (byte) (0x80 | (uc & 0x3f));
                i++;
            } else {
                bytes[size++] = (byte) (0xe0 | (c >> 12));
                bytes[size++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                bytes[size++] = (byte) (0x80 | (c & 0x3f));
            }
        }
        return bytes;
    }

    public static byte[] encodeUTF8(final char[] array) {
        return encodeUTF8(array, 0, array.length);
    }

    public static byte[] encodeUTF8(final char[] text, final int start, final int len) {
        char c;
        int size = 0;
        final char[] chs = text;
        final int limit = start + len;
        for (int i = start; i < limit; i++) {
            c = chs[i];
            if (c < 0x80) {
                size++;
            } else if (c < 0x800) {
                size += 2;
            } else if (Character.isSurrogate(c)) {
                size += 2;
            } else {
                size += 3;
            }
        }
        final byte[] bytes = new byte[size];
        size = 0;
        for (int i = start; i < limit; i++) {
            c = chs[i];
            if (c < 0x80) {
                bytes[size++] = (byte) c;
            } else if (c < 0x800) {
                bytes[size++] = (byte) (0xc0 | (c >> 6));
                bytes[size++] = (byte) (0x80 | (c & 0x3f));
            } else if (Character.isSurrogate(c)) { // 连取两个
                int uc = Character.toCodePoint(c, chs[i + 1]);
                bytes[size++] = (byte) (0xf0 | (uc >> 18));
                bytes[size++] = (byte) (0x80 | ((uc >> 12) & 0x3f));
                bytes[size++] = (byte) (0x80 | ((uc >> 6) & 0x3f));
                bytes[size++] = (byte) (0x80 | (uc & 0x3f));
                i++;
            } else {
                bytes[size++] = (byte) (0xe0 | (c >> 12));
                bytes[size++] = (byte) (0x80 | ((c >> 6) & 0x3f));
                bytes[size++] = (byte) (0x80 | (c & 0x3f));
            }
        }
        return bytes;
    }

    //    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, final char[] array) {
    //        return encodeUTF8(buffer, array, 0, array.length);
    //    }
    //
    //    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, int bytesLength, final char[] array) {
    //        return encodeUTF8(buffer, bytesLength, array, 0, array.length);
    //    }

    public static int encodeUTF8Length(String value) {
        if (value == null) {
            return -1;
        }
        if (value.isEmpty()) {
            return 0;
        }
        char c;
        int size = 0;
        final String str = value;
        final int limit = str.length();
        for (int i = 0; i < limit; i++) {
            c = str.charAt(i);
            if (c < 0x80) {
                size++;
            } else if (c < 0x800) {
                size += 2;
            } else if (Character.isSurrogate(c)) {
                size += 2;
            } else {
                size += 3;
            }
        }
        return size;
    }

    public static int encodeUTF8Length(final char[] text) {
        return encodeUTF8Length(text, 0, text.length);
    }

    public static int encodeUTF8Length(final char[] text, final int start, final int len) {
        char c;
        int size = 0;
        final char[] chs = text;
        final int limit = start + len;
        for (int i = start; i < limit; i++) {
            c = chs[i];
            if (c < 0x80) {
                size++;
            } else if (c < 0x800) {
                size += 2;
            } else if (Character.isSurrogate(c)) {
                size += 2;
            } else {
                size += 3;
            }
        }
        return size;
    }

    public static char[] charArray(StringBuilder value) {
        if (value == null) {
            return null;
        }
        return value.toString().toCharArray();
    }

    public static boolean isLatin1(String value) {
        if (value == null) {
            return true;
        }
        if (strLatin1Function != null) {
            return strLatin1Function.test(value); // LATIN1:0  UTF16:1
        }
        char[] chs = charArray(value);
        for (char ch : chs) {
            if (ch >= 0x80) {
                return false;
            }
        }
        return true;
    }

    // 只能是单字节字符串
    public static byte[] latin1ByteArray(String latin1Value) {
        if (latin1Value == null) {
            return null;
        }
        if (strByteFunction == null) {
            return latin1Value.getBytes();
        }
        return strByteFunction.apply(latin1Value);
    }

    public static byte[] utf16ByteArray(String value) {
        if (value == null || strByteFunction == null) {
            return null;
        }
        return strByteFunction.apply(value);
    }

    public static char[] charArray(String value) {
        if (value == null) {
            return null;
        }
        return value.toCharArray();
    }

    /**
     * 将两个数字组装成一个long
     *
     * @param high 高位值
     * @param low 低位值
     * @return long值
     */
    public static long merge(int high, int low) {
        return (0L + high) << 32 | low;
    }

    //    public static ByteBuffer encodeUTF8(final ByteBuffer buffer, final char[] text, final int start, final int
    // len) {
    //        return encodeUTF8(buffer, encodeUTF8Length(text, start, len), text, start, len);
    //    }
    //
    //    // 返回的ByteBuffer为扩展buffer，为null表示参数中的buffer足够存储数据
    //    public static ByteBuffer encodeUTF8(
    //            final ByteBuffer buffer, int bytesLength, final char[] text, final int start, final int len) {
    //        char c;
    //        char[] chs = text;
    //        final int limit = start + len;
    //        int remain = buffer.remaining();
    //        final ByteBuffer buffer2 =
    //                remain >= bytesLength ? null : ByteBuffer.allocate(bytesLength - remain + 4); //
    // 最差情况buffer最后两byte没有填充
    //        ByteBuffer buf = buffer;
    //        for (int i = start; i < limit; i++) {
    //            c = chs[i];
    //            if (c < 0x80) {
    //                if (buf.remaining() < 1) {
    //                    buf = buffer2;
    //                }
    //                buf.put((byte) c);
    //            } else if (c < 0x800) {
    //                if (buf.remaining() < 2) {
    //                    buf = buffer2;
    //                }
    //                buf.put((byte) (0xc0 | (c >> 6)));
    //                buf.put((byte) (0x80 | (c & 0x3f)));
    //            } else if (Character.isSurrogate(c)) { // 连取两个
    //                if (buf.remaining() < 4) {
    //                    buf = buffer2;
    //                }
    //                int uc = Character.toCodePoint(c, chs[i + 1]);
    //                buf.put((byte) (0xf0 | (uc >> 18)));
    //                buf.put((byte) (0x80 | ((uc >> 12) & 0x3f)));
    //                buf.put((byte) (0x80 | ((uc >> 6) & 0x3f)));
    //                buf.put((byte) (0x80 | (uc & 0x3f)));
    //                i++;
    //            } else {
    //                if (buf.remaining() < 3) {
    //                    buf = buffer2;
    //                }
    //                buf.put((byte) (0xe0 | (c >> 12)));
    //                buf.put((byte) (0x80 | ((c >> 6) & 0x3f)));
    //                buf.put((byte) (0x80 | (c & 0x3f)));
    //            }
    //        }
    //        if (buffer2 != null) {
    //            buffer2.flip();
    //        }
    //        return buffer2; // 返回扩展buffer
    //    }

    public static String getTypeDescriptor(java.lang.reflect.Type type) {
        if (type == null) {
            return null;
        }
        if (type instanceof Class) {
            Class d = (Class) type;
            final StringBuilder sb = new StringBuilder();
            while (true) {
                if (d.isPrimitive()) {
                    char car;
                    if (d == Integer.TYPE) {
                        car = 'I';
                    } else if (d == Void.TYPE) {
                        car = 'V';
                    } else if (d == Boolean.TYPE) {
                        car = 'Z';
                    } else if (d == Byte.TYPE) {
                        car = 'B';
                    } else if (d == Character.TYPE) {
                        car = 'C';
                    } else if (d == Short.TYPE) {
                        car = 'S';
                    } else if (d == Double.TYPE) {
                        car = 'D';
                    } else if (d == Float.TYPE) {
                        car = 'F';
                    } else /* if (d == Long.TYPE) */ {
                        car = 'J';
                    }
                    return sb.append(car).toString();
                } else if (d.isArray()) {
                    sb.append('[');
                    d = d.getComponentType();
                } else {
                    sb.append('L');
                    String name = d.getName();
                    int len = name.length();
                    for (int i = 0; i < len; ++i) {
                        char car = name.charAt(i);
                        sb.append(car == '.' ? '/' : car);
                    }
                    return sb.append(';').toString();
                }
            }
        }
        if (type instanceof ParameterizedType) { // 例如: Map<String, Serializable>
            ParameterizedType pt = (ParameterizedType) type;
            final StringBuilder sb = new StringBuilder();
            String raw = getTypeDescriptor(pt.getRawType());
            sb.append(raw.substring(0, raw.length() - 1)).append('<');
            for (java.lang.reflect.Type item : pt.getActualTypeArguments()) {
                sb.append(getTypeDescriptor(item));
            }
            return sb.append(">;").toString();
        }
        if (type instanceof WildcardType) { // 例如: <? extends Serializable>
            final WildcardType wt = (WildcardType) type;
            final StringBuilder sb = new StringBuilder();
            java.lang.reflect.Type[] us = wt.getUpperBounds();
            java.lang.reflect.Type[] ls = wt.getLowerBounds();
            if (isEmpty(ls)) {
                if (us.length == 1 && us[0] == Object.class) {
                    sb.append('*');
                } else {
                    for (java.lang.reflect.Type f : us) {
                        sb.append('+');
                        sb.append(getTypeDescriptor(f));
                    }
                }
            }
            for (java.lang.reflect.Type f : ls) {
                sb.append('-');
                sb.append(getTypeDescriptor(f));
            }
            return sb.toString();
        }
        // TypeVariable 不支持
        return null;
    }

    // -----------------------------------------------------------------------------
    //    public static javax.net.ssl.SSLContext getDefaultSSLContext() {
    //        return DEFAULTSSL_CONTEXT;
    //    }
    //
    //    public static javax.net.ssl.HostnameVerifier getDefaultHostnameVerifier() {
    //        return defaultVerifier;
    //    }
    //
    //    public static Socket createDefaultSSLSocket(InetSocketAddress address) throws IOException {
    //        return createDefaultSSLSocket(address.getAddress(), address.getPort());
    //    }
    //
    //    public static Socket createDefaultSSLSocket(InetAddress host, int port) throws IOException {
    //        Socket socket = DEFAULTSSL_CONTEXT.getSocketFactory().createSocket(host, port);
    //        return socket;
    //    }
    //
    public static String postHttpContent(String url) throws IOException {
        return remoteHttpContent("POST", url, 0, null, null).toString(StandardCharsets.UTF_8);
    }

    public static String postHttpContent(String url, int timeoutMs) throws IOException {
        return remoteHttpContent("POST", url, timeoutMs, null, null).toString(StandardCharsets.UTF_8);
    }

    public static String postHttpContent(String url, String body) throws IOException {
        return remoteHttpContent("POST", url, 0, null, body).toString(StandardCharsets.UTF_8);
    }

    public static String postHttpContent(String url, int timeoutMs, String body) throws IOException {
        return remoteHttpContent("POST", url, timeoutMs, null, body).toString(StandardCharsets.UTF_8);
    }

    public static String postHttpContent(String url, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("POST", url, 0, headers, body).toString(StandardCharsets.UTF_8);
    }

    public static String postHttpContent(String url, int timeoutMs, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("POST", url, timeoutMs, headers, body).toString(StandardCharsets.UTF_8);
    }

    public static String postHttpContent(String url, Charset charset) throws IOException {
        return remoteHttpContent("POST", url, 0, null, null).toString(charset.name());
    }

    public static String postHttpContent(String url, int timeoutMs, Charset charset) throws IOException {
        return remoteHttpContent("POST", url, timeoutMs, null, null).toString(charset.name());
    }

    public static String postHttpContent(String url, Charset charset, String body) throws IOException {
        return remoteHttpContent("POST", url, 0, null, body).toString(charset.name());
    }

    public static String postHttpContent(String url, int timeoutMs, Charset charset, String body) throws IOException {
        return remoteHttpContent("POST", url, timeoutMs, null, body).toString(charset.name());
    }

    public static String postHttpContent(String url, Charset charset, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("POST", url, 0, headers, body).toString(charset.name());
    }

    public static String postHttpContent(
            String url, int timeoutMs, Charset charset, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("POST", url, timeoutMs, headers, body).toString(charset.name());
    }

    public static byte[] postHttpBytesContent(String url) throws IOException {
        return remoteHttpContent("POST", url, 0, null, null).toByteArray();
    }

    public static byte[] postHttpBytesContent(String url, int timeoutMs) throws IOException {
        return remoteHttpContent("POST", url, timeoutMs, null, null).toByteArray();
    }

    public static byte[] postHttpBytesContent(String url, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("POST", url, 0, headers, body).toByteArray();
    }

    public static byte[] postHttpBytesContent(String url, int timeoutMs, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("POST", url, timeoutMs, headers, body).toByteArray();
    }

    public static String getHttpContent(String url) throws IOException {
        return remoteHttpContent("GET", url, 0, null, null).toString(StandardCharsets.UTF_8);
    }

    public static String getHttpContent(String url, int timeoutMs) throws IOException {
        return remoteHttpContent("GET", url, timeoutMs, null, null).toString(StandardCharsets.UTF_8);
    }

    public static String getHttpContent(String url, Map<String, Serializable> headers, String body) throws IOException {
        return remoteHttpContent("GET", url, 0, headers, body).toString(StandardCharsets.UTF_8);
    }

    public static String getHttpContent(String url, int timeoutMs, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("GET", url, timeoutMs, headers, body).toString(StandardCharsets.UTF_8);
    }

    public static String getHttpContent(String url, Charset charset) throws IOException {
        return remoteHttpContent("GET", url, 0, null, null).toString(charset.name());
    }

    public static String getHttpContent(String url, int timeoutMs, Charset charset) throws IOException {
        return remoteHttpContent("GET", url, timeoutMs, null, null).toString(charset.name());
    }

    public static String getHttpContent(String url, Charset charset, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("GET", url, 0, headers, body).toString(charset.name());
    }

    public static String getHttpContent(
            String url, int timeoutMs, Charset charset, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("GET", url, timeoutMs, headers, body).toString(charset.name());
    }

    public static byte[] getHttpBytesContent(String url) throws IOException {
        return remoteHttpContent("GET", url, 0, null, null).toByteArray();
    }

    public static byte[] getHttpBytesContent(String url, int timeoutMs) throws IOException {
        return remoteHttpContent("GET", url, timeoutMs, null, null).toByteArray();
    }

    public static byte[] getHttpBytesContent(String url, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("GET", url, 0, headers, body).toByteArray();
    }

    public static byte[] getHttpBytesContent(String url, int timeoutMs, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContent("GET", url, timeoutMs, headers, body).toByteArray();
    }

    public static String remoteHttpContent(HttpClient client, String method, String url, Charset charset)
            throws IOException {
        return remoteHttpContentAsync(client, method, url, 0, null, null)
                .thenApply(out -> out.toString(charset == null ? StandardCharsets.UTF_8 : charset))
                .join();
    }

    public static String remoteHttpContent(HttpClient client, String method, String url, int timeoutMs, Charset charset)
            throws IOException {
        return remoteHttpContentAsync(client, method, url, timeoutMs, null, null)
                .thenApply(out -> out.toString(charset == null ? StandardCharsets.UTF_8 : charset))
                .join();
    }

    public static String remoteHttpContent(
            HttpClient client, String method, String url, Charset charset, Map<String, Serializable> headers)
            throws IOException {
        return remoteHttpContentAsync(client, method, url, 0, headers, null)
                .thenApply(out -> out.toString(charset == null ? StandardCharsets.UTF_8 : charset))
                .join();
    }

    public static String remoteHttpContent(
            HttpClient client,
            String method,
            String url,
            Charset charset,
            Map<String, Serializable> headers,
            String body)
            throws IOException {
        return remoteHttpContentAsync(client, method, url, 0, headers, body)
                .thenApply(out -> out.toString(charset == null ? StandardCharsets.UTF_8 : charset))
                .join();
    }

    public static String remoteHttpContent(
            HttpClient client,
            String method,
            String url,
            int timeoutMs,
            Charset charset,
            Map<String, Serializable> headers)
            throws IOException {
        return remoteHttpContentAsync(client, method, url, timeoutMs, headers, null)
                .thenApply(out -> out.toString(charset == null ? StandardCharsets.UTF_8 : charset))
                .join();
    }

    public static String remoteHttpContent(
            HttpClient client,
            String method,
            String url,
            int timeoutMs,
            Charset charset,
            Map<String, Serializable> headers,
            String body)
            throws IOException {
        return remoteHttpContentAsync(client, method, url, timeoutMs, headers, body)
                .thenApply(out -> out.toString(charset == null ? StandardCharsets.UTF_8 : charset))
                .join();
    }

    public static byte[] remoteHttpBytesContent(
            HttpClient client,
            String method,
            String url,
            Charset charset,
            Map<String, Serializable> headers,
            String body)
            throws IOException {
        return remoteHttpContentAsync(client, method, url, 0, headers, body)
                .thenApply(out -> out.toByteArray())
                .join();
    }

    public static byte[] remoteHttpBytesContent(
            HttpClient client,
            String method,
            String url,
            int timeoutMs,
            Charset charset,
            Map<String, Serializable> headers)
            throws IOException {
        return remoteHttpContentAsync(client, method, url, timeoutMs, headers, null)
                .thenApply(out -> out.toByteArray())
                .join();
    }

    public static byte[] remoteHttpBytesContent(
            HttpClient client,
            String method,
            String url,
            int timeoutMs,
            Charset charset,
            Map<String, Serializable> headers,
            String body)
            throws IOException {
        return remoteHttpContentAsync(client, method, url, timeoutMs, headers, body)
                .thenApply(out -> out.toByteArray())
                .join();
    }

    public static ByteArrayOutputStream remoteHttpContent(
            String method, String url, Map<String, Serializable> headers, String body) throws IOException {
        return remoteHttpContent(method, url, 0, headers, body);
    }

    public static ByteArrayOutputStream remoteHttpContent(
            String method, String url, int timeoutMs, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContentAsync(method, url, timeoutMs, headers, body).join();
    }

    public static ByteArrayOutputStream remoteHttpContent(
            HttpClient client, String method, String url, int timeoutMs, Map<String, Serializable> headers, String body)
            throws IOException {
        return remoteHttpContentAsync(client, method, url, timeoutMs, headers, body)
                .join();
    }

    public static CompletableFuture<String> postHttpContentAsync(String url) {
        return remoteHttpContentAsync("POST", url, 0, null, null)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(String url, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, 0, null, null, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(String url, int timeoutMs) {
        return remoteHttpContentAsync("POST", url, timeoutMs, null, null)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, int timeoutMs, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, timeoutMs, null, null, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(String url, String body) {
        return remoteHttpContentAsync("POST", url, 0, null, body)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, String body, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, 0, null, body, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(String url, int timeoutMs, String body) {
        return remoteHttpContentAsync("POST", url, timeoutMs, null, body)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, int timeoutMs, String body, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, timeoutMs, null, body, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("POST", url, 0, headers, body)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, Map<String, Serializable> headers, String body, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, 0, headers, body, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, int timeoutMs, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("POST", url, timeoutMs, headers, body)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url,
            int timeoutMs,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, timeoutMs, headers, body, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(String url, Charset charset) {
        return remoteHttpContentAsync("POST", url, 0, null, null).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, Charset charset, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, 0, null, null, respHeaders).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(String url, int timeoutMs, Charset charset) {
        return remoteHttpContentAsync("POST", url, timeoutMs, null, null).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, int timeoutMs, Charset charset, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, timeoutMs, null, null, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(String url, Charset charset, String body) {
        return remoteHttpContentAsync("POST", url, 0, null, body).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, Charset charset, String body, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, 0, null, body, respHeaders).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            HttpClient client, String url, Charset charset, String body) {
        return remoteHttpContentAsync(client, "POST", url, 0, null, body).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            HttpClient client, String url, Charset charset, String body, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync(client, "POST", url, 0, null, body, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            HttpClient client, String url, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync(client, "POST", url, 0, headers, body)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            HttpClient client,
            String url,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync(client, "POST", url, 0, headers, body, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            HttpClient client, String url, Charset charset, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync(client, "POST", url, 0, headers, body).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            HttpClient client,
            String url,
            Charset charset,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync(client, "POST", url, 0, headers, body, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, int timeoutMs, Charset charset, String body) {
        return remoteHttpContentAsync("POST", url, timeoutMs, null, body).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, int timeoutMs, Charset charset, String body, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, timeoutMs, null, body, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, Charset charset, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("POST", url, 0, headers, body).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url,
            Charset charset,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, 0, headers, body, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url, int timeoutMs, Charset charset, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("POST", url, timeoutMs, headers, body).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> postHttpContentAsync(
            String url,
            int timeoutMs,
            Charset charset,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, timeoutMs, headers, body, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<byte[]> postHttpBytesContentAsync(String url) {
        return remoteHttpContentAsync("POST", url, 0, null, null).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> postHttpBytesContentAsync(
            String url, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, 0, null, null, respHeaders).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> postHttpBytesContentAsync(String url, int timeoutMs) {
        return remoteHttpContentAsync("POST", url, timeoutMs, null, null).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> postHttpBytesContentAsync(
            String url, int timeoutMs, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, timeoutMs, null, null, respHeaders)
                .thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> postHttpBytesContentAsync(
            String url, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("POST", url, 0, headers, body).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> postHttpBytesContentAsync(
            String url, Map<String, Serializable> headers, String body, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, 0, headers, body, respHeaders)
                .thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> postHttpBytesContentAsync(
            String url, int timeoutMs, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("POST", url, timeoutMs, headers, body).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> postHttpBytesContentAsync(
            String url,
            int timeoutMs,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("POST", url, timeoutMs, headers, body, respHeaders)
                .thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<String> getHttpContentAsync(String url) {
        return remoteHttpContentAsync("GET", url, 0, null, null).thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> getHttpContentAsync(String url, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, 0, null, null, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> getHttpContentAsync(String url, int timeoutMs) {
        return remoteHttpContentAsync("GET", url, timeoutMs, null, null)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url, int timeoutMs, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, timeoutMs, null, null, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("GET", url, 0, headers, body)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url, Map<String, Serializable> headers, String body, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, 0, headers, body, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url, int timeoutMs, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("GET", url, timeoutMs, headers, body)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url,
            int timeoutMs,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, timeoutMs, headers, body, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> getHttpContentAsync(String url, Charset charset) {
        return remoteHttpContentAsync("GET", url, 0, null, null).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url, Charset charset, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, 0, null, null, respHeaders).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> getHttpContentAsync(String url, int timeoutMs, Charset charset) {
        return remoteHttpContentAsync("GET", url, timeoutMs, null, null).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url, int timeoutMs, Charset charset, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, timeoutMs, null, null, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url, Charset charset, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("GET", url, 0, headers, body).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url,
            Charset charset,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, 0, headers, body, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url, int timeoutMs, Charset charset, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("GET", url, timeoutMs, headers, body).thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            String url,
            int timeoutMs,
            Charset charset,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, timeoutMs, headers, body, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            java.net.http.HttpClient client, String url, String body, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync(client, "GET", url, 0, null, body, respHeaders)
                .thenApply(out -> out.toString(StandardCharsets.UTF_8));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            java.net.http.HttpClient client,
            String url,
            Charset charset,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync(client, "GET", url, 0, null, body, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<String> getHttpContentAsync(
            java.net.http.HttpClient client,
            String url,
            Charset charset,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync(client, "GET", url, 0, headers, body, respHeaders)
                .thenApply(out -> out.toString(charset));
    }

    public static CompletableFuture<byte[]> getHttpBytesContentAsync(String url) {
        return remoteHttpContentAsync("GET", url, 0, null, null).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> getHttpBytesContentAsync(
            String url, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, 0, null, null, respHeaders).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> getHttpBytesContentAsync(String url, int timeoutMs) {
        return remoteHttpContentAsync("GET", url, timeoutMs, null, null).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> getHttpBytesContentAsync(
            String url, int timeoutMs, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, timeoutMs, null, null, respHeaders)
                .thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> getHttpBytesContentAsync(
            String url, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("GET", url, 0, headers, body).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> getHttpBytesContentAsync(
            String url, Map<String, Serializable> headers, String body, Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, 0, headers, body, respHeaders).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> getHttpBytesContentAsync(
            String url, int timeoutMs, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync("GET", url, timeoutMs, headers, body).thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> getHttpBytesContentAsync(
            String url,
            int timeoutMs,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync("GET", url, timeoutMs, headers, body, respHeaders)
                .thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<byte[]> getHttpBytesContentAsync(
            java.net.http.HttpClient client,
            String url,
            int timeoutMs,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync(client, "GET", url, timeoutMs, headers, body, respHeaders)
                .thenApply(out -> out.toByteArray());
    }

    public static CompletableFuture<ByteArrayOutputStream> remoteHttpContentAsync(
            String method, String url, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync(method, url, 0, headers, body);
    }

    public static CompletableFuture<ByteArrayOutputStream> remoteHttpContentAsync(
            String method,
            String url,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync(method, url, 0, headers, body, respHeaders);
    }

    public static CompletableFuture<ByteArrayOutputStream> remoteHttpContentAsync(
            String method, String url, int timeoutMs, Map<String, Serializable> headers, String body) {
        return remoteHttpContentAsync(httpClient, method, url, timeoutMs, headers, body);
    }

    public static CompletableFuture<ByteArrayOutputStream> remoteHttpContentAsync(
            String method,
            String url,
            int timeoutMs,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        return remoteHttpContentAsync(httpClient, method, url, timeoutMs, headers, body, respHeaders);
    }

    public static CompletableFuture<ByteArrayOutputStream> remoteHttpContentAsync(
            java.net.http.HttpClient client,
            String method,
            String url,
            int timeoutMs,
            Map<String, Serializable> headers,
            String body) {
        return remoteHttpContentAsync(client, method, url, timeoutMs, headers, body, null);
    }

    public static CompletableFuture<ByteArrayOutputStream> remoteHttpContentAsync(
            java.net.http.HttpClient client,
            String method,
            String url,
            int timeoutMs,
            Map<String, Serializable> headers,
            String body,
            Map<String, Serializable> respHeaders) {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs > 0 ? timeoutMs : 6000))
                .method(
                        method,
                        body == null
                                ? java.net.http.HttpRequest.BodyPublishers.noBody()
                                : java.net.http.HttpRequest.BodyPublishers.ofString(body));
        if (headers != null) {
            headers.forEach((n, v) -> {
                if (v instanceof Collection) {
                    for (Object val : (Collection) v) {
                        builder.header(n, val.toString());
                    }
                } else {
                    builder.header(n, v.toString());
                }
            });
        }
        java.net.http.HttpClient c = client == null ? httpClient : client;
        if (c == null) {
            clientLock.lock();
            try {
                if (httpClient == null) {
                    httpClient = java.net.http.HttpClient.newHttpClient();
                }
            } finally {
                clientLock.unlock();
            }
            c = httpClient;
        }
        return c.sendAsync(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray())
                .thenCompose((java.net.http.HttpResponse<byte[]> resp) -> {
                    final int rs = resp.statusCode();
                    if (rs == 301 || rs == 302) {
                        Optional<String> opt = resp.headers().firstValue("Location");
                        if (opt.isPresent()) {
                            return remoteHttpContentAsync(client, method, opt.get(), timeoutMs, headers, body);
                        } else {
                            return CompletableFuture.failedFuture(
                                    new IOException(url + " httpcode = " + rs + ", but not found Localtion"));
                        }
                    }
                    byte[] result = resp.body();
                    if (rs == 200 || result != null) {
                        if (respHeaders != null) {
                            resp.headers().map().forEach((k, l) -> {
                                if (!l.isEmpty()) {
                                    respHeaders.put(k, l.get(0));
                                }
                            });
                        }
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        if (result != null) {
                            if ("gzip"
                                    .equalsIgnoreCase(resp.headers()
                                            .firstValue("content-encoding")
                                            .orElse(null))) {
                                try {
                                    GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(result));
                                    in.transferTo(out);
                                } catch (IOException e) {
                                    return CompletableFuture.failedFuture(e);
                                }
                            } else {
                                out.writeBytes(result);
                            }
                        }
                        return CompletableFuture.completedFuture(out);
                    }
                    return CompletableFuture.failedFuture(new RetcodeException(rs, url + " httpcode = " + rs));
                });
    }
    //
    //    public static ByteArrayOutputStream remoteHttpContent(SSLContext ctx, String method, String url, int
    // timeoutMs, Map<String, Serializable> headers, String body) throws IOException {
    //        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    //        boolean opening = true;
    //        try {
    //            conn.setConnectTimeout(timeoutMs > 0 ? timeoutMs : 30000);
    //            conn.setReadTimeout(timeoutMs > 0 ? timeoutMs : 30000);
    //            if (conn instanceof HttpsURLConnection) {
    //                HttpsURLConnection httpsconn = ((HttpsURLConnection) conn);
    //                httpsconn.setSSLSocketFactory((ctx == null ? DEFAULTSSL_CONTEXT : ctx).getSocketFactory());
    //                httpsconn.setHostnameVerifier(defaultVerifier);
    //            }
    //            conn.setRequestMethod(method);
    //            if (headers != null) {
    //                for (Map.Entry<String, String> en : headers.entrySet()) {
    //                    conn.setRequestProperty(en.getKey(), en.getValue());
    //                }
    //            }
    //            if (body != null && !body.isEmpty()) { //conn.getOutputStream()会将GET强制变成POST
    //                conn.setDoInput(true);
    //                conn.setDoOutput(true);
    //                conn.getOutputStream().write(body.getBytes(UTF_8));
    //            }
    //            conn.connect();
    //            int rs = conn.getResponseCode();
    //            if (rs == 301 || rs == 302) {
    //                String newurl = conn.getHeaderField("Location");
    //                conn.disconnect();
    //                opening = false;
    //                return remoteHttpContent(ctx, method, newurl, timeoutMs, headers, body);
    //            }
    //            InputStream in = (rs < 400 || rs == 404) && rs != 405 ? conn.getInputStream() : conn.getErrorStream();
    //            if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) in = new GZIPInputStream(in);
    //            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
    //            byte[] bytes = new byte[1024];
    //            int pos;
    //            while ((pos = in.read(bytes)) != -1) {
    //                out.write(bytes, 0, pos);
    //            }
    //            in.close();
    //            return out;
    //        } finally {
    //            if (opening) conn.disconnect();
    //        }
    //    }

    public static String read(InputStream in) throws IOException {
        return read(in, StandardCharsets.UTF_8, false);
    }

    public static String readThenClose(InputStream in) throws IOException {
        return read(in, StandardCharsets.UTF_8, true);
    }

    public static String read(InputStream in, String charsetName) throws IOException {
        return read(in, Charset.forName(charsetName), false);
    }

    public static String read(InputStream in, Charset charset) throws IOException {
        return read(in, charset, false);
    }

    private static String read(InputStream in, Charset charset, boolean close) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] bytes = new byte[1024];
        int pos;
        while ((pos = in.read(bytes)) != -1) {
            out.write(bytes, 0, pos);
        }
        if (close) {
            in.close();
        }
        return charset == null ? out.toString() : out.toString(charset);
    }

    public static ByteArrayOutputStream readStream(InputStream in) throws IOException {
        return readStream(in, false);
    }

    public static ByteArrayOutputStream readStreamThenClose(InputStream in) throws IOException {
        return readStream(in, true);
    }

    private static ByteArrayOutputStream readStream(InputStream in, boolean close) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] bytes = new byte[1024];
        int pos;
        while ((pos = in.read(bytes)) != -1) {
            out.write(bytes, 0, pos);
        }
        if (close) {
            in.close();
        }
        return out;
    }

    public static byte[] readBytes(File file) throws IOException {
        return readBytesThenClose(new FileInputStream(file));
    }

    public static byte[] readBytes(InputStream in) throws IOException {
        return readStream(in).toByteArray();
    }

    public static byte[] readBytesThenClose(InputStream in) throws IOException {
        return readStreamThenClose(in).toByteArray();
    }
}
