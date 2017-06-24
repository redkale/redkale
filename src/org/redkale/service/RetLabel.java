/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.service;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.text.MessageFormat;
import java.util.*;

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
@Target(value = {ElementType.FIELD})
@Retention(value = RetentionPolicy.RUNTIME)
public @interface RetLabel {

    String value();

    public static class RetCode {

        protected final Map<Integer, String> rets = new HashMap();

        protected final Class clazz;

        protected boolean inited;

        public RetCode(Class clazz) {
            this.clazz = clazz;
        }

        public RetResult retResult(int retcode) {
            if (retcode == 0) return RetResult.success();
            return new RetResult(retcode, retInfo(retcode));
        }

        public RetResult retResult(int retcode, Object... args) {
            if (retcode == 0) return RetResult.success();
            if (args == null || args.length < 1) return new RetResult(retcode, retInfo(retcode));
            String info = MessageFormat.format(retInfo(retcode), args);
            return new RetResult(retcode, info);
        }

        public RetResult set(RetResult result, int retcode, Object... args) {
            if (retcode == 0) return result.retcode(0).retinfo("");
            if (args == null || args.length < 1) return result.retcode(retcode).retinfo(retInfo(retcode));
            String info = MessageFormat.format(retInfo(retcode), args);
            return result.retcode(retcode).retinfo(info);
        }

        public String retInfo(int retcode) {
            if (retcode == 0) return "成功";
            if (!this.inited) {
                synchronized (this) {
                    if (!this.inited) {
                        rets.putAll(RetLoader.load(clazz));
                    }
                    this.inited = true;
                }
            }
            return rets.getOrDefault(retcode, "未知错误");
        }
    }

    public static abstract class RetLoader {

        public static Map<Integer, String> load(Class clazz) {
            final Map<Integer, String> rets = new HashMap<>();
            for (Field field : clazz.getFields()) {
                if (!Modifier.isStatic(field.getModifiers())) continue;
                if (field.getType() != int.class) continue;
                RetLabel info = field.getAnnotation(RetLabel.class);
                if (info == null) continue;
                int value;
                try {
                    value = field.getInt(null);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    continue;
                }
                rets.put(value, info.value());
            }
            return rets;
        }
    }
}
