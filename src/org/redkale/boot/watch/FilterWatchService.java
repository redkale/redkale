/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot.watch;

import java.io.IOException;
import javax.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.net.TransportFactory;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;
import org.redkale.util.Comment;

/**
 *
 * @author zhangjx
 */
@RestService(name = "filter", catalog = "watch", repair = false)
public class FilterWatchService extends AbstractWatchService {

    @Comment("不存在的Group节点")
    public static final int RET_NO_GROUP = 1601_0001;

    @Comment("Filter类名不存在")
    public static final int RET_FILTER_TYPE_ILLEGAL = 1601_0002;

    @Comment("Node节点IP地址已存在")
    public static final int RET_FILTER_EXISTS = 1601_0003;

    @Resource
    private Application application;

    @Resource
    private TransportFactory transportFactory;

    @RestMapping(name = "addfilter", auth = false, comment = "动态增加Filter")
    public RetResult addFilter(@RestParam(name = "server", comment = "Server节点名, 不指定名称则所有符合条件的Server都会增加Filter") final String serverName,
        @RestParam(name = "type", comment = "Filter类名") final String filterType) throws IOException {
        if (filterType == null) return new RetResult(RET_FILTER_TYPE_ILLEGAL, "Filter Type (" + filterType + ") is illegal");
        return RetResult.success();
    }
}
