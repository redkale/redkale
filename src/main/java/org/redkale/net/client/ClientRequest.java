/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.client;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.redkale.convert.ConvertColumn;
import org.redkale.net.WorkThread;
import org.redkale.util.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 * @since 2.3.0
 */
public abstract class ClientRequest {

    public static final byte[] EMPTY_TRACEID = new byte[0];

    @ConvertColumn(index = 1)
    protected String traceid;

    @ConvertColumn(index = 2)
    protected long createTime = System.currentTimeMillis();

    protected WorkThread workThread;

    // 只会在ClientCodec的读线程里调用, 将ClientResult转成最终结果对象
    Function respTransfer;

    public abstract void writeTo(ClientConnection conn, ByteArray array);

    <T extends ClientRequest> T computeWorkThreadIfAbsent() {
        if (workThread == null) {
            workThread = WorkThread.currentWorkThread();
        }
        return (T) this;
    }

    public Serializable getRequestid() {
        return null;
    }

    // 关闭请求一定要返回false
    public boolean isCloseType() {
        return false;
    }

    // 连接上先从服务器拉取数据构建的虚拟请求一定要返回true
    public boolean isVirtualType() {
        return false;
    }

    public long getCreateTime() {
        return createTime;
    }

    public String getTraceid() {
        return traceid;
    }

    public byte[] traceBytes() {
        return Utility.isEmpty(traceid) ? EMPTY_TRACEID : traceid.getBytes(StandardCharsets.UTF_8);
    }

    public <T extends ClientRequest> T workThread(WorkThread thread) {
        this.workThread = thread;
        return (T) this;
    }

    public <T extends ClientRequest> T traceid(String traceid) {
        this.traceid = traceid;
        return (T) this;
    }

    // 数据是否全部写入，如果只写部分，返回false, 配合ClientConnection.pauseWriting使用
    protected boolean isCompleted() {
        return true;
    }

    protected void prepare() {
        this.createTime = System.currentTimeMillis();
        this.traceid = Traces.currentTraceid();
        this.respTransfer = null;
    }

    protected boolean recycle() {
        this.createTime = 0;
        this.traceid = null;
        this.respTransfer = null;
        return true;
    }
}
