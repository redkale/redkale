/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.sncp.dyn;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.redkale.convert.Convert;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.Reader;
import org.redkale.convert.Writer;
import org.redkale.net.sncp.SncpActionServlet;
import org.redkale.net.sncp.SncpRequest;
import org.redkale.net.sncp.SncpResponse;
import org.redkale.service.Service;
import org.redkale.test.util.TestBean;
import org.redkale.util.Uint128;

/**
 *
 * @author zhangjx
 */
public class DynActionTestService_changeName extends SncpActionServlet {

    public DynActionTestService_changeName(
            String resourceName,
            Class resourceType,
            Service service,
            Uint128 serviceid,
            Uint128 actionid,
            final Method method) {
        super(resourceName, resourceType, service, serviceid, actionid, method);
    }

    @Override
    public void action(SncpRequest request, SncpResponse response) throws Throwable {
        Convert<Reader, Writer> convert = request.getConvert();
        Reader in = request.getReader();
        DynSncpActionParamBean_TestService_changeName bean = convert.convertFrom(paramComposeBeanType, in);
        TestService serviceObj = (TestService) service();
        CompletableFuture future = serviceObj.changeName(bean.arg1, bean.arg2, bean.arg3);
        response.finishFuture(paramHandlerType, future);
    }

    public static class DynSncpActionParamBean_TestService_changeName {

        public DynSncpActionParamBean_TestService_changeName() {}

        public DynSncpActionParamBean_TestService_changeName(Object[] params) {
            this.arg1 = (TestBean) params[0];
            this.arg2 = (String) params[1];
            this.arg3 = (int) params[2];
        }

        @ConvertColumn(index = 1)
        public TestBean arg1;

        @ConvertColumn(index = 2)
        public String arg2;

        @ConvertColumn(index = 3)
        public int arg3;
    }
}
