package org.eclipse.paho.android.service.contract;

/**
 * 操作回调
 * @author zhouL
 * @date 2023/9/27
 */
public interface ServiceStartActionListener {

    void onSuccess();

    void onFailure(String errorMsg, Throwable t);
}
