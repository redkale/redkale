/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot;

import java.util.List;
import org.redkale.annotation.Serial;
import org.redkale.boot.ClassFilter.FilterEntry;
import org.redkale.convert.Decodeable;
import org.redkale.convert.json.*;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.persistence.Entity;
import org.redkale.source.*;
import org.redkale.util.Utility;

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
        // 必须设置redkale.resource.skip.check=true
        // 因redkale-maven-plugin的maven-core依赖jsr250，会覆盖redkale的javax.annotation.Resource导致无法识别Resource.required方法
        System.setProperty("redkale.resource.skip.check", "true");
        final Application application = new Application(AppConfig.create(false, true));
        application.init();
        application.onPreCompile();
        application.start();
        final boolean hasSncp = application.getNodeServers().stream()
                .filter(NodeSncpServer.class::isInstance)
                .findFirst()
                .isPresent();

        final ClassFilter<?> entityFilter = new ClassFilter(application.getClassLoader(), Entity.class, Object.class);
        final ClassFilter<?> entityFilter2 =
                new ClassFilter(application.getClassLoader(), javax.persistence.Entity.class, Object.class);
        final ClassFilter<?> serialFilter = new ClassFilter(application.getClassLoader(), Serial.class, Object.class);
        final ClassFilter<?> serialFilter2 =
                new ClassFilter(application.getClassLoader(), org.redkale.util.Bean.class, Object.class);
        final ClassFilter<?> filterFilter = new ClassFilter(application.getClassLoader(), null, FilterBean.class);

        application.loadClassByFilters(entityFilter, serialFilter, filterFilter);

        for (FilterEntry en : entityFilter.getFilterEntrys()) {
            Class clz = en.getType();
            if (Utility.isAbstractOrInterface(clz)) {
                continue;
            }
            try {
                List<DataSource> dataSources = application.getResourceFactory().query(DataSource.class);
                dataSources.forEach(source -> source.compile(clz));
                // application.dataSources.forEach(source -> source.compile(clz));
                JsonFactory.root().loadEncoder(clz);
                if (hasSncp) {
                    ProtobufFactory.root().loadEncoder(clz);
                }
                Decodeable decoder = JsonFactory.root().loadDecoder(clz);
                if (hasSncp) {
                    ProtobufFactory.root().loadDecoder(clz);
                }
                decoder.convertFrom(new JsonReader("{}"));
            } catch (Exception e) { // JsonFactory.loadDecoder可能会失败，因为class可能包含抽象类字段,如ColumnValue.value字段
            }
        }
        for (FilterEntry en : entityFilter2.getFilterEntrys()) {
            Class clz = en.getType();
            if (Utility.isAbstractOrInterface(clz)) {
                continue;
            }
            try {
                List<DataSource> dataSources = application.getResourceFactory().query(DataSource.class);
                dataSources.forEach(source -> source.compile(clz));
                // application.dataSources.forEach(source -> source.compile(clz));
                JsonFactory.root().loadEncoder(clz);
                if (hasSncp) {
                    ProtobufFactory.root().loadEncoder(clz);
                }
                Decodeable decoder = JsonFactory.root().loadDecoder(clz);
                if (hasSncp) {
                    ProtobufFactory.root().loadDecoder(clz);
                }
                decoder.convertFrom(new JsonReader("{}"));
            } catch (Exception e) { // JsonFactory.loadDecoder可能会失败，因为class可能包含抽象类字段,如ColumnValue.value字段
            }
        }
        for (FilterEntry en : serialFilter.getFilterEntrys()) {
            Class clz = en.getType();
            if (Utility.isAbstractOrInterface(clz)) {
                continue;
            }
            try {
                JsonFactory.root().loadEncoder(clz);
                if (hasSncp) {
                    ProtobufFactory.root().loadEncoder(clz);
                }
                Decodeable decoder = JsonFactory.root().loadDecoder(clz);
                if (hasSncp) {
                    ProtobufFactory.root().loadDecoder(clz);
                }
                decoder.convertFrom(new JsonReader("{}"));
            } catch (Exception e) { // JsonFactory.loadDecoder可能会失败，因为class可能包含抽象类字段,如ColumnValue.value字段
            }
        }
        for (FilterEntry en : serialFilter2.getFilterEntrys()) {
            Class clz = en.getType();
            if (Utility.isAbstractOrInterface(clz)) {
                continue;
            }
            try {
                JsonFactory.root().loadEncoder(clz);
                if (hasSncp) {
                    ProtobufFactory.root().loadEncoder(clz);
                }
                Decodeable decoder = JsonFactory.root().loadDecoder(clz);
                if (hasSncp) {
                    ProtobufFactory.root().loadDecoder(clz);
                }
                decoder.convertFrom(new JsonReader("{}"));
            } catch (Exception e) { // JsonFactory.loadDecoder可能会失败，因为class可能包含抽象类字段,如ColumnValue.value字段
            }
        }
        for (FilterEntry en : filterFilter.getFilterEntrys()) {
            Class clz = en.getType();
            if (Utility.isAbstractOrInterface(clz)) {
                continue;
            }
            try {
                FilterNodeBean.load(clz);
            } catch (Exception e) {
                // do nothing
            }
        }
        application.onPostCompile();
        application.shutdown();
        return application;
    }
}
