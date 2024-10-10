/*
 * Copyright (c) 2016-2116 Redkale
 * All rights reserved.
 */
package org.redkale.test.sncp.dyn;

import java.lang.reflect.Method;
import org.redkale.convert.Convert;
import org.redkale.convert.Reader;
import org.redkale.convert.Writer;
import org.redkale.net.sncp.SncpRequest;
import org.redkale.net.sncp.SncpResponse;
import org.redkale.net.sncp.SncpServlet;
import org.redkale.service.Service;
import org.redkale.test.util.TestBean;
import org.redkale.util.Uint128;

/**
 *
 * @author zhangjx
 */
public class DynActionTestService_hello extends SncpServlet.SncpActionServlet {

    public DynActionTestService_hello(
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
        TestBean bean = convert.convertFrom(paramComposeType, in);
        TestService serviceObj = (TestService) service();
        serviceObj.hello(bean);
        response.finishVoid();
    }
}
