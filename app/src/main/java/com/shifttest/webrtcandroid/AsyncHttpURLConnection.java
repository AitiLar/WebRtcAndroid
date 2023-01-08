/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.shifttest.webrtcandroid;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Scanner;

import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Asynchronous http requests implementation.
 */
public class AsyncHttpURLConnection extends WebSocketListener {
  private static final int HTTP_TIMEOUT_MS = 8000000;
  private String http_origin;
  private String method;
  private String url;
  private String message;
  private AsyncHttpEvents events;
  private String contentType;
  private MainActivity webrtcActivity;
  private PeerConnection peerConnection;
  private WebSocket webSocket;
  public AsyncHttpURLConnection(String method, String url, String message, MainActivity webrtcActivity, PeerConnection peerConnection, AsyncHttpEvents events) {
    this.method = method;
    this.url = url;
    this.message = message;
    this.events = events;
    this.webrtcActivity = webrtcActivity;
    this.peerConnection = peerConnection;
    this.http_origin = http_origin;
  }
  public AsyncHttpURLConnection(String method, MainActivity webrtcActivity, PeerConnection peerConnection) {
    this.method = method;
    this.webrtcActivity = webrtcActivity;
    this.peerConnection = peerConnection;
  }

  public void setWebSocket(WebSocket webSocket) {
    this.webSocket = webSocket;
  }
  /**
   * Http requests callbacks.
   */
  public interface AsyncHttpEvents {
    void onHttpError(String errorMessage);
    void onHttpComplete(String response);
  }



  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public void send() {
    new Thread(this ::sendHttpMessage).start();
  }

  private void sendHttpMessage() {
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
      byte[] postData = new byte[0];
      if (message != null) {
        postData = message.getBytes("UTF-8");
      }
      connection.setRequestMethod(method);
      connection.setUseCaches(false);
      connection.setDoInput(true);
      connection.setConnectTimeout(HTTP_TIMEOUT_MS);
      connection.setReadTimeout(HTTP_TIMEOUT_MS);
      // TODO(glaznev) - query request origin from pref_room_server_url_key preferences.
      connection.addRequestProperty("origin", http_origin);
      boolean doOutput = false;
      if (method.equals("POST")) {
        doOutput = true;
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(postData.length);
      }
      if (method.equals("GET")) {
        doOutput = true;
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(postData.length);
      }
      if (contentType == null) {
        connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
      } else {
        connection.setRequestProperty("Content-Type", contentType);
      }

      // Send POST request.
      if (doOutput && postData.length > 0) {
        OutputStream outStream = connection.getOutputStream();
        outStream.write(postData);
        outStream.close();
      }

      // Get response.
      int responseCode = connection.getResponseCode();
      if (responseCode != 200) {
        events.onHttpError(connection.getHeaderField(null));
        connection.disconnect();
        return;
      }
      InputStream responseStream = connection.getInputStream();
      String response = drainStream(responseStream);
      responseStream.close();
      connection.disconnect();
      events.onHttpComplete(response);
      JSONObject json = new JSONObject(response);

        if (json.getString("type").equals("offer")) {
          json.put("type", "OFFER");
          saveOfferAndAnswer(json);
        } else if (json.getString("type").equals("answer")) {
          json.put("type", "ANSWER");
          saveAnswer(json);
        }

    } catch (SocketTimeoutException e) {
      events.onHttpError("HTTP " + method + " to " + url + " timeout");
    } catch (IOException e) {
      events.onHttpError("HTTP " + method + " to " + url + " error: " + e.getMessage());
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }
  public void saveIceCandidate(JSONObject json) throws JSONException {
    IceCandidate iceCandidate = new IceCandidate(json.getString("id"),Integer.parseInt(json.getString("label")),json.getString("candidate"));
    peerConnection.addIceCandidate(iceCandidate);
  }

  public void saveAnswer(JSONObject json)  {
    try {
      SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.ANSWER, json.getString("sdp"));
      webrtcActivity.setRemoteDescription(sessionDescription);
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  public void saveOfferAndAnswer(JSONObject json) throws JSONException {
    SessionDescription sessionDescription = new SessionDescription(SessionDescription.Type.OFFER, json.getString("sdp"));
    peerConnection.setRemoteDescription(new CustomSdpObserver("remoteSetRemoteDesc"), sessionDescription);

    peerConnection.createAnswer(new CustomSdpObserver("remoteCreateOffer") {
      @Override
      public void onCreateSuccess(SessionDescription sessionDescription) {
        super.onCreateSuccess(sessionDescription);
        peerConnection.setLocalDescription(new CustomSdpObserver("remoteSetLocalDesc"), sessionDescription);
        try {
          JSONObject json = new JSONObject();
          json.put("type", sessionDescription.type);
          json.put("sdp", sessionDescription.description);
          webSocket.send(json.toString());
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }
    }, new MediaConstraints());
  }
  // Return the contents of an InputStream as a String.
  private static String drainStream(InputStream in) {
    Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
