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
 * HelloBean: Hello模块实现FilterBean的过滤Bean类
 *
 */
@RestService(name = "hello", moduleid = 0, automapping = true, repair = true, ignore = false, comment = "Hello服务模块")
public class HelloService2 implements Service {

    @Resource
    private DataSource source;

    //增加记录
    @RestMapping(name = "create", auth = false, comment = "创建Hello对象")
    public RetResult<HelloEntity> createHello(UserInfo info, @RestParam(name = "bean", comment = "Hello对象") HelloEntity entity) {
        entity.setCreator(info == null ? 0 : info.getUserid()); //设置当前用户ID
        entity.setCreatetime(System.currentTimeMillis());
        source.insert(entity);
        return new RetResult<>(entity);
    }

    //删除记录
    @RestMapping(name = "delete", auth = false, comment = "根据id删除Hello对象")
    public void deleteHello(@RestParam(name = "#", comment = "Hello对象id") int id) { //通过 /hello/delete/1234 删除对象
        source.delete(HelloEntity.class, id);
    }

    //修改记录
    @RestMapping(name = "update", auth = false, comment = "修改Hello对象")
    public void updateHello(@RestParam(name = "bean", comment = "Hello对象") HelloEntity entity) { //通过 /hello/update?bean={...} 修改对象
        entity.setUpdatetime(System.currentTimeMillis());
        source.update(entity);
    }

    //查询列表    
    @RestConvertCoder(type = HelloEntity.class, field = "createtime", coder = CreateTimeSimpleCoder.class)
    @RestMapping(name = "query", auth = false, comment = "查询Hello对象列表")
    public Sheet<HelloEntity> queryHello(@RestParam(name = "bean", comment = "过滤条件") HelloBean bean, Flipper flipper) { //通过 /hello/query/offset:0/limit:20?bean={...} 查询列表
        return source.querySheet(HelloEntity.class, flipper, bean);
    }

    //查询单个
    @RestMapping(name = "find", auth = false, comment = "根据id查找单个Hello对象")
    public HelloEntity findHello(@RestParam(name = "#", comment = "Hello对象id") int id) {  //通过 /hello/find/1234 查询对象
        return source.find(HelloEntity.class, id);
    }
}
