/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.test.convert;

import com.wentch.redkale.convert.bson.BsonConvert;
import com.wentch.redkale.convert.bson.BsonFactory;
import com.wentch.redkale.convert.json.*;
import com.wentch.redkale.util.*;
import java.io.Serializable;
import java.util.Arrays;

/**
 *
 * @author zhangjx
 */
public class BsonTestMain {
    
    public static void main(String[] args) throws Exception {
        Serializable[] sers = new Serializable[]{"aaa",4};
        final BsonConvert convert = BsonFactory.root().getConvert();
      byte[] bytes =  convert.convertTo(sers);
      Serializable[]  a = convert.convertFrom(Serializable[].class, bytes);
        System.out.println(Arrays.toString(a));
        main2(args);
    }
    public static void main2(String[] args) throws Exception {
        final BsonConvert convert = BsonFactory.root().getConvert();
        TestEntry2 entry = TestEntry2.create();
        byte[] bytes = convert.convertTo(TestEntry.class, entry);
        Utility.println(null,bytes); 
        System.out.println(JsonFactory.root().getConvert().convertTo(entry)); 
        TestEntry rs  = convert.convertFrom(TestEntry.class, bytes);
        System.out.println(rs.toString());
        System.out.println(JsonFactory.root().getConvert().convertTo(rs)); 
        
        TestComplextBean bean = new TestComplextBean();
        byte[] bytes2 = convert.convertTo(Object.class, bean);
        System.out.println(convert.convertFrom(TestComplextBean.class, bytes2).toString());
    }
}
