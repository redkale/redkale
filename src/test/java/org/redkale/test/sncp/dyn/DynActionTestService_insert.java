/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.sncp.dyn;

import java.lang.reflect.Method;
import org.redkale.convert.Convert;
import org.redkale.convert.ConvertColumn;
import org.redkale.convert.Reader;
import org.redkale.convert.Writer;
import org.redkale.net.sncp.SncpRequest;
import org.redkale.net.sncp.SncpResponse;
import org.redkale.net.sncp.SncpServlet.SncpActionServlet;
import org.redkale.service.Service;
import org.redkale.test.util.TestBean;
import org.redkale.util.Uint128;

/**
 *
 * @author zhangjx
 */
public class DynActionTestService_insert extends SncpActionServlet {

    public DynActionTestService_insert(
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
        DynActionTestService_insert_paramBean bean = convert.convertFrom(paramComposeType, in);
        bean.arg0 = response.getParamAsyncHandler();
        TestService serviceObj = (TestService) service();
        serviceObj.insert(bean.arg0, bean.arg1, bean.arg2, bean.arg3);
        response.finishVoid();
    }

    public static class DynActionTestService_insert_paramBean {

        public DynActionTestService_insert_paramBean() {}

        public DynActionTestService_insert_paramBean(Object[] params) {
            this.arg0 = (BooleanHandler) params[0];
            this.arg1 = (TestBean) params[1];
            this.arg2 = (String) params[2];
            this.arg3 = (int) params[3];
        }

        @ConvertColumn(index = 1)
        public BooleanHandler arg0;

        @ConvertColumn(index = 2)
        public TestBean arg1;

        @ConvertColumn(index = 3)
        public String arg2;

        @ConvertColumn(index = 4)
        public int arg3;
    }
}
