package com.example.c1009692.shareblue;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kaaes.spotify.webapi.android.SpotifyService;

public class MainActivity extends ActionBarActivity {

    ListView listView;
    final ArrayList<String> devicesFound = new ArrayList<>();
    ArrayAdapter<String> deviceAdapter;
    public static String SONGS_PREFERENCES = "com.example.nunes.shareblue.Songs";
    SharedPreferences songsReceived;
    private static boolean DELETE = false;
    protected BluetoothAdapter bluetoothAdapter;
    public static boolean runOnce = false;
    ChatService chatService;
    MySpotifyService spotifyService;
    boolean chatBound = false;
    boolean spotifyBound = false;
    private ArrayAdapter<String> conversationAdapter;
    private BroadcastReceiver mainReceiver;
    private static final String CLIENT_ID = "902f1e299879425e8bba02157464cf37";
    private static final String REDIRECT_URL = "share-blue://callback";
    private static final int REQUEST_CODE = 1337;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.d("ChatService", "This device doesn't support Bluetooth");
            finish();
        }
        if ((!bluetoothAdapter.isEnabled()) || (!bluetoothAdapter.isDiscovering())) {
            Intent discoverBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverBluetooth.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            startActivityForResult(discoverBluetooth, Constants.REQUEST_DISCOVERABLE_BT);
        }
        songsReceived = this.getSharedPreferences(SONGS_PREFERENCES, MODE_PRIVATE);
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID, AuthenticationResponse.Type.TOKEN, REDIRECT_URL);
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-modify-public", "playlist-modify-private", "playlist-read-private", "user-library-read"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(this, REQUEST_CODE, request);

        listView = (ListView) findViewById(R.id.listview);
        deviceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, devicesFound);
        listView.setAdapter(deviceAdapter);
        conversationAdapter = new ArrayAdapter<>(this, R.layout.message);
        ListView conversationView = (ListView) findViewById(R.id.in);
        conversationView.setAdapter(conversationAdapter);
        checkStorageDate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = new Intent(this, ChatService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        Intent spotifyIntent = new Intent(this, MySpotifyService.class);
        bindService(spotifyIntent, spotifyConnection, Context.BIND_AUTO_CREATE);
        if (mainReceiver == null) {
            mainReceiver = new MainReceiver();
        }
        IntentFilter filter = new IntentFilter(Constants.DEVICE_FOUND);
        filter.addAction(Constants.DATA_FOUND);
        filter.addAction(Constants.TRACKS_ADDED);
        registerReceiver(mainReceiver, filter);

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (chatBound) {
            unbindService(mConnection);
        }
        chatBound = false;
        if (spotifyBound) {
            unbindService(spotifyConnection);
        }
        spotifyBound = false;
        unregisterReceiver(mainReceiver);
    }

    public void stopChatService(View view) {
        if (chatBound) {
            unbindService(mConnection);
        }
        chatBound = false;
        stopService(new Intent(this, ChatService.class));
    }

    public void startChatService(View view) {
        chatBound = true;
        Intent intent = new Intent(this, ChatService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        if ((!bluetoothAdapter.isEnabled()) || (!bluetoothAdapter.isDiscovering())) {
            Intent discoverBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverBluetooth.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            startActivityForResult(discoverBluetooth, Constants.REQUEST_DISCOVERABLE_BT);

        }
        else {
            Intent serviceIntent = new Intent(this, ChatService.class);
            startService(serviceIntent);
        }
    }

    private void checkStorageDate() {
        if (songsReceived == null) {
            songsReceived = this.getSharedPreferences(SONGS_PREFERENCES, MODE_PRIVATE);
        }
        Map<String, ?> songsReceivedMap = songsReceived.getAll();
        if (songsReceivedMap.size() > 1) {
            Log.d("MainActivity", "Time to delete songs: " + (Calendar.getInstance().getTimeInMillis() - (Long)songsReceivedMap.get("TIME")));
            if (((Calendar.getInstance().getTimeInMillis() - (Long)songsReceivedMap.get("TIME")) > 43200000)) {
                DELETE = true;
            }

        } else {
            songsReceived.edit().putLong("TIME", Calendar.getInstance().getTimeInMillis()).apply();
        }
    }

    private void deleteStoredSongs() {
        if (songsReceived == null) {
            songsReceived = this.getSharedPreferences(SONGS_PREFERENCES, MODE_PRIVATE);
        }
        Log.d("MainActivity", "Deleting Songs");
        Intent deleteIntent = new Intent(this, SpotifyService.class);
        deleteIntent.putExtra("ACTION", "DELETE_SPOTIFY");
        HashMap<String, String> songsToDelete = (HashMap<String, String>)songsReceived.getAll();
        deleteIntent.putExtra("songs", songsToDelete);
        startService(deleteIntent);
        SharedPreferences.Editor editor = songsReceived.edit();
        editor.clear().commit();
        editor.putLong("TIME", Calendar.getInstance().getTimeInMillis()).apply();
        DELETE = false;
    }


    @Override
    protected void onDestroy() {
        Log.d("MainActivity", "I get destroyed");
        super.onDestroy();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically `handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }



        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == Activity.RESULT_OK) {
            //TODO need to fix 1337 thing
            if ((requestCode == Constants.REQUEST_ENABLE_BT) || (requestCode == 1337)) {
                boolean started = bluetoothAdapter.startDiscovery();
                if (started) {
                    runOnce = true;

                    Intent serviceIntent = new Intent(this, ChatService.class);
                    startService(serviceIntent);
                }
            }
        }
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Intent spotifyIntent = new Intent(this, MySpotifyService.class);
                spotifyIntent.putExtra("ACTION", "MAIN_SPOTIFY_ACTION");
                spotifyIntent.putExtra("type", response.getAccessToken());
                startService(spotifyIntent);
            }

        }
    }

    public void onTracksAdded(List<String> tracks) {
        StringBuilder tracksString = new StringBuilder();
        for (String track : tracks) {
            tracksString.append("spotify:track:").append(track);
        }
        chatService.setTracks(tracksString.toString());
        if (DELETE) {
            deleteStoredSongs();
        }
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            ChatService.LocalBinder binder = (ChatService.LocalBinder) service;
            chatService = binder.getService();
            chatBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            chatBound = false;
        }
    };

    private ServiceConnection spotifyConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MySpotifyService.SpotifyLocalBinder binder = (MySpotifyService.SpotifyLocalBinder) service;
            spotifyService = binder.getService();
            spotifyBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            spotifyBound = false;
        }
    };

    private class MainReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Constants.DEVICE_FOUND)) {
                String device = intent.getStringExtra("name") + ": " + intent.getStringExtra("address");
                if (!devicesFound.contains(device)) {
                    deviceAdapter.add(device);
                    deviceAdapter.notifyDataSetChanged();
                }
            }
            else if (intent.getAction().equals(Constants.DATA_FOUND)) {
                String song = intent.getStringExtra("song").trim();
                if (!songsReceived.contains(song)) {
                    conversationAdapter.add(song);
                    SharedPreferences.Editor editor = songsReceived.edit();
                    editor.putString(song, song);
                    editor.apply();
                    conversationAdapter.notifyDataSetChanged();

                }
            }
            else if(intent.getAction().equals(Constants.TRACKS_ADDED)) {
                onTracksAdded((ArrayList<String>)intent.getSerializableExtra("song"));

            }
        }
    }
}
