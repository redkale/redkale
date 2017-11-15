/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.sncp;

import org.redkale.net.sncp.*;
import org.redkale.util.ResourceType;

/**
 *
 * @author zhangjx
 */
@ResourceType(SncpTestIService.class)
public class _DynLocalSncpTestService extends SncpTestServiceImpl {

    private SncpClient _redkale_client;

    @SncpDyn(remote = false, index = 1)
    public long _redkale_queryLongResult(boolean selfrunnable, boolean samerunnable, boolean diffrunnable, String a, int b, long value) {
        long rs = super.queryLongResult(a, b, value);
        if (_redkale_client == null) return rs;
        if (samerunnable) _redkale_client.remoteSameGroup(1, true, false, false, a, b, value);
        if (diffrunnable) _redkale_client.remoteDiffGroup(1, true, true, false, a, b, value);
        return rs;
    }
}
