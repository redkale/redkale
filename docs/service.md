# Service组件
>Service是Redkale最核心的组件，主要处理业务逻辑和操作数据层。Service实例分两种模式: <b>本地模式</b>和<b>远程模式</b>。其模式由```conf/application.xml```文件来配置。开发人员在调用过程中通常不需要区分Service实例是哪种模式。 <br/>
>并不是Sevice都能进行本地和远程模式切换， 以下情况的Service不能转成远程模式:      
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1、Service类修饰为```final```  <br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;2、Service类被标记```@Local```  <br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;3、Service类被标记```@Component```  <br>
         
&nbsp;&nbsp;&nbsp;&nbsp;Redkale进程启动时扫描可加载的Service实现类，根据配置文件配置的模式采用```ASM```技术动态生成相应的Service临时类进行实例化，并注册到ResourceFactory同其他Service、Servlet依赖注入。