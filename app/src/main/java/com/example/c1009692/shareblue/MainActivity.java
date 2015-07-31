package com.example.c1009692.shareblue;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity implements ChatFragment.DeviceListener, SpotifyActivity.SongListener {

    ListView listView;
    final ArrayList<String> devicesFound = new ArrayList<>();
    ArrayAdapter<String> deviceAdapter;
    private Fragment spotifyActivity;
    private Fragment chatFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        spotifyActivity = getSupportFragmentManager().findFragmentByTag("spotify_fragment");
        chatFragment = getSupportFragmentManager().findFragmentByTag("chat_fragment");
        if (spotifyActivity == null) {
            spotifyActivity = new SpotifyActivity();
            getSupportFragmentManager().beginTransaction().add(spotifyActivity, "spotify_fragment").commit();
        }
       if (chatFragment == null) {
           chatFragment= new ChatFragment();
           getSupportFragmentManager().beginTransaction().add(R.id.chat_fragment, chatFragment, "chat_fragment").commit();
        }

        // FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        //transaction.replace(R.id.chat_fragment, chatFragment);
        //transaction.commit();

        listView = (ListView) findViewById(R.id.listview);
        deviceAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, devicesFound);
        listView.setAdapter(deviceAdapter);


    }

    @Override
    protected void onDestroy() {
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
    public void onDeviceFound(BluetoothDevice device) {
        deviceAdapter.add(device.getName() + ": " + device.getAddress());
        deviceAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDataReceived(String dataReceived) {
        ((SpotifyActivity) spotifyActivity).addTracks(dataReceived);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        spotifyActivity.onActivityResult(requestCode, resultCode, intent);
        chatFragment.onActivityResult(requestCode, resultCode, intent);

    }

    @Override
    public void onTracksAdded(List<String> tracks) {
        StringBuffer tracksString = new StringBuffer();
        for (String track : tracks) {
            tracksString.append("spotify:track:" + track);
        }
        ((ChatFragment) chatFragment).setTracks(tracksString.toString());
    }
}
