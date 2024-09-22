/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.source;

import java.math.BigInteger;
import org.redkale.convert.json.JsonConvert;
import org.redkale.persistence.Id;

/**
 *
 * @author zhangjx
 */
public class FullBean2 {
    @Id
    public long seqid;

    public String name;

    public byte[] img;

    public BigInteger number;

    public boolean flag;

    public short status;

    public int id;

    public long createTime;

    public float point;

    public double money;

    public Boolean flag2;

    public Short status2;

    public Integer id2;

    public Long createTime2;

    public Float point2;

    public Double money2;

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
