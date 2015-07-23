package com.example.c1009692.shareblue;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by Matthew on 17/07/15.
 */
public class ChatFragment extends Fragment {

    static Handler myHandler;
    private final BluetoothDiscoveryReceiver discoveryReceiver = new BluetoothDiscoveryReceiver();
    Map<String, String> devices = new HashMap<>();
    protected static final String APP_UUID = "84b624f0-2afd-11e5-b345-feff819cdc9f";
    protected static final String SERVICE_NAME = "BlueShare";
    List<BluetoothDevice> bluetoothDevices = new ArrayList<>();
    protected BluetoothAdapter bluetoothAdapter;
    private DeviceListener callback;
    private EditText editText;
    private ArrayAdapter<String> conversationAdapter;
    private BluetoothSocket bSocket;
    public static boolean CLIENT_STARTED = false;

    public interface DeviceListener {
         void onDeviceFound(BluetoothDevice device);
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
                myFragment.startReceiveThread(bluetoothSocket);

            }
            else if (message.what == Constants.MESSAGE_RECEIVED) {
                byte[] msg = (byte[])message.obj;
                ChatFragment myFragment = mTarget.get();
                myFragment.updateAdapter(msg);
            }

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
        ReceiveDataThread readThread = new ReceiveDataThread(socket);
        readThread.start();
    }

    public void updateAdapter(byte[] msg) {
        try {
            conversationAdapter.add(new String(msg, "UTF-8"));
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
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        getActivity().registerReceiver(discoveryReceiver, filter);
        myHandler = new mHandler(this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getActivity(), "This device does not support Bluetooth!", Toast.LENGTH_LONG).show();
            getActivity().finish();
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent turnOnBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            //turnOnBluetooth.addCategory(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            //turnOnBluetooth.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            startActivityForResult(turnOnBluetooth, Constants.REQUEST_ENABLE_BT);
            Intent discoverBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverBluetooth.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            startActivity(discoverBluetooth);
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
                byte[] b = editText.getText().toString().getBytes(Charset.forName("UTF-8"));
                SendDataThread sendDataThread = new SendDataThread(bSocket, b);
                sendDataThread.start();
            }
        });
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == Constants.REQUEST_ENABLE_BT) {
                boolean started = bluetoothAdapter.startDiscovery();
                if (started) {
                    //ServerThread serverThread = new ServerThread();
                    //serverThread.start();
                } else {
                }
            }
            if (requestCode == Constants.REQUEST_DISCOVERABLE_BT) {
            }
        }
    }

    private class ServerThread extends Thread {
        private final BluetoothServerSocket bluetoothServerSocket;


        public ServerThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, UUID.fromString(APP_UUID));
            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothServerSocket = tmp;
        }

        public void run() {
            bluetoothAdapter.cancelDiscovery();
            bSocket = null;

            while (true) {
                try {
                    bSocket = bluetoothServerSocket.accept();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }

                if (bSocket != null) {
                    myHandler.obtainMessage(Constants.MESSAGE_READ, bSocket).sendToTarget();
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
                bluetoothServerSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ClientThread extends Thread {
        private final BluetoothDevice bDevice;

        public ClientThread(BluetoothDevice bbDevice) {
            BluetoothSocket tmp = null;
            bDevice = bbDevice;

            try {
                tmp = bDevice.createRfcommSocketToServiceRecord(UUID.fromString(APP_UUID));
            } catch (IOException e) {
                e.printStackTrace();
            }
            bSocket = tmp;
        }

        public void run() {
            bluetoothAdapter.cancelDiscovery();
            try {
                bSocket.connect();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    bSocket.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    return;
                }
            }
            myHandler.obtainMessage(Constants.MESSAGE_READ, bSocket).sendToTarget();
        }

        public void cancel() {
            try {
                bSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class BluetoothDiscoveryReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                bluetoothDevices.add(device);
                if ((device.getName() != null) && (device.getName().equals("MatthewsPhone"))) {
                    Thread clientThread = new ClientThread(device);
                    clientThread.start();
                    ChatFragment.CLIENT_STARTED = true;
                }
                if (!devices.containsKey(device.getName())) {
                    devices.put(device.getName(), device.getAddress());
                    callback.onDeviceFound(device);

                }

            }
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d("Matthew App", "Finished Discovery");
                if (!ChatFragment.CLIENT_STARTED) {
                    bluetoothAdapter.startDiscovery();
                }
            }
        }
    }

    public class ReceiveDataThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ReceiveDataThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    myHandler.obtainMessage(Constants.MESSAGE_RECEIVED, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {

                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                myHandler.obtainMessage(Constants.MESSAGE_RECEIVED, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cancel() {
            try {
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
            mmSocket = socket;
            OutputStream tmpOut = null;
            buffer = buff;

            // Get the BluetoothSocket input and output streams
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mmOutStream = tmpOut;
        }


        public void run() {
            try {
                mmOutStream.write(buffer);

                // Share the sent message back to the UI Activity
                myHandler.obtainMessage(Constants.MESSAGE_RECEIVED, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(discoveryReceiver);
    }

}
