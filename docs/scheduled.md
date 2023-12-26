# 定时任务
&emsp;&emsp;@Scheduled注解在Service的方法上，实现对方法结果进行定时运行。方法必须是无参数或者```ScheduleEvent```参数。

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