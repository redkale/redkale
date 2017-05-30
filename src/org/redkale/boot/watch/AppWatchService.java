/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot.watch;

import javax.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.net.TransportFactory;
import org.redkale.net.http.RestService;
import org.redkale.watch.WatchService;

/**
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@RestService(name = "watch", repair = false)
public class AppWatchService implements WatchService {

    @Resource
    private Application application;

    @Resource
    private TransportFactory transportFactory;
    
}
