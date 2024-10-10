/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.sncp.dyn;

import java.lang.reflect.Method;
import java.nio.channels.CompletionHandler;
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
public class DynActionTestService_update extends SncpActionServlet {

    public DynActionTestService_update(
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
        DynSncpActionParamBean_TestService_update bean = convert.convertFrom(paramComposeBeanType, in);
        bean.arg3 = response.getParamAsyncHandler();
        TestService serviceObj = (TestService) service();
        serviceObj.update(bean.arg1, bean.arg2, bean.arg3, bean.arg4, bean.arg5, bean.arg6);
        response.finishVoid();
    }

    public static class DynSncpActionParamBean_TestService_update {

        public DynSncpActionParamBean_TestService_update() {}

        public DynSncpActionParamBean_TestService_update(Object[] params) {
            this.arg1 = (long) params[0];
            this.arg2 = (short) params[1];
            this.arg3 = (CompletionHandler) params[2];
            this.arg4 = (TestBean) params[3];
            this.arg5 = (String) params[4];
            this.arg6 = (int) params[5];
        }

        @ConvertColumn(index = 1)
        public long arg1;

        @ConvertColumn(index = 2)
        public short arg2;

        @ConvertColumn(index = 3)
        public CompletionHandler<Boolean, TestBean> arg3;

        @ConvertColumn(index = 4)
        public TestBean arg4;

        @ConvertColumn(index = 5)
        public String arg5;

        @ConvertColumn(index = 6)
        public int arg6;
    }
}
