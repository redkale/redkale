# 方法缓存
&emsp;&emsp;@Cached注解在Service的方法上，实现对方法结果进行缓存。      
&emsp;&emsp;&emsp;&emsp; 1、返回类型不能是```void```/```CompletableFuture<Void>```<br>
&emsp;&emsp;&emsp;&emsp; 2、返回类型必须是可json序列化的  <br>
&emsp;&emsp;&emsp;&emsp; 3、修饰必须是```protected```/```public```  <br>
&emsp;&emsp;&emsp;&emsp; 4、修饰不能是```final```/```static```  <br>
&emsp;&emsp;本地缓存和远程缓存可同时设置，```expire```设置为0，表示永不过期, 支持异步方法(返回类型为```CompletableFuture```)。

## 属性说明
|属性|默认值|说明|
| --- | --- | --- |
|name|未定义|缓存的名称|
|key|未定义|缓存的key，支持参数动态组合，比如"key_#{id}"|
|manager|空|缓存管理器名称, 不能含有':'、'#'、'@'字符|
|localExpire|-1|本地缓存过期时长， 0表示永不过期， -1表示不作本地缓存。 <br> 参数值支持方式:<br> &emsp;100: 设置数值 <br> &emsp;${env.cache.expires}: 读取系统配置项  |
|remoteExpire|-1|远程缓存过期时长， 0表示永不过期， -1表示不作远程缓存。 <br> 参数值支持方式:<br> &emsp;100: 设置数值 <br> &emsp;${env.cache.expires}: 读取系统配置项  |
|nullable|false|是否可以缓存null值|
|timeUnit|```TimeUnit.SECONDS```|时间单位TimeUnit|
|comment|未定义|备注描述|
|mode|```LoadMode.ANY```|作用于Service模式，默认值为：ANY，作用于所有模式Service，<br> LOCAL: 表示远程模式的Service对象中的缓存功能不起作用|

## 基本用法
&emsp;&emsp;将结果进行本地缓存30秒且远程缓存60秒
```java
    @Cached(name = "name", key = "name", localExpire = "30", remoteExpire = "60")
    public String getName() {
        return "haha";
    }
```

&emsp;&emsp;以参数code为key将结果进行本地缓存(时长由环境变量```env.cache.expire```配置，没配置采用默认值30秒)
```java
    @Cached(name = "name", key = "#{code}", localExpire = "${env.cache.expire:30}")
    public CompletableFuture<String> getNameAsync(String code) {
        return redis.getStringAsync(code);
    }
```

&emsp;&emsp;以参数code+map.id为key将结果进行远程缓存60毫秒
```java
    @Resource
    private CachedManager cachedManager;

    //实时修改远程缓存的key值
    public void updateName(String code, Map<String, Long> map) {
        cachedManager.remoteSetString("name", code + "_" + map.get("id"), code + "-" + map, Duration.ofMillis(60));
    }

    @Cached(name = "name", key = "#{code}_#{map.id}", remoteExpire = "60", timeUnit = TimeUnit.MILLISECONDS)
    public String getName(String code, Map<String, Long> map) {
        return code + "-" + map;
    }
```

## 动态调整缓存时长
&emsp;&emsp;使用```@Resource```注入多个```CachedManager```
```java

    @Resource
    private CachedManager cachedManager;

    @Cached(name = "name", key = "#{code}_#{map.id}", remoteExpire = "60", timeUnit = TimeUnit.MILLISECONDS)
    public String getName(String code, Map<String, Long> map) {
        return code + "-" + map;
    }

    public void updateExpire() {
        Duration expire = Duration.ofMillis(600);
        cachedManager.acceptCachedAction("name", action -> {
            //将缓存时长改成600毫秒，并开启本地缓存
            action.setLocalExpire(expire);
            action.setRemoteExpire(expire);
        });
    }
```

## 缓存配置
```xml
    <!--
        全局Serivce的缓存设置，没配置该节点将自动创建一个。
        name:  缓存管理器的名称， 默认: ""
        enabled： 是否开启缓存功能。默认: true
        remote: 远程CacheSource的资源名
        broadcastable: 存在远程CacheSource时修改数据是否进行广播到其他集群服务中。默认: true
    -->
    <cached name="" enabled="true" remote="xxx" broadcastable="true"/>
```

## 多缓存器
```xml
    <cached name="" enabled="true" remote="redis_1" broadcastable="true"/>
    <cached name="backup" enabled="true" remote="redis_2" broadcastable="true"/>
```

&emsp;&emsp;使用```@Resource```注入多个```CachedManager```
```java

    //第一个缓存器
    @Resource
    private CachedManager cachedManager;

    //第二个缓存器
    @Resource(name = "backup")
    private CachedManager cachedManager2;

    //第一个缓存器实时修改远程缓存的key值
    public void updateName(String code, Map<String, Long> map) {
        cachedManager.remoteSetString("name", code + "_" + map.get("id"), code + "-" + map, Duration.ofMillis(60));
    }

    //使用第一个缓存器
    @Cached(name = "name", key = "#{code}_#{map.id}", remoteExpire = "60", timeUnit = TimeUnit.MILLISECONDS)
    public String getName(String code, Map<String, Long> map) {
        return code + "-" + map;
    }

    //使用第二个缓存器
    @Cached(manager = "backup", name = "name", key = "#{code}_#{map.id}_2", remoteExpire = "60", timeUnit = TimeUnit.MILLISECONDS)
    public String getName2(String code, Map<String, Long> map) {
        return code + "-" + map;
    }
```
