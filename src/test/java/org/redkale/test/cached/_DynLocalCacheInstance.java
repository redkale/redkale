/*
 *
 */
package org.redkale.test.cached;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.redkale.annotation.Resource;
import org.redkale.annotation.ResourceType;
import org.redkale.cached.spi.CachedAction;
import org.redkale.cached.spi.DynForCached;
import org.redkale.net.sncp.Sncp.SncpDyn;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleException;
import org.redkale.util.ThrowSupplier;

@Resource(name = "")
@SncpDyn(remote = false, type = CachedInstance.class)
@ResourceType(CachedInstance.class)
public class _DynLocalCacheInstance extends CachedInstance {

    private AnyValue _redkale_conf;

    private String _redkale_mq;

    private CachedAction _redkale_getNameCachedAction1;

    private CachedAction _redkale_getInfoCachedAction2;

    private CachedAction _redkale_getNameAsyncCachedAction3;

    private CachedAction _redkale_getInfo2AsyncCachedAction4;

    private CachedAction _redkale_getName2AsyncCachedAction5;

    private CachedAction _redkale_getInfoAsyncCachedAction6;

    private CachedAction _redkale_getName2CachedAction7;

    public _DynLocalCacheInstance() {}

    @DynForCached(
            dynField = "_redkale_getNameCachedAction1",
            manager = "",
            name = "name",
            key = "name",
            nullable = false,
            timeUnit = TimeUnit.SECONDS,
            localLimit = "-1",
            remoteExpire = "-1",
            localExpire = "30")
    public String getName() {
        ThrowSupplier<String> supplier = () -> this.getName_afterCache();
        return _redkale_getNameCachedAction1.get(supplier);
    }

    private String getName_afterCache() {
        return super.getName();
    }

    @DynForCached(
            dynField = "_redkale_getInfoCachedAction2",
            manager = "",
            name = "info",
            key = "#{id}_#{files.one}",
            nullable = false,
            timeUnit = TimeUnit.SECONDS,
            localLimit = "-1",
            remoteExpire = "60",
            localExpire = "30")
    public File getInfo(CachedInstance.ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        ThrowSupplier<File> supplier = () -> this.getInfo_afterCache(bean, id, idList, files);
        return _redkale_getInfoCachedAction2.get(supplier);
    }

    private File getInfo_afterCache(
            CachedInstance.ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        return super.getInfo(bean, id, idList, files);
    }

    @DynForCached(
            dynField = "_redkale_getNameAsyncCachedAction3",
            manager = "",
            name = "name",
            key = "name",
            nullable = false,
            timeUnit = TimeUnit.SECONDS,
            localLimit = "-1",
            remoteExpire = "-1",
            localExpire = "30")
    public CompletableFuture<String> getNameAsync() {
        ThrowSupplier<CompletableFuture<String>> supplier = () -> this.getNameAsync_afterCache();
        return _redkale_getNameAsyncCachedAction3.get(supplier);
    }

    private CompletableFuture<String> getNameAsync_afterCache() {
        return super.getNameAsync();
    }

    @DynForCached(
            dynField = "_redkale_getInfo2AsyncCachedAction4",
            manager = "",
            name = "info",
            key = "#{id}_#{files.one}",
            nullable = false,
            timeUnit = TimeUnit.SECONDS,
            localLimit = "-1",
            remoteExpire = "60",
            localExpire = "30")
    public CompletableFuture<Map<String, Integer>> getInfo2Async(
            CachedInstance.ParamBean bean, int id, List<String> idList, Map<String, File> files)
            throws IOException, InstantiationException {
        ThrowSupplier<CompletableFuture<Map<String, Integer>>> supplier =
                () -> this.getInfo2Async_afterCache(bean, id, idList, files);
        return _redkale_getInfo2AsyncCachedAction4.get(supplier, bean, id, idList, files);
    }

    private CompletableFuture<Map<String, Integer>> getInfo2Async_afterCache(
            CachedInstance.ParamBean bean, int id, List<String> idList, Map<String, File> files)
            throws IOException, InstantiationException {
        return super.getInfo2Async(bean, id, idList, files);
    }

    @DynForCached(
            dynField = "_redkale_getName2AsyncCachedAction5",
            manager = "",
            name = "name",
            key = "name",
            nullable = false,
            timeUnit = TimeUnit.SECONDS,
            localLimit = "-1",
            remoteExpire = "60",
            localExpire = "30")
    public CompletableFuture<String> getName2Async() throws IOException, InstantiationException {
        ThrowSupplier<CompletableFuture<String>> supplier = () -> this.getName2Async_afterCache();
        return _redkale_getName2AsyncCachedAction5.get(supplier);
    }

    private CompletableFuture<String> getName2Async_afterCache() throws IOException, InstantiationException {
        return super.getName2Async();
    }

    @DynForCached(
            dynField = "_redkale_getInfoAsyncCachedAction6",
            manager = "",
            name = "info",
            key = "#{id}_#{files.one}",
            nullable = false,
            timeUnit = TimeUnit.SECONDS,
            localLimit = "-1",
            remoteExpire = "60",
            localExpire = "30")
    public CompletableFuture<File> getInfoAsync(
            CachedInstance.ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        ThrowSupplier<CompletableFuture<File>> supplier = () -> this.getInfoAsync_afterCache(bean, id, idList, files);
        return _redkale_getInfoAsyncCachedAction6.get(supplier, bean, id, idList, files);
    }

    private CompletableFuture<File> getInfoAsync_afterCache(
            CachedInstance.ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        return super.getInfoAsync(bean, id, idList, files);
    }

    @DynForCached(
            dynField = "_redkale_getName2CachedAction7",
            manager = "",
            name = "name",
            key = "name",
            nullable = false,
            timeUnit = TimeUnit.SECONDS,
            localLimit = "-1",
            remoteExpire = "60",
            localExpire = "30")
    public String getName2() throws RedkaleException {
        ThrowSupplier<String> supplier = () -> this.getName2_afterCache();
        return _redkale_getName2CachedAction7.get(supplier);
    }

    private String getName2_afterCache() throws RedkaleException {
        return super.getName2();
    }
}
