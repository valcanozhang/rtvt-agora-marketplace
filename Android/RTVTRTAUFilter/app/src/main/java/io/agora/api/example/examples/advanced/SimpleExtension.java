package io.agora.api.example.examples.advanced;

import static io.agora.api.example.common.model.Examples.ADVANCED;
import static io.agora.rtc2.Constants.CLIENT_ROLE_BROADCASTER;
import static io.agora.rtc2.Constants.RENDER_MODE_HIDDEN;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import io.agora.api.example.R;
import io.agora.api.example.annotation.Example;
import io.agora.api.example.common.BaseFragment;
import io.agora.api.example.utils.CommonUtil;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineConfig;
import io.agora.rtc2.video.CameraCapturerConfiguration;
import io.agora.rtc2.video.VideoCanvas;

/**
 * This demo demonstrates how to make a one-to-one voice call
 *
 * @author cjw
 */
@Example(
        index = 24,
        group = ADVANCED,
        name = R.string.item_ext,
        actionId = R.id.action_mainFragment_extension,
        tipsId = R.string.simple_extension
)



public class SimpleExtension extends BaseFragment implements View.OnClickListener, io.agora.rtc2.IMediaExtensionObserver {
    android.media.MediaPlayer mediaPlayer = new MediaPlayer();
    private static final String TAG = SimpleExtension.class.getSimpleName();
    public static final String EXTENSION_NAME = "agora-iLiveData-filter"; // Name of target link library used in CMakeLists.txt
    public static final String EXTENSION_VENDOR_NAME = "iLiveData"; // Provider name used for registering in agora-bytedance.cpp
    public static final String EXTENSION_VIDEO_FILTER_WATERMARK = "RTAU"; // Video filter name defined in LiveDataExtensionProvider.h
    public static final String EXTENSION_AUDIO_FILTER_VOLUME = "RTVT"; // Audio filter name defined in LiveDataExtensionProvider.h
    public static final String KEY_ENABLE_WATER_MARK = "key";
    public static final String ENABLE_WATER_MARK_FLAG = "plugin.watermark.wmEffectEnabled";
    public static final String ENABLE_WATER_MARK_STRING = "plugin.watermark.wmStr";
    public static final String KEY_ADJUST_VOLUME_CHANGE = "volume";
    private FrameLayout local_view, remote_view;
    private EditText et_channel;
    private Button join;
    private RtcEngine engine;
    private int myUid;
    private boolean joined = false;
    private SeekBar record;
    ListView rtvttestview;
    ArrayAdapter srcadapter;
    ArrayList<String> srcarrayList = new ArrayList<>();


    private Button startaudit, closeAudit, starttrans,stoptrans, stopextension;



    private void initMediaPlayer() {
        try {
            AssetFileDescriptor fd = getActivity().getAssets().openFd("zh.wav");
            mediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mediaPlayer.setLooping(true);//设置为循环播放
            mediaPlayer.prepare();//初始化播放器MediaPlayer
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void addlog(String msg){
        this.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                srcarrayList.add(msg);
                srcadapter.notifyDataSetChanged();
            }
        });
    }

    SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if(joined && seekBar.getId() == record.getId()){
                engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, KEY_ADJUST_VOLUME_CHANGE, ""+progress);
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_extension, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        join = view.findViewById(R.id.btn_join);
        startaudit = view.findViewById(R.id.startaudit);
        closeAudit = view.findViewById(R.id.closeAudit);
        starttrans = view.findViewById(R.id.starttrans);
        stoptrans = view.findViewById(R.id.stoptrans);
        stopextension = view.findViewById(R.id.stopextension);

        et_channel = view.findViewById(R.id.et_channel);
        view.findViewById(R.id.btn_join).setOnClickListener(this);
        record = view.findViewById(R.id.recordingVol);
        record.setOnSeekBarChangeListener(seekBarChangeListener);
        record.setEnabled(false);
        local_view = view.findViewById(R.id.fl_local);
        remote_view = view.findViewById(R.id.fl_remote);
        rtvttestview = view.findViewById(R.id.rtvttest);
        srcadapter = new ArrayAdapter(getContext(), android.R.layout.simple_list_item_1, srcarrayList);
        rtvttestview.setAdapter(srcadapter);


        stopextension.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.enableExtension(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, false);
            }
        });

        starttrans.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JSONObject jsonObject = new JSONObject();
                try {
//                    Log.i("sdktest", "java token is " + ApiSecurityExample.genToken(80001000,"qwerty"));
                    String spid  = getString(R.string.livedata_translate_pid);
                    if (spid.isEmpty()){
                        showAlert("请配置实时翻译的项目id");
                        return;
                    }
                    long pid = Long.parseLong(spid);

                    String skey  = getString(R.string.livedata_translate_key);
                    if (skey.isEmpty()){
                        showAlert("请配置实时翻译的秘钥");
                        return;
                    }

                    jsonObject.put("srclang", "zh");
                    jsonObject.put("dstLang", "en");
                    jsonObject.put("appKey", pid);
                    jsonObject.put("appSecret", skey);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Toast.makeText(getContext(), "开始翻译", Toast.LENGTH_LONG).show();

                engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, "startAudioTranslation", jsonObject.toString());
            }
        });

        stoptrans.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, "closeAudioTranslation", "{}");
            }
        });


        startaudit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JSONObject jsonObject = new JSONObject();
                try {
//                    Log.i("sdktest", "java token is " + ApiSecurityExample.genToken(80001000,"qwerty"));
                    ArrayList<String> attrs = new ArrayList<String>(){{add("1");add("2");}};

                    String callbackUrl = getString(R.string.livedata_callbackUrl);

                    jsonObject.put("streamId", String.valueOf(System.currentTimeMillis()));
                    jsonObject.put("callbackUrl", callbackUrl);
                    jsonObject.put("audioLang", "zh-CN");


                    String spid  = getString(R.string.livedata_audit_pid);
                    if (spid.isEmpty()){
                        showAlert("请配置实时审核的项目id");
                        return;
                    }
                    long pid = Long.parseLong(spid);

                    String skey  = getString(R.string.livedata_audit_key);
                    if (skey.isEmpty()){
                        showAlert("请配置实时审核的秘钥");
                        return;
                    }

                    jsonObject.put("appKey", pid);
                    jsonObject.put("appSecret", skey);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Toast.makeText(getContext(), "开始审核", Toast.LENGTH_LONG).show();
                Log.i("sdktest","start json " + jsonObject.toString());
                int ret = engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_VIDEO_FILTER_WATERMARK, "startAudit", jsonObject.toString());
            }
        });

        closeAudit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                engine.enableExtension(EXTENSION_VENDOR_NAME, EXTENSION_VIDEO_FILTER_WATERMARK, false);


                Toast.makeText(getContext(), "结束审核", Toast.LENGTH_LONG).show();

                int ret = engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_VIDEO_FILTER_WATERMARK, "closeAudit", "{}");
                Log.i("sdktest","setExtensionProperty closeAudit " + ret);

            }
        });
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Check if the context is valid
        Context context = getContext();
        if (context == null) {
            return;
        }
//        if(!hasAgoraSimpleFilterLib()){
//            new AlertDialog.Builder(context)
//                    .setMessage(R.string.simple_extension_tip)
//                    .setCancelable(false)
//                    .setPositiveButton(R.string.ok, (dialog, which) -> {
//                        dialog.dismiss();
//                        requireActivity().finish();
//                    })
//                    .show();
//            return;
//        }
        try {
            RtcEngineConfig config = new RtcEngineConfig();
            /**
             * The context of Android Activity
             */
            config.mContext = context.getApplicationContext();
            /**
             * The App ID issued to you by Agora. See <a href="https://docs.agora.io/en/Agora%20Platform/token#get-an-app-id"> How to get the App ID</a>
             */
            config.mAppId = getString(R.string.agora_app_id);
            if (config.mAppId.isEmpty()){
                showAlert("请配置声网appid");
                return;
            }
            /** Sets the channel profile of the Agora RtcEngine.
             CHANNEL_PROFILE_COMMUNICATION(0): (Default) The Communication profile.
             Use this profile in one-on-one calls or group calls, where all users can talk freely.
             CHANNEL_PROFILE_LIVE_BROADCASTING(1): The Live-Broadcast profile. Users in a live-broadcast
             channel have a role as either broadcaster or audience. A broadcaster can both send and receive streams;
             an audience can only receive streams.*/
            config.mChannelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING;
            /**
             * IRtcEngineEventHandler is an abstract class providing default implementation.
             * The SDK uses this class to report to the app on SDK runtime events.
             */
            //Name of dynamic link library is provided by plug-in vendor,
            //e.g. libagora-bytedance.so whose EXTENSION_NAME should be "agora-bytedance"
            //and one or more plug-ins can be added
            config.addExtension(EXTENSION_NAME);
            config.mExtensionObserver = this;
            config.mEventHandler = iRtcEngineEventHandler;
            engine = RtcEngine.create(config);
            /**
             * Enable/Disable extension.
             *
             * @param id id for extension, e.g. agora.beauty.
             * @param enable enable or disable.
             * - true: enable.
             * - false: disable.
             *
             * @return
             * - 0: Success.
             * - < 0: Failure.
             */
            int ret = engine.enableExtension(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, true);
            // enable video filter before enable video

            Log.i("sdktest", "ret is " + ret);
            ret = engine.enableExtension(EXTENSION_VENDOR_NAME, EXTENSION_VIDEO_FILTER_WATERMARK, true);
            // enable video filter before enable video

            Log.i("sdktest", "ret is " + ret);

            if (!AndPermission.hasPermissions(this, Permission.Group.STORAGE, Permission.Group.MICROPHONE, Permission.Group.CAMERA)) {

                // Request permission
                AndPermission.with(this).runtime().permission(
                        Permission.Group.STORAGE,
                        Permission.Group.MICROPHONE,
                        Permission.Group.CAMERA
                ).onGranted(permissions ->
                {
                    engine.enableVideo();
                    TextureView textureView = new TextureView(context);
                    if(local_view.getChildCount() > 0)
                    {
                        local_view.removeAllViews();
                    }
                    // Add to the local container
                    local_view.addView(textureView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    // Setup local video to render your local camera preview
                    engine.setupLocalVideo(new VideoCanvas(textureView, RENDER_MODE_HIDDEN, 0));
                    engine.startPreview();
                }).start();
            }
            else{
                engine.enableVideo();
                TextureView textureView = new TextureView(context);
                if(local_view.getChildCount() > 0)
                {
                    local_view.removeAllViews();
                }
                local_view.addView(textureView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                engine.setupLocalVideo(new VideoCanvas(textureView, RENDER_MODE_HIDDEN, 0));
                engine.startPreview();
            }



//            initMediaPlayer();
        }
        catch (Exception e) {
            e.printStackTrace();
            getActivity().onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        /**leaveChannel and Destroy the RtcEngine instance*/
        if (engine != null) {
            engine.leaveChannel();
        }
        engine.enableExtension(EXTENSION_VENDOR_NAME, EXTENSION_AUDIO_FILTER_VOLUME, false);
        // enable video filter before enable video
        engine.enableExtension(EXTENSION_VENDOR_NAME, EXTENSION_VIDEO_FILTER_WATERMARK, false);
        handler.post(RtcEngine::destroy);
        engine = null;
    }

    private void setWaterMarkProperty(){

        String jsonValue = null;
        JSONObject o = new JSONObject();
        try {
            o.put(ENABLE_WATER_MARK_STRING, "hello world");
            o.put(ENABLE_WATER_MARK_FLAG, true);
            jsonValue = o.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (jsonValue != null) {
            engine.setExtensionProperty(EXTENSION_VENDOR_NAME, EXTENSION_VIDEO_FILTER_WATERMARK, KEY_ENABLE_WATER_MARK, jsonValue);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_join) {
            if (!joined) {
                CommonUtil.hideInputBoard(getActivity(), et_channel);
                // call when join button hit
                String channelId = et_channel.getText().toString();
                // Check permission
                if (AndPermission.hasPermissions(this, Permission.Group.STORAGE, Permission.Group.MICROPHONE)) {
                    joinChannel(channelId);
                    return;
                }
                // Request permission
                AndPermission.with(this).runtime().permission(
                        Permission.Group.STORAGE,
                        Permission.Group.MICROPHONE
                ).onGranted(permissions ->
                {
                    // Permissions Granted
                    joinChannel(channelId);
                }).start();
            } else {
                joined = false;
                /**After joining a channel, the user must call the leaveChannel method to end the
                 * call before joining another channel. This method returns 0 if the user leaves the
                 * channel and releases all resources related to the call. This method call is
                 * asynchronous, and the user has not exited the channel when the method call returns.
                 * Once the user leaves the channel, the SDK triggers the onLeaveChannel callback.
                 * A successful leaveChannel method call triggers the following callbacks:
                 *      1:The local client: onLeaveChannel.
                 *      2:The remote client: onUserOffline, if the user leaving the channel is in the
                 *          Communication channel, or is a BROADCASTER in the Live Broadcast profile.
                 * @returns 0: Success.
                 *          < 0: Failure.
                 * PS:
                 *      1:If you call the destroy method immediately after calling the leaveChannel
                 *          method, the leaveChannel process interrupts, and the SDK does not trigger
                 *          the onLeaveChannel callback.
                 *      2:If you call the leaveChannel method during CDN live streaming, the SDK
                 *          triggers the removeInjectStreamUrl method.*/
                engine.leaveChannel();
                join.setText(getString(R.string.join));
                record.setEnabled(false);
                record.setProgress(0);
            }
        }
    }

    /**
     * @param channelId Specify the channel name that you want to join.
     *                  Users that input the same channel name join the same channel.
     */
    private void joinChannel(String channelId) {
        /**In the demo, the default is to enter as the anchor.*/
        engine.setClientRole(CLIENT_ROLE_BROADCASTER);
        /**Please configure accessToken in the string_config file.
         * A temporary token generated in Console. A temporary token is valid for 24 hours. For details, see
         *      https://docs.agora.io/en/Agora%20Platform/token?platform=All%20Platforms#get-a-temporary-token
         * A token generated at the server. This applies to scenarios with high-security requirements. For details, see
         *      https://docs.agora.io/en/cloud-recording/token_server_java?platform=Java*/
        String accessToken = getString(R.string.agora_access_token);
        if (TextUtils.equals(accessToken, "") || TextUtils.equals(accessToken, "<#YOUR ACCESS TOKEN#>")) {
            accessToken = null;
        }
        engine.enableAudioVolumeIndication(1000, 3, false);
        ChannelMediaOptions option = new ChannelMediaOptions();
        option.autoSubscribeAudio = true;
        option.autoSubscribeVideo = true;
        int res = engine.joinChannel(accessToken, channelId, 0, option);
        if (res != 0) {
            // Usually happens with invalid parameters
            // Error code description can be found at:
            // en: https://docs.agora.io/en/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
            // cn: https://docs.agora.io/cn/Voice/API%20Reference/java/classio_1_1agora_1_1rtc_1_1_i_rtc_engine_event_handler_1_1_error_code.html
            showAlert(RtcEngine.getErrorDescription(Math.abs(res)));
            Log.e(TAG, RtcEngine.getErrorDescription(Math.abs(res)));
            return;
        }
        // Prevent repeated entry
        join.setEnabled(false);


    }



    /**
     * IRtcEngineEventHandler is an abstract class providing default implementation.
     * The SDK uses this class to report to the app on SDK runtime events.
     */
    private final IRtcEngineEventHandler iRtcEngineEventHandler = new IRtcEngineEventHandler() {
        /**Occurs when a user leaves the channel.
         * @param stats With this callback, the application retrieves the channel information,
         *              such as the call duration and statistics.*/
        @Override
        public void onLeaveChannel(RtcStats stats) {
            super.onLeaveChannel(stats);
            Log.i(TAG, String.format("local user %d leaveChannel!", myUid));
            showLongToast(String.format("local user %d leaveChannel!", myUid));
        }

        /**Occurs when the local user joins a specified channel.
         * The channel name assignment is based on channelName specified in the joinChannel method.
         * If the uid is not specified when joinChannel is called, the server automatically assigns a uid.
         * @param channel Channel name
         * @param uid User ID
         * @param elapsed Time elapsed (ms) from the user calling joinChannel until this callback is triggered*/
        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            Log.i(TAG, String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
            showLongToast(String.format("onJoinChannelSuccess channel %s uid %d", channel, uid));
            myUid = uid;
            joined = true;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    join.setEnabled(true);
                    join.setText(getString(R.string.leave));
                    record.setEnabled(true);
                    record.setProgress(100);
                }
            });
        }

        /**Occurs when a remote user (Communication)/host (Live Broadcast) joins the channel.
         * @param uid ID of the user whose audio state changes.
         * @param elapsed Time delay (ms) from the local user calling joinChannel/setClientRole
         *                until this callback is triggered.*/
        @Override
        public void onUserJoined(int uid, int elapsed) {
            super.onUserJoined(uid, elapsed);
            Log.i(TAG, "onUserJoined->" + uid);
            showLongToast(String.format("user %d joined!", uid));
            /**Check if the context is correct*/
            Context context = getContext();
            if (context == null) {
                return;
            }
            else{
                handler.post(() ->
                {
                    if(remote_view.getChildCount() > 0){
                        remote_view.removeAllViews();
                    }
                    /**Display remote video stream*/
                    TextureView textureView = null;
                    // Create render view by RtcEngine
                    textureView = new TextureView(context);
                    // Add to the remote container
                    remote_view.addView(textureView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    // Setup remote video to render
                    engine.setupRemoteVideo(new VideoCanvas(textureView, RENDER_MODE_HIDDEN, uid));
                });
            }
        }

        /**Occurs when a remote user (Communication)/host (Live Broadcast) leaves the channel.
         * @param uid ID of the user whose audio state changes.
         * @param reason Reason why the user goes offline:
         *   USER_OFFLINE_QUIT(0): The user left the current channel.
         *   USER_OFFLINE_DROPPED(1): The SDK timed out and the user dropped offline because no data
         *              packet was received within a certain period of time. If a user quits the
         *               call and the message is not passed to the SDK (due to an unreliable channel),
         *               the SDK assumes the user dropped offline.
         *   USER_OFFLINE_BECOME_AUDIENCE(2): (Live broadcast only.) The client role switched from
         *               the host to the audience.*/
        @Override
        public void onUserOffline(int uid, int reason) {
            Log.i(TAG, String.format("user %d offline! reason:%d", uid, reason));
            showLongToast(String.format("user %d offline! reason:%d", uid, reason));
            handler.post(new Runnable() {
                @Override
                public void run() {
                    /**Clear render view
                     Note: The video will stay at its last frame, to completely remove it you will need to
                     remove the SurfaceView from its parent*/
                    engine.setupRemoteVideo(new VideoCanvas(null, RENDER_MODE_HIDDEN, uid));
                }
            });
        }

        @Override
        public void onActiveSpeaker(int uid) {
            super.onActiveSpeaker(uid);
            Log.i(TAG, String.format("onActiveSpeaker:%d", uid));
        }
    };


    @Override
    public void onEvent(String vendor, String extension, String key, String value) {
        if (vendor.equals("iLiveData"))
            addlog(value);
    }


    @Override
    public void onStarted(String s, String s1) {

    }

    @Override
    public void onStopped(String s, String s1) {

    }

    @Override
    public void onError(String s, String s1, int i, String s2) {

    }
}
