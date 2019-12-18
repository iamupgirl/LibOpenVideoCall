package io.agora.openvcall.ui;

import android.content.Context;
import android.util.Log;

import io.agora.openvcall.model.CurrentUserSettings;
import io.agora.openvcall.model.WorkerThread;

/**
 * Created by shanxs on 2017/10/16.
 */

public class AgoraManager {

    private static final String TAG = "AgoraManager";

    public static AgoraManager sAgoraManager;

    public static Context mContext;

    private AgoraManager() {
    }

    public static AgoraManager getInstance() {
        if (sAgoraManager == null) {
            synchronized (AgoraManager.class) {
                if (sAgoraManager == null) {
                    sAgoraManager = new AgoraManager();
                }
            }
        }
        return sAgoraManager;
    }

    public void init(Context context) {
        this.mContext = context;
    }

    private WorkerThread mWorkerThread;

    public synchronized void initWorkerThread() {
        if (mWorkerThread == null) {
            mWorkerThread = new WorkerThread(mContext);
            mWorkerThread.start();

            mWorkerThread.waitForReady();
        }
    }

    public synchronized WorkerThread getWorkerThread() {
        return mWorkerThread;
    }

    public synchronized void deInitWorkerThread() {
        mWorkerThread.exit();
        try {
            mWorkerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mWorkerThread = null;
    }

    public static final CurrentUserSettings mVideoSettings = new CurrentUserSettings();

}