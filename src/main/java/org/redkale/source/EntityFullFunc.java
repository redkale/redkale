/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.source;

import java.io.Serializable;
import org.redkale.util.Attribute;
import org.redkale.util.Creator;

/**
 * 可以是实体类，也可以是查询结果的JavaBean类
 *
 * @author zhangjx
 * @param <T> T
 * @since 2.8.0
 */
public abstract class EntityFullFunc<T> {

    protected final Class<T> type;

    protected final Creator<T> creator;

    protected final Attribute<T, Serializable>[] attrs;

    protected EntityFullFunc(Class<T> type, Creator<T> creator, Attribute<T, Serializable>[] attrs) {
        this.type = type;
        this.creator = creator;
        this.attrs = attrs;
    }

    public abstract T getObject(DataResultSetRow row);

    protected void setFieldValue(int attrIndex, DataResultSetRow row, T obj) {
        Attribute<T, Serializable> attr = attrs[attrIndex];
        if (attr != null) {
            attr.set(obj, row.getObject(attr, attrIndex + 1, null));
        }
    }

    public Class<T> getType() {
        return type;
    }

    public Creator<T> getCreator() {
        return creator;
    }

    public Attribute<T, Serializable>[] getAttrs() {
        return attrs;
    }

    public static <T> EntityFullFunc<T> create(Class<T> type, Creator<T> creator, Attribute<T, Serializable>[] attrs) {
        return null;
    }
}
