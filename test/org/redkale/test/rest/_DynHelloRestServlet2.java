/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.rest;

import java.io.IOException;
import java.util.Map;
import javax.annotation.Resource;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;
import org.redkale.source.Flipper;
import org.redkale.util.Sheet;

/**
 *
 * @author zhangjx
 */
@WebServlet(value = {"/hello/*"}, repair = true)
public class _DynHelloRestServlet2 extends SimpleRestServlet {

    @Resource
    private HelloService2 _service;

    @Resource
    private Map<String, HelloService2> _servicemap;

    @AuthIgnore
    @WebMapping(url = "/hello/create", comment = "创建Hello对象")
    @WebParam(name = "bean", type = HelloEntity.class, comment = "Hello对象")
    public void create(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService2 service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        bean.setClientaddr(req.getRemoteAddr());
        bean.setResname(req.getHeader("hello-res"));
        UserInfo user = currentUser(req);
        RetResult<HelloEntity> result = service.createHello(user, bean);
        resp.finishJson(result);
    }

    @AuthIgnore
    @WebMapping(url = "/hello/delete/", comment = "根据id删除Hello对象")
    @WebParam(name = "#", type = int.class, comment = "Hello对象id")
    public void delete(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService2 service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        int id = Integer.parseInt(req.getRequstURILastPath());
        service.deleteHello(id);
        resp.finishJson(RetResult.success());
    }

    @AuthIgnore
    @WebMapping(url = "/hello/update", comment = "修改Hello对象")
    @WebParam(name = "bean", type = HelloEntity.class, comment = "Hello对象")
    public void update(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService2 service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        bean.setClientaddr(req.getRemoteAddr());
        bean.setResname(req.getHeader("hello-res"));
        service.updateHello(bean);
        resp.finishJson(RetResult.success());
    }

    @AuthIgnore
    @WebMapping(url = "/hello/query", comment = "查询Hello对象列表")
    @WebParam(name = "bean", type = HelloBean.class, comment = "过滤条件")
    public void query(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService2 service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        HelloBean bean = req.getJsonParameter(HelloBean.class, "bean");
        bean.setClientaddr(req.getRemoteAddr());
        bean.setUseragent(req.getHeader("User-Agent"));
        bean.setRescookie(req.getCookie("hello-cookie"));
        bean.setSessionid(req.getSessionid(false));
        Flipper flipper = req.getFlipper();
        Sheet<HelloEntity> result = service.queryHello(bean, flipper);
        resp.finishJson(result);
    }

    @AuthIgnore
    @WebMapping(url = "/hello/find/", comment = "根据id删除Hello对象")
    @WebParam(name = "#", type = int.class, comment = "Hello对象id")
    public void find(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService2 service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        int id = Integer.parseInt(req.getRequstURILastPath());
        HelloEntity bean = service.findHello(id);
        resp.finishJson(bean);
    }
}
