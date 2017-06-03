/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.boot.watch;

import org.redkale.net.http.RestService;
import org.redkale.watch.WatchService;

/**
 *
 * @author zhangjx
 */
@RestService(name = "filter", catalog = "watch", repair = false)
public class FilterWatchService implements WatchService {
    
}
