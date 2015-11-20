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

    public static <T extends FilterBean> FilterBeanNode load(Class<T> clazz) {
        return load(clazz, -1, true, null);
    }

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

                final boolean pubmod = Modifier.isPublic(field.getModifiers());

                char[] chars = field.getName().toCharArray();
                chars[0] = Character.toUpperCase(chars[0]);
                final Class t = field.getType();
                Method getter = null;
                try {
                    getter = cltmp.getMethod(((t == boolean.class || t == Boolean.class) ? "is" : "get") + new String(chars));
                } catch (Exception ex) {
                    if (!pubmod) continue;
                }
                fields.add(field.getName());
                FilterBeanNode newnode = new FilterBeanNode(field.getName(), true, pubmod ? Attribute.create(field) : Attribute.create(getter, null));
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
                        final String jc = joinCol.column().isEmpty() ? secinfo.getPrimary().field() : joinCol.column();
                        if (first) {
                            joinsb.append(" ").append(joinCol.type().name()).append(" JOIN ").append(secinfo.getTable())
                                    .append(" ").append(alias).append(" ON a.").append(secinfo.getSQLColumn(jc)).append(" = ")
                                    .append(alias).append(".").append(secinfo.getSQLColumn(jc));
                        }
                        newnode.foreignEntity = secinfo;
                        newnode.tabalis = alias;
                        newnode.columnAttribute = secinfo.getAttribute(newnode.column);
                        newnode.byjoinColumn = jc;
                        newnode.foreignAttribute = secinfo.getAttribute(jc);
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

    private EntityInfo foreignEntity; // join 表

    private String byjoinColumn;  //被join表的join字段

    private Attribute foreignAttribute;  //join表的join字段

    private Attribute columnAttribute;

    private long least;

    private boolean string;

    private boolean number;

    protected FilterBeanNode(String col, boolean sign, Attribute beanAttr) {
        this.column = col;
        this.and = sign;
        this.beanAttribute = beanAttr;
    }

    private void setField(Field field) {
        final FilterColumn fc = field.getAnnotation(FilterColumn.class);
        if (fc != null && !fc.name().isEmpty()) this.column = fc.name();
        final Class type = field.getType();
        this.least = fc == null ? 1L : fc.least();
        this.number = (type.isPrimitive() && type != boolean.class) || Number.class.isAssignableFrom(type);
        this.string = CharSequence.class.isAssignableFrom(type);

        FilterExpress exp = fc == null ? null : fc.express();
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
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
        FilterBeanNode newnode = new FilterBeanNode(this.column, this.and, this.beanAttribute);
        newnode.express = this.express;
        newnode.nodes = this.nodes;
        newnode.foreignEntity = this.foreignEntity;
        newnode.byjoinColumn = this.byjoinColumn;
        newnode.foreignAttribute = this.foreignAttribute;
        newnode.columnAttribute = this.columnAttribute;
        newnode.least = this.least;
        newnode.number = this.number;
        newnode.string = this.string;
        this.nodes = new FilterNode[]{newnode};
        this.column = node.column;
        this.express = node.express;
        this.and = sign;
        this.setValue(node.getValue());
        if (node instanceof FilterBeanNode) {
            FilterBeanNode beanNode = ((FilterBeanNode) node);
            this.beanAttribute = beanNode.beanAttribute;
            this.foreignEntity = beanNode.foreignEntity;
            this.byjoinColumn = beanNode.byjoinColumn;
            this.foreignAttribute = beanNode.foreignAttribute;
            this.columnAttribute = beanNode.columnAttribute;
            this.least = beanNode.least;
            this.number = beanNode.number;
            this.string = beanNode.string;
        }
    }

    @Override
    protected <T> CharSequence createSQLJoin(final EntityInfo<T> info) {
        if (joinSQL == null) return null;
        return joinSQL;
    }

    @Override
    protected <T> Predicate<T> createPredicate(final EntityCache<T> cache, FilterBean bean) {
        if (this.foreignEntity == null) return super.createPredicate(cache, bean);
        final Map<EntityInfo, Predicate> foreign = new HashMap<>();
        Predicate<T> result = null;
        putForeignPredicate(cache, foreign, bean);
        if (this.nodes != null) {
            for (FilterNode n : this.nodes) {
                FilterBeanNode node = (FilterBeanNode) n;
                if (node.foreignEntity == null) {
                    Predicate<T> f = node.createPredicate(cache, bean);
                    if (f == null) continue;
                    final Predicate<T> one = result;
                    final Predicate<T> two = f;
                    result = (result == null) ? f : (and ? new Predicate<T>() {

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
                    putForeignPredicate(cache, foreign, bean);
                }
            }
        }
        if (foreign.isEmpty()) return result;
        final String byjoinCol = this.byjoinColumn;
        final Attribute foreignAttr = this.foreignAttribute;
        for (final Map.Entry<EntityInfo, Predicate> en : foreign.entrySet()) {
            Attribute<T, Serializable> byjoinAttr = cache.getAttribute(byjoinCol);
            final Predicate p = en.getValue();
            Predicate<T> f = new Predicate<T>() {

                @Override
                public boolean test(T t) {
                    Serializable key = byjoinAttr.get(t);
                    Predicate k = (e) -> key.equals(foreignAttr.get(e));
                    return cache.exists(k.and(p));
                }

                @Override
                public String toString() {
                    return "(" + byjoinAttr.field() + " = " + en.getKey().getType().getSimpleName() + "." + foreignAttr.field() + " AND " + p + ")";
                }
            };
            final Predicate<T> one = result;
            final Predicate<T> two = f;
            result = (result == null) ? f : (and ? new Predicate<T>() {

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

    private <T> void putForeignPredicate(final EntityCache<T> cache, final Map<EntityInfo, Predicate> foreign, FilterBean bean) {
        if (this.foreignEntity == null) return;
        final Serializable val = getElementValue(bean);
        Predicate filter = (val == null && express != ISNULL && express != ISNOTNULL) ? new Predicate<T>() {

            @Override
            public boolean test(T t) {
                return and;
            }

            @Override
            public String toString() {
                return "" + and;
            }
        } : super.createElementPredicate(cache, this.columnAttribute, bean);
        if (filter == null) return;
        Predicate p = foreign.get(this.foreignEntity);
        if (p == null) {
            foreign.put(foreignEntity, filter);
        } else {
            final Predicate<T> one = p;
            final Predicate<T> two = filter;
            p = and ? new Predicate<T>() {

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
            };
            foreign.put(foreignEntity, p);
        }
    }

    @Override
    protected boolean isCacheUseable() {
        return joinallcached;
    }

    @Override
    protected Serializable getElementValue(FilterBean bean) {
        if (bean == null || beanAttribute == null) return null;
        Serializable rs = (Serializable) beanAttribute.get(bean);
        if (rs == null) return null;
        if (string && ((CharSequence) rs).length() == 0) return null;
        if (number && ((Number) rs).longValue() < this.least) return null;
        return rs;
    }
}
