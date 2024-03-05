/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.source;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.Attribute;

/**
 * java.sql.ResultSet的简化版。  <br>
 * <br>
 * 字段类型支持： <br>
 * 1、boolean/Boolean <br>
 * 2、byte/Byte <br>
 * 3、short/Short <br>
 * 4、char/Character <br>
 * 5、int/Integer/AtomicInteger <br>
 * 6、long/Long/AtomicLong/LongAdder <br>
 * 7、float/Float <br>
 * 8、double/Double <br>
 * 9、java.math.BigInteger <br>
 * 10、java.math.BigDecimal <br>
 * 11、String <br>
 * 12、byte[] <br>
 * 13、java.time.LocalDate/java.sql.Date/java.util.Date <br>
 * 14、java.time.LocalTime/java.sql.Time <br>
 * 15、java.time.LocalDateTime/java.sql.Timestamp <br>
 * 16、JavaBean/其他可JSON化类型 <br>
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.5.0
 */
public interface DataResultSet extends EntityInfo.DataResultSetRow {

    public boolean next();

    public void close();

    public static Serializable formatColumnValue(Class t, Object o) {
        return formatColumnValue(t, null, o);
    }

    public static Serializable formatColumnValue(Class t, Type genericType, Object o) {
        if (t == byte[].class) {
            return (byte[]) o;
        } else {
            if (t.isPrimitive()) {
                if (o != null) {
                    if (t == int.class) {
                        o = ((Number) o).intValue();
                    } else if (t == long.class) {
                        o = ((Number) o).longValue();
                    } else if (t == short.class) {
                        o = ((Number) o).shortValue();
                    } else if (t == float.class) {
                        o = ((Number) o).floatValue();
                    } else if (t == double.class) {
                        o = ((Number) o).doubleValue();
                    } else if (t == byte.class) {
                        o = ((Number) o).byteValue();
                    } else if (t == char.class) {
                        o = (char) ((Number) o).intValue();
                    } else if (t == boolean.class) {
                        o = (Boolean) o;
                    }
                } else if (t == int.class) {
                    o = 0;
                } else if (t == long.class) {
                    o = 0L;
                } else if (t == short.class) {
                    o = (short) 0;
                } else if (t == float.class) {
                    o = 0.0f;
                } else if (t == double.class) {
                    o = 0.0d;
                } else if (t == byte.class) {
                    o = (byte) 0;
                } else if (t == boolean.class) {
                    o = false;
                } else if (t == char.class) {
                    o = (char) 0;
                }
            } else if (t == Integer.class) {
                o = ((Number) o).intValue();
            } else if (t == Long.class) {
                o = ((Number) o).longValue();
            } else if (t == Short.class) {
                o = ((Number) o).shortValue();
            } else if (t == Float.class) {
                o = ((Number) o).floatValue();
            } else if (t == Double.class) {
                o = ((Number) o).doubleValue();
            } else if (t == Byte.class) {
                o = ((Number) o).byteValue();
            } else if (t == Character.class) {
                o = (char) ((Number) o).intValue();
            } else if (t == Boolean.class) {
                o = (Boolean) o;
            } else if (t == AtomicInteger.class) {
                if (o != null) {
                    o = new AtomicInteger(((Number) o).intValue());
                } else {
                    o = new AtomicInteger();
                }
            } else if (t == AtomicLong.class) {
                if (o != null) {
                    o = new AtomicLong(((Number) o).longValue());
                } else {
                    o = new AtomicLong();
                }
            } else if (t == LongAdder.class) {
                if (o != null) {
                    LongAdder v = new LongAdder();
                    v.add(((Number) o).longValue());
                    o = v;
                } else {
                    o = new LongAdder();
                }
            } else if (t == BigInteger.class) {
                if (o != null && !(o instanceof BigInteger)) {
                    if (o instanceof byte[]) {
                        o = new BigInteger((byte[]) o);
                    } else {
                        o = new BigInteger(o.toString(), 10);
                    }
                }
            } else if (t == BigDecimal.class) {
                if (o != null && !(o instanceof BigDecimal)) {
                    if (o instanceof byte[]) {
                        o = new BigDecimal(new String((byte[]) o));
                    } else {
                        o = new BigDecimal(o.toString());
                    }
                }
            } else if (t == LocalDate.class) {
                if (o != null && !(o instanceof LocalDate)) {
                    if (o instanceof java.sql.Date) {
                        o = ((java.sql.Date) o).toLocalDate();
                    } else if (o instanceof java.sql.Timestamp) {
                        o = ((java.sql.Timestamp) o).toLocalDateTime().toLocalDate();
                    }
                }
            } else if (t == LocalTime.class) {
                if (o != null && !(o instanceof LocalTime)) {
                    if (o instanceof java.sql.Time) {
                        o = ((java.sql.Time) o).toLocalTime();
                    } else if (o instanceof java.sql.Timestamp) {
                        o = ((java.sql.Timestamp) o).toLocalDateTime().toLocalTime();
                    }
                }
            } else if (t == LocalDateTime.class) {
                if (o != null && !(o instanceof LocalDateTime)) {
                    if (o instanceof java.sql.Date) {
                        o = ((java.sql.Date) o).toLocalDate().atStartOfDay();
                    } else if (o instanceof java.sql.Timestamp) {
                        o = ((java.sql.Timestamp) o).toLocalDateTime();
                    }
                }
            } else if (t == Instant.class) {
                if (o != null && !(o instanceof Instant)) {
                    if (o instanceof java.sql.Date) {
                        o = ((java.sql.Date) o).toInstant();
                    } else if (o instanceof java.sql.Time) {
                        o = ((java.sql.Time) o).toInstant();
                    } else if (o instanceof java.sql.Timestamp) {
                        o = ((java.sql.Timestamp) o).toInstant();
                    }
                }
            } else if (t == String.class) {
                if (o == null) {
                    o = "";
                } else if (o instanceof byte[]) {
                    o = new String((byte[]) o, StandardCharsets.UTF_8);
                } else {
                    o = o.toString();
                }
            } else if (o != null && !t.isAssignableFrom(o.getClass()) && o instanceof CharSequence) {
                o = ((CharSequence) o).length() == 0 ? null : JsonConvert.root().convertFrom(genericType == null ? t : genericType, o.toString());
            }
        }
        return (Serializable) o;
    }

    public static <T> Serializable getRowColumnValue(final EntityInfo.DataResultSetRow row, Attribute<T, Serializable> attr, int index, String column) {
        final Class t = attr.type();
        Serializable o = null;
        try {
            if (t == byte[].class) {
                Object blob = index > 0 ? row.getObject(index) : row.getObject(column);
                if (blob == null) {
                    o = null;
                } else { //不支持超过2G的数据
//                if (blob instanceof java.sql.Blob) {
//                    java.sql.Blob b = (java.sql.Blob) blob;
//                    try {
//                        o = b.getBytes(1, (int) b.length());
//                    } catch (Exception e) { //一般不会发生
//                        o = null;
//                    }
//                } else {
                    o = (byte[]) blob;
                    //}
                }
            } else {
                o = (Serializable) (index > 0 ? row.getObject(index) : row.getObject(column));
                o = formatColumnValue(t, attr.genericType(), o);
            }
        } catch (Exception e) {
            throw new SourceException(row.getEntityInfo() + "." + attr.field() + ".value=" + o + ": " + e.getMessage(), e.getCause());
        }
        return o;
    }

}
