/*******************************************************************************
 * Copyright (c) 1999, 2014 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 */
package org.eclipse.paho.android.service.token;

import androidx.annotation.Nullable;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttWireMessage;

/** IMqttToken接口的实现和包装类 */

public class MqttTokenAndroid implements IMqttToken {

    private IMqttActionListener mListener;

    private volatile boolean isComplete;

    private final Object mWaitObject = new Object();

    private final MqttAndroidClient mClient;

    private Object mObject;

    private final String[] mTopics;

    private IMqttToken delegate;

    @Nullable
    private MqttException mMqttException = null;

    /**
     * Standard constructor
     *
     * @param client      used to pass MqttAndroidClient object
     * @param object used to pass context
     * @param listener    optional listener that will be notified when the action completes. Use null if not required.
     */
    public MqttTokenAndroid(MqttAndroidClient client, @Nullable Object object, @Nullable IMqttActionListener listener) {
        this(client, object, listener, null);
    }

    /**
     * Constructor for use with subscribe operations
     *
     * @param client      used to pass MqttAndroidClient object
     * @param object used to pass context
     * @param listener    optional listener that will be notified when the action completes. Use null if not required.
     * @param topics      topics to subscribe to, which can include wildcards.
     */
    public MqttTokenAndroid(MqttAndroidClient client, @Nullable Object object, @Nullable IMqttActionListener listener, @Nullable String[] topics) {
        this.mClient = client;
        this.mObject = object;
        this.mListener = listener;
        this.mTopics = topics;
    }

    public void notifyComplete() {
        synchronized (mWaitObject) {
            isComplete = true;
            mWaitObject.notifyAll();
            if (mListener != null) {
                mListener.onSuccess(this);
            }
        }
    }

    public void notifyFailure(Throwable exception) {
        synchronized (mWaitObject) {
            isComplete = true;
            if (exception instanceof MqttException) {
                mMqttException = (MqttException) exception;
            } else {
                mMqttException = new MqttException(exception);
            }
            mWaitObject.notifyAll();
            if (mListener != null) {
                mListener.onFailure(this, exception);
            }
        }

    }

    @Override
    public void waitForCompletion() throws MqttException {
        synchronized (mWaitObject) {
            try {
                mWaitObject.wait();
            } catch (InterruptedException e) {
                // do nothing
            }
        }
        if (mMqttException != null) {
            throw mMqttException;
        }
    }

    @Override
    public void waitForCompletion(long timeout) throws MqttException {
        synchronized (mWaitObject) {
            try {
                mWaitObject.wait(timeout);
            } catch (InterruptedException e) {
                // do nothing
            }
            if (!isComplete) {
                throw new MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT);
            }
            if (mMqttException != null) {
                throw mMqttException;
            }
        }
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public MqttException getException() {
        return mMqttException;
    }

    @Override
    public IMqttAsyncClient getClient() {
        return mClient;
    }

    @Override
    public void setActionCallback(IMqttActionListener listener) {
        this.mListener = listener;
    }

    @Override
    public IMqttActionListener getActionCallback() {
        return mListener;
    }

    @Override
    public String[] getTopics() {
        return mTopics;
    }

    @Override
    public void setUserContext(Object userContext) {
        this.mObject = userContext;
    }

    @Override
    public Object getUserContext() {
        return mObject;
    }

    public void setDelegate(IMqttToken delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getMessageId() {
        return (delegate != null) ? delegate.getMessageId() : 0;
    }

    @Override
    public MqttWireMessage getResponse() {
        return delegate.getResponse();
    }

    @Override
    public boolean getSessionPresent() {
        return delegate.getSessionPresent();
    }

    @Override
    public int[] getGrantedQos() {
        return delegate.getGrantedQos();
    }

}
