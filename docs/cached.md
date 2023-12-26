# 方法缓存
&emsp;&emsp;@Cached注解在Service的方法上，实现对方法结果进行缓存。      
&emsp;&emsp;&emsp;&emsp; 1、返回类型不能是```void```/```CompletableFuture<Void>```<br>
&emsp;&emsp;&emsp;&emsp; 2、返回类型必须是可json序列化的  <br>
&emsp;&emsp;&emsp;&emsp; 3、修饰必须是```protected```/```public```  <br>
&emsp;&emsp;&emsp;&emsp; 4、修饰不能是```final```/```static```  <br>
&emsp;&emsp;本地缓存和远程缓存可同时设置，```expire```设置为0，表示永不过期。

&emsp;&emsp;将结果进行本地缓存30秒且远程缓存60秒
```java
    @Cached(key = "name", localExpire = "30", remoteExpire = "60")
    public String getName() {
        return "haha";
    }
```

&emsp;&emsp;以参数code为key将结果进行本地缓存(时长由环境变量```env.cache.expire```配置，没配置采用默认值30秒)
```java
    @Cached(key = "#{code}", localExpire = "${env.cache.expire:30}")
    public CompletableFuture<String> getNameAsync(String code) {
        return redis.getStringAsync(code);
    }
```

&emsp;&emsp;以参数code+map.id为key将结果进行远程缓存60毫秒
```java
    @Resource
    private CacheManager cacheManager;

    //实时修改远程缓存的key值
    public void updateName(String code, Map<String, Long> map) {
        cacheManager.remoteSetString(code, code + "_" + map.get("id"), Duration.ofMillis(60));
    }

    @Cached(key = "#{code}_#{map.id}", remoteExpire = "60", timeUnit = TimeUnit.MILLISECONDS)
    public String getName(String code, Map<String, Long> map) {
        return code + "-" + map;
    }
```

# 缓存配置
```xml
    <!--
        全局Serivce的缓存设置，没配置该节点将自动创建一个。
        enabled： 是否开启缓存功能。默认: true
        source: 远程CacheSource的资源名
    -->
    <cache enabled="true" source="xxx"/>
```