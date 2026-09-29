/*
 *
 */
package org.redkale.util;

import static java.lang.classfile.ClassFile.*;
import static java.lang.constant.ConstantDescs.*;

import java.io.*;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.constant.*;
import java.lang.reflect.*;
import java.math.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import java.util.stream.Stream;

/** @author zhangjx */
class Inners {

    private Inners() {}

    static class CreatorInner {

        static final Logger logger = Logger.getLogger(Creator.class.getSimpleName());

        static final Map<Class, Creator> creatorCacheMap = new ConcurrentHashMap<>();

        static final Map<String, Creator> creatorCacheMap2 = new ConcurrentHashMap<>();

        static final Map<Class, IntFunction> arrayCacheMap = new ConcurrentHashMap<>();

        static final IntFunction<String[]> stringFuncArray = x -> new String[x];

        private CreatorInner() {}

        static {
            creatorCacheMap.put(Object.class, p -> new Object());
            creatorCacheMap.put(ArrayList.class, p -> new ArrayList<>());
            creatorCacheMap.put(HashMap.class, p -> new HashMap<>());
            creatorCacheMap.put(HashSet.class, p -> new HashSet<>());
            creatorCacheMap.put(LinkedHashSet.class, p -> new LinkedHashSet<>());
            creatorCacheMap.put(LinkedHashMap.class, p -> new LinkedHashMap<>());
            creatorCacheMap.put(Stream.class, p -> new ArrayList<>().stream());
            creatorCacheMap.put(ConcurrentHashMap.class, p -> new ConcurrentHashMap<>());
            creatorCacheMap.put(CompletableFuture.class, p -> new CompletableFuture<>());
            creatorCacheMap.put(CompletionStage.class, p -> new CompletableFuture<>());
            creatorCacheMap.put(Future.class, p -> new CompletableFuture<>());
            creatorCacheMap.put(AnyValueWriter.class, p -> new AnyValueWriter());
            creatorCacheMap.put(AnyValue.class, p -> new AnyValueWriter());
            creatorCacheMap.put(Map.Entry.class, new Creator<Map.Entry>() {
                @Override
                @org.redkale.annotation.ConstructorParameters({"key", "value"})
                public Map.Entry create(Object... params) {
                    return new AbstractMap.SimpleEntry(params[0], params[1]);
                }

                @Override
                public Class[] paramTypes() {
                    return new Class[] {Object.class, Object.class};
                }
            });
            creatorCacheMap.put(AbstractMap.SimpleEntry.class, new Creator<AbstractMap.SimpleEntry>() {
                @Override
                @org.redkale.annotation.ConstructorParameters({"key", "value"})
                public AbstractMap.SimpleEntry create(Object... params) {
                    return new AbstractMap.SimpleEntry(params[0], params[1]);
                }

                @Override
                public Class[] paramTypes() {
                    return new Class[] {Object.class, Object.class};
                }
            });

            arrayCacheMap.put(int.class, t -> new int[t]);
            arrayCacheMap.put(byte.class, t -> new byte[t]);
            arrayCacheMap.put(long.class, t -> new long[t]);
            arrayCacheMap.put(String.class, t -> new String[t]);
            arrayCacheMap.put(Object.class, t -> new Object[t]);
            arrayCacheMap.put(boolean.class, t -> new boolean[t]);
            arrayCacheMap.put(short.class, t -> new short[t]);
            arrayCacheMap.put(char.class, t -> new char[t]);
            arrayCacheMap.put(float.class, t -> new float[t]);
            arrayCacheMap.put(double.class, t -> new double[t]);
            arrayCacheMap.put(BigInteger.class, t -> new BigInteger[t]);
            arrayCacheMap.put(BigDecimal.class, t -> new BigDecimal[t]);
            arrayCacheMap.put(ByteBuffer.class, t -> new ByteBuffer[t]);
            arrayCacheMap.put(SocketAddress.class, t -> new SocketAddress[t]);
            arrayCacheMap.put(InetSocketAddress.class, t -> new InetSocketAddress[t]);
            arrayCacheMap.put(CompletableFuture.class, t -> new CompletableFuture[t]);
        }

        public static AbstractMap.SimpleEntry<String, Class>[] getConstructorField(
                Class clazz, int paramCount, String constructorDesc) {
            String n = clazz.getName();
            InputStream in = clazz.getResourceAsStream(n.substring(n.lastIndexOf('.') + 1) + ".class");
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
            byte[] bytes = new byte[1024];
            int pos;
            try {
                while ((pos = in.read(bytes)) != -1) {
                    out.write(bytes, 0, pos);
                }
                in.close();
            } catch (IOException io) {
                return null;
            }
            final List<String> fieldNames = new ArrayList<>();
            for (MethodModel method : ClassFile.of().parse(out.toByteArray()).methods()) {
                if (!method.methodName().equalsString("<init>")
                        || (constructorDesc != null && !method.methodType().equalsString(constructorDesc))) {
                    continue;
                }
                method.code()
                        .flatMap(code -> code.findAttribute(Attributes.localVariableTable()))
                        .ifPresent(table -> {
                            for (LocalVariableInfo variable : table.localVariables()) {
                                int slot = variable.slot();
                                if (slot < 1) {
                                    continue;
                                }
                                while (fieldNames.size() < slot) {
                                    fieldNames.add(" ");
                                }
                                fieldNames.set(slot - 1, variable.name().stringValue());
                            }
                        });
                break;
            }
            while (fieldNames.remove(" ")) {
                // 删掉空元素
            }
            if (fieldNames.isEmpty()) {
                return null;
            }
            if (paramCount == fieldNames.size()) {
                return getConstructorField(clazz, paramCount, fieldNames.toArray(new String[fieldNames.size()]));
            } else {
                String[] fs = new String[paramCount];
                for (int i = 0; i < fs.length; i++) {
                    fs[i] = fieldNames.get(i);
                }
                return getConstructorField(clazz, paramCount, fs);
            }
        }

        public static AbstractMap.SimpleEntry<String, Class>[] getConstructorField(
                Class clazz, int paramCount, String[] names) {
            AbstractMap.SimpleEntry<String, Class>[] se = new AbstractMap.SimpleEntry[names.length];
            for (int i = 0; i < names.length; i++) { // 查询参数名对应的Field
                try {
                    Field field = clazz.getDeclaredField(names[i]);
                    se[i] = new AbstractMap.SimpleEntry<>(field.getName(), field.getType());
                } catch (NoSuchFieldException fe) {
                    Class cz = clazz;
                    Field field = null;
                    while ((cz = cz.getSuperclass()) != Object.class) {
                        try {
                            field = cz.getDeclaredField(names[i]);
                            break;
                        } catch (NoSuchFieldException nsfe) {
                            // do nothing
                        }
                    }
                    if (field == null) {
                        return null;
                    }
                    se[i] = new AbstractMap.SimpleEntry<>(field.getName(), field.getType());
                } catch (Exception e) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE, clazz + " getConstructorField error", e);
                    }
                    return null;
                }
            }
            return se;
        }

        public static AbstractMap.SimpleEntry<String, Class>[] getConstructorField(
                Class clazz, int paramCount, Parameter[] params) {
            AbstractMap.SimpleEntry<String, Class>[] se = new AbstractMap.SimpleEntry[params.length];
            for (int i = 0; i < params.length; i++) { // 查询参数名对应的Field
                try {
                    Field field = clazz.getDeclaredField(params[i].getName());
                    se[i] = new AbstractMap.SimpleEntry<>(field.getName(), field.getType());
                } catch (Exception e) {
                    return null;
                }
            }
            return se;
        }

        public static <T> IntFunction<T[]> createArrayFunction(final Class<T> clazz) {
            if (Utility.inNativeImage()) {
                return t -> (T[]) Array.newInstance(clazz, t);
            }

            final ClassDesc componentDesc = ClassDesc.ofDescriptor(clazz.descriptorString());
            final RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
            final String newDynName = "org/redkaledyn/creator/_DynArrayFunction__"
                    + clazz.getName()
                            .replace('.', '_')
                            .replace('$', '_')
                            .replace('[', '_')
                            .replace(';', '_');
            try {
                return (IntFunction) classLoader
                        .loadClass(newDynName.replace('/', '.'))
                        .getDeclaredConstructor()
                        .newInstance();
            } catch (Throwable ex) {
                // do nothing
            }

            // -------------------------------------------------------------
            final ClassDesc dynDesc = ClassDesc.ofInternalName(newDynName);
            final ClassDesc arrayDesc = componentDesc.arrayType();
            final MethodTypeDesc applyDesc = MethodTypeDesc.of(arrayDesc, CD_int);
            final byte[] bytes = ClassFile.of().build(dynDesc, cb -> {
                cb.withVersion(JAVA_11_VERSION, 0)
                        .withFlags(ACC_PUBLIC | ACC_FINAL | ACC_SUPER)
                        .withSuperclass(CD_Object)
                        .withInterfaceSymbols(ClassDesc.of("java.util.function.IntFunction"));
                cb.with(SignatureAttribute.of(ClassSignature.parseFrom(
                        "Ljava/lang/Object;Ljava/util/function/IntFunction<" + arrayDesc.descriptorString() + ">;")));
                cb.withMethodBody(
                        "<init>",
                        MethodTypeDesc.of(CD_void),
                        ACC_PUBLIC,
                        code -> code.aload(0)
                                .invokespecial(CD_Object, "<init>", MethodTypeDesc.of(CD_void))
                                .return_());
                cb.withMethodBody(
                        "apply",
                        applyDesc,
                        ACC_PUBLIC,
                        code -> code.iload(1).anewarray(componentDesc).areturn());
                cb.withMethodBody(
                        "apply",
                        MethodTypeDesc.of(CD_Object, CD_int),
                        ACC_PUBLIC | ACC_BRIDGE | ACC_SYNTHETIC,
                        code -> code.aload(0)
                                .iload(1)
                                .invokevirtual(dynDesc, "apply", applyDesc)
                                .areturn());
            });
            try {
                Class<?> resultClazz = classLoader.loadClass(newDynName.replace('/', '.'), bytes);
                RedkaleClassLoader.putReflectionDeclaredConstructors(resultClazz, newDynName.replace('/', '.'));
                return (IntFunction<T[]>) resultClazz.getDeclaredConstructor().newInstance();
            } catch (Throwable ex) {
                // ex.printStackTrace();  //一般不会发生, native-image在没有预编译情况下会报错
                return t -> (T[]) Array.newInstance(clazz, t);
            }
        }
    }

    static class CopierInner {

        static final ConcurrentHashMap<Integer, ConcurrentHashMap<Class, Copier>> copierOneCaches =
                new ConcurrentHashMap();

        static final ConcurrentHashMap<Integer, ConcurrentHashMap<Class, ConcurrentHashMap<Class, Copier>>>
                copierTwoCaches = new ConcurrentHashMap();

        static final ConcurrentHashMap<Integer, ConcurrentHashMap<Class, Function>> copierFuncOneCaches =
                new ConcurrentHashMap();

        static final ConcurrentHashMap<Integer, ConcurrentHashMap<Class, ConcurrentHashMap<Class, Function>>>
                copierFuncTwoCaches = new ConcurrentHashMap();

        static final ConcurrentHashMap<Class, ConcurrentHashMap<Integer, ConcurrentHashMap<Class, Function>>>
                copierFuncListOneCaches = new ConcurrentHashMap();

        static final ConcurrentHashMap<
                        Class, ConcurrentHashMap<Integer, ConcurrentHashMap<Class, ConcurrentHashMap<Class, Function>>>>
                copierFuncListTwoCaches = new ConcurrentHashMap();

        private CopierInner() {}

        public static void clearCopierCache() {
            copierOneCaches.clear();
            copierTwoCaches.clear();
            copierFuncOneCaches.clear();
            copierFuncTwoCaches.clear();
            copierFuncListOneCaches.clear();
            copierFuncListTwoCaches.clear();
        }
    }

    static class InvokerInner {

        static final ConcurrentHashMap<Class, ConcurrentHashMap<Method, Invoker>> invokerCaches =
                new ConcurrentHashMap();

        private InvokerInner() {}
    }
}
