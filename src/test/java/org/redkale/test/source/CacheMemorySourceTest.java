/*
 *
 */
package org.redkale.test.source;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.redkale.convert.json.JsonConvert;
import org.redkale.source.CacheEventListener;
import org.redkale.source.CacheMemorySource;
import org.redkale.source.CacheScoredValue;
import org.redkale.source.Flipper;
import org.redkale.util.TypeToken;
import org.redkale.util.Utility;

/**
 *
 * @author zhangjx
 */
public class CacheMemorySourceTest {

    public static void main(String[] args) throws Throwable {
        CacheMemorySourceTest test = new CacheMemorySourceTest();
        test.run();
    }

    @Test
    public void run() throws Exception {
        CacheMemorySource source = new CacheMemorySource("");
        source.init(null);
        System.out.println("------------------------------------");
        source.del("stritem1", "stritem2", "stritem1x");
        source.setString("stritem1", "value1");
        source.setString("stritem2", "value2");
        Assertions.assertEquals("value2", source.getDelString("stritem2"));
        Assertions.assertTrue(source.getDelString("stritem2") == null);
        source.setString("stritem2", "value2");

        List<String> list = source.keysStartsWith("stritem");
        System.out.println("stritem开头的key有两个: " + list);
        Assertions.assertTrue(Utility.equalsElement(List.of("stritem2", "stritem1"), list));

        Map<String, String> map = source.mgetsString("stritem1", "stritem2");
        System.out.println("[有值] MGET : " + map);
        Assertions.assertTrue(Utility.equalsElement(Utility.ofMap("stritem1", "value1", "stritem2", "value2"), map));

        List<String> array = source.mgetString("stritem1", "stritem2");
        System.out.println("[有值] MGET : " + array);
        Assertions.assertTrue(Utility.equalsElement(List.of("value1", "value2"), array));

        Assertions.assertFalse(source.persist("stritem1"));
        Assertions.assertTrue(source.rename("stritem1", "stritem1x"));
        Assertions.assertEquals("value1", source.getString("stritem1x"));
        Assertions.assertEquals(null, source.getString("stritem1"));
        Assertions.assertFalse(source.renamenx("stritem1x", "stritem2"));
        Assertions.assertEquals("value2", source.getString("stritem2"));
        Assertions.assertTrue(source.renamenx("stritem1x", "stritem1"));

        source.del("intitem1", "intitem2");
        source.setLong("intitem1", 333);
        source.setLong("intitem2", 444);

        map = source.mgetsString("intitem1", "intitem22", "intitem2");
        System.out.println("[有值] MGET : " + map);
        Assertions.assertTrue(Utility.equalsElement(Utility.ofMap("intitem1", "333", "intitem2", "444"), map));

        array = source.mgetString("intitem1", "intitem22", "intitem2");
        System.out.println("[有值] MGET : " + array);
        List<String> ss = new ArrayList<>();
        ss.add("333");
        ss.add(null);
        ss.add("444");
        Assertions.assertTrue(Utility.equalsElement(ss, array));

        source.del("objitem1", "objitem2");
        source.mset(Utility.ofMap("objitem1", new Flipper(10), "objitem2", new Flipper(20)));

        Map<String, Flipper> flippermap = source.mgets(Flipper.class, "objitem1", "objitem2");
        System.out.println("[有值] MGET : " + flippermap);
        Assertions.assertTrue(Utility.equalsElement(Utility.ofMap("objitem1", new Flipper(10), "objitem2", new Flipper(20)), flippermap));

        source.del("key1", "key2", "300");
        source.setex("key1", 1000, String.class, "value1");
        source.set("key1", String.class, "value1");
        source.setString("keystr1", "strvalue1");
        source.setLong("keylong1", 333L);
        source.set("300", String.class, "4000");
        Object obj = source.getex("key1", 3500, String.class);
        System.out.println("[有值] key1 GET : " + obj);
        Assertions.assertEquals("value1", obj);

        obj = source.get("300", String.class);
        System.out.println("[有值] 300 GET : " + obj);
        Assertions.assertEquals("4000", obj);

        obj = source.get("key1", String.class);
        System.out.println("[有值] key1 GET : " + obj);
        Assertions.assertEquals("value1", obj);

        obj = source.getSet("key1", String.class, "value11");
        System.out.println("[旧值] key1 GETSET : " + obj);
        Assertions.assertEquals("value1", obj);

        obj = source.get("key2", String.class);
        System.out.println("[无值] key2 GET : " + obj);
        Assertions.assertNull(obj);

        obj = source.getString("keystr1");
        System.out.println("[有值] keystr1 GET : " + obj);
        Assertions.assertEquals("strvalue1", obj);

        long num = source.getLong("keylong1", 0L);
        System.out.println("[有值] keylong1 GET : " + null);
        Assertions.assertEquals(333L, num);

        boolean bool = source.exists("key1");
        System.out.println("[有值] key1 EXISTS : " + bool);
        Assertions.assertTrue(bool);

        bool = source.exists("key2");
        System.out.println("[无值] key2 EXISTS : " + bool);
        Assertions.assertFalse(bool);

        source.del("keys3");
        source.rpush("keys3", String.class, "vals1");
        source.rpush("keys3", String.class, "vals2");
        System.out.println("-------- keys3 追加了两个值 --------");

        Collection col = source.lrangeString("keys3");
        System.out.println("[两值] keys3 VALUES : " + col);
        Assertions.assertTrue(Utility.equalsElement(List.of("vals1", "vals2"), col));

        bool = source.exists("keys3");
        System.out.println("[有值] keys3 EXISTS : " + bool);
        Assertions.assertTrue(bool);

        source.lrem("keys3", String.class, "vals1");
        col = source.lrangeString("keys3");
        System.out.println("[一值] keys3 VALUES : " + col);
        Assertions.assertIterableEquals(List.of("vals2"), col);
        source.rpush("keys3", String.class, "vals2");
        source.rpush("keys3", String.class, "vals3");
        source.rpush("keys3", String.class, "vals4");
        Assertions.assertEquals("vals4", source.rpopString("keys3"));
        source.lpush("keys3", String.class, "vals0");
        Assertions.assertEquals("vals0", source.lpopString("keys3"));
        String rlv = source.rpoplpush("keys3", "keys3-2", String.class);
        Assertions.assertEquals("vals3", rlv);

        source.del("keys3");
        source.lpushString("keys3", "vals20");
        source.lpushString("keys3", "vals10");
        System.out.println("keys3-list: " + source.lrangeString("keys3"));
        Assertions.assertEquals("vals10", source.lindexString("keys3", 0));
        Assertions.assertEquals("vals20", source.lindexString("keys3", -1));
        source.linsertBeforeString("keys3", "vals10", "vals00");
        source.linsertAfterString("keys3", "vals10", "vals15");
        Assertions.assertEquals(0, source.linsertBeforeString("keys_3", "vals10", "vals00"));
        Assertions.assertEquals(-1, source.linsertBeforeString("keys3", "vals90", "vals00"));
        Assertions.assertEquals(4, source.llen("keys3"));
        Assertions.assertIterableEquals(List.of("vals00", "vals10", "vals15", "vals20"), source.lrangeString("keys3"));

        source.del("stringmap");
        source.sadd("stringmap", JsonConvert.TYPE_MAP_STRING_STRING, Utility.ofMap("a", "aa", "b", "bb"));
        source.sadd("stringmap", JsonConvert.TYPE_MAP_STRING_STRING, Utility.ofMap("c", "cc", "d", "dd"));
        col = source.smembers("stringmap", JsonConvert.TYPE_MAP_STRING_STRING);
        System.out.println("[两值] stringmap VALUES : " + col);
        Assertions.assertTrue(Utility.equalsElement(List.of(Utility.ofMap("c", "cc", "d", "dd"), Utility.ofMap("a", "aa", "b", "bb")), col));

        source.del("sets3");
        source.del("sets4");
        source.del("sets5");
        source.sadd("sets3", String.class, "setvals1");
        source.sadd("sets3", String.class, "setvals2");
        source.sadd("sets3", String.class, "setvals1");
        source.sadd("sets4", String.class, "setvals2");
        source.sadd("sets4", String.class, "setvals1");
        col = source.smembersString("sets3");
        System.out.println("[两值] sets3 VALUES : " + col);
        List col2 = new ArrayList(col);
        Collections.sort(col2);
        Assertions.assertIterableEquals(List.of("setvals1", "setvals2"), col2);

        bool = source.exists("sets3");
        System.out.println("[有值] sets3 EXISTS : " + bool);
        Assertions.assertTrue(bool);

        bool = source.sismember("sets3", String.class, "setvals2");
        System.out.println("[有值] sets3-setvals2 EXISTSITEM : " + bool);
        Assertions.assertTrue(bool);

        bool = source.sismember("sets3", String.class, "setvals3");
        System.out.println("[无值] sets3-setvals3 EXISTSITEM : " + bool);
        Assertions.assertFalse(bool);

        source.srem("sets3", String.class, "setvals1");
        col = source.smembersString("sets3");
        System.out.println("[一值] sets3 VALUES : " + col);
        Assertions.assertIterableEquals(List.of("setvals2"), col);

        long size = source.scard("sets3");
        System.out.println("sets3 大小 : " + size);
        Assertions.assertEquals(1, size);

        col = source.keys();
        System.out.println("all keys: " + col);

        col = source.keys("key*");
        Collections.sort((List<String>) col);
        System.out.println("key startkeys: " + col);
        Assertions.assertIterableEquals(List.of("key1", "keylong1", "keys3", "keys3-2", "keystr1"), col);

        num = source.incr("newnum");
        System.out.println("newnum 值 : " + num);
        Assertions.assertEquals(1, num);

        num = source.decr("newnum");
        System.out.println("newnum 值 : " + num);
        Assertions.assertEquals(0, num);

        Map<String, Collection> mapcol = new LinkedHashMap<>();
        mapcol.put("sets3", source.smembersString("sets3"));
        mapcol.put("sets4", source.smembersString("sets4"));
        System.out.println("sets3&sets4:  " + mapcol);
        Map<String, Collection> news = new HashMap<>();
        mapcol.forEach((x, y) -> {
            if (y instanceof Set) {
                List newy = new ArrayList(y);
                Collections.sort(newy);
                news.put(x, newy);
            } else {
                Collections.sort((List) y);
            }
        });
        mapcol.putAll(news);
        Assertions.assertEquals(Utility.ofMap("sets3", List.of("setvals2"), "sets4", List.of("setvals1", "setvals2")).toString(), mapcol.toString());

        source.del("sets3");
        source.del("sets4");
        source.del("sets5");
        source.del("sets6");
        source.saddString("sets3", "setvals1", "setvals2", "setvals3", "setvals4", "setvals5");
        source.saddString("sets4", "setvals3", "setvals6", "setvals7", "setvals8");
        source.saddString("sets5", "setvals5", "setvals6", "setvals7", "setvals8");
        Set<String> diffanswer = new TreeSet<>(Set.of("setvals1", "setvals2", "setvals4"));
        Set<String> diffset = new TreeSet<>(source.sdiffString("sets3", "sets4", "sets5"));
        System.out.println("sdiff: " + diffset);
        Assertions.assertIterableEquals(diffanswer, diffset);
        source.sdiffstore("sets6", "sets3", "sets4", "sets5");
        diffset = new TreeSet<>(source.smembersString("sets6"));
        System.out.println("sdiffstore: " + diffset);
        Assertions.assertIterableEquals(diffanswer, diffset);

        source.del("sets3");
        source.del("sets4");
        source.del("sets5");
        source.del("sets6");
        source.saddString("sets3", "setvals1", "setvals2", "setvals3", "setvals4", "setvals5");
        source.saddString("sets4", "setvals2", "setvals3", "setvals5", "setvals8");
        source.saddString("sets5", "setvals5", "setvals6", "setvals7", "setvals8");
        Set<String> interanswer = new TreeSet<>(Set.of("setvals5"));
        Set<String> interset = new TreeSet<>(source.sinterString("sets3", "sets4", "sets5"));
        System.out.println("sinter: " + interset);
        Assertions.assertIterableEquals(interanswer, interset);
        source.sinterstore("sets6", "sets3", "sets4", "sets5");
        interset = new TreeSet<>(source.smembersString("sets6"));
        System.out.println("sinterstore: " + interset);
        Assertions.assertIterableEquals(interanswer, interset);

        source.del("sets6");
        Set<String> unionanswer = new TreeSet<>(Set.of("setvals1", "setvals2", "setvals3", "setvals4", "setvals5", "setvals6", "setvals7", "setvals8"));
        Set<String> unionset = new TreeSet<>(source.sunionString("sets3", "sets4", "sets5"));
        System.out.println("sunion: " + unionset);
        Assertions.assertIterableEquals(unionanswer, unionset);
        source.sunionstore("sets6", "sets3", "sets4", "sets5");
        unionset = new TreeSet<>(source.smembersString("sets6"));
        System.out.println("sunionstore: " + unionset);
        Assertions.assertIterableEquals(unionanswer, unionset);

        List<Boolean> ems = source.smismembers("sets3", "setvals3", "setvals33");
        System.out.println("smismembers: " + ems);
        Assertions.assertIterableEquals(List.of(true, false), ems);
        List<String> rands = List.of("setvals1", "setvals2", "setvals3", "setvals4", "setvals5");
        List<String> rand2 = (List) source.srandmemberString("sets3", 100);
        Collections.sort(rand2);
        System.out.println("srandmember: " + rand2);
        Assertions.assertIterableEquals(rands, rand2);

        Assertions.assertTrue(source.smoveString("sets4", "sets5", "setvals5"));
        Assertions.assertTrue(source.smoveString("sets4", "sets7", "setvals3"));

        System.out.println("------------------------------------");
        InetSocketAddress addr88 = new InetSocketAddress("127.0.0.1", 7788);
        InetSocketAddress addr99 = new InetSocketAddress("127.0.0.1", 7799);
        source.set("myaddr", InetSocketAddress.class, addr88);

        obj = source.getString("myaddr");
        System.out.println("myaddrstr:  " + obj);
        Assertions.assertEquals("127.0.0.1:7788", obj);

        obj = source.get("myaddr", InetSocketAddress.class);
        System.out.println("myaddr:  " + obj);
        Assertions.assertEquals(addr88, obj);

        source.del("myaddrs");
        source.del("myaddrs2");
        source.sadd("myaddrs", InetSocketAddress.class, addr88);
        source.sadd("myaddrs", InetSocketAddress.class, addr99);

        col = source.smembers("myaddrs", InetSocketAddress.class);
        System.out.println("myaddrs:  " + col);
        List cola2 = new ArrayList(col);
        Collections.sort(cola2, (o1, o2) -> o1.toString().compareTo(o2.toString()));
        Assertions.assertIterableEquals(cola2, List.of(addr88, addr99));

        source.srem("myaddrs", InetSocketAddress.class, addr88);
        col = source.smembers("myaddrs", InetSocketAddress.class);
        System.out.println("myaddrs:  " + col);
        Assertions.assertIterableEquals(col, List.of(addr99));

        source.sadd("myaddrs2", InetSocketAddress.class, addr88);
        source.sadd("myaddrs2", InetSocketAddress.class, addr99);
        mapcol.clear();
        mapcol.put("myaddrs", source.smembers("myaddrs", InetSocketAddress.class));
        mapcol.put("myaddrs2", source.smembers("myaddrs2", InetSocketAddress.class));
        System.out.println("myaddrs&myaddrs2:  " + mapcol);
        Map<String, Collection> news2 = new HashMap<>();
        mapcol.forEach((x, y) -> {
            if (y instanceof Set) {
                List newy = new ArrayList(y);
                Collections.sort(newy, (o1, o2) -> o1.toString().compareTo(o2.toString()));
                news2.put(x, newy);
            } else {
                Collections.sort((List) y, (o1, o2) -> o1.toString().compareTo(o2.toString()));
            }
        });
        mapcol.putAll(news2);
        Assertions.assertEquals(Utility.ofMap("myaddrs", List.of(addr99), "myaddrs2", List.of(addr88, addr99)).toString(), mapcol.toString());

        System.out.println("------------------------------------");
        source.del("myaddrs");
        Type mapType = new TypeToken<Map<String, Integer>>() {
        }.getType();
        Map<String, Integer> paramap = new HashMap<>();
        paramap.put("a", 1);
        paramap.put("b", 2);
        source.set("mapvals", mapType, paramap);

        map = source.get("mapvals", mapType);
        System.out.println("mapvals:  " + map);
        Assertions.assertEquals(Utility.ofMap("a", 1, "b", 2).toString(), map.toString());

        source.del("hmapall");
        source.hmset("hmapall", Utility.ofMap("k1", "111", "k2", "222"));
        Assertions.assertIterableEquals(List.of("111", "222"), source.hvals("hmapall", String.class));
        Assertions.assertIterableEquals(List.of("111", "222"), source.hvalsString("hmapall"));
        Assertions.assertIterableEquals(List.of(111L, 222L), source.hvalsLong("hmapall"));
        Assertions.assertIterableEquals(List.of("111", "222"), source.hvalsAsync("hmapall", String.class).join());
        Assertions.assertIterableEquals(List.of("111", "222"), source.hvalsStringAsync("hmapall").join());
        Assertions.assertIterableEquals(List.of(111L, 222L), source.hvalsLongAsync("hmapall").join());
        Assertions.assertEquals(Utility.ofMap("k1", "111", "k2", "222"), source.hgetall("hmapall", String.class));
        Assertions.assertEquals(Utility.ofMap("k1", "111", "k2", "222"), source.hgetallString("hmapall"));
        Assertions.assertEquals(JsonConvert.root().convertTo(Utility.ofMap("k1", 111L, "k2", 222L)), JsonConvert.root().convertTo(source.hgetallLong("hmapall")));
        Assertions.assertEquals(JsonConvert.root().convertTo(Utility.ofMap("k1", "111", "k2", "222")), JsonConvert.root().convertTo(source.hgetallAsync("hmapall", String.class).join()));
        Assertions.assertEquals(JsonConvert.root().convertTo(Utility.ofMap("k1", "111", "k2", "222")), JsonConvert.root().convertTo(source.hgetallStringAsync("hmapall").join()));
        Assertions.assertEquals(JsonConvert.root().convertTo(Utility.ofMap("k1", 111L, "k2", 222L)), JsonConvert.root().convertTo(source.hgetallLongAsync("hmapall").join()));

        //h
        source.del("hmap");
        source.hincr("hmap", "key1");
        num = source.hgetLong("hmap", "key1", -1);
        System.out.println("hmap.key1 值 : " + num);
        Assertions.assertEquals(1L, num);

        source.hmset("hmap", Utility.ofMap("key2", "haha", "key3", 333));
        source.hmset("hmap", "sm", (HashMap) Utility.ofMap("a", "aa", "b", "bb"));

        map = source.hget("hmap", "sm", JsonConvert.TYPE_MAP_STRING_STRING);
        System.out.println("hmap.sm 值 : " + map);
        Assertions.assertEquals(Utility.ofMap("a", "aa", "b", "bb").toString(), map.toString());

        col = source.hmget("hmap", String.class, "key1", "key2", "key3");
        System.out.println("hmap.[key1,key2,key3] 值 : " + col);
        Assertions.assertIterableEquals(col, List.of("1", "haha", "333"));

        col = source.hkeys("hmap");
        System.out.println("hmap.keys 四值 : " + col);
        Assertions.assertIterableEquals(col, List.of("key1", "key2", "key3", "sm"));

        source.hdel("hmap", "key1", "key3");

        col = source.hkeys("hmap");
        System.out.println("hmap.keys 两值 : " + col);
        Assertions.assertIterableEquals(col, List.of("key2", "sm"));

        obj = source.hgetString("hmap", "key2");
        System.out.println("hmap.key2 值 : " + obj);
        Assertions.assertEquals("haha", obj);

        size = source.hlen("hmap");
        System.out.println("hmap列表(2)大小 : " + size);
        Assertions.assertEquals(2, size);

        source.del("hmaplong");
        source.hincrby("hmaplong", "key1", 10);
        source.hsetLong("hmaplong", "key2", 30);
        AtomicLong cursor = new AtomicLong();
        Map<String, Long> longmap = source.hscan("hmaplong", long.class, cursor, 10);
        System.out.println("hmaplong.所有两值 : " + longmap);
        Assertions.assertEquals(Utility.ofMap("key1", 10, "key2", 30).toString(), longmap.toString());
        Assertions.assertEquals(2L, source.hstrlen("hmaplong", "key1"));

        source.del("hmapstr");
        source.hsetString("hmapstr", "key1", "str10");
        source.hsetString("hmapstr", "key2", null);
        cursor = new AtomicLong();
        map = source.hscan("hmapstr", String.class, cursor, 10);
        System.out.println("hmapstr.所有一值 : " + map);
        Assertions.assertEquals(Utility.ofMap("key1", "str10").toString(), map.toString());

        source.del("hmapstrmap");
        source.hset("hmapstrmap", "key1", JsonConvert.TYPE_MAP_STRING_STRING, (HashMap) Utility.ofMap("ks11", "vv11"));
        source.hset("hmapstrmap", "key2", JsonConvert.TYPE_MAP_STRING_STRING, null);
        cursor = new AtomicLong();
        map = source.hscan("hmapstrmap", JsonConvert.TYPE_MAP_STRING_STRING, cursor, 10, "key2*");
        System.out.println("hmapstrmap.无值 : " + map);
        Assertions.assertEquals(Utility.ofMap().toString(), map.toString());

        source.del("hmap");
        String vpref = "v";
        int ccc = 600;
        for (int i = 101; i <= ccc + 100; i++) {
            source.hmset("hmap", "k" + i, vpref + i);
        }
        cursor = new AtomicLong();
        Map<String, String> smap = source.hscan("hmap", String.class, cursor, 5);
        System.out.println("hmap.hscan 长度 : " + smap.size() + ", cursor: " + cursor + ", 内容: " + smap);
        //smap.size 是不确定的，可能是全量，也可能比5多，也可能比5少
        Assertions.assertFalse(smap.isEmpty());
        if (smap.size() == ccc) {
            Assertions.assertTrue(cursor.get() == 0);
        } else {
            Assertions.assertTrue(cursor.get() > 0);
        }
        cursor = new AtomicLong();
        smap = (Map) source.hscanAsync("hmap", String.class, cursor, 5).join();
        Assertions.assertFalse(smap.isEmpty());
        if (smap.size() == ccc) {
            Assertions.assertTrue(cursor.get() == 0);
        } else {
            Assertions.assertTrue(cursor.get() > 0);
        }

        source.del("sortset");
        source.zadd("sortset", 100, "key100");
        source.zadd("sortset", 200, "key200");
        source.zadd("sortset", 300, "key300");
        source.zadd("sortset", 400, "key400");
        source.zadd("sortset", 500, "key500");
        System.out.println("sortset 写入5条记录 ");

        Assertions.assertEquals(2L, source.zrank("sortset", "key300"));
        Assertions.assertEquals(1L, source.zrevrank("sortset", "key400"));
        Assertions.assertEquals(List.of("key100", "key200", "key300"), source.zrange("sortset", 0, 2));
        cursor = new AtomicLong();
        Assertions.assertEquals(List.of(CacheScoredValue.create(100, "key100"),
            CacheScoredValue.create(200, "key200"),
            CacheScoredValue.create(300, "key300"),
            CacheScoredValue.create(400, "key400"),
            CacheScoredValue.create(500, "key500")
        ), source.zscanInteger("sortset", cursor, -1));

        size = source.zcard("sortset");
        Assertions.assertEquals(5, size);
        size = source.zrem("sortset", "key400", "key800");
        Assertions.assertTrue(size == 1);
        List<Long> scores = source.zmscoreLong("sortset", "key200", "key800", "key500");
        List<Long> scoreAnswer = new ArrayList<>();
        scoreAnswer.add(200L);
        scoreAnswer.add(null);
        scoreAnswer.add(500L);
        Assertions.assertIterableEquals(scoreAnswer, scores);
        source.del("sortnumset");
        source.zincrby("sortnumset", 100, "int100");
        Assertions.assertEquals(100, source.zscoreInteger("sortnumset", "int100"));
        source.zincrby("sortnumset", 120.12f, "float120");
        source.zincrby("sortnumset", 130.13f, "float120");
        Assertions.assertEquals(250.25f, source.zscore("sortnumset", float.class, "float120"));

        source.del("popset");
        source.saddString("popset", "111");
        source.saddString("popset", "222");
        source.saddString("popset", "333");
        source.saddString("popset", "444");
        source.saddString("popset", "555");

        cursor = new AtomicLong();
        Set<String> sset = source.sscan("popset", String.class, cursor, 3);
        System.out.println("popset.sscan 长度 : " + sset.size() + ", cursor: " + cursor + ", 内容: " + sset);
        //smap.size 是不确定的，可能是全量，也可能比5多，也可能比5少
        Assertions.assertFalse(sset.isEmpty());
        if (sset.size() == 5) {
            Assertions.assertTrue(cursor.get() == 0);
        } else {
            Assertions.assertTrue(cursor.get() > 0);
        }
        cursor = new AtomicLong();
        sset = (Set) source.sscanAsync("popset", String.class, cursor, 3).join();
        Assertions.assertFalse(smap.isEmpty());
        if (sset.size() == 5) {
            Assertions.assertTrue(cursor.get() == 0);
        } else {
            Assertions.assertTrue(cursor.get() > 0);
        }

        obj = source.spopString("popset");
        Assertions.assertTrue(obj != null);
        System.out.println("SPOP一个String元素：" + obj);
        Assertions.assertTrue(List.of("111", "222", "333", "444", "555").contains(obj));
        size = source.scard("popset");
        System.out.println("popset元素个数：" + size);
        Assertions.assertEquals(4, size);

        col = source.spopString("popset", 2);
        Assertions.assertEquals(2, col.size());
        System.out.println("SPOP两个String元素：" + col);
        col = source.spopString("popset", 5);
        Assertions.assertEquals(2, col.size());
        System.out.println("SPOP五个String元素(值两个)：" + col);

        source.del("popset");
        source.saddLong("popset", 111L);
        source.saddLong("popset", 222L);
        source.saddLong("popset", 333L);
        source.saddLong("popset", 444L, 555L);
        System.out.println("SPOP一个Long元素：" + source.spopLong("popset"));
        col = source.spopLong("popset", 2);
        Assertions.assertEquals(2, col.size());
        System.out.println("SPOP两个Long元素：" + col);
        col = source.spopLong("popset", 5);
        Assertions.assertEquals(2, col.size());
        System.out.println("SPOP五个Long元素(值两个)：" + col);
        obj = source.spopLong("popset");
        Assertions.assertTrue(obj == null);
        System.out.println("SPOP一个Long元素：" + obj);

        cursor = new AtomicLong();
        List<String> keys = source.scan(cursor, 5);
        System.out.println("scan 长度 : " + keys.size() + ", dbsize: " + source.dbsize() + ", cursor: " + cursor + ", 内容: " + keys);
        Assertions.assertFalse(keys.isEmpty());
        if (keys.size() == source.dbsize()) {
            Assertions.assertTrue(cursor.get() == 0);
        } else {
            Assertions.assertTrue(cursor.get() > 0);
        }
        cursor = new AtomicLong();
        keys = (List) source.scanAsync(cursor, 5).join();
        Assertions.assertFalse(keys.isEmpty());

        long dbsize = source.dbsize();
        System.out.println("keys总数量 : " + dbsize);
        //清除
        long rs = source.del("stritem1");
        System.out.println("删除stritem1个数: " + rs);
        source.del("popset");
        source.del("stritem2");
        source.del("intitem1");
        source.del("intitem2");
        source.del("keylong1");
        source.del("keystr1");
        source.del("mapvals");
        source.del("myaddr");
        source.del("myaddrs2");
        source.del("newnum");
        source.del("objitem1");
        source.del("objitem2");
        source.del("key1");
        source.del("key2");
        source.del("keys3", "keys3-2");
        source.del("sets3", "sets4", "sets5", "sets6");
        source.del("myaddrs");
        source.del("300");
        source.del("stringmap");
        source.del("hmap");
        source.del("hmapall");
        source.del("hmaplong");
        source.del("hmapstr");
        source.del("hmapstrmap");
        source.del("byteskey");
        source.del("nxexkey1");
        System.out.println("--------###------- 接口测试结束 --------###-------");
        source.flushdb();
        System.out.println("------订阅发布功能------");
        final CountDownLatch cdl = new CountDownLatch(2);
        final String channel = "hello_topic";
        final String content = "this is a message content";
        CacheEventListener<byte[]> listener = new CacheEventListener<byte[]>() {
            @Override
            public void onMessage(String topic, byte[] message) {
                String msg = new String(message, StandardCharsets.UTF_8);
                System.out.println("订阅到主题: " + topic + ", 消息: " + msg);
                Assertions.assertEquals(channel, topic);
                Assertions.assertEquals(content, msg);
                cdl.countDown();
            }
        };
        source.subscribe(listener, channel);
        System.out.println("订阅结束");
        source.publish(channel, content);
        System.out.println("发布结束");
        if (!source.getClass().getName().contains("Redisson")) { //Redisson不支持
            List<String> channels = source.pubsubChannels(null);
            Assertions.assertEquals(List.of(channel), channels);
        }
        source.unsubscribe(listener, channel);
        System.out.println("取消订阅结束");
        source.publish(channel, content);
        System.out.println("再次发布结束");
        source.subscribe(listener, channel);
        System.out.println("再次订阅结束");
        source.publish(channel, content);
        System.out.println("再次发布结束");
        cdl.await();
        source.unsubscribe(listener, channel);
        System.out.println("取消订阅结束");
    }

}
