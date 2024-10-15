/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import java.lang.reflect.Type;
import org.junit.jupiter.api.*;
import org.redkale.convert.*;
import org.redkale.convert.json.*;

/** @author zhangjx */
@SuppressWarnings("unchecked")
public class ObjectOrStringTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        ObjectOrStringTest test = new ObjectOrStringTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        JsonConvert convert = JsonConvert.root();
        String json = "[\"aaaaa\",{\"id\":200,\"name\":\"haha\"}]";
        AbstractBean[] beans = convert.convertFrom(AbstractBean[].class, json);
        System.out.println(convert.convertTo(beans[0]));
        System.out.println(convert.convertTo(beans[1]));
        System.out.println(convert.convertTo(beans));
        if (!main) Assertions.assertEquals(json, convert.convertTo(beans));
    }

    public abstract static class AbstractBean {

        // 必须声明为private， 否则加载StringBean时配置Decoder会采用此方法
        private static Decodeable<JsonReader, AbstractBean> createDecoder(
                final org.redkale.convert.json.JsonFactory factory) {
            Decodeable<JsonReader, StringBean> stringDecoder = factory.loadDecoder(StringBean.class);
            Decodeable<JsonReader, ObjectBean> objectDecoder = factory.loadDecoder(ObjectBean.class);
            return new Decodeable<JsonReader, AbstractBean>() {
                @Override
                public AbstractBean convertFrom(JsonReader in) {
                    Decodeable coder = in.isNextObject() ? objectDecoder : stringDecoder;
                    return (AbstractBean) coder.convertFrom(in);
                }

                @Override
                public Type getType() {
                    return AbstractBean.class;
                }
            };
        }
    }

    public static class ObjectBean extends AbstractBean {

        private int id;

        private String name;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class StringBean extends AbstractBean {

        private String value;

        public StringBean() {}

        public StringBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        static SimpledCoder<Reader, Writer, StringBean> createConvertCoder(
                final org.redkale.convert.ConvertFactory factory) {
            return new SimpledCoder<Reader, Writer, StringBean>() {
                @Override
                public void convertTo(Writer out, StringBean val) {
                    out.writeString(val == null ? null : val.value);
                }

                @Override
                public StringBean convertFrom(Reader in) {
                    String val = in.readString();
                    return new StringBean(val);
                }
            };
        }
    }
}
