package com.example.c1009692.shareblue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.widget.Toast;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.Config;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.Player;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.Spotify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
import kaaes.spotify.webapi.android.models.PlaylistTrack;
import kaaes.spotify.webapi.android.models.SavedTrack;
import kaaes.spotify.webapi.android.models.UserPrivate;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Created by Matthew on 20/07/15.
 */
public class SpotifyActivity extends Fragment implements PlayerNotificationCallback, ConnectionStateCallback {

    private static final String CLIENT_ID = "902f1e299879425e8bba02157464cf37";
    private static final String REDIRECT_URL = "share-blue://callback";

    private static final int REQUEST_CODE = 1337;
    private PlaylistSimple playlist;
    private SpotifyService spotify;
    public List<String> tracks;
    final SpotifyUserData userData = new SpotifyUserData();
    private SongListener callback;

    public interface SongListener {
        void onTracksAdded(List<String> tracks);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID, AuthenticationResponse.Type.TOKEN, REDIRECT_URL);
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-modify-public", "playlist-modify-private", "playlist-read-private", "user-library-read"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(getActivity(), REQUEST_CODE, request);


    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            callback = (SongListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnHeadlineSelectedListener");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {

        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                SpotifyApi api = new SpotifyApi();
                api.setAccessToken(response.getAccessToken());
                spotify = api.getService();
                spotify.getMe(new Callback<UserPrivate>() {
                    @Override
                    public void success(UserPrivate user, Response response) {
                        Log.d("SpotifyFragment", user.id);
                        userData.setUsername(user.id);
                        //create a playlist if one does not exist
                        spotify.getPlaylists(userData.getUsername(), new Callback<Pager<PlaylistSimple>>() {
                            @Override
                            public void success(Pager<PlaylistSimple> pager, Response response) {
                                for (PlaylistSimple simpleP : pager.items) {
                                    if (simpleP.name.equals("ShareBlue")) {
                                        playlist = simpleP;
                                    }
                                }
                                if (playlist != null) {

                                } else {
                                    Log.d("SpotifyFragment", "Creating ShareBlue Playlist");
                                    Map<String, Object> playlistDetails = new HashMap<>();
                                    playlistDetails.put("name", "ShareBlue");
                                    playlistDetails.put("public", true);
                                    spotify.createPlaylist(userData.getUsername(), playlistDetails, new Callback<Playlist>() {
                                        @Override
                                        public void success(Playlist playlist, Response response) {
                                            Log.d("SpotifyFragment ", "Successfully created playlist: " + playlist.name);
                                        }

                                        @Override
                                        public void failure(RetrofitError error) {
                                            Log.d("SpotifyFragment ", "Failure creating playlist: " + error.toString());
                                        }
                                    });
                                }
                            }

                            @Override
                            public void failure(RetrofitError error) {
                                Log.d("SpotifyFragment", "Failure getting all playlists " + error.toString());
                            }
                        });
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        Log.d("SpotifyFragment", "Failure getting user: " + error.toString());
                    }
                });


                //get saved tracks
                tracks = new ArrayList<>();
                spotify.getMySavedTracks(new Callback<Pager<SavedTrack>>() {
                    @Override
                    public void success(Pager<SavedTrack> savedTrackPager, Response response) {
                        Log.d("SpotifyFragment", "Successfully got saved tracks");
                        Random rand = new Random();
                        if (savedTrackPager.total > 0) {
                            tracks.add(savedTrackPager.items.get(rand.nextInt(savedTrackPager.items.size())).track.id);
                            Log.d("SpotifyFragment", "Track added: " + tracks.get(0));
                            callback.onTracksAdded(tracks);
                        }
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        Log.d("SpotifyFragment ", "Error getting saved Tracks: " + error.toString());
                    }
                });

            }
        }
    }

    public void addTracks(String tracksToAdd) {
        Map<String, Object> queryParameters = new HashMap<>();
        queryParameters.put("uris", tracksToAdd.trim());
        queryParameters.put("position", 0);
        spotify.addTracksToPlaylist(userData.getUsername(), playlist.id, queryParameters, new HashMap<String, Object>(), new Callback<Pager<PlaylistTrack>>() {
            @Override
            public void success(Pager<PlaylistTrack> playlistTrackPager, Response response) {
                Log.d("SpotifyActivity", "Successfully Added Tracks to Playlist");
            }

            @Override
            public void failure(RetrofitError error) {
                Log.d("SpotifyActivity", "Failure Adding Tracks to Playlist: " + error.toString());
            }
        });
    }

    public List<String> getTracks() {
        return tracks;
    }

    @Override
    public void onLoggedIn() {
        Log.d("SpotifyActivity", "User logged in");
    }

    @Override
    public void onLoggedOut() {
        Log.d("SpotifyActivity", "User logged out");
    }

    @Override
    public void onLoginFailed(Throwable throwable) {
        Log.d("SpotifyActivity", "Login failed");
    }

    @Override
    public void onTemporaryError() {
        Log.d("SpotifyActivity", "Temporary error occurred!");
    }

    @Override
    public void onConnectionMessage(String s) {
        Log.d("SpotifyActivity", "Received connection message: " + s);
    }

    @Override
    public void onPlaybackEvent(EventType eventType, PlayerState playerState) {
        Log.d("SpotifyActivity", "Playback event received: " + eventType.name());
        switch (eventType) {
            default:
                break;
        }
    }

    @Override
    public void onPlaybackError(ErrorType errorType, String s) {
        Log.d("SpotifyActivity", "Playback error received: " + errorType.name());
        switch (errorType) {
            default:
                break;
        }
    }

    @Override
    public void onDestroy() {
        Spotify.destroyPlayer(this);
        Log.d("SPOTIFY", "I get destroyed");
        super.onDestroy();
    }
}
