/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.mq;

import java.io.Serializable;
import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class HttpMessageContent implements Serializable {

    @Override
    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
