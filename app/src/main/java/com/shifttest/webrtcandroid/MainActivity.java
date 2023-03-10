package com.shifttest.webrtcandroid;

import static android.content.ContentValues.TAG;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

public class MainActivity extends AppCompatActivity {
    EglBase rootEglBase;
    private String socketAddress = "http://192.XXX.X.XX:8080";
    private OkHttpClient webSocket1;
    private WebSocket ws1;
    private OkHttpClient webSocket2;
    private WebSocket ws2;
    private final int MY_PERMISSIONS_REQUEST_CAMERA = 100;
    private final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 101;
    private final int MY_PERMISSIONS_REQUEST = 102;
    @BindView(R.id.local_gl_surface_view)
    SurfaceViewRenderer localVideoView;
    private PeerConnection localPeer, remotePeer;
    private PeerConnectionFactory peerConnectionFactory;
    private VideoRenderer remoteRenderer;
    private VideoTrack localVideoTrack;
    MediaConstraints audioConstraints;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    DataChannel dataChannel;
    boolean audio = true;
    private enum MessageType { MESSAGE, LEAVE }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        localVideoView.setMirror(true);
        rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
    }

    public void setRemoteDescription(SessionDescription sessionDescription) {

        localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemoteDesc"), sessionDescription);


    }



    public void askForPermissions() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST);
        } else if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);

        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);
        }
    }


    public void start(View view) {
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */true,  /* enableH264HighProfile */false);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();
        VideoCapturer videoGrabberAndroid = createVideoGrabber();
        MediaConstraints constraints = new MediaConstraints();

        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoGrabberAndroid);
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        AudioSource audioSource = peerConnectionFactory.createAudioSource(constraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        videoGrabberAndroid.startCapture(1000, 1000, 30);

        final VideoRenderer localRenderer = new VideoRenderer(localVideoView);
        localVideoTrack.addRenderer(localRenderer);

        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));


        createLocalPeerConnection(sdpConstraints);
        createLocalSocket();

        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");

        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);

        createLocalOffer(sdpConstraints);
        createRemotePeerConnection();
        createRemoteSocket();

        new CountDownTimer(2000, 1000) {


            public void onTick(long millisUntilFinished) {

            }

            public void onFinish() {
                createLoclOffer(sdpConstraints);
            }
        }
                .start();


    }

    public void createLocalPeerConnection(MediaConstraints sdpConstraints) {
        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder("turn:XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXudp")
                .setUsername("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
                .setPassword("XXXXXXXXXXXXXX")

                .createIceServer();
        iceServers.add(peerIceServer);

        localPeer = peerConnectionFactory.createPeerConnection(iceServers, sdpConstraints, new CustomPeerConnectionObserver("localPeerCreation") {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                try {
                    JSONObject json = new JSONObject();
                    json.put("type", "candidate");
                    json.put("label", iceCandidate.sdpMLineIndex);
                    json.put("id", iceCandidate.sdpMid);
                    json.put("candidate", iceCandidate.sdp);

                    ws1.send(json.toString());

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }
            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                super.onIceGatheringChange(iceGatheringState);

            }
        });

    }

    public void createLocalSocket() {
        Request request = new Request.Builder().url(socketAddress).build();
        AsyncHttpURLConnection listener = new AsyncHttpURLConnection("GET",this, localPeer);
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
        webSocket1 = okHttpClientBuilder.build();
        ws1 = webSocket1.newWebSocket(request, listener);
        webSocket1.dispatcher().executorService().shutdown();

    }


    public void createRemotePeerConnection() {
        final List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        PeerConnection.IceServer iceServer = PeerConnection.IceServer.builder("turn:XXXXXXXXXXXXXXXXXXXXXXXXXudp")
                .setUsername("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
                .setPassword("XXXXXXXXXXXXXXX")
                .createIceServer();
        iceServers.add(iceServer);

        MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        remotePeer = peerConnectionFactory.createPeerConnection(iceServers, sdpConstraints, new CustomPeerConnectionObserver("remotePeerCreation") {

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                try {
                    JSONObject json = new JSONObject();
                    json.put("type", "candidate");

                    ws2.send(json.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                super.onIceGatheringChange(iceGatheringState);

            }
        });
    }
    public void createLocalOffer(MediaConstraints sdpConstraints) {
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                try {

                    JSONObject json = new JSONObject();
                    json.put("sdp", sessionDescription.description);
                    json.put("type", "offer");
                    json.put("video", "video");
                    json.put("video_transform", "edges");
                    String js = json.toString();
                    ws1.send(js);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, sdpConstraints);

    }
    public void createLoclOffer(MediaConstraints sdpConstraints) {
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                try {

                    JSONObject json = new JSONObject();
                    json.put("sdp", sessionDescription.description);
                    json.put("type", "offer");
                    json.put("video", "video");
                    json.put("video_transform", "edges");
                    String js = json.toString();
                    ws1.send(js);
                    sendPostMessage(MessageType.MESSAGE, socketAddress, json.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, sdpConstraints);

    }
    public void createRemoteSocket() {
        Request request = new Request.Builder().url(socketAddress).build();
        AsyncHttpURLConnection listener = new AsyncHttpURLConnection("GET",this, remotePeer);
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();
        webSocket2 = okHttpClientBuilder.build();
        ws2 = webSocket2.newWebSocket(request, listener);
        listener.setWebSocket(ws2);
        webSocket2.dispatcher().executorService().shutdown();


    }

    public void hangup(View view) {
        ws1.send("bye");
        ws2.send("bye");
        localPeer.close();
        remotePeer.close();
        localPeer = null;
        remotePeer = null;
    }

    public VideoCapturer createVideoGrabber() {
        VideoCapturer videoCapturer;
        videoCapturer = createCameraGrabber(new Camera1Enumerator(false));
        return videoCapturer;
    }

    public VideoCapturer createCameraGrabber(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        AudioTrack audioTrack = stream.audioTracks.get(0);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                  //  remoteRenderer = new VideoRenderer(remoteVideoView);
                 //   remoteVideoView.setVisibility(View.VISIBLE);
                    videoTrack.addRenderer(remoteRenderer);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

    }


    private void sendPostMessage(
            final MessageType messageType, final String url, @Nullable final String message) {
        String logInfo = url;
        if (message != null) {
            logInfo += ". Message: " + message;
        }
        Log.d(TAG, "C->GAE: " + logInfo);
        AsyncHttpURLConnection httpConnection =
                new AsyncHttpURLConnection("POST", url, message, MainActivity.this, localPeer, new AsyncHttpURLConnection.AsyncHttpEvents() {
                    @Override
                    public void onHttpError(String errorMessage) {
                        Log.d(TAG, "C<-GAE: " + errorMessage);
                    }

                    @Override
                    public void
                    onHttpComplete(String response) {
                        if (messageType == MessageType.MESSAGE) {
                            try {
                                JSONObject roomJson = new JSONObject(response);
                                Log.d(TAG, "C<-GAE: " + roomJson);
                                String result = roomJson.getString("complete");
                                if (!result.equals("SUCCESS")) {
                                }
                            } catch (JSONException e) {
                                return;
                            }
                        }
                    }
                });
        httpConnection.send();
    }


}