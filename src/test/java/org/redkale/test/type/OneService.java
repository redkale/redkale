/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.type;

import org.redkale.service.RetResult;

/**
 * @author zhangjx
 * @param <OR>
 * @param <OB>
 */
public class OneService<OR extends OneRound, OB extends OneBean> {

    public RetResult run(OR round, OB bean) {
        return RetResult.success();
    }
}
