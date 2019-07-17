package com.example.musicplayer.view;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.transition.Slide;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.example.musicplayer.R;
import com.example.musicplayer.app.BaseUri;
import com.example.musicplayer.app.BroadcastName;
import com.example.musicplayer.app.Constant;
import com.example.musicplayer.base.activity.BaseMvpActivity;
import com.example.musicplayer.contract.IPlayContract;
import com.example.musicplayer.entiy.LocalSong;
import com.example.musicplayer.entiy.Song;
import com.example.musicplayer.presenter.PlayPresenter;
import com.example.musicplayer.service.PlayerService;
import com.example.musicplayer.util.CommonUtil;
import com.example.musicplayer.util.DisplayUtil;
import com.example.musicplayer.util.FastBlurUtil;
import com.example.musicplayer.util.FileHelper;
import com.example.musicplayer.util.MediaUtil;
import com.example.musicplayer.util.RxApiManager;
import com.example.musicplayer.widget.BackgroundAnimationRelativeLayout;
import com.example.musicplayer.widget.DiscView;
import com.example.musicplayer.widget.LrcView;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import butterknife.BindView;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 播放界面
 */
public class PlayActivity extends BaseMvpActivity<PlayPresenter> implements IPlayContract.View {

    private String TAG = "PlayActivity";
    @BindView(R.id.tv_song)
    TextView mSongTv;
    @BindView(R.id.iv_back)
    ImageView mBackIv;
    @BindView(R.id.tv_singer)
    TextView mSingerTv;
    @BindView(R.id.btn_player)
    Button mPlayBtn;
    @BindView(R.id.btn_last)
    Button mLastBtn;
    @BindView(R.id.btn_order)
    Button mOrderBtn;
    @BindView(R.id.next)
    Button mNextBtn;
    @BindView(R.id.relative_root)
    BackgroundAnimationRelativeLayout mRootLayout;
    @BindView(R.id.btn_love)
    Button mLoveBtn;
    @BindView(R.id.seek)
    SeekBar mSeekBar;
    @BindView(R.id.tv_current_time)
    TextView mCurrentTimeTv;
    @BindView(R.id.tv_duration_time)
    TextView mDurationTimeTv;
    @BindView(R.id.disc_view)
    DiscView mDisc; //唱碟
    @BindView(R.id.iv_disc_background)
    ImageView mDiscImg; //唱碟中的歌手头像
    @BindView(R.id.btn_get_img_lrc)
    Button mGetImgAndLrcBtn;//获取封面和歌词
    @BindView(R.id.lrcView)
    LrcView mLrcView; //歌词自定义View
    @BindView(R.id.downloadIv)
    ImageView mDownLoad; //下载

    private PlayPresenter mPresenter;


    private boolean isOnline; //判断是否为网络歌曲
    private int mListType; //列表类型
    private int mPlayStatus;

    private boolean isChange; //拖动进度条
    private boolean isSeek;//标记是否在暂停的时候拖动进度条
    private boolean flag; //用做暂停的标记
    private int time;   //记录暂停的时间
    private boolean isPlaying;
    private Song mSong;
    private MediaPlayer mMediaPlayer;


    private RelativeLayout mPlayRelative;

    private String mLrc = null;
    private boolean isLove;//是否已经在我喜欢的列表中
    private Bitmap mImgBmp;
    private List<LocalSong> mLocalSong;//用来判断是否有本地照片
    //服务
    private Thread mSeekBarThread;
    private IntentFilter mIntentFilter;
    private SongChangeReceiver songChangeReceiver;
    private PlayerService.PlayStatusBinder mPlayStatusBinder;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mPlayStatusBinder = (PlayerService.PlayStatusBinder) service;

            isOnline = FileHelper.getSong().isOnline();
            if (isOnline) {
                mGetImgAndLrcBtn.setVisibility(View.GONE);
                setSingerImg(FileHelper.getSong().getImgUrl());
                if (mPlayStatus == Constant.PLAY) {
                    mDisc.play();
                    mPlayBtn.setSelected(true);
                    startUpdateSeekBarProgress();
                }
            } else {
                setLocalImg(mSong.getSinger());
                mSeekBar.setSecondaryProgress((int) mSong.getDuration());
            }
            mDurationTimeTv.setText(MediaUtil.formatTime(mSong.getDuration()));
            //缓存进度条
            mPlayStatusBinder.getMediaPlayer().setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    mSeekBar.setSecondaryProgress(percent * mSeekBar.getProgress());
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {


        }
    };
    private Handler mMusicHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (!isChange) {
                mSeekBar.setProgress((int) mPlayStatusBinder.getCurrentTime());
                mCurrentTimeTv.setText(MediaUtil.formatTime(mSeekBar.getProgress()));
                startUpdateSeekBarProgress();
            }

        }
    };


    @Override
    protected void initView() {
        super.initView();
        CommonUtil.hideStatusBar(this, true);
        //设置进入退出动画
        getWindow().setEnterTransition(new Slide());
        getWindow().setExitTransition(new Slide());

        //是否为网络歌曲
        mPlayStatus = getIntent().getIntExtra(Constant.PLAYER_STATUS, 2);

        //注册广播
        registerBroadCast();

        //绑定服务
        Intent playIntent = new Intent(PlayActivity.this, PlayerService.class);
        bindService(playIntent, connection, Context.BIND_AUTO_CREATE);


    }

    @Override
    protected PlayPresenter getPresenter() {
        //与Presenter建立关系
        mPresenter = new PlayPresenter();
        return mPresenter;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_play;
    }

    @Override
    protected void initData() {

        //界面填充
        mSong = FileHelper.getSong();
        mListType = mSong.getListType();
        mSingerTv.setText(mSong.getSinger());
        mSongTv.setText(mSong.getSongName());
        mCurrentTimeTv.setText(MediaUtil.formatTime(mSong.getCurrentTime()));
        mSeekBar.setMax((int) mSong.getDuration());
        mSeekBar.setProgress((int) mSong.getCurrentTime());
        Log.d(TAG, "initData: "+mSongTv.getText());

        mPresenter.queryLove(mSong.getSongId()); //查找歌曲是否为我喜欢的歌曲

        if (mPlayStatus == Constant.PLAY) {
            mDisc.play();
            mPlayBtn.setSelected(true);
            startUpdateSeekBarProgress();
        }
    }

    private void registerBroadCast(){
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(BroadcastName.SONG_CHANGE);
        mIntentFilter.addAction(BroadcastName.ONLINE_SONG);
        mIntentFilter.addAction(BroadcastName.ONLINE_ALBUM_SONG_Change);
        songChangeReceiver = new SongChangeReceiver();
        registerReceiver(songChangeReceiver, mIntentFilter);
    }

    private void try2UpdateMusicPicBackground(final Bitmap bitmap) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Drawable drawable = getForegroundDrawable(bitmap);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRootLayout.setForeground(drawable);
                        mRootLayout.beginAnimation();
                    }
                });
            }
        }).start();
    }

    private Drawable getForegroundDrawable(Bitmap bitmap) {
        /*得到屏幕的宽高比，以便按比例切割图片一部分*/
        final float widthHeightSize = (float) (DisplayUtil.getScreenWidth(PlayActivity.this)
                * 1.0 / DisplayUtil.getScreenHeight(this) * 1.0);

        int cropBitmapWidth = (int) (widthHeightSize * bitmap.getHeight());
        int cropBitmapWidthX = (int) ((bitmap.getWidth() - cropBitmapWidth) / 2.0);

        /*切割部分图片*/
        Bitmap cropBitmap = Bitmap.createBitmap(bitmap, cropBitmapWidthX, 0, cropBitmapWidth,
                bitmap.getHeight());
        /*缩小图片*/
        Bitmap scaleBitmap = Bitmap.createScaledBitmap(cropBitmap, bitmap.getWidth() / 50, bitmap
                .getHeight() / 50, false);
        /*模糊化*/
        final Bitmap blurBitmap = FastBlurUtil.doBlur(scaleBitmap, 8, true);

        final Drawable foregroundDrawable = new BitmapDrawable(blurBitmap);
        /*加入灰色遮罩层，避免图片过亮影响其他控件*/
        foregroundDrawable.setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
        return foregroundDrawable;
    }


    @Override
    protected void onClick() {
        mBackIv.setOnClickListener(v -> {
            finish();
        });
        mGetImgAndLrcBtn.setOnClickListener(v -> getSingerAndLrc());

        //进度条的监听事件
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                //防止在拖动进度条进行进度设置时与Thread更新播放进度条冲突
                isChange = true;
                isSeek = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mPlayStatusBinder.isPlaying()) {
                    mMediaPlayer = mPlayStatusBinder.getMediaPlayer();
                    mMediaPlayer.seekTo(seekBar.getProgress());
                    startUpdateSeekBarProgress();
                } else {
                    time = seekBar.getProgress();
                }
                mCurrentTimeTv.setText(MediaUtil.formatTime(seekBar.getProgress()));
                isChange = false;

            }
        });

        mOrderBtn.setOnClickListener(v -> CommonUtil.showToast(PlayActivity.this, "抱歉，目前只支持顺序播放，其他功能还在开发中"));

        //
        mPlayBtn.setOnClickListener(v -> {
            mMediaPlayer = mPlayStatusBinder.getMediaPlayer();
            if (mPlayStatusBinder.isPlaying()) {
                time = mMediaPlayer.getCurrentPosition();
                mPlayStatusBinder.pause();
                stopUpdateSeekBarProgress();
                flag = true;
                mPlayBtn.setSelected(false);
                mDisc.pause();
            } else if (flag) {
                mPlayStatusBinder.resume();
                flag = false;
                if (isSeek) {
                    mMediaPlayer.seekTo(time);
                } else {
                    isSeek = false;
                }
                mDisc.play();
                mPlayBtn.setSelected(true);
                startUpdateSeekBarProgress();
            } else {
                if (isOnline) {
                    mPlayStatusBinder.playOnline();
                } else {
                    mPlayStatusBinder.play(mListType);
                }
                mMediaPlayer.seekTo((int) mSong.getCurrentTime());
                mDisc.play();
                mPlayBtn.setSelected(true);
                startUpdateSeekBarProgress();
            }
        });
        mNextBtn.setOnClickListener(v -> {
            mPlayStatusBinder.next();
            if (mPlayStatusBinder.isPlaying()) {
                mPlayBtn.setSelected(true);
            } else {
                mPlayBtn.setSelected(false);
            }
            mDisc.next();
        });
        mLastBtn.setOnClickListener(v -> {
            mPlayStatusBinder.last();
            mPlayBtn.setSelected(true);
            mDisc.last();
        });

        mLoveBtn.setOnClickListener(v -> {
            showLoveAnim();
            if (isLove) {
                mLoveBtn.setSelected(false);
                mPresenter.deleteFromLove(FileHelper.getSong().getSongId());
            } else {
                mLoveBtn.setSelected(true);
                mPresenter.saveToLove(FileHelper.getSong());
            }
            isLove = !isLove;
        });

        //唱碟点击效果
        mDisc.setOnClickListener(v -> {
                    if (!isOnline) {
                        if (getLrcFromLocal().equals("")) {
                            mPresenter.getLrcUrl(getSongName(), mSong.getDuration());
                        }
                        showLrc(getLrcFromLocal());
                    } else {
                        mPresenter.getLrcUrl(getSongName(), mSong.getDuration());
                    }
                }
        );
        //歌词点击效果
        mLrcView.setOnClickListener(v -> {
            mLrcView.setVisibility(View.GONE);
            mDisc.setVisibility(View.VISIBLE);
        });
        //歌曲下载
        mDownLoad.setOnClickListener(v -> {
            CommonUtil.showToast(this,"开始下载歌曲");
            downLoad(mSong.getUrl(), mSong.getSongName());
        });

    }

    @Override
    public String getSingerName() {
        Song song = FileHelper.getSong();
        if (song.getSinger().contains("/")) {
            String[] s = song.getSinger().split("/");
            return s[0].trim();
        } else {
            return song.getSinger().trim();
        }

    }

    private String getSongName() {
        Song song = FileHelper.getSong();
        return song.getSongName().trim();
    }

    @Override
    public void getSingerAndLrc() {
        mGetImgAndLrcBtn.setText("正在获取...");
        mPresenter.getSingerImg(getSingerName(), getSongName(), mSong.getDuration());
    }

    @Override
    public void setSingerImg(String ImgUrl) {
        SimpleTarget target = new SimpleTarget<Drawable>(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL) {
            @Override
            public void onResourceReady(@Nullable Drawable resource, Transition<? super Drawable> transition) {
                mImgBmp = ((BitmapDrawable) resource).getBitmap();
                //如果是本地音乐
                if (!isOnline) {
                    //保存图片到本地
                    FileHelper.saveImgToNative(PlayActivity.this, mImgBmp, getSingerName());
                    //将封面地址放到数据库中
                    LocalSong localSong = new LocalSong();
                    localSong.setPic(BaseUri.STORAGE_IMG_FILE + FileHelper.getSong().getSinger() + ".jpg");
                    localSong.updateAll("songId=?", FileHelper.getSong().getSongId());
                }

                try2UpdateMusicPicBackground(mImgBmp);
                setDiscImg(mImgBmp);
                mGetImgAndLrcBtn.setVisibility(View.GONE);
            }
        };
        Glide.with(this)
                .load(ImgUrl)
                .apply(RequestOptions.placeholderOf(R.drawable.welcome))
                .apply(RequestOptions.errorOf(R.drawable.welcome))
                .into(target);

    }

    @Override
    public void setImgFail(String errorMessage) {
        CommonUtil.showToast(this, errorMessage);
    }

    @Override
    public void showLove(final boolean love) {
        isLove = love;
        runOnUiThread(() -> {
            if (love) {
                mLoveBtn.setSelected(true);
            } else {
                mLoveBtn.setSelected(false);
            }
        });

    }

    @Override
    public void showLoveAnim() {
        AnimatorSet animatorSet = (AnimatorSet) AnimatorInflater.loadAnimator(PlayActivity.this, R.animator.favorites_anim);
        animatorSet.setTarget(mLoveBtn);
        animatorSet.start();
    }

    @Override
    public void saveToLoveSuccess() {
        CommonUtil.showToast(PlayActivity.this, getString(R.string.love_success));
    }

    @Override
    public void sendUpdateCollection() {
        sendBroadcast(new Intent(BroadcastName.LOVE_SONG_CANCEL));
    }

    @Override
    public void showLrcMessage(String lrc, String id) {
        if (lrc == null) {
            CommonUtil.showToast(PlayActivity.this, getString(R.string.get_lrc_fail));
            mLrc = null;
        } else {
            mLrc = lrc;
            saveLrcToLocal();
        }
    }


    //设置唱碟中歌手头像
    private void setDiscImg(Bitmap bitmap) {
        mDiscImg.setImageDrawable(mDisc.getDiscDrawable(bitmap));
        int marginTop = (int) (DisplayUtil.SCALE_DISC_MARGIN_TOP * CommonUtil.getScreenHeight(PlayActivity.this));
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) mDiscImg
                .getLayoutParams();
        layoutParams.setMargins(0, marginTop, 0, 0);

        mDiscImg.setLayoutParams(layoutParams);
    }

    private class SongChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            mDisc.setVisibility(View.VISIBLE);
            mLrcView.setVisibility(View.GONE);
            Song mSong = FileHelper.getSong();
            mSongTv.setText(mSong.getSongName());
            mSingerTv.setText(mSong.getSinger());
            mDurationTimeTv.setText(MediaUtil.formatTime(mSong.getDuration()));
            mPlayBtn.setSelected(true);
            mSeekBar.setMax((int) mSong.getDuration());
            startUpdateSeekBarProgress();
            //缓存进度条
            mPlayStatusBinder.getMediaPlayer().setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                @Override
                public void onBufferingUpdate(MediaPlayer mp, int percent) {
                    Log.d(TAG, "onBufferingUpdate: " + percent);
                    mSeekBar.setSecondaryProgress(percent * mSeekBar.getProgress());
                }
            });
            mPresenter.queryLove(mSong.getSongId()); //查找歌曲是否为我喜欢的歌曲
            if (mSong.isOnline()) {
                setSingerImg(mSong.getImgUrl());
            } else {
                setLocalImg(mSong.getSinger());//显示照片
            }
        }
    }

    private void startUpdateSeekBarProgress() {
        /*避免重复发送Message*/
        stopUpdateSeekBarProgress();
        mMusicHandler.sendEmptyMessageDelayed(0, 1000);
    }

    private void stopUpdateSeekBarProgress() {
        mMusicHandler.removeMessages(0);
    }

    private void setLocalImg(String singer) {
        String imgUrl = BaseUri.STORAGE_IMG_FILE + MediaUtil.formatSinger(singer) + ".jpg";
        SimpleTarget target = new SimpleTarget<Drawable>(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL) {
            @Override
            public void onResourceReady(@Nullable Drawable resource, Transition<? super Drawable> transition) {
                mGetImgAndLrcBtn.setVisibility(View.GONE);
                mImgBmp = ((BitmapDrawable) resource).getBitmap();
                try2UpdateMusicPicBackground(mImgBmp);
                setDiscImg(mImgBmp);
            }
        };
        Glide.with(this)
                .load(imgUrl)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        mGetImgAndLrcBtn.setVisibility(View.VISIBLE);
                        mGetImgAndLrcBtn.setText("获取封面和歌词");
                        setDiscImg(BitmapFactory.decodeResource(getResources(), R.drawable.default_disc));
                        mRootLayout.setBackgroundResource(R.drawable.welcome);
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        return false;
                    }
                })
                .apply(RequestOptions.placeholderOf(R.drawable.welcome))
                .apply(RequestOptions.errorOf(R.drawable.welcome))
                .into(target);

    }

    private void saveLrcToLocal() {
        new Thread(() -> {
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            BufferedWriter writer = null;
            try {
                URL url = new URL(mLrc);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                InputStream inputStream = urlConnection.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder lrcBuilder = new StringBuilder();
                String lrc;

                /**
                 * 保存到本地
                 */
                if (!isOnline) {
                    File file = new File(BaseUri.STORAGE_LRC_FILE);
                    if (!file.exists()) {
                        file.mkdirs();
                    }
                    File lrcFile = new File(file, getSongName() + ".lrc");
                    FileOutputStream outputStream = new FileOutputStream(lrcFile);
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, "gbk");
                    writer = new BufferedWriter(outputStreamWriter);
                }
                while ((lrc = reader.readLine()) != null) {
                    if (isOnline) {
                        lrcBuilder.append(lrc);
                        lrcBuilder.append("\n");
                    } else {
                        //写入
                        writer.write(lrc);
                        writer.newLine();
                    }

                }
                if (isOnline) showLrc(lrcBuilder.toString());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (urlConnection != null) urlConnection.disconnect();
                try {
                    if (reader != null) reader.close();
                    if (writer != null) writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 从本地读取歌词文件
     */
    private String getLrcFromLocal() {
        try {
            InputStream inputStream = new FileInputStream(new File(BaseUri.STORAGE_LRC_FILE + getSongName() + ".lrc"));
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len = -1;
            while ((len = inputStream.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            return os.toString("gbk"); //文件编码是gbk

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 展示歌词
     *
     * @param lrc
     */
    private void showLrc(final String lrc) {
        runOnUiThread(()->{
            mDisc.setVisibility(View.GONE);
            mLrcView.setVisibility(View.VISIBLE);
            mLrcView.setLrc(lrc);
            mLrcView.setHighLineColor(getResources().getColor(R.color.musicStyle));
            mLrcView.setPlayer(mPlayStatusBinder.getMediaPlayer());
            mLrcView.init();
        });

    }

    private void downLoad(String url, String song) {
        new Thread(() -> {
            File file = new File(BaseUri.STORAGE_SONG_FILE);
            if (!file.exists()) {
                file.mkdirs();
            }
            File songFile = new File(file, song + ".mp3");
            BufferedOutputStream out = null;
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()){
                    out = new BufferedOutputStream(new FileOutputStream(songFile));
                    byte[] bytes = response.body().bytes();
                    out.write(bytes,0,bytes.length);
                    out.close();
                }
                showLoadSuccess();
            } catch (IOException e){
                e.printStackTrace();
            }catch (Exception e) {
                e.printStackTrace();
            }finally {
                try{
                    if(out != null) out.close();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }).start();
    }



    private void showLoadSuccess(){
        runOnUiThread(()->{
            CommonUtil.showToast(this,"歌曲下载成功");
        });

    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(connection);
        unregisterReceiver(songChangeReceiver);
        stopUpdateSeekBarProgress();
    }

}
