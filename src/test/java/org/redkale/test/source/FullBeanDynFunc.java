/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.source;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
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
        rs.setScale(row.getBigDecimal(5));
        setFieldValue(6, row, rs); // bit: Byte

        rs.setFlag(row.getBoolean(7, false));
        rs.setStatus(row.getShort(8, (short) 0));
        rs.setId(row.getInteger(9, 0));
        rs.setCreateTime(row.getLong(10, 0));
        rs.setPoint(row.getFloat(11, 0f));
        rs.setMoney(row.getDouble(12, 0d));

        rs.setFlag2(row.getBoolean(13));
        rs.setStatus2(row.getShort(14));
        rs.setId2(row.getInteger(15));
        rs.setCreateTime2(row.getLong(16));
        rs.setPoint2(row.getFloat(17));
        rs.setMoney2(row.getDouble(18));
        return rs;
    }

    @Override
    public FullBean getObject(Serializable... values) {
        FullBean rs = creator.create();
        rs.setSeqid((Long) values[0]);
        rs.setName((String) values[1]);
        rs.setImg((byte[]) values[2]);
        rs.setNumber((BigInteger) values[3]);
        rs.setScale((BigDecimal) values[4]);
        rs.setBit((Byte) values[5]);

        rs.setFlag((Boolean) values[6]);
        rs.setStatus((Short) values[7]);
        rs.setId((Integer) values[8]);
        rs.setCreateTime((Long) values[9]);
        rs.setPoint((Float) values[10]);
        rs.setMoney((Double) values[11]);

        rs.setFlag2((Boolean) values[12]);
        rs.setStatus2((Short) values[13]);
        rs.setId2((Integer) values[14]);
        rs.setCreateTime2((Long) values[15]);
        rs.setPoint2((Float) values[16]);
        rs.setMoney2((Double) values[17]);
        return rs;
    }
}
