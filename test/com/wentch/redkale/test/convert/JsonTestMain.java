/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.convert;

import com.wentch.redkale.convert.json.*;
import com.wentch.redkale.util.*;
import java.lang.reflect.*;
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
    }
}
