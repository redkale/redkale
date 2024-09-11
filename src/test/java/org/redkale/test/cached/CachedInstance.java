/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.cached;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.redkale.annotation.Resource;
import org.redkale.cached.Cached;
import org.redkale.cached.CachedManager;
import org.redkale.service.Service;
import org.redkale.source.Range;
import org.redkale.util.RedkaleException;

/** @author zhangjx */
public class CachedInstance implements Service {

    @Resource
    private CachedManager cachedManager;

    // 修改远程缓存的key值
    public void updateName(String code, Map<String, Long> map) {
        cachedManager.remoteSetString("name", code + "_" + map.get("id"), code + "-" + map, Duration.ofMillis(60));
    }

    @Cached(name = "code", key = "#{code}_#{map.id}", remoteExpire = "60", timeUnit = TimeUnit.MILLISECONDS)
    public String getName(String code, Map<String, Long> map) {
        return code + "-" + map;
    }

    @Cached(name = "name", key = "name", localExpire = "30")
    public String getName() {
        return "haha";
    }

    public void updateName2(String val) {
        cachedManager.bothSet("name2", "name_2", String.class, val, Duration.ofSeconds(31), Duration.ofSeconds(60));
    }

    @Cached(name = "name2", key = "name_2", localExpire = "31", remoteExpire = "60")
    public String getName2() throws RedkaleException {
        return "haha";
    }

    @Cached(name = "dictcode", key = "dictcode", localExpire = "30", remoteExpire = "60")
    public CompletableFuture<String> getDictcodeAsync() {
        System.out.println("执行了 getDictcodeAsync");
        return CompletableFuture.completedFuture("code001");
    }

    @Cached(name = "name", key = "name", localExpire = "30")
    public CompletableFuture<String> getNameAsync() {
        return CompletableFuture.completedFuture("nameAsync");
    }

    @Cached(name = "name", key = "name", localExpire = "30", remoteExpire = "60")
    public CompletableFuture<String> getName2Async() throws IOException, InstantiationException {
        return CompletableFuture.completedFuture("name2Async");
    }

    @Cached(name = "info", key = "#{id}_#{files.one}", localExpire = "30", remoteExpire = "60")
    public File getInfo(ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        return new File("aa.txt");
    }

    @Cached(name = "info", key = "#{id}_#{files.one}", localExpire = "30", remoteExpire = "60")
    public CompletableFuture<File> getInfoAsync(ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        return CompletableFuture.completedFuture(new File("aa.txt"));
    }

    @Cached(
            name = "info",
            key = "#{id}_#{files.one}",
            localExpire = "30",
            remoteExpire = "60",
            timeUnit = TimeUnit.MILLISECONDS)
    public CompletableFuture<Map<String, Integer>> getInfo2Async(
            ParamBean bean, int id, List<String> idList, Map<String, File> files)
            throws IOException, InstantiationException {
        return CompletableFuture.completedFuture(null);
    }

    public CachedManager getCachedManager() {
        return cachedManager;
    }

    public static class ParamBean {

        private String name;

        private int day;

        private Integer status;

        private Range.IntRange range;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getDay() {
            return day;
        }

        public void setDay(int day) {
            this.day = day;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }

        public Range.IntRange getRange() {
            return range;
        }

        public void setRange(Range.IntRange range) {
            this.range = range;
        }
    }
}
