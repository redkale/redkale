/*
 *
 */
package org.redkale.test.cache;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.redkale.annotation.Resource;
import org.redkale.annotation.ResourceType;
import org.redkale.cache.spi.CacheAction;
import org.redkale.cache.spi.DynForCache;
import org.redkale.net.sncp.Sncp.SncpDyn;
import org.redkale.util.AnyValue;
import org.redkale.util.RedkaleException;
import org.redkale.util.ThrowSupplier;

@Resource(
    name = ""
)
@SncpDyn(
    remote = false,
    type = CacheInstance.class
)
@ResourceType(CacheInstance.class)
public class _DynLocalCacheInstance extends CacheInstance {

    private AnyValue _redkale_conf;

    private String _redkale_mq;

    private CacheAction _redkale_getNameCacheAction1;

    private CacheAction _redkale_getInfoCacheAction2;

    private CacheAction _redkale_getNameAsyncCacheAction3;

    private CacheAction _redkale_getInfo2AsyncCacheAction4;

    private CacheAction _redkale_getName2AsyncCacheAction5;

    private CacheAction _redkale_getInfoAsyncCacheAction6;

    private CacheAction _redkale_getName2CacheAction7;

    public _DynLocalCacheInstance() {
    }

    @DynForCache(dynField = "_redkale_getNameCacheAction1", hash = "", key = "name", nullable = false, timeUnit = TimeUnit.SECONDS, remoteExpire = "-1", localExpire = "30"
    )
    public String getName() {
        ThrowSupplier<String> supplier = () -> this.getName_afterCache();
        return _redkale_getNameCacheAction1.get(supplier);
    }

    private String getName_afterCache() {
        return super.getName();
    }

    @DynForCache(dynField = "_redkale_getInfoCacheAction2", hash = "", key = "info_#{id}_file#{files.one}", nullable = false, timeUnit = TimeUnit.SECONDS, remoteExpire = "60", localExpire = "30")
    public File getInfo(CacheInstance.ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        ThrowSupplier<File> supplier = () -> this.getInfo_afterCache(bean, id, idList, files);
        return _redkale_getInfoCacheAction2.get(supplier);
    }

    private File getInfo_afterCache(CacheInstance.ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        return super.getInfo(bean, id, idList, files);
    }

    @DynForCache(dynField = "_redkale_getNameAsyncCacheAction3", hash = "", key = "name", nullable = false, timeUnit = TimeUnit.SECONDS, remoteExpire = "-1", localExpire = "30")
    public CompletableFuture<String> getNameAsync() {
        ThrowSupplier<CompletableFuture<String>> supplier = () -> this.getNameAsync_afterCache();
        return _redkale_getNameAsyncCacheAction3.get(supplier);
    }

    private CompletableFuture<String> getNameAsync_afterCache() {
        return super.getNameAsync();
    }

    @DynForCache(dynField = "_redkale_getInfo2AsyncCacheAction4", hash = "", key = "info_#{id}_file#{files.one}", nullable = false, timeUnit = TimeUnit.SECONDS, remoteExpire = "60", localExpire = "30")
    public CompletableFuture<Map<String, Integer>> getInfo2Async(CacheInstance.ParamBean bean, int id,
        List<String> idList, Map<String, File> files) throws IOException, InstantiationException {
        ThrowSupplier<CompletableFuture<Map<String, Integer>>> supplier = () -> this.getInfo2Async_afterCache(bean, id, idList, files);
        return _redkale_getInfo2AsyncCacheAction4.get(supplier, bean, id, idList, files);
    }

    private CompletableFuture<Map<String, Integer>> getInfo2Async_afterCache(CacheInstance.ParamBean bean, int id,
        List<String> idList, Map<String, File> files) throws IOException, InstantiationException {
        return super.getInfo2Async(bean, id, idList, files);
    }

    @DynForCache(dynField = "_redkale_getName2AsyncCacheAction5", hash = "", key = "name", nullable = false, timeUnit = TimeUnit.SECONDS, remoteExpire = "60", localExpire = "30")
    public CompletableFuture<String> getName2Async() throws IOException, InstantiationException {
        ThrowSupplier<CompletableFuture<String>> supplier = () -> this.getName2Async_afterCache();
        return _redkale_getName2AsyncCacheAction5.get(supplier);
    }

    private CompletableFuture<String> getName2Async_afterCache() throws IOException, InstantiationException {
        return super.getName2Async();
    }

    @DynForCache(dynField = "_redkale_getInfoAsyncCacheAction6", hash = "", key = "info_#{id}_file#{files.one}", nullable = false, timeUnit = TimeUnit.SECONDS, remoteExpire = "60", localExpire = "30")
    public CompletableFuture<File> getInfoAsync(CacheInstance.ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        ThrowSupplier<CompletableFuture<File>> supplier = () -> this.getInfoAsync_afterCache(bean, id, idList, files);
        return _redkale_getInfoAsyncCacheAction6.get(supplier, bean, id, idList, files);
    }

    private CompletableFuture<File> getInfoAsync_afterCache(CacheInstance.ParamBean bean, int id, List<String> idList, Map<String, File> files) {
        return super.getInfoAsync(bean, id, idList, files);
    }

    @DynForCache(dynField = "_redkale_getName2CacheAction7", hash = "", key = "name", nullable = false, timeUnit = TimeUnit.SECONDS, remoteExpire = "60", localExpire = "30")
    public String getName2() throws RedkaleException {
        ThrowSupplier<String> supplier = () -> this.getName2_afterCache();
        return _redkale_getName2CacheAction7.get(supplier);
    }

    private String getName2_afterCache() throws RedkaleException {
        return super.getName2();
    }
}
