/*
 *
 */
package org.redkale.source;

import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.redkale.annotation.Nullable;
import org.redkale.convert.ConvertDisabled;
import org.redkale.persistence.*;
import org.redkale.util.*;

/**
 * 可以是实体类，也可以是查询结果的JavaBean类
 *
 * @author zhangjx
 * @param <T> T
 * @since 2.8.0
 */
public class EntityBuilder<T> {

    private static final ConcurrentHashMap<Class, EntityBuilder> cacheMap = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, String> lowerMap = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, String> snakeMap = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, String> camelMap = new ConcurrentHashMap<>();

    // 实体或者JavaBean类名
    private final Class<T> type;

    // 是否Map类型的虚拟实体类
    private final boolean entityIsMap;

    // Entity构建器
    private final Creator<T> creator;

    // Entity构建器参数
    @Nullable
    private final String[] constructorParameters;

    // key：类字段名， value：数据库字段名
    // 只有field.name 与 Column.name不同才存放在aliasmap里.
    @Nullable
    private final Map<String, String> aliasMap;

    // Entity构建器参数Attribute， 数组个数与constructorParameters相同
    @Nullable
    private final Attribute<T, Serializable>[] constructorAttributes;

    // Entity构建器参数Attribute
    @Nullable
    private final Attribute<T, Serializable>[] unconstructorAttributes;

    // key：类字段名
    final Map<String, Attribute<T, Serializable>> attributeMap;

    // key：数据库字段名
    private final Map<String, Attribute<T, Serializable>> sqlAttrMap;

    // key：数据库字段名去掉下划线并小写
    private final Map<String, Attribute<T, Serializable>> sqlLowerAttrMap;

    // 数据库中所有字段, 顺序必须与querySqlColumns、querySqlColumnSequence一致
    private final Attribute<T, Serializable>[] attributes;

    // 创建全字段对应对象的函数
    private final EntityFullFunc<T> fullFunc;

    EntityBuilder(
            Class<T> type,
            Creator<T> creator,
            Map<String, String> aliasMap,
            String[] constructorParameters,
            Attribute<T, Serializable>[] constructorAttributes,
            Attribute<T, Serializable>[] unconstructorAttributes,
            Map<String, Attribute<T, Serializable>> attributeMap,
            Attribute<T, Serializable>[] queryAttributes) {
        this.type = type;
        this.creator = creator;
        this.aliasMap = aliasMap;
        this.constructorParameters = constructorParameters;
        this.constructorAttributes = constructorAttributes;
        this.unconstructorAttributes = unconstructorAttributes;
        this.attributeMap = attributeMap;
        this.attributes = queryAttributes;
        this.sqlAttrMap = new HashMap<>();
        this.sqlLowerAttrMap = new HashMap<>();
        this.entityIsMap = Map.class.isAssignableFrom(type);
        attributeMap.forEach((k, v) -> {
            String col = getSQLColumn(null, k);
            sqlAttrMap.put(col, v);
            sqlLowerAttrMap.put(lowerCaseColumn(col), v);
        });
        if (constructorAttributes == null && !entityIsMap) {
            this.fullFunc = EntityFullFunc.create(type, creator, attributes);
        } else {
            this.fullFunc = null;
        }
    }

    public static boolean isSimpleType(Class type) {
        return type == byte[].class
                || type == String.class
                || type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || (!Map.class.isAssignableFrom(type) && type.getName().startsWith("java."));
    }

    public static <T> EntityBuilder<T> load(Class<T> type) {
        return cacheMap.computeIfAbsent(type, EntityBuilder::create);
    }

    private static <T> EntityBuilder<T> create(Class<T> type) {
        Creator<T> creator = Creator.create(type);
        String[] constructorParameters = null;
        if (!Map.class.isAssignableFrom(type)) {
            try {
                Method cm = creator.getClass().getMethod("create", Object[].class);
                RedkaleClassLoader.putReflectionPublicMethods(creator.getClass().getName());
                RedkaleClassLoader.putReflectionMethod(creator.getClass().getName(), cm);
                org.redkale.annotation.ConstructorParameters cp =
                        cm.getAnnotation(org.redkale.annotation.ConstructorParameters.class);
                if (cp != null && cp.value().length > 0) {
                    constructorParameters = cp.value();
                } else {
                    org.redkale.util.ConstructorParameters cp2 =
                            cm.getAnnotation(org.redkale.util.ConstructorParameters.class);
                    if (cp2 != null && cp2.value().length > 0) {
                        constructorParameters = cp2.value();
                    }
                }
            } catch (Exception e) {
                throw new SourceException(type + " cannot find "
                        + org.redkale.annotation.ConstructorParameters.class.getSimpleName() + " Creator");
            }
        }
        Class cltmp = type;
        Map<String, String> aliasmap = null;
        Set<String> fields = new HashSet<>();
        List<Attribute<T, Serializable>> queryAttrs = new ArrayList<>();
        HashMap<String, Attribute<T, Serializable>> attributeMap = new HashMap<>();
        if (!Map.class.isAssignableFrom(type)) {
            do {
                RedkaleClassLoader.putReflectionDeclaredFields(cltmp.getName());
                for (Field field : cltmp.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    if (Modifier.isFinal(field.getModifiers())) {
                        continue;
                    }
                    if (field.getAnnotation(Transient.class) != null) {
                        continue;
                    }
                    if (field.getAnnotation(javax.persistence.Transient.class) != null) {
                        continue;
                    }
                    if (fields.contains(field.getName())) {
                        continue;
                    }
                    final String fieldName = field.getName();
                    final Column col = field.getAnnotation(Column.class);
                    final String sqlField = col == null || col.name().isEmpty() ? fieldName : col.name();
                    if (!fieldName.equals(sqlField)) {
                        if (aliasmap == null) {
                            aliasmap = new HashMap<>();
                        }
                        aliasmap.put(fieldName, sqlField);
                    }
                    Attribute attr;
                    try {
                        attr = Attribute.create(type, cltmp, field);
                    } catch (RuntimeException e) {
                        continue;
                    }
                    RedkaleClassLoader.putReflectionField(cltmp.getName(), field);
                    queryAttrs.add(attr);
                    fields.add(fieldName);
                    attributeMap.put(fieldName, attr);
                }
            } while ((cltmp = cltmp.getSuperclass()) != Object.class);
        }
        Attribute<T, Serializable>[] constructorAttributes;
        Attribute<T, Serializable>[] unconstructorAttributes;
        if (constructorParameters == null) {
            constructorAttributes = null;
            unconstructorAttributes = null;
        } else {
            constructorAttributes = new Attribute[constructorParameters.length];
            List<Attribute<T, Serializable>> unconstructorAttrs = new ArrayList<>();
            List<String> newQueryCols1 = new ArrayList<>();
            List<String> newQueryCols2 = new ArrayList<>();
            for (Attribute<T, Serializable> attr : new ArrayList<>(queryAttrs)) {
                int pos = -1;
                for (int i = 0; i < constructorParameters.length; i++) {
                    if (attr.field().equals(constructorParameters[i])) {
                        pos = i;
                        break;
                    }
                }
                if (pos >= 0) {
                    constructorAttributes[pos] = attr;
                } else {
                    unconstructorAttrs.add(attr);
                }
            }
            unconstructorAttributes = unconstructorAttrs.toArray(new Attribute[unconstructorAttrs.size()]);
            newQueryCols1.addAll(newQueryCols2);
            List<Attribute<T, Serializable>> newQueryAttrs = new ArrayList<>();
            newQueryAttrs.addAll(List.of(constructorAttributes));
            newQueryAttrs.addAll(unconstructorAttrs);
            queryAttrs = newQueryAttrs;
        }
        return new EntityBuilder<>(
                type,
                creator,
                aliasmap,
                constructorParameters,
                constructorAttributes,
                unconstructorAttributes,
                attributeMap,
                queryAttrs.toArray(new Attribute[queryAttrs.size()]));
    }

    /**
     * 将数据ResultSet转成对象集合
     *
     * @param <T> 泛型
     * @param type 实体类或JavaBean
     * @param rset 数据ResultSet
     * @return 对象集合
     */
    public static <T> List<T> getListValue(Class<T> type, final DataResultSet rset) {
        if (type == byte[].class
                || type == String.class
                || type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || (!Map.class.isAssignableFrom(type) && type.getName().startsWith("java."))) {
            List<T> list = new ArrayList<>();
            while (rset.next()) {
                list.add(rset.wasNull() ? null : (T) DataResultSet.formatColumnValue(type, rset.getObject(1)));
            }
            return list;
        }
        return EntityBuilder.load(type).getObjectList(rset);
    }

    /**
     * 将数据ResultSet转成单个对象
     *
     * @param <T> 泛型
     * @param type 实体类或JavaBean
     * @param rset 数据ResultSet
     * @return 单个对象
     */
    public static <T> T getOneValue(Class<T> type, final DataResultSet rset) {
        if (!rset.next()) {
            return null;
        }
        if (type == byte[].class
                || type == String.class
                || type.isPrimitive()
                || Number.class.isAssignableFrom(type)
                || (!Map.class.isAssignableFrom(type) && type.getName().startsWith("java."))) {
            return (T) DataResultSet.formatColumnValue(type, rset.getObject(1));
        }
        return EntityBuilder.load(type).getObjectValue(rset);
    }

    public List<T> getObjectList(final DataResultSet rset) {
        List<T> list = new ArrayList<>();
        List<String> sqlColumns = rset.getColumnLabels();
        while (rset.next()) {
            list.add(getObjectValue(sqlColumns, rset));
        }
        return list;
    }

    public T getObjectValue(final DataResultSetRow row) {
        return getObjectValue(null, row);
    }

    // 去掉字段名中的下划线并转出小写
    protected String lowerCaseColumn(String sqlCol) {
        return lowerMap.computeIfAbsent(sqlCol, col -> {
            char ch;
            char[] chs = col.toCharArray();
            StringBuilder sb = new StringBuilder(chs.length);
            for (int i = 0; i < chs.length; i++) {
                ch = chs[i];
                if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                    sb.append(ch);
                } else if (ch >= 'A' && ch <= 'Z') {
                    sb.append(Character.toLowerCase(ch));
                }
            }
            return sb.toString();
        });
    }

    // 下划线式字段名替换成驼峰式
    protected String snakeCaseColumn(String sqlCol) {
        return snakeMap.computeIfAbsent(sqlCol, col -> {
            char ch;
            char[] chs = col.toCharArray();
            StringBuilder sb = new StringBuilder(chs.length - 1);
            for (int i = 0; i < chs.length; i++) {
                ch = chs[i];
                if (ch != '_') {
                    sb.append(i > 0 && chs[i - 1] == '_' ? Character.toUpperCase(ch) : ch);
                }
            }
            return sb.toString();
        });
    }

    // 驼峰式字段名替换成下划线式
    protected String camelCaseColumn(String column) {
        return camelMap.computeIfAbsent(column, EntityColumn::camelCase);
    }

    protected T getObjectValue(List<String> sqlColumns, final DataResultSetRow row) {
        if (row.wasNull()) {
            return null;
        }
        T obj;
        if (sqlColumns == null) {
            sqlColumns = row.getColumnLabels();
        }
        if (entityIsMap) {
            final Map map = (Map) creator.create();
            obj = (T) map;
            for (String sqlCol : sqlColumns) {
                map.put(sqlCol, getFieldValue(row, sqlCol));
            }
            return obj;
        }
        Map<String, Attribute<T, Serializable>> attrs = this.sqlAttrMap;
        if (this.constructorParameters == null) {
            obj = creator.create();
            for (String sqlCol : sqlColumns) {
                Attribute<T, Serializable> attr = attrs.get(sqlCol);
                boolean sqlFlag = false;
                if (attr == null && sqlCol.indexOf('_') > -1) {
                    attr = attrs.get(snakeCaseColumn(sqlCol));
                    sqlFlag = true;
                }
                if (attr == null) {
                    attr = sqlLowerAttrMap.get(lowerCaseColumn(sqlCol));
                    sqlFlag = true;
                }
                if (attr != null) { // 兼容返回的字段不存在类中
                    if (sqlFlag) {
                        attr.set(obj, getFieldValue(row, sqlCol));
                    } else {
                        attr.set(obj, getFieldValue(row, attr, 0));
                    }
                }
            }
        } else {
            Object[] cps = new Object[this.constructorParameters.length];
            for (int i = 0; i < this.constructorAttributes.length; i++) {
                Attribute<T, Serializable> attr = this.constructorAttributes[i];
                String sqlCol = getSQLColumn(null, attr.field());
                if (sqlColumns.contains(sqlCol)) {
                    cps[i] = getFieldValue(row, attr, 0);
                } else {
                    sqlCol = camelCaseColumn(sqlCol);
                    if (sqlColumns.contains(sqlCol)) {
                        cps[i] = getFieldValue(row, attr, 0);
                    }
                }
            }
            obj = creator.create(cps);
            for (Attribute<T, Serializable> attr : this.unconstructorAttributes) {
                String sqlCol = getSQLColumn(null, attr.field());
                if (sqlColumns.contains(sqlCol)) {
                    attr.set(obj, getFieldValue(row, attr, 0));
                } else {
                    sqlCol = camelCaseColumn(sqlCol);
                    if (sqlColumns.contains(sqlCol)) {
                        attr.set(obj, getFieldValue(row, attr, 0));
                    }
                }
            }
        }
        return obj;
    }

    public List<T> getEntityList(final SelectColumn sels, final DataResultSet rset) {
        List<T> list = new ArrayList<>();
        while (rset.next()) {
            list.add(getEntityValue(sels, rset));
        }
        return list;
    }

    /**
     * 将一行的ResultSet组装成一个Entity对象
     *
     * @param sels 指定字段
     * @param row ResultSet
     * @return Entity对象
     */
    public T getEntityValue(final SelectColumn sels, final DataResultSetRow row) {
        if (sels == null) {
            return getFullEntityValue(row);
        }
        if (row.wasNull()) {
            return null;
        }
        if (entityIsMap) {
            final Map map = (Map) creator.create();
            for (String sqlCol : row.getColumnLabels()) {
                map.put(sqlCol, getFieldValue(row, sqlCol));
            }
            return (T) map;
        }
        T obj;
        Attribute<T, Serializable>[] attrs = this.attributes;
        if (this.constructorParameters == null) {
            obj = creator.create();
        } else {
            Object[] cps = new Object[this.constructorParameters.length];
            for (int i = 0; i < this.constructorAttributes.length; i++) {
                Attribute<T, Serializable> attr = this.constructorAttributes[i];
                if (sels.test(attr.field())) {
                    cps[i] = getFieldValue(row, attr, 0);
                }
            }
            obj = creator.create(cps);
            attrs = this.unconstructorAttributes;
        }
        for (Attribute<T, Serializable> attr : attrs) {
            if (sels.test(attr.field())) {
                attr.set(obj, getFieldValue(row, attr, 0));
            }
        }
        return obj;
    }

    public List<T> getFullEntityList(final DataResultSet rset) {
        List<T> list = new ArrayList<>();
        while (rset.next()) {
            list.add(getFullEntityValue(rset));
        }
        return list;
    }

    public T getFullEntityValue(final DataResultSetRow row) {
        if (this.fullFunc != null) {
            return this.fullFunc.getObject(row);
        }
        return getEntityValue(
                constructorAttributes, constructorAttributes == null ? attributes : unconstructorAttributes, row);
    }

    public T getFullEntityValue(final Serializable... values) {
        return getEntityValue(
                constructorAttributes, constructorAttributes == null ? attributes : unconstructorAttributes, values);
    }

    /**
     * 将一行的ResultSet组装成一个Entity对象
     *
     * @param constructorAttrs 构建函数的Attribute数组, 大小必须与this.constructorAttributes相同
     * @param unconstructorAttrs 非构建函数的Attribute数组
     * @param row ResultSet
     * @return Entity对象
     */
    protected T getEntityValue(
            final Attribute<T, Serializable>[] constructorAttrs,
            final Attribute<T, Serializable>[] unconstructorAttrs,
            final DataResultSetRow row) {
        if (row.wasNull()) {
            return null;
        }
        if (entityIsMap) {
            final Map map = (Map) creator.create();
            for (String sqlCol : row.getColumnLabels()) {
                map.put(sqlCol, getFieldValue(row, sqlCol));
            }
            return (T) map;
        }
        T obj;
        int columnIndex = 0;
        if (this.constructorParameters == null) {
            obj = creator.create();
        } else {
            Object[] cps = new Object[this.constructorParameters.length];
            for (int i = 0; i < constructorAttrs.length; i++) {
                Attribute<T, Serializable> attr = constructorAttrs[i];
                if (attr != null) {
                    cps[i] = getFieldValue(row, attr, ++columnIndex);
                }
            }
            obj = creator.create(cps);
        }
        if (unconstructorAttrs != null) {
            for (Attribute<T, Serializable> attr : unconstructorAttrs) {
                if (attr != null) {
                    attr.set(obj, getFieldValue(row, attr, ++columnIndex));
                }
            }
        }
        return obj;
    }

    /**
     * 将一行的ResultSet组装成一个Entity对象
     *
     * @param constructorAttrs 构建函数的Attribute数组, 大小必须与this.constructorAttributes相同
     * @param unconstructorAttrs 非构建函数的Attribute数组
     * @param values 字段值集合
     * @return Entity对象
     */
    protected T getEntityValue(
            final Attribute<T, Serializable>[] constructorAttrs,
            final Attribute<T, Serializable>[] unconstructorAttrs,
            final Serializable... values) {
        if (values == null) {
            return null;
        }
        T obj;
        int index = -1;
        if (this.constructorParameters == null) {
            obj = creator.create();
        } else {
            Object[] cps = new Object[this.constructorParameters.length];
            for (int i = 0; i < constructorAttrs.length; i++) {
                Attribute<T, Serializable> attr = constructorAttrs[i];
                if (attr != null) {
                    cps[i] = values[++index];
                }
            }
            obj = creator.create(cps);
        }
        if (unconstructorAttrs != null) {
            for (Attribute<T, Serializable> attr : unconstructorAttrs) {
                if (attr != null) {
                    attr.set(obj, values[++index]);
                }
            }
        }
        return obj;
    }

    protected Serializable getFieldValue(final DataResultSetRow row, String sqlColumn) {
        return (Serializable) row.getObject(sqlColumn);
    }

    protected Serializable getFieldValue(DataResultSetRow row, Attribute<T, Serializable> attr, int columnIndex) {
        return row.getObject(attr, columnIndex, columnIndex > 0 ? null : this.getSQLColumn(null, attr.field()));
    }

    /**
     * 根据field字段名获取数据库对应的字段名
     *
     * @param tabAlis 表别名
     * @param fieldName 字段名
     * @return String
     */
    public String getSQLColumn(@Nullable String tabAlis, String fieldName) {
        return this.aliasMap == null
                ? (tabAlis == null ? fieldName : (tabAlis + '.' + fieldName))
                : (tabAlis == null
                        ? aliasMap.getOrDefault(fieldName, fieldName)
                        : (tabAlis + '.' + aliasMap.getOrDefault(fieldName, fieldName)));
    }

    public boolean hasConstructorAttribute() {
        return constructorAttributes != null;
    }

    public EntityFullFunc<T> getFullFunc() {
        return fullFunc;
    }

    /**
     * 判断Entity类的字段名与表字段名s是否存在不一致的值
     *
     * @return boolean
     */
    @ConvertDisabled
    public boolean isNoAlias() {
        return this.aliasMap == null;
    }

    @ConvertDisabled
    public Creator<T> getCreator() {
        return creator;
    }

    @ConvertDisabled
    public Class<T> getType() {
        return type;
    }
}
