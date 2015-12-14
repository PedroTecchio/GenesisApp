package edu.upenn.genesisapp;

import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by tecchio on 12/3/15.
 */
public class ROS {

    private static final String DEFAULT_HOSTNAME = "192.168.0.12";
    private static final String DEFAULT_PORT = "9090";
    private static final String DEFAULT_PROTOCOL = "ws";

//    uri = new URI("ws://158.130.111.154:9090");
//            uri = new URI("ws://158.130.107.233:9090");
//            uri = new URI("ws://192.168.0.12:9090");



    private final String hostname;
    private final String port;
    private final String protocol;


    private final HashMap<String,ConcurrentLinkedQueue<JSONObject>> mapTopicMsgs;

    private WebSocketClient mWebSocketClient;

    public ROS(){
        this(ROS.DEFAULT_HOSTNAME);
    }

    public ROS(String hostname){
        this(hostname, ROS.DEFAULT_PORT);
    }

    public ROS(String hostname, String port){
        this(hostname, port, ROS.DEFAULT_PROTOCOL);
    }

    public ROS(String hostname, String port, String protocol){
        this.hostname = hostname;
        this.port = port;
        this.protocol = protocol;
        this.mapTopicMsgs = new HashMap<String,ConcurrentLinkedQueue<JSONObject>>();
        this.connect();
    }

    private String getURI(){
        return this.protocol + "://" + this.hostname + ":" + this.port;
    }

    private boolean connect() {
        URI uri;
        try {
           uri = new URI(this.getURI());
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
            return false;
        }


        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i("Websocket", "Opened");
            }

            @Override
            public void onMessage(String s) {
                final String msg = s;
                new Runnable() {
                    @Override
                    public void run() {
                        handleMsg(msg);
                    }
                };
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i("Websocket", "Closed " + s);
            }

            @Override
            public void onError(Exception e) {
                Log.i("Websocket", "Error " + e.getMessage());
            }
        };
        mWebSocketClient.connect();
        return true;
    }

    public void send(JSONObject jsonObject){
        mWebSocketClient.send(jsonObject.toString());
    }

    private void handleMsg(String msg){
        try{
            JSONObject jsonObject = new JSONObject(msg);
            String op = jsonObject.getString("op");
            if(op.equals("publish")){
                ConcurrentLinkedQueue<JSONObject> q = mapTopicMsgs.get(jsonObject.getString("topic"));
                if(q == null){
                    q = new ConcurrentLinkedQueue<JSONObject>();

                }
            }
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }
    }

    public JSONObject getTopicMsg(String topic){
        if(mapTopicMsgs.containsKey(topic)){
            return mapTopicMsgs.get(topic).remove();

        }
        else
        {
            JSONObject jsonObject = new JSONObject();
            return jsonObject;
        }


    }
}
