package com.example.c1009692.shareblue;

import android.app.Activity;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.echonest.api.v4.Artist;
import com.echonest.api.v4.EchoNestAPI;
import com.echonest.api.v4.EchoNestException;
import com.echonest.api.v4.Term;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;
import com.spotify.sdk.android.player.ConnectionStateCallback;
import com.spotify.sdk.android.player.PlayerNotificationCallback;
import com.spotify.sdk.android.player.PlayerState;
import com.spotify.sdk.android.player.Spotify;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.SpotifyService;
import kaaes.spotify.webapi.android.models.ArtistSimple;
import kaaes.spotify.webapi.android.models.Pager;
import kaaes.spotify.webapi.android.models.Playlist;
import kaaes.spotify.webapi.android.models.PlaylistSimple;
import kaaes.spotify.webapi.android.models.PlaylistTrack;
import kaaes.spotify.webapi.android.models.SavedTrack;
import kaaes.spotify.webapi.android.models.SnapshotId;
import kaaes.spotify.webapi.android.models.Track;
import kaaes.spotify.webapi.android.models.TrackToRemove;
import kaaes.spotify.webapi.android.models.TracksToRemove;
import kaaes.spotify.webapi.android.models.UserPrivate;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Created by Matthew on 08/08/15.
 */
public class MySpotifyService extends IntentService implements PlayerNotificationCallback, ConnectionStateCallback {

    private static final String CLIENT_ID = "902f1e299879425e8bba02157464cf37";
    private static final String REDIRECT_URL = "share-blue://callback";

    private static final int REQUEST_CODE = 1337;
    private PlaylistSimple playlist;
    private SpotifyService spotify;
    public ArrayList<String> tracks;
    final SpotifyUserData userData = new SpotifyUserData();
    private Map<String, Integer> genreMap = new HashMap<>();
    ExecutorService executor;
    EchoNestAPI nestAPI;
    public static String SHARED_PREFERENCES = "com.example.nunes.shareblue.Genres";
    public static final int THRESHOLD = 25;
    static final Map<String, String> userD = new HashMap<>();
    public static String USER_PREFERENCES = "com.example.nunes.shareblue.User";

    //TODO make spotify handle this
    public static String SONGS_RECEIVED_PREFERENCES = "com.example.nunes.shareblue.SongsReceived";
    private final IBinder mBinder = new SpotifyLocalBinder();

    public MySpotifyService() {
        super("MySpotifyService");
    }


    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("SpotifyFragment", "onHandleIntent called");
        Log.d("SpotifyFragment", "ACTION: " + intent.getStringExtra("ACTION"));
        if (intent.getStringExtra("ACTION").equals("MAIN_SPOTIFY_ACTION")) {
            onMainActivityResult(intent.getStringExtra("type"));
        } else if (intent.getStringExtra("ACTION").equals("DELETE_SPOTIFY")) {
            invokeEmptyPlaylist((HashMap<String, String>)intent.getSerializableExtra("songs"));
        } else if (intent.getStringExtra("ACTION").equals("SONG_RECEIVED")) {
            addTracks(intent.getStringExtra("song"));
        }

    }

    public class SpotifyLocalBinder extends Binder {
        MySpotifyService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MySpotifyService.this;
        }
    }

    class SpotifyRunnable implements Runnable {

        @Override
        public void run() {
            final Map<String, String> userPreferences = (Map<String, String>)getSharedPreferences(USER_PREFERENCES, Context.MODE_PRIVATE).getAll();
            spotify.getMe(new retrofit.Callback<UserPrivate>() {
                @Override
                public void success(UserPrivate user, Response response) {
                    Log.d("SpotifyFragment", user.id);
                    userData.setUsername(user.id);
                    userPreferences.put("username", user.id);
                    //create a playlist if one does not exist
                    spotify.getPlaylists(userData.getUsername(), new retrofit.Callback<Pager<PlaylistSimple>>() {
                        @Override
                        public void success(Pager<PlaylistSimple> pager, Response response) {
                            for (PlaylistSimple simpleP : pager.items) {
                                if (simpleP.name.equals("ShareBlue")) {
                                    playlist = simpleP;
                                    userData.setPlaylistID(playlist.id);
                                    userPreferences.put("playlist", playlist.id);
                                    SharedPreferences.Editor editor = getSharedPreferences(USER_PREFERENCES, Context.MODE_PRIVATE).edit();
                                    for (Map.Entry<String, String> entry : userPreferences.entrySet()) {
                                        editor.putString(entry.getKey(), entry.getValue());
                                    }
                                    editor.apply();
                                }
                            }
                            if (playlist != null) {

                            } else {
                                Log.d("SpotifyFragment", "Creating ShareBlue Playlist");
                                Map<String, Object> playlistDetails = new HashMap<>();
                                playlistDetails.put("name", "ShareBlue");
                                playlistDetails.put("public", true);
                                spotify.createPlaylist(userData.getUsername(), playlistDetails, new retrofit.Callback<Playlist>() {
                                    @Override
                                    public void success(Playlist playlist, Response response) {
                                        Log.d("SpotifyFragment ", "Successfully created playlist: " + playlist.name);
                                        userData.setPlaylistID(playlist.id);
                                        userPreferences.put("playlist", playlist.id);
                                        SharedPreferences.Editor editor = getSharedPreferences(USER_PREFERENCES, Context.MODE_PRIVATE).edit();
                                        for (Map.Entry<String, String> entry : userPreferences.entrySet()) {
                                            editor.putString(entry.getKey(), entry.getValue());
                                        }
                                        editor.apply();
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
            tracks = new ArrayList<>();

            Map<String, Object> savedTrackOptions = new HashMap<>();
            savedTrackOptions.put("limit", "50");
            spotify.getMySavedTracks(savedTrackOptions, new retrofit.Callback<Pager<SavedTrack>>() {
                @Override
                public void success(Pager<SavedTrack> savedTrackPager, Response response) {
                    Log.d("SpotifyFragment", "Successfully got saved tracks");
                    Random rand = new Random();
                    if (savedTrackPager.total > 0) {
                        Log.d("SpotifyFragment", "Saved Tracks Returned: " + savedTrackPager.total);
                        tracks.add(savedTrackPager.items.get(rand.nextInt(savedTrackPager.items.size())).track.id);
                        Log.d("SpotifyFragment", "Track added: " + tracks.get(0));
                        Log.d("SpotifyFragment", savedTrackPager.items.get(0).track.type);
                        Intent tracksAddedIntent = new Intent(Constants.TRACKS_ADDED);
                        tracksAddedIntent.putExtra("song", tracks);
                        sendBroadcast(tracksAddedIntent);
                    }

                    boolean exceptionThrown = false;
                    SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE);
                    Map<String, ?> sharedPrefsMap = sharedPreferences.getAll();
                    Log.d("SpotifyFragment", "Shared Preferences size: " + sharedPrefsMap.size());
                    if (sharedPrefsMap.size() == 0) {

                        for (SavedTrack track : savedTrackPager.items) {
                            int i = 0;
                            while (i < track.track.artists.size()) {
                                ArtistSimple artist = track.track.artists.get(i);
                                List<Artist> artistsFound = null;
                                try {
                                    artistsFound = nestAPI.searchArtists(artist.name);
                                    exceptionThrown = false;
                                    i += 1;
                                } catch (EchoNestException e) {
                                    if (e.getCode() == 3) {
                                        exceptionThrown = true;
                                        try {
                                            Thread.sleep(60000);
                                        } catch (InterruptedException e1) {
                                            e1.printStackTrace();
                                        }
                                        Log.d("SpotifyFragment", "Error finding artist! " + e.getCode());
                                        e.printStackTrace();
                                    }
                                }
                                if ((!exceptionThrown) && (artistsFound != null) && (artistsFound.size() > 0)) {
                                    Artist foundArtist = artistsFound.get(0);
                                    try {
                                        List<Term> terms = foundArtist.getTerms();
                                        for (Term term : terms) {
                                            Log.d("SpotifyFragment Term", term.getName());
                                            Integer count = genreMap.get(term.getName());
                                            if (count == null) {
                                                genreMap.put(term.getName(), 1);
                                            } else {
                                                genreMap.put(term.getName(), count + 1);
                                            }
                                        }
                                    } catch (EchoNestException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    Log.d("SpotifyFragment", "Artist not found: " + artist.name);
                                }
                            }
                        }
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        for (Map.Entry<String, Integer> entry : genreMap.entrySet()) {
                            editor.putInt(entry.getKey(), entry.getValue());
                        }
                        editor.apply();
                    }
                    for (Map.Entry<String, ?> entry : sharedPrefsMap.entrySet()) {
                        Log.d("SpotifyFragment", "SharedPreferences: " + entry.getKey() + ": " + entry.getValue());
                    }

                }

                @Override
                public void failure(RetrofitError error) {
                    Log.d("SpotifyFragment ", "Error getting saved Tracks: " + error.toString());
                }
            });

        }


    }

    @Override
    public void onCreate() {
        super.onCreate();
        nestAPI = new EchoNestAPI("E45RYODK2CMOYIYSH");
        executor = Executors.newSingleThreadExecutor();

    }
/**
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
*/

    public void onMainActivityResult(String accessToken) {
        SpotifyApi api = new SpotifyApi(executor, executor);
        api.setAccessToken(accessToken);
        spotify = api.getService();
        executor.execute(new SpotifyRunnable());

    }

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void addTracks(String tracksToAdd) {
        TrackAdderRunnable runnable = new TrackAdderRunnable(tracksToAdd);
        executor.execute(runnable);
    }


    public class TrackAdderRunnable implements Runnable {

        String tracksToAdd;

        public TrackAdderRunnable(String tracksToAdd) {
            this.tracksToAdd = tracksToAdd;
        }


        public void run() {
            final Map<String, Object> queryParameters = new HashMap<>();
            queryParameters.put("uris", tracksToAdd.trim());
            queryParameters.put("position", 0);
            spotify.getTrack(tracksToAdd.trim().split(":")[2], new Callback<Track>() {
                @Override
                public void success(Track track, Response response) {
                    SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE);
                    Map<String, ?> preferencesMap = sharedPreferences.getAll();
                    boolean found = false;
                    List<ArtistSimple> artists = track.artists;
                    for (ArtistSimple artist : artists) {
                        try {
                            Artist nestArtist = nestAPI.searchArtists(artist.name).get(0);
                            List<Term> genres = nestArtist.getTerms();
                            for (Term genre : genres) {
                                if ((preferencesMap.containsKey(genre.getName())) && ((Integer) preferencesMap.get(genre.getName()) > THRESHOLD)) {
                                    found = true;
                                    Log.d("SpotifyFragment", "Genre Matched! " + genre.getName());
                                    break;
                                }
                            }
                        } catch (EchoNestException e) {
                            e.printStackTrace();
                        }
                    }
                    if (found) {
                        Log.d("SpotifyFragment", "Track meets genre requirements");
                        spotify.addTracksToPlaylist(userData.getUsername(), playlist.id, queryParameters, new HashMap<String, Object>(), new Callback<Pager<PlaylistTrack>>() {
                            @Override
                            public void success(Pager<PlaylistTrack> playlistTrackPager, Response response) {
                                Log.d("SpotifyActivity", "Successfully Added Tracks to Playlist");
                            }

                            @Override
                            public void failure(RetrofitError error) {
                                Log.d("SpotifyActivity", "Failure Adding Tracks to Playlist: " + error.getMessage());
                            }
                        });
                    } else {
                        Log.d("SpotifyFragment", "Track not added to playlist");
                    }
                }

                @Override
                public void failure(RetrofitError error) {
                    Log.d("SpotifyFragment", "Track not added to playlist " + error.getMessage());
                }
            });

        }
    }

    public void invokeEmptyPlaylist(HashMap<String, String> tracks) {
        CleanPlaylist playlistRunnable = new CleanPlaylist(tracks);
        executor.execute(playlistRunnable);

    }

    class CleanPlaylist implements Runnable {
        HashMap<String, String> tracks;

        public CleanPlaylist(HashMap<String, String> tracks) {
            this.tracks = tracks;
        }

        public void run() {
            tracks.remove("TIME");
            TracksToRemove tracksToRemove = new TracksToRemove();
            List<TrackToRemove> removalList = new ArrayList<>();
            for (String track : tracks.keySet()) {
                TrackToRemove removeMe = new TrackToRemove();
                removeMe.uri = track;
                removalList.add(removeMe);
            }
            tracksToRemove.tracks = removalList;
            Map<String, ?> userPrefs = getSharedPreferences(USER_PREFERENCES, Context.MODE_PRIVATE).getAll();
            spotify.removeTracksFromPlaylist((String)userPrefs.get("username"), (String)userPrefs.get("playlist"), tracksToRemove, new Callback<SnapshotId>() {
                @Override
                public void success(SnapshotId snapshotId, Response response) {
                    Log.d("SpotifyFragment", "Successfully removed tracks");
                }

                @Override
                public void failure(RetrofitError error) {
                    Log.d("SpotifyFragment", "Error removing tracks " + error.getMessage());
                }
            });
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
    public void onPlaybackEvent(PlayerNotificationCallback.EventType eventType, PlayerState playerState) {
        Log.d("SpotifyActivity", "Playback event received: " + eventType.name());
        switch (eventType) {
            default:
                break;
        }
    }

    @Override
    public void onPlaybackError(PlayerNotificationCallback.ErrorType errorType, String s) {
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
