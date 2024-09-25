/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.convert.pb;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.json.JsonConvert;
import org.redkale.persistence.Id;

/**
 *
 * @author zhangjx
 */
public class UserBean {
    @Id
    @ConvertColumn(index = 1)
    private long seqid;

    @ConvertColumn(index = 2)
    private String name;

    @ConvertColumn(index = 3)
    private String[] remarks;

    @ConvertColumn(index = 4)
    private byte[] img;

    @ConvertColumn(index = 5)
    private BigInteger number;

    @ConvertColumn(index = 6)
    private BigDecimal scale;

    @ConvertColumn(index = 7)
    private boolean flag;

    @ConvertColumn(index = 8)
    private short status;

    @ConvertColumn(index = 9)
    private int id;

    @ConvertColumn(index = 10)
    private long createTime;

    @ConvertColumn(index = 11)
    private float point;

    @ConvertColumn(index = 12)
    private double money;

    @ConvertColumn(index = 13)
    private byte bit;

    @ConvertColumn(index = 14)
    private Boolean flag2;

    @ConvertColumn(index = 15)
    private Short status2;

    @ConvertColumn(index = 16)
    private Integer id2;

    @ConvertColumn(index = 17)
    private Long createTime2;

    @ConvertColumn(index = 18)
    private Float point2;

    @ConvertColumn(index = 19)
    private Double money2;

    @ConvertColumn(index = 20)
    private Set<Integer> ids;

    @ConvertColumn(index = 21)
    public int id3;

    @ConvertColumn(index = 22)
    public long createTime3;

    @ConvertColumn(index = 23)
    public float point3;

    @ConvertColumn(index = 24)
    public double money3;

    @ConvertColumn(index = 25)
    public byte bit3;

    @ConvertColumn(index = 26)
    private int[] id4;

    @ConvertColumn(index = 27)
    private long[] createTime4;

    @ConvertColumn(index = 28)
    private float[] point4;

    @ConvertColumn(index = 29)
    private double[] money4;

    @ConvertColumn(index = 30)
    private byte[] bit4;

    @ConvertColumn(index = 31)
    private Integer[] id5;

    @ConvertColumn(index = 32)
    private Long[] createTime5;

    @ConvertColumn(index = 33)
    private Float[] point5;

    @ConvertColumn(index = 34)
    private Double[] money5;

    @ConvertColumn(index = 35)
    private Byte[] bit5;

    @ConvertColumn(index = 36)
    private List<Integer> id6;

    @ConvertColumn(index = 37)
    private List<Long> createTime6;

    @ConvertColumn(index = 38)
    private List<Float> point6;

    @ConvertColumn(index = 39)
    private List<Double> money6;

    @ConvertColumn(index = 40)
    private List<Byte> bit6;

    @ConvertColumn(index = 41)
    private Map<String, String> map;

    @ConvertColumn(index = 42)
    public UserKind kind;

    public Map<String, String> getMap() {
        return map;
    }

    public void setMap(Map<String, String> map) {
        this.map = map;
    }

    public List<Integer> getId6() {
        return id6;
    }

    public void setId6(List<Integer> id6) {
        this.id6 = id6;
    }

    public List<Long> getCreateTime6() {
        return createTime6;
    }

    public void setCreateTime6(List<Long> createTime6) {
        this.createTime6 = createTime6;
    }

    public List<Float> getPoint6() {
        return point6;
    }

    public void setPoint6(List<Float> point6) {
        this.point6 = point6;
    }

    public List<Double> getMoney6() {
        return money6;
    }

    public void setMoney6(List<Double> money6) {
        this.money6 = money6;
    }

    public List<Byte> getBit6() {
        return bit6;
    }

    public void setBit6(List<Byte> bit6) {
        this.bit6 = bit6;
    }

    public int getId3() {
        return id3;
    }

    public void setId3(int id3) {
        this.id3 = id3;
    }

    public long getCreateTime3() {
        return createTime3;
    }

    public void setCreateTime3(long createTime3) {
        this.createTime3 = createTime3;
    }

    public float getPoint3() {
        return point3;
    }

    public void setPoint3(float point3) {
        this.point3 = point3;
    }

    public double getMoney3() {
        return money3;
    }

    public void setMoney3(double money3) {
        this.money3 = money3;
    }

    public byte getBit3() {
        return bit3;
    }

    public void setBit3(byte bit3) {
        this.bit3 = bit3;
    }

    public Integer[] getId5() {
        return id5;
    }

    public void setId5(Integer[] id5) {
        this.id5 = id5;
    }

    public Long[] getCreateTime5() {
        return createTime5;
    }

    public void setCreateTime5(Long[] createTime5) {
        this.createTime5 = createTime5;
    }

    public Float[] getPoint5() {
        return point5;
    }

    public void setPoint5(Float[] point5) {
        this.point5 = point5;
    }

    public Double[] getMoney5() {
        return money5;
    }

    public void setMoney5(Double[] money5) {
        this.money5 = money5;
    }

    public Byte[] getBit5() {
        return bit5;
    }

    public void setBit5(Byte[] bit5) {
        this.bit5 = bit5;
    }

    public int[] getId4() {
        return id4;
    }

    public void setId4(int[] id4) {
        this.id4 = id4;
    }

    public long[] getCreateTime4() {
        return createTime4;
    }

    public void setCreateTime4(long[] createTime4) {
        this.createTime4 = createTime4;
    }

    public float[] getPoint4() {
        return point4;
    }

    public void setPoint4(float[] point4) {
        this.point4 = point4;
    }

    public double[] getMoney4() {
        return money4;
    }

    public void setMoney4(double[] money4) {
        this.money4 = money4;
    }

    public byte[] getBit4() {
        return bit4;
    }

    public void setBit4(byte[] bit4) {
        this.bit4 = bit4;
    }

    public long getSeqid() {
        return seqid;
    }

    public void setSeqid(long seqid) {
        this.seqid = seqid;
    }

    public String getName() {
        return name;
    }

    public UserBean setName(String name) {
        this.name = name;
        return this;
    }

    public String[] getRemarks() {
        return remarks;
    }

    public void setRemarks(String[] remarks) {
        this.remarks = remarks;
    }

    public Set<Integer> getIds() {
        return ids;
    }

    public UserBean setIds(Set<Integer> ids) {
        this.ids = ids;
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

    public BigDecimal getScale() {
        return scale;
    }

    public void setScale(BigDecimal scale) {
        this.scale = scale;
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
