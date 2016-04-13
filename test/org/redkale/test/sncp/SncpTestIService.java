/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.sncp;

import org.redkale.service.*;
import org.redkale.source.DataCallArrayAttribute;

/**
 *
 * @author zhangjx
 */
public interface SncpTestIService extends Service {

    public String queryResult(SncpTestBean bean);

    public void insert(@DynCall(DataCallArrayAttribute.class) SncpTestBean... beans);

    public String updateBean(@DynCall(SncpTestService.CallAttribute.class) SncpTestBean bean);
}
