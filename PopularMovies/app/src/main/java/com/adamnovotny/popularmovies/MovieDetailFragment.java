/**
 * Copyright (C) 2016 Adam Novotny
 */

package com.adamnovotny.popularmovies;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.adamnovotny.popularmovies.data.MovieContract;
import com.adamnovotny.popularmovies.data.MovieDbHelper;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;


/**
 * View only class showing movie details
 * by updating appropriate layout views
 */
public class MovieDetailFragment extends Fragment implements GetStringDataInterface {
    private final String LOG_TAG = MovieDetailFragment.class.getSimpleName();
    private boolean dataReceived = true;
    private Context mContext;
    private MovieDbHelper db;
    private boolean isFavorite;
    private String id;
    private String title;
    private String image;
    private String overview;
    private String vote;
    private String release;
    private ArrayList<String> videoAL;
    private ArrayList<String> reviewAL;
    View mainView;
    // videos
    private RecyclerView videoRecyclerView;
    private VideoAdapter mVideoAdapter;
    private boolean mVideoDataReceived = false;
    // reviews
    private RecyclerView reviewRecyclerView;
    private ReviewAdapter mReviewAdapter;
    private boolean mReviewDataReceived = false;
    private Button privateBtn;

    public MovieDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // get bundle
        Bundle bdl = getArguments();
        if (bdl == null) {
            dataReceived = false;
        }
        // if screen is rotated, get data from instance
        else if(savedInstanceState != null &&
                savedInstanceState.containsKey("videos")) {
            this.id = savedInstanceState.getString("id");
            this.title = savedInstanceState.getString("title");
            this.image = savedInstanceState.getString("image");
            this.overview = savedInstanceState.getString("overview");
            this.vote = savedInstanceState.getString("vote");
            this.release = savedInstanceState.getString("release");
            mContext = getContext();
            db = new MovieDbHelper(mContext);
            isFavorite = isFavorite(mContext, this.id);
            videoAL = savedInstanceState.getStringArrayList("videos");
            reviewAL = savedInstanceState.getStringArrayList("reviews");
            mVideoDataReceived = true;
            mReviewDataReceived = true;
        }
        // case new launch
        else {
            this.id = bdl.getString("id");
            this.title = bdl.getString("title");
            this.image = bdl.getString("image");
            this.overview = bdl.getString("overview");
            this.vote = bdl.getString("vote");
            this.release = bdl.getString("release");
            mContext = getContext();
            db = new MovieDbHelper(mContext);
            isFavorite = isFavorite(mContext, this.id);
            // get reviews
            GetReviewsAsync reviewAsync = new GetReviewsAsync(this);
            reviewAsync.execute(id);
            // get videos
            GetVideoAsync videoAsync = new GetVideoAsync(this);
            videoAsync.execute(id);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mainView = inflater.inflate(R.layout.fragment_movie_detail, container, false);
        if (dataReceived) {
            setViews(mainView);
        }
        else {
            // in case of network issue, use dummy data in fragment
            placeDummyData();
        }
        return mainView;
    }

    /**
     *
     * @param outState is generated when fragment is killed.
     * Ddata is restored using savedInstanceState in onCreate()
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("id", this.id);
        outState.putString("title", this.title);
        outState.putString("image", this.image);
        outState.putString("overview", this.overview);
        outState.putString("vote", this.vote);
        outState.putString("release", this.release);
        outState.putStringArrayList("videos", videoAL);
        outState.putStringArrayList("reviews", reviewAL);
    }

    // Updates all screen views
    private void setViews(View mainView) {
        TextView title = (TextView) mainView.findViewById(R.id.title);
        title.setText(this.title);
        ImageView img = (ImageView) mainView.findViewById(R.id.movie_image);
        String baseUrl = "http://image.tmdb.org/t/p/w185/";
        Picasso.with(getContext()).load(baseUrl + this.image).into(img);
        TextView release = (TextView) mainView.findViewById(R.id.release);
        release.setText(this.release);
        TextView vote = (TextView) mainView.findViewById(R.id.vote);
        vote.setText(this.vote);
        TextView overview = (TextView) mainView.findViewById(R.id.overview);
        overview.setText(this.overview);

        // hook favorite button to db
        buildFavoriteDbHook(mainView);
        // add videos
        if (mVideoDataReceived) {
            buildRecyclerVideo(mainView);
        }
        // add reviews
        if (mReviewDataReceived) {
            buildRecyclerReview(mainView);
        }
    }

    // Set up for RecyclerView containing videos
    private void buildRecyclerVideo(View mainView) {
        videoRecyclerView =
                (RecyclerView) mainView.findViewById(R.id.recycler_movies);
        mVideoAdapter = new VideoAdapter(this.videoAL, getContext());
        RecyclerView.LayoutManager mLayoutManager =
                new LinearLayoutManager(getContext());
        videoRecyclerView.setLayoutManager(mLayoutManager);
        videoRecyclerView.setAdapter(mVideoAdapter);
    }

    // Set up for RecyclerView containing reviews
    private void buildRecyclerReview(View mainView) {
        reviewRecyclerView =
                (RecyclerView) mainView.findViewById(R.id.recycler_reviews);
        mReviewAdapter = new ReviewAdapter(this.reviewAL, getContext());
        RecyclerView.LayoutManager mLayoutManager =
                new LinearLayoutManager(getContext());
        reviewRecyclerView.setLayoutManager(mLayoutManager);
        reviewRecyclerView.setAdapter(mReviewAdapter);
    }

    // Set up for Favorite button
    private void buildFavoriteDbHook(View mainView) {
        privateBtn =
                (Button) mainView.findViewById(R.id.favorite_button);
        String btnText = getResources().getString(R.string.set_favorite);
        if (isFavorite) {
            btnText = getResources().getString(R.string.remove_favorite);
        }
        privateBtn.setText(btnText);
        privateBtn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isFavorite) {
                    ContentResolver resolver = mContext.getContentResolver();
                    String delStr = MovieContract.MovieEntry.COLUMN_MOVIE_ID + "=" + id;
                    int rows = resolver.delete(MovieContract.MovieEntry.CONTENT_URI, delStr, null);
                    isFavorite = false;
                    privateBtn.setText(
                            getResources().getString(R.string.set_favorite));
                }
                else {
                    ContentResolver resolver = mContext.getContentResolver();
                    ContentValues values = new ContentValues();
                    values.put(MovieContract.MovieEntry.COLUMN_MOVIE_ID, id);
                    Uri uri = resolver.insert(MovieContract.MovieEntry.CONTENT_URI, values);
                    isFavorite = true;
                    privateBtn.setText(
                            getResources().getString(R.string.remove_favorite));
                }
            }
        });
    }

    /**
     * Dummy data visible until user selects a movie
     */
    private void placeDummyData() {
        String warningText = "No movie selected";
        TextView title = (TextView) mainView.findViewById(R.id.title);
        title.setText(warningText);
        TextView release = (TextView) mainView.findViewById(R.id.release);
        release.setText(warningText);
        TextView vote = (TextView) mainView.findViewById(R.id.vote);
        vote.setText(warningText);
        privateBtn =
                (Button) mainView.findViewById(R.id.favorite_button);
        privateBtn.setText(warningText);
    }

    /**
     * Helper method to determine if a movie with id is favorite
     * @param context
     * @param id movie
     * @return true if the movie favorite
     */
    public static boolean isFavorite(Context context, String id) {
        String selection = MovieContract.MovieEntry.COLUMN_MOVIE_ID + "=" + id;
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(
                MovieContract.MovieEntry.CONTENT_URI, null, selection, null, null);
        if (cursor.getCount() > 0) {
            return true;
        }
        return false;
    }

    /**
     * Async task listener
     * @param source is the listener class name
     * @param data ArrayList<String> of contents
     */
    public void onTaskCompleted(String source, ArrayList<String> data) {
        if (source.equals(GetReviewsAsync.class.getSimpleName())) {
            reviewAL = data;
            mReviewDataReceived = true;
            Log.i(LOG_TAG, "Review data received");
        }
        else if (source.equals(GetVideoAsync.class.getSimpleName())) {
            videoAL = data;
            mVideoDataReceived = true;
            Log.i(LOG_TAG, "Video data received");
        }
        if (mVideoDataReceived && mReviewDataReceived && isAdded()) {
            setViews(mainView);
        }
    }
}
