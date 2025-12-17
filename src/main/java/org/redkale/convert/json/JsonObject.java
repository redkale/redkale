/*
 *
 */
package org.redkale.convert.json;

import java.lang.reflect.Type;
import java.math.*;
import java.util.*;
import org.redkale.annotation.Nullable;
import org.redkale.convert.ConvertDisabled;
import org.redkale.util.*;

/**
 * 常规json对象
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.convert.json.JsonElement
 * @see org.redkale.convert.json.JsonString
 * @see org.redkale.convert.json.JsonArray
 * @author zhangjx
 * @since 2.8.0
 */
public class JsonObject extends LinkedHashMap<String, Object> implements JsonElement {

    public JsonObject() {}

    public JsonObject(Map map) {
        super(map);
    }

    public static JsonObject convertFrom(String text) {
        return convertFrom(Utility.charArray(text));
    }

    public static JsonObject convertFrom(char[] text) {
        return convertFrom(text, 0, text.length);
    }

    public static JsonObject convertFrom(char[] text, int offset, int length) {
        return JsonConvert.root().convertFrom(JsonObject.class, text, offset, length);
    }

    public static JsonObject create() {
        return new JsonObject();
    }

    public static JsonObject of(Object bean) {
        if (bean instanceof CharSequence) {
            return convertFrom(bean.toString());
        }
        if (bean instanceof JsonObject) {
            return (JsonObject) bean;
        }
        if (bean instanceof Map) {
            return new JsonObject((Map) bean);
        }
        return convertFrom(JsonConvert.root().convertTo(bean));
    }

    @Deprecated(since = "2.8.1")
    public <T> T toObject(Type type) {
        return cast(type);
    }

    public <T> T cast(Type type) {
        return (T) JsonConvert.root().convertFrom(type, JsonConvert.root().convertTo(this));
    }

    public JsonObject append(String key, Object value) {
        super.put(key, value);
        return this;
    }

    public JsonObject append(String key, Collection value) {
        super.put(key, value == null || value instanceof JsonArray ? value : new JsonArray(value));
        return this;
    }

    public JsonObject putObject(String key) {
        JsonObject val = new JsonObject();
        super.put(key, val);
        return val;
    }

    public JsonArray putArray(String key) {
        JsonArray val = new JsonArray();
        super.put(key, val);
        return val;
    }

    public boolean has(String key) {
        return containsKey(key);
    }

    public boolean isNull(String key) {
        return get(key) == null;
    }

    public boolean isJsonObject(String key) {
        return get(key) instanceof JsonObject;
    }

    public boolean isJsonArray(String key) {
        return get(key) instanceof JsonArray;
    }

    public JsonObject getJsonObject(String key) {
        Object val = get(key);
        if (val instanceof JsonObject) {
            return (JsonObject) val;
        }
        if (val instanceof Map) {
            return new JsonObject((Map) val);
        }
        throw new RedkaleException("val [" + val + "] is not a valid JsonObject.");
    }

    public JsonArray getJsonArray(String key) {
        Object val = get(key);
        if (val instanceof JsonArray) {
            return (JsonArray) val;
        }
        if (val instanceof Collection) {
            return new JsonArray((Collection) val);
        }
        throw new RedkaleException("val [" + val + "] is not a valid JsonArray.");
    }

    public String getString(String key) {
        final Object val = get(key);
        if (val == null) {
            return null;
        }
        return val.toString();
    }

    public String getString(String key, String defValue) {
        final Object val = get(key);
        if (val == null) {
            return defValue;
        }
        return val.toString();
    }

    public BigDecimal getBigDecimal(String key) {
        final Object val = get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        return new BigDecimal(val.toString());
    }

    public BigDecimal getBigDecimal(String key, BigDecimal defValue) {
        final Object val = get(key);
        if (val == null) {
            return defValue;
        }
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return defValue;
        }
    }

    public BigInteger getBigInteger(String key) {
        final Object val = get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof BigInteger) {
            return (BigInteger) val;
        }
        return new BigInteger(val.toString());
    }

    public BigInteger getBigInteger(String key, BigInteger defValue) {
        final Object val = get(key);
        if (val == null) {
            return defValue;
        }
        if (val instanceof BigInteger) {
            return (BigInteger) val;
        }
        try {
            return new BigInteger(val.toString());
        } catch (Exception e) {
            return defValue;
        }
    }

    public Number getNumber(String key) {
        Object val = get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return (Number) val;
        }
        return stringToNumber(val.toString());
    }

    public Number getNumber(String key, Number defValue) {
        Object val = get(key);
        if (val == null) {
            return defValue;
        }
        if (val instanceof Number) {
            return (Number) val;
        }
        try {
            return stringToNumber(val.toString());
        } catch (Exception e) {
            return defValue;
        }
    }

    public Double getDouble(String key) {
        final Object val = get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return Double.parseDouble(val.toString());
    }

    public double getDouble(String key, double defValue) {
        final Object val = get(key);
        if (val == null) {
            return defValue;
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return defValue;
        }
    }

    public Long getLong(String key) {
        final Object val = get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return Long.parseLong(val.toString());
    }

    public long getLong(String key, long defValue) {
        final Object val = get(key);
        if (val == null) {
            return defValue;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return defValue;
        }
    }

    public Float getFloat(String key) {
        final Object val = get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        return Float.parseFloat(val.toString());
    }

    public float getFloat(String key, float defValue) {
        final Object val = get(key);
        if (val == null) {
            return defValue;
        }
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        try {
            return Float.parseFloat(val.toString());
        } catch (Exception e) {
            return defValue;
        }
    }

    public Integer getInt(String key) {
        final Object val = get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return Integer.parseInt(val.toString());
    }

    public int getInt(String key, int defValue) {
        final Object val = get(key);
        if (val == null) {
            return defValue;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(val.toString());
        } catch (Exception e) {
            return defValue;
        }
    }

    public Short getShort(String key) {
        final Object val = get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).shortValue();
        }
        return Short.parseShort(val.toString());
    }

    public short getShort(String key, short defValue) {
        final Object val = get(key);
        if (val == null) {
            return defValue;
        }
        if (val instanceof Number) {
            return ((Number) val).shortValue();
        }
        try {
            return Short.parseShort(val.toString());
        } catch (Exception e) {
            return defValue;
        }
    }

    public Byte getByte(String key) {
        final Object val = get(key);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).byteValue();
        }
        return Byte.parseByte(val.toString());
    }

    public byte getByte(String key, byte defValue) {
        final Object val = get(key);
        if (val == null) {
            return defValue;
        }
        if (val instanceof Number) {
            return ((Number) val).byteValue();
        }
        try {
            return Byte.parseByte(val.toString());
        } catch (Exception e) {
            return defValue;
        }
    }

    @Nullable
    public Boolean getBoolean(String key) {
        final Object val = get(key);
        if (val == null) {
            return null;
        }
        return "true".equalsIgnoreCase(val.toString());
    }

    public boolean getBoolean(String key, boolean defValue) {
        final Object val = get(key);
        if (val == null) {
            return defValue;
        }
        return "true".equalsIgnoreCase(val.toString());
    }

    protected static Number stringToNumber(String val) {
        char initial = val.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            // decimal representation
            if (val.indexOf('.') > -1 || val.indexOf('e') > -1 || val.indexOf('E') > -1 || "-0".equals(val)) {
                // Use a BigDecimal all the time so we keep the original
                // representation. BigDecimal doesn't support -0.0, ensure we
                // keep that by forcing a decimal.
                try {
                    BigDecimal bd = new BigDecimal(val);
                    if (initial == '-' && BigDecimal.ZERO.compareTo(bd) == 0) {
                        return -0.0;
                    }
                    return bd;
                } catch (NumberFormatException retryAsDouble) {
                    // this is to support "Hex Floats" like this: 0x1.0P-1074
                    try {
                        Double d = Double.valueOf(val);
                        if (d.isNaN() || d.isInfinite()) {
                            throw new NumberFormatException("val [" + val + "] is not a valid number.");
                        }
                        return d;
                    } catch (NumberFormatException ignore) {
                        throw new NumberFormatException("val [" + val + "] is not a valid number.");
                    }
                }
            }
            // block items like 00 01 etc. Java number parsers treat these as Octal.
            if (initial == '0' && val.length() > 1) {
                char at1 = val.charAt(1);
                if (at1 >= '0' && at1 <= '9') {
                    throw new NumberFormatException("val [" + val + "] is not a valid number.");
                }
            } else if (initial == '-' && val.length() > 2) {
                char at1 = val.charAt(1);
                char at2 = val.charAt(2);
                if (at1 == '0' && at2 >= '0' && at2 <= '9') {
                    throw new NumberFormatException("val [" + val + "] is not a valid number.");
                }
            }
            // integer representation.
            // This will narrow any values to the smallest reasonable Object representation
            // (Integer, Long, or BigInteger)

            // BigInteger down conversion: We use a similar bitLength compare as
            // BigInteger#intValueExact uses. Increases GC, but objects hold
            // only what they need. i.e. Less runtime overhead if the value is
            // long lived.
            BigInteger bi = new BigInteger(val);
            if (bi.bitLength() <= 31) {
                return bi.intValue();
            }
            if (bi.bitLength() <= 63) {
                return bi.longValue();
            }
            return bi;
        }
        return null;
    }

    @Override
    @ConvertDisabled
    public final boolean isObject() {
        return true;
    }

    @Override
    @ConvertDisabled
    public final boolean isArray() {
        return false;
    }

    @Override
    @ConvertDisabled
    public final boolean isString() {
        return false;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
