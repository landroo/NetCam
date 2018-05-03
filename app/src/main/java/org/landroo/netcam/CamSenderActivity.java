package org.landroo.netcam;


import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class CamSenderActivity extends Activity
{
	private static final String TAG = "CamSender";

	public Socket toReceiver;
	public DataOutputStream outStream = null;

	private boolean isSend = false;

	private CamPreview mPreview;
	private int port = 8040;

	private int mWidth = 0;
	private int mHeight = 0;
	private int imageFormat = ImageFormat.NV21;
	private int imageQuality = 20;
	private SurfaceHolder mHolder;
	private FrameLayout senderPreview;

	public Camera mCamera;
	private List<Size> supportedSizes;

	private RadioButton frontButton;
	private RadioButton backButton;

	private TextView infoText;
	private int sentCnt = 0;

	private int currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

	private static PowerManager.WakeLock wakeLock = null;


	private Handler mHandler = new Handler(Looper.getMainLooper()) {
		@Override
		public void handleMessage(Message msg) {
			if(msg.what == 0)
			{
				String sText = getResources().getString(R.string.sentImages) + sentCnt;
				infoText.setText(sText);
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_cam_sender);

		getInfo();

		String ip = "192.168.0.1";
		// get parameters
		Bundle extras = getIntent().getExtras().getBundle("camparam");
		if (extras != null)
			ip = extras.getString("ip");

		createCamera();

		senderPreview = (FrameLayout) findViewById(R.id.senderPreview);
		senderPreview.setLayoutParams(new RelativeLayout.LayoutParams(mWidth, mHeight));

		mPreview = new CamPreview(this, ip);
		senderPreview.addView(mPreview);

		Display display = getWindowManager().getDefaultDisplay();
		int displayWidth = display.getWidth();
		int displayHeight = display.getHeight();

		// create preview size radi buttons
		RadioGroup rgp = (RadioGroup) findViewById(R.id.senderSizeGroup);
		RadioGroup.LayoutParams rprms;

		infoText = (TextView) findViewById(R.id.senderInfoText);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		int i = 0;
		for (Size size: supportedSizes)
		{
			if(size.height <= displayHeight)
			{
				RadioButton radioButton = new RadioButton(this);
				radioButton.setText("" + size.width + "x" + size.height);
				radioButton.setId(i);
				radioButton.setTag(size);
				rprms = new RadioGroup.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
				radioButton.setOnClickListener(new View.OnClickListener()
				{
					@Override
					public void onClick(View v)
					{

						try
						{
							mCamera.stopPreview();

							while(isSend);

							Size size = (Size) v.getTag();
							mWidth = size.width;
							mHeight = size.height;

							senderPreview.setLayoutParams(new RelativeLayout.LayoutParams(mWidth, mHeight));

							Camera.Parameters parameters = mCamera.getParameters();
							parameters.setPreviewSize(mWidth, mHeight);

							imageFormat = parameters.getPreviewFormat();

							mCamera.setParameters(parameters);

							mCamera.setPreviewCallback(new PreviewCallback()
							{
								// Called for each frame previewed
								public void onPreviewFrame(byte[] data, Camera camera)
								{
									System.gc();
									if(!isSend && outStream != null)
									{
										isSend = true;
										new SendTask().execute(data);
									}
								}
							});

							mCamera.startPreview();

						}
						catch (Exception ex)
						{
							//Log.i(TAG, "" + ex);
							String sMess = getResources().getString(R.string.resolution);
							Toast.makeText(CamSenderActivity.this, sMess, Toast.LENGTH_LONG).show();
						}

						RadioButton rb = (RadioButton) findViewById(R.id.senderPreviewVisible);
						rb.setChecked(true);
					}
				});
				rgp.addView(radioButton, rprms);
				if(size.height == mHeight)
					radioButton.setChecked(true);
				i++;
			}
		}

		if(Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO && Camera.getNumberOfCameras() > 1)
		{
			rgp = (RadioGroup) findViewById(R.id.senderCameraGroup);
			frontButton = new RadioButton(this);
			frontButton.setId(R.id.frontButton);
			frontButton.setText("Front");
			rprms = new RadioGroup.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			frontButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					switchCamera();
					try {
						mCamera.setPreviewCallback(new PreviewCallback() {
							// Called for each frame previewed
							public void onPreviewFrame(byte[] data, Camera camera)
							{
								System.gc();
								if(!isSend && outStream != null)
								{
									isSend = true;
									new SendTask().execute(data);
								}
							}
						});
					}
					catch (Exception ex)
					{
						Log.e(TAG, "back button " + ex);
					}
				}
			});
			rgp.addView(frontButton, rprms);

			backButton = new RadioButton(this);
			backButton.setId(R.id.backButton);
			backButton.setText("Back");
			backButton.setChecked(true);
			rprms = new RadioGroup.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			backButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					switchCamera();
					try {
						mCamera.setPreviewCallback(new PreviewCallback() {
							// Called for each frame previewed
							public void onPreviewFrame(byte[] data, Camera camera) {
								System.gc();
								if (!isSend && outStream != null) {
									isSend = true;
									new SendTask().execute(data);
								}
							}
						});
					}
					catch (Exception ex)
					{
						Log.e(TAG, "back button " + ex);
					}
				}
			});
			rgp.addView(backButton, rprms);
		}

		RadioButton rb = (RadioButton) findViewById(R.id.senderPreviewVisible);
		rb.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				senderPreview.setLayoutParams(new RelativeLayout.LayoutParams(mWidth, mHeight));

				infoText.setVisibility(View.INVISIBLE);
			}
		});

		rb = (RadioButton) findViewById(R.id.senderPreviewInvisible);
		rb.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				senderPreview.setLayoutParams(new RelativeLayout.LayoutParams(1, 1));

				infoText.setVisibility(View.VISIBLE);
			}
		});

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "NetCamLock");

	}

	@Override
	protected void onPause()
	{
		super.onPause();

		try
		{
			if(mCamera != null)
				mCamera.stopPreview();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		wakeLock.release();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		try
		{
			if(mCamera != null)
				mCamera.startPreview();
			else
			{
				mCamera = Camera.open(currentCameraId);

				// acquire the parameters for the camera
				Camera.Parameters parameters = mCamera.getParameters();
				parameters.setPreviewSize(mWidth, mHeight);

				mCamera.setParameters(parameters);// setting the parameters to the camera but this line is not required
				mCamera.setPreviewCallback(new PreviewCallback()
				{
					// Called for each frame previewed
					public void onPreviewFrame(byte[] data, Camera camera)
					{
						try
						{
							System.gc();
							if(!isSend && outStream != null)
							{
								isSend = true;
								new SendTask().execute(data);
							}
						}
						catch (Exception ex)
						{
							Log.i(TAG, "" + ex);
						}
					}
				});

				String ip = "192.168.0.1";
				// get parameters
				Bundle extras = getIntent().getExtras().getBundle("camparam");
				if (extras != null)
					ip = extras.getString("ip");

				mPreview = new CamPreview(this, ip);

				senderPreview.setLayoutParams(new RelativeLayout.LayoutParams(mWidth, mHeight));
				senderPreview.addView(mPreview);

				outStream = new DataOutputStream(toReceiver.getOutputStream());
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		wakeLock.acquire();
	}

	@Override
	protected void onStop()
	{
		super.onStop();

		if(outStream != null)
		{
			try
			{
				outStream.writeInt(-1);
				outStream.close();
				outStream = null;
			}
			catch (Exception e)
			{
				Log.i(TAG, "outStream " + e);
			}
		}

		if(toReceiver != null)
		{
			try
			{
				toReceiver.close();
			}
			catch(Exception ex)
			{
				Log.i(TAG, "toReceiver " + ex);
			}
		}

		try
		{
			if (mCamera != null)
			{
				// Call stopPreview() to stop updating the preview surface.
				mCamera.stopPreview();

				mCamera.setPreviewCallback(null);
				mPreview.getHolder().removeCallback(mPreview);

				// Important: Call release() to release the camera for use by other
				// applications. Applications should release the camera immediately
				// during onPause() and re-open() it during onResume()).
				mCamera.release();

				mCamera = null;
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	class CamPreview extends SurfaceView implements SurfaceHolder.Callback
	{

		private String ip = "192.168.0.124";

		public CamPreview(Context context, String ip)
		{
			super(context);

			mHolder = getHolder();// initialize the Surface Holder
			mHolder.addCallback(this); // add the call back to the surface holder
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

			this.ip = ip;

			new ConnectTask().execute();
		}

		// Called once the holder is ready
		public void surfaceCreated(SurfaceHolder holder)
		{
			// The Surface has been created, acquire the camera and tell it where to draw.
			try
			{
				mCamera.setPreviewDisplay(holder);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

		// Called when the holder is destroyed
		public void surfaceDestroyed(SurfaceHolder holder)
		{
			if(outStream != null)
			{
				try
				{
					outStream.writeInt(-1);
					outStream.close();
					outStream = null;
				}
				catch (Exception e)
				{
					Log.i(TAG, "outStream " + e);
				}
			}

			if(toReceiver != null)
			{
				try
				{
					toReceiver.close();
				}
				catch(Exception ex)
				{
					Log.i(TAG, "toReceiver " + ex);
				}
			}
		}

		// Called when holder has changed
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
		{
			try
			{
				if(mCamera == null)
					mCamera = Camera.open();// activate the camera
				if(mCamera != null)
					mCamera.startPreview();// camera frame preview starts when user launches application screen

				Camera.Parameters parameters = mCamera.getParameters();
				supportedSizes = parameters.getSupportedPictureSizes();

				int w = Integer.MAX_VALUE;
				for(Size s:supportedSizes)
					if(s.width < w) w = s.width;
				int i = 0;
				for(Size s:supportedSizes)
				{
					if(s.width == w) break;
					i++;
				}
				parameters.setPreviewSize(mWidth, mHeight);

				mCamera.setParameters(parameters);// setting the parameters to the camera but this line is not required
				mCamera.setPreviewCallback(new PreviewCallback()
				{
					// Called for each frame previewed
					public void onPreviewFrame(byte[] data, Camera camera)
					{
						System.gc();
						if(!isSend && outStream != null)
						{
							isSend = true;
							new SendTask().execute(data);
						}
					}
				});

				outStream = new DataOutputStream(toReceiver.getOutputStream());
			} // open an outputstream to the socket for sending the image data
			catch (Exception e)
			{
				Log.i(TAG, "outStream " + e);
			}
		}

		//
		private class ConnectTask extends AsyncTask<Integer, Integer, Long>
		{
			protected Long doInBackground(Integer... data)
			{
				while(outStream == null)
				{
					try
					{
						Thread.sleep(1000);
						// connect to server socket of Ip address
						toReceiver = new Socket(ip, port);
						outStream = new DataOutputStream(toReceiver.getOutputStream());
					} // open an outputstream to the socket for sending the image data
					catch (Exception e)
					{
						Log.i(TAG, "ConnectTask " + e);
					}
				}

				return (long)0;
			}
		}
	}

	private boolean createCamera()
	{
		try
		{
			mCamera = Camera.open(); // attempt to get a Camera instance
			if(mCamera == null)
				mCamera = Camera.open(0);
			if(mCamera == null)
			{
				mCamera = Camera.open(1);
				currentCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;

				frontButton.setChecked(true);
				backButton.setChecked(false);
			}
			if(mCamera == null)
				return false;

			// acquire the parameters for the camera
			Camera.Parameters parameters = mCamera.getParameters();

			supportedSizes = parameters.getSupportedPictureSizes();
			int w = Integer.MAX_VALUE;
			for (Size s : supportedSizes)
				if (s.width < w) w = s.width;
			int i = 0;
			for (Size s : supportedSizes) {
				if (s.width == w) break;
				i++;
			}

			while(isSend);

			mWidth = supportedSizes.get(i).width;
			mHeight = supportedSizes.get(i).height;
			parameters.setPreviewSize(mWidth, mHeight);

			mCamera.setParameters(parameters);// setting the parameters to the camera but this line is not required
			mCamera.setPreviewCallback(new PreviewCallback() {
				// Called for each frame previewed
				public void onPreviewFrame(byte[] data, Camera camera) {
					System.gc();
					if (!isSend && outStream != null) {
						isSend = true;
						new SendTask().execute(data);
					}
				}
			});

			imageFormat = parameters.getPreviewFormat();
		}
		catch (Exception ex)
		{
			Log.e(TAG, "" + ex);
		}

		return true;
	}

	//
	private class SendTask extends AsyncTask<byte[], Integer, Long>
	{
		protected Long doInBackground(byte[]... data)
		{
			try
			{
				//Log.i(TAG, "Compress " + mWidth + " " + mHeight);

				Rect rect = new Rect(0, 0, mWidth, mHeight);
				ByteArrayOutputStream output_stream = new ByteArrayOutputStream();

				YuvImage yuv_image = new YuvImage(data[0], imageFormat, mWidth, mHeight, null);
				// all bytes are in YUV format therefore to use the YUV helper functions we are putting in a YUV object
				yuv_image.compressToJpeg(rect, imageQuality, output_stream);
				// image has now been converted to the jpg format and bytes have been written to the output_stream object

				byte[] tmp = output_stream.toByteArray();

				outStream.writeInt(tmp.length);
				outStream.write(tmp);// writing the array to the socket output stream
				outStream.flush();

				sentCnt++;
				mHandler.sendEmptyMessage(0);
			}
			catch (OutOfMemoryError e) {
				System.gc();
				Log.i(TAG, "SendTask " + e);
			}
			catch(Exception ex)
			{
				Log.i(TAG, "SendTask " + ex);
			}

			isSend = false;

			return (long)0;
		}
	}

	public void switchCamera()
	{
		try
		{
			mCamera.stopPreview();

			mCamera.setPreviewCallback(null);
			mPreview.getHolder().removeCallback(mPreview);

			//NB: if you don't release the current camera before switching, you app will crash
			mCamera.release();

			//swap the id of the camera to be used
			if(currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK)
			{
				currentCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
			}
			else
			{
				currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
			}

			mCamera = Camera.open(currentCameraId);

			//this step is critical or preview on new camera will no know where to render to
			mCamera.setPreviewDisplay(mHolder);

			Camera.Parameters parameters = mCamera.getParameters();
			parameters.setPreviewSize(mWidth, mHeight);

			mCamera.setParameters(parameters);

			imageFormat = parameters.getPreviewFormat();

			mCamera.startPreview();
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	private void getInfo()
	{
		SharedPreferences settings = getSharedPreferences("org.landroo.netcam_preferences", Context.MODE_PRIVATE);
		String txt = settings.getString("default_port", "8040");
		port = Integer.parseInt(txt);
	}
}
