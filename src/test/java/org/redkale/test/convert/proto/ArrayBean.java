/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.proto;

import java.util.*;
import org.redkale.util.Utility;
import org.redkale.convert.proto.ProtobufConvert;

/**
 *
 * @author zhangjx
 */
public class ArrayBean {

    public static class IntArrayBean {

        public int[] values1;
    }

    public static class IntListBean {

        public List<Integer> values2;
    }

    public static class IntegerArrayBean {

        public Integer[] values3;
    }

    public static void main(String[] args) throws Throwable {
        IntArrayBean bean1 = new IntArrayBean();
        bean1.values1 = new int[]{2, 3, 4};
        IntListBean bean2 = new IntListBean();
        bean2.values2 = Utility.ofList(2, 3, 4);
        IntegerArrayBean bean3 = new IntegerArrayBean();
        bean3.values3 = new Integer[]{2, 3, 4};
        byte[] bs1 = ProtobufConvert.root().convertTo(bean1);
        byte[] bs2 = ProtobufConvert.root().convertTo(bean2);
        byte[] bs3 = ProtobufConvert.root().convertTo(bean3);
        if (!Arrays.equals(bs1, bs2)) {
            Utility.println("int数组: ", bs1);
            Utility.println("int列表: ", bs2);
        } else if (!Arrays.equals(bs1, bs3)) {
            Utility.println("int数组: ", bs1);
            Utility.println("int集合: ", bs3);
        } else {
            System.out.println("两者相同");
        }
        IntArrayBean bean11 = ProtobufConvert.root().convertFrom(IntArrayBean.class, bs1);
        IntListBean bean22 = ProtobufConvert.root().convertFrom(IntListBean.class, bs2);
        IntegerArrayBean bean33 = ProtobufConvert.root().convertFrom(IntegerArrayBean.class, bs3);
        System.out.println(Arrays.toString(bean11.values1));
        System.out.println(bean22.values2);
        System.out.println(Arrays.toString(bean33.values3));
    }
}
