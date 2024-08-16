/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.http;

import org.redkale.net.http.RestBody;
import org.redkale.net.http.RestService;
import org.redkale.service.AbstractService;
import org.redkale.util.Utility;

/** @author zhangjx */
@RestService(name = "test", autoMapping = true)
public class RestSleepService extends AbstractService {

    public String sleep200() {
        Utility.sleep(200);
        System.out.println("当前执行线程: " + Thread.currentThread().getName());
        return "ok200";
    }

    public String sleep300() {
        Utility.sleep(300);
        return "ok300";
    }

    public String sleep400() {
        Utility.sleep(400);
        return "ok400";
    }

    public String sleep500() {
        Utility.sleep(500);
        return "ok500";
    }

    public String sleepName(@RestBody String name) {
        System.out.println("获取到的名字: " + name);
        return "sleep: " + name;
    }
}
