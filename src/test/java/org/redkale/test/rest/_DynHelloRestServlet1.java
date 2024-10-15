package org.redkale.test.rest;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import org.redkale.annotation.Resource;
import org.redkale.boot.Application;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;
import org.redkale.source.Flipper;
import org.redkale.util.*;

@WebServlet(
        value = {"/hello/*"},
        repair = true)
public class _DynHelloRestServlet1 extends SimpleRestServlet {

    @Resource
    private HelloService _redkale_service;

    @Resource
    private Map<String, HelloService> _redkale_servicemap;

    public static void main(String[] args) throws Throwable {

        Application application = Application.create(true);
        HttpServer server = new HttpServer(application);
        HelloService service = new HelloService();
        RedkaleClassLoader classLoader = RedkaleClassLoader.currentClassLoader();
        Class clazz = SimpleRestServlet.class;
        System.out.println(server.addRestServlet(classLoader, service, null, clazz, "/pipes"));
        System.out.println(server.addRestServlet(classLoader, new HelloService(3), null, clazz, "/pipes"));

        AnyValueWriter conf = AnyValueWriter.create("port", "0");
        server.init(conf);
        server.start();
        Utility.sleep(100);
        final int port = server.getSocketAddress().getPort();
        HelloEntity entity = new HelloEntity();
        entity.setHelloname("my name");
        Map<String, Serializable> headers = new HashMap<>();
        headers.put("hello-res", "my res");
        // headers.put(Rest.REST_HEADER_RESNAME, "my-res");
        String url = "http://127.0.0.1:" + port + "/pipes/hello/update?entity=%7B%7D&bean2=%7B%7D";
        System.out.println(Utility.postHttpContent(url, headers, null));
        url = "http://127.0.0.1:" + port + "/pipes/hello/update2?entity=%7B%7D&bean2=%7B%7D";
        System.out.println(Utility.postHttpContent(url, headers, null));

        url = "http://127.0.0.1:" + port + "/pipes/hello/asyncfind/1234";
        System.out.println("异步查找: " + Utility.postHttpContent(url, headers, null));

        url = "http://127.0.0.1:" + port + "/pipes/hello/listmap?map=%7B'a':5%7D";
        System.out.println("listmap: " + Utility.postHttpContent(url, headers, null));

        url = "http://127.0.0.1:" + port + "/pipes/hello/create?entity=%7B%7D";
        System.out.println("增加记录: " + Utility.postHttpContent(url, headers, "{'a':2,'b':3}"));

        url = "http://127.0.0.1:" + port + "/pipes/hello/asyncfind/111111";
        System.out.println("listmap: " + Utility.postHttpContent(url, headers, null));
        url = "http://127.0.0.1:" + port + "/pipes/hello/asyncfind2/22222";
        System.out.println("listmap: " + Utility.postHttpContent(url, headers, null));
        url = "http://127.0.0.1:" + port + "/pipes/hello/asyncfind3/333333";
        System.out.println("listmap: " + Utility.postHttpContent(url, headers, null));
    }

    @HttpMapping(url = "/hello/create", auth = false)
    public void create(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _redkale_servicemap == null
                ? _redkale_service
                : _redkale_servicemap.get(req.getHeader(Rest.REST_HEADER_RESNAME, ""));
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        bean.setClientaddr(req.getRemoteAddr());
        bean.setResname(req.getHeader("hello-res"));
        UserInfo user = new UserInfo();
        RetResult<HelloEntity> result = service.createHello(user, bean, req.getBodyJson(Map.class));
        resp.finishJson(result);
    }

    @HttpMapping(url = "/hello/delete/", auth = false)
    public void delete(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _redkale_servicemap == null
                ? _redkale_service
                : _redkale_servicemap.get(req.getHeader(Rest.REST_HEADER_RESNAME, ""));
        int id = Integer.parseInt(
                req.getRequestPath().substring(req.getRequestPath().lastIndexOf('/') + 1));
        service.deleteHello(id);
        resp.finishJson(RetResult.success());
    }

    @HttpMapping(url = "/hello/update", auth = false)
    public void update(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _redkale_servicemap == null
                ? _redkale_service
                : _redkale_servicemap.get(req.getHeader(Rest.REST_HEADER_RESNAME, ""));
        String clientaddr = req.getRemoteAddr();
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        bean.setClientaddr(req.getRemoteAddr());
        bean.setResname(req.getHeader("hello-res"));
        service.updateHello(clientaddr, bean);
        resp.finishJson(RetResult.success());
    }

    @HttpMapping(url = "/hello/partupdate", auth = false)
    public void partupdate(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _redkale_servicemap == null
                ? _redkale_service
                : _redkale_servicemap.get(req.getHeader(Rest.REST_HEADER_RESNAME, ""));
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        bean.setClientaddr(req.getRemoteAddr());
        bean.setResname(req.getHeader("hello-res"));
        String[] cols = req.getJsonParameter(String[].class, "cols");
        service.updateHello(bean, cols);
        resp.finishJson(RetResult.success());
    }

    @HttpMapping(url = "/hello/query", auth = false)
    public void query(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _redkale_servicemap == null
                ? _redkale_service
                : _redkale_servicemap.get(req.getHeader(Rest.REST_HEADER_RESNAME, ""));
        HelloBean bean = req.getJsonParameter(HelloBean.class, "bean");
        bean.setClientaddr(req.getRemoteAddr());
        bean.setUseragent(req.getHeader("User-Agent"));
        bean.setRescookie(req.getCookie("hello-cookie"));
        bean.setSessionid(req.getSessionid(false));
        Flipper flipper = req.getFlipper();
        Sheet<HelloEntity> result = service.queryHello(bean, flipper);
        resp.finishJson(result);
    }

    @HttpMapping(url = "/hello/list", auth = false)
    public void list(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _redkale_servicemap == null
                ? _redkale_service
                : _redkale_servicemap.get(req.getHeader(Rest.REST_HEADER_RESNAME, ""));
        HelloBean bean = req.getJsonParameter(HelloBean.class, "bean");
        bean.setClientaddr(req.getRemoteAddr());
        bean.setUseragent(req.getHeader("User-Agent"));
        bean.setRescookie(req.getCookie("hello-cookie"));
        bean.setSessionid(req.getSessionid(false));
        List<HelloEntity> result = service.queryHello(bean);
        resp.finishJson(result);
    }

    @HttpMapping(url = "/hello/find/", auth = false)
    public void find(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _redkale_servicemap == null
                ? _redkale_service
                : _redkale_servicemap.get(req.getHeader(Rest.REST_HEADER_RESNAME, ""));
        int id = Integer.parseInt(
                req.getRequestPath().substring(req.getRequestPath().lastIndexOf('/') + 1));
        HelloEntity bean = service.findHello(id);
        resp.finishJson(bean);
    }

    @HttpMapping(url = "/hello/asyncfind/", auth = false)
    public void asyncfind(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _redkale_servicemap == null
                ? _redkale_service
                : _redkale_servicemap.get(req.getHeader(Rest.REST_HEADER_RESNAME, ""));
        int id = Integer.parseInt(
                req.getRequestPath().substring(req.getRequestPath().lastIndexOf('/') + 1));
        resp.finishJson(service.asyncFindHello(id));
    }

    @HttpMapping(url = "/hello/asyncfind2/", auth = false)
    public void asyncfind2(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _redkale_servicemap == null
                ? _redkale_service
                : _redkale_servicemap.get(req.getHeader(Rest.REST_HEADER_RESNAME, ""));
        int id = Integer.parseInt(
                req.getRequestPath().substring(req.getRequestPath().lastIndexOf('/') + 1));
        service.asyncFindHello(resp.createAsyncHandler(), id);
    }

    @HttpMapping(url = "/hello/asyncfind3/", auth = false)
    public void asyncfind3(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _redkale_servicemap == null
                ? _redkale_service
                : _redkale_servicemap.get(req.getHeader(Rest.REST_HEADER_RESNAME, ""));
        int id = Integer.parseInt(
                req.getRequestPath().substring(req.getRequestPath().lastIndexOf('/') + 1));
        service.asyncFindHello(resp.createAsyncHandler(HelloAsyncHandler.class), id);
    }
}
