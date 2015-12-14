package edu.upenn.genesisapp;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Timer;
import java.util.TimerTask;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;

    private View mContentView;
    private View mControlsView;
    private View mStartView;

    private boolean mVisible;

    private TextView textMsg1;
    private TextView textMsg2;

    private WebSocketClient mWebSocketClient;
    private boolean mWebSocketStatus = false;

    private ImageView imageView;

    private double[] cmd_vel_linear = {0.0, 0.0, 0.0};
    private double[] cmd_vel_angular = {0.0, 0.0, 0.0};
    private boolean enableCmdVelMsg = false;

    private boolean hasImage = false;
    class ImageBuffer {
        byte[] data;
        int width;
        int height;

        ImageBuffer(){
            width = 640;
            height = 480;
            data = new byte[640*480*4];
        }
    }

    private ImageBuffer new_one = new ImageBuffer();
    private ImageBuffer old_one = new ImageBuffer();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mVisible = false;
//        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);
        mStartView = findViewById(R.id.start_content);

        textMsg2 = (TextView) findViewById(R.id.text_msg_2);
        textMsg1 = (TextView) findViewById(R.id.text_msg_1);

        hide();
        imageView = (ImageView) findViewById(R.id.imageView1);

        connectWebSocket();

        new Thread(new Runnable() {
            public void run() {
                Timer timer = new Timer();
                timer.schedule(new sendCmdVelMsgTask(), 0,100);
                }

        }).start();

        new Thread(new Runnable() {
            public void run() {
                handleImageParsing();
            }
        }).start();



        // Set up the user interaction to manually show or hide the system UI.
        /*mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });*/



        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
//        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
    }


    class sendCmdVelMsgTask extends TimerTask {

        @Override
        public void run() {
            if (mWebSocketStatus && enableCmdVelMsg) {
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("op", "publish");
                    jsonObject.put("topic", "/cmd_vel_mux/input/teleop");
                    JSONObject jsonObjectMsg = new JSONObject();
                    JSONObject jsonObjectMsgLinear = new JSONObject();
                    jsonObjectMsgLinear.put("x", cmd_vel_linear[0]);
                    jsonObjectMsgLinear.put("y", cmd_vel_linear[1]);
                    jsonObjectMsgLinear.put("z", cmd_vel_linear[2]);
                    JSONObject jsonObjectMsgAngular = new JSONObject();
                    jsonObjectMsgAngular.put("x", cmd_vel_angular[0]);
                    jsonObjectMsgAngular.put("y", cmd_vel_angular[1]);
                    jsonObjectMsgAngular.put("z", cmd_vel_angular[2]);
                    jsonObjectMsg.put("linear", jsonObjectMsgLinear);
                    jsonObjectMsg.put("angular", jsonObjectMsgAngular);
                    jsonObject.put("msg", jsonObjectMsg);
                    Log.i("json", jsonObject.toString());
                    mWebSocketClient.send(jsonObject.toString());

                } catch (JSONException ex) {
                    ex.printStackTrace();
                }

            }
        }
    };

    private void updateCmdVel(int direction){
        switch (direction) {
            case 0:
                cmd_vel_linear[0] = 0.0;
                cmd_vel_angular[2] = 0.0;
                break;
            case 1:
                if(cmd_vel_linear[0] <= 0.2){
                    cmd_vel_linear[0] += 0.02;
                }
                cmd_vel_angular[2] = 0.0;
                break;
            case 2:
                if(cmd_vel_linear[0] >= -0.2){
                    cmd_vel_linear[0] -= 0.02;
                }
                cmd_vel_angular[2] = 0.0;
                break;
            case 3:
                if(cmd_vel_angular[2] <= 1.0){
                    cmd_vel_angular[2] += 0.1;
                }
                cmd_vel_linear[0] = 0.0;
                break;
            case 4:
                if(cmd_vel_angular[2] >= -1.0){
                    cmd_vel_angular[2] -= 0.1;
                }
                cmd_vel_linear[0] = 0.0;
                break;
            default:
                cmd_vel_linear[0] = 0.0;
                cmd_vel_angular[2] = 0.0;
                break;
        }
    }

    /*@Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }*/

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
//        ActionBar actionBar = getSupportActionBar();
//        if (actionBar != null) {
//            actionBar.hide();
//        }
//        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mStartView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };


    private final Runnable mHidePart3Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };

    private final Handler mHideHandler = new Handler();
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    public void sendConnect(View view){
     /*   try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op","subscribe");
            jsonObject.put("topic","/Lifecam/image_raw");
            jsonObject.put("throttle_rate", 100);
            jsonObject.put("queue_length", 1);
            textMsg1.setText(jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }

        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op","advertise");
            jsonObject.put("topic","/cmd_vel_mux/input/teleop");
            jsonObject.put("type", "/geometry_msgs/Twist");
            textMsg1.setText(jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());
            enableCmdVelMsg = true;
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }
*/
        mHideHandler.postDelayed(mHidePart3Runnable, UI_ANIMATION_DELAY);

    }

    public void sendDisconnect(View view){
        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op","unsubscribe");
            jsonObject.put("topic","/Lifecam/image_raw");
//            jsonObject.put("topic","/turtle1/cmd_vel");
            textMsg1.setText(jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }
        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op","unadvertise");
            jsonObject.put("topic","/cmd_vel_mux/input/teleop");
            textMsg1.setText(jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());
            enableCmdVelMsg = false;
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }

    }


    public void sendMoveUp(View view){
        updateCmdVel(1);

    }
    public void sendMoveDown(View view){
        updateCmdVel(2);
    }
    public void sendMoveLeft(View view){
        updateCmdVel(3);
    }
    public void sendMoveRight(View view){
        updateCmdVel(4);
    }

    public void sendStop(View view){
        updateCmdVel(0);
    }

    private void handleImageParsing(){
        while(true){
            if(hasImage){
                old_one = new_one;
                hasImage = false;
                int[] argb = new int[(old_one.data.length)];

                int index = 0;

                for (int i = 0; i < old_one.data.length; i += 4) {
                    argb[index]  = ((int) old_one.data[i]) << 24;
                    argb[index] |= ((int) old_one.data[i + 1]) << 16;
                    argb[index] |= ((int) old_one.data[i + 2]) << 8;
                    argb[index] |= ((int) old_one.data[i + 3]);
                    index++;
                }

                //                            IntBuffer intBuf = ByteBuffer.wrap(rgb)
                //                                            .order(ByteOrder.BIG_ENDIAN)
                //                                            .asIntBuffer();
                //                            int[] argb = new int[intBuf.remaining()];
                //                            intBuf.get(argb);

                final Bitmap image = Bitmap.createBitmap(argb, old_one.width, old_one.height, Bitmap.Config.ARGB_8888);
                imageView.post(new Runnable() {
                    public void run() {
                        imageView.setImageBitmap(image);
                    }
                });
            }
        }
    }


    private void connectWebSocket() {
        URI uri;
        try {
            uri = new URI("ws://158.130.111.154:9090");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

//        textMsg2.setText("URI");

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i("Websocket", "Opened");

                mWebSocketStatus = true;
            }

            @Override
            public void onMessage(String s) {
                final String msg = s;
//                new Thread(new Runnable() {
//                    public void run() {

                        try {
                            JSONObject jsonObject = new JSONObject(msg);
                            new_one.height = jsonObject.getJSONObject("msg").getInt("height");
                            new_one.width = jsonObject.getJSONObject("msg").getInt("width");
                            new_one.data = jsonObject.getJSONObject("msg").getString("data").getBytes();
                            hasImage = true;

                            Log.i("[INFO height]", Integer.toString(new_one.height));
                            Log.i("[INFO width]", Integer.toString(new_one.width));

                        } catch (JSONException ex) {
                            ex.printStackTrace();
                        }

//                    }
//                }).start();

            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i("Websocket", "Closed " + s);
                mWebSocketStatus = false;
            }

            @Override
            public void onError(Exception e) {
                Log.i("Websocket", "Error " + e.getMessage());
                mWebSocketStatus = false;
            }
        };
        mWebSocketClient.connect();
    }

}
