/*
 */
package org.redkale.test.convert;

import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.function.*;
import org.junit.jupiter.api.*;
import org.redkale.convert.*;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.*;

/** @author zhangjx */
public class CustMessageTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        CustMessageTest test = new CustMessageTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        final BiFunction<Attribute, Object, Object> objFieldFunc = (Attribute t, Object u) -> {
            if (t.field().equals("retinfo")) return null;
            return t.get(u);
        };
        OnPlayerLeaveMessage msg1 = new OnPlayerLeaveMessage(100, "haha");
        System.out.println(msg1);
        OnPlayerLeaveMessage2 msg2 = new OnPlayerLeaveMessage2(100, "haha");
        System.out.println(msg2);
        if (!main) Assertions.assertEquals(msg1.toString(), msg2.toString());
        System.out.println();

        String json = msg1.toString();
        OnPlayerLeaveMessage2 newmsg2 = JsonConvert.root().convertFrom(OnPlayerLeaveMessage2.class, json);
        System.out.println(newmsg2);
        System.out.println();

        JsonConvert convert = JsonConvert.root().newConvert(objFieldFunc);
        System.out.println(convert.convertTo(msg1));
        System.out.println(convert.convertTo(msg2));
        if (!main) Assertions.assertEquals(convert.convertTo(msg1), convert.convertTo(msg2));
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

        public static Encodeable<Writer, BaseMessage> createConvertEnCoder(ConvertFactory factory, Class<?> clazz) {
            Encodeable valEncoder = factory.createEncoder(clazz, true);
            ObjectEncoder encoder = new ObjectEncoder<Writer, BaseMessage>(clazz) {
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

        public static Decodeable<Reader, BaseMessage> createConvertDeCoder(ConvertFactory factory, Class<?> clazz) {
            Decodeable valDecoder = factory.createDecoder(clazz, true);
            ObjectDecoder decoder = new ObjectDecoder<Reader, BaseMessage>(clazz) {
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
                public BaseMessage convertFrom(Reader in) {
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
