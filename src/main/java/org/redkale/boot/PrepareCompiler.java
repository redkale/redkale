/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.lang.reflect.Modifier;
import org.redkale.annotation.Bean;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.convert.Decodeable;
import org.redkale.convert.bson.BsonFactory;
import org.redkale.convert.json.*;
import org.redkale.persistence.Entity;
import org.redkale.source.*;

/**
 * 执行一次Application.run提前获取所有动态类
 *
 * @author zhangjx
 * @since 2.5.0
 */
public class PrepareCompiler {

//    public static void main(String[] args) throws Exception {
//        new PrepareCompiler().run();
//    }
    public Application run() throws Exception {
        //必须设置redkale.resource.skip.check=true
        //因redkale-maven-plugin的maven-core依赖jsr250，会覆盖redkale的javax.annotation.Resource导致无法识别Resource.required方法
        System.setProperty("redkale.resource.skip.check", "true");
        final Application application = new Application(false, true, Application.loadAppConfig());
        application.init();
        for (ApplicationListener listener : application.listeners) {
            listener.preStart(application);
        }
        for (ApplicationListener listener : application.listeners) {
            listener.preCompile(application);
        }
        application.start();
        final boolean hasSncp = application.getNodeServers().stream().filter(v -> v instanceof NodeSncpServer).findFirst().isPresent();
        final String[] exlibs = (application.excludelibs != null ? (application.excludelibs + ";") : "").split(";");

        final ClassFilter<?> entityFilter = new ClassFilter(application.getClassLoader(), Entity.class, Object.class, (Class[]) null);
        final ClassFilter<?> entityFilter2 = new ClassFilter(application.getClassLoader(), javax.persistence.Entity.class, Object.class, (Class[]) null);
        final ClassFilter<?> beanFilter = new ClassFilter(application.getClassLoader(), Bean.class, Object.class, (Class[]) null);
        final ClassFilter<?> beanFilter2 = new ClassFilter(application.getClassLoader(), org.redkale.util.Bean.class, Object.class, (Class[]) null);
        final ClassFilter<?> filterFilter = new ClassFilter(application.getClassLoader(), null, FilterBean.class, (Class[]) null);

        ClassFilter.Loader.load(application.getHome(), application.getClassLoader(), exlibs, entityFilter, beanFilter, filterFilter);

        for (FilterEntry en : entityFilter.getFilterEntrys()) {
            Class clz = en.getType();
            if (clz.isInterface() || Modifier.isAbstract(clz.getModifiers())) continue;
            try {
                application.dataSources.forEach(source -> source.compile(clz));
                JsonFactory.root().loadEncoder(clz);
                if (hasSncp) BsonFactory.root().loadEncoder(clz);
                Decodeable decoder = JsonFactory.root().loadDecoder(clz);
                if (hasSncp) BsonFactory.root().loadDecoder(clz);
                decoder.convertFrom(new JsonReader("{}"));
            } catch (Exception e) { //JsonFactory.loadDecoder可能会失败，因为class可能包含抽象类字段,如ColumnValue.value字段
            }
        }
        for (FilterEntry en : entityFilter2.getFilterEntrys()) {
            Class clz = en.getType();
            if (clz.isInterface() || Modifier.isAbstract(clz.getModifiers())) continue;
            try {
                application.dataSources.forEach(source -> source.compile(clz));
                JsonFactory.root().loadEncoder(clz);
                if (hasSncp) BsonFactory.root().loadEncoder(clz);
                Decodeable decoder = JsonFactory.root().loadDecoder(clz);
                if (hasSncp) BsonFactory.root().loadDecoder(clz);
                decoder.convertFrom(new JsonReader("{}"));
            } catch (Exception e) { //JsonFactory.loadDecoder可能会失败，因为class可能包含抽象类字段,如ColumnValue.value字段
            }
        }
        for (FilterEntry en : beanFilter.getFilterEntrys()) {
            Class clz = en.getType();
            if (clz.isInterface() || Modifier.isAbstract(clz.getModifiers())) continue;
            try {
                JsonFactory.root().loadEncoder(clz);
                if (hasSncp) BsonFactory.root().loadEncoder(clz);
                Decodeable decoder = JsonFactory.root().loadDecoder(clz);
                if (hasSncp) BsonFactory.root().loadDecoder(clz);
                decoder.convertFrom(new JsonReader("{}"));
            } catch (Exception e) { //JsonFactory.loadDecoder可能会失败，因为class可能包含抽象类字段,如ColumnValue.value字段
            }
        }
        for (FilterEntry en : beanFilter2.getFilterEntrys()) {
            Class clz = en.getType();
            if (clz.isInterface() || Modifier.isAbstract(clz.getModifiers())) continue;
            try {
                JsonFactory.root().loadEncoder(clz);
                if (hasSncp) BsonFactory.root().loadEncoder(clz);
                Decodeable decoder = JsonFactory.root().loadDecoder(clz);
                if (hasSncp) BsonFactory.root().loadDecoder(clz);
                decoder.convertFrom(new JsonReader("{}"));
            } catch (Exception e) { //JsonFactory.loadDecoder可能会失败，因为class可能包含抽象类字段,如ColumnValue.value字段
            }
        }
        for (FilterEntry en : filterFilter.getFilterEntrys()) {
            Class clz = en.getType();
            if (clz.isInterface() || Modifier.isAbstract(clz.getModifiers())) continue;
            try {
                FilterNodeBean.load(clz);
            } catch (Exception e) {
            }
        }
        for (ApplicationListener listener : application.listeners) {
            listener.postCompile(application);
        }
        application.shutdown();
        return application;
    }
}
