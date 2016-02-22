/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.media;

import com.alibaba.fastjson.*;
import com.google.gson.*;
import org.redkale.convert.json.*;

/**
 *
 * @author redkale
 */
public class JsonTestMain {

    public static void main(String[] args) throws Exception {
        final MediaContent entry = MediaContent.createDefault();
        final JsonConvert convert = JsonFactory.root().getConvert();
        final String entryString = convert.convertTo(entry);
        convert.convertFrom(MediaContent.class, entryString);
        System.out.println("redkale-convert: " + convert.convertTo(entry));
        JSON.parseObject(entryString, MediaContent.class);
        System.out.println("fastjson  1.2.7: " + JSON.toJSONString(entry));
        final Gson gson = new Gson();
        gson.fromJson(entryString, MediaContent.class);
        System.out.println("google-gson 2.4: " + gson.toJson(entry));
        System.out.println("------------------------------------------------");
        System.out.println("组件              序列化耗时(ms)             反序列化耗时(ms)");
        final int count = 10_0000;
        long s = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            convert.convertTo(entry);
        }
        long e = System.currentTimeMillis() - s;
        System.out.print("redkale-convert             " + e);

        s = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            convert.convertFrom(MediaContent.class, entryString);
        }
        e = System.currentTimeMillis() - s;
        System.out.println("\t                " + e);

        //----------------------------------------------------------------------------
        s = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            JSON.toJSONString(entry);
        }
        e = System.currentTimeMillis() - s;
        System.out.print("fastjson  1.2.7             " + e);

        s = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            JSON.parseObject(entryString, MediaContent.class);
        }
        e = System.currentTimeMillis() - s;
        System.out.println("\t                " + e);
        //----------------------------------------------------------------------------
        s = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            gson.toJson(entry);
        }
        e = System.currentTimeMillis() - s;
        System.out.print("google-gson 2.4            " + e);

        s = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            gson.fromJson(entryString, MediaContent.class);
        }
        e = System.currentTimeMillis() - s;
        System.out.println("\t                " + e);
        //----------------------------------------------------------------------------

    }
}

