/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.inject;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.io.File;
import java.lang.annotation.*;
import java.lang.reflect.Field;
import org.junit.jupiter.api.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.inject.ResourceAnnotationLoader;
import org.redkale.inject.ResourceFactory;

/** @author zhangjx */
public class ResourceAnnotationTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        ResourceAnnotationTest test = new ResourceAnnotationTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        ResourceFactory factory = ResourceFactory.create();
        factory.register(new CustomConfProvider());
        InjectBean bean = new InjectBean();
        factory.inject(bean);
        if (!main) Assertions.assertEquals(new File("conf/test.xml").toString(), bean.conf.toString());
    }

    public static class CustomConfProvider implements ResourceAnnotationLoader<CustomConf> {

        @Override
        public void load(
                ResourceFactory factory,
                String srcResourceName,
                Object srcObj,
                CustomConf annotation,
                Field field,
                Object attachment) {
            try {
                field.set(srcObj, new File(annotation.path()));
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("对象是 src =" + srcObj + ", path=" + annotation.path());
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
