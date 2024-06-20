# 缓存组件 CacheSource
&emsp;&emsp;```CachedCacheSource```是框架主要的缓存组件，主要提供redis和内存两大实现，接口大部分与redis命令保持一致。

## 使用CacheSouce存放登录会话
```java
public class UserService implements Service {

    //用户简单信息缓存
    private final Map<Integer, UserInfo> users = new ConcurrentHashMap<>();

    //使用CacheSource必须要指明泛型
    @Resource(name = "usersessions")
    protected CacheSource sessions;

    //登录
    public RetResult<UserInfo> login(LoginBean bean) { //bean.sessionid 在接入层进行赋值
        UserInfo user = null;
        // 登陆逻辑 user = ...
        users.put(user.getUserid(), user);
        sessions.setLong(600, bean.getSessionid(), user.getUserid()); //session过期时间设置为10分钟
        return new RetResult<>(user);
    }

    //获取当前用户信息
    public UserInfo current(String sessionid) { //给HTTP的BaseServlet用
        Long userid = sessions.getexLong(sessionid, 600);
        return userid == null ? null : users.get(userid.intValue());
    }

    //注销
    public void logout(String sessionid) {
        sessions.del(sessionid);
    }
}
```

## source.properties 配置说明
```

# usersession为@Resource.name值
# type可以不用设置，框架会根据url判断使用哪个CacheSource实现类
redkale.cachesource.usersession.type = org.redkalex.cache.redis.RedisCacheSource
# 最大连接数
redkale.cachesource.usersession.maxconns = 16
# 节点地址
redkale.cachesource.usersession.nodes = redis://127.0.0.1:6363
# 节点密码
redkale.cachesource.usersession.password = 12345678
# 节点db
redkale.cachesource.usersession.db = 0

#简化写法: 可以不用.node[0], 将参数都合并到url中
redkale.cachesource.usersession.url = redis://user:123456@127.0.0.1:6363?db=0

@Resource.name=""的CacheSource
redkale.cachesource.nodes = redis://127.0.0.1:6363
redkale.cachesource.password = 12345678
```