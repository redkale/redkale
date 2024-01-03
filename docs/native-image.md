# 使用native-image
&emsp;&emsp; Redkale支持GraalVM的native-image， 由于Redkale使用了大量的asm动态生成代码，而native-image不支持动态字节码，因此需要使用```redkale-maven-plugin```执行预编译，提前生成动态字节码进行打包。

## 安装GraalVM
```
  下载地址: https://www.graalvm.org/downloads/
```

## 安装native-image
```
  install native-image
```

## 配置pom
```xml
    <plugin>
        <groupId>org.redkale.maven.plugins</groupId>
        <artifactId>redkale-maven-plugin</artifactId>
        <version>1.2.0</version>                                                
        <configuration>		
            <nativeimageArgs>
                <arg>--no-fallback</arg>
            </nativeimageArgs>
        </configuration>                                    
        <executions>
            <execution>
                <id>redkale-compile</id> 
                <phase>process-classes</phase>
                <goals>
                    <goal>compile</goal>
                </goals>                   
            </execution>
        </executions>    
    </plugin>
```

## native-image编译
```
 native-image -H:+ReportExceptionStackTraces --report-unsupported-elements-at-runtime -jar xxx.jar
```