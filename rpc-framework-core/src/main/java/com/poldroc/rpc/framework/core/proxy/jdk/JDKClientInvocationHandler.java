package com.poldroc.rpc.framework.core.proxy.jdk;

import com.poldroc.rpc.framework.core.client.RpcReferenceWrapper;
import com.poldroc.rpc.framework.core.common.RpcInvocation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static com.poldroc.rpc.framework.core.common.cache.CommonClientCache.RESP_MAP;
import static com.poldroc.rpc.framework.core.common.cache.CommonClientCache.SEND_QUEUE;
import static com.poldroc.rpc.framework.core.common.constants.RpcConstants.DEFAULT_TIMEOUT;
import static com.poldroc.rpc.framework.core.common.constants.RpcConstants.TIME_OUT;

/**
 * 各代理工厂都统一使用
 * 核心任务就是将需要调用的方法名称、服务名称，参数统统都封装好到RpcInvocation当中，然后塞入到一个队列里，并且等待服务端的数据返回
 *
 * @author Poldroc
 * @date 2023/9/15
 */

public class JDKClientInvocationHandler implements InvocationHandler {

    /**
     * 用于锁定当前对象
     */
    private final static Object OBJECT = new Object();

    private RpcReferenceWrapper rpcReferenceWrapper;

    private int timeOut = DEFAULT_TIMEOUT;

    public JDKClientInvocationHandler(RpcReferenceWrapper rpcReferenceWrapper) {
        this.rpcReferenceWrapper = rpcReferenceWrapper;
        timeOut = Integer.valueOf(String.valueOf(rpcReferenceWrapper.getAttatchments().get(TIME_OUT)));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        RpcInvocation rpcInvocation = new RpcInvocation();
        rpcInvocation.setArgs(args);
        rpcInvocation.setTargetServiceName(rpcReferenceWrapper.getAimClass().getName());
        rpcInvocation.setTargetMethod(method.getName());
        // 注入uuid，用于标识请求
        rpcInvocation.setUuid(UUID.randomUUID().toString());
        rpcInvocation.setAttachments(rpcReferenceWrapper.getAttatchments());
        // 将请求信息放入发送队列
        SEND_QUEUE.add(rpcInvocation);
        if (rpcReferenceWrapper.isAsync()) {
            return null;
        }
        RESP_MAP.put(rpcInvocation.getUuid(), OBJECT);
        // 如果不是异步调用，客户端会等待服务端的响应，同时检查是否超时
        long beginTime = System.currentTimeMillis();
        int retryTimes = 0;
        // 判断是否出现了超时异常 或者 是否设置了重置次数
        while (System.currentTimeMillis() - beginTime < timeOut || rpcInvocation.getRetry() > 0) {
            // 从响应结果集中获取响应结果
            Object object = RESP_MAP.get(rpcInvocation.getUuid());
            if (object instanceof RpcInvocation) {
                RpcInvocation rpcInvocationResp = (RpcInvocation) object;
                // 正常结果
                if (rpcInvocationResp.getRetry() == 0 && rpcInvocationResp.getE() == null) {
                    return rpcInvocationResp.getResponse();
                } else if (rpcInvocationResp.getE() != null) {
                    // 每次重试之后都会将retry值扣减1
                    if (rpcInvocationResp.getRetry() == 0) {
                        return rpcInvocationResp.getResponse();
                    }
                    // 如果是因为超时的情况，才会触发重试规则，否则重试机制不生效
                    if (System.currentTimeMillis() - beginTime > timeOut) {
                        retryTimes++;
                        // 重新请求
                        rpcInvocation.setResponse(null);
                        rpcInvocation.setRetry(rpcInvocationResp.getRetry() - 1);
                        RESP_MAP.put(rpcInvocation.getUuid(), OBJECT);
                        SEND_QUEUE.add(rpcInvocation);
                    }
                }
            }
        }
        // 如果超时，抛出异常
        throw new TimeoutException("Wait for response from server on client " + timeOut + "ms,retry times is " + retryTimes + ",service's name is " + rpcInvocation.getTargetServiceName() + "#" + rpcInvocation.getTargetMethod());
    }
}
