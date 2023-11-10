/*
 *
 */
package org.redkale.test.http;

import org.redkale.net.http.RestService;
import org.redkale.service.AbstractService;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
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
}
