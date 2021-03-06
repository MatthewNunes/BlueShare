package com.example.c1009692.shareblue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
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
    private Map<String, Integer> genreMap = new HashMap<>();
    ExecutorService executor;
    EchoNestAPI nestAPI;
    public static String SHARED_PREFERENCES = "com.example.nunes.shareblue.Genres";
    public static final int THRESHOLD = 25;
    static final Map<String, String> userD = new HashMap<>();
    public static String USER_PREFERENCES = "com.example.nunes.shareblue.User";
    public interface SongListener {
        void onTracksAdded(List<String> tracks);
    }

    class SpotifyRunnable implements Runnable {

        public void run() {
            final Map<String, String> userPreferences = (Map<String, String>)getActivity().getSharedPreferences(USER_PREFERENCES, Context.MODE_PRIVATE).getAll();
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
                                    SharedPreferences.Editor editor = getActivity().getSharedPreferences(USER_PREFERENCES, Context.MODE_PRIVATE).edit();
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
                                        SharedPreferences.Editor editor = getActivity().getSharedPreferences(USER_PREFERENCES, Context.MODE_PRIVATE).edit();
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
                        callback.onTracksAdded(tracks);
                    }

                    boolean exceptionThrown = false;
                    SharedPreferences sharedPreferences = getActivity().getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE);
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

    class SongGenreRunnable implements Runnable {

        @Override
        public void run() {

        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        nestAPI = new EchoNestAPI("E45RYODK2CMOYIYSH");
        AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(CLIENT_ID, AuthenticationResponse.Type.TOKEN, REDIRECT_URL);
        builder.setScopes(new String[]{"user-read-private", "streaming", "playlist-modify-public", "playlist-modify-private", "playlist-read-private", "user-library-read"});
        AuthenticationRequest request = builder.build();
        AuthenticationClient.openLoginActivity(getActivity(), REQUEST_CODE, request);
        executor = Executors.newSingleThreadExecutor();

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
                SpotifyApi api = new SpotifyApi(executor, executor);
                api.setAccessToken(response.getAccessToken());
                spotify = api.getService();
                executor.execute(new SpotifyRunnable());


            }
        }
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
                    SharedPreferences sharedPreferences = getActivity().getSharedPreferences(SHARED_PREFERENCES, Context.MODE_PRIVATE);
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

    public void invokeEmptyPlaylist(Map<String, ?> tracks) {
        CleanPlaylist playlistRunnable = new CleanPlaylist(tracks);
        executor.execute(playlistRunnable);

    }

    class CleanPlaylist implements Runnable {
        Map<String, ?> tracks;

        public CleanPlaylist(Map<String, ?> tracks) {
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
            Map<String, ?> userPrefs = getActivity().getSharedPreferences(USER_PREFERENCES, Context.MODE_PRIVATE).getAll();
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

    class GenreThread extends Thread {
        EchoNestAPI nestAPI;

        public GenreThread() {
            nestAPI = new EchoNestAPI("E45RYODK2CMOYIYSH");
        }

        @Override
        public void run() {
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
                        callback.onTracksAdded(tracks);
                    }
                    boolean exceptionThrown = false;
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

                }

                @Override
                public void failure(RetrofitError error) {
                    Log.d("SpotifyFragment ", "Error getting saved Tracks: " + error.toString());
                }
            });

        }
    }
}