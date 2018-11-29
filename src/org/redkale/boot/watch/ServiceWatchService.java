/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot.watch;

import java.lang.reflect.*;
import java.util.*;
import javax.annotation.Resource;
import org.redkale.boot.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;
import org.redkale.util.*;

/**
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@RestService(name = "service", catalog = "watch", repair = false)
public class ServiceWatchService extends AbstractWatchService {

    @Comment("没有找到目标Service")
    public static final int RET_SERVICE_DEST_NOT_EXISTS = 1603_0001;

    @Resource
    protected Application application;

    @RestConvert(type = void.class)
    @RestMapping(name = "setfield", auth = false, comment = "设置Service中指定字段的内容")
    public RetResult setfield(@RestParam(name = "name", comment = "Service的资源名") String name,
        @RestParam(name = "type", comment = "Service的类名") String type,
        @RestParam(name = "field", comment = "字段名") String field,
        @RestParam(name = "value", comment = "字段值") String value) {
        if (name == null) name = "";
        if (type == null) type = "";
        if (field == null) field = "";
        type = type.trim();
        field = field.trim();
        if (type.isEmpty()) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param `type`");
        if (field.isEmpty()) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param `field`");
        Object dest = findService(name, type);
        Class clazz = dest.getClass();
        Throwable t = null;
        try {
            Field fieldObj = null;
            do {
                try {
                    fieldObj = clazz.getDeclaredField(field);
                    break;
                } catch (Exception e) {
                    if (t == null) t = e;
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            if (fieldObj == null) return new RetResult(RET_WATCH_RUN_EXCEPTION, "run exception (" + String.valueOf(t) + ")");
            fieldObj.setAccessible(true);
            fieldObj.set(dest, JsonConvert.root().convertFrom(fieldObj.getGenericType(), value));
            return RetResult.success();
        } catch (Throwable t2) {
            return new RetResult(RET_WATCH_RUN_EXCEPTION, "run exception (" + t2.toString() + ")");
        }
    }

    @RestConvert(type = void.class)
    @RestMapping(name = "getfield", auth = false, comment = "查询Service中指定字段的内容")
    public RetResult getfield(@RestParam(name = "name", comment = "Service的资源名") String name,
        @RestParam(name = "type", comment = "Service的类名") String type,
        @RestParam(name = "field", comment = "字段名") String field) {
        if (name == null) name = "";
        if (type == null) type = "";
        if (field == null) field = "";
        type = type.trim();
        field = field.trim();
        if (type.isEmpty()) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param `type`");
        if (field.isEmpty()) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param `field`");
        Object dest = findService(name, type);
        Class clazz = dest.getClass();
        Throwable t = null;
        try {
            Field fieldObj = null;
            do {
                try {
                    fieldObj = clazz.getDeclaredField(field);
                    break;
                } catch (Exception e) {
                    if (t == null) t = e;
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            if (fieldObj == null) return new RetResult(RET_WATCH_RUN_EXCEPTION, "run exception (" + String.valueOf(t) + ")");
            fieldObj.setAccessible(true);
            return new RetResult(fieldObj.get(dest));
        } catch (Throwable t2) {
            return new RetResult(RET_WATCH_RUN_EXCEPTION, "run exception (" + t2.toString() + ")");
        }
    }

    @RestConvert(type = void.class)
    @RestMapping(name = "runmethod", auth = false, comment = "调用Service中指定方法")
    public RetResult runmethod(@RestParam(name = "name", comment = "Service的资源名") String name,
        @RestParam(name = "type", comment = "Service的类名") String type,
        @RestParam(name = "method", comment = "Service的方法名") String method,
        @RestParam(name = "params", comment = "方法的参数值") List<String> params,
        @RestParam(name = "paramtypes", comment = "方法的参数数据类型") List<String> paramtypes) {
        if (name == null) name = "";
        if (type == null) type = "";
        if (method == null) method = "";
        type = type.trim();
        method = method.trim();
        if (type.isEmpty()) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param `type`");
        if (method.isEmpty()) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param `method`");
        Object dest = findService(name, type);
        Class clazz = dest.getClass();
        Throwable t = null;
        final int paramcount = params == null ? 0 : params.size();
        if (paramtypes != null && paramcount != paramtypes.size()) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "params.size not equals to paramtypes.size");
        try {
            Method methodObj = null;
            do {
                try {
                    for (Method m : clazz.getDeclaredMethods()) {
                        if (m.getName().equals(method) && m.getParameterCount() == paramcount) {
                            boolean flag = true;
                            if (paramtypes != null) {
                                Class[] pts = m.getParameterTypes();
                                for (int i = 0; i < pts.length; i++) {
                                    if (!pts[i].getName().endsWith(paramtypes.get(i))) {
                                        flag = false;
                                        break;
                                    }
                                }
                            }
                            if (flag) {
                                methodObj = m;
                                break;
                            }
                        }
                    }
                    if (methodObj != null) break;
                } catch (Exception e) {
                    if (t == null) t = e;
                }
            } while ((clazz = clazz.getSuperclass()) != Object.class);
            if (methodObj == null) return new RetResult(RET_WATCH_RUN_EXCEPTION, "run exception (" + (t == null ? ("not found method(" + method + ")") : String.valueOf(t)) + ")");
            methodObj.setAccessible(true);
            if (paramcount < 1) return new RetResult(methodObj.invoke(dest));
            Object[] paramObjs = new Object[paramcount];
            Type[] pts = methodObj.getGenericParameterTypes();
            for (int i = 0; i < paramObjs.length; i++) {
                paramObjs[i] = JsonConvert.root().convertFrom(pts[i], params.get(i));
            }
            return new RetResult(methodObj.invoke(dest, paramObjs));
        } catch (Throwable t2) {
            return new RetResult(RET_WATCH_RUN_EXCEPTION, "run exception (" + t2.toString() + ")");
        }
    }

    protected Object findService(String name, String type) {
        Object dest = null;
        for (NodeServer ns : application.getNodeServers()) {
            ResourceFactory resFactory = ns.getResourceFactory();
            List list = resFactory.query((n, s) -> name.equals(n) && s != null && s.getClass().getName().endsWith(type));
            if (list == null || list.isEmpty()) continue;
            dest = list.get(0);
        }
        if (dest == null) return new RetResult(RET_SERVICE_DEST_NOT_EXISTS, "not found servie (name=" + name + ", type=" + type + ")");
        return dest;
    }

    @RestMapping(name = "load", auth = false, comment = "动态增加Service")
    public RetResult loadService(@RestParam(name = "type", comment = "Service的类名") String type,
        @RestUploadFile(maxLength = 10 * 1024 * 1024, fileNameReg = "\\.jar$") byte[] jar) {
        //待开发
        return RetResult.success();
    }

    @RestMapping(name = "reload", auth = false, comment = "重新加载Service")
    public RetResult reloadService(@RestParam(name = "name", comment = "Service的资源名") String name,
        @RestParam(name = "type", comment = "Service的类名") String type) {
        //待开发
        return RetResult.success();
    }

    @RestMapping(name = "stop", auth = false, comment = "动态停止Service")
    public RetResult stopService(@RestParam(name = "name", comment = "Service的资源名") String name,
        @RestParam(name = "type", comment = "Service的类名") String type) {
        //待开发
        return RetResult.success();
    }

    @RestMapping(name = "find", auth = false, comment = "查找Service")
    public RetResult find(@RestParam(name = "name", comment = "Service的资源名") String name,
        @RestParam(name = "type", comment = "Service的类名") String type) {
        //待开发
        return RetResult.success();
    }
}
