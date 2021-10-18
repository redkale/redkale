/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import org.redkale.util.RedkaleClassLoader;

import java.io.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import static org.redkale.boot.Application.*;

/**
 * 用于定义错误码的注解  <br>
 * 结果码定义范围:  <br>
 *    // 10000001 - 19999999 预留给Redkale的核心包使用  <br>
 *    // 20000001 - 29999999 预留给Redkale的扩展包使用  <br>
 *    // 30000001 - 99999999 预留给Dev开发系统自身使用  <br>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@Inherited
@Documented
@Target({FIELD})
@Retention(RUNTIME)
@Repeatable(RetLabel.RetLabels.class)
public @interface RetLabel {

    String value();

    String locale() default "";

    @Inherited
    @Documented
    @Target({FIELD})
    @Retention(RUNTIME)
    @interface RetLabels {

        RetLabel[] value();
    }

    public static interface RetInfoTransfer extends BiFunction<Integer, String, String> {

    }

    public static abstract class RetLoader {

        public static Map<String, Map<Integer, String>> loadMap(Class clazz) {
            final Map<String, Map<Integer, String>> rets = new LinkedHashMap<>();
            ServiceLoader<RetInfoTransfer> loader = ServiceLoader.load(RetInfoTransfer.class);
            RedkaleClassLoader.putServiceLoader(RetInfoTransfer.class);
            Iterator<RetInfoTransfer> it = loader.iterator();
            RetInfoTransfer func = it.hasNext() ? it.next() : null;
            if (func != null) RedkaleClassLoader.putReflectionPublicConstructors(func.getClass(), func.getClass().getName());
            RedkaleClassLoader.putReflectionPublicFields(clazz.getName());
            for (Field field : clazz.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != int.class) continue;
                RetLabel[] infos = field.getAnnotationsByType(RetLabel.class);
                if (infos == null || infos.length == 0) continue;
                int value;
                try {
                    value = field.getInt(null);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    continue;
                }
                for (RetLabel info : infos) {
                    rets.computeIfAbsent(info.locale(), (k) -> new LinkedHashMap<>()).put(value, func == null ? info.value() : func.apply(value, info.value()));
                }
            }
            try {
                File propPath = new File(System.getProperty(RESNAME_APP_CONF, new File(System.getProperty(RESNAME_APP_HOME, ""), "conf").getPath()));
                if (propPath.isDirectory() && propPath.canRead()) {
                    final String prefix = clazz.getSimpleName().toLowerCase();
                    for (File propFile : propPath.listFiles(f -> f.getName().startsWith(prefix) && f.getName().endsWith(".properties"))) {
                        if (propFile.isFile() && propFile.canRead()) {
                            String locale = propFile.getName().substring(prefix.length()).replaceAll("\\.\\d+", "");
                            locale = locale.substring(0, locale.indexOf(".properties"));
                            Map<Integer, String> defrets = rets.get(locale);
                            if (defrets != null) {
                                InputStreamReader in = new InputStreamReader(new FileInputStream(propFile), StandardCharsets.UTF_8);
                                Properties prop = new Properties();
                                prop.load(in);
                                in.close();
                                prop.forEach((k, v) -> {
                                    int retcode = Integer.parseInt(k.toString());
                                    if (defrets.containsKey(retcode)) defrets.put(retcode, v.toString());
                                });
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return rets;
        }

//        @Deprecated
//        public static Map<Integer, String> load(Class clazz) {
//            return loadMap(clazz).computeIfAbsent("", (k) -> new LinkedHashMap<>());
//        }
    }
}
