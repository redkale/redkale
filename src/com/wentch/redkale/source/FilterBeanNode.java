/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import static com.wentch.redkale.source.FilterExpress.*;
import com.wentch.redkale.util.*;
import java.io.Serializable;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.logging.Logger;
import javax.persistence.Transient;

/**
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
final class FilterBeanNode extends FilterNode {

    private static final Logger logger = Logger.getLogger(FilterBeanNode.class.getSimpleName());

    private static final ConcurrentHashMap<Class, FilterBeanNode> beanodes = new ConcurrentHashMap<>();

    public static <T extends FilterBean> FilterBeanNode load(Class<T> clazz, final int nodeid, final boolean cacheForbidden,
            Function<Class, List> fullloader) {
        FilterBeanNode rs = beanodes.get(clazz);
        if (rs != null) return rs;
        synchronized (beanodes) {
            rs = beanodes.get(clazz);
            if (rs == null) {
                rs = createNode(clazz, nodeid, cacheForbidden, fullloader);
                beanodes.put(clazz, rs);
            }
            return rs;
        }
    }

    private static <T extends FilterBean> FilterBeanNode createNode(Class<T> clazz, final int nodeid, final boolean cacheForbidden,
            Function<Class, List> fullloader) {
        Class cltmp = clazz;
        Set<String> fields = new HashSet<>();
        final Map<Class, String> joinTables = new HashMap<>();
        Map<String, FilterBeanNode> nodemap = new HashMap<>();
        boolean joinallcached = true;
        final StringBuilder joinsb = new StringBuilder();
        do {
            for (Field field : cltmp.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (fields.contains(field.getName())) continue;
                if (field.getAnnotation(Ignore.class) != null) continue;
                if (field.getAnnotation(Transient.class) != null) continue;

                char[] chars = field.getName().toCharArray();
                chars[0] = Character.toUpperCase(chars[0]);
                final Class t = field.getType();
                Method getter;
                try {
                    getter = cltmp.getMethod(((t == boolean.class || t == Boolean.class) ? "is" : "get") + new String(chars));
                } catch (Exception ex) {
                    continue;
                }
                fields.add(field.getName());
                FilterBeanNode newnode = new FilterBeanNode(field.getName(), true, Attribute.create(getter, null));
                newnode.setField(field);
                //------------------------------------
                {
                    FilterJoinColumn joinCol = field.getAnnotation(FilterJoinColumn.class);
                    if (joinCol != null) {
                        boolean first = false;
                        final Class joinClass = joinCol.table();
                        if (!joinTables.containsKey(joinClass)) {
                            first = true;
                            joinTables.put(joinClass, String.valueOf((char) ('b' + joinTables.size())));
                        }
                        final String alias = joinTables.get(joinClass);
                        final EntityInfo secinfo = EntityInfo.load(joinClass, nodeid, cacheForbidden, fullloader);
                        if (secinfo.getCache() == null || !secinfo.getCache().isFullLoaded()) {
                            joinallcached = false;
                        }
                        if (first) {
                            joinsb.append(" ").append(joinCol.type().name()).append(" JOIN ").append(secinfo.getTable())
                                    .append(" ").append(alias).append(" ON a.# = ").append(alias).append(".")
                                    .append(joinCol.column().isEmpty() ? secinfo.getPrimarySQLColumn() : secinfo.getSQLColumn(joinCol.column()));
                        }
                        newnode.foreignEntity = secinfo;
                        newnode.tabalis = alias;
                        newnode.columnAttribute = secinfo.getAttribute(newnode.column);
                        newnode.foreignAttribute = joinCol.column().isEmpty() ? secinfo.getPrimary() : secinfo.getAttribute(joinCol.column());
                        if (newnode.foreignEntity != null && newnode.foreignAttribute == null) throw new RuntimeException(clazz.getName() + "." + field.getName() + " have illegal FilterJoinColumn " + joinCol);
                    }
                }
                //------------------------------------
                {
                    FilterGroup[] refs = field.getAnnotationsByType(FilterGroup.class);
                    String[] groups = new String[refs.length];
                    for (int i = 0; i < refs.length; i++) {
                        groups[i] = refs[i].value();
                    }
                    if (groups.length == 0) groups = new String[]{"[AND]"};
                    for (String key : groups) {
                        if (!key.startsWith("[AND]") && !key.startsWith("[OR]")) {
                            throw new RuntimeException(field + "'s FilterGroup.value(" + key + ") illegal, must be [AND] or [OR] startsWith");
                        }
                        FilterBeanNode node = nodemap.get(key);
                        if (node == null) {
                            nodemap.put(key, newnode);
                        } else {
                            node.any(newnode, !key.contains("[OR]"));
                        }
                    }
                }
            }
        } while ((cltmp = cltmp.getSuperclass()) != Object.class);
        FilterBeanNode rs = null;
        for (FilterBeanNode f : nodemap.values()) {
            if (rs == null) {
                rs = f;
            } else {
                rs.and(f);
            }
        }
        if (rs != null) {
            rs.joinallcached = joinallcached;
            if (joinsb.length() > 0) rs.joinSQL = joinsb.toString();
        }
        return rs;
    }

    //--------------------------- only header -----------------------------------------------------
    private boolean joinallcached = true;

    private String joinSQL;

    //---------------------------------------------------------------------------------------------
    private Attribute beanAttribute;

    private EntityInfo foreignEntity;

    private Attribute foreignAttribute;

    private Attribute columnAttribute;

    private boolean array;

    private boolean collection;

    private boolean string;

    private boolean number;

    private boolean ignoreCase;

    private long least;

    FilterBeanNode(String col, boolean sign, Attribute beanAttr) {
        this.column = col;
        this.signand = sign;
        this.beanAttribute = beanAttr;
    }

    private void setField(Field field) {
        final FilterColumn fc = field.getAnnotation(FilterColumn.class);
        if (fc != null && !fc.name().isEmpty()) this.column = fc.name();
        final Class type = field.getType();
        this.array = type.isArray();
        this.collection = Collection.class.isAssignableFrom(type);
        this.least = fc == null ? 1L : fc.least();
        this.likefit = fc == null ? true : fc.likefit();
        this.ignoreCase = fc == null ? true : fc.ignoreCase();
        this.number = (type.isPrimitive() && type != boolean.class) || Number.class.isAssignableFrom(type);
        this.string = CharSequence.class.isAssignableFrom(type);

        FilterExpress exp = fc == null ? null : fc.express();
        if (this.array || this.collection) {
            if (Range.class.isAssignableFrom(type.getComponentType())) {
                if (AND != exp) exp = OR;
            } else {
                if (NOTIN != exp) exp = IN;
            }
        } else if (Range.class.isAssignableFrom(type)) {
            if (NOTBETWEEN != exp) exp = BETWEEN;
        }
        if (exp == null) exp = EQUAL;
        this.express = exp;
        this.tabalis = "a";
    }

    @Override
    protected void append(FilterNode node, boolean sign) {
        FilterBeanNode newnode = new FilterBeanNode(this.column, this.signand, this.beanAttribute);
        newnode.express = this.express;
        newnode.nodes = this.nodes;
        newnode.foreignEntity = this.foreignEntity;
        newnode.foreignAttribute = this.foreignAttribute;
        newnode.columnAttribute = this.columnAttribute;
        newnode.array = this.array;
        newnode.collection = this.collection;
        newnode.ignoreCase = this.ignoreCase;
        newnode.least = this.least;
        newnode.number = this.number;
        newnode.string = this.string;
        this.nodes = new FilterNode[]{newnode};
        this.column = node.column;
        this.express = node.express;
        this.signand = sign;
        this.setValue(node.getValue());
        if (node instanceof FilterBeanNode) {
            FilterBeanNode beanNode = ((FilterBeanNode) node);
            this.beanAttribute = beanNode.beanAttribute;
            this.foreignEntity = beanNode.foreignEntity;
            this.foreignAttribute = beanNode.foreignAttribute;
            this.columnAttribute = beanNode.columnAttribute;
            this.array = beanNode.array;
            this.collection = beanNode.collection;
            this.ignoreCase = beanNode.ignoreCase;
            this.least = beanNode.least;
            this.number = beanNode.number;
            this.string = beanNode.string;
        }
    }

    @Override
    protected <T> StringBuilder createFilterSQLExpress(final boolean first, final EntityInfo<T> info, FilterBean bean) {
        if (joinSQL == null || !first) return super.createFilterSQLExpress(first, info, bean);
        StringBuilder sb = super.createFilterSQLExpress(first, info, bean);
        String jsql = joinSQL.replace("#", info.getPrimarySQLColumn());
        return new StringBuilder(sb.length() + jsql.length()).append(jsql).append(sb);
    }

    @Override
    protected <T> Predicate<T> createFilterPredicate(final EntityInfo<T> info, FilterBean bean) {
        return createFilterPredicate(true, info, bean);
    }

    private <T> Predicate<T> createFilterPredicate(final boolean first, final EntityInfo<T> info, FilterBean bean) {
        //if ((this.joinSQL == null && first) || this.foreignEntity == null) return super.createFilterPredicate(info, bean);
        if (this.foreignEntity == null) return super.createFilterPredicate(info, bean);
        final Map<EntityInfo, Predicate> foreign = new HashMap<>();
        Predicate<T> result = null;
        putForeignPredicate(foreign, bean);
        if (this.nodes != null) {
            for (FilterNode n : this.nodes) {
                FilterBeanNode node = (FilterBeanNode) n;
                if (node.foreignEntity == null) {
                    Predicate<T> f = node.createFilterPredicate(false, info, bean);
                    if (f == null) continue;
                    final Predicate<T> one = result;
                    final Predicate<T> two = f;
                    result = (result == null) ? f : (signand ? new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            return one.test(t) && two.test(t);
                        }

                        @Override
                        public String toString() {
                            return "(" + one + " AND " + two + ")";
                        }
                    } : new Predicate<T>() {

                        @Override
                        public boolean test(T t) {
                            return one.test(t) || two.test(t);
                        }

                        @Override
                        public String toString() {
                            return "(" + one + " OR " + two + ")";
                        }
                    });
                } else {
                    putForeignPredicate(foreign, bean);
                }
            }
        }
        if (foreign.isEmpty()) return result;
        final Attribute foreignAttr = this.foreignAttribute;
        for (final Map.Entry<EntityInfo, Predicate> en : foreign.entrySet()) {
            Attribute<T, Serializable> mainIdAttr = info.getPrimary();
            final EntityCache cache = en.getKey().getCache();
            final Predicate p = en.getValue();
            Predicate<T> f = new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    Serializable key = mainIdAttr.get(t);
                    Predicate k = (e) -> key.equals(foreignAttr.get(e));
                    return cache.exists(k.and(p));
                }

                @Override
                public String toString() {
                    return "(" + mainIdAttr.field() + " = " + en.getKey().getType().getSimpleName() + "." + foreignAttr.field() + " AND " + p + ")";
                }
            };
            final Predicate<T> one = result;
            final Predicate<T> two = f;
            result = (result == null) ? f : (signand ? new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return one.test(t) && two.test(t);
                }

                @Override
                public String toString() {
                    return "(" + one + " AND " + two + ")";
                }
            } : new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    return one.test(t) || two.test(t);
                }

                @Override
                public String toString() {
                    return "(" + one + " OR " + two + ")";
                }
            });
        }
        return result;
    }

    private void putForeignPredicate(final Map<EntityInfo, Predicate> foreign, FilterBean bean) {
        if (this.foreignEntity == null) return;
        final Serializable val = getValue(bean);
        Predicate filter = (val == null && express != ISNULL && express != ISNOTNULL) ? ((t) -> signand) : super.createFilterPredicate(this.columnAttribute, val);
        if (filter == null) return;
        Predicate p = foreign.get(this.foreignEntity);
        if (p == null) {
            foreign.put(foreignEntity, filter);
        } else if (signand) {
            p.and(filter);
        } else {
            p.or(filter);
        }
    }

    @Override
    protected boolean isJoinAllCached() {
        return joinallcached;
    }

    @Override
    protected Serializable getValue(FilterBean bean) {
        if (bean == null || beanAttribute == null) return null;
        Serializable rs = (Serializable) beanAttribute.get(bean);
        if (rs == null) return null;
        if (string && ((CharSequence) rs).length() == 0) return null;
        if (number && ((Number) rs).longValue() < this.least) return null;
        if (array && Array.getLength(rs) == 0) return null;
        if (collection && ((Collection) rs).isEmpty()) return null;
        return rs;
    }
}
