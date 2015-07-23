package com.example.c1009692.shareblue;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

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

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
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
    private Player myPlayer;
    private SpotifyService spotify;
    private final String[] username = new String[1];
    public List<String> tracks;

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
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        final SpotifyUserData userData = new SpotifyUserData();
        if (requestCode == REQUEST_CODE) {
            AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, intent);
            if (response.getType() == AuthenticationResponse.Type.TOKEN) {
                Config playerConfig = new Config(getActivity(), response.getAccessToken(), CLIENT_ID);
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
                                    spotify.createPlaylist(username[0], playlistDetails, new Callback<Playlist>() {
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
                        if (savedTrackPager.total > 0) {
                            for (SavedTrack st : savedTrackPager.items) {
                                tracks.add(st.track.id);
                                Log.d("SpotifyFragment", "Track added: " + st.track.id);
                                if (tracks.size() == 3) {
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        Log.d("SpotifyFragment ", "Error getting saved Tracks: " + error.toString());
                    }
                });
                /** Plays a song
                 myPlayer = Spotify.getPlayer(playerConfig, this, new Player.InitializationObserver() {

                @Override public void onInitialized(Player player) {
                myPlayer.addConnectionStateCallback(SpotifyActivity.this);
                myPlayer.addPlayerNotificationCallback(SpotifyActivity.this);
                myPlayer.play("spotify:track:1HNkqx9Ahdgi1Ixy2xkKkL");
                }

                @Override public void onError(Throwable throwable) {
                Log.e("SpotifyActivity", "Cound not intialise player: " + throwable.getMessage());
                }
                });
                 */

            }
        }
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
        super.onDestroy();
    }
}
