package edu.upenn.genesisapp;

/*
 * Developer: Pedro Paulo Ventura Tecchio
 * E-mail: tecchio at seas dot upenn dot edu
 * Colaborators: Guan Sun and Christopher Clingerman
 * Institution: University of Pennsylvania
 */

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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

/*
 * The following code was created towards the integration of a Project Tango device with the ROS
 * - Robotic Operation System environment. The main goal was to enable communications between then.
 *
 * We achieved this by exploring websockets implementations in Android,
 * https://github.com/TooTallNate/Java-WebSocket, and in ROS, http://wiki.ros.org/rosbridge_suite.
 *
 * This Android Application was intended to be always in full screen mode and that is why we have
 * this class name. Later on, due to time constraints we dropped the full screen mode and kept a
 * standard one, but we did not change the name of this class.
 *
 * This code should be viewed as a first attempt towards our goal and has a LOT of space for
 * improvement, for example: better screen management, use of a media codec to enable video
 * streaming decoding, message acks, memory management and completion of the joystick function.
 *
 * App actions:
 * - Creates the websocket connection with the ROSBridge acting as a server in the robot
 * - Switches screens according to the state of the execution
 * - Sends and receive message using topics
 * - 00Able to show received images without color decompression and with delays.
 */


public class FullscreenActivity extends AppCompatActivity {

    // All the different screens (views) we have.
    private View mStartView;
    private View mChooseTaskView;
    private View mChooseLocationView;
    private View mChooseObjectView;
    private View mExecutingView;
    private View mGoalAchievedView;
    private View mJoystickView;
    private View mFollowFacesView;
    // Specific views we need to access at some point in the code.
    private ImageView imageView;
    private TextView goalTextView;
    // Websocket related variables.
    private WebSocketClient mWebSocketClient;
    private boolean mWebSocketStatus = false;
    // Definitions of topic names and types
    private final String ANDROID_CMD_TOPIC = "/android_ui";
    private final String ANDROID_CMD_TYPE = "std_msgs/String";
    private final String ANDROID_FB_TOPIC = "/android_fb";
    private final String ANDROID_FB_TYPE = "std_msgs/String";
    private final String CAMERA_TOPIC = "/Lifecam/image_raw";
    private final String TELEOP_TOPIC = "/cmd_vel_mux/input/teleop";
    private final String TELEOP_TYPE = "/geometry_msgs/Twist";
    // Variables needed to send CMD_VEL messages to move the robot
    private double[] cmd_vel_linear = {0.0, 0.0, 0.0};
    private double[] cmd_vel_angular = {0.0, 0.0, 0.0};
    private boolean enableCmdVelMsg = false;
    // Variables related to image parsing
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


    /*
     * We use to buffers to store received image data, new_one always keep the last data received
     * and is usually overwritten. old_one receives a copy of new_one only when we process an image
     * and is only used within the thread function that parses images.
     */
    private ImageBuffer new_one = new ImageBuffer();
    private ImageBuffer old_one = new ImageBuffer();
    // Variables used to specify objectives to the robot
    private String location = "GRASP Lab";
    private String object = "Object1";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mStartView          = findViewById(R.id.start_content);
        mChooseTaskView     = findViewById(R.id.choose_task_content);
        mChooseLocationView = findViewById(R.id.choose_location_content);
        mChooseObjectView   = findViewById(R.id.choose_object_content);
        mExecutingView      = findViewById(R.id.executing_content);
        mGoalAchievedView   = findViewById(R.id.goal_achieved_content);
        mJoystickView       = findViewById(R.id.joystick_content);
        mFollowFacesView    = findViewById(R.id.follow_faces_content);

        imageView = (ImageView) findViewById(R.id.joystick_content_imageView1);
        goalTextView = (TextView) findViewById(R.id.goal_achieved_content_text_view);

        // This creates the websocket connection. Should be interesting to change later to an app
        // pre init step.
        connectWebSocket();

        // Switch to connect button view
        switchViews(0);

        /* Creates a thread responsible for continually sending CMD_VEL messages to move the robot
         * around in the TELEOP mode. If the correct topic and the correct controller are enable in
         * the robot, then it should work nicely.
         */
        new Thread(new Runnable() {
            public void run() {
                Timer timer = new Timer();
                // Sends a message every 100 ms, may be delayed by other tasks.
                timer.schedule(new sendCmdVelMsgTask(), 0,100);
                }

        }).start();

        // Creates a thread responsible for parsing the images received.
        new Thread(new Runnable() {
            public void run() {
                handleImageParsing();
            }
        }).start();

    }

    /*
     * Function to switch views. Most basic way to do so, by setting the visibility of the views,
     * better ways exists and should be explored latter on.
     */
    private void switchViews(int view){
        switch (view){
            case 0:
                mChooseTaskView.setVisibility(View.GONE);
                mChooseLocationView.setVisibility(View.GONE);
                mChooseObjectView.setVisibility(View.GONE);
                mExecutingView.setVisibility(View.GONE);
                mGoalAchievedView.setVisibility(View.GONE);
                mJoystickView.setVisibility(View.GONE);
                mFollowFacesView.setVisibility(View.GONE);
                mStartView.setVisibility(View.VISIBLE);
                break;
            case 1:
                mStartView.setVisibility(View.GONE);
                mChooseLocationView.setVisibility(View.GONE);
                mChooseObjectView.setVisibility(View.GONE);
                mExecutingView.setVisibility(View.GONE);
                mGoalAchievedView.setVisibility(View.GONE);
                mJoystickView.setVisibility(View.GONE);
                mFollowFacesView.setVisibility(View.GONE);
                mChooseTaskView.setVisibility(View.VISIBLE);
                break;
            case 2:
                mStartView.setVisibility(View.GONE);
                mChooseTaskView.setVisibility(View.GONE);
                mChooseObjectView.setVisibility(View.GONE);
                mExecutingView.setVisibility(View.GONE);
                mGoalAchievedView.setVisibility(View.GONE);
                mJoystickView.setVisibility(View.GONE);
                mFollowFacesView.setVisibility(View.GONE);
                mChooseLocationView.setVisibility(View.VISIBLE);
                break;
            case 3:
                mStartView.setVisibility(View.GONE);
                mChooseTaskView.setVisibility(View.GONE);
                mChooseLocationView.setVisibility(View.GONE);
                mExecutingView.setVisibility(View.GONE);
                mGoalAchievedView.setVisibility(View.GONE);
                mJoystickView.setVisibility(View.GONE);
                mFollowFacesView.setVisibility(View.GONE);
                mChooseObjectView.setVisibility(View.VISIBLE);
                break;
            case 4:
                mStartView.setVisibility(View.GONE);
                mChooseTaskView.setVisibility(View.GONE);
                mChooseLocationView.setVisibility(View.GONE);
                mChooseObjectView.setVisibility(View.GONE);
                mGoalAchievedView.setVisibility(View.GONE);
                mJoystickView.setVisibility(View.GONE);
                mFollowFacesView.setVisibility(View.GONE);
                mExecutingView.setVisibility(View.VISIBLE);
                break;
            case 5:
                mStartView.setVisibility(View.GONE);
                mChooseTaskView.setVisibility(View.GONE);
                mChooseLocationView.setVisibility(View.GONE);
                mChooseObjectView.setVisibility(View.GONE);
                mExecutingView.setVisibility(View.GONE);
                mJoystickView.setVisibility(View.GONE);
                mFollowFacesView.setVisibility(View.GONE);
                mGoalAchievedView.setVisibility(View.VISIBLE);
                break;
            case 6:
                mStartView.setVisibility(View.GONE);
                mChooseTaskView.setVisibility(View.GONE);
                mChooseLocationView.setVisibility(View.GONE);
                mChooseObjectView.setVisibility(View.GONE);
                mExecutingView.setVisibility(View.GONE);
                mGoalAchievedView.setVisibility(View.GONE);
                mFollowFacesView.setVisibility(View.GONE);
                mJoystickView.setVisibility(View.VISIBLE);
                break;
            case 7:
                mStartView.setVisibility(View.GONE);
                mChooseTaskView.setVisibility(View.GONE);
                mChooseLocationView.setVisibility(View.GONE);
                mChooseObjectView.setVisibility(View.GONE);
                mExecutingView.setVisibility(View.GONE);
                mGoalAchievedView.setVisibility(View.GONE);
                mJoystickView.setVisibility(View.GONE);
                mFollowFacesView.setVisibility(View.VISIBLE);
                break;

        }
    }

    /*
     * Every timer tick we test websocket connection and CMD_VEL msgs enabling flags, if booth are
     * enabled we construct a CMD_VEL message using JSON functions and stored data in global
     * variables.
     */
    class sendCmdVelMsgTask extends TimerTask {

        @Override
        public void run() {
            if (mWebSocketStatus && enableCmdVelMsg) {
                try {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("op", "publish");
                    jsonObject.put("topic", TELEOP_TOPIC);
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
                    Log.i("[sendCmdVelMsg]", jsonObject.toString());
                    mWebSocketClient.send(jsonObject.toString());

                } catch (JSONException ex) {
                    ex.printStackTrace();
                }

            }
        }
    };

    /*
     * This function update the stored values of the global variables used to send CMD_VEL messages.
     * We hardcoded here maximum values for the velocities, but if desired one can change these
     * values. We do not check the actual values used by the robot at any point. There is no closed
     * loop control.
     */
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

    /*
     * The send*() functions are called by the diverse Buttons' onCLick methods.
     * They either set some global variable to a desired value or are used to construct messages
     * sent to defined topics in order to start different processes on the robot.
     */

    public void sendConnect(View view){
        // Advertise the topic to send cmds to the robot
        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op","advertise");
            jsonObject.put("topic",ANDROID_CMD_TOPIC);
            jsonObject.put("type", ANDROID_CMD_TYPE);
            Log.i("[sendConnect]", jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }
        // Subscribe to the topic to receive feedback from the robot.
        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op","subscribe");
            jsonObject.put("topic",ANDROID_FB_TOPIC);
            // It is necessary to send the topic type in order to ensure that it will be created in
            // case it does not exist so far.
            jsonObject.put("type", ANDROID_FB_TYPE);
            jsonObject.put("throttle_rate", 0);
            jsonObject.put("queue_length", 10);
            Log.i("[sendConnect]", jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }

        switchViews(1);
    }

    public void sendGotoCmd(View view){
        switchViews(2);
    }

    public void sendFollowFacesCmd(View view){
        // Send the command to start the follow face procedure
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op", "publish");
            jsonObject.put("topic", ANDROID_CMD_TOPIC);
            JSONObject jsonObjectData = new JSONObject();
            jsonObjectData.put("data", "cmd_follow");
            jsonObject.put("msg", jsonObjectData);
            Log.i("[sendGotoMsg]", jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());

        } catch (JSONException ex) {
            ex.printStackTrace();
        }

        switchViews(7);
    }

    public void sendJoystickCmd(View view){
        // Advertise the topic to send cmd_vel commands
        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op","advertise");
            jsonObject.put("topic", TELEOP_TOPIC);
            jsonObject.put("type", TELEOP_TYPE);
            Log.i("[sendJoystickCmd]", jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());
            enableCmdVelMsg = true;
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }
        // Subscribe to the raw streaming camera topic
        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op","subscribe");
            jsonObject.put("topic", CAMERA_TOPIC);
            jsonObject.put("throttle_rate", 0);
            jsonObject.put("queue_length", 1);
            Log.i("[sendJoystickCmd]", jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }
        // Send the command to start the teleop procedure
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op", "publish");
            jsonObject.put("topic", ANDROID_CMD_TOPIC);
            JSONObject jsonObjectData = new JSONObject();
            jsonObjectData.put("data", "cmd_teleop");
            jsonObject.put("msg", jsonObjectData);
            Log.i("[sendGotoMsg]", jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());

        } catch (JSONException ex) {
            ex.printStackTrace();
        }

        switchViews(6);
    }

    // Cmd go to related buttons
    public void sendLocation1(View view){
        location = "grasp_lab";
        switchViews(3);
    }

    public void sendLocation2(View view){
        location = "charitys_office";
        switchViews(3);
    }

    public void sendLocation3(View view){
        location = "jeans_office";
        switchViews(3);
    }

    public void sendLocation4(View view){
        location = "vending_machine";
        switchViews(3);
    }

    public void sendObject1(View view){
        object = "keyboard";
        sendGotoMsg();
        switchViews(4);
    }

    public void sendObject2(View view){
        object = "ball";
        sendGotoMsg();
        switchViews(4);
    }

    public void sendObject3(View view){
        object = "magazine";
        sendGotoMsg();
        switchViews(4);
    }
/*
    public void sendObject4(View view){
        object = "Object4";
        sendGotoMsg();
        switchViews(4);
    }*/

    public void sendGotoMsg(){
        // Send command to go to an specific location and find an specific object
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op", "publish");
            jsonObject.put("topic", ANDROID_CMD_TOPIC);
            JSONObject jsonObjectData = new JSONObject();
            jsonObjectData.put("data", "cmd_goto " + location + " " + object);
            jsonObject.put("msg", jsonObjectData);
            Log.i("[sendGotoMsg]", jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());

        } catch (JSONException ex) {
            ex.printStackTrace();
        }
    }

    /*public void sendFinishLocation(View view){
        switchViews(1);
    }

    public void sendFinishObject(View view){
        switchViews(1);
    }
*/
  /*  public void sendFinishExecuting(View view){
        switchViews(5);
    }
*/
    // Send a command to finish executing some procedures and return to an idle state
    public void sendFinish(View view){
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op", "publish");
            jsonObject.put("topic", ANDROID_CMD_TOPIC);
            JSONObject jsonObjectData = new JSONObject();
            jsonObjectData.put("data", "cmd_idle");
            jsonObject.put("msg", jsonObjectData);
            Log.i("[sendGotoMsg]", jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());

        } catch (JSONException ex) {
            ex.printStackTrace();
        }
        switchViews(1);
    }

    // Cmd teleop related buttons
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

    public void sendJoystickFinish(View view){
        // Unsubscribe from the raw image topic
        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op","unsubscribe");
            jsonObject.put("topic", CAMERA_TOPIC);
            Log.i("[sendJoystickFinish]", jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }
        // Stop advertising the topic to send cmd_vel msgs
        try{
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("op","unadvertise");
            jsonObject.put("topic", TELEOP_TOPIC);
            Log.i("[sendJoystickFinish]", jsonObject.toString());
            mWebSocketClient.send(jsonObject.toString());
            enableCmdVelMsg = false;
        }
        catch (JSONException ex){
            ex.printStackTrace();
        }

        switchViews(1);
    }

    /*
     * This functions is our first attempt to parse the raw image streaming feed received.
     * The idea was to try processing all received frames in a bitmap and show it on the screen as
     * soon as the image processing was done. In order to do so, we execute this function in an
     * independent thread and just call the UIthread to draw the bitmap in the end.
     *
     * This function has two major problems:
     * - The image colors are wrong. We believe that there is YUV 4:2:2 or 4:2:0 compression that we
     * do not decode.
     * - There is a significant amount of delay in the image shown.
     *
     * In order to solve such problems we believe that one should encode the feed with h.264 or
     * vp8 codec and later use Android's media decoder.
     */
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
                // Call for UIthread
                imageView.post(new Runnable() {
                    public void run() {
                        imageView.setImageBitmap(image);
                    }
                });
            }
        }
    }

    /*
     * The basic format of the following function was obtained from an example of its library and
     * modified to our needs. We are able to create and establish a websocket connection without
     * security protocols, send and receive messages. Future work here should include:
     * - use of wss protocol instead of ws.
     * - use of the authentication frame available in rosbridge.
     * - verification of exceptions and errors that may occur.
     */
    private void connectWebSocket() {
        URI uri;
        try {
            uri = new URI("ws://158.130.111.154:9090");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

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
                // We parse the topic information of each message in order to call the desired
                // procedures.
                        Log.i("[onMessage]", msg);
                        try {
                            JSONObject jsonObject = new JSONObject(msg);
                            String topic = jsonObject.getString("topic");
                            if(topic.equals(CAMERA_TOPIC)) {
                                new_one.height = jsonObject.getJSONObject("msg").getInt("height");
                                new_one.width = jsonObject.getJSONObject("msg").getInt("width");
                                new_one.data = jsonObject.getJSONObject("msg").getString("data").getBytes();
                                hasImage = true;

                                Log.i("[INFO height]", Integer.toString(new_one.height));
                                Log.i("[INFO width]", Integer.toString(new_one.width));
                            }
                            else if(topic.equals(ANDROID_FB_TOPIC)){
                                String data = jsonObject.getJSONObject("msg").getString("data");
                                String text = "Did not receive correct msg!";
                                if(data.contains("location: success"))
                                     text = " I was able to arrive at desired location!\n";
                                else
                                    text = " I was not able to arrive at desired location!\n";

                                if(data.contains("object: success"))
                                    text = text + " I found the object!";
                                else
                                    text = text + " I could not find the object!";

                                final String text2 = text;
                                goalTextView.post(new Runnable() {
                                    public void run() {
                                        goalTextView.setText(text2);
                                    }
                                });
                                if(data.contains("location:") && data.contains("object:"))
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            switchViews(5);
                                        }
                                    });

                            }

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
