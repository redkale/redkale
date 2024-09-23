/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.source;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Set;
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

    public String[] remarks;

    public byte[] img;

    public BigInteger number;

    public BigDecimal scale;

    public boolean flag;

    public short status;

    public int id;

    public long createTime;

    public float point;

    public double money;

    public byte bit;

    public Boolean flag2;

    public Short status2;

    public Integer id2;

    public Long createTime2;

    public Float point2;

    public Double money2;

    public Set<Integer> ids;

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
