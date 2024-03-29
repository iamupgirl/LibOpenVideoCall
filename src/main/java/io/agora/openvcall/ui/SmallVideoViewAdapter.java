package io.agora.openvcall.ui;

import android.app.Activity;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.SurfaceView;
import android.view.WindowManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

import io.agora.openvcall.model.UserModel;

public class SmallVideoViewAdapter extends VideoViewAdapter {
    private final static Logger log = LoggerFactory.getLogger(SmallVideoViewAdapter.class);

    private int mExceptedUid;

    public SmallVideoViewAdapter(Activity activity, int localUid, int exceptedUid, HashMap<Integer, SurfaceView> uids, VideoViewEventListener listener, List<UserModel> userModelList, int myUid) {
        super(activity, localUid, uids, listener, userModelList, myUid);
        mExceptedUid = exceptedUid;
        log.debug("SmallVideoViewAdapter " + (mLocalUid & 0xFFFFFFFFL) + " " + (mExceptedUid & 0xFFFFFFFFL));
    }

    @Override
    protected void customizedInit(HashMap<Integer, SurfaceView> uids, boolean force, List<UserModel> userModelList, int myUid) {
        VideoViewAdapterUtil.composeDataItem(mUsers, uids, mLocalUid, null, null, mVideoInfo, mExceptedUid, userModelList, myUid);

        if (force || mItemWidth == 0 || mItemHeight == 0) {
            WindowManager windowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics outMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(outMetrics);
            mItemWidth = outMetrics.widthPixels / 4;
            mItemHeight = outMetrics.heightPixels / 4;
        }
    }

    @Override
    public void notifyUiChanged(HashMap<Integer, SurfaceView> uids, int uidExcepted, HashMap<Integer, Integer> status, HashMap<Integer, Integer> volume, List<UserModel> userModelList, int myUid) {
        mUsers.clear();

        mExceptedUid = uidExcepted;

        log.debug("notifyUiChanged " + (mLocalUid & 0xFFFFFFFFL) + " " + (uidExcepted & 0xFFFFFFFFL) + " " + uids + " " + status + " " + volume);
        VideoViewAdapterUtil.composeDataItem(mUsers, uids, mLocalUid, status, volume, mVideoInfo, uidExcepted, userModelList, myUid);

        notifyDataSetChanged();
    }

    public int getExceptedUid() {
        return mExceptedUid;
    }
}
