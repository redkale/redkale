/*
 *
 */
package org.redkale.source.spi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntFunction;
import org.redkale.annotation.Param;
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
import static org.redkale.asm.Opcodes.*;
import org.redkale.asm.Type;
import org.redkale.convert.json.JsonObject;
import org.redkale.persistence.Entity;
import org.redkale.persistence.Sql;
import org.redkale.source.AbstractDataSqlSource;
import org.redkale.source.DataNativeSqlInfo;
import static org.redkale.source.DataNativeSqlInfo.SqlMode.SELECT;
import org.redkale.source.DataNativeSqlParser;
import org.redkale.source.DataSqlMapper;
import org.redkale.source.DataSqlSource;
import org.redkale.source.EntityBuilder;
import org.redkale.source.RowBound;
import org.redkale.source.SourceException;
import org.redkale.util.RedkaleClassLoader;
import org.redkale.util.Sheet;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/**
 * DataSqlMapper工厂类
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.8.0
 */
public final class DataSqlMapperBuilder {

    private DataSqlMapperBuilder() {}

    public static <T, M extends DataSqlMapper<T>> M createMapper(
            DataNativeSqlParser nativeSqlParser, DataSqlSource source, Class<M> mapperType) {
        if (!mapperType.isInterface()) {
            throw new SourceException(mapperType + " is not interface");
        }
        final RedkaleClassLoader loader = RedkaleClassLoader.getRedkaleClassLoader();
        final Class entityType = entityType(mapperType);
        final String supDynName = mapperType.getName().replace('.', '/');
        final String newDynName = "org/redkaledyn/source/mapper/_DynDataSqlMapper_"
                + mapperType.getName().replace('.', '_').replace('$', '_');
        try {
            Class clz = RedkaleClassLoader.findDynClass(newDynName.replace('/', '.'));
            Class newClazz = clz == null ? loader.loadClass(newDynName.replace('/', '.')) : clz;
            M mapper = (M) newClazz.getDeclaredConstructor().newInstance();
            { // DataSqlSource
                Field c = newClazz.getDeclaredField("_source");
                c.setAccessible(true);
                c.set(mapper, source);
            }
            { // Entity Class
                Field c = newClazz.getDeclaredField("_type");
                c.setAccessible(true);
                c.set(mapper, entityType);
            }
            return mapper;
        } catch (ClassNotFoundException e) {
            // do nothing
        } catch (Throwable t) {
            t.printStackTrace();
        }
        EntityBuilder.load(entityType);
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
                    throw new SourceException(mapperType.getSimpleName() + "." + method.getName() + " require @"
                            + Sql.class.getSimpleName());
                }
                continue;
            }
            if (!Modifier.isAbstract(method.getModifiers())) {
                throw new SourceException(mapperType.getSimpleName() + "." + method.getName()
                        + " is not abstract, but contains @" + Sql.class.getSimpleName());
            }
            if (method.getExceptionTypes().length > 0) {
                throw new SourceException(
                        "@" + Sql.class.getSimpleName() + " cannot on throw-exception method, but " + method);
            }
            IntFunction<String> signFunc = null;
            if (source instanceof AbstractDataSqlSource) {
                signFunc = ((AbstractDataSqlSource) source).getSignFunc();
            }
            DataNativeSqlInfo sqlInfo = nativeSqlParser.parse(signFunc, source.getType(), sql.value());
            AsmMethodBean methodBean = selfMethodBeans.get(AsmMethodBoost.getMethodBeanKey(method));
            List<String> fieldNames = methodBean.paramNameList(method);
            Class resultClass = resultClass(method);
            int roundIndex = -1;
            if (resultClass.isAssignableFrom(Sheet.class)) {
                Class[] pts = method.getParameterTypes();
                for (int i = 0; i < pts.length; i++) {
                    if (RowBound.class.isAssignableFrom(pts[i])) {
                        roundIndex = i;
                        break;
                    }
                }
                if (roundIndex < 0) {
                    throw new SourceException(
                            mapperType.getSimpleName() + "." + method.getName() + " need RowBound type parameter on @"
                                    + Sql.class.getSimpleName() + "(" + sql.value() + ")");
                }
                fieldNames.remove(roundIndex);
            }
            if (!Utility.equalsElement(sqlInfo.getRootParamNames(), fieldNames)) {
                throw new SourceException(mapperType.getSimpleName() + "." + method.getName()
                        + " parameters not match, fieldNames = " + fieldNames + ", sqlParams = "
                        + sqlInfo.getRootParamNames() + ", methodBean = " + methodBean);
            }
            if (sqlInfo.getSqlMode() != SELECT) { // 非SELECT语句只能返回int或void
                if (resultClass != Integer.class && resultClass != int.class) {
                    throw new SourceException("Update SQL must on return int method, but " + method);
                }
            }
            items.add(new Item(method, sqlInfo, methodBean, roundIndex));
        }
        // ------------------------------------------------------------------------------

        final String utilClassName = Utility.class.getName().replace('.', '/');
        final String sheetDesc = Type.getDescriptor(Sheet.class);
        final String roundDesc = Type.getDescriptor(RowBound.class);
        final String entityDesc = Type.getDescriptor(entityType);
        final String sqlSourceName = DataSqlSource.class.getName().replace('.', '/');
        final String sqlSourceDesc = Type.getDescriptor(DataSqlSource.class);

        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES);
        FieldVisitor fv;
        MethodVisitor mv;

        cw.visit(V11, ACC_PUBLIC + ACC_SUPER, newDynName, null, "java/lang/Object", new String[] {supDynName});
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
            mv = cw.visitMethod(
                    ACC_PUBLIC, "entityType", "()Ljava/lang/Class;", "()Ljava/lang/Class<" + entityDesc + ">;", null);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, newDynName, "_type", "Ljava/lang/Class;");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        // sql系列方法
        // int nativeUpdate(String sql)
        // CompletableFuture<Integer> nativeUpdateAsync(String sql)
        // int nativeUpdate(String sql, Map<String, Object> params)
        // CompletableFuture<Integer> nativeUpdateAsync(String sql, Map<String, Object> params)
        //
        // V nativeQueryOne(Class<V> type, String sql)
        // CompletableFuture<V> nativeQueryOneAsync(Class<V> type, String sql)
        // V nativeQueryOne(Class<V> type, String sql, Map<String, Object> params)
        // CompletableFuture<V> nativeQueryOneAsync(Class<V> type, String sql, Map<String, Object> params)
        //
        // Map<K, V> nativeQueryMap(Class<K> keyType, Class<V> valType, String sql, Map<String, Object> params)
        // CompletableFuture<Map<K, V>> nativeQueryMapAsync(Class<K> keyType, Class<V> valType, String sql, Map<String,
        // Object> params)
        //
        // nativeQueryOne、nativeQueryList、nativeQuerySheet
        for (Item item : items) {
            Method method = item.method;
            DataNativeSqlInfo sqlInfo = item.sqlInfo;
            AsmMethodBean methodBean = item.methodBean;
            int roundIndex = item.roundIndex;
            Sql sql = method.getAnnotation(Sql.class);
            Class resultClass = resultClass(method);
            Class[] componentTypes = resultComponentType(method);
            final boolean async = method.getReturnType().isAssignableFrom(CompletableFuture.class);
            Parameter[] params = method.getParameters();
            Class[] paramTypes = method.getParameterTypes();
            List<AsmMethodParam> methodParams = methodBean.getParams();
            List<Integer> insns = new ArrayList<>();
            if (!EntityBuilder.isSimpleType(componentTypes[0])) {
                EntityBuilder.load(componentTypes[0]);
            }

            mv = new MethodDebugVisitor(cw.visitMethod(
                            ACC_PUBLIC, method.getName(), methodBean.getDesc(), methodBean.getSignature(), null))
                    .setDebug(false);
            Label l0 = new Label();
            mv.visitLabel(l0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, newDynName, "dataSource", "()" + sqlSourceDesc, false);
            if (sqlInfo.getSqlMode() == SELECT) {
                // 参数：结果类
                mv.visitLdcInsn(Type.getType(Type.getDescriptor(componentTypes[0])));
                if (resultClass.isAssignableFrom(Map.class)) {
                    mv.visitLdcInsn(Type.getType(Type.getDescriptor(componentTypes[1])));
                }
            }
            // 参数：sql
            mv.visitLdcInsn(sql.value());
            if (roundIndex >= 0) {
                mv.visitVarInsn(ALOAD, roundIndex + 1);
            }
            // 参数: params
            Asms.visitInsn(mv, paramTypes.length * 2 - (roundIndex >= 0 ? 2 : 0));
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            int insn = 0;
            for (int i = 0; i < paramTypes.length; i++) {
                insn++;
                if (i != roundIndex) {
                    Class pt = paramTypes[i];
                    // 参数名
                    mv.visitInsn(DUP);
                    Asms.visitInsn(mv, i * 2);
                    Param p = params[i].getAnnotation(Param.class);
                    String k = p == null ? methodParams.get(i).getName() : p.value();
                    mv.visitLdcInsn(k);
                    mv.visitInsn(AASTORE);
                    // 参数值
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
                    Asms.visitPrimitiveValueOf(mv, pt);
                    mv.visitInsn(AASTORE);
                }
                insns.add(insn);
            }

            mv.visitMethodInsn(INVOKESTATIC, utilClassName, "ofMap", "([Ljava/lang/Object;)Ljava/util/HashMap;", false);

            // One:   "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/lang/Object;"
            // Map:   "(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/util/Map;"
            // List:  "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/util/List;"
            // Sheet:
            // "(Ljava/lang/Class;Ljava/lang/String;Lorg/redkale/source/RowRound;Ljava/util/Map;)Lorg/redkale/util/Sheet;"
            // Async: "(Ljava/lang/Class;Ljava/lang/String;Ljava/util/Map;)Ljava/util/concurrent/CompletableFuture;"
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
                    queryMethodDesc = "(Ljava/lang/Class;Ljava/lang/String;" + roundDesc + "Ljava/util/Map;)"
                            + (async ? "Ljava/util/concurrent/CompletableFuture;" : sheetDesc);
                }
                mv.visitMethodInsn(
                        INVOKEINTERFACE,
                        sqlSourceName,
                        queryMethodName + (async ? "Async" : ""),
                        queryMethodDesc,
                        true);
                if (oneMode) {
                    mv.visitTypeInsn(CHECKCAST, componentTypes[0].getName().replace('.', '/'));
                }
                mv.visitInsn(ARETURN);
            } else {
                String updateMethodName = "nativeUpdate" + (async ? "Async" : "");
                String updateMethodDesc = "(Ljava/lang/String;Ljava/util/Map;)"
                        + (async ? "Ljava/util/concurrent/CompletableFuture;" : "I");
                mv.visitMethodInsn(INVOKEINTERFACE, sqlSourceName, updateMethodName, updateMethodDesc, true);
                if (resultClass == int.class) {
                    mv.visitInsn(IRETURN);
                } else if (!async && resultClass == Integer.class) {
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    mv.visitInsn(ARETURN);
                } else if (resultClass == void.class) {
                    mv.visitInsn(POP);
                    mv.visitInsn(RETURN);
                } else {
                    mv.visitInsn(ARETURN);
                }
            }
            Label l2 = new Label();
            mv.visitLabel(l2);
            mv.visitLocalVariable("this", "L" + newDynName + ";", null, l0, l2, 0);
            for (int i = 0; i < paramTypes.length; i++) {
                AsmMethodParam param = methodParams.get(i);
                mv.visitLocalVariable(
                        param.getName(),
                        param.description(paramTypes[i]),
                        param.signature(paramTypes[i]),
                        l0,
                        l2,
                        insns.get(i));
            }
            mv.visitMaxs(8, 5);
            mv.visitEnd();
        }

        cw.visitEnd();

        byte[] bytes = cw.toByteArray();
        Class<?> newClazz = loader.loadClass(newDynName.replace('/', '.'), bytes);
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
                Class<?> entityClass = TypeToken.typeToClass(((ParameterizedType) t).getActualTypeArguments()[0]);
                if (entityClass.getAnnotation(Entity.class) == null) {
                    throw new SourceException(
                            "Entity Class " + entityClass.getName() + " must be on Annotation @Entity");
                }
                return entityClass;
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
                return new Class[] {TypeToken.typeToClass(ts[0]), TypeToken.typeToClass(ts[1])};
            } else {
                return new Class[] {String.class, JsonObject.class};
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
        return new Class[] {clzz};
    }

    private static class Item {

        public Method method;

        public DataNativeSqlInfo sqlInfo;

        public AsmMethodBean methodBean;

        public int roundIndex = -1;

        public Item(Method method, DataNativeSqlInfo sqlInfo, AsmMethodBean methodBean, int roundIndex) {
            this.method = method;
            this.sqlInfo = sqlInfo;
            this.methodBean = methodBean;
            this.roundIndex = roundIndex;
        }
    }
}
