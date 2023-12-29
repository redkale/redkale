# Service组件
&emsp;&emsp;Service是Redkale最核心的组件，主要处理业务逻辑和操作数据层。Service实例分两种模式: <b>本地模式</b>和<b>远程模式</b>。其模式由```conf/application.xml```文件来配置。开发人员在调用过程中通常不需要区分Service实例是哪种模式。 <br/>
&emsp;&emsp;并不是Sevice都能进行本地和远程模式切换， 以下情况的Service不能转成远程模式:      
&emsp;&emsp;&emsp;&emsp; 1、Service类修饰为```final```  <br>
&emsp;&emsp;&emsp;&emsp; 2、Service类被标记```@Local```  <br>
&emsp;&emsp;&emsp;&emsp; 3、Service类被标记```@Component```  <br>
         
&emsp;&emsp;Redkale进程启动时扫描可加载的Service实现类，根据配置文件配置的模式采用```ASM```技术动态生成相应的Service临时类进行实例化，并注册到ResourceFactory同其他Service、Servlet依赖注入。

## Service使用类型
|类型|使用注解|场景说明|
| --- | --- | --- |
|默认加载|```@AutoLoad```或无注解|默认的Service会自动加载并初始化，且会自动生成对应协议层Servlet|
|依赖加载|```@AutoLoad(false)```|此类Service只有被其他服务依赖或者显式的配置时才会被初始化，<br> 主要用于工具类功能服务， <br> 比如```DataSource```、```CacheSource```|
|本地模式|```@Local```|此类Service无论配不配成远程模式，都不会转成远程模式，<br>主要用于功能依赖本地环境或者参数无法序列化的服务|
|组件模式|```@Component```|此类Service不会被动态生成协议层Servlet，<br>主要用于无需提供对进程外提供接口的服务，<br> 比如```DataSource```、```CacheSource```的实现|
# 基本用法
```java
@RestService(comment = "用户服务模块")
public class UserService implements Service {
    
    @Resource(name = "platf")
    private DataSource source;

    @RestMapping(auth = true, comment = "更改密码")
    public RetResult<String> updatePwd(@RestUserid long userid, UserPwdBean bean) {
        //逻辑处理
        return RetResult.success();
    }

    @RestMapping(auth = true, comment = "更新用户介绍")
    public RetResult<String> updateIntro(@RestUserid long userid, String intro) {
        intro = Utility.orElse(intro, "");  //为null则用""
        //更新数据库
        source.updateColumn(UserDetail.class, userid, UserDetail::getIntro, intro); 
        return RetResult.success();
    }

    @RestMapping(auth = true, comment = "修改用户性别(异步方法)")
    public CompletableFuture<RetResult<String>> updateGender(@RestUserid long userid, short gender) {
        if (gender != GENDER_MALE && gender != GENDER_FEMALE) {
            return RetCodes.retResultFuture(RET_USER_GENDER_ILLEGAL);
        }        
        //更新数据库
        return source.updateColumnAsync(UserDetail.class, userid, UserDetail::getGender, gender)
                     .thenApply(v -> RetResult.success());
    }
}
```
&emsp;&emsp;```@RestUserid int userid```为当前用户Id, 值是在BaseServlet里进行设置，userid可以是String、long、int类型。

## 远程模式Service
&emsp;&emsp;远程Servie其实是提供RPC接口，需要配置文件中显式配置才可使用远程模式。
```xml
    <group name="remote-A" nodes="192.168.10.111:7070,192.168.10.112:7070"/>

    <server protocol="HTTP" host="0.0.0.0" port="8080">  
        <services autoload="true" group="remote-A"/>  
        <service name="" value="org.redkale.demo.user.UserService" group="remote-A"/>
    </server>
```