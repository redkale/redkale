/*

*/

package org.redkale.source;

import java.io.Serializable;
import java.math.BigDecimal;
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

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @return 字段值
     */
    public Object getObject(int columnIndex);

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @return  字段值
     */
    public Object getObject(String columnLabel);

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param <T> 泛型
     * @param attr  Attribute
     * @param columnIndex 字段序号
     * @param columnLabel 字段名
     * @return 字段值
     */
    default <T> Serializable getObject(Attribute<T, Serializable> attr, int columnIndex, String columnLabel) {
        return DataResultSet.getRowColumnValue(this, attr, columnIndex, columnLabel);
    }

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @return 字段值
     */
    @ClassDepends
    public String getString(int columnIndex);

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @return  字段值
     */
    @ClassDepends
    public String getString(String columnLabel);
    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @return 字段值
     */
    @ClassDepends
    public byte[] getBytes(int columnIndex);

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @return  字段值
     */
    @ClassDepends
    public byte[] getBytes(String columnLabel);

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @return 字段值
     */
    @ClassDepends
    public BigDecimal getBigDecimal(int columnIndex);

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @return  字段值
     */
    @ClassDepends
    public BigDecimal getBigDecimal(String columnLabel);

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @return 字段值
     */
    @ClassDepends
    public Boolean getBoolean(int columnIndex);

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @return  字段值
     */
    @ClassDepends
    public Boolean getBoolean(String columnLabel);

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @return 字段值
     */
    @ClassDepends
    public Short getShort(int columnIndex);

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @return  字段值
     */
    @ClassDepends
    public Short getShort(String columnLabel);

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @return 字段值
     */
    @ClassDepends
    public Integer getInteger(int columnIndex);

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @return  字段值
     */
    @ClassDepends
    public Integer getInteger(String columnLabel);

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @return 字段值
     */
    @ClassDepends
    public Float getFloat(int columnIndex);

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @return  字段值
     */
    @ClassDepends
    public Float getFloat(String columnLabel);

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @return 字段值
     */
    @ClassDepends
    public Long getLong(int columnIndex);

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @return  字段值
     */
    @ClassDepends
    public Long getLong(String columnLabel);

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @return 字段值
     */
    @ClassDepends
    public Double getDouble(int columnIndex);

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @return  字段值
     */
    @ClassDepends
    public Double getDouble(String columnLabel);

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @param defValue 默认值
     * @return 字段值
     */
    @ClassDepends
    default boolean getBoolean(int columnIndex, boolean defValue) {
        Boolean val = getBoolean(columnIndex);
        return val == null ? defValue : val;
    }

    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @param defValue 默认值
     * @return  字段值
     */
    @ClassDepends
    default boolean getBoolean(String columnLabel, boolean defValue) {
        Boolean val = getBoolean(columnLabel);
        return val == null ? defValue : val;
    }

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @param defValue 默认值
     * @return 字段值
     */
    @ClassDepends
    default short getShort(int columnIndex, short defValue) {
        Short val = getShort(columnIndex);
        return val == null ? defValue : val;
    }
    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @param defValue 默认值
     * @return  字段值
     */
    @ClassDepends
    default short getShort(String columnLabel, short defValue) {
        Short val = getShort(columnLabel);
        return val == null ? defValue : val;
    }

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @param defValue 默认值
     * @return 字段值
     */
    @ClassDepends
    default int getInteger(int columnIndex, int defValue) {
        Integer val = getInteger(columnIndex);
        return val == null ? defValue : val;
    }
    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @param defValue 默认值
     * @return  字段值
     */
    @ClassDepends
    default int getInteger(String columnLabel, int defValue) {
        Integer val = getInteger(columnLabel);
        return val == null ? defValue : val;
    }

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @param defValue 默认值
     * @return 字段值
     */
    @ClassDepends
    default float getFloat(int columnIndex, float defValue) {
        Float val = getFloat(columnIndex);
        return val == null ? defValue : val;
    }
    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @param defValue 默认值
     * @return  字段值
     */
    @ClassDepends
    default float getFloat(String columnLabel, float defValue) {
        Float val = getFloat(columnLabel);
        return val == null ? defValue : val;
    }

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @param defValue 默认值
     * @return 字段值
     */
    @ClassDepends
    default long getLong(int columnIndex, long defValue) {
        Long val = getLong(columnIndex);
        return val == null ? defValue : val;
    }
    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @param defValue 默认值
     * @return  字段值
     */
    @ClassDepends
    default long getLong(String columnLabel, long defValue) {
        Long val = getLong(columnLabel);
        return val == null ? defValue : val;
    }

    /**
     * 根据字段序号获取字段值， index从1开始
     *
     * @param columnIndex 字段序号
     * @param defValue 默认值
     * @return 字段值
     */
    @ClassDepends
    default double getDouble(int columnIndex, double defValue) {
        Double val = getDouble(columnIndex);
        return val == null ? defValue : val;
    }
    /**
     * 根据字段名获取字段值
     *
     * @param columnLabel 字段名
     * @param defValue 默认值
     * @return  字段值
     */
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
