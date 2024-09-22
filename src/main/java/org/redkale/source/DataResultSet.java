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
 * java.sql.ResultSet的简化版。 <br>
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
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.5.0
 */
public interface DataResultSet extends DataResultSetRow {

    public boolean next();

    public void close();

    /**
     * 将对象转化成另一个类型对象
     * @param type 类型
     * @param o 数据库字段值
     * @return  转换后对象
     */
    public static Serializable formatColumnValue(Class type, Object o) {
        return formatColumnValue(type, null, o);
    }

    /**
     * 将对象转化成另一个类型对象
     * @param type 类型
     * @param genericType 泛型类型
     * @param o 数据库字段值
     * @return  转换后对象
     */
    public static Serializable formatColumnValue(Class type, Type genericType, Object o) {
        if (type == byte[].class) {
            return (byte[]) o;
        } else {
            if (type.isPrimitive()) {
                if (o != null) {
                    if (type == int.class) {
                        o = ((Number) o).intValue();
                    } else if (type == long.class) {
                        o = ((Number) o).longValue();
                    } else if (type == short.class) {
                        o = ((Number) o).shortValue();
                    } else if (type == float.class) {
                        o = ((Number) o).floatValue();
                    } else if (type == double.class) {
                        o = ((Number) o).doubleValue();
                    } else if (type == byte.class) {
                        o = ((Number) o).byteValue();
                    } else if (type == char.class) {
                        o = (char) ((Number) o).intValue();
                    } else if (type == boolean.class) {
                        if (o instanceof Number) {
                            o = ((Number) o).intValue() != 0;
                        }
                    }
                } else if (type == int.class) {
                    o = 0;
                } else if (type == long.class) {
                    o = 0L;
                } else if (type == short.class) {
                    o = (short) 0;
                } else if (type == float.class) {
                    o = 0.0f;
                } else if (type == double.class) {
                    o = 0.0d;
                } else if (type == byte.class) {
                    o = (byte) 0;
                } else if (type == boolean.class) {
                    o = false;
                } else if (type == char.class) {
                    o = (char) 0;
                }
            } else if (type == Integer.class) {
                o = ((Number) o).intValue();
            } else if (type == Long.class) {
                o = ((Number) o).longValue();
            } else if (type == Short.class) {
                o = ((Number) o).shortValue();
            } else if (type == Float.class) {
                o = ((Number) o).floatValue();
            } else if (type == Double.class) {
                o = ((Number) o).doubleValue();
            } else if (type == Byte.class) {
                o = ((Number) o).byteValue();
            } else if (type == Character.class) {
                o = (char) ((Number) o).intValue();
            } else if (type == Boolean.class) {
                if (o instanceof Number) {
                    o = ((Number) o).intValue() != 0;
                }
            } else if (type == AtomicInteger.class) {
                if (o != null) {
                    o = new AtomicInteger(((Number) o).intValue());
                } else {
                    o = new AtomicInteger();
                }
            } else if (type == AtomicLong.class) {
                if (o != null) {
                    o = new AtomicLong(((Number) o).longValue());
                } else {
                    o = new AtomicLong();
                }
            } else if (type == LongAdder.class) {
                if (o != null) {
                    LongAdder v = new LongAdder();
                    v.add(((Number) o).longValue());
                    o = v;
                } else {
                    o = new LongAdder();
                }
            } else if (type == BigInteger.class) {
                if (o != null && !(o instanceof BigInteger)) {
                    if (o instanceof byte[]) {
                        o = new BigInteger((byte[]) o);
                    } else {
                        o = new BigInteger(o.toString(), 10);
                    }
                }
            } else if (type == BigDecimal.class) {
                if (o != null && !(o instanceof BigDecimal)) {
                    if (o instanceof byte[]) {
                        o = new BigDecimal(new String((byte[]) o));
                    } else {
                        o = new BigDecimal(o.toString());
                    }
                }
            } else if (type == LocalDate.class) {
                if (o != null && !(o instanceof LocalDate)) {
                    if (o instanceof java.sql.Date) {
                        o = ((java.sql.Date) o).toLocalDate();
                    } else if (o instanceof java.sql.Timestamp) {
                        o = ((java.sql.Timestamp) o).toLocalDateTime().toLocalDate();
                    }
                }
            } else if (type == LocalTime.class) {
                if (o != null && !(o instanceof LocalTime)) {
                    if (o instanceof java.sql.Time) {
                        o = ((java.sql.Time) o).toLocalTime();
                    } else if (o instanceof java.sql.Timestamp) {
                        o = ((java.sql.Timestamp) o).toLocalDateTime().toLocalTime();
                    }
                }
            } else if (type == LocalDateTime.class) {
                if (o != null && !(o instanceof LocalDateTime)) {
                    if (o instanceof java.sql.Date) {
                        o = ((java.sql.Date) o).toLocalDate().atStartOfDay();
                    } else if (o instanceof java.sql.Timestamp) {
                        o = ((java.sql.Timestamp) o).toLocalDateTime();
                    }
                }
            } else if (type == Instant.class) {
                if (o != null && !(o instanceof Instant)) {
                    if (o instanceof java.sql.Date) {
                        o = ((java.sql.Date) o).toInstant();
                    } else if (o instanceof java.sql.Time) {
                        o = ((java.sql.Time) o).toInstant();
                    } else if (o instanceof java.sql.Timestamp) {
                        o = ((java.sql.Timestamp) o).toInstant();
                    }
                }
            } else if (type == String.class) {
                if (o == null) {
                    o = "";
                } else if (o instanceof byte[]) {
                    o = new String((byte[]) o, StandardCharsets.UTF_8);
                } else {
                    o = o.toString();
                }
            } else if (o != null && !type.isAssignableFrom(o.getClass()) && o instanceof CharSequence) {
                o = ((CharSequence) o).length() == 0
                        ? null
                        : JsonConvert.root().convertFrom(genericType == null ? type : genericType, o.toString());
            }
        }
        return (Serializable) o;
    }

    public static <T> Serializable getRowColumnValue(
            DataResultSetRow row, Attribute<T, Serializable> attr, int index, String column) {
        final Class t = attr.type();
        if (t == byte[].class) {
            return (byte[]) (index > 0 ? row.getObject(index) : row.getObject(column));
        } else {
            Serializable o = (Serializable) (index > 0 ? row.getObject(index) : row.getObject(column));
            return formatColumnValue(t, attr.genericType(), o);
        }
    }
}
