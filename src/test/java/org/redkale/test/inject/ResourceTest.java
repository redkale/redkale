/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template bigint, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.inject;

import java.math.BigInteger;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.redkale.annotation.*;
import org.redkale.inject.ResourceEvent;
import org.redkale.inject.ResourceFactory;

/** @author zhangjx */
public class ResourceTest {

    private boolean main;

    public static void main(String[] args) throws Throwable {
        ResourceTest test = new ResourceTest();
        test.main = true;
        test.run();
    }

    @Test
    public void run() throws Exception {
        ResourceFactory factory = ResourceFactory.create();
        factory.register("id", "2345"); // 注入String类型的property.id
        AService aservice = new AService();
        BService bservice = new BService("eeeee");

        factory.register(aservice); // 放进Resource池内，默认的资源名name为""
        factory.register(bservice); // 放进Resource池内，默认的资源名name为""

        factory.inject(aservice); // 给aservice注入id、bservice，bigint没有资源，所以为null
        factory.inject(bservice); // 给bservice注入id、aservice
        System.out.println(aservice); // 输出结果为：{id:"2345", intid: 2345, bigint:null, bservice:{name:eeeee}}
        System.out.println(bservice); // 输出结果为：{name:"eeeee", id: 2345, aserivce:{id:"2345", intid: 2345, bigint:null,
        // bservice:{name:eeeee}}}

        factory.register("seqid", 200); // 放进Resource池内, 同时ResourceFactory会自动更新aservice的seqid值
        System.out.println(factory.find("seqid", int.class)); // 输出结果为：200
        factory.register(
                "bigint", new BigInteger("666666666666666")); // 放进Resource池内, 同时ResourceFactory会自动更新aservice对象的bigint值
        System.out.println(aservice); // 输出结果为：{id:"2345", intid: 2345, bigint:666666666666666, bservice:{name:eeeee}}
        // 可以看出seqid与bigint值都已自动更新

        factory.register("id", "6789"); // 更新Resource池内的id资源值, 同时ResourceFactory会自动更新aservice、bservice的id值
        System.out.println(aservice); // 输出结果为：{id:"6789", intid: 6789, bigint:666666666666666, bservice:{name:eeeee}}
        System.out.println(
                bservice); // 输出结果为：{name:"eeeee", id: 6789, aserivce:{id:"6789", intid: 6789, bigint:666666666666666,
        // bservice:{name:eeeee}}}

        Properties props = new Properties();
        props.put("id", "5555");
        props.put("desc", "my desc");
        factory.register(props);

        bservice = new BService("ffff");
        factory.register(bservice); // 更新Resource池内name=""的BService资源, 同时ResourceFactory会自动更新aservice的bservice对象
        factory.inject(bservice);
        System.out.println(aservice); // 输出结果为：{id:"6789", intid: 6789, bigint:666666666666666, bservice:{name:ffff}}
    }
}

class BService {

    @Resource(name = "${id}")
    private String id;

    @Resource(name = "${desc}", required = false)
    private String desc;

    @Resource
    private AService aservice;

    private String name = "";

    @ResourceChanged
    private void changeResource(ResourceEvent[] events) {
        for (ResourceEvent event : events) {
            System.out.println(getClass().getSimpleName() + " @Resource = " + event.name() + " 资源变更:  newVal = "
                    + event.newValue() + ", oldVal = " + event.oldValue());
        }
    }

    @ConstructorParameters({"name"})
    public BService(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AService getAservice() {
        return aservice;
    }

    public void setAservice(AService aservice) {
        this.aservice = aservice;
    }

    @Override
    public String toString() {
        return "{name:\"" + name + "\", id: " + id + ", aserivce:" + aservice + "}";
    }
}

class AService {

    @Resource(name = "${id}")
    private String id;

    @Resource(name = "id") // property.开头的资源名允许String自动转换成primitive数值类型
    private int intid;

    @Resource(name = "bigint", required = false)
    private BigInteger bigint;

    @Resource(name = "seqid", required = false)
    private int seqid;

    @Resource
    private BService bservice;

    @Override
    public String toString() {
        return "{id:\"" + id + "\", intid: " + intid + ", bigint:" + bigint + ", bservice:"
                + (bservice == null ? null : ("{name:" + bservice.getName() + "}")) + "}";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getIntid() {
        return intid;
    }

    public void setIntid(int intid) {
        this.intid = intid;
    }

    public int getSeqid() {
        return seqid;
    }

    public void setSeqid(int seqid) {
        this.seqid = seqid;
    }

    public BigInteger getBigint() {
        return bigint;
    }

    public void setBigint(BigInteger bigint) {
        this.bigint = bigint;
    }

    public void setBservice(BService bservice) {
        this.bservice = bservice;
    }
}
