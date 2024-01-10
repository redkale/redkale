/*
 *
 */
package org.redkale.source.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import org.redkale.asm.AnnotationVisitor;
import org.redkale.asm.AsmMethodBean;
import org.redkale.asm.AsmMethodBoost;
import org.redkale.asm.AsmMethodParam;
import org.redkale.asm.Asms;
import org.redkale.asm.ClassWriter;
import static org.redkale.asm.ClassWriter.COMPUTE_FRAMES;
import org.redkale.asm.FieldVisitor;
import org.redkale.asm.Label;
import org.redkale.asm.MethodDebugVisitor;
import org.redkale.asm.MethodVisitor;
import static org.redkale.asm.Opcodes.AASTORE;
import static org.redkale.asm.Opcodes.ACC_PRIVATE;
import static org.redkale.asm.Opcodes.ACC_PUBLIC;
import static org.redkale.asm.Opcodes.ACC_SUPER;
import static org.redkale.asm.Opcodes.ALOAD;
import static org.redkale.asm.Opcodes.ANEWARRAY;
import static org.redkale.asm.Opcodes.ARETURN;
import static org.redkale.asm.Opcodes.CHECKCAST;
import static org.redkale.asm.Opcodes.DLOAD;
import static org.redkale.asm.Opcodes.DUP;
import static org.redkale.asm.Opcodes.FLOAD;
import static org.redkale.asm.Opcodes.GETFIELD;
import static org.redkale.asm.Opcodes.ILOAD;
import static org.redkale.asm.Opcodes.INVOKEINTERFACE;
import static org.redkale.asm.Opcodes.INVOKESPECIAL;
import static org.redkale.asm.Opcodes.INVOKESTATIC;
import static org.redkale.asm.Opcodes.INVOKEVIRTUAL;
import static org.redkale.asm.Opcodes.LLOAD;
import static org.redkale.asm.Opcodes.RETURN;
import static org.redkale.asm.Opcodes.V11;
import org.redkale.asm.Type;
import org.redkale.convert.json.JsonObject;
import org.redkale.persistence.Sql;
import org.redkale.source.AbstractDataSqlSource;
import org.redkale.source.DataNativeSqlInfo;
import static org.redkale.source.DataNativeSqlInfo.SqlMode.SELECT;
import org.redkale.source.DataNativeSqlParser;
import org.redkale.source.DataSqlMapper;
import org.redkale.source.DataSqlSource;
import org.redkale.source.Flipper;
import org.redkale.source.SourceException;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.Sheet;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/**
 * DataSqlMapper工厂类
 *
 * <p>
 * 详情见: https://redkale.org
 *
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public final class DataSqlMapperBuilder {

    private static Map<String, AsmMethodBean> baseMethodBeans;

    private DataSqlMapperBuilder() {
    }

    public static <T, M extends DataSqlMapper<T>> M createMapper(DataNativeSqlParser nativeSqlParser, DataSqlSource source, Class<M> mapperType) {
        if (!mapperType.isInterface()) {
            throw new SourceException(mapperType + " is not interface");
        }
        final ClassLoader loader = Thread.currentThread().getContextClassLoader();
        final Class entityType = entityType(mapperType);
        final String supDynName = mapperType.getName().replace('.', '/');
        final String newDynName = "org/redkaledyn/source/mapper/_DynDataSqlMapper__" + supDynName.replace('$', '_');
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            Class newClazz = clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz;
            M mapper = (M) newClazz.getDeclaredConstructor().newInstance();
            {
                Field c = newClazz.getDeclaredField("_source");
                c.setAccessible(true);
                c.set(mapper, source);
            }
            {
                Field c = newClazz.getDeclaredField("_type");
                c.setAccessible(true);
                c.set(mapper, entityType);
            }
            return mapper;
        } catch (ClassNotFoundException e) {
            //do nothing
        } catch (Throwable t) {
            t.printStackTrace();
        }

        if (baseMethodBeans == null) {
            baseMethodBeans = AsmMethodBoost.getMethodBeans(DataSqlMapper.class);
        }
        List<Item> items = new ArrayList<>();
        Map<String, AsmMethodBean> selfMethodBeans = AsmMethodBoost.getMethodBeans(mapperType);
        for (Method method : mapperType.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if ("dataSource".equals(method.getName()) && method.getParameterCount() == 0) {
                continue;
            }
            if ("entityType".equals(method.getName()) && method.getParameterCount() == 0) {
                continue;
            }
            Sql sql = method.getAnnotation(Sql.class);
            if (sql == null) {
                if (Modifier.isAbstract(method.getModifiers())) {
                    throw new SourceException(method + " require @" + Sql.class.getSimpleName());
                }
                continue;
            }
            if (!Modifier.isAbstract(method.getModifiers())) {
                throw new SourceException(method + " is not abstract, but contains @" + Sql.class.getSimpleName());
            }
            if (method.getExceptionTypes().length > 0) {
                throw new SourceException("@" + Sql.class.getSimpleName() + " cannot on throw-exception method, but " + method);
            }
            IntFunction<String> signFunc = null;
            if (source instanceof AbstractDataSqlSource) {
                signFunc = ((AbstractDataSqlSource) source).getSignFunc();
            }
            DataNativeSqlInfo sqlInfo = nativeSqlParser.parse(signFunc, source.getType(), sql.value());
            AsmMethodBean methodBean = selfMethodBeans.get(AsmMethodBoost.getMethodBeanKey(method));
            if (!Utility.equalsElement(sqlInfo.getRootParamNames(), methodBean.fieldNameList())) {
                throw new SourceException(method + " parameters not match @" + Sql.class.getSimpleName() + "(" + sql.value() + ")");
            }
            Class resultClass = resultClass(method);
            if (sqlInfo.getSqlMode() != SELECT) { //非SELECT语句只能返回int或void
                if (resultClass != Integer.class && resultClass != int.class
                    && resultClass != Void.class && resultClass != void.class) {
                    throw new SourceException("@" + Sql.class.getSimpleName()
                        + "(" + sql.value() + ") must on return int or void method, but " + method);
                }
            }
            items.add(new Item(method, sqlInfo, methodBean));
        }
        //------------------------------------------------------------------------------

        final String utilClassName = Utility.class.getName().replace('.', '/');
        final String sheetDesc = Type.getDescriptor(Sheet.class);
        final String flipperDesc = Type.getDescriptor(Flipper.class);
        final String entityDesc = Type.getDescriptor(entityType);
        final String sqlSourceName = DataSqlSource.class.getName().replace('.', '/');
        final String sqlSourceDesc = Type.getDescriptor(DataSqlSource.class);

        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodVisitor mv;
        AnnotationVisitor av0;

        cw.visit(V11, ACC_PUBLIC + ACC_SUPER, newDynName, null, "java/lang/Object", new String[]{supDynName});
        {
            fv = cw.visitField(ACC_PRIVATE, "_source", sqlSourceDesc, null, null);
            fv.visitEnd();
        }
        {
            fv = cw.visitField(ACC_PRIVATE, "_type", "Ljava/lang/Class;", null, null);
            fv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "dataSource", "()" + sqlSourceDesc, null, null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, "_source", sqlSourceDesc);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC, "entityType", "()Ljava/lang/Class;", "()Ljava/lang/Class<" + entityDesc + ">;", null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, "_type", "Ljava/lang/Class;");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        //sql系列方法
        //int nativeUpdate(String sql)
        //CompletableFuture<Integer> nativeUpdateAsync(String sql)
        //int nativeUpdate(String sql, Map<String, Object> params)
        //CompletableFuture<Integer> nativeUpdateAsync(String sql, Map<String, Object> params)
        //
        //V nativeQueryOne(Class<V> type, String sql)
        //CompletableFuture<V> nativeQueryOneAsync(Class<V> type, String sql)
        //V nativeQueryOne(Class<V> type, String sql, Map<String, Object> params)
        //CompletableFuture<V> nativeQueryOneAsync(Class<V> type, String sql, Map<String, Object> params)
        //
        //Map<K, V> nativeQueryMap(Class<K> keyType, Class<V> valType, String sql, Map<String, Object> params)
        //CompletableFuture<Map<K, V>> nativeQueryMapAsync(Class<K> keyType, Class<V> valType, String sql, Map<String, Object> params)
        //
        //nativeQueryOne、nativeQueryList、nativeQuerySheet
        for (Item item : items) {
            Method method = item.method;
            DataNativeSqlInfo sqlInfo = item.sqlInfo;
            AsmMethodBean methodBean = item.methodBean;
            Sql sql = method.getAnnotation(Sql.class);
            Class resultClass = resultClass(method);
            Class[] componentTypes = resultComponentType(method);
            final boolean async = method.getReturnType().isAssignableFrom(CompletableFuture.class);
            Class[] paramTypes = method.getParameterTypes();
            List<AsmMethodParam> methodParams = methodBean.getParams();
            List<Integer> insns = new ArrayList<>();

            mv = new MethodDebugVisitor(cw.visitMethod(ACC_PUBLIC, method.getName(), methodBean.getDesc(), methodBean.getSignature(), null)).setDebug(false);
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "dataSource", "()" + sqlSourceDesc, false);
            //参数：结果类
            mv.visitLdcInsn(Type.getType(Type.getDescriptor(componentTypes[0])));
            if (resultClass.isAssignableFrom(Map.class)) {
                mv.visitLdcInsn(Type.getType(Type.getDescriptor(componentTypes[1])));
            }
            //参数：sql
            mv.visitLdcInsn(sql.value());
            //参数: params
            Asms.visitInsn(mv, paramTypes.length * 2);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            int insn = 0;
            for (int i = 0; i < paramTypes.length; i++) {
                insn++;
                Class pt = paramTypes[i];
                //参数名
                mv.visitInsn(DUP);
                Asms.visitInsn(mv, i * 2);
                mv.visitLdcInsn(methodParams.get(i).getName());
                mv.visitInsn(AASTORE);
                //参数值
                mv.visitInsn(DUP);
                Asms.visitInsn(mv, i * 2 + 1);
                if (pt.isPrimitive()) {
                    if (pt == long.class) {
                        mv.visitVarInsn(LLOAD, insn++);
                    } else if (pt == float.class) {
                        mv.visitVarInsn(FLOAD, insn++);
                    } else if (pt == double.class) {
                        mv.visitVarInsn(DLOAD, insn++);
                    } else {
                        mv.visitVarInsn(ILOAD, insn);
                    }
                } else {
                    mv.visitVarInsn(ALOAD, insn);
                }
                insns.add(insn);
                Asms.visitPrimitiveValueOf(mv, pt);
                mv.visitInsn(AASTORE);
            }

            mv.visitMethodInsn(INVOKESTATIC, utilClassName, "ofMap", "([Ljava/lang/Object;)Ljava/util/HashMap;", false);

            //One:   "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/lang/Object;"
            //Map:   "(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/util/Map;"
            //List:  "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/util/List;"
            //Sheet: "(Ljava/lang/Class;Ljava/lang/String;Lorg/redkale/source/Flipper;Ljava/util/Map;)Lorg/redkale/util/Sheet;"
            //Async: "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/util/concurrent/CompletableFuture;"
            if (sqlInfo.getSqlMode() == SELECT) {
                String queryMethodName = "nativeQueryOne";
                String queryMethodDesc = "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)"
                    + (async ? "Ljava/util/concurrent/CompletableFuture;" : "Ljava/lang/Object;");
                boolean oneMode = !async;
                if (resultClass.isAssignableFrom(Map.class)) {
                    oneMode = false;
                    queryMethodName = "nativeQueryMap";
                    queryMethodDesc = "(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)"
                        + (async ? "Ljava/util/concurrent/CompletableFuture;" : "Ljava/util/Map;");
                } else if (resultClass.isAssignableFrom(List.class)) {
                    oneMode = false;
                    queryMethodName = "nativeQueryList";
                    queryMethodDesc = "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)"
                        + (async ? "Ljava/util/concurrent/CompletableFuture;" : "Ljava/util/List;");
                } else if (resultClass.isAssignableFrom(Sheet.class)) {
                    oneMode = false;
                    queryMethodName = "nativeQuerySheet";
                    queryMethodDesc = "(Ljava/lang/Class;Ljava/lang/String;" + flipperDesc + "Ljava/util/Map;)"
                        + (async ? "Ljava/util/concurrent/CompletableFuture;" : sheetDesc);
                }
                mv.visitMethodInsn(INVOKEINTERFACE, sqlSourceName, queryMethodName + (async ? "Async" : ""), queryMethodDesc, true);
                if (oneMode) {
                    mv.visitTypeInsn(CHECKCAST, componentTypes[0].getName().replace('.', '/'));
                }
            } else {
                //UPDATE
            }
            mv.visitInsn(ARETURN);
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLocalVariable("this", "L" + newDynName + ";", null, l0, l2, 0);
            for (int i = 0; i < paramTypes.length; i++) {
                AsmMethodParam param = methodParams.get(i);
                mv.visitLocalVariable(param.getName(), param.description(paramTypes[i]), param.signature(paramTypes[i]), l0, l2, insns.get(i));
            }
            mv.visitMaxs(8, 5);
            mv.visitEnd();
        }

        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = new ClassLoader(loader) {
            public final Class<?> loadClass(String name, byte[] b) {
                return defineClass(name, b, 0, b.length);
            }
        }.loadClass(newDynName.replace('/', '.'), bytes);
        RedkaleClassLoader.putDynClass(newDynName.replace('/', '.'), bytes, newClazz);
        RedkaleClassLoader.putReflectionPublicConstructors(newClazz, newDynName.replace('/', '.'));
        RedkaleClassLoader.putReflectionDeclaredConstructors(newClazz, newDynName.replace('/', '.'));
        try {
            M mapper = (M) newClazz.getDeclaredConstructor().newInstance();
            {
                Field c = newClazz.getDeclaredField("_source");
                c.setAccessible(true);
                c.set(mapper, source);
            }
            {
                Field c = newClazz.getDeclaredField("_type");
                c.setAccessible(true);
                c.set(mapper, entityType);
            }
            return mapper;
        } catch (Exception ex) {
            throw new SourceException(ex);
        }
    }

    private static Class entityType(Class mapperType) {
        for (java.lang.reflect.Type t : mapperType.getGenericInterfaces()) {
            if (DataSqlMapper.class.isAssignableFrom(TypeToken.typeToClass(t))) {
                return TypeToken.typeToClass(((ParameterizedType) t).getActualTypeArguments()[0]);
            }
        }
        throw new SourceException("Not found entity class from " + mapperType.getName());
    }

    private static Class resultClass(Method method) {
        Class type = method.getReturnType();
        if (type.isAssignableFrom(CompletableFuture.class)) {
            ParameterizedType pt = (ParameterizedType) method.getGenericReturnType();
            return TypeToken.typeToClass(pt.getActualTypeArguments()[0]);
        }
        return type;
    }

    private static Class[] resultComponentType(Method method) {
        if (method.getReturnType().isAssignableFrom(CompletableFuture.class)) {
            ParameterizedType pt = (ParameterizedType) method.getGenericReturnType();
            return resultComponentType(pt.getActualTypeArguments()[0]);
        }
        return resultComponentType(method.getGenericReturnType());
    }

    private static Class[] resultComponentType(java.lang.reflect.Type type) {
        Class clzz = TypeToken.typeToClass(type);
        if (clzz.isAssignableFrom(Map.class)) {
            if (type instanceof ParameterizedType) {
                java.lang.reflect.Type[] ts = ((ParameterizedType) type).getActualTypeArguments();
                return new Class[]{TypeToken.typeToClass(ts[0]), TypeToken.typeToClass(ts[1])};
            } else {
                return new Class[]{String.class, JsonObject.class};
            }
        } else if (clzz.isAssignableFrom(List.class)) {
            if (type instanceof ParameterizedType) {
                clzz = TypeToken.typeToClass(((ParameterizedType) type).getActualTypeArguments()[0]);
            } else {
                clzz = JsonObject.class;
            }
        } else if (clzz.isAssignableFrom(Sheet.class)) {
            if (type instanceof ParameterizedType) {
                clzz = TypeToken.typeToClass(((ParameterizedType) type).getActualTypeArguments()[0]);
            } else {
                clzz = JsonObject.class;
            }
        }
        return new Class[]{clzz};
    }

    private static class Item {

        public Method method;

        public DataNativeSqlInfo sqlInfo;

        public AsmMethodBean methodBean;

        public Item(Method method, DataNativeSqlInfo sqlInfo, AsmMethodBean methodBean) {
            this.method = method;
            this.sqlInfo = sqlInfo;
            this.methodBean = methodBean;
        }

    }
}
