/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.ws;

import org.redkale.convert.json.JsonConvert;

/**
 *
 * @author zhangjx
 */
public class ChatMessage {

    public int fromuserid;
    
    public int touserid;

    public String fromusername;

    public String content;

    public String toString() {
        return JsonConvert.root().convertTo(this);
    }
}
