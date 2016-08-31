/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.rest;

import javax.annotation.Resource;
import org.redkale.net.http.*;
import org.redkale.service.*;
import org.redkale.source.*;
import org.redkale.util.Sheet;

/**
 * 类说明:
 * Flipper : Source组件中的翻页对象
 * UserInfo ：当前用户类
 * HelloEntity: Hello模块的实体类
 * HelloBean: Hellow模块实现FilterBean的过滤Bean类
 *
 */
@RestService(value = "hello", module = 0, repair = true, ignore = false)
public class HelloService2 implements Service {

    @Resource
    private DataSource source;

    //增加记录
    @RestMapping(name = "create", auth = false)
    public RetResult<HelloEntity> createHello(UserInfo info, @RestParam("bean") HelloEntity entity) {
        entity.setCreator(info == null ? 0 : info.getUserid()); //设置当前用户ID
        entity.setCreatetime(System.currentTimeMillis());
        source.insert(entity);
        return new RetResult<>(entity);
    }

    //删除记录
    @RestMapping(name = "delete", auth = false)
    public void deleteHello(@RestParam("#") int id) { //通过 /hello/delete/1234 删除对象
        source.delete(HelloEntity.class, id);
    }

    //修改记录
    @RestMapping(name = "update", auth = false)
    public void updateHello(@RestParam("bean") HelloEntity entity) { //通过 /hello/update?bean={...} 修改对象
        entity.setUpdatetime(System.currentTimeMillis());
        source.update(entity);
    }

    //查询列表
    @RestMapping(name = "query", auth = false)
    public Sheet<HelloEntity> queryHello(@RestParam("bean") HelloBean bean, Flipper flipper) { //通过 /hello/query/offset:0/limit:20?bean={...} 查询列表
        return source.querySheet(HelloEntity.class, flipper, bean);
    }

    //查询单个
    @RestMapping(name = "find", auth = false)
    public HelloEntity findHello(@RestParam("#") int id) {  //通过 /hello/find/1234 查询对象
        return source.find(HelloEntity.class, id);
    }
}
