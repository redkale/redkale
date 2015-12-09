/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import org.redkale.convert.json.JsonFactory;
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
