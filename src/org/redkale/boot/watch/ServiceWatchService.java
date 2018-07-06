/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot.watch;

import java.lang.reflect.Field;
import java.util.List;
import javax.annotation.Resource;
import org.redkale.boot.*;
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

    @RestMapping(name = "findfield", auth = false, comment = "查询Service中指定字段的内容")
    @RestConvert(type = void.class)
    public RetResult findfield(String name, String type, String field) {
        if (name == null) name = "";
        if (type == null) type = "";
        if (field == null) field = "";
        type = type.trim();
        field = field.trim();
        if (type.isEmpty()) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param `type`");
        if (field.isEmpty()) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param `field`");
        final String name0 = name;
        final String type0 = type;
        Object dest = null;
        for (NodeServer ns : application.getNodeServers()) {
            ResourceFactory resFactory = ns.getResourceFactory();
            List list = resFactory.query((n, s) -> name0.equals(n) && s.getClass().getName().endsWith(type0));
            if (list == null || list.isEmpty()) continue;
            dest = list.get(0);
        }
        if (dest == null) return new RetResult(RET_SERVICE_DEST_NOT_EXISTS, "not found servie (name=" + name + ", type=" + type + ")");
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
//    
//    @RestMapping(name = "load", auth = false, comment = "动态增加Service")
//    public RetResult loadService(String type, @RestUploadFile(maxLength = 10 * 1024 * 1024, fileNameReg = "\\.jar$") byte[] jar) {
//        //待开发
//        return RetResult.success();
//    }
//
//    @RestMapping(name = "stop", auth = false, comment = "动态停止Service")
//    public RetResult stopService(String name, String type) {
//        //待开发
//        return RetResult.success();
//    }
}
