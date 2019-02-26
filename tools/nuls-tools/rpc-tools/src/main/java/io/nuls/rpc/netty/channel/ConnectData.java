package io.nuls.rpc.netty.channel;

import io.netty.channel.socket.SocketChannel;
import io.nuls.rpc.model.message.Ack;
import io.nuls.rpc.model.message.Message;
import io.nuls.rpc.model.message.Request;
import io.nuls.rpc.model.message.Response;
import io.nuls.rpc.netty.channel.manager.ConnectManager;
import io.nuls.rpc.server.runtime.ServerRuntime;
import io.nuls.tools.parse.JSONUtils;
import io.nuls.tools.thread.ThreadUtils;
import io.nuls.tools.thread.commom.NulsThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;

/**
 * 链接基础类
 * Link base class
 * @author tag
 * 2019/2/21
 * */
public class ConnectData {

    private final SocketChannel channel;

    /**
     * 链接关闭断开标识
     * Link Close Disconnection Identification
     * */
    private boolean connected = true;

    /**
     * 从服务端得到的握手确认
     * Handshake confirmation(NegotiateConnectionResponse) from the server
     */
    private final Queue<Message> negotiateResponseQueue = new ConcurrentLinkedQueue<>();

    /**
     * 从服务端得到的请求确认
     * Request confirmation(Ack) from the server
     */
    private final Queue<Ack> ackQueue = new ConcurrentLinkedQueue<>();

    /**
     * 从服务端得到的需要手动处理的应答消息
     * Response that need to be handled manually from the server
     */
    private final Queue<Response> responseManualQueue = new ConcurrentLinkedQueue<>();

    /**
     * 从服务端得到的自动处理的应答消息
     * Response that need to be handled Automatically from the server
     */
    private final Queue<Response> responseAutoQueue = new ConcurrentLinkedQueue<>();

    /**
     * 请求超时的请求
     * Request for timeout
     * */
    private final List<String> timeOutMessageList = new ArrayList<>();

    /**
     * 接口最近调用时间（订阅间隔指定时间返回会用到）
     * Recent call time of interface
     * Key: Message
     * Value: Time(long)
     */
    private final Map<Message, Long> cmdInvokeTime = new ConcurrentHashMap<>();

    /**
     * 单次响应队列，Message
     * Single called queue.Message.
     */
    private final ConcurrentLinkedQueue<Object []> requestSingleQueue = new ConcurrentLinkedQueue<>();

    /**
     * 多次响应队列（根据时间间隔订阅/Period），Message
     * Multiply called queue (Period).Message.
     */
    private final LinkedBlockingQueue<Object []> requestPeriodLoopQueue = new LinkedBlockingQueue<>();

    /**
     * 多次响应（根据时间触发次数订阅/Event count），Message
     * Multiply called (Event count). Message.
     */
    private final List<Message> requestEventCountLoopList = new CopyOnWriteArrayList<>();

    /**
     * 当前链接订阅接口待返回结果列表
     * Current Link Subscription Interface to Return Result List
     * */
    private final LinkedBlockingQueue<Response> requestEventResponseQueue = new LinkedBlockingQueue<>();

    /**
     * 按时间订阅消息的消息ID与详细键值对
     *
     * Key:MessageID
     * Value:Message
     * */
    private final Map<String, Message> idToPeriodMessageMap = new ConcurrentHashMap<>();

    /**
     * 按时间触发次数订阅消息的消息ID与详细键值对
     *
     * Key:MessageID
     * Value:Message
     * */
    private final Map<String, Message> idToEventMessageMap = new ConcurrentHashMap<>();

    /**
     * 订阅接口时接口被改变的初始次数
     * Initial number of interface changes when subscribing to an interface
     *
     * Key:cmd_messageId
     * Value:订阅时接口已改变次数/Number of interface changes at subscription time
     * */
    private final Map<String, Integer> subscribeInitCount = new ConcurrentHashMap<>();

    /**
     * 判断指定消息是否为订阅消息，且是按指定间隔时间返回数据
     * Determines whether the specified message is a subscription message and returns data at a specified interval
     *
     * @param messageId
     * */
    private boolean periodMessageIsExist(String messageId){
        return idToPeriodMessageMap.containsKey(messageId);
    }

    /**
     * 判断指定消息是否为订阅消息，且是事件触发次数返回数据
     * Determines whether the specified message is a subscription message and returns data on the number of event triggers
     *
     * @param messageId
     * */
    private boolean eventMessageIsExist(String messageId){
        return idToEventMessageMap.containsKey(messageId);
    }

    /**
     * 该链接处理消息的需要的线程
     * The thread that the link needs to process the message
     * */
    private final ExecutorService threadPool = ThreadUtils.createThreadPool(8, 50, new NulsThreadFactory("ServerProcessor"));

    /**
     * 订阅事件（接口改变次数）
     * Subscription events (number of interface changes)
     * */
    public void subscribeByEvent(Message message){
        ConnectManager.subscribeByEvent(this,message);
        idToEventMessageMap.put(message.getMessageId(),message);
        addSubscribeInitCount(message);
        requestEventCountLoopList.add(message);
    }

    /**
     * 取消订阅 / unsubscribe
     *
     * @param messageId
     * */
    public void unsubscribe(String messageId){
        Message message;
        if(periodMessageIsExist(messageId)){
            message = idToPeriodMessageMap.remove(messageId);
            cmdInvokeTime.remove(message);
            requestPeriodLoopQueue.remove(message);
        }else if(eventMessageIsExist(messageId)){
            message = idToEventMessageMap.remove(messageId);
            requestEventCountLoopList.remove(message);
            removeSubscribeInitCount(message);
            ConnectManager.unsubscribeByEvent(message);
        }
    }

    /**
     * 添加订阅接口初始次数
     * Initial number of subscription interfaces added
     * */
    public void addSubscribeInitCount(Message message){
        Request request = JSONUtils.map2pojo((Map) message.getMessageData(), Request.class);
        for (Object method : request.getRequestMethods().keySet()) {
            String cmd = (String)method;
            String key = ServerRuntime.getSubscribeKey(message.getMessageId(),cmd);
            if(!subscribeInitCount.containsKey(key)){
                subscribeInitCount.put(key, ServerRuntime.getCmdChangeCount(cmd));
            }
        }
    }

    /**
     * 刪除订阅接口初始次数
     * Initial number of subscription interfaces added
     * */
    public void removeSubscribeInitCount(Message message){
        Request request = JSONUtils.map2pojo((Map) message.getMessageData(), Request.class);
        for (Object method : request.getRequestMethods().keySet()) {
            String cmd = (String)method;
            String key = ServerRuntime.getSubscribeKey(message.getMessageId(),cmd);
            if(subscribeInitCount.containsKey(key)){
                subscribeInitCount.remove(key);
            }
        }
    }

    public ConnectData(SocketChannel channel){
        this.channel = channel;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public Map<Message, Long> getCmdInvokeTime() {
        return cmdInvokeTime;
    }

    public ConcurrentLinkedQueue<Object[]> getRequestSingleQueue() {
        return requestSingleQueue;
    }
    public LinkedBlockingQueue<Object []> getRequestPeriodLoopQueue() {
        return requestPeriodLoopQueue;
    }

    public List<Message> getRequestEventCountLoopList() {
        return requestEventCountLoopList;
    }

    public LinkedBlockingQueue<Response> getRequestEventResponseQueue() {
        return requestEventResponseQueue;
    }

    public Map<String, Message> getIdToPeriodMessageMap() {
        return idToPeriodMessageMap;
    }

    public Map<String, Message> getIdToEventMessageMap() {
        return idToEventMessageMap;
    }

    public Map<String, Integer> getSubscribeInitCount() {
        return subscribeInitCount;
    }

    public SocketChannel getChannel() {
        return channel;
    }


    public Queue<Message> getNegotiateResponseQueue() {
        return negotiateResponseQueue;
    }

    public Queue<Ack> getAckQueue() {
        return ackQueue;
    }

    public Queue<Response> getResponseManualQueue() {
        return responseManualQueue;
    }

    public Queue<Response> getResponseAutoQueue() {
        return responseAutoQueue;
    }

    public List<String> getTimeOutMessageList() {
        return timeOutMessageList;
    }

    public ExecutorService getThreadPool() {
        return threadPool;
    }

    /**
     * @return 第一条握手确认消息，The first handshake confirmed message
     */
    public Message firstMessageInNegotiateResponseQueue() {
        return negotiateResponseQueue.poll();
    }

    /**
     * @return 第一条确认消息，The first ack message
     */
    public Ack firstMessageInAckQueue() {
        return ackQueue.poll();
    }

    /**
     * @return 第一条需要手动处理的Response消息，The first Response message that needs to be handled manually
     */
    public Response firstMessageInResponseManualQueue() {
        return responseManualQueue.poll();
    }

    /**
     * @return 第一条需要自动处理的Response消息，The first Response message that needs to be handled automatically
     */
    public Response firstMessageInResponseAutoQueue() {
        return responseAutoQueue.poll();
    }
}