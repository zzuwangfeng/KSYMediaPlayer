package com.ksy.media.widget;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import com.ksy.media.player.IMediaPlayer;
import com.ksy.media.player.MediaInfo;
import com.ksy.media.player.IMediaPlayer.OnBufferingUpdateListener;
import com.ksy.media.player.IMediaPlayer.OnCompletionListener;
import com.ksy.media.player.IMediaPlayer.OnErrorListener;
import com.ksy.media.player.IMediaPlayer.OnInfoListener;
import com.ksy.media.player.IMediaPlayer.OnPreparedListener;
import com.ksy.media.player.IMediaPlayer.OnSeekCompleteListener;
import com.ksy.media.player.IMediaPlayer.OnSurfaceListener;
import com.ksy.media.player.IMediaPlayer.OnVideoSizeChangedListener;
import com.ksy.media.player.KSYMediaPlayer;
import com.ksy.media.player.option.AvFourCC;
import com.ksy.media.player.option.format.AvFormatOption_HttpDetectRangeSupport;
import com.ksy.media.player.util.Constants;
import com.ksy.media.widget.MediaPlayerBaseControllerView.MediaPlayerController;

/**
 * Displays a video file. The VideoView class can load images from various
 * sources (such as resources or content providers), takes care of computing its
 * measurement from the video so that it can be used in any layout manager, and
 * provides various display options such as scaling and tinting.
 * 
 */
public class MediaPlayerVideoView extends SurfaceView implements
		IMediaPlayerControl {
	private static final String TAG = MediaPlayerVideoView.class.getName();

	private Uri mUri;
	private long mDuration;
	private MediaInfo mMediaInfo;
	private String mUserAgent;

	private static final int STATE_ERROR = -1;
	private static final int STATE_IDLE = 0;
	private static final int STATE_PREPARING = 1;
	private static final int STATE_PREPARED = 2;
	public static final int STATE_PLAYING = 3;
	private static final int STATE_PAUSED = 4;
	private static final int STATE_PLAYBACK_COMPLETED = 5;
	private static final int STATE_SUSPEND = 6;
	private static final int STATE_RESUME = 7;
	private static final int STATE_SUSPEND_UNSUPPORTED = 8;

	public int mCurrentState = STATE_IDLE;
	private int mTargetState = STATE_IDLE;

	private int mVideoLayout = VIDEO_LAYOUT_SCALE;
	public static final int VIDEO_LAYOUT_ORIGIN = 0;
	public static final int VIDEO_LAYOUT_SCALE = 1;
	public static final int VIDEO_LAYOUT_STRETCH = 2;
	public static final int VIDEO_LAYOUT_ZOOM = 3;

	private SurfaceHolder mSurfaceHolder = null;
	private IMediaPlayer mMediaPlayer = null;
	private int mVideoWidth;
	private int mVideoHeight;
	private int mVideoSarNum;
	private int mVideoSarDen;
	private int mSurfaceWidth;
	private int mSurfaceHeight;
	private OnCompletionListener mOnCompletionListener;
	private OnPreparedListener mOnPreparedListener;
	private OnErrorListener mOnErrorListener;
	private OnSeekCompleteListener mOnSeekCompleteListener;
	private OnInfoListener mOnInfoListener;
	private OnBufferingUpdateListener mOnBufferingUpdateListener;
	private OnSurfaceListener mOnSurfaceListener;
	private MediaPlayerController mMediaPlayerController;

	private int mCurrentBufferPercentage;
	private long mSeekWhenPrepared;
	private Context mContext;

	private boolean mHasPrepared = false;

	public MediaPlayerVideoView(Context context) {
		super(context);
		initVideoView(context);
	}

	public MediaPlayerVideoView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public MediaPlayerVideoView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
		initVideoView(context);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
		int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
		setMeasuredDimension(width, height);
	}

	public void setVideoLayout(int layout) {
		Log.d(Constants.LOG_TAG, "SetVideoLayout ,Mode = "+layout);
		LayoutParams lp = getLayoutParams();
		Pair<Integer, Integer> res = ScreenResolution.getResolution(mContext);
		int windowWidth = res.first.intValue(), windowHeight = res.second
				.intValue();
		float windowRatio = windowWidth / (float) windowHeight;
		int sarNum = mVideoSarNum;
		int sarDen = mVideoSarDen;
		if (mVideoHeight > 0 && mVideoWidth > 0) {
			float videoRatio = ((float) (mVideoWidth)) / mVideoHeight;
			if (sarNum > 0 && sarDen > 0)
				videoRatio = videoRatio * sarNum / sarDen;
			mSurfaceHeight = mVideoHeight;
			mSurfaceWidth = mVideoWidth;

			if (layout == MediaPlayerMovieRatioView.MOVIE_RATIO_MODE_16_9) {
				// 16:9
				float target_ratio = 16.0f / 9.0f;
				float dh = windowHeight;
				float dw = windowWidth;
				if (windowRatio < target_ratio) {
					dh = dw / target_ratio;
				} else {
					dw = dh * target_ratio;
				}
				lp.width = (int) dw;
				lp.height = (int) dh;

			} else if (layout == MediaPlayerMovieRatioView.MOVIE_RATIO_MODE_4_3) {
				// 4:3
				float target_ratio = 4.0f / 3.0f;
				float source_height = windowHeight;
				float source_width = windowWidth;
				if (windowRatio < target_ratio) {
					source_height = source_width / target_ratio;
				} else {
					source_width = source_height * target_ratio;
				}
				lp.width = (int) source_width;
				lp.height = (int) source_height;
			} else if (layout == MediaPlayerMovieRatioView.MOVIE_RATIO_MODE_ORIGIN
					&& mSurfaceWidth < windowWidth
					&& mSurfaceHeight < windowHeight) {
				// origin
				lp.width = (int) (mSurfaceHeight * videoRatio);
				lp.height = mSurfaceHeight;
			} else if (layout == MediaPlayerMovieRatioView.MOVIE_RATIO_MODE_FULLSCREEN) {
				// fullscreen
				lp.width = (windowRatio < videoRatio) ? windowWidth
						: (int) (videoRatio * windowHeight);
				lp.height = (windowRatio > videoRatio) ? windowHeight
						: (int) (windowWidth / videoRatio);
			}

			setLayoutParams(lp);
			getHolder().setFixedSize(mSurfaceWidth, mSurfaceHeight);
			DebugLog.dfmt(
					TAG,
					"VIDEO: %dx%dx%f[SAR:%d:%d],Layout :%d, Surface: %dx%d, LP: %dx%d, Window: %dx%dx%f",
					mVideoWidth, mVideoHeight, videoRatio, mVideoSarNum,
					mVideoSarDen, layout, mSurfaceWidth, mSurfaceHeight,
					lp.width, lp.height, windowWidth, windowHeight, windowRatio);
		}
		mVideoLayout = layout;
	}

	private void initVideoView(Context ctx) {
		mContext = ctx;
		mVideoWidth = 0;
		mVideoHeight = 0;
		mVideoSarNum = 0;
		mVideoSarDen = 0;
		getHolder().addCallback(mSHCallback);
		setFocusable(true);
		setFocusableInTouchMode(true);
		requestFocus();
		mCurrentState = STATE_IDLE;
		mTargetState = STATE_IDLE;
		if (ctx instanceof Activity)
			((Activity) ctx).setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}

	public boolean isValid() {
		return (mSurfaceHolder != null && mSurfaceHolder.getSurface().isValid());
	}

	public void setVideoPath(String path) {
		Log.i(Constants.LOG_TAG, "setVideoPath : path :" + path);
		setVideoURI(Uri.parse(path));
	}

	public void setVideoURI(Uri uri) {
		mUri = uri;
		mSeekWhenPrepared = 0;
		openVideo();
		requestLayout();
		invalidate();
	}

	public void setUserAgent(String ua) {
		mUserAgent = ua;
	}

	public void stopPlayback() {
		if (mMediaPlayer != null) {
			mMediaPlayer.stop();
			mMediaPlayer.release();
			mMediaPlayer = null;
			mCurrentState = STATE_IDLE;
			mTargetState = STATE_IDLE;
		}
	}

	private void openVideo() {
		Log.i(Constants.LOG_TAG, "Open video action begin");
		if (mUri == null || mSurfaceHolder == null)
			return;

		Intent i = new Intent("com.android.music.musicservicecommand");
		i.putExtra("command", "pause");
		mContext.sendBroadcast(i);

		release(false);
		try {
			mDuration = -1;
			mCurrentBufferPercentage = 0;
			mMediaInfo = null;
			// mMediaPlayer = new AndroidMediaPlayer();
			KSYMediaPlayer ijkMediaPlayer = null;
			if (mUri != null) {
				ijkMediaPlayer = new KSYMediaPlayer();
				ijkMediaPlayer
						.setAvOption(AvFormatOption_HttpDetectRangeSupport.Disable);
				ijkMediaPlayer.setOverlayFormat(AvFourCC.SDL_FCC_RV32);

				ijkMediaPlayer.setAvCodecOption("skip_loop_filter", "48");
				ijkMediaPlayer.setFrameDrop(12);
				if (mUserAgent != null) {
					ijkMediaPlayer.setAvFormatOption("user_agent", mUserAgent);
				}
			}
			mMediaPlayer = ijkMediaPlayer;
			mMediaPlayer.setOnPreparedListener(mPreparedListener);
			mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
			mMediaPlayer.setOnCompletionListener(mCompletionListener);
			mMediaPlayer.setOnErrorListener(mErrorListener);
			mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
			mMediaPlayer.setOnInfoListener(mInfoListener);
			mMediaPlayer.setOnSeekCompleteListener(mSeekCompleteListener);
			if (mUri != null)
				mMediaPlayer.setDataSource(mUri.toString());
			mMediaPlayer.setDisplay(mSurfaceHolder);
			mMediaPlayer.setScreenOnWhilePlaying(true);
			mMediaPlayer.prepareAsync();
			if (mMediaPlayerController != null)
				mMediaPlayerController.onVideoPreparing();
			mCurrentState = STATE_PREPARING;
			// attachMediaController();
		} catch (IOException ex) {
			DebugLog.e(TAG, "Unable to open content: " + mUri, ex);
			mCurrentState = STATE_ERROR;
			mTargetState = STATE_ERROR;
			mErrorListener.onError(mMediaPlayer,
					IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
			return;
		} catch (IllegalArgumentException ex) {
			DebugLog.e(TAG, "Unable to open content: " + mUri, ex);
			mCurrentState = STATE_ERROR;
			mTargetState = STATE_ERROR;
			mErrorListener.onError(mMediaPlayer,
					IMediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
			return;
		}
	}

	OnVideoSizeChangedListener mSizeChangedListener = new OnVideoSizeChangedListener() {
		public void onVideoSizeChanged(IMediaPlayer mp, int width, int height,
				int sarNum, int sarDen) {
			DebugLog.dfmt(TAG, "onVideoSizeChanged: (%dx%d)", width, height);
			Log.d(Constants.LOG_TAG, "OnSizeChanged");
			mVideoWidth = mp.getVideoWidth();
			mVideoHeight = mp.getVideoHeight();
			mVideoSarNum = sarNum;
			mVideoSarDen = sarDen;
		}
	};

	OnPreparedListener mPreparedListener = new OnPreparedListener() {
		public void onPrepared(IMediaPlayer mp) {
			Log.d(Constants.LOG_TAG, "OnPrepared");
			mHasPrepared = true;
			mCurrentState = STATE_PREPARED;
			mTargetState = STATE_PLAYING;

			if (mOnPreparedListener != null)
				mOnPreparedListener.onPrepared(mMediaPlayer);

			mVideoWidth = mp.getVideoWidth();
			mVideoHeight = mp.getVideoHeight();

			long seekToPosition = mSeekWhenPrepared;

			if (seekToPosition != 0)
				seekTo(seekToPosition);
			if (mVideoWidth != 0 && mVideoHeight != 0) {
				if (mSurfaceWidth == mVideoWidth
						&& mSurfaceHeight == mVideoHeight) {
					if (mTargetState == STATE_PLAYING) {

					} else if (!isPlaying()
							&& (seekToPosition != 0 || getCurrentPosition() > 0)) {
					}
				}
			} else if (mTargetState == STATE_PLAYING) {

			}
		}
	};

	private OnCompletionListener mCompletionListener = new OnCompletionListener() {
		public void onCompletion(IMediaPlayer mp) {
			Log.d(Constants.LOG_TAG, "onCompletion");
			mCurrentState = STATE_PLAYBACK_COMPLETED;
			mTargetState = STATE_PLAYBACK_COMPLETED;

			if (mOnCompletionListener != null)
				mOnCompletionListener.onCompletion(mMediaPlayer);
		}
	};

	private OnErrorListener mErrorListener = new OnErrorListener() {
		public boolean onError(IMediaPlayer mp, int framework_err, int impl_err) {

			mCurrentState = STATE_ERROR;
			mTargetState = STATE_ERROR;

			/* If an error handler has been supplied, use it and finish. */
			if (mOnErrorListener != null) {
				if (mOnErrorListener.onError(mMediaPlayer, framework_err,
						impl_err)) {
					return true;
				}
			}
			return true;

		}
	};

	private OnBufferingUpdateListener mBufferingUpdateListener = new OnBufferingUpdateListener() {
		public void onBufferingUpdate(IMediaPlayer mp, int percent) {
			mCurrentBufferPercentage = percent;
			if (mOnBufferingUpdateListener != null)
				mOnBufferingUpdateListener.onBufferingUpdate(mp, percent);
		}
	};

	private OnInfoListener mInfoListener = new OnInfoListener() {
		@Override
		public boolean onInfo(IMediaPlayer mp, int what, int extra) {
			if (mOnInfoListener != null) {
				mOnInfoListener.onInfo(mp, what, extra);
			}
			return true;
		}
	};

	private OnSeekCompleteListener mSeekCompleteListener = new OnSeekCompleteListener() {
		@Override
		public void onSeekComplete(IMediaPlayer mp) {
			Log.d(Constants.LOG_TAG, "onSeekComplete");
			if (mOnSeekCompleteListener != null)
				mOnSeekCompleteListener.onSeekComplete(mp);
		}
	};

	public void setMediaPlayerController(
			MediaPlayerController mediaPlayerController) {
		mMediaPlayerController = mediaPlayerController;
	}

	public void setOnPreparedListener(OnPreparedListener l) {
		mOnPreparedListener = l;
	}

	public void setOnCompletionListener(OnCompletionListener l) {
		mOnCompletionListener = l;
	}

	public void setOnErrorListener(OnErrorListener l) {
		mOnErrorListener = l;
	}

	public void setOnBufferingUpdateListener(OnBufferingUpdateListener l) {
		mOnBufferingUpdateListener = l;
	}

	public void setOnSeekCompleteListener(OnSeekCompleteListener l) {
		mOnSeekCompleteListener = l;
	}

	public void setOnInfoListener(OnInfoListener l) {
		mOnInfoListener = l;
	}

	public void setOnSurfaceListener(OnSurfaceListener l) {
		mOnSurfaceListener = l;
	}

	SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback() {
		public void surfaceChanged(SurfaceHolder holder, int format, int w,
				int h) {
			Log.i(Constants.LOG_TAG, "surfaceChanged in videoview");
			mSurfaceHolder = holder;
			if (mMediaPlayer != null) {
				mMediaPlayer.setDisplay(mSurfaceHolder);
			}

			mSurfaceWidth = w;
			mSurfaceHeight = h;
			boolean isValidState = (mTargetState == STATE_PLAYING);
			boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
			if (mMediaPlayer != null && isValidState && hasValidSize) {
				if (mSeekWhenPrepared != 0)
					seekTo(mSeekWhenPrepared);
			}
			if (mOnSurfaceListener != null)
				mOnSurfaceListener.surfaceChanged(holder, format, w, h);
		}

		public void surfaceCreated(SurfaceHolder holder) {
			Log.i(Constants.LOG_TAG, "surfaceCreated in video view");
			mSurfaceHolder = holder;
			if (mMediaPlayer != null && mCurrentState == STATE_SUSPEND
					&& mTargetState == STATE_RESUME) {
				Log.i(Constants.LOG_TAG, "surfaceCreated  resume in video view");
				mMediaPlayer.setDisplay(mSurfaceHolder);
				resume();
			} else {
				Log.i(Constants.LOG_TAG, "surfaceCreated  openVideo in video view");
				openVideo();
			}
			if (mOnSurfaceListener != null)
				mOnSurfaceListener.surfaceCreated(holder);
		}

		public void surfaceDestroyed(SurfaceHolder holder) {
			mSurfaceHolder = null;

			if (mCurrentState != STATE_SUSPEND)
				release(true);
			if (mOnSurfaceListener != null)
				mOnSurfaceListener.surfaceDestroyed(holder);
		}
	};

	private void release(boolean cleartargetstate) {
		if (mMediaPlayer != null) {
			mMediaPlayer.reset();
			mMediaPlayer.release();
			mMediaPlayer = null;
			mCurrentState = STATE_IDLE;
			if (cleartargetstate)
				mTargetState = STATE_IDLE;
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {

		return false;
	}

	@Override
	public boolean onTrackballEvent(MotionEvent ev) {

		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK
				&& keyCode != KeyEvent.KEYCODE_VOLUME_UP
				&& keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
				&& keyCode != KeyEvent.KEYCODE_MENU
				&& keyCode != KeyEvent.KEYCODE_CALL
				&& keyCode != KeyEvent.KEYCODE_ENDCALL;
		if (isInPlaybackState() && isKeyCodeSupported) {
			if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
					|| keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
					|| keyCode == KeyEvent.KEYCODE_SPACE) {
				if (mMediaPlayer.isPlaying()) {
					pause();

				} else {
					start();

				}
				return true;
			} else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
					&& mMediaPlayer.isPlaying()) {
				pause();
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void start() {
		Log.i(Constants.LOG_TAG, "start , " + isInPlaybackState());
		StackTraceElement[] elems = new Exception().getStackTrace();
		StringBuffer sb = new StringBuffer();
		for (StackTraceElement ele : elems) {
			sb.append(ele.toString()).append("\n");
		}
		Log.i(Constants.LOG_TAG, "trace " + sb.toString());
		if (isInPlaybackState()) {
			mMediaPlayer.start();
			mCurrentState = STATE_PLAYING;
			if (mMediaPlayerController != null)
				mMediaPlayerController.onPlay();
		}
		mTargetState = STATE_PLAYING;
	}

	@Override
	public void pause() {
		if (isInPlaybackState()) {
			if (mMediaPlayer.isPlaying()) {
				mMediaPlayer.pause();
				mCurrentState = STATE_PAUSED;
				if (mMediaPlayerController != null)
					mMediaPlayerController.onPause();
			}
		}
		mTargetState = STATE_PAUSED;
	}

	public void resume() {
		if (mSurfaceHolder == null && mCurrentState == STATE_SUSPEND) {
			mTargetState = STATE_RESUME;
		} else if (mCurrentState == STATE_SUSPEND_UNSUPPORTED) {
			openVideo();
		}
	}

	@Override
	public int getDuration() {
		if (isInPlaybackState()) {
			if (mDuration > 0)
				return (int) mDuration;
			mDuration = mMediaPlayer.getDuration();
			return (int) mDuration;
		}
		mDuration = -1;
		return (int) mDuration;
	}

	public MediaInfo getMediaInfo(){
		if(isInPlaybackState()){
			if(mMediaInfo == null){
				mMediaInfo = mMediaPlayer.getMediaInfo();
			}
			return mMediaInfo;
		}
		
		mMediaInfo = null;
		return mMediaInfo;
	}
	
	@Override
	public int getCurrentPosition() {
		if (isInPlaybackState()) {
			long position = mMediaPlayer.getCurrentPosition();
			return (int) position;
		}
		return 0;
	}

	@Override
	public void seekTo(long msec) {
		if (isInPlaybackState()) {
			mMediaPlayer.seekTo(msec);
			mSeekWhenPrepared = 0;
		} else {
			mSeekWhenPrepared = msec;
		}
	}

	@Override
	public boolean isPlaying() {
		return isInPlaybackState() && mMediaPlayer.isPlaying();
	}

	@Override
	public int getBufferPercentage() {
		if (mMediaPlayer != null)
			return mCurrentBufferPercentage;
		return 0;
	}

	public int getVideoWidth() {
		return mVideoWidth;
	}

	public int getVideoHeight() {
		return mVideoHeight;
	}

	protected boolean isInPlaybackState() {
		return (mMediaPlayer != null && mCurrentState != STATE_ERROR
				&& mCurrentState != STATE_IDLE && mCurrentState != STATE_PREPARING);
	}

	public boolean canPause() {
		if(isPlaying())
			return true;
		return false;
	}

	public boolean canSeekBackward() {
		if (this.getDuration() > 0)
			return true;
		return false;
	}

	public boolean canSeekForward() {
		if (this.getDuration() > 0)
			return true;
		return false;
	}

	@Override
	public boolean canStart() {
		return isInPlaybackState();
	}

	@Override
	public void onPlay() {

	}

	@Override
	public void onPause() {

	}
	
	// P1 Added Interface
	public void setAudioAmplify(float ratio) {
		mMediaPlayer.setAudioAmplify(ratio);
	}
	
	public void setVideoRate(float rate) {
		mMediaPlayer.setVideoRate(rate);
	}
	
	public void getPicture(Bitmap bitmap) {
		 mMediaPlayer.getPicture(bitmap);
	}
	
	
}
