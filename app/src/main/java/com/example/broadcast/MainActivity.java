package com.example.broadcast;

import android.app.Activity;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.broadcast.broadcast.BroadcastManager;
import com.example.broadcast.broadcast.MessageBroadcastTask;
import com.example.broadcast.broadcast.UiThreadCallback;
import com.example.broadcast.broadcast.Util;

import java.lang.ref.WeakReference;
import java.util.HashSet;

import io.left.rightmesh.android.AndroidMeshManager;
import io.left.rightmesh.android.AndroidMeshService;
import io.left.rightmesh.id.MeshId;
import io.left.rightmesh.mesh.MeshManager;
import io.left.rightmesh.mesh.MeshStateListener;
import io.left.rightmesh.util.Logger;
import io.left.rightmesh.util.RightMeshException;

import static io.left.rightmesh.mesh.MeshManager.DATA_RECEIVED;
import static io.left.rightmesh.mesh.MeshManager.PEER_CHANGED;
import static io.left.rightmesh.mesh.MeshManager.REMOVED;

public class MainActivity extends Activity implements MeshStateListener, UiThreadCallback {
    // TODO: this port must match the port assigned, on developer.rightmesh.io, to your key
    private static final int HELLO_PORT = 61087;

    // MeshManager instance - interface to the mesh network.
    AndroidMeshManager mm = null;

    // Set to keep track of peers connected to the mesh.
    HashSet<MeshId> users = new HashSet<>();


    // The handler for the UI thread. Used for handling messages from worker threads.
    private UiHandler mUiHandler;

    // A text view to show messages sent from work threads
    private TextView mDisplayTextView;

    // A thread pool manager
    // It is a static singleton instance by design and will survive activity lifecycle
    private BroadcastManager broadcastManager;

    /**
     * Called when app first opens, initializes {@link AndroidMeshManager} reference (which will
     * start the {@link AndroidMeshService} if it isn't already running.
     *
     * @param savedInstanceState passed from operating system
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDisplayTextView =  mDisplayTextView = (TextView)findViewById(R.id.display);

        // TODO: when testing, we suggest using rightmesh-library-dev in app:build.gradle,
        // and specifying a pattern as the third argument to this call. This will isolate
        // your devices so they won't try to connect to the network of the developer sitting
        // beside you :D
        mm = AndroidMeshManager.getInstance(
                MainActivity.this,
                MainActivity.this, "", "broad_aj");
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Initialize the handler for UI thread to handle message from worker threads
        mUiHandler = new UiHandler(Looper.getMainLooper(), mDisplayTextView);



        // get the thread pool manager instance
        broadcastManager = BroadcastManager.getInstance();
        // CustomThreadPoolManager stores activity as a weak reference. No need to unregister.
        broadcastManager.setUiThreadCallback(this);
    }


    /**
     * Called when activity is on screen.
     */
    @Override
    protected void onResume() {
        try {
            super.onResume();
            mm.resume();
        } catch (RightMeshException.RightMeshServiceDisconnectedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called when the app is being closed (not just navigated away from). Shuts down
     * the {@link AndroidMeshManager} instance.
     */
    @Override
    protected void onDestroy() {
        try {
            super.onDestroy();
            mm.stop();
        } catch (RightMeshException.RightMeshServiceDisconnectedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called by the {@link AndroidMeshService} when the mesh state changes.
     * Initializes mesh connection
     * on first call.
     *
     * @param uuid our own user id on first detecting
     * @param state state which indicates SUCCESS or an error code
     */
    @Override
    public void meshStateChanged(MeshId uuid, int state) {
        if (state == MeshStateListener.SUCCESS) {
            try {
                // Binds this app to MESH_PORT.
                // This app will now receive all events generated on that port.
                mm.bind(HELLO_PORT);

                // Subscribes handlers to receive events from the mesh.
                mm.on(DATA_RECEIVED, this::handleDataReceived);
                mm.on(PEER_CHANGED, this::handlePeerChanged);

                // Enable buttons now that mesh is connected.
                Button btnConfigure = findViewById(R.id.btnConfigure);
                Button btnSend = findViewById(R.id.btnHello);
                btnConfigure.setEnabled(true);
                btnSend.setEnabled(true);
            } catch (RightMeshException e) {
                String status = "Error initializing the library" + e.toString();
                Toast.makeText(getApplicationContext(), status, Toast.LENGTH_SHORT).show();
                TextView txtStatus = findViewById(R.id.txtStatus);
                txtStatus.setText(status);
                return;
            }
        }

        // Update display on successful calls (i.e. not FAILURE or DISABLED).
        if (state == MeshStateListener.SUCCESS || state == MeshStateListener.RESUME) {
            updateStatus();
        }
    }

    /**
     * Update the {@link TextView} with a list of all peers.
     */
    private void updateStatus() {
        StringBuilder status = new StringBuilder("uuid: " + mm.getUuid().toString() + "\npeers:\n");
        for (MeshId user : users) {
            status.append(user.toString()).append("\n");
        }
        TextView txtStatus = findViewById(R.id.txtStatus);
        txtStatus.setText(status.toString());
    }

    /**
     * Handles incoming data events from the mesh - toasts the contents of the data.
     *
     * @param e event object from mesh
     */
    private void handleDataReceived(MeshManager.RightMeshEvent e) {
        final MeshManager.DataReceivedEvent event = (MeshManager.DataReceivedEvent) e;

        runOnUiThread(() -> {
            // Toast data contents.
            String message = new String(event.data);
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();

            // Play a notification.
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(MainActivity.this, notification);
            r.play();
        });
    }

    /**
     * Handles peer update events from the mesh - maintains a list of peers and updates the display.
     *
     * @param e event object from mesh
     */
    private void handlePeerChanged(MeshManager.RightMeshEvent e) {
        // Update peer list.
        MeshManager.PeerChangedEvent event = (MeshManager.PeerChangedEvent) e;
        if (event.state != REMOVED && !users.contains(event.peerUuid)) {
            users.add(event.peerUuid);
        } else if (event.state == REMOVED) {
            users.remove(event.peerUuid);
        }

        // Update display.
        runOnUiThread(this::updateStatus);
    }

    /**
     * Sends "hello" to all known peers.
     *
     * @param v calling view
     * @throws RightMeshException Throws exception when there's an error in the library
     */
    public void sendHello(View v) throws RightMeshException {
        for (MeshId receiver : users) {

            String msg = "Hello to: " + receiver + " from" + mm.getUuid();
            Logger.log(this.getClass().getCanonicalName(), "MSG: " + msg);
            /*byte[] testData = msg.getBytes();
            mm.sendDataReliable(receiver, HELLO_PORT, testData);*/


            MessageBroadcastTask messageBroadcastTask = new MessageBroadcastTask();
            messageBroadcastTask.setMeshId(receiver);
            messageBroadcastTask.setMeshMessage(msg);
            messageBroadcastTask.setMeshManager(mm);
            messageBroadcastTask.setCustomThreadPoolManager(broadcastManager);

            broadcastManager.addCallable(messageBroadcastTask);
        }
    }

    /**
     * Open mesh settings screen.
     *
     * @param v calling view
     */
    public void configure(View v) {
        try {
            mm.showSettingsActivity();
        } catch (RightMeshException ex) {
            Logger.log(this.getClass().getCanonicalName(), "Service not connected");
        }
    }

    @Override
    public void publishToUiThread(Message message) {
        // add the message from worker thread to UI thread's message queue
        if(mUiHandler != null){
            mUiHandler.sendMessage(message);
        }
    }


    // UI handler class, declared as static so it doesn't have implicit
    // reference to activity context. This helps to avoid memory leak.
    private static class UiHandler extends Handler {
        private WeakReference<TextView> mWeakRefDisplay;

        public UiHandler(Looper looper, TextView display) {
            super(looper);
            this.mWeakRefDisplay = new WeakReference<TextView>(display);
        }

        // This method will run on UI thread
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                // Our communication protocol for passing a string to the UI thread
                case Util.MESSAGE_ID:
                    Bundle bundle = msg.getData();
                    String messsageText = bundle.getString(Util.MESSAGE_BODY, Util.EMPTY_MESSAGE);


                    if(mWeakRefDisplay != null && mWeakRefDisplay.get() != null)
                        mWeakRefDisplay.get().append(Util.getReadableTime() + " " + messsageText + "\n");
                    break;
                default:
                    break;
            }
        }
    }
}
