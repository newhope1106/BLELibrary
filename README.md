# BLELibrary
蓝牙BLE扫描、连接及数据传输，做了分包处理，支持大量数据
<br>
## Feature
- 扫描蓝牙超时功能
- 大量数据(>20字节)传输功能

## 用法
###1.在Applicaiton的onCreate中
```java
/**
 * 初始化上下文、服务UUID、特征值UUID
 * @param context 上下文，因为可能涉及到跨Activity使用，使用全局的Application，可以在Application中初始化
 * @param serviceUUID 服务UUID
 * @param characteristicUUID 特征值UUID
 * */
UbtBLEManager.getInstance().install(Application context, String serviceUUID, String characteristicUUID);
```

###2.扫描蓝牙接口
```java
/**
 * 开始扫描设备
 * @param timeout 超时时间
 * @param callback 扫描结果回调
 * */
UbtBLEManager.getInstance().scanDevices(int timeout, DeviceScanCallback callback)
或者
/**
 * 开始扫描设备，缺省的时间是{@link #TIME_OUT_SCAN}
 * @param callback 扫描结果回调
 * */
UbtBLEManager.getInstance().scanDevices(DeviceScanCallback callback)
```

###3.设置连接回调
```java
UbtBLEManager.getInstance().setConnectCallback(ConnectCallback connectCallback)
```

###4.连接设备
```java
/**
 * 连接设备
 * */
UbtBLEManager.getInstance().connect(final BluetoothDevice device)
```

###5.发送数据
```java
/**
 * 发送数据
 * @param data 数据
 * */
UbtBLEManager.getInstance().sendData(final String data)
```

###6.断开连接
```java
/**
 * 关闭所有连接
 * */
UbtBLEManager.getInstance().closeConnection()
```

# About
@Author : Liu Liaopu </br>
@Website : https://github.com/newhope1106

# License
Copyright 2016-2017 Liu Liaopu

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.   
