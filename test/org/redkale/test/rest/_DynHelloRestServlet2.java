/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.rest;

import java.io.IOException;
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

    @AuthIgnore
    @WebAction(url = "/hello/create")
    public void create(HttpRequest req, HttpResponse resp) throws IOException {
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        UserInfo user = currentUser(req);
        RetResult<HelloEntity> result = _service.createHello(user, bean);
        resp.finishJson(result);
    }

    @AuthIgnore
    @WebAction(url = "/hello/delete/")
    public void delete(HttpRequest req, HttpResponse resp) throws IOException {
        int id = Integer.parseInt(req.getRequstURILastPath());
        _service.deleteHello(id);
        resp.finishJson(RetResult.success());
    }

    @AuthIgnore
    @WebAction(url = "/hello/update")
    public void update(HttpRequest req, HttpResponse resp) throws IOException {
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        _service.updateHello(bean);
        resp.finishJson(RetResult.success());
    }

    @AuthIgnore
    @WebAction(url = "/hello/query")
    public void query(HttpRequest req, HttpResponse resp) throws IOException {
        HelloBean bean = req.getJsonParameter(HelloBean.class, "bean");
        Flipper flipper = req.getFlipper();
        Sheet<HelloEntity> result = _service.queryHello(bean, flipper);
        resp.finishJson(result);
    }

    @AuthIgnore
    @WebAction(url = "/hello/find/")
    public void find(HttpRequest req, HttpResponse resp) throws IOException {
        int id = Integer.parseInt(req.getRequstURILastPath());
        HelloEntity bean = _service.findHello(id);
        resp.finishJson(bean);
    }
}
