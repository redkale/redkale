package org.redkale.test.rest;

import java.util.List;
import javax.annotation.Resource;
import org.redkale.net.http.*;

import org.redkale.service.*;
import org.redkale.source.DataSource;
import org.redkale.source.Flipper;
import org.redkale.util.Sheet;


/**
 * 类说明:
 * Flipper : Source组件中的翻页对象
 * UserInfo ：当前用户类
 * HelloEntity: Hello模块的实体类
 * HelloBean: Hellow模块实现FilterBean的过滤Bean类
 *
 */
public class HelloService implements Service {

    @Resource
    private DataSource source;

    //增加记录
    public RetResult<HelloEntity> createHello(UserInfo info, HelloEntity entity) {
        entity.setCreator(info == null ? 0 : info.getUserid()); //设置当前用户ID
        entity.setCreatetime(System.currentTimeMillis());
        source.insert(entity);
        return new RetResult<>(entity);
    }

    //删除记录
    public void deleteHello(int id) { //通过 /hello/delete/1234 删除对象
        source.delete(HelloEntity.class, id);
    }

    //修改记录
    public void updateHello(HelloEntity entity) { //通过 /hello/update?bean={...} 修改对象
        entity.setUpdatetime(System.currentTimeMillis());
        source.update(entity);
    }

    //修改记录
    @RestMapping(name = "partupdate")  
    public void updateHello(HelloEntity entity, @RestParam("cols") String[] columns) { //通过 /hello/partupdate?bean={...} 修改对象
        entity.setUpdatetime(System.currentTimeMillis());
        source.updateColumns(entity, columns);
    }
    
    //查询Sheet列表
    public Sheet<HelloEntity> queryHello(HelloBean bean, Flipper flipper) { //通过 /hello/query/offset:0/limit:20?bean={...} 查询Sheet列表
        return source.querySheet(HelloEntity.class, flipper, bean);
    }

    //查询List列表
    @RestMapping(name = "list")
    public List<HelloEntity> queryHello(HelloBean bean) { //通过 /hello/list?bean={...} 查询List列表
        return source.queryList(HelloEntity.class, bean);
    }

    //查询单个
    @RestMapping(name = "find")
    @RestMapping(name = "jsfind", jsvar = "varhello")
    public HelloEntity findHello(@RestParam("#") int id) {  //通过 /hello/find/1234 查询对象
        return source.find(HelloEntity.class, id);
    }
}