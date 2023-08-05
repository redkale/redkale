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
import org.redkale.source.EntityInfo.DataResultSetRow;
import org.redkale.util.*;

/**
 * 可以是实体类，也可以是查询结果的JavaBean类
 *
 * @author zhangjx
 * @since 2.8.0
 */
public class EntityBuilder<T> {

    private static final ConcurrentHashMap<Class, EntityBuilder> cacheMap = new ConcurrentHashMap<>();

    //实体类名
    private final Class<T> type;

    //是否Map类型的虚拟实体类
    private final boolean entityIsMap;

    //Entity构建器
    private final Creator<T> creator;

    //Entity构建器参数
    @Nullable
    private final String[] constructorParameters;

    //key：类字段名， value：数据库字段名
    //只有field.name 与 Column.name不同才存放在aliasmap里.
    @Nullable
    private final Map<String, String> aliasmap;

    //Entity构建器参数Attribute， 数组个数与constructorParameters相同
    @Nullable
    private final Attribute<T, Serializable>[] constructorAttributes;

    //Entity构建器参数Attribute
    @Nullable
    private final Attribute<T, Serializable>[] unconstructorAttributes;

    //key：类字段名
    private final Map<String, Attribute<T, Serializable>> attributeMap;

    //key：数据库字段名
    private final Map<String, Attribute<T, Serializable>> sqlAttrMap;

    //数据库中所有字段, 顺序必须与querySqlColumns、querySqlColumnSequence一致
    private final Attribute<T, Serializable>[] attributes;

    EntityBuilder(Class<T> type, Creator<T> creator,
        Map<String, String> aliasmap, String[] constructorParameters,
        Attribute<T, Serializable>[] constructorAttributes,
        Attribute<T, Serializable>[] unconstructorAttributes,
        Map<String, Attribute<T, Serializable>> attributeMap,
        Attribute<T, Serializable>[] queryAttributes) {
        this.type = type;
        this.creator = creator;
        this.aliasmap = aliasmap;
        this.constructorParameters = constructorParameters;
        this.constructorAttributes = constructorAttributes;
        this.unconstructorAttributes = unconstructorAttributes;
        this.attributeMap = attributeMap;
        this.attributes = queryAttributes;
        this.sqlAttrMap = new HashMap<>();
        this.entityIsMap = Map.class.isAssignableFrom(type);
        attributeMap.forEach((k, v) -> sqlAttrMap.put(getSQLColumn(null, k), v));
    }

    public static <T> EntityBuilder<T> load(Class<T> type) {
        return cacheMap.computeIfAbsent(type, t -> create(t));
    }

    private static <T> EntityBuilder<T> create(Class<T> type) {
        Creator<T> creator = Creator.create(type);
        String[] constructorParameters = null;
        if (!Map.class.isAssignableFrom(type)) {
            try {
                Method cm = creator.getClass().getMethod("create", Object[].class);
                RedkaleClassLoader.putReflectionPublicMethods(creator.getClass().getName());
                RedkaleClassLoader.putReflectionMethod(creator.getClass().getName(), cm);
                org.redkale.annotation.ConstructorParameters cp = cm.getAnnotation(org.redkale.annotation.ConstructorParameters.class);
                if (cp != null && cp.value().length > 0) {
                    constructorParameters = cp.value();
                } else {
                    org.redkale.util.ConstructorParameters cp2 = cm.getAnnotation(org.redkale.util.ConstructorParameters.class);
                    if (cp2 != null && cp2.value().length > 0) {
                        constructorParameters = cp2.value();
                    }
                }
            } catch (Exception e) {
                throw new SourceException(type + " cannot find ConstructorParameters Creator");
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
        return new EntityBuilder<>(type, creator, aliasmap, constructorParameters, constructorAttributes,
            unconstructorAttributes, attributeMap, queryAttrs.toArray(new Attribute[queryAttrs.size()]));
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
                if (attr != null) { //兼容返回的字段不存在类中
                    attr.set(obj, getFieldValue(attr, row, 0));
                }
            }
        } else {
            Object[] cps = new Object[this.constructorParameters.length];
            for (int i = 0; i < this.constructorAttributes.length; i++) {
                Attribute<T, Serializable> attr = this.constructorAttributes[i];
                String sqlCol = getSQLColumn(null, attr.field());
                if (sqlColumns.contains(sqlCol)) {
                    cps[i] = getFieldValue(attr, row, 0);
                }
            }
            obj = creator.create(cps);
            for (Attribute<T, Serializable> attr : this.unconstructorAttributes) {
                String sqlCol = getSQLColumn(null, attr.field());
                if (sqlColumns.contains(sqlCol)) {
                    attr.set(obj, getFieldValue(attr, row, 0));
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
     * @param row  ResultSet
     *
     * @return Entity对象
     */
    public T getEntityValue(final SelectColumn sels, final DataResultSetRow row) {
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
                if (sels == null || sels.test(attr.field())) {
                    cps[i] = getFieldValue(attr, row, 0);
                }
            }
            obj = creator.create(cps);
            attrs = this.unconstructorAttributes;
        }
        for (Attribute<T, Serializable> attr : attrs) {
            if (sels == null || sels.test(attr.field())) {
                attr.set(obj, getFieldValue(attr, row, 0));
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
        return getEntityValue(constructorAttributes, constructorAttributes == null ? attributes : unconstructorAttributes, row);
    }

    public T getFullEntityValue(final Serializable... values) {
        return getEntityValue(constructorAttributes, constructorAttributes == null ? attributes : unconstructorAttributes, values);
    }

    /**
     * 将一行的ResultSet组装成一个Entity对象
     *
     * @param constructorAttrs   构建函数的Attribute数组, 大小必须与this.constructorAttributes相同
     * @param unconstructorAttrs 非构建函数的Attribute数组
     * @param row                ResultSet
     *
     * @return Entity对象
     */
    protected T getEntityValue(final Attribute<T, Serializable>[] constructorAttrs, final Attribute<T, Serializable>[] unconstructorAttrs, final DataResultSetRow row) {
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
        int index = 0;
        if (this.constructorParameters == null) {
            obj = creator.create();
        } else {
            Object[] cps = new Object[this.constructorParameters.length];
            for (int i = 0; i < constructorAttrs.length; i++) {
                Attribute<T, Serializable> attr = constructorAttrs[i];
                if (attr == null) {
                    continue;
                }
                cps[i] = getFieldValue(attr, row, ++index);
            }
            obj = creator.create(cps);
        }
        if (unconstructorAttrs != null) {
            for (Attribute<T, Serializable> attr : unconstructorAttrs) {
                if (attr == null) {
                    continue;
                }
                attr.set(obj, getFieldValue(attr, row, ++index));
            }
        }
        return obj;
    }

    /**
     * 将一行的ResultSet组装成一个Entity对象
     *
     * @param constructorAttrs   构建函数的Attribute数组, 大小必须与this.constructorAttributes相同
     * @param unconstructorAttrs 非构建函数的Attribute数组
     * @param values             字段值集合
     *
     * @return Entity对象
     */
    protected T getEntityValue(final Attribute<T, Serializable>[] constructorAttrs, final Attribute<T, Serializable>[] unconstructorAttrs, final Serializable... values) {
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
                if (attr == null) {
                    continue;
                }
                cps[i] = values[++index];
            }
            obj = creator.create(cps);
        }
        if (unconstructorAttrs != null) {
            for (Attribute<T, Serializable> attr : unconstructorAttrs) {
                if (attr == null) {
                    continue;
                }
                attr.set(obj, values[++index]);
            }
        }
        return obj;
    }

    protected Serializable getFieldValue(final DataResultSetRow row, String sqlColumn) {
        return (Serializable) row.getObject(sqlColumn);
    }

    protected Serializable getFieldValue(Attribute<T, Serializable> attr, final DataResultSetRow row, int index) {
        return row.getObject(attr, index, index > 0 ? null : this.getSQLColumn(null, attr.field()));
    }

    /**
     * 根据field字段名获取数据库对应的字段名
     *
     * @param tabalis   表别名
     * @param fieldname 字段名
     *
     * @return String
     */
    public String getSQLColumn(String tabalis, String fieldname) {
        return this.aliasmap == null ? (tabalis == null ? fieldname : (tabalis + '.' + fieldname))
            : (tabalis == null ? aliasmap.getOrDefault(fieldname, fieldname) : (tabalis + '.' + aliasmap.getOrDefault(fieldname, fieldname)));
    }

    public boolean hasConstructorAttribute() {
        return constructorAttributes != null;
    }

    /**
     * 判断Entity类的字段名与表字段名s是否存在不一致的值
     *
     * @return boolean
     */
    @ConvertDisabled
    public boolean isNoAlias() {
        return this.aliasmap == null;
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
