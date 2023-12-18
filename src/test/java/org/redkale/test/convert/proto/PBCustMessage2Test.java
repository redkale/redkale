/*
 */
package org.redkale.test.convert.proto;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.Arrays;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.convert.proto.ProtobufConvert;
import org.redkale.convert.proto.ProtobufObjectDecoder;
import org.redkale.convert.proto.ProtobufObjectEncoder;
import org.redkale.convert.proto.ProtobufReader;
import org.redkale.util.*;

/**
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class PBCustMessage2Test {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        PBCustMessage2Test test = new PBCustMessage2Test();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        final BiFunction<Attribute, Object, Object> objFieldFunc = (Attribute t, Object u) -> {
            if (t.field().equals("retinfo")) {
                return null;
            }
            return t.get(u);
        };
        OnPlayerLeaveMessage msg1 = new OnPlayerLeaveMessage(100, "haha");
        byte[] bs1 = ProtobufConvert.root().convertTo(msg1);
        OnPlayerLeaveMessage2 msg2 = new OnPlayerLeaveMessage2(100, "haha");
        byte[] bs2 = ProtobufConvert.root().convertTo(msg2);
        System.out.println(Arrays.toString(bs1));
        System.out.println(Arrays.toString(bs2));
        if (!main) {
            Assertions.assertEquals(Arrays.toString(bs1), Arrays.toString(bs2));
        }
        System.out.println();

        OnPlayerLeaveMessage2 newmsg2 = ProtobufConvert.root().convertFrom(OnPlayerLeaveMessage2.class, bs1);
        byte[] newbs2 = ProtobufConvert.root().convertTo(newmsg2);
        System.out.println(Arrays.toString(newbs2));
        if (!main) {
            Assertions.assertEquals(Arrays.toString(bs1), Arrays.toString(newbs2));
        }
        System.out.println();

        ProtobufConvert convert = ProtobufConvert.root().newConvert(objFieldFunc);
        System.out.println(Arrays.toString(convert.convertTo(msg1)));
        System.out.println(Arrays.toString(convert.convertTo(msg2)));
        if (!main) {
            Assertions.assertEquals(Arrays.toString(convert.convertTo(msg1)), Arrays.toString(convert.convertTo(msg2)));
        }
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
            if (mn != null) {
                return mn.value();
            }
            char[] fieldChars = clazz.getSimpleName().toCharArray();
            fieldChars[0] = Character.toLowerCase(fieldChars[0]);
            return new String(fieldChars);
        }

        public static Encodeable<Writer, BaseMessage> createConvertEnCoder(final ConvertFactory factory, final Class<? extends BaseMessage> clazz) {
            Encodeable valEncoder = factory.createEncoder(clazz, true);
            final String eventName = getMessageName(clazz);
            ObjectEncoder encoder = new ProtobufObjectEncoder< BaseMessage>(clazz) {
                @Override
                protected void afterInitEnMember(ConvertFactory factory) {
                    Function func1 = t -> eventName;
                    Attribute attribute1 = Attribute.create(clazz, "event", String.class, func1, null);
                    EnMember member1 = new EnMember(attribute1, factory.loadEncoder(String.class), null, null);
                    setIndex(member1, 1);
                    setPosition(member1, 1);
                    initForEachEnMember(factory, member1);

                    Function func2 = t -> t;
                    Attribute attribute2 = Attribute.create(clazz, "data", clazz, func2, null);
                    EnMember member2 = new EnMember(attribute2, valEncoder, null, null);
                    setIndex(member2, 2);
                    setPosition(member2, 2);
                    initForEachEnMember(factory, member2);
                    this.members = new EnMember[]{member1, member2};
                }
            };
            encoder.init(factory);
            return encoder;
        }

        public static Decodeable<Reader, BaseMessage> createConvertDeCoder(final ConvertFactory factory, final Class<? extends BaseMessage> clazz) {
            Decodeable valDecoder = factory.createDecoder(clazz, true);
            final String eventName = getMessageName(clazz);
            ObjectDecoder decoder = new ProtobufObjectDecoder< BaseMessage>(clazz) {
                @Override
                protected void afterInitDeMember(ConvertFactory factory) {
                    Function func1 = t -> eventName;
                    Attribute attribute1 = Attribute.create(clazz, "event", String.class, func1, null);
                    DeMember member1 = new DeMember(attribute1, factory.loadDecoder(String.class), null, null);
                    setIndex(member1, 1);
                    setPosition(member1, 1);
                    initForEachDeMember(factory, member1);

                    this.creator = (Creator) objs -> new Object[1];
                    Function func2 = t -> t;
                    BiConsumer consumer2 = (t, v) -> ((Object[]) t)[0] = v;
                    Attribute attribute2 = Attribute.create(clazz, "data", clazz, func2, consumer2);
                    DeMember member2 = new DeMember(attribute2, valDecoder, null, null);
                    setIndex(member2, 2);
                    setPosition(member2, 2);
                    initForEachDeMember(factory, member2);
                    this.members = new DeMember[]{member1, member2};
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

        public OnPlayerLeaveMessage2() {
        }

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
        private String event = "onPlayerLeaveMessage";

        @ConvertColumn(index = 2)
        private OnPlayerLeaveContent result;

        public OnPlayerLeaveMessage() {
        }

        public OnPlayerLeaveMessage(int userid) {
            this.result = new OnPlayerLeaveContent(userid);
        }

        public OnPlayerLeaveMessage(int userid, String retinfo) {
            this.result = new OnPlayerLeaveContent(userid, retinfo);
        }

        public String getEvent() {
            return event;
        }

        public void setEvent(String event) {
            this.event = event;
        }

        public OnPlayerLeaveContent getResult() {
            return result;
        }

        public void setResult(OnPlayerLeaveContent result) {
            this.result = result;
        }

        public static class OnPlayerLeaveContent {

            @ConvertColumn(index = 1)
            public int userid;

            @ConvertColumn(index = 2)
            public String retinfo;

            public OnPlayerLeaveContent() {
            }

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
