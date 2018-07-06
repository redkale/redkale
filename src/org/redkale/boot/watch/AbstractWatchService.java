/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot.watch;

import org.redkale.service.AbstractService;
import org.redkale.util.Comment;
import org.redkale.watch.WatchService;

/**
 *
 * @author zhangjx
 */
public abstract class AbstractWatchService extends AbstractService implements WatchService {

    @Comment("缺少参数")
    public static final int RET_WATCH_PARAMS_ILLEGAL = 1600_0001;

    @Comment("执行异常")
    public static final int RET_WATCH_RUN_EXCEPTION = 1600_0002;
}
