/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.source.func;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.source.DataResultSetRow;
import org.redkale.source.EntityBuilder;
import org.redkale.source.EntityFullFunc;
import org.redkale.source.EntityInfo;

/**
 *
 * @author zhangjx
 */
public class EntityFullFuncTest {

    public static void main(String[] args) throws Throwable {
        EntityFullFuncTest test = new EntityFullFuncTest();
        test.run1();
        test.run2();
    }

    @Test
    public void run1() throws Exception {
        EntityFullFunc<FullBean> func = EntityBuilder.load(FullBean.class).getFullFunc();
        Assertions.assertTrue(func != null);
        FullBean bean = func.getObject(fullBeanRow);
        System.out.println(bean);
    }

    @Test
    public void run2() throws Exception {
        EntityFullFunc<FullBean2> func2 = EntityBuilder.load(FullBean2.class).getFullFunc();
        Assertions.assertTrue(func2 != null);
        EntityFullFunc<FullBean> func = EntityBuilder.load(FullBean.class).getFullFunc();
        FullBean bean = func.getObject(fullBeanRow);
        FullBean2 bean2 = func2.getObject(fullBeanRow);
        Assertions.assertEquals(bean.toString(), bean2.toString());
    }

    protected static final DataResultSetRow fullBeanRow = new DataResultSetRow() {
        @Override
        public EntityInfo getEntityInfo() {
            return null;
        }

        @Override
        public Object getObject(int columnIndex) {
            if (columnIndex < 1) throw new IllegalArgumentException();
            return null;
        }

        @Override
        public Object getObject(String columnLabel) {
            return null;
        }

        @Override
        public String getString(int columnIndex) {
            if (columnIndex < 1) throw new IllegalArgumentException();
            return "mystring";
        }

        @Override
        public String getString(String columnLabel) {
            return "mystring";
        }

        @Override
        public byte[] getBytes(int columnIndex) {
            if (columnIndex < 1) throw new IllegalArgumentException();
            return null;
        }

        @Override
        public byte[] getBytes(String columnLabel) {
            return null;
        }

        @Override
        public BigDecimal getBigDecimal(int columnIndex) {
            if (columnIndex < 1) throw new IllegalArgumentException();
            return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal getBigDecimal(String columnLabel) {
            return BigDecimal.ZERO;
        }

        @Override
        public Boolean getBoolean(int columnIndex) {
            if (columnIndex < 1) throw new IllegalArgumentException();
            return true;
        }

        @Override
        public Boolean getBoolean(String columnLabel) {
            return true;
        }

        @Override
        public Short getShort(int columnIndex) {
            if (columnIndex < 1) throw new IllegalArgumentException();
            return 111;
        }

        @Override
        public Short getShort(String columnLabel) {
            return 111;
        }

        @Override
        public Integer getInteger(int columnIndex) {
            if (columnIndex < 1) throw new IllegalArgumentException();
            return 222;
        }

        @Override
        public Integer getInteger(String columnLabel) {
            return 222;
        }

        @Override
        public Float getFloat(int columnIndex) {
            if (columnIndex < 1) throw new IllegalArgumentException();
            return 333.f;
        }

        @Override
        public Float getFloat(String columnLabel) {
            return 333.f;
        }

        @Override
        public Long getLong(int columnIndex) {
            if (columnIndex < 1) throw new IllegalArgumentException();
            return 444L;
        }

        @Override
        public Long getLong(String columnLabel) {
            return 444L;
        }

        @Override
        public Double getDouble(int columnIndex) {
            if (columnIndex < 1) throw new IllegalArgumentException();
            return 555.d;
        }

        @Override
        public Double getDouble(String columnLabel) {
            return 555.d;
        }

        @Override
        public boolean wasNull() {
            return false;
        }

        @Override
        public List<String> getColumnLabels() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    };
}
