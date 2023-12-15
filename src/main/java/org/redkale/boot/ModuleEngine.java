/*
 *
 */
package org.redkale.boot;

import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import org.redkale.service.Service;
import org.redkale.util.Environment;
import org.redkale.util.ResourceEvent;
import org.redkale.util.ResourceFactory;

/**
 *
 * 各组件的引擎类, 由Application管理
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 *
 * @since 2.8.0
 */
public abstract class ModuleEngine {

    protected final Logger logger = Logger.getLogger(getClass().getSimpleName());

    protected final Application application;

    protected final ResourceFactory resourceFactory;

    protected final Environment environment;

    public ModuleEngine(Application application) {
        this.application = application;
        this.resourceFactory = application.getResourceFactory();
        this.environment = application.getEnvironment();
    }

    /**
     * 进入Application.init方法时被调用
     * 此时状态:
     * 1、远程配置项未获取
     * 2、WorkExecutor未初始化
     */
    public void onAppPreInit() {
        //do nothing
    }

    /**
     * 结束Application.init方法前被调用
     */
    public void onAppPostInit() {
        //do nothing
    }

    /**
     * 进入Application.start方法被调用
     */
    public void onAppPreStart() {
        //do nothing
    }

    /**
     * 结束Application.start方法前被调用
     */
    public void onAppPostStart() {
        //do nothing
    }

    /**
     * 配置项加载后被调用
     *
     * @param props 配置项全量
     */
    public void onEnvironmentLoaded(Properties props) {
        //do nothing
    }

    /**
     * 配置项变更时被调用
     *
     * @param namespace 命名空间
     * @param events    变更项
     */
    public void onEnvironmentChanged(String namespace, List<ResourceEvent> events) {
        //do nothing
    }

    /**
     * 服务全部启动前被调用
     */
    public void onServersPreStart() {
        //do nothing
    }

    /**
     * 服务全部启动后被调用
     */
    public void onServersPostStart() {
        //do nothing
    }

    /**
     * 执行Service.init方法前被调用
     *
     * @param service Service
     */
    public void onServicePreInit(Service service) {
        //do nothing
    }

    /**
     * 执行Service.init方法后被调用
     *
     * @param service Service
     */
    public void onServicePostInit(Service service) {
        //do nothing
    }

    /**
     * 执行Service.destroy方法前被调用
     *
     * @param service Service
     */
    public void onServicePreDestroy(Service service) {
        //do nothing
    }

    /**
     * 执行Service.destroy方法后被调用
     *
     * @param service Service
     */
    public void onServicePostDestroy(Service service) {
        //do nothing
    }

    /**
     * 服务全部停掉前被调用
     */
    public void onServersPreStop() {
        //do nothing
    }

    /**
     * 服务全部停掉后被调用
     */
    public void onServersPostStop() {
        //do nothing
    }

    /**
     * 进入Application.shutdown方法被调用
     */
    public void onAppPreShutdown() {
        //do nothing
    }

    /**
     * 结束Application.shutdown方法前被调用
     */
    public void onAppPostShutdown() {
        //do nothing
    }
}
