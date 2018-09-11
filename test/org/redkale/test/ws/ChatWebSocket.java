/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.test.ws;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Resource;
import org.redkale.net.http.*;
import org.redkale.service.RetResult;
import org.redkale.test.rest.*;

/**
 *
 * @author zhangjx
 */
//anyuser = true 表示WebSocket.createUserid返回的值不表示用户登录态
@RestWebSocket(name = "chat", catalog = "ws", comment = "文字聊天", anyuser = true)
public class ChatWebSocket extends WebSocket<Integer, Object> {

    //@Resource标记的Field只能被修饰为public或protected
    @Resource
    protected ChatService service;

    @Resource
    protected UserService userService;

    protected UserInfo user;

    @Override
    protected CompletableFuture<String> onOpen(final HttpRequest request) {
        LoginBean bean = request.getJsonParameter(LoginBean.class, "bean");
        RetResult<UserInfo> ret = userService.login(bean);
        if (ret.isSuccess()) { //登录成功
            user = ret.getResult();
            //随机创建一个sessionid
            return CompletableFuture.completedFuture(request.getSessionid(true));
        } else { //登录失败, 返回null
            return send("{\"onLoginFailMessage\":" + ret + "}").thenApply(x -> null);
        }
    }

    @Override
    protected CompletableFuture<Integer> createUserid() {
        return CompletableFuture.completedFuture(user.getUserid());
    }

    /**
     * 浏览器WebSocket请求：
     * <pre>
     * websocket.send(JSON.stringify({
     *      sendmessage:{
     *          message:{
     *              content : "这是聊天内容"
     *          },
     *          extmap:{
     *              "a":1,
     *              "b":"haha"
     *          }
     *      }
     * }));
     * </pre>
     *
     * @param message 参数1
     * @param extmap  参数2
     */
    @RestOnMessage(name = "sendmessage")
    public void onChatMessage(ChatMessage message, Map<String, String> extmap) {
        message.fromuserid = getUserid();
        message.fromusername = "用户" + getUserid();
        System.out.println("获取消息: message: " + message + ", map: " + extmap);
        service.chatMessage(message);
    }

    /**
     * 浏览器WebSocket请求：
     * <pre>
     * websocket.send(JSON.stringify({
     *      joinroom:{
     *          roomid: 10212
     *      }
     * }));
     * </pre>
     *
     * @param roomid 参数1
     */
    @RestOnMessage(name = "joinroom")
    public void onJoinRoom(int roomid) {
        service.joinRoom(getUserid(), roomid);
    }

}
