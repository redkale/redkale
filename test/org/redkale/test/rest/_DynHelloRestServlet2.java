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
    @WebAction(url = "/hello/create")
    public void create(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService2 service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        UserInfo user = currentUser(req);
        RetResult<HelloEntity> result = service.createHello(user, bean);
        resp.finishJson(result);
    }

    @AuthIgnore
    @WebAction(url = "/hello/delete/")
    public void delete(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService2 service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        int id = Integer.parseInt(req.getRequstURILastPath());
        service.deleteHello(id);
        resp.finishJson(RetResult.success());
    }

    @AuthIgnore
    @WebAction(url = "/hello/update")
    public void update(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService2 service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        service.updateHello(bean);
        resp.finishJson(RetResult.success());
    }

    @AuthIgnore
    @WebAction(url = "/hello/query")
    public void query(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService2 service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        HelloBean bean = req.getJsonParameter(HelloBean.class, "bean");
        Flipper flipper = req.getFlipper();
        Sheet<HelloEntity> result = service.queryHello(bean, flipper);
        resp.finishJson(result);
    }

    @AuthIgnore
    @WebAction(url = "/hello/find/")
    public void find(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService2 service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        int id = Integer.parseInt(req.getRequstURILastPath());
        HelloEntity bean = service.findHello(id);
        resp.finishJson(bean);
    }
}
