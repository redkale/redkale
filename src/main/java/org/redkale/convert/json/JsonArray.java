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
 * 常规json数组
 *
 * <p>详情见: https://redkale.org
 *
 * @see org.redkale.convert.json.JsonElement
 * @see org.redkale.convert.json.JsonObject
 * @see org.redkale.convert.json.JsonString
 * @author zhangjx
 * @since 2.8.0
 */
public class JsonArray extends ArrayList<Object> implements JsonElement {

    public JsonArray() {}

    public JsonArray(Collection collection) {
        super(collection);
    }

    public JsonArray(Object... array) {
        super(Arrays.asList(array));
    }

    public static JsonArray convertFrom(String text) {
        return convertFrom(Utility.charArray(text));
    }

    public static JsonArray convertFrom(char[] text) {
        return convertFrom(text, 0, text.length);
    }

    public static JsonArray convertFrom(char[] text, int offset, int length) {
        return JsonConvert.root().convertFrom(JsonArray.class, text, offset, length);
    }

    public static JsonArray create() {
        return new JsonArray();
    }

    public <T> List<T> toList(Type componentType) {
        Type listType = TypeToken.createParameterizedType(null, ArrayList.class, componentType);
        return (List)
                JsonConvert.root().convertFrom(listType, JsonConvert.root().convertTo(this));
    }

    public JsonArray append(Object value) {
        super.add(value);
        return this;
    }

    public JsonArray append(int index, Object value) {
        super.set(index, value);
        return this;
    }

    public boolean isNull(int index) {
        return get(index) == null;
    }

    public boolean isJsonObject(int index) {
        return get(index) instanceof JsonObject;
    }

    public boolean isJsonArray(int index) {
        return get(index) instanceof JsonArray;
    }

    public JsonObject getJsonObject(int index) {
        Object val = get(index);
        if (val instanceof JsonObject) {
            return (JsonObject) val;
        }
        if (val instanceof Map) {
            return new JsonObject((Map) val);
        }
        throw new RedkaleException("val [" + val + "] is not a valid JsonObject.");
    }

    public JsonArray getJsonArray(int index) {
        Object val = get(index);
        if (val instanceof JsonArray) {
            return (JsonArray) val;
        }
        if (val instanceof Collection) {
            return new JsonArray((Collection) val);
        }
        throw new RedkaleException("val [" + val + "] is not a valid JsonArray.");
    }

    public String getString(int index) {
        final Object val = get(index);
        if (val == null) {
            return null;
        }
        return val.toString();
    }

    public String getString(int index, String defValue) {
        final Object val = get(index);
        if (val == null) {
            return defValue;
        }
        return val.toString();
    }

    public BigDecimal getBigDecimal(int index) {
        final Object val = get(index);
        if (val == null) {
            return null;
        }
        if (val instanceof BigDecimal) {
            return (BigDecimal) val;
        }
        return new BigDecimal(val.toString());
    }

    public BigDecimal getBigDecimal(int index, BigDecimal defValue) {
        final Object val = get(index);
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

    public BigInteger getBigInteger(int index) {
        final Object val = get(index);
        if (val == null) {
            return null;
        }
        if (val instanceof BigInteger) {
            return (BigInteger) val;
        }
        return new BigInteger(val.toString());
    }

    public BigInteger getBigInteger(int index, BigInteger defValue) {
        final Object val = get(index);
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

    public Number getNumber(int index) {
        Object val = get(index);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return (Number) val;
        }
        return JsonObject.stringToNumber(val.toString());
    }

    public Number getNumber(int index, Number defValue) {
        Object val = get(index);
        if (val == null) {
            return defValue;
        }
        if (val instanceof Number) {
            return (Number) val;
        }
        try {
            return JsonObject.stringToNumber(val.toString());
        } catch (Exception e) {
            return defValue;
        }
    }

    public Double getDouble(int index) {
        final Object val = get(index);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return Double.parseDouble(val.toString());
    }

    public double getDouble(int index, double defValue) {
        final Object val = get(index);
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

    public Long getLong(int index) {
        final Object val = get(index);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return Long.parseLong(val.toString());
    }

    public long getLong(int index, long defValue) {
        final Object val = get(index);
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

    public Float getFloat(int index) {
        final Object val = get(index);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        return Float.parseFloat(val.toString());
    }

    public float getFloat(int index, float defValue) {
        final Object val = get(index);
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

    public Integer getInt(int index) {
        final Object val = get(index);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return Integer.parseInt(val.toString());
    }

    public int getInt(int index, int defValue) {
        final Object val = get(index);
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

    public Short getShort(int index) {
        final Object val = get(index);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).shortValue();
        }
        return Short.parseShort(val.toString());
    }

    public short getShort(int index, short defValue) {
        final Object val = get(index);
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

    public Byte getByte(int index) {
        final Object val = get(index);
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number) val).byteValue();
        }
        return Byte.parseByte(val.toString());
    }

    public byte getByte(int index, byte defValue) {
        final Object val = get(index);
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
    public Boolean getBoolean(int index) {
        final Object val = get(index);
        if (val == null) {
            return null;
        }
        return "true".equalsIgnoreCase(val.toString());
    }

    public boolean getBoolean(int index, boolean defValue) {
        final Object val = get(index);
        if (val == null) {
            return defValue;
        }
        return "true".equalsIgnoreCase(val.toString());
    }

    @Override
    @ConvertDisabled
    public final boolean isObject() {
        return false;
    }

    @Override
    @ConvertDisabled
    public final boolean isArray() {
        return true;
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
