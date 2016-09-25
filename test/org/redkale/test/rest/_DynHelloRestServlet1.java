package org.redkale.test.rest;

import java.io.IOException;
import java.util.*;
import javax.annotation.Resource;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;
import org.redkale.source.Flipper;
import org.redkale.util.*;
import org.redkale.util.AnyValue.DefaultAnyValue;

@WebServlet(value = {"/hello/*"}, repair = true)
public class _DynHelloRestServlet1 extends SimpleRestServlet {

    @Resource
    private HelloService _service;

    @Resource
    private Map<String, HelloService> _servicemap;

    public static void main(String[] args) throws Throwable {
        final int port = 8888;
        HelloService service = new HelloService();
        HttpServer server = new HttpServer();

        System.out.println(server.addRestServlet("", HelloService.class, service, SimpleRestServlet.class, "/pipes"));
        System.out.println(server.addRestServlet("my-res", HelloService.class, new HelloService(3), SimpleRestServlet.class, "/pipes"));

        DefaultAnyValue conf = DefaultAnyValue.create("port", "" + port);
        server.init(conf);
        server.start();
        Thread.sleep(100);

        HelloEntity entity = new HelloEntity();
        entity.setHelloname("my name");
        Map<String, String> headers = new HashMap<>();
        headers.put("hello-res", "my res");
        //headers.put(Rest.REST_HEADER_RESOURCE_NAME, "my-res");
        String url = "http://127.0.0.1:" + port + "/pipes/hello/update?entity={}&bean2={}";
        System.out.println(Utility.postHttpContent(url, headers, null));

    }

    @AuthIgnore
    @WebAction(url = "/hello/create")
    public void create(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        UserInfo user = currentUser(req);
        RetResult<HelloEntity> result = service.createHello(user, bean);
        resp.finishJson(result);
    }

    @AuthIgnore
    @WebAction(url = "/hello/delete/")
    public void delete(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        int id = Integer.parseInt(req.getRequstURILastPath());
        service.deleteHello(id);
        resp.finishJson(RetResult.success());
    }

    @AuthIgnore
    @WebAction(url = "/hello/update")
    public void update(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        String clientaddr = req.getRemoteAddr();
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        service.updateHello(clientaddr, bean);
        resp.finishJson(RetResult.success());
    }

    @AuthIgnore
    @WebAction(url = "/hello/partupdate")
    public void partupdate(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        HelloEntity bean = req.getJsonParameter(HelloEntity.class, "bean");
        String[] cols = req.getJsonParameter(String[].class, "cols");
        service.updateHello(bean, cols);
        resp.finishJson(RetResult.success());
    }

    @AuthIgnore
    @WebAction(url = "/hello/query")
    public void query(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        HelloBean bean = req.getJsonParameter(HelloBean.class, "bean");
        Flipper flipper = req.getFlipper();
        Sheet<HelloEntity> result = service.queryHello(bean, flipper);
        resp.finishJson(result);
    }

    @AuthIgnore
    @WebAction(url = "/hello/list")
    public void list(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        HelloBean bean = req.getJsonParameter(HelloBean.class, "bean");
        List<HelloEntity> result = service.queryHello(bean);
        resp.finishJson(result);
    }

    @AuthIgnore
    @WebAction(url = "/hello/find/")
    public void find(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        int id = Integer.parseInt(req.getRequstURILastPath());
        HelloEntity bean = service.findHello(id);
        resp.finishJson(bean);
    }

    @AuthIgnore
    @WebAction(url = "/hello/jsfind/")
    public void jsfind(HttpRequest req, HttpResponse resp) throws IOException {
        HelloService service = _servicemap == null ? _service : _servicemap.get(req.getHeader(Rest.REST_HEADER_RESOURCE_NAME, ""));
        int id = Integer.parseInt(req.getRequstURILastPath());
        HelloEntity bean = service.findHello(id);
        resp.finishJsResult("varhello", bean);
    }
}
