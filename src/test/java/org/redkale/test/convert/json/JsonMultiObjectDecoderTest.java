/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import org.junit.jupiter.api.*;
import org.redkale.convert.ConvertImpl;
import org.redkale.convert.json.JsonConvert;

/** @author zhangjx */
public class JsonMultiObjectDecoderTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        JsonMultiObjectDecoderTest test = new JsonMultiObjectDecoderTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        {
            String json = "{\"a0\":\"000\",\"a6\":\"666\"}";
            AbstractBean bean = JsonConvert.root().convertFrom(AbstractBean.class, json);
            Assertions.assertEquals(bean.getClass().getName(), Bean69.class.getName());
            System.out.println(bean);
        }
        {
            String json = "{\"a0\":\"000\",\"a2\":\"222\",\"a4\":\"444\"}";
            AbstractBean bean = JsonConvert.root().convertFrom(AbstractBean.class, json);
            Assertions.assertEquals(bean.getClass().getName(), Bean423.class.getName());
            System.out.println(bean);
        }
        {
            String json = "{\"a0\":\"000\",\"a6\":\"666\",\"a5\":\"555\"}";
            AbstractBean bean = JsonConvert.root().convertFrom(AbstractBean.class, json);
            Assertions.assertEquals(bean.getClass().getName(), Bean65.class.getName());
            System.out.println(bean);
        }
    }

    // [{"1", "2", "3"}, {"2", "3"}, {"4", "2", "3"}, {"6", "7", "8"}, {"6", "5"}, {"6", "9"}]
    @ConvertImpl(types = {Bean123.class, Bean23.class, Bean423.class, Bean678.class, Bean65.class, Bean69.class})
    public abstract static class AbstractBean {

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    public static class Bean123 extends AbstractBean {

        public String a1;

        public String a2;

        public String a3;
    }

    public static class Bean23 extends AbstractBean {

        public String a2;

        public String a3;
    }

    public static class Bean423 extends AbstractBean {

        public String a4;

        public String a2;

        public String a3;
    }

    public static class Bean678 extends AbstractBean {

        public String a6;

        public String a7;

        public String a8;
    }

    public static class Bean69 extends AbstractBean {

        public String a6;

        public String a9;

        public Bean69(String a9) {
            this.a9 = a9;
        }
    }

    public static class Bean65 extends AbstractBean {

        private String a6;

        private String a5;

        public Bean65(String a5) {
            this.a5 = a5;
        }

        public String getA6() {
            return a6;
        }

        public void setA6(String a6) {
            this.a6 = a6;
        }

        public String getA5() {
            return a5;
        }
    }
}
