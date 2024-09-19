/*
 */
package org.redkale.test.convert.pb;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.pb.ProtobufConvert;
import org.redkale.convert.pb.ProtobufFactory;
import org.redkale.convert.pb.ProtobufObjectDecoder;
import org.redkale.convert.pb.ProtobufObjectEncoder;
import org.redkale.convert.pb.ProtobufReader;
import org.redkale.util.*;

/** @author zhangjx */
public class PBCustMessageTest {

    public static void main(String[] args) throws Throwable {
        PBCustMessageTest test = new PBCustMessageTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        final BiFunction<Attribute, Object, Object> objFieldFunc = (Attribute t, Object u) -> {
            if (t.field().equals("retinfo")) return null;
            return t.get(u);
        };
        OnPlayerLeaveMessage msg1 = new OnPlayerLeaveMessage(100, "haha");
        byte[] bs1 = ProtobufConvert.root().convertTo(msg1);
        OnPlayerLeaveMessage2 msg2 = new OnPlayerLeaveMessage2(100, "haha");
        byte[] bs2 = ProtobufConvert.root().convertTo(msg2);
        System.out.println(Arrays.toString(bs1));
        System.out.println(Arrays.toString(bs2));
        Assertions.assertEquals(Arrays.toString(bs1), Arrays.toString(bs2));
        System.out.println();

        OnPlayerLeaveMessage2 newmsg2 = ProtobufConvert.root().convertFrom(OnPlayerLeaveMessage2.class, bs1);
        byte[] newbs2 = ProtobufConvert.root().convertTo(newmsg2);
        System.out.println(Arrays.toString(newbs2));
        Assertions.assertEquals(Arrays.toString(bs1), Arrays.toString(newbs2));
        System.out.println();

        ProtobufConvert convert = ProtobufConvert.root().newConvert(objFieldFunc);
        System.out.println(Arrays.toString(convert.convertTo(msg1)));
        System.out.println(Arrays.toString(convert.convertTo(msg2)));
        Assertions.assertEquals(Arrays.toString(convert.convertTo(msg1)), Arrays.toString(convert.convertTo(msg2)));
        System.out.println();
    }

    public static interface BaseMessage {

        @Inherited
        @Documented
        @Target({TYPE})
        @Retention(RUNTIME)
        public @interface MessageName {

            String value();
        }

        public static String getMessageName(Class<?> clazz) {
            MessageName mn = clazz.getAnnotation(MessageName.class);
            if (mn != null) return mn.value();
            char[] fieldChars = clazz.getSimpleName().toCharArray();
            fieldChars[0] = Character.toLowerCase(fieldChars[0]);
            return new String(fieldChars);
        }

        public static Encodeable<Writer, BaseMessage> createConvertCoder(ProtobufFactory factory, Class<?> clazz) {
            Encodeable valEncoder = factory.createEncoder(clazz, true);
            ObjectEncoder encoder = new ProtobufObjectEncoder<BaseMessage>(clazz) {
                @Override
                protected void afterInitEnMember(ConvertFactory factory) {
                    Function func = t -> t;
                    Attribute attribute = Attribute.create(clazz, getMessageName(clazz), clazz, func, null);
                    EnMember member = new EnMember(attribute, valEncoder, null, null, null);
                    setIndex(member, 1);
                    setPosition(member, 1);
                    initForEachEnMember(factory, member);
                    this.initFieldMember(new EnMember[] {member});
                }
            };
            encoder.init(factory);
            return encoder;
        }

        public static Decodeable<Reader, BaseMessage> createConvertDeCoder(ProtobufFactory factory, Class<?> clazz) {
            Decodeable valDecoder = factory.createDecoder(clazz, true);
            ObjectDecoder decoder = new ProtobufObjectDecoder<BaseMessage>(clazz) {
                @Override
                protected void afterInitDeMember(ConvertFactory factory) {
                    this.creator = (Creator) objs -> new Object[1];
                    Function func = t -> t;
                    BiConsumer consumer = (t, v) -> ((Object[]) t)[0] = v;
                    Attribute attribute = Attribute.create(clazz, getMessageName(clazz), clazz, func, consumer);
                    DeMember member = new DeMember(attribute, valDecoder, null, null);
                    setIndex(member, 1);
                    setPosition(member, 1);
                    initForEachDeMember(factory, member);
                    this.initFieldMember(new DeMember[] {member});
                }

                @Override
                public BaseMessage convertFrom(ProtobufReader in) {
                    Object result = (Object) super.convertFrom(in);
                    return (BaseMessage) ((Object[]) result)[0];
                }
            };
            decoder.init(factory);
            return decoder;
        }
    }

    @BaseMessage.MessageName("onPlayerLeaveMessage")
    public static class OnPlayerLeaveMessage2 implements BaseMessage {

        @ConvertColumn(index = 1)
        public int userid;

        @ConvertColumn(index = 2)
        public String retinfo;

        public OnPlayerLeaveMessage2() {}

        public OnPlayerLeaveMessage2(int userid, String retinfo) {
            this.userid = userid;
            this.retinfo = retinfo;
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }

    public static class OnPlayerLeaveMessage {

        @ConvertColumn(index = 1)
        private OnPlayerLeaveContent onPlayerLeaveMessage;

        public OnPlayerLeaveMessage() {}

        public OnPlayerLeaveMessage(int userid) {
            this.onPlayerLeaveMessage = new OnPlayerLeaveContent(userid);
        }

        public OnPlayerLeaveMessage(int userid, String retinfo) {
            this.onPlayerLeaveMessage = new OnPlayerLeaveContent(userid, retinfo);
        }

        public OnPlayerLeaveContent getOnPlayerLeaveMessage() {
            return onPlayerLeaveMessage;
        }

        public void setOnPlayerLeaveMessage(OnPlayerLeaveContent onPlayerLeaveMessage) {
            this.onPlayerLeaveMessage = onPlayerLeaveMessage;
        }

        public static class OnPlayerLeaveContent {

            @ConvertColumn(index = 1)
            public int userid;

            @ConvertColumn(index = 2)
            public String retinfo;

            public OnPlayerLeaveContent() {}

            public OnPlayerLeaveContent(int userid) {
                this.userid = userid;
            }

            public OnPlayerLeaveContent(int userid, String retinfo) {
                this.userid = userid;
                this.retinfo = retinfo;
            }

            @Override
            public String toString() {
                return JsonConvert.root().convertTo(this);
            }
        }

        @Override
        public String toString() {
            return JsonConvert.root().convertTo(this);
        }
    }
}
