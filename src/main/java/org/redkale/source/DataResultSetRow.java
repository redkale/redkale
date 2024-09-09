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

    // index从1开始
    public Object getObject(int index);

    public Object getObject(String columnLabel);

    // index从1开始
    default <T> Serializable getObject(Attribute<T, Serializable> attr, int index, String columnLabel) {
        return DataResultSet.getRowColumnValue(this, attr, index, columnLabel);
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
