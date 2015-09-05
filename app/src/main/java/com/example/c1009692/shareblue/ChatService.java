package com.example.c1009692.shareblue;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.*;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Matthew on 07/08/15.
 */
public class ChatService extends Service {

    static Handler myHandler;
    public final BluetoothDiscoveryReceiver discoveryReceiver = new BluetoothDiscoveryReceiver();
    private final PairingRequest pairingRequest = new PairingRequest();
    Map<String, String> devices = new LinkedHashMap<>();
    protected static final String APP_UUID = "84b624f0-2afd-11e5-b345-feff819cdc9f";
    protected static final String SERVICE_NAME = "BlueShare";
    List<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    protected BluetoothAdapter bluetoothAdapter;
    private DeviceListener callback;
    private EditText editText;
    private ArrayAdapter<String> conversationAdapter;
    public static boolean CLIENT_STARTED = false;
    private static int NOTIFICATION_ID = 93;
    private static String CLOSE_ACTION = "CLOSE_FOREGROUND";
    public static int count = 0;
    private String tracks;
    List<String> blacklistedDevices = new ArrayList<>();
    private final String CLOSE_CLIENT_ACTION = "com.example.c1009692.shareblue.ChatFragment.CLOSE_CLIENT";
    private final String SONG_SEND_PREFERENCE = "com.example.nunes.shareblue.ChatFragment.SEND";
    private final String SONGS_RECEIVED_PREFERENCE = "com.example.nunes.shareblue.ChatFragment.SONGS_RECEIVED_PREFERENCE";
    private SharedPreferences songToSend;
    private SharedPreferences songReceived;
    Thread runningThread;
    ReceiveDataThread readThread;
    SendDataThread writeThread;
    ServerThread serverThread;
    public static AtomicBoolean SENDING = new AtomicBoolean(false);
    public static AtomicBoolean RECEIVING = new AtomicBoolean(false);
    public static AtomicBoolean SEND_REQUEST_FINISHED = new AtomicBoolean(true);
    public static AtomicBoolean RUN_THREAD = new AtomicBoolean(true);
    public static AtomicBoolean SEND_RECEIVE = new AtomicBoolean(false);
    public static AtomicBoolean MAY_RECEIVE = new AtomicBoolean(false);
    public static AtomicBoolean DELAY = new AtomicBoolean(false);
    private final Binder mBinder = new LocalBinder();

    MainThread mainThread;
    ExecutorService executor;
    public interface DeviceListener {
        void onDeviceFound(BluetoothDevice device);
        void onDataReceived(String dataReceived);
    }

    //WHILE MAY RECEIVE SUNDAY

   // public ChatService() {
   //     super("ChatService");
   // }

    public class LocalBinder extends Binder {
        ChatService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ChatService.this;
        }
    }

    private static final class mHandler extends Handler {


        private final ChatService mTarget;
        mHandler(ChatService target) {
            mTarget = target;
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what == Constants.MESSAGE_READ) {
                BluetoothSocket bluetoothSocket = (BluetoothSocket) message.obj;
                //MainActivity target = mTarget.get();
                //Toast.makeText(target.getApplicationContext(), "Connected", Toast.LENGTH_LONG).show();

                Log.d("ChatFragment", "Preparing to receive a message");
                mTarget.startReceiveThread(bluetoothSocket);

            }
            else if (message.what == Constants.MESSAGE_SEND) {
                BluetoothSocket bluetoothSocket = (BluetoothSocket) message.obj;
                //MainActivity target =
                // mTarget.get();
                //Toast.makeText(target.getApplicationContext(), "Connected", Toast.LENGTH_LONG).show();
                Log.d("ChatFragment", "Preparing to send a message");
                mTarget.startSendThread(bluetoothSocket);
            }
            else if (message.what == Constants.MESSAGE_RECEIVED) {
                Map<String, Object> msg = (HashMap<String, Object>)message.obj;
                byte[] dataReceived = (byte[])msg.get("buffer");
                BluetoothSocket bluetoothSocket = (BluetoothSocket)msg.get("socket");
                //myFragment.myHandler.obtainMessage(Constants.SEND_RECEIVE, bluetoothSocket).sendToTarget();
                CLIENT_STARTED = false;
                mTarget.updateAdapter(dataReceived);
                mTarget.closeReceiveThread();
                mTarget.bluetoothAdapter.startDiscovery();
                mTarget.startServerThread();

                /** Needs to be moved to send data part
                 if (myFragment.CLIENT_STARTED == true) {
                 mTarget.get().discoveryReceiver.addToBlacklist();
                 mTarget.get().closeReceiveThread();
                 mTarget.get().discoveryReceiver.disconnect();
                 }
                 */
            }
            else if (message.what == Constants.SEND_RECEIVE) {
                BluetoothSocket bluetoothSocket = (BluetoothSocket) message.obj;
                Log.d("ChatFragment", "Preparing to send a message in SEND_RECEIVE");
                SEND_RECEIVE.set(true);
                CLIENT_STARTED = false;
                mTarget.bluetoothAdapter.cancelDiscovery();
                mTarget.startSendThread(bluetoothSocket);
            }
            else if (message.what == Constants.CLOSE_THREADS) {
                mTarget.closeReceiveThread();
                mTarget.closeSendThread();
                CLIENT_STARTED = false;
                mTarget.bluetoothAdapter.startDiscovery();
                mTarget.startServerThread();

            }

        }
    }

    class MainThread extends HandlerThread {
        private Handler theHandler;

        public MainThread() {
            super("MainThread", android.os.Process.THREAD_PRIORITY_BACKGROUND);
        }

        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();

            theHandler = new Handler(getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case 1:
                            startServerThread();
                            while (true) {
                                Log.d("ChatFragment", "MAINTHREAD: " + (Looper.myLooper() == Looper.getMainLooper()) + "");
                                Random rand = new Random();
                                int timeToRun = rand.nextInt(15) + 30;
                                Log.d("ChatFragment", "Server Started for " + timeToRun + " seconds");
                                boolean started = bluetoothAdapter.startDiscovery();
                                try {
                                    Thread.sleep(timeToRun * 1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                while (RECEIVING.get());
                                RUN_THREAD.set(true);
                                CLIENT_STARTED = true;
                                bluetoothAdapter.cancelDiscovery();
                                SEND_REQUEST_FINISHED.set(true);
                                //attemptConnectionThread = new Thread(attemptConnectionRunnable);
                                //attemptConnectionThread.start();

                                Log.d("ChatFragment", "Client Started for " + timeToRun + " seconds");
                                long t1 = SystemClock.elapsedRealtime() / 1000;
                                while (((SystemClock.elapsedRealtime() / 1000) - t1) < timeToRun) {
                                    Future<Boolean> finished = executor.submit(new RunClientThread());
                                    try {
                                        finished.get();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    } catch (ExecutionException e) {
                                        e.printStackTrace();
                                    }

                                }
                                while(SENDING.get());

                                SEND_REQUEST_FINISHED.set(false);
                                CLIENT_STARTED = false;
                                RUN_THREAD.set(false);
                                /**
                                 try {
                                 runClientThread.join();
                                 } catch (InterruptedException e) {
                                 e.printStackTrace();
                                 }
                                 */
                                bluetoothAdapter.startDiscovery();


                            }
                    }
                }
            };
        }

        public void runMainThread() {
            while (theHandler==null);
            theHandler.sendEmptyMessage(1);
        }

    }


    private class RunClientThread implements Callable<Boolean> {

        @Override
        public Boolean call() {
            //  if ((!SEND_RECEIVE.get()) && (!MAY_RECEIVE.get())) {
            if (!RECEIVING.get()) {
                discoveryReceiver.disconnect();
                while (SENDING.get()) {
                    DELAY.set(true);
                }
                if (DELAY.get()) {
                    DELAY.set(false);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            //  }
            // else {
            //     try {
            //         Thread.sleep(3000);
            //         MAY_RECEIVE.set(false);
            //     } catch (InterruptedException e) {
            //         e.printStackTrace();
            //     }
            //  }
            return true;
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if ((intent != null) && (intent.getAction() != null) && (intent.getAction().equals(CLOSE_ACTION))) {
            Log.d("ChatService", "Stop Foreground");
            stopForeground(true);
        }
        else {
            if (executor == null) {
                executor = Executors.newSingleThreadExecutor();
            }
            mainThread.runMainThread();
            songToSend = this.getSharedPreferences(SONG_SEND_PREFERENCE, Context.MODE_PRIVATE);
            songReceived = this.getSharedPreferences(SONGS_RECEIVED_PREFERENCE, Context.MODE_PRIVATE);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Track Sharer")
                    .setContentText("Sending and receiving tracks");
            Intent activityIntent = new Intent(this, MainActivity.class);
            //activityIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP );
            activityIntent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activityIntent.addCategory(Intent.ACTION_MAIN);
            activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            Intent closeIntent = new Intent(this, ChatService.class);
            closeIntent.setAction(CLOSE_ACTION);
            PendingIntent pcloseIntent = PendingIntent.getService(this, 0, closeIntent, 0);
            //TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            //stackBuilder.addParentStack(MainActivity.class);
            //stackBuilder.addNextIntent(activityIntent);
            //PendingIntent mainActivityPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            PendingIntent mainActivityPendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            notificationBuilder.setContentIntent(mainActivityPendingIntent);
            notificationBuilder.addAction(R.drawable.close, "", pcloseIntent);
            RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.activity_notification);
            contentView.setImageViewResource(R.id.image, R.mipmap.ic_launcher);
            contentView.setOnClickPendingIntent(R.id.closeImage, pcloseIntent);
            contentView.setTextViewText(R.id.title, "Blue Share");
            contentView.setTextViewText(R.id.text, "Sending/Receiving songs");
            contentView.setOnClickPendingIntent(R.id.layout, mainActivityPendingIntent);
            notificationBuilder.setContent(contentView);
            startForeground(NOTIFICATION_ID, notificationBuilder.build());
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void startReceiveThread(BluetoothSocket socket) {
        readThread = new ReceiveDataThread(socket);
        readThread.start();
    }

    public void startSendThread(BluetoothSocket socket) {
        byte[] b = songToSend.getAll().get("song").toString().getBytes(Charset.forName("UTF-8"));
        writeThread = new SendDataThread(socket, b);
        writeThread.start();
    }

    public void closeSendThread() {
        if (writeThread != null) {
            writeThread.cancel();
            try {
                writeThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void startServerThread() {
        if (serverThread != null) {
            serverThread.cancel();
            try {
                serverThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        serverThread = new ServerThread();
        serverThread.start();
    }

    public void closeReceiveThread() {
        if (readThread != null) {
            readThread.cancel();
            try {
                readThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private synchronized void clearFileDescriptor(BluetoothSocket socket){
        try{
            Field field = BluetoothSocket.class.getDeclaredField("mPfd");
            field.setAccessible(true);
            ParcelFileDescriptor mPfd = (ParcelFileDescriptor)field.get(socket);
            if(null == mPfd){
                return;
            }

            mPfd.close();
        }catch(Exception e){
            Log.w("ChatFragment", "LocalSocket could not be cleanly closed.");
        }
    }

    private synchronized void clearServerFileDescriptor(BluetoothServerSocket socket){
        try{
            Field mSocketFld = socket.getClass().getDeclaredField("mSocket");
            mSocketFld.setAccessible(true);

            BluetoothSocket btsock = (BluetoothSocket)mSocketFld.get(socket);

            Field mPfdFld = btsock.getClass().getDeclaredField("mPfd");
            mPfdFld.setAccessible(true);

            ParcelFileDescriptor pfd = (ParcelFileDescriptor)mPfdFld.get(btsock);
            if (null == pfd) {
                return;
            }
            pfd.close();
        }catch(Exception e){
            Log.w("ChatFragment", "LocalSocket could not be cleanly closed.");
        }
    }

    public void updateAdapter(byte[] msg) {
        try {
            String dataReceived = new String(msg, "UTF-8");
            String[] songsReceived = dataReceived.split(",");
            //for (String song : songsReceived) {
                //if (!songReceived.contains(dataReceived.trim())) {
                    Log.d("ChatFragment", "About to send intent");
                    Intent dataReceivedIntent = new Intent(Constants.DATA_FOUND);
                    dataReceivedIntent.putExtra("song", dataReceived);
                    sendBroadcast(dataReceivedIntent);

                    Intent spotifyAddIntent = new Intent(this, MySpotifyService.class);
                    spotifyAddIntent.putExtra("ACTION", "SONG_RECEIVED");
                    spotifyAddIntent.putExtra("song", dataReceived.trim());
                    startService(spotifyAddIntent);
                    SharedPreferences.Editor editor = songReceived.edit();
                    editor.putString(dataReceived.trim(), dataReceived.trim());
                    editor.apply();
                //}
          //  }
            //callback.onDataReceived(dataReceived);
            //conversationAdapter.add(dataReceived);
           // conversationAdapter.notifyDataSetChanged();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onCreate() {
        if (mainThread == null) {
            mainThread = new MainThread();
            mainThread.start();
        }
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.d("ChatService", "This device doesn't support Bluetooth");
            stopSelf();
        }
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter action_disc_filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        IntentFilter close_client = new IntentFilter(CLOSE_CLIENT_ACTION);
        registerReceiver(discoveryReceiver, filter);
        registerReceiver(discoveryReceiver, action_disc_filter);
        registerReceiver(discoveryReceiver, close_client);

        IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        /*
         * Registering a new BTBroadcast receiver from the Main Activity context
         * with pairing request event
         */
        registerReceiver(pairingRequest, filter2);
        if (myHandler == null) {
            myHandler = new mHandler(this);
        }

        super.onCreate();
    }



    public void setTracks(String tracks) {
        this.tracks = tracks;
        SharedPreferences.Editor editor = songToSend.edit();
        editor.putString("song", tracks);
        editor.apply();
    }

    private class ServerThread extends Thread {
        private final BluetoothServerSocket bluetoothServerSocket;



        public ServerThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, UUID.fromString(APP_UUID));
            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket serverSocket = null;

            while (true) {
                try {
                    serverSocket = bluetoothServerSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                } catch (NullPointerException ne) {
                    ne.printStackTrace();
                    break;
                }

                Log.d("ChatFragment", "I get close to receiving a message");
                if (serverSocket != null) {
                    myHandler.obtainMessage(Constants.MESSAGE_READ, serverSocket).sendToTarget();
                    try {
                        bluetoothServerSocket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                clearServerFileDescriptor(bluetoothServerSocket);
                bluetoothServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ClientThread extends Thread {
        private final BluetoothDevice bDevice;
        private final BluetoothSocket clientSocket;

        public ClientThread(BluetoothDevice bbDevice) {
            BluetoothSocket tmp = null;
            bDevice = bbDevice;

            try {
                tmp = bDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString(APP_UUID));
                Log.d("ChatFragment", "CLIENT THREAD: I connected");
                SENDING.set(true);
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("ChatFragment", "CLIENT THREAD: I didn't connect");
                SENDING.set(false);
            }
            clientSocket = tmp;
        }

        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                Log.d("ChatFragment", "Before Client Socket connect");
                clientSocket.connect();
                Log.d("ChatFragment", "After Client Socket connect");
                SENDING.set(true);
                myHandler.obtainMessage(Constants.MESSAGE_SEND, clientSocket).sendToTarget();
            } catch (IOException e) {
                Log.d("ChatFragment", "I hit exception connecting");
                e.printStackTrace();
                SENDING.set(false);
                /** Going to need cancel elsewhere
                 if (clientSocket != null) {
                 cancel();
                 SEND_REQUEST_FINISHED.set(true);
                 }*/
            }
            //myHandler.obtainMessage(Constants.MESSAGE_READ, bSocket).sendToTarget();
        }

        public void cancel() {
            try {
                if (clientSocket != null) {
                    clearFileDescriptor(clientSocket);
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class BluetoothDiscoveryReceiver extends BroadcastReceiver {
        ClientThread clientThread;
        SendDataThread sendDataThread;
        BluetoothDevice currentDevice;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if ((!blacklistedDevices.contains(device.getAddress())) && (!devices.containsKey(device.getName()))) {
                    //if (("MatthewsPhone").equals(device.getName())) {
                    bluetoothDevices.add(device);
                    devices.put(device.getName(), device.getAddress());
                    Log.d("ChatService", "DEVICE FOUND! " + device.getName());
                    Intent deviceFoundIntent = new Intent(Constants.DEVICE_FOUND);
                    deviceFoundIntent.putExtra("name", device.getName());
                    deviceFoundIntent.putExtra("address", device.getAddress());
                    sendBroadcast(deviceFoundIntent);
                    //callback.onDeviceFound(device);
                    //}
                }


            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d("ChatFragment", "Finished Discovery");
                if (!CLIENT_STARTED) {
                    bluetoothAdapter.startDiscovery();
                }
            }
            /**
             else if(BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
             if ((currentDevice != null) && (blacklistedDevices.size() > 0) && (blacklistedDevices.get(blacklistedDevices.size() -1).equals(currentDevice.getAddress()))) {
             blacklistedDevices.remove(blacklistedDevices.size() - 1);
             bluetoothDevices.add(0, currentDevice);
             }
             SEND_REQUEST_FINISHED = true;
             Log.d("ChatFragment", "Blacklisted Devices: " + blacklistedDevices.size());
             Log.d("ChatFragment", "Bluetooth Devices: " + bluetoothDevices.size());
             //disconnect();
             }
             */
            /**
             else if((BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) && (bSocket != null)) {
             Log.d("ChatFragment", "Connected!");
             //byte[] b = tracks.getBytes(Charset.forName("UTF-8"));
             //blacklisted device must be added a second time as it will be removed once when the connection terminates
             //sendDataThread = new SendDataThread(bSocket, b);
             //sendDataThread.start();
             }
             */
            else if(CLOSE_CLIENT_ACTION.equals(action)) {
                clientThread.cancel();
                try {
                    clientThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void disconnect() {
            if (!CLIENT_STARTED) {
                if (clientThread != null) {
                    clientThread.cancel();
                    try {
                        clientThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (sendDataThread != null) {
                    sendDataThread.cancel();
                    try {
                        sendDataThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
            else {
                if (sendDataThread != null) {
                    sendDataThread.cancel();
                    try {
                        sendDataThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (clientThread != null) {
                    clientThread.cancel();
                    try {
                        clientThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (bluetoothDevices.size() > 0) {
                    currentDevice = bluetoothDevices.remove(bluetoothDevices.size() - 1);
                    /**
                     while (bluetoothAdapter.getName().compareToIgnoreCase(currentDevice.getName() + "") > 0 ) {
                     if (bluetoothDevices.size() > 0) {
                     blacklistedDevices.add(currentDevice.getAddress());
                     currentDevice = bluetoothDevices.remove(bluetoothDevices.size() - 1);
                     }
                     else {
                     currentDevice = null;
                     break;
                     }
                     }
                     */
                    if ((currentDevice != null) && (!currentDevice.getName().equalsIgnoreCase("Bishop"))) {
                        if (currentDevice.getName().equalsIgnoreCase("MatthewsPhone") || currentDevice.getName().equalsIgnoreCase("TestPhone")) {
                            count+=1;
                            Log.d("COUNT", "COUNT: " + count);
                        }
                        bluetoothDevices.add(0, currentDevice);
                        Log.d("ChatFragment", "Trying to connect to " + currentDevice.getName());
                        SENDING.set(true);
                        clientThread = new ClientThread(currentDevice);
                        clientThread.start();
                    }
                }
            }
        }

        public void addToBlacklist() {
            blacklistedDevices.add(currentDevice.getAddress());
        }
    }

    public static class PairingRequest extends BroadcastReceiver {
        public PairingRequest() {
            super();
        }

        @TargetApi(Build.VERSION_CODES.KITKAT)
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.bluetooth.device.action.PAIRING_REQUEST")) {
                try {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    int pin=intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", 0);
                    //the pin in case you need to accept for an specific pin
                    Log.d("ChatFragment", "PIN: " + intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY",0));
                    //maybe you look for a name or address
                    Log.d("ChatFragment", "Bonded: " + device.getName());
                    byte[] pinBytes;
                    pinBytes = (""+pin).getBytes("UTF-8");
                    device.setPin(pinBytes);
                    //setPairing confirmation if neeeded
                    device.setPairingConfirmation(true);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class ReceiveDataThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;

        public ReceiveDataThread(BluetoothSocket socket) {
            Log.d("ChatFragment", "Receive Data Thread called");
            RECEIVING.set(true);
            mmSocket = socket;
            InputStream tmpIn = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException | NullPointerException e) {
                RECEIVING.set(false);
                e.printStackTrace();
            }
            mmInStream = tmpIn;
        }

        public void run() {
            Log.d("ChatFragment", "Run method within recive fragment called");
            byte[] buffer = new byte[2048];
            Map<String, Object> dataToSend = new HashMap<>();
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    Log.d("ChatFragment", "Got message");
                    // Send the obtained bytes to the UI Activity
                    dataToSend.put("buffer", buffer);
                    dataToSend.put("socket", mmSocket);
                    myHandler.obtainMessage(Constants.MESSAGE_RECEIVED, bytes, -1, dataToSend)
                            .sendToTarget();
                    RECEIVING.set(false);
                } catch (IOException e) {
                    RECEIVING.set(false);
                    cancel();
                    break;
                } catch (NullPointerException ne) {
                    RECEIVING.set(false);
                    cancel();
                    break;
                }
            }
            RECEIVING.set(false);

        }

        public void cancel() {
            try {
                clearFileDescriptor(mmSocket);
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class SendDataThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final OutputStream mmOutStream;
        private byte[] buffer;

        public SendDataThread(BluetoothSocket socket, byte[] buff) {
            SENDING.set(true);
            mmSocket = socket;
            OutputStream tmpOut = null;
            buffer = buff;

            // Get the BluetoothSocket input and output streams
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                SENDING.set(false);
            }
            mmOutStream = tmpOut;
        }


        public void run() {
            Log.d("ChatFragment", "SendData thread run method");
            try {
                Log.d("ChatFragment", "Send Socket connected? " + mmSocket.isConnected());
                mmOutStream.write(buffer);
                Log.d("ChatFragment", "I send");
                // Share the sent message back to the UI Activity
                //myHandler.obtainMessage(Constants.MESSAGE_RECEIVED, -1, -1, buffer)
                //        .sendToTarget();
                MAY_RECEIVE.set(true);
            } catch (IOException | NullPointerException e) {
                Log.d("ChatFragment", "I hit exception");
                e.printStackTrace();
            }
            SEND_REQUEST_FINISHED.set(true);
            SENDING.set(false);
            /**
             if (SEND_RECEIVE.get()) {
             SEND_RECEIVE.set(false);
             myHandler.obtainMessage(Constants.CLOSE_THREADS).sendToTarget();

             }
             */

        }

        public void cancel() {
            try {
                clearFileDescriptor(mmSocket);
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onDestroy() {
        Log.d("ChatFragment", "I've been destroyed");
        mainThread.quit();
        unregisterReceiver(discoveryReceiver);
        unregisterReceiver(pairingRequest);
        super.onDestroy();

    }

    //@Override
    //protected void onHandleIntent(Intent intent) {
    //    if (executor == null) {
    //        executor = Executors.newSingleThreadExecutor();
    //    }

        /*
        if (mainThread == null) {
            mainThread = new MainThread();
            mainThread.start();
        }

        mainThread.runMainThread();
        */
    //    startServerThread();
    //    while (true) {
    //        Log.d("ChatFragment", "MAINTHREAD: " + (Looper.myLooper() == Looper.getMainLooper()) + "");
    //        Random rand = new Random();
    //        int timeToRun = rand.nextInt(15) + 30;
    //        Log.d("ChatFragment", "Server Started for " + timeToRun + " seconds");
    //        boolean started = bluetoothAdapter.startDiscovery();
    //        try {
    //            Thread.sleep(timeToRun * 1000);
    //        } catch (InterruptedException e) {
     //           e.printStackTrace();
    //        }
    //        while (RECEIVING.get()) ;
    //        RUN_THREAD.set(true);
    //        CLIENT_STARTED = true;
    //        bluetoothAdapter.cancelDiscovery();
    //        SEND_REQUEST_FINISHED.set(true);
            //attemptConnectionThread = new Thread(attemptConnectionRunnable);
            //attemptConnectionThread.start();

    //        Log.d("ChatFragment", "Client Started for " + timeToRun + " seconds");
    //        long t1 = SystemClock.elapsedRealtime() / 1000;
    //        while (((SystemClock.elapsedRealtime() / 1000) - t1) < timeToRun) {
    //            Future<Boolean> finished = executor.submit(new RunClientThread());
    //            try {
    //                finished.get();
    //            } catch (InterruptedException e) {
    //                e.printStackTrace();
    //            } catch (ExecutionException e) {
    //                e.printStackTrace();
    //            }

    //        }
    //        while (SENDING.get()) ;

    //        SEND_REQUEST_FINISHED.set(false);
    //        CLIENT_STARTED = false;
    //        RUN_THREAD.set(false);
            /**
             try {
             runClientThread.join();
             } catch (InterruptedException e) {
             e.printStackTrace();
             }
             */
    //        bluetoothAdapter.startDiscovery();

    //    }
    //}


}
