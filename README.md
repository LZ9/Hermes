# Hermes推送订阅
这个是基于 [paho.mqtt](https://github.com/eclipse/paho.mqtt.java) 开源库的封装，支持mqtt，可对后台主题进行订阅和发送。

## 目录
- [1、添加Gradle依赖](https://github.com/LZ9/Hermes#1添加Gradle依赖)
- [2、Hermes涉及的依赖库](https://github.com/LZ9/Hermes#2Hermes涉及的依赖库)
- [3、使用方法](https://github.com/LZ9/Hermes#3使用方法)
- [扩展](https://github.com/LZ9/Hermes#扩展)

## 1、添加Gradle依赖
```
    compile 'cn.lodz:hermes:1.0.1'
```

## 2、Hermes涉及的依赖库
该库引用了下列这些第三方库，你可以确认你的项目，排除重复的引用。
```
    dependencies {
        api 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.0'
        api 'org.eclipse.paho:org.eclipse.paho.android.service:1.1.1'
    }
```

## 3、使用方法
Hermes的使用非常简单，仅需3步：

#### 1）使用HermesAgent创建Hermes进行订阅
```
    Hermes hermes =
        HermesAgent.create()
            .setUrl(url)// 设置tcp地址和端口
            .setClientId(clientId)// 设置客户端id
            .setPrintLog(true)// 是否启用日志（默认是关闭的）
            .setSubTopics(subTopic)// 订阅多个主题
            .setSubTopic(subTopic)// 订阅单个主题
            .setConnectOptions(MqttConnectOptions)// 可以将自己创建的MqttConnectOptions对象配置后传入
            .setOnConnectListener(new OnConnectListener() {// 设置连接回调监听器
                @Override
                public void onConnectComplete(boolean isReconnected) {
                    // 连接完成，isReconnected表示是否是重连
                }

                @Override
                public void onConnectFailure(Throwable cause) {
                    // 连接失败
                }

                @Override
                public void onConnectionLost(Throwable cause) {
                    // 连接断开（丢失）
                }
            })
            .setOnSendListener(new OnSendListener() {// 设置发送监听器
                @Override
                public void onSendComplete(String topic, String content) {
                    // 发送成功
                }

                @Override
                public void onSendFailure(String topic, Throwable cause) {
                    // 发送失败
                }
            })
            .setOnSubscribeListener(new OnPushListener() {// 设置订阅监听器

                @Override
                public void onSubscribeSuccess(String topic) {
                    // 订阅主题成功
                }

                @Override
                public void onSubscribeFailure(String topic, Throwable cause) {
                    // 订阅主题失败
                }

                @Override
                public void onMsgArrived(String subTopic, String msg) {
                    // 后台消息到达
                }
            })
            .build(getContext().getApplicationContext())// 构建Hermes对象
            .buildConnect(getContext().getApplicationContext());// 构建Hermes对象并自动连接后台
```

- 小伙伴可以根据自己的需要选择方法进行设置
- setUrl()和setClientId()一定要配置，否则会抛出空指针异常
- setSubTopics()和setSubTopic()正常二选一进行调用，如果不设置将不会订阅后台的主题，订阅状态会通过setOnSubscribeListener()内的监听器回调
- setConnectOptions()正常情况下不需要设置，内部默认的MqttConnectOptions包含了自动断线重连，如果需要深度定制再调用该方法
- setOnConnectListener()、setOnSendListener()和setOnSubscribeListener()这3个监听器方法大家根据自己的业务需要选择监听即可
- build()和buildConnect()和也是二选一调用，差别在于后者会在创建后自动帮你连接，建议传入的Context使用ApplicationContext

#### 2）使用Hermes向后台发送信息
```
    hermes.sendTopic(topic, content);
```

- 传入发送的主题和内容
- 发送的结果和状态会通过setOnSendListener()方法内的监听器回调

#### 3）控制Hermes状态
```
    hermes.connect();// 连接后台
    hermes.disconnect();// 断开连接
    hermes.isConnected();// 是否已连接
```

- 如果你希望在build()后手动进行连接，可以调用connect()方法
- 调用disconnect()方法可以手动断开连接
- isConnected()可以告诉你当前的连接状态

## 扩展

- [更新记录](https://github.com/LZ9/Hermes/blob/master/hermes/readme_hermes_update.md)
- [回到顶部](https://github.com/LZ9/Hermes#hermes推送订阅)

## License
- [Apache Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

Copyright 2018 Lodz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

<http://www.apache.org/licenses/LICENSE-2.0>

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.