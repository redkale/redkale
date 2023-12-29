# 定时任务
&emsp;&emsp;@Scheduled注解在Service的方法上，实现对方法结果进行定时运行。方法必须是无参数或者```ScheduleEvent```参数。

# 属性说明
|属性|默认值|说明|
| --- | --- | --- |
|name|未定义|名称, 可用于第三方实现的定时任务组件的key, 比如xxl-job的任务标识|
|cron|未定义|cron表达式，也可以使用常量值: <br> @yearly、@annually、@monthly、@weekly、<br> @daily、@midnight、@hourly、@minutely <br> @1m、@2m、@3m、@5m、@10m、@15m、@30m <br> @1h、@2h、@3h、@6h <br> ${env.scheduling.cron}: 读取系统配置项|
|zone|未定义|时区，```cron```有值才有效, 例如: "UTC+08"|
|fixedDelay|-1|延迟时间，负数为无效值，支持参数配置、乘法表达式和对象字段值 <br> 参数值支持方式:<br> 100: 设置数值 <br> 5*60: 乘法表达式，值为30 <br> ${env.scheduling.fixedDelay}: 读取系统配置项 <br> #delays: 读取宿主对象的delays字段值作为值, <br> &emsp;&emsp;&emsp;&emsp;&emsp; 字段类型必须是int、long数值类型 <br> 值大于0且fixedRate小于0则使用 ScheduledThreadPoolExecutor.scheduleWithFixedDelay |
|fixedRate|-1|周期时间，负数为无效值，支持参数配置、乘法表达式和对象字段值 <br> 参数值支持方式:<br> 100: 设置数值 <br> 5*60: 乘法表达式，值为30 <br> ${env.scheduling.fixedRate}: 读取系统配置项 <br> #intervals: 读取宿主对象的intervals字段值作为值, <br> &emsp;&emsp;&emsp;&emsp;&emsp;&emsp;&emsp; 字段类型必须是int、long数值类型 <br> 值大于0且fixedRate小于0则使用 ScheduledThreadPoolExecutor.scheduleAtFixedRate |
|initialDelay|-1|起始延迟时间，负数为无效值，支持参数配置、乘法表达式和对象字段值 <br> 参数值支持方式:<br> 100: 设置数值 <br> 5*60: 乘法表达式，值为30 <br> ${env.scheduling.initialDelay}: 读取系统配置项 <br> #inits: 读取宿主对象的inits字段值作为值, <br> &emsp;&emsp;&emsp;&emsp;&emsp;字段类型必须是int、long数值类型 <br> 值大于0且fixedRate和fixedDelay小于0则使用 ScheduledThreadPoolExecutor.schedule |
|timeUnit|```TimeUnit.SECONDS```|时间单位TimeUnit|
|comment|未定义|备注描述|
|mode|```LoadMode.LOCAL```|作用于Service模式，默认值为：LOCAL，<br> LOCAL: 表示远程模式的Service对象中的定时任务不起作用|

# 用法
&emsp;&emsp;每秒执行
```java
    @Scheduled(cron = "0/1 * * * * ?")
    public void task1() {
        System.out.println(Times.nowMillis() + "执行一次");
    }
```

&emsp;&emsp;<b>环境配置</b>, 定时间隔时间由环境变量```env.schedule.fixedRate```配置，没配置采用默认值60秒)
```java
    @Scheduled(fixedRate = "${env.schedule.fixedRate:60}")
    public String task2() {
        System.out.println(Times.nowMillis() + "执行一次");
        return "";
    }
```

&emsp;&emsp;<b>支持乘法表达式</b>, 系统启动后延迟10分钟后每60分钟执行一次，
```java
    @Scheduled(fixedDelay = "10", fixedRate = "2*30", timeUnit = TimeUnit.MINUTES)
    private void task3() {
        System.out.println(Times.nowMillis() + "执行一次");
    }
```

# 定时配置
```xml
    <!--
        全局Serivce的定时任务设置，没配置该节点将自动创建一个。
        enabled： 是否开启缓存功能。默认: true
    -->
    <schedule enabled="true"/>
```