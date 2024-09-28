/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.*;
import org.redkale.convert.ConvertImpl;
import org.redkale.convert.json.JsonConvert;

/** @author zhangjx */
public class ConvertImplTest {

    public static void main(String[] args) throws Throwable {
        ConvertImplTest test = new ConvertImplTest();
        test.run1();
    }

    @Test
    public void run1() throws Throwable {
        String json = "{'name':'hellow'}";
        OneEntity one = JsonConvert.root().convertFrom(OneEntity.class, json);
        Assertions.assertTrue(one instanceof OneImpl);
        byte[] bs = json.getBytes();
        one = JsonConvert.root().convertFrom(OneEntity.class, new ByteArrayInputStream(bs));
        Assertions.assertTrue(one instanceof OneImpl);
        one = JsonConvert.root().convertFrom(OneEntity.class, ByteBuffer.wrap(bs));
        Assertions.assertTrue(one instanceof OneImpl);
    }

    @ConvertImpl(OneImpl.class)
    public static interface OneEntity {

        public String getName();
    }

    public static class OneImpl implements OneEntity {

        private String name;

        @Override
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
