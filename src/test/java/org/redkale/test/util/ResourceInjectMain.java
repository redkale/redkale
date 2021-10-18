/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.util;

import java.io.File;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.Field;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
public class ResourceInjectMain {

    public static void main(String[] args) throws Throwable {
        ResourceFactory factory = ResourceFactory.create();
        factory.register(new CustomConfLoader());
        InjectBean bean = new InjectBean();
        factory.inject(bean);
    }

    public static class CustomConfLoader implements ResourceInjectLoader<CustomConf> {

        @Override
        public void load(ResourceFactory factory, Object src, CustomConf annotation, Field field, Object attachment) {
            try {
                field.set(src, new File(annotation.path()));
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("对象是 src =" + src + ", path=" + annotation.path());
        }

        @Override
        public Class<CustomConf> annotationType() {
            return CustomConf.class;
        }

    }

    public static class InjectBean {

        @CustomConf(path = "conf/test.xml")
        public File conf;

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    @Documented
    @Target({FIELD})
    @Retention(RUNTIME)
    public static @interface CustomConf {

        String path();
    }

}
