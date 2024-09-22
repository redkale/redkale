/*

*/

package org.redkale.source;

import java.io.Serializable;
import java.util.List;
import org.redkale.annotation.Nullable;
import org.redkale.util.Attribute;

/**
 *
 * @author zhangjx
 */
public interface DataResultSetRow {

    // 可以为空
    public @Nullable EntityInfo getEntityInfo();

    // columnIdex从1开始
    public Object getObject(int columnIdex);

    public Object getObject(String columnLabel);

    // columnIdex从1开始
    default <T> Serializable getObject(Attribute<T, Serializable> attr, int columnIndex, String columnLabel) {
        return DataResultSet.getRowColumnValue(this, attr, columnIndex, columnLabel);
    }

    // columnIdex从1开始
    public String getString(int columnIdex);

    public String getString(String columnLabel);

    // columnIdex从1开始
    public byte[] getBytes(int columnIdex);

    public byte[] getBytes(String columnLabel);

    // columnIdex从1开始
    public Boolean getBoolean(int columnIdex);

    public Boolean getBoolean(String columnLabel);

    // columnIdex从1开始
    public Short getShort(int columnIdex);

    public Short getShort(String columnLabel);

    // columnIdex从1开始
    public Integer getInteger(int columnIdex);

    public Integer getInteger(String columnLabel);

    // columnIdex从1开始
    public Float getFloat(int columnIdex);

    public Float getFloat(String columnLabel);

    // columnIdex从1开始
    public Long getLong(int columnIdex);

    public Long getLong(String columnLabel);

    // columnIdex从1开始
    public Double getDouble(int columnIdex);

    public Double getDouble(String columnLabel);

    // columnIdex从1开始
    default boolean getBoolean(int columnIdex, boolean defValue) {
        Boolean val = getBoolean(columnIdex);
        return val == null ? defValue : val;
    }

    default boolean getBoolean(String columnLabel, boolean defValue) {
        Boolean val = getBoolean(columnLabel);
        return val == null ? defValue : val;
    }

    // columnIdex从1开始
    default short getShort(int columnIdex, short defValue) {
        Short val = getShort(columnIdex);
        return val == null ? defValue : val;
    }

    default short getShort(String columnLabel, short defValue) {
        Short val = getShort(columnLabel);
        return val == null ? defValue : val;
    }

    // columnIdex从1开始
    default int getInteger(int columnIdex, int defValue) {
        Integer val = getInteger(columnIdex);
        return val == null ? defValue : val;
    }

    default int getInteger(String columnLabel, int defValue) {
        Integer val = getInteger(columnLabel);
        return val == null ? defValue : val;
    }

    // columnIdex从1开始
    default float getFloat(int columnIdex, float defValue) {
        Float val = getFloat(columnIdex);
        return val == null ? defValue : val;
    }

    default float getFloat(String columnLabel, float defValue) {
        Float val = getFloat(columnLabel);
        return val == null ? defValue : val;
    }

    // columnIdex从1开始
    default long getLong(int columnIdex, long defValue) {
        Long val = getLong(columnIdex);
        return val == null ? defValue : val;
    }

    default long getLong(String columnLabel, long defValue) {
        Long val = getLong(columnLabel);
        return val == null ? defValue : val;
    }

    // columnIdex从1开始
    default double getDouble(int columnIdex, double defValue) {
        Double val = getDouble(columnIdex);
        return val == null ? defValue : val;
    }

    default double getDouble(String columnLabel, double defValue) {
        Double val = getDouble(columnLabel);
        return val == null ? defValue : val;
    }

    /**
     * 判断当前行值是否为null
     *
     * @return boolean
     */
    public boolean wasNull();

    /**
     * 获取字段名集合，尽量不要多次调用
     *
     * @return List
     */
    public List<String> getColumnLabels();
}
