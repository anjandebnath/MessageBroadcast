package com.example.broadcast.broadcast;

import android.os.Message;

import java.lang.ref.WeakReference;
import java.util.concurrent.Callable;

import io.left.rightmesh.android.AndroidMeshManager;
import io.left.rightmesh.id.MeshId;

/**
 Created by Anjan Debnath on 6/28/2018.
 * Copyright (c) 2018, W3 Engineers Ltd. All rights reserved..
 *
 * MessageBroadcastTask is used for sending tasks to the thread pool. When a callable is submitted,
 * a Future object is returned, allowing the thread pool manager to stop the task.
 */
public class MessageBroadcastTask implements Callable {

    private static final int HELLO_PORT = 61087;

    public AndroidMeshManager getMeshManager() {
        return meshManager;
    }

    public void setMeshManager(AndroidMeshManager meshManager) {
        this.meshManager = meshManager;
    }

    private AndroidMeshManager meshManager;


    public MeshId getMeshId() {
        return meshId;
    }

    public void setMeshId(MeshId meshId) {
        this.meshId = meshId;
    }

    private MeshId meshId;

    public String getMeshMessage() {
        return meshMessage;
    }

    public void setMeshMessage(String meshMessage) {
        this.meshMessage = meshMessage;
    }

    private String meshMessage;


    // Keep a weak reference to the CustomThreadPoolManager singleton object, so we can send a
    // message. Use of weak reference is not a must here because CustomThreadPoolManager lives
    // across the whole application lifecycle
    private WeakReference<BroadcastManager> mCustomThreadPoolManagerWeakReference;




    @Override
    public Object call() throws Exception {
        try {
            // check if thread is interrupted before lengthy operation
            if (Thread.interrupted()) throw new InterruptedException();

            // In real world project, you might do some blocking IO operation
            // In this example, I just let the thread sleep for 3 second
            //Thread.sleep(3000);

            byte[] testData = meshMessage.getBytes();
            meshManager.sendDataReliable(meshId, HELLO_PORT, testData);

            // After work is finished, send a message to CustomThreadPoolManager
            Message message = Util.createMessage(Util.MESSAGE_ID, "Thread " +
                    String.valueOf(Thread.currentThread().getId()) + " " +
                    String.valueOf(Thread.currentThread().getName()) + " completed");

            if(mCustomThreadPoolManagerWeakReference != null
                    && mCustomThreadPoolManagerWeakReference.get() != null) {

                mCustomThreadPoolManagerWeakReference.get().sendMessageToUiThread(message);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setCustomThreadPoolManager(BroadcastManager customThreadPoolManager) {
        this.mCustomThreadPoolManagerWeakReference = new WeakReference<BroadcastManager>(customThreadPoolManager);
    }
}
