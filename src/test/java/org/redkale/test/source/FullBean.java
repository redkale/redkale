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
public class FullBean {
    @Id
    private long seqid;

    private String name;

    private byte[] img;

    private BigInteger number;

    private boolean flag;

    private short status;

    private int id;

    private long createTime;

    private float point;

    private double money;

    private byte bit;

    private Boolean flag2;

    private Short status2;

    private Integer id2;

    private Long createTime2;

    private Float point2;

    private Double money2;

    public long getSeqid() {
        return seqid;
    }

    public void setSeqid(long seqid) {
        this.seqid = seqid;
    }

    public String getName() {
        return name;
    }

    public FullBean setName(String name) {
        this.name = name;
        return this;
    }

    public byte getBit() {
        return bit;
    }

    public void setBit(byte bit) {
        this.bit = bit;
    }

    public byte[] getImg() {
        return img;
    }

    public void setImg(byte[] img) {
        this.img = img;
    }

    public BigInteger getNumber() {
        return number;
    }

    public void setNumber(BigInteger number) {
        this.number = number;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public short getStatus() {
        return status;
    }

    public void setStatus(short status) {
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public float getPoint() {
        return point;
    }

    public void setPoint(float point) {
        this.point = point;
    }

    public double getMoney() {
        return money;
    }

    public void setMoney(double money) {
        this.money = money;
    }

    public Boolean getFlag2() {
        return flag2;
    }

    public void setFlag2(Boolean flag2) {
        this.flag2 = flag2;
    }

    public Short getStatus2() {
        return status2;
    }

    public void setStatus2(Short status2) {
        this.status2 = status2;
    }

    public Integer getId2() {
        return id2;
    }

    public void setId2(Integer id2) {
        this.id2 = id2;
    }

    public Long getCreateTime2() {
        return createTime2;
    }

    public void setCreateTime2(Long createTime2) {
        this.createTime2 = createTime2;
    }

    public Float getPoint2() {
        return point2;
    }

    public void setPoint2(Float point2) {
        this.point2 = point2;
    }

    public Double getMoney2() {
        return money2;
    }

    public void setMoney2(Double money2) {
        this.money2 = money2;
    }

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
