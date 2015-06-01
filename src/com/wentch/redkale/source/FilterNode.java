/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.source;

import static com.wentch.redkale.source.FilterExpress.*;
import com.wentch.redkale.util.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/**
 *
 * @author zhangjx
 */
public class FilterNode {

    protected boolean signand = true;

    protected String tabalis;

    protected String column;

    protected FilterExpress express;

    protected FilterNode[] nodes;

    private Serializable value;

    public FilterNode() {
    }

    FilterNode(String col, FilterExpress exp, Serializable val) {
        Objects.requireNonNull(col);
        if (exp == null) {
            if (val instanceof Range) {
                exp = FilterExpress.BETWEEN;
            } else if (val instanceof Collection) {
                exp = FilterExpress.IN;
            } else if (val != null && val.getClass().isArray()) {
                exp = FilterExpress.IN;
            } else {
                exp = FilterExpress.EQUAL;
            }
        }
        this.column = col;
        this.express = exp;
        this.value = val;
    }

    public final FilterNode and(FilterNode node) {
        return any(node, true);
    }

    public final FilterNode and(String column, Serializable value) {
        return and(new FilterNode(column, null, value));
    }

    public final FilterNode and(String column, FilterExpress express, Serializable value) {
        return and(new FilterNode(column, express, value));
    }

    public final FilterNode or(FilterNode node) {
        return any(node, false);
    }

    public final FilterNode or(String column, Serializable value) {
        return or(new FilterNode(column, null, value));
    }

    public final FilterNode or(String column, FilterExpress express, Serializable value) {
        return or(new FilterNode(column, express, value));
    }

    protected final FilterNode any(FilterNode node, boolean sign) {
        Objects.requireNonNull(node);
        if (nodes == null) {
            nodes = new FilterNode[]{node};
            this.signand = sign;
            return this;
        }
        if (signand == sign) {
            FilterNode[] newsiblings = new FilterNode[nodes.length + 1];
            System.arraycopy(nodes, 0, newsiblings, 0, nodes.length);
            newsiblings[nodes.length] = node;
            this.nodes = newsiblings;
            return this;
        }
        this.append(node, sign);
        return this;
    }

    protected void append(FilterNode node, boolean sign) {
        FilterNode newnode = new FilterNode(this.column, this.express, this.value);
        newnode.signand = this.signand;
        newnode.nodes = this.nodes;
        this.nodes = new FilterNode[]{newnode};
        this.tabalis = node.tabalis;
        this.column = node.column;
        this.express = node.express;
        this.signand = sign;
        this.value = node.value;
    }

    protected Serializable getValue(FilterBean bean) {
        return value;
    }

    protected boolean isJoinAllCached() {
        return true;
    }

    public static FilterNode create(String column, Serializable value) {
        return create(column, FilterExpress.EQUAL, value);
    }

    public static FilterNode create(String column, FilterExpress express, Serializable value) {
        return new FilterNode(column, express, value);
    }

    protected final <T> StringBuilder createFilterSQLExpress(final EntityInfo<T> info, FilterBean bean) {
        return createFilterSQLExpress(true, info, bean);
    }

    protected <T> StringBuilder createFilterSQLExpress(final boolean first, final EntityInfo<T> info, FilterBean bean) {
        final Serializable val = getValue(bean);
        if (val == null && (express == ISNULL || express == ISNOTNULL)) return new StringBuilder(0);
        StringBuilder sb0 = createFilterSQLExpress(info, val);
        if (this.nodes == null) {
            if (sb0 == null) return new StringBuilder(0);
            if (!first) return sb0;
            return new StringBuilder(sb0.length() + 8).append(" WHERE ").append(sb0);
        }
        final StringBuilder rs = new StringBuilder();
        rs.append(first ? " WHERE (" : " (");
        boolean more = false;
        if (sb0 != null && sb0.length() > 2) {
            more = true;
            rs.append(sb0);
        }
        for (FilterNode node : this.nodes) {
            StringBuilder f = node.createFilterSQLExpress(false, info, bean);
            if (f == null || f.length() < 3) continue;
            if (more) rs.append(signand ? " AND " : " OR ");
            rs.append(f);
            more = true;
        }
        rs.append(')');
        if (rs.length() < (first ? 10 : 5)) return new StringBuilder(0);
        return rs;
    }

    protected static <E> String createFilterSQLOrderBy(EntityInfo<E> info, Flipper flipper) {
        if (flipper == null || flipper.getSort() == null || flipper.getSort().isEmpty()) return "";
        final StringBuilder sb = new StringBuilder();
        sb.append(" ORDER BY ");
        if (info.isNoAlias()) {
            sb.append(flipper.getSort());
        } else {
            boolean flag = false;
            for (String item : flipper.getSort().split(",")) {
                if (item.isEmpty()) continue;
                String[] sub = item.split("\\s+");
                if (flag) sb.append(',');
                if (sub.length < 2 || sub[1].equalsIgnoreCase("ASC")) {
                    sb.append("a.").append(info.getSQLColumn(sub[0])).append(" ASC");
                } else {
                    sb.append("a.").append(info.getSQLColumn(sub[0])).append(" DESC");
                }
                flag = true;
            }
        }
        return sb.toString();
    }

    private <T> StringBuilder createFilterSQLExpress(final EntityInfo<T> info, Serializable val0) {
        final StringBuilder val = formatValue(val0);
        if (val == null) return null;
        StringBuilder sb = new StringBuilder();
        if (tabalis != null) sb.append(tabalis).append('.');
        sb.append(info.getSQLColumn(column)).append(' ');
        switch (express) {
            case ISNULL:
            case ISNOTNULL:
                sb.append(express.value());
                break;
            case OPAND:
            case OPOR:
                sb.append(express.value()).append(' ').append(val).append(" > 0");
                break;
            case OPANDNO:
                sb.append(express.value()).append(' ').append(val).append(" = 0");
                break;
            default:
                sb.append(express.value()).append(' ').append(val);
                break;
        }
        return sb;
    }

    protected final <T> Predicate<T> createFilterPredicate(final EntityInfo<T> info, FilterBean bean) {
        if (info == null) return null;
        final Serializable val = getValue(bean);
        if (val == null && express != ISNULL && express != ISNOTNULL) return null;
        Predicate<T> filter = createFilterPredicate(info.getAttribute(column), val);
        if (nodes == null) return filter;
        for (FilterNode node : this.nodes) {
            Predicate<T> f = node.createFilterPredicate(info, bean);
            if (f == null) continue;
            filter = (filter == null) ? f : (signand ? filter.and(f) : filter.or(f));
        }
        return filter;
    }

    private <T> Predicate<T> createFilterPredicate(final Attribute<T, Serializable> attr, final Serializable val) {
        if (attr == null) return null;
        switch (express) {
            case EQUAL: return (T t) -> val.equals(attr.get(t));
            case NOTEQUAL: return (T t) -> !val.equals(attr.get(t));
            case GREATERTHAN: return (T t) -> ((Number) attr.get(t)).longValue() > ((Number) val).longValue();
            case LESSTHAN: return (T t) -> ((Number) attr.get(t)).longValue() < ((Number) val).longValue();
            case GREATERTHANOREQUALTO: return (T t) -> ((Number) attr.get(t)).longValue() >= ((Number) val).longValue();
            case LESSTHANOREQUALTO: return (T t) -> ((Number) attr.get(t)).longValue() <= ((Number) val).longValue();
            case ISNULL: return (T t) -> attr.get(t) == null;
            case ISNOTNULL: return (T t) -> attr.get(t) != null;
            case OPAND: return (T t) -> (((Number) attr.get(t)).longValue() & ((Number) val).longValue()) > 0;
            case OPOR: return (T t) -> (((Number) attr.get(t)).longValue() | ((Number) val).longValue()) > 0;
            case OPANDNO: return (T t) -> (((Number) attr.get(t)).longValue() & ((Number) val).longValue()) == 0;
            case LIKE:
                return (T t) -> {
                    Object rs = attr.get(t);
                    return rs != null && rs.toString().contains(val.toString());
                };
            case NOTLIKE:
                return (T t) -> {
                    Object rs = attr.get(t);
                    return rs == null || !rs.toString().contains(val.toString());
                };
            case BETWEEN:
            case NOTBETWEEN:
                Range range = (Range) val;
                final Comparable min = range.getMin();
                final Comparable max = range.getMax();
                Predicate<T> p = (T t) -> {
                    Comparable rs = (Comparable) attr.get(t);
                    if (rs == null) return false;
                    if (min != null && min.compareTo(rs) >= 0) return false;
                    return !(max != null && max.compareTo(rs) <= 0);
                };
                if (express == NOTBETWEEN) p = p.negate();
                return p;
            case IN:
            case NOTIN:
                Predicate<T> filter;
                if (val instanceof Collection) {
                    filter = (T t) -> {
                        Object rs = attr.get(t);
                        return rs != null && ((Collection) val).contains(rs);
                    };
                } else {
                    Class type = val.getClass();
                    if (type == int[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((int[]) val, (int) rs) >= 0;
                        };
                    } else if (type == short[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((short[]) val, (short) rs) >= 0;
                        };
                    } else if (type == long[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((long[]) val, (long) rs) >= 0;
                        };
                    } else if (type == float[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((float[]) val, (float) rs) >= 0;
                        };
                    } else if (type == double[].class) {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            if (rs == null) return false;
                            return Arrays.binarySearch((double[]) val, (double) rs) >= 0;
                        };
                    } else {
                        filter = (T t) -> {
                            Object rs = attr.get(t);
                            return rs != null && Arrays.binarySearch((Object[]) val, rs) > -1;
                        };
                    }
                }
                if (express == NOTIN) filter = filter.negate();
                return filter;
        }
        return null;
    }

    protected static <E> Comparator<E> createFilterComparator(EntityInfo<E> info, Flipper flipper) {
        if (flipper == null || flipper.getSort() == null || flipper.getSort().isEmpty()) return null;
        Comparator<E> comparator = null;
        for (String item : flipper.getSort().split(",")) {
            if (item.isEmpty()) continue;
            String[] sub = item.split("\\s+");
            final Attribute<E, Serializable> attr = info.getAttribute(sub[0]);
            Comparator<E> c = (E o1, E o2) -> {
                Comparable c1 = (Comparable) attr.get(o1);
                Comparable c2 = (Comparable) attr.get(o2);
                return c1 == null ? -1 : c1.compareTo(c2);
            };
            if (sub.length > 1 && sub[1].equalsIgnoreCase("DESC")) {
                c = c.reversed();
            }
            if (comparator == null) {
                comparator = c;
            } else {
                comparator = comparator.thenComparing(c);
            }
        }
        return comparator;
    }

    protected StringBuilder formatValue(Object value) {
        return formatValue(express, value);
    }

    protected static String formatToString(Object value) {
        StringBuilder sb = formatValue(null, value);
        return sb == null ? null : sb.toString();
    }

    private static StringBuilder formatValue(FilterExpress express, Object value) {
        if (value == null) return null;
        if (value instanceof Number) return new StringBuilder().append(value);
        if (value instanceof CharSequence) {
            if (express == LIKE || express == NOTLIKE) value = "%" + value + '%';
            return new StringBuilder().append('\'').append(value.toString().replace("'", "\\'")).append('\'');
        } else if (value instanceof Range) {
            Range range = (Range) value;
            boolean rangestring = range.getClass() == Range.StringRange.class;
            StringBuilder sb = new StringBuilder();
            if (rangestring) {
                sb.append('\'').append(range.getMin().toString().replace("'", "\\'")).append('\'');
            } else {
                sb.append(range.getMin());
            }
            sb.append(" AND ");
            if (rangestring) {
                sb.append('\'').append(range.getMax().toString().replace("'", "\\'")).append('\'');
            } else {
                sb.append(range.getMax());
            }
            return sb;
        } else if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            if (len == 0) return null;
            if (len == 1) {
                Object firstval = Array.get(value, 0);
                if (firstval != null && firstval.getClass().isArray()) return formatValue(express, firstval);
            }
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            for (int i = 0; i < len; i++) {
                Object o = Array.get(value, i);
                if (sb.length() > 1) sb.append(',');
                if (o instanceof CharSequence) {
                    sb.append('\'').append(o.toString().replace("'", "\\'")).append('\'');
                } else {
                    sb.append(o);
                }
            }
            return sb.append(')');
        } else if (value instanceof Collection) {
            Collection c = (Collection) value;
            if (c.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append('(');
            for (Object o : c) {
                if (sb.length() > 1) sb.append(',');
                if (o instanceof CharSequence) {
                    sb.append('\'').append(o.toString().replace("'", "\\'")).append('\'');
                } else {
                    sb.append(o);
                }
            }
            return sb.append(')');
        }
        return new StringBuilder().append(value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (nodes == null) {
            sb.append(column).append(' ').append(express.value()).append(' ').append(formatValue(value));
        } else {
            sb.append('(').append(column).append(' ').append(express.value()).append(' ').append(formatValue(value));
            for (FilterNode node : this.nodes) {
                sb.append(signand ? " AND " : " OR ").append(node.toString());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    public Serializable getValue() {
        return value;
    }

    public void setValue(Serializable value) {
        this.value = value;
    }

    public boolean isSignand() {
        return signand;
    }

    public void setSignand(boolean signand) {
        this.signand = signand;
    }

    public String getTabalis() {
        return tabalis;
    }

    public void setTabalis(String tabalis) {
        this.tabalis = tabalis;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public FilterExpress getExpress() {
        return express;
    }

    public void setExpress(FilterExpress express) {
        this.express = express;
    }

    public FilterNode[] getNodes() {
        return nodes;
    }

    public void setNodes(FilterNode[] nodes) {
        this.nodes = nodes;
    }

}
