package org.redkale.test.convert.pb;

/// *
// * To change this license header, choose License Headers in Project Properties.
// * To change this template file, choose Tools | Templates
// * and open the template in the editor.
// */
// package org.redkalex.test.protobuf;
//
// import java.util.Arrays;
// import org.redkale.convert.ConvertColumn;
// import org.redkale.convert.json.JsonConvert;
// import org.redkale.util.Utility;
// import org.redkale.convert.pb.ProtobufConvert;
//
/// **
// *
// * @author zhangjx
// */
// public class SimpleBean {
//
//    public static class PSimpleEntry {
//
//        @ConvertColumn(index = 1)
//        public int id = 66;
//
//        @ConvertColumn(index = 2)
//        public String name = "哈哈";
//
//        @ConvertColumn(index = 3)
//        public String email = "redkale@redkale.org";
//    }
//
//    public static class PTwoEntry {
//
//        @ConvertColumn(index = 1)
//        public int status = 2;
//
//        @ConvertColumn(index = 2)
//        public long createtime = System.currentTimeMillis();
//
//    }
//
//    @ConvertColumn(index = 1)
//    public PSimpleEntry simple;
//
//    @ConvertColumn(index = 2)
//    public PTwoEntry two;
//
//    @ConvertColumn(index = 3)
//    public String strings = "abcd";
//
//    @Override
//    public String toString() {
//        return JsonConvert.root().convertTo(this);
//    }
//
//    public static void main(String[] args) throws Throwable {
//        //System.out.println(ProtobufConvert.root().getProtoDescriptor(SimpleBean.class));
//        SimpleBean bean = new SimpleBean();
//        bean.simple = new PSimpleEntry();
//        bean.two = new PTwoEntry();
//        bean.strings = "abcde";
//
//        //-------------------------------
//        byte[] jsonbs = JsonConvert.root().convertToBytes(bean);
//        byte[] bs = ProtobufConvert.root().convertTo(bean);
//        Utility.println("predkale ", bs);
//        PSimpleBeanOuterClass.PSimpleBean.Builder builder = PSimpleBeanOuterClass.PSimpleBean.newBuilder();
//
//        PSimpleBeanOuterClass.PSimpleBean bean2 = createPSimpleBean(bean, builder);
//        byte[] bs2 = bean2.toByteArray();
//        Utility.println("protobuf ", bs2);
//        Thread.sleep(10);
//        if (!Arrays.equals(bs, bs2)) throw new RuntimeException("两者序列化出来的byte[]不一致");
//
//        System.out.println(bean);
//        System.out.println(ProtobufConvert.root().convertFrom(SimpleBean.class, bs).toString());
//        System.out.println(JsonConvert.root().convertFrom(SimpleBean.class, jsonbs).toString());
//
//    }
//
//    private static PSimpleBeanOuterClass.PSimpleBean createPSimpleBean(SimpleBean bean,
// PSimpleBeanOuterClass.PSimpleBean.Builder builder) {
//        if (builder == null) {
//            builder = PSimpleBeanOuterClass.PSimpleBean.newBuilder();
//        } else {
//            builder.clear();
//        }
//        PSimpleBeanOuterClass.PSimpleBean.PSimpleEntry.Builder sentry =
// PSimpleBeanOuterClass.PSimpleBean.PSimpleEntry.newBuilder();
//        sentry.setId(bean.simple.id);
//        sentry.setName(bean.simple.name);
//        sentry.setEmail(bean.simple.email);
//        builder.setSimple(sentry.build());
//
//        PSimpleBeanOuterClass.PSimpleBean.PTwoEntry.Builder tentry =
// PSimpleBeanOuterClass.PSimpleBean.PTwoEntry.newBuilder();
//        tentry.setStatus(bean.two.status);
//        tentry.setCreatetime(bean.two.createtime);
//        builder.setTwo(tentry.build());
//
//        builder.setStrings(bean.strings);
//
//        PSimpleBeanOuterClass.PSimpleBean bean2 = builder.build();
//        return bean2;
//    }
// }
