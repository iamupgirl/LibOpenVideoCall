package io.agora.openvcall.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import io.agora.openvcall.R;
import io.agora.openvcall.model.UserModel;
import io.agora.propeller.UserStatusData;
import io.agora.propeller.VideoInfoData;

public class VideoViewAdapterUtil {

    private final static Logger log = LoggerFactory.getLogger(VideoViewAdapterUtil.class);

    private static final boolean DEBUG = false;

    public static void composeDataItem1(final ArrayList<UserStatusData> users, HashMap<Integer, SurfaceView> uids, int localUid, List<UserModel> userModelList, int myUid) {

        for (HashMap.Entry<Integer, SurfaceView> entry : uids.entrySet()) {
            if (DEBUG) {
                log.debug("composeDataItem1 " + (entry.getKey() & 0xFFFFFFFFL) + " " + (localUid & 0xFFFFFFFFL) + " " + users.size() + " " + entry.getValue());
            }
            SurfaceView surfaceV = entry.getValue();
            surfaceV.setZOrderOnTop(false);
            surfaceV.setZOrderMediaOverlay(false);

            log.info("composeDataItem1:key=" + entry.getKey() + ",myUid=" + myUid + ",localUid=" + localUid);
            if (localUid != myUid) {
                searchUidsAndAppend(users, entry, localUid, UserStatusData.DEFAULT_STATUS, UserStatusData.DEFAULT_VOLUME, null, userModelList, myUid);
            }
        }
        removeNotExisted(users, uids, localUid);
    }

    private static void removeNotExisted(ArrayList<UserStatusData> users, HashMap<Integer, SurfaceView> uids, int localUid) {
        if (DEBUG) {
            log.debug("removeNotExisted all " + uids + " " + users.size());
        }
        Iterator<UserStatusData> it = users.iterator();
        while (it.hasNext()) {
            UserStatusData user = it.next();
            if (DEBUG) {
                log.debug("removeNotExisted " + user + " " + localUid);
            }
            if (uids.get(user.mUid) == null && user.mUid != localUid) {
                it.remove();
            }
        }
    }

    private static void searchUidsAndAppend(ArrayList<UserStatusData> users, HashMap.Entry<Integer, SurfaceView> entry,
                                            int localUid, Integer status, int volume, VideoInfoData i, List<UserModel> userModelList, int myUid) {
        if (entry.getKey() == myUid || entry.getKey() == localUid) {
            boolean found = false;
            for (UserStatusData user : users) {
                if ((user.mUid == entry.getKey() && user.mUid == myUid) || user.mUid == localUid) { // first time
                    user.mUid = localUid;
                    if (status != null) {
                        user.mStatus = status;
                    }
                    user.mVolume = volume;
                    user.setVideoInfo(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                Log.i("sherry", "searchUidsAndAppend:UserStatusData.size()=" + users.size() + ",localUid=" + localUid + ",userModel.size()=" + userModelList.size());
                users.add(0, new UserStatusData(localUid, entry.getValue(), status, volume, i, userModelList.get(localUid - 1).getName(), myUid));
            }
        } else {
            boolean found = false;
            for (UserStatusData user : users) {
                if (user.mUid == entry.getKey()) {
                    if (status != null) {
                        user.mStatus = status;
                    }
                    user.mVolume = volume;
                    user.setVideoInfo(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                users.add(new UserStatusData(entry.getKey(), entry.getValue(), status, volume, i, userModelList.get(entry.getKey() - 1).getName(), myUid));
            }
        }
    }

    public static void composeDataItem(final ArrayList<UserStatusData> users,
                                       HashMap<Integer, SurfaceView> uids,
                                       int localUid,
                                       HashMap<Integer, Integer> status,
                                       HashMap<Integer, Integer> volume,
                                       HashMap<Integer, VideoInfoData> video,
                                       List<UserModel> userModelList,
                                       int myUid) {
        composeDataItem(users, uids, localUid, status, volume, video, 0, userModelList, myUid);
    }

    public static void composeDataItem(final ArrayList<UserStatusData> users, HashMap<Integer, SurfaceView> uids,
                                       int localUid,
                                       HashMap<Integer, Integer> status,
                                       HashMap<Integer, Integer> volume,
                                       HashMap<Integer, VideoInfoData> video, int uidExcepted,
                                       List<UserModel> userModelList,
                                       int myUid) {
        for (HashMap.Entry<Integer, SurfaceView> entry : uids.entrySet()) {

            int uid = entry.getKey();

            if (uid == uidExcepted && uidExcepted != myUid) {
                continue;
            }

            boolean local = uid == myUid || uid == localUid;

            Integer s = null;
            if (status != null) {
                s = status.get(uid);
                if (local && s == null) { // check again
                    s = status.get(uid == myUid ? localUid : 0);
                }
            }
            Integer v = null;
            if (volume != null) {
                v = volume.get(uid);
                if (local && v == null) { // check again
                    v = volume.get(uid == myUid ? localUid : 0);
                }
            }
            if (v == null) {
                v = UserStatusData.DEFAULT_VOLUME;
            }
            VideoInfoData i;
            if (video != null) {
                i = video.get(uid);
                if (local && i == null) { // check again
                    i = video.get(uid == myUid ? localUid : 0);
                }
            } else {
                i = null;
            }
            if (DEBUG) {
                log.debug("composeDataItem " + users + " " + entry + " " + (localUid & 0XFFFFFFFFL) + " " + s + " " + v + " " + i + " " + local + " " + (uid & 0XFFFFFFFFL) + " " + (uidExcepted & 0XFFFFFFFFL));
            }
            searchUidsAndAppend(users, entry, localUid, s, v, i, userModelList, myUid);
        }

        removeNotExisted(users, uids, localUid);
    }

    public static void renderExtraData(boolean isFirst, Context context, int size, UserStatusData user, VideoUserStatusHolder myHolder, int position) {
        if (DEBUG) {
            log.debug("renderExtraData " + user + " " + myHolder);
        }

        if (user.mStatus != null) {
            if ((user.mStatus & UserStatusData.VIDEO_MUTED) != 0) {
                myHolder.mAvatar.setVisibility(View.VISIBLE);
                myHolder.mMaskView.setBackground(context.getResources().getDrawable(R.drawable.video_talk_bg));
            } else {
                myHolder.mAvatar.setVisibility(View.GONE);
                myHolder.mMaskView.setBackgroundColor(Color.TRANSPARENT);
            }

            if ((user.mStatus & UserStatusData.AUDIO_MUTED) != 0) {
                myHolder.mIndicator.setImageResource(R.drawable.video_icon_mute_sel);
                myHolder.mIndicator.setVisibility(View.VISIBLE);
                myHolder.mIndicator.setTag(System.currentTimeMillis());
                return;
            } else {
                myHolder.mIndicator.setTag(null);
                myHolder.mIndicator.setVisibility(View.INVISIBLE);
            }
        }

        Object tag = myHolder.mIndicator.getTag();
        if (tag != null && System.currentTimeMillis() - (Long) tag < 1500) { // workaround for audio volume comes just later than mute
            return;
        }

        int volume = user.mVolume;

        if (volume > 0) {
            myHolder.mIndicator.setImageResource(R.drawable.video_icon_loudspeaker_sel);
            myHolder.mIndicator.setVisibility(View.VISIBLE);
        } else {
            myHolder.mIndicator.setVisibility(View.INVISIBLE);
        }

//        if (Constant.SHOW_VIDEO_INFO && user.getVideoInfoData() != null) {
//            VideoInfoData videoInfo = user.getVideoInfoData();
//            myHolder.mMetaData.setText(ViewUtil.composeVideoInfoString(context, videoInfo));
//            myHolder.mVideoInfo.setVisibility(View.VISIBLE);
//        } else {
//            myHolder.mVideoInfo.setVisibility(View.GONE);
//        }

        log.info("position=" + position);
        if (isFirst && user.mStatus == null && user.mUid == 1) {
            myHolder.mUserName.setVisibility(View.GONE);
        } else {
            myHolder.mUserName.setText(user.mUserName);
            myHolder.mUserName.setVisibility(View.VISIBLE);
        }
    }

    public static void stripView(SurfaceView view) {
        ViewParent parent = view.getParent();
        if (parent != null) {
            ((FrameLayout) parent).removeView(view);
        }
    }
}