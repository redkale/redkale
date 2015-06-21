/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.convert;

import com.wentch.redkale.convert.json.JsonFactory;
import java.io.Serializable;

/**
 *
 * @author zhangjx
 */
public abstract class BasedEntity implements Serializable {

    @Override
    public String toString() {
        return JsonFactory.root().getConvert().convertTo(this);
    }
}
