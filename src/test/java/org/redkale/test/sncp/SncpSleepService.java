/*
 *
 */
package org.redkale.test.sncp;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import org.redkale.service.AbstractService;
import org.redkale.util.Times;
import org.redkale.util.Utility;

/** @author zhangjx */
public class SncpSleepService extends AbstractService {

    public CompletableFuture<String> sleep200() {
        System.out.println(Times.nowMillis() + " " + Thread.currentThread().getName() + " 接收sleep200");
        return CompletableFuture.supplyAsync(
                () -> {
                    Utility.sleep(200);
                    System.out.println(
                            Times.nowMillis() + " " + Thread.currentThread().getName() + " 执行完sleep200");
                    return "ok200";
                },
                getExecutor());
    }

    public CompletableFuture<String> sleep300() {
        System.out.println(Times.nowMillis() + " " + Thread.currentThread().getName() + " 接收sleep300");
        return CompletableFuture.supplyAsync(
                () -> {
                    Utility.sleep(300);
                    System.out.println(
                            Times.nowMillis() + " " + Thread.currentThread().getName() + " 执行完sleep300");
                    return "ok300";
                },
                getExecutor());
    }

    public CompletableFuture<String> sleep500() {
        System.out.println(Times.nowMillis() + " " + Thread.currentThread().getName() + " 接收sleep500");
        return CompletableFuture.supplyAsync(
                () -> {
                    Utility.sleep(500);
                    System.out.println(
                            Times.nowMillis() + " " + Thread.currentThread().getName() + " 执行完sleep500");
                    return "ok500";
                },
                getExecutor());
    }

    public String test(Serializable id, String[] names, Collection<File> files) {
        return "ok";
    }
}
