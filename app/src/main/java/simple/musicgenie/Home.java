package simple.musicgenie;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.arlib.floatingsearchview.FloatingSearchView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import simple.music.MusicGenieMediaPlayer;

public class Home extends AppCompatActivity {

    private RecyclerView mRecyclerView;
    private StaggeredGridLayoutManager layoutManager;
    private ResulstsRecyclerAdapter mRecyclerAdapter;
    private ProgressDialog progressDialog;
    private HashMap<String, ArrayList<BaseSong>> songMap;
    private CentralDataRepository repository;
    private FloatingSearchView searchView;
    private SwipeRefreshLayout swipeRefressLayout;
    private Handler mHandler;
    private SharedPrefrenceUtils utils;
    private ProgressBar progressBar;
    private TextView progressBarMsgPanel;
    private StreamFragment streamDialog;
    private boolean isStreaming;
    private MediaPlayer mPlayer;
    static SeekBar streamSeeker;
    static TextView currentTrackPosition;
    static TextView totalTrackPosition;
    static TextView cancelStreamingBtn;
    static TextView streamingItemTitle;
    private boolean prepared = false;
    private StreamUriBroadcastReceiver receiver;
    private boolean mReceiverRegistered = false;
    private int mDuration;
    private int mCurrentPosition;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        configureStorageDirectory(savedInstanceState);
        instantiateViews();
        redgisterAdapter();
        subscribeToTaskAddListener();

        utils = SharedPrefrenceUtils.getInstance(this);

        if (!utils.getFirstPageLoadedStatus()) {
            invokeAction(Constants.ACTION_TYPE_FIRST_LOAD);
            utils.setFirstPageLoadedStatus(true);
        } else {
            invokeAction(Constants.ACTION_TYPE_RESUME);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mReceiverRegistered) {
            registerForBroadcastListen(this);
            L.m("Home", "Register Receiver");
        }
    }

    @Override
    protected void onStop() {
        L.m("Home", "onStop()");
        super.onStop();
        // dismiss dialog befor stop to rescue from leakage
//        if (streamDialog != null) {
////            streamDialog.hide();
//            streamDialog.dismiss();
//        }

        if (mReceiverRegistered) {
            unRegisterBroadcast();
            L.m("Home", "UnRegister Receiver");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        invokeAction(Constants.ACTION_TYPE_RESUME);
    }

    private void redgisterAdapter() {

        repository = CentralDataRepository.getInstance(this);

        repository.registerForDataLoadListener(new CentralDataRepository.DataReadyToSubmitListener() {
            @Override
            public void onDataSubmit(SectionModel item) {


                // check if item is empty
                if (item.getList() != null) {
                    if (item.getList().size() == 0 && mRecyclerAdapter.getItemCount() == 0) {
                        // hide the recycler view and Show Message
                        mRecyclerView.setVisibility(RecyclerView.GONE);
                        progressBar.setVisibility(View.GONE);
                        progressBarMsgPanel.setVisibility(View.VISIBLE);
                        progressBarMsgPanel.setText("Troubling Getting Data......\nCheck Your Working Data Connection");
                        progressBarMsgPanel.setText("hello");

                    }
                }


                mRecyclerView.setVisibility(RecyclerView.VISIBLE);
                progressBar.setVisibility(View.GONE);
                progressBarMsgPanel.setVisibility(View.GONE);

                mRecyclerAdapter.enque(item);

            }
        });
    }

    /**
     * @param actionType type of action to invoke
     *                   todo: must- attach Adapters
     */
    public void invokeAction(int actionType) {

        // regardless of any action Type
        // make Recycler View Invisible and progress bar visible
        mRecyclerView.setVisibility(RecyclerView.GONE);
        progressBar.setVisibility(View.VISIBLE);
        progressBarMsgPanel.setVisibility(View.VISIBLE);


        switch (actionType) {

            case Constants.ACTION_TYPE_FIRST_LOAD:
                //  showProgress("Presenting Trending...");
                if(!ConnectivityUtils.getInstance(this).isConnectedToNet()){
                    mRecyclerView.setVisibility(RecyclerView.GONE);
                    progressBar.setVisibility(View.GONE);
                    progressBarMsgPanel.setVisibility(View.VISIBLE);
                    L.m("Home","setting msg");Toast.makeText(this,"hello",Toast.LENGTH_SHORT).show();
                    progressBarMsgPanel.setText("Troubling Getting DataCheck Your Working Data Connection");
                    return;
                }

                progressBarMsgPanel.setText("Loading Trending");

                try {
                    repository.submitAction(CentralDataRepository.FLAG_FIRST_LOAD, new CentralDataRepository.ActionCompletedListener() {
                        @Override
                        public void onActionCompleted() {
                            hideProgress();
                        }
                    });
                } catch (CentralDataRepository.InvalidCallbackException e) {
                    e.printStackTrace();
                }

                break;
            case Constants.ACTION_TYPE_RESUME:
                //showProgress("Presenting Your Items");

                progressBarMsgPanel.setText("Resuming Contents");

                try {
                    repository.submitAction(CentralDataRepository.FLAG_RESTORE, new CentralDataRepository.ActionCompletedListener() {
                        @Override
                        public void onActionCompleted() {
                            hideProgress();
                        }
                    });
                } catch (CentralDataRepository.InvalidCallbackException e) {
                    e.printStackTrace();
                }

                break;

            case Constants.ACTION_TYPE_REFRESS:

                if(!ConnectivityUtils.getInstance(this).isConnectedToNet()){
                    mRecyclerView.setVisibility(RecyclerView.GONE);
                    progressBar.setVisibility(View.GONE);
                    progressBarMsgPanel.setVisibility(View.VISIBLE);
                    progressBarMsgPanel.setText("Troubling Getting Data......\nCheck Your Working Data Connection");
                    return;
                }// or continue the same
                progressBarMsgPanel.setText("Refressing Content");

                try {
                    repository.submitAction(CentralDataRepository.FLAG_REFRESS, new CentralDataRepository.ActionCompletedListener() {
                        @Override
                        public void onActionCompleted() {
                            // disable refressing
                            L.m("Callback[Refress] ", "Refressed");
                            if (swipeRefressLayout.isRefreshing()) {
                                swipeRefressLayout.setRefreshing(false);
                                swipeRefressLayout.setEnabled(true);
                            }
                        }
                    });
                } catch (CentralDataRepository.InvalidCallbackException e) {
                    e.printStackTrace();
                }

                break;


            case Constants.ACTION_TYPE_SEARCH:
                //showProgress("Searching Item");
                String searchQuery = SharedPrefrenceUtils.getInstance(this).getLastSearchTerm();
                progressBarMsgPanel.setText("Searching For.. " + searchQuery);

                try {
                    repository.submitAction(CentralDataRepository.FLAG_SEARCH, new CentralDataRepository.ActionCompletedListener() {
                        @Override
                        public void onActionCompleted() {
                            hideProgress();
                        }
                    });
                } catch (CentralDataRepository.InvalidCallbackException e) {
                    e.printStackTrace();
                }
                break;
        }

    }

    private void showProgress(String msg) {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage(msg);
        progressDialog.show();
    }

    private void hideProgress() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    private void subscribeToTaskAddListener() {
        ResulstsRecyclerAdapter.getInstance(this).setOnTaskAddListener(new TaskAddListener() {
            @Override
            public void onTaskTapped() {
                L.m("Home subscribeToTaskAddListener() ", "callback: task tapped");
                progressDialog = new ProgressDialog(Home.this);
                progressDialog.setMessage("Requesting Your Stuff..");
                progressDialog.setCancelable(false);
                progressDialog.show();
            }

            @Override
            public void onTaskAddedToQueue(String task_info) {
                L.m("Home subscribeToTaskAddListener() ", "callback: task added to download queue");
                progressDialog.dismiss();
                Toast.makeText(Home.this, task_info + " Added To Download", Toast.LENGTH_LONG).show();
                //TODO: navigate to DownloadsActivity
            }
        });
    }

    private int screenMode() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        float yInches = metrics.heightPixels / metrics.ydpi;
        float xInches = metrics.widthPixels / metrics.xdpi;

        double diagonal = Math.sqrt(yInches * yInches + xInches * xInches);
        if (diagonal > 6.5) {
            return Constants.SCREEN_MODE_TABLET;
        } else {
            return Constants.SCREEN_MODE_MOBILE;
        }
    }

    private void plugAdapter() {
        mRecyclerAdapter.setOrientation(getOrientation());
        mRecyclerAdapter.setScreenMode(screenMode());
        mRecyclerView.setAdapter(mRecyclerAdapter);
        subscribeForStreamOption(mRecyclerAdapter);
    }

    private void instantiateViews() {

        int maxCols = (isPortrait(getOrientation())) ? ((screenMode() == Constants.SCREEN_MODE_MOBILE) ? 2 : 3) : 4;
        swipeRefressLayout = (SwipeRefreshLayout) findViewById(R.id.content_refresser);
        mRecyclerView = (RecyclerView) findViewById(R.id.trendingRecylerView);
        layoutManager = new StaggeredGridLayoutManager(maxCols, 1);
        mRecyclerAdapter = ResulstsRecyclerAdapter.getInstance(this);
        mRecyclerView.setLayoutManager(layoutManager);
        progressBar = (ProgressBar) findViewById(R.id.homeProgressBar);
        progressBarMsgPanel = (TextView) findViewById(R.id.ProgressMsgPanel);
        swipeRefressLayout.setColorSchemeColors(getResources().getColor(R.color.PrimaryColor), Color.WHITE);
        swipeRefressLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refressContent();
            }
        });

        plugAdapter();
        setSearchView();
    }

    private boolean isPortrait(int orientation) {
        return orientation % 2 == 0;
    }

    private void refressContent() {

        if (ConnectivityUtils.getInstance(this).isConnectedToNet()) {
            invokeAction(Constants.ACTION_TYPE_REFRESS);
            swipeRefressLayout.setRefreshing(true);
            swipeRefressLayout.setEnabled(false);

        } else {
            Snackbar.make(swipeRefressLayout, "No Connectivity !!", Snackbar.LENGTH_SHORT).show();
            swipeRefressLayout.setRefreshing(false);
            swipeRefressLayout.setVisibility(View.VISIBLE);
        }

    }

    public void setSearchView() {
        searchView = (FloatingSearchView) findViewById(R.id.floating_search_view);
        searchView.setOnQueryChangeListener(new FloatingSearchView.OnQueryChangeListener() {


            @Override
            public void onSearchTextChanged(String oldText, String newText) {
                if (!oldText.equals("") && newText.equals("")) {
                    searchView.clearSuggestions();
                } else {

                    searchView.showProgress();
                    SearchSuggestionHelper.getInstance(Home.this).findSuggestion(newText,
                            new SearchSuggestionHelper.OnFindSuggestionListener() {
                                @Override
                                public void onResult(ArrayList<SearchSuggestion> list) {
                                    searchView.swapSuggestions(list);
                                    searchView.hideProgress();
                                }
                            });
                }
            }
        });


        searchView.setOnSearchListener(new FloatingSearchView.OnSearchListener() {
            @Override
            public void onSuggestionClicked(com.arlib.floatingsearchview.suggestions.model.SearchSuggestion searchSuggestion) {
                //stop futhur suggestion requests

                SearchSuggestionHelper.getInstance(Home.this).cancelFuthurRequestUntilQueryChange();

                fireSearch(searchSuggestion.getBody());
            }

            @Override
            public void onSearchAction(String query) {
                fireSearch(query);
            }
        });

        searchView.setOnMenuItemClickListener(new FloatingSearchView.OnMenuItemClickListener() {
            @Override
            public void onActionMenuItemSelected(MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.action_settings) {
                    Intent i = new Intent(Home.this, UserPreferenceSetting.class);
                    startActivity(i);
                }
                if (id == R.id.action_downloads) {
                    Intent i = new Intent(Home.this, DowloadsActivity.class);
                    startActivity(i);
                }
            }
        });


    }

    private void fireSearch(String query) {

        SharedPrefrenceUtils.getInstance(this).setLastSearchTerm(query);
        L.m("Home", " invoking action search");
        invokeAction(Constants.ACTION_TYPE_SEARCH);

    }

    private void configureStorageDirectory(Bundle savedInstance) {

        if (savedInstance == null) {
            L.m("Home configureStorageDirectory()", "making dirs");
            AppConfig.getInstance(this).configureDevice();
        }
    }

    private void subscribeForStreamOption(ResulstsRecyclerAdapter mRecyclerAdapter) {
        mRecyclerAdapter.setOnStreamingSourceAvailable(new ResulstsRecyclerAdapter.OnStreamingSourceAvailableListener() {
            @Override
            public void onPrepared(String uri) {

                if (progressDialog != null)
                    progressDialog.dismiss();

            }

            @Override
            public void optioned() {

                progressDialog = new ProgressDialog(Home.this);
                progressDialog.setMessage("Requesting Audio For You....");
                progressDialog.show();

            }
        });

    }

    private int getOrientation() {
        return getWindowManager().getDefaultDisplay().getOrientation();
    }

    class Player extends AsyncTask<String, Void, Boolean> {

        Context context;
        private ProgressDialog progressDialog;

        public Player(Context context) {
            this.context = context;
        }

        public Player() {
            progressDialog = new ProgressDialog(Home.this);


        }

        @Override
        protected Boolean doInBackground(String... strings) {

            try {
                mPlayer = MusicGenieMediaPlayer
                        .getInstance(Home.this)
                        .setURI(strings[0])
                        .getPlayer();

                // mPlayer.setScreenOnWhilePlaying(true);
                L.m("Home", "playing");
                mPlayer.start();

                while (mPlayer.isPlaying()) {

                    Message msg = Message.obtain();
                    msg.arg1 = mPlayer.getCurrentPosition();
                    msg.arg2 = mPlayer.getDuration();
                    if (mHandler != null)
                        mHandler.sendMessage(msg);

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                }
                mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mediaPlayer) {
                        isStreaming = false;
                    }
                });
                mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                        isStreaming = false;
                        return true;
                    }
                });


            } catch (IllegalArgumentException e) {
                // TODO Auto-generated catch block
                Log.d("IllegarArgument", e.getMessage());
                prepared = false;
                e.printStackTrace();
            } catch (SecurityException e) {
                // TODO Auto-generated catch block
                prepared = false;
                e.printStackTrace();
            } catch (IllegalStateException e) {
                // TODO Auto-generated catch block
                prepared = false;
                e.printStackTrace();
            }


            return prepared;
        }
//
//        private void publishStreamingProgress(int currentPosition, int duration) {
//
//
//  //          L.m("Home","streamSeeker : "+streamSeeker.getId()+" currentTrackPosition :"+currentTrackPosition.getId());
////
//
//            streamSeeker.setMax(duration);
//            currentTrackPosition.setText(getTimeFromMillisecond(currentPosition));
//            totalTrackPosition.setText(getTimeFromMillisecond(duration));
//            streamSeeker.setProgress(currentPosition);
//
//        }

        @Override
        protected void onPostExecute(Boolean result) {
            // TODO Auto-generated method stub
            super.onPostExecute(result);

            Log.d("Prepared", "//" + result);
            if (streamDialog != null) {
//                streamDialog.hide();
//                streamDialog.dismiss();
                SharedPrefrenceUtils.getInstance(Home.this).setCurrentStreamingItem("");
            }

            isStreaming = false;
        }

        @Override
        protected void onPreExecute() {
            // TODO Auto-generated method stub
            super.onPreExecute();
//            this.progressDialog.setMessage("Buffering...");
//            this.progressDialog.show();

        }


    }

    private void makeStreamingDialog(String file_name) {

        streamDialog = new StreamFragment();

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {

                mDuration = msg.arg2;
                mCurrentPosition = msg.arg1;

                streamSeeker.setProgress(mCurrentPosition);
                streamSeeker.setMax(mDuration);
                currentTrackPosition.setText(getTimeFromMillisecond(mCurrentPosition));
                totalTrackPosition.setText(getTimeFromMillisecond(mDuration));

            }
        };


        streamDialog.setFileName(file_name);
        streamDialog.show(getFragmentManager(), "Stream");


    }

    private void prepareStreaming(String uri, String file_name) {
        SharedPrefrenceUtils.getInstance(this).setCurrentStreamingItem(file_name);
        makeStreamingDialog(file_name);
        final String uriToStream = uri;
        isStreaming = true;
        new Player().execute(uriToStream);
//        streamSeeker.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override
//            public void onProgressChanged(SeekBar seekBar, int position, boolean fromUser) {
//
//                if (fromUser)
//                    mPlayer.seekTo(position);
//            }
//
//            @Override
//            public void onStartTrackingTouch(SeekBar seekBar) {
//
//            }
//
//            @Override
//            public void onStopTrackingTouch(SeekBar seekBar) {
//
//            }
//        });


    }

    private void unRegisterBroadcast() {
        if (mReceiverRegistered) {
            this.unregisterReceiver(receiver);
            mReceiverRegistered = false;
        }
    }

    private void registerForBroadcastListen(Context context) {
        receiver = new StreamUriBroadcastReceiver();
        if (!mReceiverRegistered) {
            context.registerReceiver(receiver, new IntentFilter(Constants.ACTION_STREAM_URL_FETCHED));
            mReceiverRegistered = true;
        }
    }


    public class StreamUriBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(Constants.ACTION_STREAM_URL_FETCHED)) {

                L.m("Home", "update via broadcast: streaming uri " + intent.getStringExtra(Constants.EXTRAA_URI));

                if (!isStreaming)
                    prepareStreaming(intent.getStringExtra(Constants.EXTRAA_URI), intent.getStringExtra(Constants.EXTRAA_STREAM_FILE));
            }
        }

    }


    public static class StreamFragment extends DialogFragment {

        private String fileName;

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Dialog streamDialog = new Dialog(getActivity());
            setCancelable(false);


            LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View layout = inflater.inflate(R.layout.stream_layout, (ViewGroup) getActivity().findViewById(R.id.dialogParent));
            final SharedPrefrenceUtils utils = SharedPrefrenceUtils.getInstance(getActivity());

            streamingItemTitle = (TextView) layout.findViewById(R.id.streamItemTitle);
            cancelStreamingBtn = (TextView) layout.findViewById(R.id.stream_cancel_btn_text);
            streamSeeker = (SeekBar) layout.findViewById(R.id.streaming_audio_seekbar);
            Typeface tf = FontManager.getInstance(getActivity()).getTypeFace(FontManager.FONT_RALEWAY_REGULAR);
            currentTrackPosition = (TextView) layout.findViewById(R.id.currentTrackPositionText);
            totalTrackPosition = (TextView) layout.findViewById(R.id.totalTrackLengthText);
            currentTrackPosition.setTypeface(tf);
            totalTrackPosition.setTypeface(tf);
            streamSeeker.getProgressDrawable().setColorFilter(getResources().getColor(R.color.PrimaryColor), PorterDuff.Mode.SRC_IN);
            streamSeeker.getThumb().setColorFilter(getResources().getColor(R.color.PrimaryColor), PorterDuff.Mode.SRC_IN);
            streamingItemTitle.setText(utils.getCurrentStreamingItem());
            cancelStreamingBtn.setTypeface(FontManager.getInstance(getActivity()).getTypeFace(FontManager.FONT_AWESOME));

            cancelStreamingBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    L.m("Home", "touched cancel btn");

                    MusicGenieMediaPlayer
                            .getInstance(getActivity())
                            .stopPlayer();

                    streamDialog.hide();
                    streamDialog.dismiss();
                }
            });

            streamSeeker.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int position, boolean fromUser) {

                    L.m("StreamTEST", " from User:" + fromUser + " new position " + position);
                    if (fromUser)
                        MusicGenieMediaPlayer.getInstance(getActivity()).seekTo(position);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    //MusicGenieMediaPlayer.getInstance(getActivity()).
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            streamDialog.setTitle("Streaming...");
            streamDialog.setContentView(layout);

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            Window window = streamDialog.getWindow();
            lp.copyFrom(window.getAttributes());
            //This makes the dialog take up the full width
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);

            return streamDialog;
        }

    }


    private String getTimeFromMillisecond(int millis) {
        String hr = "";
        String min = "";
        String sec = "";
        String time = "";
        int i_hr = (millis / 1000) / 3600;
        int i_min = (millis / 1000) / 60;
        int i_sec = (millis / 1000) % 60;

        if (i_hr == 0) {
            min = (String.valueOf(i_min).length() < 2) ? "0" + i_min : String.valueOf(i_min);
            sec = (String.valueOf(i_sec).length() < 2) ? "0" + i_sec : String.valueOf(i_sec);
            time = min + " : " + sec;
        } else {
            hr = (String.valueOf(i_hr).length() < 2) ? "0" + i_hr : String.valueOf(i_hr);
            min = (String.valueOf(i_min).length() < 2) ? "0" + i_min : String.valueOf(i_min);
            sec = (String.valueOf(i_sec).length() < 2) ? "0" + i_sec : String.valueOf(i_sec);
            time = hr + " : " + min + " : " + sec;
        }

        return time;
    }


}
