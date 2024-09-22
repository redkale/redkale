/*

*/

package org.redkale.source;

import java.io.Serializable;
import java.util.List;
import org.redkale.annotation.ClassDepends;
import org.redkale.annotation.Nullable;
import org.redkale.util.Attribute;

/**
 *
 * @author zhangjx
 */
@ClassDepends
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
    @ClassDepends
    public String getString(int columnIdex);

    @ClassDepends
    public String getString(String columnLabel);

    // columnIdex从1开始
    @ClassDepends
    public byte[] getBytes(int columnIdex);

    @ClassDepends
    public byte[] getBytes(String columnLabel);

    // columnIdex从1开始
    @ClassDepends
    public Boolean getBoolean(int columnIdex);

    @ClassDepends
    public Boolean getBoolean(String columnLabel);

    // columnIdex从1开始
    @ClassDepends
    public Short getShort(int columnIdex);

    @ClassDepends
    public Short getShort(String columnLabel);

    // columnIdex从1开始
    @ClassDepends
    public Integer getInteger(int columnIdex);

    @ClassDepends
    public Integer getInteger(String columnLabel);

    // columnIdex从1开始
    @ClassDepends
    public Float getFloat(int columnIdex);

    @ClassDepends
    public Float getFloat(String columnLabel);

    // columnIdex从1开始
    @ClassDepends
    public Long getLong(int columnIdex);

    @ClassDepends
    public Long getLong(String columnLabel);

    // columnIdex从1开始
    @ClassDepends
    public Double getDouble(int columnIdex);

    @ClassDepends
    public Double getDouble(String columnLabel);

    // columnIdex从1开始
    @ClassDepends
    default boolean getBoolean(int columnIdex, boolean defValue) {
        Boolean val = getBoolean(columnIdex);
        return val == null ? defValue : val;
    }

    @ClassDepends
    default boolean getBoolean(String columnLabel, boolean defValue) {
        Boolean val = getBoolean(columnLabel);
        return val == null ? defValue : val;
    }

    // columnIdex从1开始
    @ClassDepends
    default short getShort(int columnIdex, short defValue) {
        Short val = getShort(columnIdex);
        return val == null ? defValue : val;
    }

    @ClassDepends
    default short getShort(String columnLabel, short defValue) {
        Short val = getShort(columnLabel);
        return val == null ? defValue : val;
    }

    // columnIdex从1开始
    @ClassDepends
    default int getInteger(int columnIdex, int defValue) {
        Integer val = getInteger(columnIdex);
        return val == null ? defValue : val;
    }

    @ClassDepends
    default int getInteger(String columnLabel, int defValue) {
        Integer val = getInteger(columnLabel);
        return val == null ? defValue : val;
    }

    // columnIdex从1开始
    @ClassDepends
    default float getFloat(int columnIdex, float defValue) {
        Float val = getFloat(columnIdex);
        return val == null ? defValue : val;
    }

    @ClassDepends
    default float getFloat(String columnLabel, float defValue) {
        Float val = getFloat(columnLabel);
        return val == null ? defValue : val;
    }

    // columnIdex从1开始
    @ClassDepends
    default long getLong(int columnIdex, long defValue) {
        Long val = getLong(columnIdex);
        return val == null ? defValue : val;
    }

    @ClassDepends
    default long getLong(String columnLabel, long defValue) {
        Long val = getLong(columnLabel);
        return val == null ? defValue : val;
    }

    // columnIdex从1开始
    @ClassDepends
    default double getDouble(int columnIdex, double defValue) {
        Double val = getDouble(columnIdex);
        return val == null ? defValue : val;
    }

    @ClassDepends
    default double getDouble(String columnLabel, double defValue) {
        Double val = getDouble(columnLabel);
        return val == null ? defValue : val;
    }

    /**
     * 判断当前行值是否为null
     *
     * @return boolean
     */
    @ClassDepends
    public boolean wasNull();

    /**
     * 获取字段名集合，尽量不要多次调用
     *
     * @return List
     */
    public List<String> getColumnLabels();
}
