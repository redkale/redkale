/*
 *
 */
package org.redkale.test.cache;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.redkale.cache.Cached;
import org.redkale.service.Service;
import org.redkale.source.Range;
import org.redkale.util.RedkaleException;

/**
 *
 * @author zhangjx
 */
public class CacheInstance implements Service {

    @Cached(key = "name", localExpire = "30")
    public String getName() {
        return "haha";
    }

    @Cached(key = "name", localExpire = "30", remoteExpire = "60")
    public String getName2() throws RedkaleException {
        return "haha";
    }

    @Cached(key = "name", localExpire = "30")
    public CompletableFuture<String> getNameAsync() {
        return CompletableFuture.completedFuture("nameAsync");
    }

    @Cached(key = "name", localExpire = "30", remoteExpire = "60")
    public CompletableFuture<String> getName2Async() throws IOException, InstantiationException {
        return CompletableFuture.completedFuture("name2Async");
    }

    @Cached(key = "info_#{id}_file#{files.one}", localExpire = "30", remoteExpire = "60")
    public File getInfo(ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        return new File("aa.txt");
    }

    @Cached(key = "info_#{id}_file#{files.one}", localExpire = "30", remoteExpire = "60")
    public CompletableFuture<File> getInfoAsync(ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        return CompletableFuture.completedFuture(new File("aa.txt"));
    }

    @Cached(key = "info_#{id}_file#{files.one}", localExpire = "30", remoteExpire = "60")
    public CompletableFuture<Map<String, Integer>> getInfo2Async(ParamBean bean, int id, List<String> idList, Map<String, File> files) throws IOException, InstantiationException {
        return CompletableFuture.completedFuture(null);
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
