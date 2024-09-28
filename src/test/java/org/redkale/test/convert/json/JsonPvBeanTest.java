/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.convert.json;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.*;
import org.redkale.annotation.Comment;
import org.redkale.convert.json.JsonConvert;
import org.redkale.util.TypeToken;

/** @author zhangjx */
public class JsonPvBeanTest {

    public static void main(String[] args) throws Throwable {
        JsonPvBeanTest test = new JsonPvBeanTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        String json = "[\n"
                + "  {\n"
                + "    \"pagename\": \"首页\",\n"
                + "    \"cate\": \"home_page\",\n"
                + "    \"functions\": [\n"
                + "      {\n"
                + "        \"functionname\": \"茶室\",\n"
                + "        \"type\": \"tea\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"桌游\",\n"
                + "        \"type\": \"board\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"密室\",\n"
                + "        \"type\": \"escape\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"剧本杀\",\n"
                + "        \"type\": \"drama\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"PS5/switch\",\n"
                + "        \"type\": \"ps5\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"电竞\",\n"
                + "        \"type\": \"game\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"赛事\",\n"
                + "        \"type\": \"match\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"预约\",\n"
                + "        \"type\": \"book\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"充值\",\n"
                + "        \"type\": \"charge\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"福利中心\",\n"
                + "        \"type\": \"weal\"\n"
                + "      }\n"
                + "    ]\n"
                + "  },\n"
                + "  {\n"
                + "    \"pagename\": \"福利中心\",\n"
                + "    \"cate\": \"weal_page\",\n"
                + "    \"functions\": [\n"
                + "      {\n"
                + "        \"functionname\": \"卡券套餐\",\n"
                + "        \"type\": \"card\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"优惠券\",\n"
                + "        \"type\": \"coupon\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"充值\",\n"
                + "        \"type\": \"charge\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"团购\",\n"
                + "        \"type\": \"group\"\n"
                + "      }\n"
                + "    ]\n"
                + "  },\n"
                + "  {\n"
                + "    \"pagename\": \"地图找店\",\n"
                + "    \"cate\": \"map_page\",\n"
                + "    \"functions\": [\n"
                + "      {\n"
                + "        \"functionname\": \"搜索框\",\n"
                + "        \"type\": \"search\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"店铺详情\",\n"
                + "        \"type\": \"site\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"城市切换\",\n"
                + "        \"type\": \"city\"\n"
                + "      }\n"
                + "    ]\n"
                + "  },\n"
                + "  {\n"
                + "    \"pagename\": \"房间服务\",\n"
                + "    \"cate\": \"site_page\",\n"
                + "    \"functions\": [\n"
                + "      {\n"
                + "        \"functionname\": \"切换门店\",\n"
                + "        \"type\": \"venue\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"确认支付\",\n"
                + "        \"type\": \"pay\"\n"
                + "      }\n"
                + "    ]\n"
                + "  },\n"
                + "  {\n"
                + "    \"pagename\": \"个人中心\",\n"
                + "    \"cate\": \"personal_page\",\n"
                + "    \"functions\": [\n"
                + "      {\n"
                + "        \"functionname\": \"会员中心\",\n"
                + "        \"type\": \"vip\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"余额\",\n"
                + "        \"type\": \"amount\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"优惠券\",\n"
                + "        \"type\": \"coupon\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"卡券套餐\",\n"
                + "        \"type\": \"card\"\n"
                + "      },\n"
                + "      {\n"
                + "        \"functionname\": \"积分\",\n"
                + "        \"type\": \"exp\"\n"
                + "      }\n"
                + "    ]\n"
                + "  }\n"
                + "]\n"
                + "";

        List<JsonPvBean> list = null;
        list = JsonConvert.root().convertFrom(new TypeToken<List<JsonPvBean>>() {}.getType(), json);
        Assertions.assertNotNull(list);
        Assertions.assertEquals(5, list.size());
        System.out.println("-----------------");

        list = JsonConvert.root()
                .convertFrom(
                        new TypeToken<List<JsonPvBean>>() {}.getType(),
                        ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertNotNull(list);
        Assertions.assertEquals(5, list.size());
        System.out.println("-----------------");

        InputStream in = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        list = JsonConvert.root().convertFrom(new TypeToken<List<JsonPvBean>>() {}.getType(), in);
        Assertions.assertNotNull(list);
        Assertions.assertEquals(5, list.size());
        System.out.println(list);
    }

    public static class JsonPvBean {

        @Comment("页面名称")
        public String pagename;

        @Comment("页面类别")
        public String cate;

        @Comment("页面类别")
        public String code;

        @Comment("页面功能点")
        public List<Functions> functions;

        @Override
        public String toString() {
            return "{\"pagename\":\"" + pagename + "\",\"cate\":\"" + cate + "\",\"code\":\"" + code
                    + "\",\"functions\":" + functions + "}";
        }

        public static class Functions {

            @Comment("功能名称")
            public String functionname;

            @Comment("功能类型")
            public String type;

            @Override
            public String toString() {
                return "{\"functionname\":\"" + functionname + "\",\"type\":\"" + type + "\"}";
            }
        }
    }
}
