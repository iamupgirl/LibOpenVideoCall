package io.agora.openvcall.util;

/**
 * Created by shanxs on 2017/10/20.
 */

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.SparseIntArray;

import io.agora.openvcall.R;

/**
 * 音效播放工具类(软件启动之前需要先调用SoundPoolUtils.init()方法做初始化)
 * 【特别注意】系统权限：MEDIA_CONTENT_CONTROL、MODIFY_AUDIO_SETTINGS、READ_EXTERNAL_STORAGE
 * @author 蓝亭书序
 *
 */
public class SoundPoolUtils {
    // 标记是否已经初始化过
    private static boolean inited = false;
    private static final int MAX_STREAMS = 10;
    private static SoundPool soundPool;

    // 声音池
    private static SparseIntArray soundMap = new SparseIntArray();

    /**
     * 声音录制准备完毕（一般开始录制前需要播放）的索引号
     */
    public static final int RECORD_PREPARED = 1;
    /**
     * 消息成功发送的音频提示索引号
     */
    public static final int MSG_SEND = 2;
    /**
     * 语音接通中的等待音
     */
    public static final int CALL_WAITING = 3;
    // 正在播放的等待音的ID
    private static int CALL_PLAYING_ID = -1;
    /**
     * 切换群组的提示音
     */
    public static final int GROUP_SWITCH_TIP = 4;

    /**
     *
     * @param context
     */
    @SuppressWarnings("deprecation")
    public static void init(Context context) {
        if (context != null) {
            if (!inited) {
                soundPool = new SoundPool(MAX_STREAMS,
                        AudioManager.STREAM_MUSIC, 0);
//                soundMap.append(RECORD_PREPARED,
//                        soundPool.load(context, R.raw.start_record, 1));
//                soundMap.append(MSG_SEND,
//                        soundPool.load(context, R.raw.msg_send, 1));
                soundMap.append(CALL_WAITING,
                        soundPool.load(context, R.raw.call_waiting, 1));
//                soundMap.append(GROUP_SWITCH_TIP,
//                        soundPool.load(context, R.raw.group_switch_tip, 1));
                inited = true;
            }
        }
    }

    /**
     * 播放录音准备音
     */
    public static void playRecordPreAudio() {
        soundPool.play(soundMap.get(RECORD_PREPARED), 1, 1, 0, 0, 1);
    }

    /**
     * 播放消息发送成功（或者是录制完毕的提示音）
     */
    public static void playMessageSendAudio() {
        soundPool.play(soundMap.get(MSG_SEND), 1, 1, 0, 0, 1);
    }

    /**
     * 播放语音连接中的等待音（这个声音会循环播放很久，类似手机来电声音效果）
     */
    public static void playCallWaitingAudio() {
        CALL_PLAYING_ID = soundPool.play(soundMap.get(CALL_WAITING), 1, 1, 0,
                -1, 1);
    }

    /**
     * 停止播放等待音
     */
    public static void stopCallWaitingAudio() {
        if (soundPool != null) {
            soundPool.stop(CALL_PLAYING_ID);
        } else {
            soundPool = new SoundPool(MAX_STREAMS,
                    AudioManager.STREAM_MUSIC, 0);
            soundPool.stop(CALL_PLAYING_ID);
        }
        CALL_PLAYING_ID = -1;
    }

    /**
     * 播放群组切换成功的提示音
     */
    public static void playGroupSwitchTipAudio() {
        soundPool.play(soundMap.get(GROUP_SWITCH_TIP), 1, 1, 0, 0, 1);
    }
}
