/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot.watch;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Properties;
import javax.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.net.http.*;
import org.redkale.service.*;
import org.redkale.source.*;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
@RestService(name = "source", catalog = "watch", repair = false)
public class SourceWatchService extends AbstractWatchService {

    @Comment("不存在的Source")
    public static final int RET_SOURCE_NOT_EXISTS = 1605_0001;

    @Comment("Source不支持getReadPoolSource/getWritePoolSource方法")
    public static final int RET_SOURCE_CHANGE_METHOD_NOT_EXISTS = 1605_0002;

    @Comment("PoolSource调用change方法失败")
    public static final int RET_SOURCE_METHOD_INVOKE_NOT_EXISTS = 1605_0003;

    @Resource
    protected Application application;

    @RestMapping(name = "change", auth = false, comment = "动态更改DataSource的配置")
    public RetResult addNode(@RestParam(name = "name", comment = "DataSource的标识") final String name,
        @RestParam(name = "properties", comment = "配置") final Properties properties) throws IOException {
        if (name == null) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param (name)");
        if (properties == null) return new RetResult(RET_WATCH_PARAMS_ILLEGAL, "not found param (properties)");
        DataSource source = null;
        for (DataSource s : application.getDataSources()) {
            String resName = ((Resourcable) s).resourceName();
            if (resName == null) continue;
            if (!resName.equals(name)) continue;
            source = s;
            break;
        }
        if (source == null) return new RetResult(RET_SOURCE_NOT_EXISTS, "not found source (name = " + name + ")");
        Method readPoolMethod = null;
        Method writePoolMethod = null;
        Class stype = source.getClass();
        do {
            for (Method m : stype.getDeclaredMethods()) {
                if (!PoolSource.class.isAssignableFrom(m.getReturnType())) continue;
                if (m.getParameterCount() != 0) continue;
                if (m.getName().equals("getReadPoolSource")) {
                    readPoolMethod = m;
                } else if (m.getName().equals("getWritePoolSource")) {
                    writePoolMethod = m;
                }
            }
        } while ((stype = stype.getSuperclass()) != Object.class);
        if (readPoolMethod == null) return new RetResult(RET_SOURCE_CHANGE_METHOD_NOT_EXISTS, "not found source method(getReadPoolSource)");
        if (writePoolMethod == null) return new RetResult(RET_SOURCE_CHANGE_METHOD_NOT_EXISTS, "not found source method(getWritePoolSource)");
        readPoolMethod.setAccessible(true);
        writePoolMethod.setAccessible(true);
        try {
            PoolSource readPoolSource = (PoolSource) readPoolMethod.invoke(source);
            PoolSource writePoolSource = (PoolSource) writePoolMethod.invoke(source);
            readPoolSource.change(properties);
            writePoolSource.change(properties);
            return RetResult.success();
        } catch (Exception e) {
            return new RetResult(RET_SOURCE_METHOD_INVOKE_NOT_EXISTS, "poolsource invoke method('change') error");
        }
    }
}
