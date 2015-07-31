package com.example.c1009692.shareblue;

import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Matthew on 17/07/15.
 */
public class ChatFragment extends Fragment {

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
    public static boolean runOnce = false;
    private String tracks;
    List<String> blacklistedDevices = new ArrayList<>();
    private final String CLOSE_CLIENT_ACTION = "com.example.c1009692.shareblue.ChatFragment.CLOSE_CLIENT";
    Thread runningThread;
    ReceiveDataThread readThread;
    SendDataThread writeThread;
    ServerThread serverThread;
    Thread attemptConnectionThread;
    Runnable attemptConnectionRunnable;
    public static AtomicBoolean SENDING = new AtomicBoolean(false);
    public static boolean RECEIVING = false;
    public static AtomicBoolean SEND_REQUEST_FINISHED = new AtomicBoolean(true);
    public static AtomicBoolean RUN_THREAD = new AtomicBoolean(true);
    RunClientThread runClientThread;
    MainThread mainThread;
    public interface DeviceListener {
        void onDeviceFound(BluetoothDevice device);
        void onDataReceived(String dataReceived);
    }



    private static final class mHandler extends Handler {


        private final WeakReference<ChatFragment> mTarget;
        mHandler(ChatFragment target) {
            mTarget = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what == Constants.MESSAGE_READ) {
                BluetoothSocket bluetoothSocket = (BluetoothSocket) message.obj;
                //MainActivity target = mTarget.get();
                //Toast.makeText(target.getApplicationContext(), "Connected", Toast.LENGTH_LONG).show();
                ChatFragment myFragment = mTarget.get();
                Log.d("ChatFragment", "Preparing to receive a message");
                myFragment.startReceiveThread(bluetoothSocket);

            }
            else if (message.what == Constants.MESSAGE_SEND) {
                BluetoothSocket bluetoothSocket = (BluetoothSocket) message.obj;
                //MainActivity target = mTarget.get();
                //Toast.makeText(target.getApplicationContext(), "Connected", Toast.LENGTH_LONG).show();
                ChatFragment myFragment = mTarget.get();
                Log.d("ChatFragment", "Preparing to send a message");
                myFragment.startSendThread(bluetoothSocket);
            }
            else if (message.what == Constants.MESSAGE_RECEIVED) {
                byte[] msg = (byte[])message.obj;
                ChatFragment myFragment = mTarget.get();
                myFragment.updateAdapter(msg);
                myFragment.closeReceiveThread();
                myFragment.startServerThread();

                /** Needs to be moved to send data part
                if (myFragment.CLIENT_STARTED == true) {
                    mTarget.get().discoveryReceiver.addToBlacklist();
                    mTarget.get().closeReceiveThread();
                    mTarget.get().discoveryReceiver.disconnect();
                }
                */
            }

        }
    }

    class MainThread extends HandlerThread {
        private Handler theHandler;

        public MainThread() {
            super("MainThread", Process.THREAD_PRIORITY_BACKGROUND);
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
                                while (RECEIVING);
                                RUN_THREAD.set(true);
                                CLIENT_STARTED = true;
                                bluetoothAdapter.cancelDiscovery();
                                SEND_REQUEST_FINISHED.set(true);
                                //attemptConnectionThread = new Thread(attemptConnectionRunnable);
                                //attemptConnectionThread.start();
                                runClientThread = new RunClientThread();
                                Log.d("ChatFragment", "Client Started for " + timeToRun + " seconds");
                                long t1 = SystemClock.elapsedRealtime() / 1000;
                                runClientThread.start();
                                while (((SystemClock.elapsedRealtime() / 1000) - t1) < timeToRun) {

                                    if (!runClientThread.isAlive()) {
                                        runClientThread.run();
                                    }
                                }
                                Log.d("ChatFragment", "Just before while(SENDING)");
                                while(SENDING.get());
                                Log.d("ChatFragment", "After while(SENDING)");
                                SEND_REQUEST_FINISHED.set(false);
                                CLIENT_STARTED = false;
                                RUN_THREAD.set(false);
                                Log.d("ChatFragment", "Before joining");
                                try {
                                    runClientThread.join();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                Log.d("ChatFragment", "After joining");
                                //attemptConnectionThread.join();
                                bluetoothAdapter.startDiscovery();


                            }
                    }
                }
            };
        }

        public void runMainThread() {
            theHandler.sendEmptyMessage(1);
        }

    }


    private class RunClientThread extends Thread {

        @Override
        public void run() {
            discoveryReceiver.disconnect();
        }

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            callback = (DeviceListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnHeadlineSelectedListener");
        }
    }

    public void startReceiveThread(BluetoothSocket socket) {
        readThread = new ReceiveDataThread(socket);
        readThread.start();
    }

    public void startSendThread(BluetoothSocket socket) {
        byte[] b = tracks.getBytes(Charset.forName("UTF-8"));
        writeThread = new SendDataThread(socket, b);
        writeThread.start();
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
            callback.onDataReceived(dataReceived);
            conversationAdapter.add(dataReceived);
            conversationAdapter.notifyDataSetChanged();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        editText = (EditText) getActivity().findViewById(R.id.edit_text_out);
        conversationAdapter = new ArrayAdapter<>(getActivity(), R.layout.message);
        ListView conversationView = (ListView) getActivity().findViewById(R.id.in);
        conversationView.setAdapter(conversationAdapter);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        IntentFilter action_disc_filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        //IntentFilter action_disconnected = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        //IntentFilter action_connected = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        IntentFilter close_client = new IntentFilter(CLOSE_CLIENT_ACTION);
        getActivity().registerReceiver(discoveryReceiver, filter);
        getActivity().registerReceiver(discoveryReceiver, action_disc_filter);
        //getActivity().registerReceiver(discoveryReceiver, action_disconnected);
        //getActivity().registerReceiver(discoveryReceiver, action_connected);
        getActivity().registerReceiver(discoveryReceiver, close_client);

        IntentFilter filter2 = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);


        /*
         * Registering a new BTBroadcast receiver from the Main Activity context
         * with pairing request event
         */
        getActivity().registerReceiver(pairingRequest, filter2);
        myHandler = new mHandler(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        /**
        attemptConnectionRunnable = new Runnable() {
            public void run() {
                while(RUN_THREAD.get()) {
                    if (SEND_REQUEST_FINISHED.get()) {
                        discoveryReceiver.disconnect();
                        SEND_REQUEST_FINISHED.set(false);
                    }
                }
                Log.d("ChatFragment", "Thread gets out of while");
            }
        };
        */
        mainThread = new MainThread();
        mainThread.start();

        if (bluetoothAdapter == null) {
            Toast.makeText(getActivity(), "This device does not support Bluetooth!", Toast.LENGTH_LONG).show();
            getActivity().finish();
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent turnOnBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //turnOnBluetooth.addCategory(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            //turnOnBluetooth.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            getActivity().startActivityForResult(turnOnBluetooth, Constants.REQUEST_ENABLE_BT);
            Intent discoverBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverBluetooth.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            getActivity().startActivityForResult(discoverBluetooth, Constants.REQUEST_DISCOVERABLE_BT);
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_chatfragment, container, false);
        Button sendButton = (Button) view.findViewById(R.id.button_send);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(view.getContext(), "works", Toast.LENGTH_LONG).show();
                byte[] b = tracks.getBytes(Charset.forName("UTF-8"));
                //SendDataThread sendDataThread = new SendDataThread(bSocket, b);
                //sendDataThread.start();
            }
        });
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!runOnce) {
            if (resultCode == Activity.RESULT_OK) {
                //TODO need to fix 1337 thing
                if ((requestCode == Constants.REQUEST_ENABLE_BT) || (requestCode == 1337)) {
                    boolean started = bluetoothAdapter.startDiscovery();
                    if (started) {
                        mainThread.runMainThread();
                        runOnce = true;
                        //ServerThread serverThread = new ServerThread();
                        //s                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            erverThread.start();
                    } else {
                    }
                }
                if (requestCode == Constants.REQUEST_DISCOVERABLE_BT) {
                }
            }

        }
    }

    public void runThread() {
        if (runningThread != null) {
            try {
                runningThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        repeatRunning();
    }

    public void repeatRunning() {
        //FRIDAY put this in a handler
        new Thread() {
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
            public void run() {
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
                    while (RECEIVING);
                    RUN_THREAD.set(true);
                    CLIENT_STARTED = true;
                    bluetoothAdapter.cancelDiscovery();
                    SEND_REQUEST_FINISHED.set(true);
                    //attemptConnectionThread = new Thread(attemptConnectionRunnable);
                    //attemptConnectionThread.start();
                    runClientThread = new RunClientThread();
                    Log.d("ChatFragment", "Client Started for " + timeToRun + " seconds");
                    long t1 = SystemClock.elapsedRealtime() / 1000;
                    while (((SystemClock.elapsedRealtime() / 1000) - t1) < timeToRun) {
                        runClientThread.start();
                        if (!runClientThread.isAlive()) {
                            runClientThread.run();
                        }
                    }
                    Log.d("ChatFragment", "Just before while(SENDING)");
                    while(SENDING.get());
                    Log.d("ChatFragment", "After while(SENDING)");
                    SEND_REQUEST_FINISHED.set(false);
                    CLIENT_STARTED = false;
                    RUN_THREAD.set(false);
                    Log.d("ChatFragment", "Before joining");
                    try {
                        runClientThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Log.d("ChatFragment", "After joining");
                    //attemptConnectionThread.join();
                    bluetoothAdapter.startDiscovery();


                }
                //runThread();



            }
        }.start();
    }


    public void setTracks(String tracks) {
        this.tracks = tracks;
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
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("ChatFragment", "CLIENT THREAD: I didn't connect");
            }
            clientSocket = tmp;
        }

        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                clientSocket.connect();
                SENDING.set(true);
                myHandler.obtainMessage(Constants.MESSAGE_SEND, clientSocket).sendToTarget();
            } catch (IOException e) {
                Log.d("ChatFragment", "I hit exception connecting");
                e.printStackTrace();
                SENDING.set(false);

                if (clientSocket != null) {
                    cancel();
                    SEND_REQUEST_FINISHED.set(true);
                }
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
                    callback.onDeviceFound(device);
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

                    if (currentDevice != null) {
                        bluetoothDevices.add(0, currentDevice);
                        Log.d("ChatFragment", "Trying to connect to " + currentDevice.getName());
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
            RECEIVING = true;
            mmSocket = socket;
            InputStream tmpIn = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException | NullPointerException e) {
                RECEIVING = false;
                e.printStackTrace();
            }
            mmInStream = tmpIn;
        }

        public void run() {
            Log.d("ChatFragment", "Run method within recive fragment called");
            byte[] buffer = new byte[2048];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    Log.d("ChatFragment", "Got message");
                    // Send the obtained bytes to the UI Activity
                    myHandler.obtainMessage(Constants.MESSAGE_RECEIVED, bytes, -1, buffer)
                            .sendToTarget();
                    RECEIVING = false;
                } catch (IOException e) {
                    RECEIVING = false;
                    break;
                } catch (NullPointerException ne) {
                    RECEIVING = false;
                    break;
                }
            }
            cancel();
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
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                //myHandler.obtainMessage(Constants.MESSAGE_RECEIVED, -1, -1, buffer)
                //        .sendToTarget();
            } catch (IOException | NullPointerException e) {
                e.printStackTrace();
            }
            SEND_REQUEST_FINISHED.set(true);
            SENDING.set(false);

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
        getActivity().unregisterReceiver(discoveryReceiver);
        getActivity().unregisterReceiver(pairingRequest);
        super.onDestroy();

    }
    //WEIRD STUFF STARTS HERE
    /**
    public static interface BluetoothSocketWrapper {

        InputStream getInputStream() throws IOException;

        OutputStream getOutputStream() throws IOException;

        String getRemoteDeviceName();

        void connect() throws IOException;

        String getRemoteDeviceAddress();

        void close() throws IOException;

        BluetoothSocket getUnderlyingSocket();

    }


    public static class NativeBluetoothSocket implements BluetoothSocketWrapper {

        private BluetoothSocket socket;

        public NativeBluetoothSocket(BluetoothSocket tmp) {
            this.socket = tmp;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return socket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return socket.getOutputStream();
        }

        @Override
        public String getRemoteDeviceName() {
            return socket.getRemoteDevice().getName();
        }

        @Override
        public void connect() throws IOException {
            socket.connect();
        }

        @Override
        public String getRemoteDeviceAddress() {
            return socket.getRemoteDevice().getAddress();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }

        @Override
        public BluetoothSocket getUnderlyingSocket() {
            return socket;
        }

    }

    public class FallbackBluetoothSocket extends NativeBluetoothSocket {

        private BluetoothSocket fallbackSocket;

        public FallbackBluetoothSocket(BluetoothSocket tmp) throws FallbackException {
            super(tmp);
            try
            {
                Class<?> clazz = tmp.getRemoteDevice().getClass();
                Class<?>[] paramTypes = new Class<?>[] {Integer.TYPE};
                Method m = clazz.getMethod("createInsecureRfcommSocket", paramTypes);
                Object[] params = new Object[] {Integer.valueOf(1)};
                fallbackSocket = (BluetoothSocket) m.invoke(tmp.getRemoteDevice(), params);
            }
            catch (Exception e)
            {
                throw new FallbackException(e);
            }
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return fallbackSocket.getInputStream();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return fallbackSocket.getOutputStream();
        }


        @Override
        public void connect() throws IOException {
            fallbackSocket.connect();
        }


        @Override
        public void close() throws IOException {
            fallbackSocket.close();
        }

    }

    public static class FallbackException extends Exception {

        private static final long serialVersionUID = 1L;

        public FallbackException(Exception e) {
            super(e);
        }

    }
    */
}
