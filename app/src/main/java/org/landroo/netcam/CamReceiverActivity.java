package org.landroo.netcam;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.OpenCVFrameConverter;

import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_BGR24;
import static org.bytedeco.javacpp.avutil.AV_PIX_FMT_NV21;
import static org.bytedeco.javacpp.avutil.AV_SAMPLE_FMT_S16;
import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvDecodeImage;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvLoadImage;

public class CamReceiverActivity extends Activity {
	private static final String TAG = "CamReceiver";
	private static int MAX_VIEW = 4;

	private int serverPort = 8040;
	private int miRunning = 0;

	TextView infoText;
	private int recCnt = 0;

	private ImageView[] imageView = new ImageView[4];
	private ImageView[] imageRec = new ImageView[4];
	private ServerSocket server;
	private int viewID[] = new int[4];

	private static PowerManager.WakeLock wakeLock = null;

	private FFmpegFrameRecorder recorder;
	private AndroidFrameConverter converter = new AndroidFrameConverter();
	private int newRecord = 0;
	private int record = -1;
	private int width = 0;
	private int height = 0;
	private boolean isRecord = false;

	private Handler mHandler = new Handler(Looper.getMainLooper()) {
		@Override
		public void handleMessage(Message msg)
		{
			Bitmap image = (Bitmap) msg.obj;
			if(msg.what == 100)
			{
				String sText = getResources().getString(R.string.recImages) + recCnt;
				infoText.setText("IP: " + getLocalIpAddress() + " " + sText);
			}
			else if (image != null)
			{
				boolean bOK = true;
				for (int i = 0; i < MAX_VIEW; i++)
				{
					if (msg.what == viewID[i])
					{
						if (imageView[i].getVisibility() == View.GONE)
							imageView[i].setVisibility(View.VISIBLE);

						imageView[i].setImageBitmap(image);
						imageView[i].invalidate();

						bOK = false;
						break;
					}
				}

				if(bOK)
				{
					String sMess = getResources().getString(R.string.resolution);
					infoText.setText("IP: " + getLocalIpAddress() + " " + sMess);
				}
			}
			else
			{
				for (int i = 0; i < MAX_VIEW; i++)
				{
					if (msg.what == viewID[i])
					{
						imageView[i].setVisibility(View.GONE);
						viewID[i] = -1;
						break;
					}
				}
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_cam_receiver);

		getInfo();

		imageView[0] = (ImageView) findViewById(R.id.receiverPreview1);
		imageView[1] = (ImageView) findViewById(R.id.receiverPreview2);
		imageView[2] = (ImageView) findViewById(R.id.receiverPreview3);
		imageView[3] = (ImageView) findViewById(R.id.receiverPreview4);

		for (int i = 0; i < MAX_VIEW; i++) {
			viewID[i] = -1;
			imageView[i].setVisibility(View.GONE);
			imageView[i].setId(i);
			imageView[i].setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if(record == -1) {
						record = viewID[v.getId()];
						newRecord = 1;
						imageRec[v.getId()].setVisibility(View.VISIBLE);
					}
					else {
						record = -1;
						newRecord = 2;
						imageRec[v.getId()].setVisibility(View.GONE);
					}
				}
			});
		}

		imageRec[0] = (ImageView) findViewById(R.id.receiverRec1);
		imageRec[1] = (ImageView) findViewById(R.id.receiverRec2);
		imageRec[2] = (ImageView) findViewById(R.id.receiverRec3);
		imageRec[3] = (ImageView) findViewById(R.id.receiverRec4);
		for (int i = 0; i < MAX_VIEW; i++) {
			imageRec[i].setVisibility(View.GONE);
			imageRec[i].setId(i);
		}

		infoText = (TextView) findViewById(R.id.receiverIpTextView);
		infoText.setText("IP: " + getLocalIpAddress());

		try {
			server = new ServerSocket(serverPort);

			new ShowTask().execute(0);

		} catch (Exception e) {
			Log.e(TAG, "newSocket " + e);
		}

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "NetCamLock");

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onStop()
	{
		if (server != null)
		{
			try
			{
				server.close();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		super.onStop();
	}

	@Override
	protected void onPause()
	{
		wakeLock.release();
		super.onPause();
	}

	@Override
	protected void onResume()
	{
		wakeLock.acquire();
		super.onResume();
	}

	@Override
	public void onBackPressed()
	{
		if(record != -1) {
			record = -1;
			newRecord = -1;
		}

		if (server != null)
		{
			try
			{
				server.close();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		super.onBackPressed();
	}

	private class ShowTask extends AsyncTask<Integer, Integer, Long>
	{
		protected Long doInBackground(Integer... num)
		{
			try 
			{
				int id = 0;
				ClientServiceThread clientThread = null;
			    while (true)
			    {
			    	Socket socket = server.accept();
			    	clientThread = new ClientServiceThread(socket, id);
			    	clientThread.start();
			    	//Log.i(TAG, "New client " + id);
			    	id++;
			    }
			}
			catch (Exception e) 
			{
				Log.e(TAG, "showTask " + e);
			}
			//Log.i(TAG, "Exit");

			return (long)0;
		}
	}
	
	class ClientServiceThread extends Thread 
	{
		Socket socket;
		public int clientID = -1;
		public boolean running = true;

		ClientServiceThread(Socket soc, int id) 
		{
			socket = soc;
			clientID = id;
			
			for(int i = 0; i < MAX_VIEW; i++)
			{
				if(viewID[i] == -1)
				{
					viewID[i] = id;
					break;
				}
			}
		}
		
		public void run() 
		{
			while (running) 
			{
				try 
				{
					DataInputStream dis = new DataInputStream(socket.getInputStream());
					int len = dis.readInt();
					
					if(len > 0 && len < 200000)
					{
						byte[] buffer = new byte[len];			
						dis.readFully(buffer, 0, len);
						
						final Bitmap image = BitmapFactory.decodeByteArray(buffer, 0, len);
						if(image != null)
						{
							mHandler.obtainMessage(clientID, image).sendToTarget();

							if(width != image.getWidth() && height != image.getHeight() && record != -1){
								newRecord = 1;
							}

							if(newRecord != 0) {
								if (recorder != null) {
									try {
										recorder.stop();
										recorder = null;
									} catch (Exception ex) {
										Log.i(TAG, "stop recorder " + ex);
									}
								}
								if(newRecord == 2)
									newRecord = 0;
							}

							width = image.getWidth();
							height = image.getHeight();

							if(newRecord == 1) {
								startRecorder(image.getWidth(), image.getHeight());
								newRecord = 0;
								record = clientID;
							}

							if(record == clientID && !isRecord) {
								isRecord = true;
								new Thread(new Runnable() {
									public void run() {
										try {
											Frame frame = converter.convert(image);
											recorder.record(frame);
										} catch (OutOfMemoryError e) {
											System.gc();
											Log.e(TAG, "recordThread " + e);
											running = false;
										} catch (Exception ex) {
											Log.e(TAG, "recordThread " + ex);
										}
										recCnt++;
										mHandler.sendEmptyMessage(100);

										isRecord = false;
									}
								}).start();
							}
						}
					}
					else
					{
						running = false;
						mHandler.obtainMessage(clientID, null).sendToTarget();
						//Log.i(TAG, "Exit");
					}

					System.gc();
				}
				catch (OutOfMemoryError e) {
					System.gc();
					Log.e(TAG, "receiverThread " + e);
					running = false;
				}
				catch (Exception e) 
				{
					Log.e(TAG, "receiverThread " + e);
					running = false;
				}
			}

		}
		
	}

	/**
	 * Get the IP address of the device
	 * @return String 192.168.0.1
	 */
	public String getLocalIpAddress()
	{
		String res = "";
		try
		{
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();)
			{
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();)
				{
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address)
					{
						res += " " + inetAddress.getHostAddress();
					}
				}
			}
		}
		catch (Exception ex)
		{
			Log.i(TAG, "getLocalIpAddress " + ex);
		}
		//Log.i(TAG, res);

		return res;
	}
	
	private void getInfo()
	{
		SharedPreferences settings = getSharedPreferences("org.landroo.netcam_preferences", Context.MODE_PRIVATE);
		String txt = settings.getString("default_port", "8040");
		serverPort = Integer.parseInt(txt);
	}

	private void startRecorder(int imageWidth, int imageHeight) {

		//Log.i(TAG,"init recorder");
		String path = getVideoFile();

		recorder = new FFmpegFrameRecorder(path, imageWidth, imageHeight, 1);
		recorder.setFormat("mp4");
		//recorder.setVideoQuality(0);
		recorder.setVideoOption("preset", "veryslow"); // ultrafast veryslow slow medium
		// Set in the surface changed method
		recorder.setFrameRate(5);
		//recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);

		//recorder.setFormat("matroska"); // mp4 doesn't support streaming
		//recorder.setPixelFormat(AV_PIX_FMT_BGR24);
		//recorder.setVideoCodecName("libx264rgb");
		//recorder.setVideoQuality(0); // lossless
		//recorder.setSampleFormat(AV_SAMPLE_FMT_S16);
		//recorder.setSampleRate(44100);
		//recorder.setAudioCodecName("pcm_s16le");

		//recorder.setVideoCodecName("libx264");
		recorder.setVideoCodecName("libopenh264");

		try {
			recorder.start();
		}
		catch (Exception ex){
			Log.e(TAG, "recorderStart " + ex);
		}
		//Log.i(TAG, "recorder initialize success");
	}

	// get attach image file name
	private String getVideoFile()
	{
		File mediaStorageDir = new File(Environment.getExternalStorageDirectory(), "NetCam");
		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists())
		{
			// Failed to create directory
			if (!mediaStorageDir.mkdirs()) return null;
		}

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		String path = mediaStorageDir.getPath() + File.separator + "rec_" + timeStamp + ".mp4";

		return path;
	}

	private void sendMail(String body){
		Intent i = new Intent(Intent.ACTION_SEND);
		i.setType("message/rfc822");
		i.putExtra(Intent.EXTRA_EMAIL, new String[]{"landroo9@gmail.com"});
		i.putExtra(Intent.EXTRA_SUBJECT, "NetCam exception");
		i.putExtra(Intent.EXTRA_TEXT, body);
		try {
			startActivity(Intent.createChooser(i, "Send mail..."));
		} catch (android.content.ActivityNotFoundException ex) {
			Toast.makeText(CamReceiverActivity.this, "There are no email clients installed.", Toast.LENGTH_SHORT).show();
		}
	}
}
