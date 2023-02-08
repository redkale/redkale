/*
 *
 */
package org.redkale.test.sncp;

import java.lang.reflect.Method;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import org.redkale.annotation.ResourceType;
import org.redkale.convert.bson.*;
import org.redkale.net.sncp.SncpDynServlet.SncpActionServlet;
import org.redkale.net.sncp.*;
import org.redkale.service.Service;
import org.redkale.test.util.TestBean;
import org.redkale.util.Uint128;

/**
 *
 * @author zhangjx
 */
public interface TestService extends Service {

    public boolean change(TestBean bean, String name, int id);

    public void insert(BooleanHandler handler, TestBean bean, String name, int id);

    public void update(long show, short v2, CompletionHandler<Boolean, TestBean> handler, TestBean bean, String name, int id);

    public CompletableFuture<String> changeName(TestBean bean, String name, int id);

    @ResourceType(TestService.class)
    public static class TestServiceImpl implements TestService {

        @Override
        public boolean change(TestBean bean, String name, int id) {
            return false;
        }

        @Override
        public void insert(BooleanHandler handler, TestBean bean, String name, int id) {
        }

        @Override
        public void update(long show, short v2, CompletionHandler<Boolean, TestBean> handler, TestBean bean, String name, int id) {
        }

        @Override
        public CompletableFuture<String> changeName(TestBean bean, String name, int id) {
            return null;
        }
    }

    public static class BooleanHandler implements CompletionHandler<Boolean, TestBean> {

        @Override
        public void completed(Boolean result, TestBean attachment) {
        }

        @Override
        public void failed(Throwable exc, TestBean attachment) {
        }

    }

    public static class DynActionTestService_change extends SncpActionServlet {

        public DynActionTestService_change(String resourceName, Class resourceType, Service service, Uint128 serviceid, Uint128 actionid, final Method method) {
            super(resourceName, resourceType, service, serviceid, actionid, method);
        }

        @Override
        public void action(SncpRequest request, SncpResponse response) throws Throwable {
            BsonConvert convert = request.getBsonConvert();
            BsonReader in = request.getBsonReader();
            TestBean arg1 = convert.convertFrom(paramTypes[1], in);
            String arg2 = convert.convertFrom(paramTypes[2], in);
            int arg3 = convert.convertFrom(paramTypes[3], in);
            TestService serviceObj = (TestService) service();
            Object rs = serviceObj.change(arg1, arg2, arg3);
            response.finish(boolean.class, rs);
        }
    }

    public static class DynActionTestService_insert extends SncpActionServlet {

        public DynActionTestService_insert(String resourceName, Class resourceType, Service service, Uint128 serviceid, Uint128 actionid, final Method method) {
            super(resourceName, resourceType, service, serviceid, actionid, method);
        }

        @Override
        public void action(SncpRequest request, SncpResponse response) throws Throwable {
            BsonConvert convert = request.getBsonConvert();
            BsonReader in = request.getBsonReader();
            BooleanHandler arg0 = response.getParamAsyncHandler();
            convert.convertFrom(CompletionHandler.class, in);
            TestBean arg1 = convert.convertFrom(paramTypes[2], in);
            String arg2 = convert.convertFrom(paramTypes[3], in);
            int arg3 = convert.convertFrom(paramTypes[4], in);
            TestService serviceObj = (TestService) service();
            serviceObj.insert(arg0, arg1, arg2, arg3);
            response.finishVoid();
        }
    }

    public static class DynActionTestService_update extends SncpActionServlet {

        public DynActionTestService_update(String resourceName, Class resourceType, Service service, Uint128 serviceid, Uint128 actionid, final Method method) {
            super(resourceName, resourceType, service, serviceid, actionid, method);
        }

        @Override
        public void action(SncpRequest request, SncpResponse response) throws Throwable {
            BsonConvert convert = request.getBsonConvert();
            BsonReader in = request.getBsonReader();
            long a1 = convert.convertFrom(paramTypes[1], in);
            short a2 = convert.convertFrom(paramTypes[2], in);
            CompletionHandler a3 = response.getParamAsyncHandler();
            convert.convertFrom(CompletionHandler.class, in);
            TestBean arg1 = convert.convertFrom(paramTypes[4], in);
            String arg2 = convert.convertFrom(paramTypes[5], in);
            int arg3 = convert.convertFrom(paramTypes[6], in);
            TestService serviceObj = (TestService) service();
            serviceObj.update(a1, a2, a3, arg1, arg2, arg3);
            response.finishVoid();
        }
    }

    public static class DynActionTestService_changeName extends SncpActionServlet {

        public DynActionTestService_changeName(String resourceName, Class resourceType, Service service, Uint128 serviceid, Uint128 actionid, final Method method) {
            super(resourceName, resourceType, service, serviceid, actionid, method);
        }

        @Override
        public void action(SncpRequest request, SncpResponse response) throws Throwable {
            BsonConvert convert = request.getBsonConvert();
            BsonReader in = request.getBsonReader();
            TestBean arg1 = convert.convertFrom(paramTypes[1], in);
            String arg2 = convert.convertFrom(paramTypes[2], in);
            int arg3 = convert.convertFrom(paramTypes[3], in);
            TestService serviceObj = (TestService) service();
            CompletableFuture future = serviceObj.changeName(arg1, arg2, arg3);
            response.finishFuture(paramHandlerResultType, future);
        }
    }
}
