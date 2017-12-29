package org.redkale.test.rest;

import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Resource;
import org.redkale.net.http.*;

import org.redkale.service.*;
import org.redkale.source.DataSource;
import org.redkale.source.Flipper;
import org.redkale.util.*;

/**
 * 类说明:
 * Flipper : Source组件中的翻页对象
 * UserInfo ：当前用户类
 * HelloEntity: Hello模块的实体类
 * HelloBean: Hello模块实现FilterBean的过滤Bean类
 *
 */
@RestService(automapping = true)
public class HelloService implements Service {

    private int nodeid;

    @Resource
    private DataSource source;

    public HelloService() {
    }

    public HelloService(int nodeid) {
        this.nodeid = nodeid;
    }

    //增加记录
    public RetResult<HelloEntity> createHello(UserInfo info, HelloEntity entity, @RestBody Map<String, String> body) {
        System.out.println("增加记录----------------" + nodeid + ": body =" + body + ", entity =" + entity);
        entity.setCreator(info == null ? 0 : info.getUserid()); //设置当前用户ID
        entity.setCreatetime(System.currentTimeMillis());
        if (source != null) source.insert(entity);
        return new RetResult<>(entity);
    }

    //
    public HttpResult showHello(int id) {
        return new HttpResult("a");
    }

    //删除记录
    public void deleteHello(int id) { //通过 /pipes/hello/delete/1234 删除对象
        source.delete(HelloEntity.class, id);
    }

    //修改记录
    public void updateHello(@RestAddress String clientAddr, HelloEntity entity) { //通过 /pipes/hello/update?bean={...} 修改对象
        System.out.println("修改记录-" + nodeid + ": clientAddr = " + clientAddr + ", entity =" + entity);
        if (entity != null) entity.setUpdatetime(System.currentTimeMillis());
        if (source != null) source.update(entity);
    }

    //修改记录
    public void update2Hello(@RestAddress String clientAddr, @RestUploadFile byte[] fs) { //通过 /pipes/hello/update2?bean={...} 修改对象
        System.out.println("修改记录2-" + nodeid + ": clientAddr = " + clientAddr + ", fs =" + fs);
    }
    
    //修改记录
    @RestMapping(name = "partupdate")
    public void updateHello(HelloEntity entity, @RestParam(name = "cols") String[] columns) { //通过 /pipes/hello/partupdate?bean={...}&cols=... 修改对象
        entity.setUpdatetime(System.currentTimeMillis());
        source.updateColumn(entity, columns);
    }

    //查询Sheet列表
    public Sheet<HelloEntity> queryHello(HelloBean bean, Flipper flipper) { //通过 /pipes/hello/query/offset:0/limit:20?bean={...} 查询Sheet列表
        return source.querySheet(HelloEntity.class, flipper, bean);
    }

    //查询List列表
    @RestMapping(name = "list")
    @RestConvert(type = HelloEntity.class, ignoreColumns = {"createtime"})
    public List<HelloEntity> queryHello(HelloBean bean) { //通过 /pipes/hello/list?bean={...} 查询List列表
        return source.queryList(HelloEntity.class, bean);
    }

    //查询List列表
    @RestMapping(name = "listmap")
    public List<HelloEntity> queryHello(HelloBean bean, @RestParam(name = "map") Map<String, String> map) { //通过 /pipes/hello/list?bean={...} 查询List列表
        System.out.println("map参数: " + map);
        if (source != null) return source.queryList(HelloEntity.class, bean);
        return new ArrayList<>();
    }

    //查询单个
    @RestMapping(name = "find")
    public HelloEntity findHello(@RestParam(name = "#") int id) {  //通过 /pipes/hello/find/1234、/pipes/hello/jsfind/1234 查询对象
        return source.find(HelloEntity.class, id);
    }

    //异步查询单个
    @RestMapping(name = "asyncfind")
    public CompletableFuture<HelloEntity> asyncFindHello(@RestParam(name = "#") int id) {  //通过 /pipes/hello/find/1234、/pipes/hello/jsfind/1234 查询对象
        if (source != null) source.findAsync(HelloEntity.class, id);
        System.out.println("------------进入asyncfind1-------");
        return CompletableFuture.completedFuture(new HelloEntity());
    }

    //异步查询单个
    @RestMapping(name = "asyncfind2")
    public void asyncFindHello(CompletionHandler hander, @RestParam(name = "#") int id) {  //通过 /pipes/hello/find/1234、/pipes/hello/jsfind/1234 查询对象
        if (source != null) source.findAsync(HelloEntity.class, id);
        System.out.println("-----------进入asyncfind2--------" + hander);
        hander.completed(new HelloEntity(id), id);
    }

    //异步查询单个
    @RestMapping(name = "asyncfind3")
    public void asyncFindHello(HelloAsyncHandler hander, @RestParam(name = "#") int id) {  //通过 /pipes/hello/find/1234、/pipes/hello/jsfind/1234 查询对象
        if (source != null) source.findAsync(HelloEntity.class, id);
        System.out.println("-----------进入asyncfind3--------" + hander);
        hander.completed(new HelloEntity(id), id);
    }
}
