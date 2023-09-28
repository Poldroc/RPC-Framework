package com.poldroc.rpc.framework.core.common.config;

import lombok.Data;

/**
 * 客户端配置类
 * @author Poldroc
 * @date 2023/9/14
 */
@Data
public class ClientConfig {

    private String applicationName;

    private String registerAddr;

    private String registerType;

    private String proxyType;

    /**
     * 负载均衡策略 random,rotate
     */
    private String routerStrategy;

    /**
     * 客户端序列化方式 hession2,kryo,jdk,fastjson
     */
    private String clientSerialize;

}
