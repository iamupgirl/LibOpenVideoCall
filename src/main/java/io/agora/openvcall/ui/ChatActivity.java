package io.agora.openvcall.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.TextHttpResponseHandler;

import org.apache.http.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.agora.openvcall.R;
import io.agora.openvcall.model.AGEventHandler;
import io.agora.openvcall.model.ConstantApp;
import io.agora.openvcall.model.UserModel;
import io.agora.openvcall.util.SPUtils;
import io.agora.openvcall.util.SoundPoolUtils;
import io.agora.propeller.Constant;
import io.agora.propeller.UserStatusData;
import io.agora.propeller.VideoInfoData;
import io.agora.propeller.ui.RtlLinearLayoutManager;
import io.agora.rtc.IRtcEngineEventHandler;
import io.agora.rtc.RtcEngine;
import io.agora.rtc.video.VideoCanvas;

public class ChatActivity extends BaseActivity implements AGEventHandler {

    private final static Logger log = LoggerFactory.getLogger(ChatActivity.class);

    private GridVideoViewContainer mGridVideoViewContainer;

    private RelativeLayout mSmallVideoViewDock;

    private LinearLayout vRlAskChat;

    private RelativeLayout vRlVideo;

    private TextView vTvCallerName;

    private Button vBtnCameraOrSpeaker;

    // should only be modified under UI thread
    private final HashMap<Integer, SurfaceView> mUidsList = new HashMap<>(); // uid = 0 || uid == EngineConfig.mUid

    private volatile boolean mVideoMuted = false;

    private volatile boolean mAudioMuted = false;

    private volatile int mAudioRouting = -1; // Default

    private int mGetTokenCount = 0;

    private boolean mIsGettingToken = false;

    private String mChannelName, mCallerName, mEncryptionKey, mEncryptionMode, mFPToken, mUsersStr, mToken, mAppId, mGetTokenUrl;

    private int mMyUid;

    private List<UserModel> mList = new ArrayList<>();

    private Vibrator vibrator;

    private MediaPlayer mMediaPlayer;

    private boolean mInRoom = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        SPUtils.put(this.getApplicationContext(), Constant.IS_CHATTING, true);

        SoundPoolUtils.init(this.getApplicationContext());

        startAlarm();

        startVibrate();

        Intent i = getIntent();

        mChannelName = i.getStringExtra(ConstantApp.ACTION_KEY_CHANNEL_NAME);

        mCallerName = i.getStringExtra(ConstantApp.ACTION_KEY_CALLER_NAME);

        mEncryptionKey = getIntent().getStringExtra(ConstantApp.ACTION_KEY_ENCRYPTION_KEY);

        mEncryptionMode = getIntent().getStringExtra(ConstantApp.ACTION_KEY_ENCRYPTION_MODE);

        mFPToken = getIntent().getStringExtra(ConstantApp.ACTION_KEY_ACCESS_APP_TOKEN);

        mUsersStr = getIntent().getStringExtra(ConstantApp.ACTION_KEY_ACCESS_USERS);

        mToken = getIntent().getStringExtra(ConstantApp.ACTION_KEY_ACCESS_TOKEN);

        mAppId = getIntent().getStringExtra(ConstantApp.ACTION_KEY_ACCESS_APP_ID);

        mMyUid = getIntent().getIntExtra(ConstantApp.ACTION_KEY_ACCESS_UID, 0);

        mGetTokenUrl = getIntent().getStringExtra(ConstantApp.ACTION_KEY_BASE_ADDRESS);

        SPUtils.put(this, Constant.TOKEN, mToken);
        SPUtils.put(this, Constant.APP_ID, mAppId);
        SPUtils.put(this, Constant.GET_TOKEN_URL, mGetTokenUrl);

        if (!TextUtils.isEmpty(mUsersStr)) {
            try {
                JSONArray jsonArray = new JSONArray(mUsersStr);
                if (!jsonArray.equals(null) && !jsonArray.equals("[]")) {
                    for (int j = 0; j < jsonArray.length(); j++) {
                        UserModel userModel = new UserModel();
                        JSONObject jsonObject = jsonArray.getJSONObject(j);
                        userModel.setId(jsonObject.getString("id"));
                        userModel.setName(jsonObject.getString("name"));
                        mList.add(userModel);
                    }
                }
            }
            catch (JSONException ex) {
                ex.printStackTrace();
            }
        }

        vRlAskChat = (LinearLayout) findViewById(R.id.rl_ask_chat);
        vRlVideo = (RelativeLayout) findViewById(R.id.rl_video_chat);
        vTvCallerName = (TextView) findViewById(R.id.tv_caller_name);

        vTvCallerName.setText(mCallerName);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }

    protected void initUIandEvent() {
        event().addEventHandler(this);

        doConfigEngine(mEncryptionKey, mEncryptionMode);

        mGridVideoViewContainer = (GridVideoViewContainer) findViewById(R.id.grid_video_view_container);
        mGridVideoViewContainer.setItemEventHandler(new VideoViewEventListener() {
            @Override
            public void onItemDoubleClick(View v, Object item) {
                log.debug("onItemDoubleClick " + v + " " + item + " " + mLayoutType);

                if (mUidsList.size() < 2) {
                    return;
                }

                UserStatusData user = (UserStatusData) item;
                int uid = (user.mUid == mMyUid) ? mMyUid : user.mUid;

                if (mLayoutType == LAYOUT_TYPE_DEFAULT && mUidsList.size() == 2) {
                    switchToSmallVideoView(uid);
                } else {
                    switchToDefaultVideoView();
                }
            }
        });

        SurfaceView surfaceV = RtcEngine.CreateRendererView(getApplicationContext());
        rtcEngine().setupLocalVideo(new VideoCanvas(surfaceV, VideoCanvas.RENDER_MODE_HIDDEN, mMyUid));
        surfaceV.setZOrderOnTop(false);
        surfaceV.setZOrderMediaOverlay(false);
        mUidsList.put(mMyUid, surfaceV); // get first surface view

        mGridVideoViewContainer.initViewContainer(this, mMyUid, mUidsList, mList, mMyUid); // first is now full view
        worker().preview(true, surfaceV, mMyUid);
        Log.i("sherry", "channelName=" + mChannelName + ",mMyUid=" + mMyUid + ",mUidsList.size()=" + mUidsList.size());
        worker().joinChannel(mChannelName, mMyUid);

        optional();

        LinearLayout bottomContainer = (LinearLayout) findViewById(R.id.bottom_container);
        FrameLayout.MarginLayoutParams fmp = (FrameLayout.MarginLayoutParams) bottomContainer.getLayoutParams();
        fmp.bottomMargin = virtualKeyHeight() + 16;

    }

    private void optional() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    private void optionalDestroy() {
    }

    private int getVideoProfileIndex() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(AgoraManager.mContext);
        int profileIndex = pref.getInt(ConstantApp.PrefManager.PREF_PROPERTY_PROFILE_IDX, ConstantApp.DEFAULT_PROFILE_IDX);
        if (profileIndex > ConstantApp.VIDEO_PROFILES.length - 1) {
            profileIndex = ConstantApp.DEFAULT_PROFILE_IDX;

            // save the new value
            SharedPreferences.Editor editor = pref.edit();
            editor.putInt(ConstantApp.PrefManager.PREF_PROPERTY_PROFILE_IDX, profileIndex);
            editor.apply();
        }
        return profileIndex;
    }

    private void doConfigEngine(String encryptionKey, String encryptionMode) {
        int vProfile = ConstantApp.VIDEO_PROFILES[getVideoProfileIndex()];

        worker().configEngine(vProfile, encryptionKey, encryptionMode);
    }

    public void onCustomizedFunctionClicked(View view) {
        log.info("onCustomizedFunctionClicked " + view + " " + mVideoMuted + " " + mAudioMuted + " " + mAudioRouting);
        if (mVideoMuted) {
            onSwitchSpeakerClicked();
        } else {
            onSwitchCameraClicked();
        }
    }

    /**
     * 转换为摄像头切换
     */
    private void onSwitchCameraClicked() {
        RtcEngine rtcEngine = rtcEngine();
        rtcEngine.switchCamera();
    }

    /**
     * 转换为 麦克风/外放
     */
    private void onSwitchSpeakerClicked() {
        RtcEngine rtcEngine = rtcEngine();
        rtcEngine.setEnableSpeakerphone(mAudioRouting != 3);
    }

    @Override
    protected void deInitUIandEvent() {
        optionalDestroy();

        doLeaveChannel();
        event().removeEventHandler(this);

        mUidsList.clear();
    }

    /**
     * 离开频道
     */
    private void doLeaveChannel() {
        mInRoom = false;
        worker().leaveChannel(config().mChannel);
        worker().preview(false, null, mMyUid);
    }

    /**
     * 挂断
     * @param view
     */
    public void onEndCallClicked(View view) {
        log.info("onEndCallClicked " + view);

        clickEndCall();
    }

    private void clickEndCall() {

        stopViberate();

        stopAlarm();

        showExitDlg();
    }

    private void showExitDlg() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(getString(R.string.exit_video_warn));
        dialog.setMessage(getString(R.string.exit_video_call));
        dialog.setPositiveButton(getString(R.string.sure), new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                arg0.dismiss();
                Toast.makeText(ChatActivity.this, "聊天结束", Toast.LENGTH_LONG).show();

                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                        SPUtils.put(ChatActivity.this, Constant.IS_CHATTING, false);
                    }
                }, 1500);
            }
        });
        dialog.setNeutralButton(getString(R.string.cancel), new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                if (mInRoom == false) {
                    clickReceiveCall();
                }
                arg0.dismiss();
            }
        });
        dialog.show();
    }

    /**
     * 接听
     * @param view
     */
    public void onReceiveCallClicked(View view) {
        log.info("onReceiveCallClicked" + view);

        clickReceiveCall();
    }

    private void clickReceiveCall() {

        mInRoom = true;

        stopViberate();

        stopAlarm();

        vRlAskChat.setVisibility(View.GONE);
        vRlVideo.setVisibility(View.VISIBLE);

        initUIandEvent();
    }

    /**
     * 切换到语音聊天
     * @param view
     */
    public void onVoiceChatClicked(View view) {
        log.info("onVoiceChatClicked " + view + " " + mUidsList.size() + " video_status: " + mVideoMuted + " audio_status: " + mAudioMuted);
        if (mUidsList.size() == 0) {
            return;
        }

        SurfaceView surfaceV = getLocalView();
        ViewParent parent;
        if (surfaceV == null || (parent = surfaceV.getParent()) == null) {
            log.warn("onVoiceChatClicked " + view + " " + surfaceV);
            return;
        }

        RtcEngine rtcEngine = rtcEngine();
        mVideoMuted = !mVideoMuted;

        if (mVideoMuted) {
            rtcEngine.disableVideo();
        } else {
            rtcEngine.enableVideo();
        }

        ImageView iv = (ImageView) view;

        iv.setImageResource(mVideoMuted ? R.drawable.video_icon_switch_video : R.drawable.video_icon_switch_voice);

        hideLocalView(mVideoMuted);

        vBtnCameraOrSpeaker = (Button) findViewById(R.id.customized_function_id);

        if (mVideoMuted) {
            resetToVideoDisabledUI();
        } else {
            resetToVideoEnabledUI();
        }
    }

    private SurfaceView getLocalView() {
        for (HashMap.Entry<Integer, SurfaceView> entry : mUidsList.entrySet()) {
            log.debug("getLocalView:mMyUid=" + mMyUid);

            if (entry.getKey() == mMyUid) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void hideLocalView(boolean hide) {
        log.info("hideLocalView:mMyUid=" + mMyUid);
        int uid = mMyUid;
        doHideTargetView(uid, hide);
    }

    private void doHideTargetView(int targetUid, boolean hide) {
        log.warn("doHideTargetView:targetUid=" + targetUid);
        HashMap<Integer, Integer> status = new HashMap<>();
        status.put(targetUid, hide ? UserStatusData.VIDEO_MUTED : UserStatusData.DEFAULT_STATUS);
        if (mLayoutType == LAYOUT_TYPE_DEFAULT) {
            mGridVideoViewContainer.notifyUiChanged(mUidsList, targetUid, status, null, mList, mMyUid);
        } else if (mLayoutType == LAYOUT_TYPE_SMALL) {
            UserStatusData bigBgUser = mGridVideoViewContainer.getItem(0);
            if (bigBgUser.mUid == targetUid) { // big background is target view
                mGridVideoViewContainer.notifyUiChanged(mUidsList, targetUid, status, null, mList, mMyUid);
            } else { // find target view in small video view list
                log.warn("SmallVideoViewAdapter call notifyUiChanged " + mUidsList + " " + (bigBgUser.mUid & 0xFFFFFFFFL) + " target: " + (targetUid & 0xFFFFFFFFL) + "==" + targetUid + " " + status);
                mSmallVideoViewAdapter.notifyUiChanged(mUidsList, bigBgUser.mUid, status, null, mList, mMyUid);
            }
        }
    }

    /**
     * 重置为视频聊天UI界面
     */
    private void resetToVideoEnabledUI() {
        vBtnCameraOrSpeaker.setCompoundDrawablesWithIntrinsicBounds(null, getResources().getDrawable(R.drawable.video_icon_switch_camera), null, null);
        notifyHeadsetPlugged(mAudioRouting);
    }

    /**
     * 重置为非视频聊天（即语音聊天）UI界面
     */
    private void resetToVideoDisabledUI() {
        notifyHeadsetPlugged(mAudioRouting);
    }

    /**
     * 静音按钮点击
     * @param view
     */
    public void onVoiceMuteClicked(View view) {
        log.info("onVoiceMuteClicked " + view + " " + mUidsList.size() + " video_status: " + mVideoMuted + " audio_status: " + mAudioMuted);
        if (mUidsList.size() == 0) {
            return;
        }

        RtcEngine rtcEngine = rtcEngine();
        rtcEngine.muteLocalAudioStream(mAudioMuted = !mAudioMuted);

        Button btnMute = (Button) view;

        if (mAudioMuted) {
            btnMute.setCompoundDrawablesWithIntrinsicBounds(null, getResources().getDrawable(R.drawable.video_icon_mute_sel), null, null);
        } else {
            btnMute.setCompoundDrawablesWithIntrinsicBounds(null, getResources().getDrawable(R.drawable.video_icon_mute), null, null);
        }
    }

    @Override
    public void onFirstRemoteVideoDecoded(int uid, int width, int height, int elapsed) {
        doRenderRemoteUi(uid);
    }

    /**
     * 创建视图表达远程视频UI
     * @param uid
     */
    private void doRenderRemoteUi(final int uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                if (mUidsList.containsKey(uid)) {
                    return;
                }

                SurfaceView surfaceV = RtcEngine.CreateRendererView(getApplicationContext());
                mUidsList.put(uid, surfaceV);

                boolean useDefaultLayout = mLayoutType == LAYOUT_TYPE_DEFAULT && mUidsList.size() != 2;
                log.info("useDefaultLayout=" + useDefaultLayout + ",uidSize=" + mUidsList.size());

                surfaceV.setZOrderOnTop(!useDefaultLayout);
                surfaceV.setZOrderMediaOverlay(!useDefaultLayout);

                rtcEngine().setupRemoteVideo(new VideoCanvas(surfaceV, VideoCanvas.RENDER_MODE_HIDDEN, uid));

                if (mUidsList.size() != 2) {
                    log.debug("doRenderRemoteUi LAYOUT_TYPE_DEFAULT " + (uid & 0xFFFFFFFFL));
                    switchToDefaultVideoView();
                } else {
                    int bigBgUid = mSmallVideoViewAdapter == null ? uid : mSmallVideoViewAdapter.getExceptedUid();
                    log.debug("doRenderRemoteUi LAYOUT_TYPE_SMALL " + (uid & 0xFFFFFFFFL) + " " + (bigBgUid & 0xFFFFFFFFL));
                    switchToSmallVideoView(bigBgUid);
                }
            }
        });
    }

    @Override
    public void onJoinChannelSuccess(String channel, final int uid, int elapsed) {
        log.debug("onJoinChannelSuccess " + channel + " " + (uid & 0xFFFFFFFFL) + " " + elapsed);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                SurfaceView local = mUidsList.remove(mMyUid);

                if (local == null) {
                    return;
                }

                mUidsList.put(uid, local);
                log.info("onJoinChannelSuccess:mUidsList.size()=" + mUidsList.size());
            }
        });
    }

    // 获取系统默认铃声的Uri
    private Uri getSystemDefultRingtoneUri() {
        return RingtoneManager.getActualDefaultRingtoneUri(this,RingtoneManager.TYPE_RINGTONE);
    }

    /**
     * 播放系统声音
     * */
    private void startAlarm() {
        //有的手机会创建失败，从而导致mMediaPlayer为空。
        mMediaPlayer = MediaPlayer.create(this, getSystemDefultRingtoneUri());
        if (mMediaPlayer == null) {//有的手机铃声会创建失败，如果创建失败，播放我们自己的铃声
            SoundPoolUtils.playCallWaitingAudio();//自己定义的铃音播放工具类。具体实现见下方
        } else {
            mMediaPlayer.setLooping(true);// 设置循环
            try {
                mMediaPlayer.prepare();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mMediaPlayer.start();
        }
    }

    /**
     * 停止播放来电声音
     */
    private void stopAlarm() {
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
                mMediaPlayer.release();
                mMediaPlayer = null;
            }
        }
        SoundPoolUtils.stopCallWaitingAudio();
    }


    /**
     * 开启震动
     */
    private void startVibrate() {
        if (vibrator == null) {
            //获取震动服务
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        //震动模式隔1秒震动1.4秒
        long[] pattern = { 1000, 1400 };
        //震动重复，从数组的0开始（-1表示不重复）
        vibrator.vibrate(pattern, 0);
    }

    /**
     * 停止震动
     */
    private void stopViberate() {
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    public void onUserOffline(int uid, int reason) {
        doRemoveRemoteUi(uid);
    }

    @Override
    public void onExtraCallback(final int type, final Object... data) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                doHandleExtraCallback(type, data);
            }
        });
    }

    private void doHandleExtraCallback(int type, Object... data) {
        int peerUid;
        boolean muted;

        switch (type) {
            case AGEventHandler.EVENT_TYPE_ON_USER_AUDIO_MUTED:
                peerUid = (Integer) data[0];
                muted = (boolean) data[1];

                if (mLayoutType == LAYOUT_TYPE_DEFAULT) {
                    HashMap<Integer, Integer> status = new HashMap<>();
                    status.put(peerUid, muted ? UserStatusData.AUDIO_MUTED : UserStatusData.DEFAULT_STATUS);
                    mGridVideoViewContainer.notifyUiChanged(mUidsList, mMyUid, status, null, mList, mMyUid);
                }

                break;

            case AGEventHandler.EVENT_TYPE_ON_USER_VIDEO_MUTED:
                peerUid = (Integer) data[0];
                muted = (boolean) data[1];

                doHideTargetView(peerUid, muted);

                break;

            case AGEventHandler.EVENT_TYPE_ON_USER_VIDEO_STATS:
                IRtcEngineEventHandler.RemoteVideoStats stats = (IRtcEngineEventHandler.RemoteVideoStats) data[0];

                if (Constant.SHOW_VIDEO_INFO) {
                    if (mLayoutType == LAYOUT_TYPE_DEFAULT) {
                        mGridVideoViewContainer.addVideoInfo(stats.uid, new VideoInfoData(stats.width, stats.height, stats.delay, stats.receivedFrameRate, stats.receivedBitrate));
                        int uid = mMyUid;
                        int profileIndex = getVideoProfileIndex();
                        String resolution = getResources().getStringArray(R.array.string_array_resolutions)[profileIndex];
                        String fps = getResources().getStringArray(R.array.string_array_frame_rate)[profileIndex];
                        String bitrate = getResources().getStringArray(R.array.string_array_bit_rate)[profileIndex];

                        String[] rwh = resolution.split("x");
                        int width = Integer.valueOf(rwh[0]);
                        int height = Integer.valueOf(rwh[1]);

                        mGridVideoViewContainer.addVideoInfo(uid, new VideoInfoData(width > height ? width : height,
                                width > height ? height : width,
                                0, Integer.valueOf(fps), Integer.valueOf(bitrate)));
                    }
                } else {
                    mGridVideoViewContainer.cleanVideoInfo();
                }

                break;

            case AGEventHandler.EVENT_TYPE_ON_SPEAKER_STATS:
                IRtcEngineEventHandler.AudioVolumeInfo[] infos = (IRtcEngineEventHandler.AudioVolumeInfo[]) data[0];

                if (infos.length == 1 && infos[0].uid == mMyUid) { // local guy, ignore it
                    break;
                }

                if (mLayoutType == LAYOUT_TYPE_DEFAULT) {
                    HashMap<Integer, Integer> volume = new HashMap<>();

                    for (IRtcEngineEventHandler.AudioVolumeInfo each : infos) {
                        peerUid = each.uid;
                        int peerVolume = each.volume;

                        if (peerUid == mMyUid) {
                            continue;
                        }
                        volume.put(peerUid, peerVolume);
                    }
                    mGridVideoViewContainer.notifyUiChanged(mUidsList, mMyUid, null, volume, mList, mMyUid);
                }

                break;

            case AGEventHandler.EVENT_TYPE_ON_APP_ERROR:
                int subType = (int) data[0];

                if (subType == ConstantApp.AppError.NO_NETWORK_CONNECTION) {
                    showLongToast(getString(R.string.msg_no_network_connection));
                }

                break;
            case AGEventHandler.EVENT_TYPE_ON_AUDIO_ROUTE_CHANGED:
                log.info("EVENT_TYPE_ON_AUDIO_ROUTE_CHANGED");
                notifyHeadsetPlugged((int) data[0]);
                break;

            case AGEventHandler.EVENT_TYPE_ON_AGORA_MEDIA_ERROR:
                log.info("EVENT_TYPE_ON_AGORA_MEDIA_ERROR");
                showLongToast("AGEventHandler.EVENT_TYPE_ON_AGORA_MEDIA_ERROR" + data[0]);
                int error = (int) data[0];
                if (error == 109 && !mIsGettingToken) {
                    mIsGettingToken = true;
                    getToken();
                }
                break;

        }
    }

    public void getToken() {
        // 创建异步的客户端对象
        AsyncHttpClient client = new AsyncHttpClient();
        // 请求的地址
        String url;
        if (!TextUtils.isEmpty(mGetTokenUrl)) {
            url = mGetTokenUrl;
        } else {
            url = (String) SPUtils.get(this, Constant.GET_TOKEN_URL,
                    "https://www.yunlinye.com/hlxj_test/v1/agoraController/token");
        }
        // 创建请求参数的封装的对象
        RequestParams params = new RequestParams();
        params.put("token", mFPToken);
        params.put("roomNo", mChannelName); // 设置请求的参数名和参数值
        params.put("uid", mMyUid);// 设置请求的参数名和参数

        client.get(url, params, new TextHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers,
                                  String data) {
                    log.info("sherry--success", "data=" + data);
                    try {
                        JSONObject jsonObject = new JSONObject(data);
                        int status = jsonObject.getInt("status");
                        if (status == 200) {
                            JSONObject body = jsonObject.getJSONObject("body");
                            String token = body.getString("body");
                            int result = rtcEngine().renewToken(token);
                            log.info("sherry--success", "result=" + result);
                            if (result != 0) {
                                mGetTokenCount++;
                                if (mGetTokenCount < 3) {
                                    getToken();
                                } else {
                                    showLongToast("您的视频通话已过期~");
                                    doLeaveChannel();
                                }
                            } else {
                                mGetTokenCount = 0;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                mIsGettingToken = false;
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String data, Throwable throwable) {

                log.info("sherry--fail", "data=" + data);
                // 失败处理的方法
                throwable.printStackTrace();
                mGetTokenCount++;
                if (mGetTokenCount < 3) {
                    getToken();
                } else {
                    showLongToast("您的视频通话已过期~");
                    finish();
                }
                mIsGettingToken = false;
            }
        });
    }

    private void requestRemoteStreamType(final int currentHostCount) {
        log.debug("requestRemoteStreamType " + currentHostCount);
    }

    /**
     * 移除远程视频对应的ui
     * @param uid 该远程视频的uid
     */
    private void doRemoveRemoteUi(final int uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                Object target = mUidsList.remove(uid);
                if (target == null) {
                    return;
                }

                int bigBgUid = -1;
                if (mSmallVideoViewAdapter != null) {
                    bigBgUid = mSmallVideoViewAdapter.getExceptedUid();
                }

                log.debug("doRemoveRemoteUi " + (uid & 0xFFFFFFFFL) + " " + (bigBgUid & 0xFFFFFFFFL) + " " + mLayoutType);

                if (mLayoutType == LAYOUT_TYPE_DEFAULT || uid == bigBgUid) {
                    switchToDefaultVideoView();
                } else {
                    switchToSmallVideoView(bigBgUid);
                }

                log.info("doRemoveRemoteUi:mUidList.size()" + mUidsList + ",uid=" + uid);
                if (mUidsList.size() == 1 || mList.get(uid - 1).getName().equals(mCallerName)) {
                    Toast.makeText(ChatActivity.this, "对方已挂断，聊天结束", Toast.LENGTH_LONG).show();
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ChatActivity.this.finish();
                            SPUtils.put(ChatActivity.this, Constant.IS_CHATTING, false);
                        }
                    }, 1000);
                }
            }
        });
    }

    private SmallVideoViewAdapter mSmallVideoViewAdapter;

    private void switchToDefaultVideoView() {
        if (mSmallVideoViewDock != null) {
            mSmallVideoViewDock.setVisibility(View.GONE);
        }

        log.info("switchToDefaultVideoView:mMyUid=" + mMyUid);
        mGridVideoViewContainer.initViewContainer(this, mMyUid, mUidsList, mList, mMyUid);

        mLayoutType = LAYOUT_TYPE_DEFAULT;
    }

    /**
     * 转换成小的视频视图
     * @param bigBgUid
     */
    private void switchToSmallVideoView(int bigBgUid) {
        HashMap<Integer, SurfaceView> slice = new HashMap<>(1);
        slice.put(bigBgUid, mUidsList.get(bigBgUid));
        mGridVideoViewContainer.initViewContainer(this, bigBgUid, slice, mList, mMyUid);

        bindToSmallVideoView(bigBgUid);

        mLayoutType = LAYOUT_TYPE_SMALL;

        requestRemoteStreamType(mUidsList.size());
    }

    public int mLayoutType = LAYOUT_TYPE_DEFAULT;

    public static final int LAYOUT_TYPE_DEFAULT = 0;

    public static final int LAYOUT_TYPE_SMALL = 1;

    /**
     * 约束成小的视频视图
     * @param exceptUid
     */
    private void bindToSmallVideoView(int exceptUid) {
        if (mSmallVideoViewDock == null) {
            ViewStub stub = (ViewStub) findViewById(R.id.small_video_view_dock);
            mSmallVideoViewDock = (RelativeLayout) stub.inflate();
        }

        boolean twoWayVideoCall = mUidsList.size() == 2;

        RecyclerView recycler = (RecyclerView) findViewById(R.id.small_video_view_container);

        boolean create = false;

        if (mSmallVideoViewAdapter == null) {
            create = true;
            mSmallVideoViewAdapter = new SmallVideoViewAdapter(this, mMyUid, exceptUid, mUidsList, new VideoViewEventListener() {
                @Override
                public void onItemDoubleClick(View v, Object item) {
                    switchToDefaultVideoView();
                }
            }, mList, mMyUid);
            mSmallVideoViewAdapter.setHasStableIds(true);
        }
        recycler.setHasFixedSize(true);

        log.debug("bindToSmallVideoView " + twoWayVideoCall + " " + (exceptUid & 0xFFFFFFFFL));

        if (twoWayVideoCall) {
            log.info("context---bindToSmallVideoView--1---" + getApplicationContext());
            recycler.setLayoutManager(new RtlLinearLayoutManager(getApplicationContext(), RtlLinearLayoutManager.HORIZONTAL, false));
        } else {
            log.info("context---bindToSmallVideoView--2---" + getApplicationContext());
            recycler.setLayoutManager(new LinearLayoutManager(getApplicationContext(), LinearLayoutManager.HORIZONTAL, false));
        }
        recycler.addItemDecoration(new SmallVideoViewDecoration());
        recycler.setAdapter(mSmallVideoViewAdapter);

        recycler.setDrawingCacheEnabled(true);
        recycler.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_AUTO);

        if (!create) {
            mSmallVideoViewAdapter.setLocalUid(mMyUid);
            mSmallVideoViewAdapter.notifyUiChanged(mUidsList, exceptUid, null, null, mList, mMyUid);
        }
        recycler.setVisibility(View.VISIBLE);
        mSmallVideoViewDock.setVisibility(View.VISIBLE);
    }

    public void notifyHeadsetPlugged(final int routing) {
        log.info("notifyHeadsetPlugged " + routing + " " + mVideoMuted);

        mAudioRouting = routing;

        if (!mVideoMuted) {
            return;
        }

        if (mAudioRouting == 3) { // Speakerphone
            vBtnCameraOrSpeaker.setCompoundDrawablesWithIntrinsicBounds(null, getResources().getDrawable(R.drawable.video_icon_loudspeaker_sel), null, null);
        } else {
            vBtnCameraOrSpeaker.setCompoundDrawablesWithIntrinsicBounds(null, getResources().getDrawable(R.drawable.video_icon_loudspeaker_normal), null, null);
        }
    }

    @Override
    public void onBackPressed() {
        clickEndCall();
        super.onBackPressed();
    }
}
