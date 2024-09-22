/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.source;

import java.io.Serializable;
import org.redkale.source.DataResultSetRow;
import org.redkale.source.EntityFullFunc;
import org.redkale.util.Attribute;
import org.redkale.util.Creator;

/**
 *
 * @author zhangjx
 */
public class FullBeanDynFunc extends EntityFullFunc<FullBean> {

    public FullBeanDynFunc(Class<FullBean> type, Creator<FullBean> creator, Attribute<FullBean, Serializable>[] attrs) {
        super(type, creator, attrs);
    }

    @Override
    public FullBean getObject(DataResultSetRow row) {
        if (row.wasNull()) {
            return null;
        }
        FullBean rs = creator.create();
        rs.setSeqid(row.getLong(1, 0));
        rs.setName(row.getString(2));
        rs.setImg(row.getBytes(3));

        setFieldValue(4, row, rs); // number: BigInteger

        rs.setFlag(row.getBoolean(5, false));
        rs.setStatus(row.getShort(6, (short) 0));
        rs.setId(row.getInteger(7, 0));
        rs.setCreateTime(row.getLong(8, 0));
        rs.setPoint(row.getFloat(9, 0f));
        rs.setMoney(row.getDouble(10, 0d));

        rs.setFlag2(row.getBoolean(5));
        rs.setStatus2(row.getShort(6));
        rs.setId2(row.getInteger(7));
        rs.setCreateTime2(row.getLong(8));
        rs.setPoint2(row.getFloat(9));
        rs.setMoney2(row.getDouble(10));
        return rs;
    }
}
