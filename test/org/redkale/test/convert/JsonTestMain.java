/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert;

import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.json.JsonFactory;
import org.redkale.util.TypeToken;
import java.lang.reflect.*;
import java.nio.*;
import java.util.*;

/**
 *
 * @author zhangjx
 */
public class JsonTestMain {

    private static final Type MAPTYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    public static void main(String[] args) throws Exception {
        JsonFactory factory = JsonFactory.root();
        factory.setTiny(true); 
        final JsonConvert convert = JsonFactory.root().getConvert();
        String json = "{\"access_token\":\"vVX2bIjN5P9TMOphDkStM96eNWapAehTuWAlVDO74aFaYxLwj2b-9-T9p_W2mfr9\",\"expires_in\":7200, \"aa\":\"\"}"; 
        Map<String, String> map = convert.convertFrom(MAPTYPE, json);
        System.out.println(map);
        System.out.println(convert.convertTo(map));
        ByteBuffer[] buffers = convert.convertTo(() -> ByteBuffer.allocate(1024), map);
        byte[] bs = new byte[buffers[0].remaining()];
        buffers[0].get(bs);
        System.out.println(new String(bs)); 
    }
}
