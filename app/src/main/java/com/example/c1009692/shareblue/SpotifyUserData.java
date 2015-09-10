package com.example.c1009692.shareblue;

import android.util.Log;

/**
 * Created by Matthew on 22/07/15.
 */
public class SpotifyUserData {
    private String username;
    private String playlistID;

    public String getPlaylistID() {
        return playlistID;
    }

    public void setPlaylistID(String playlistID) {
        this.playlistID = playlistID;
    }

    public void setUsername(String name) {
        username = name;
        Log.d("SpotifyUserData", "USERNAME: " + name);
    }
    public String getUsername() {
        return username;
    }

}
