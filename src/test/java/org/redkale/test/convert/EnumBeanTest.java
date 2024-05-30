/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import org.junit.jupiter.api.*;
import org.redkale.convert.ConvertEnumValue;
import org.redkale.convert.json.JsonConvert;

/** @author zhangjx */
public class EnumBeanTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        EnumBeanTest test = new EnumBeanTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        EnumBean bean = new EnumBean();
        bean.v1 = EnumKey.TWO;
        bean.v2 = 5;
        String expect = "{\"v1\":2,\"v2\":5}";
        String json = JsonConvert.root().convertTo(bean);
        if (!main) Assertions.assertEquals(expect, json);
        System.out.println(json);
        EnumBean b = JsonConvert.root().convertFrom(EnumBean.class, json);
        String js = JsonConvert.root().convertTo(b);
        System.out.println(js);
        if (!main) Assertions.assertEquals(expect, js);
    }

    public static class EnumBean {

        public EnumKey v1;

        public int v2;
    }

    @ConvertEnumValue("code")
    public static enum EnumKey {
        ONE(1),
        TWO(2);

        private final int code;

        private EnumKey(int v) {
            this.code = v;
        }

        public int getCode() {
            return code;
        }
    }
}
