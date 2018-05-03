package org.landroo.netcam;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.util.Base64;
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

import org.w3c.dom.Text;

public class CamServerActivity extends Activity 
{
	private static final String TAG = "CamServer";

	private ThreadPooledServer mServer;
	private ServerPreview mPreview;

	private SurfaceHolder mHolder;
	private Camera mCamera;
	private byte[] mData = null;
	private boolean inProgress = false;
	private int mWidth = 0;
	private int mHeight = 0;
	private List<Size> supportedSizes;
	private int currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;

	private String serverIp;
	private int serverPort = 8040;

	private RadioButton frontButton;
	private RadioButton backButton;

	private TextView infoText;
	private int sentCnt = 0;
	
	private FrameLayout serverPreview;
	
	private String indexHtml;
	
	private int imageFormat = ImageFormat.JPEG;
	
	private int fileCnt = 0;
	private int fileMax = 99;
	private boolean saveImage = true;

	private static PowerManager.WakeLock wakeLock = null;

	private boolean saveTime = false;
	private int saveSec = 5000;

	private Handler mHandler = new Handler(Looper.getMainLooper())
	{
		@Override
		public void handleMessage(Message msg)
		{
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
		setContentView(R.layout.activity_cam_server);
		
		getInfo();

		serverIp = getLocalIpAddress() + ":" + serverPort;

		TextView tv = (TextView)findViewById(R.id.serverIpTextView);
		tv.setText("http://" + serverIp);
		
		indexHtml = loadAsset("index.html");

		createCamera();

		mPreview = new ServerPreview(this);

		serverPreview = (FrameLayout) findViewById(R.id.serverPreview);
		serverPreview.setLayoutParams(new RelativeLayout.LayoutParams(mWidth, mHeight));
		serverPreview.addView(mPreview);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		RadioGroup rgp = (RadioGroup) findViewById(R.id.serverSizeGroup);
		RadioGroup.LayoutParams rprms;

		infoText = (TextView) findViewById(R.id.serverInfoText);

		Display display = getWindowManager().getDefaultDisplay();
		int displayWidth = display.getWidth();
		int displayHeight = display.getHeight();

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

							while(inProgress);

							Size size = (Size) v.getTag();
							mWidth = size.width;
							mHeight = size.height;

							FrameLayout fl = (FrameLayout) findViewById(R.id.serverPreview);
							fl.setLayoutParams(new RelativeLayout.LayoutParams(mWidth, mHeight));

							Camera.Parameters parameters = mCamera.getParameters();
							parameters.setPreviewSize(mWidth, mHeight);

							mCamera.setParameters(parameters);
							mCamera.setPreviewCallback(new PreviewCallback() 
							{
								// Called for each frame previewed
								public void onPreviewFrame(byte[] data, Camera camera) 
								{
									try
									{
										if (!inProgress)
											//mData = data;
											//mData = Arrays.copyOf(data, data.length);
											mData = data.clone();
										//else
										//Log.i(TAG, "Data dropped!");
									}
									catch (Exception ex)
									{
										Log.i(TAG, "" + ex);
									}
								}
							});

							imageFormat = parameters.getPreviewFormat();

							mCamera.startPreview();

						}
						catch (Exception ex)
						{
							String sMess = getResources().getString(R.string.resolution);
							Toast.makeText(CamServerActivity.this, sMess, Toast.LENGTH_LONG).show();
							if(!inProgress)
								mData = null;
							//else
								//Log.i(TAG, "Data dropped!");
							v.setVisibility(View.GONE);
						}

						RadioButton rb = (RadioButton) findViewById(R.id.preview_visible);
						rb.setChecked(true);

						infoText.setVisibility(View.INVISIBLE);
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
			rgp = (RadioGroup) findViewById(R.id.serverCameraGroup);
			frontButton = new RadioButton(this);
			frontButton.setId(R.id.frontButton);
			String txt = getResources().getString(R.string.front);
			frontButton.setText(txt);
			rprms = new RadioGroup.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			frontButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					switchCamera();
					try
					{
						mCamera.setPreviewCallback(new PreviewCallback()
						{
							// Called for each frame previewed
							public void onPreviewFrame(byte[] data, Camera camera)
							{
								try
								{
									if (!inProgress)
										//mData = data;
										//mData = Arrays.copyOf(data, data.length);
										mData = data.clone();
									//else
									//Log.i(TAG, "Data dropped!");
								}
								catch (Exception ex)
								{
									Log.i(TAG, "" + ex);
								}
							}
						});
					}
					catch (Exception ex)
					{
						Log.e(TAG, "front button " + ex);
					}
				}
			});
			rgp.addView(frontButton, rprms);

			backButton = new RadioButton(this);
			backButton.setId(R.id.backButton);
			txt = getResources().getString(R.string.back);
			backButton.setText(txt);
			backButton.setChecked(true);
			rprms = new RadioGroup.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			backButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					try
					{
						switchCamera();
						mCamera.setPreviewCallback(new PreviewCallback()
						{
							// Called for each frame previewed
							public void onPreviewFrame(byte[] data, Camera camera)
							{
								try
								{
									if (!inProgress)
										//mData = data;
										//mData = Arrays.copyOf(data, data.length);
										mData = data.clone();
									//else
									//Log.i(TAG, "Data dropped!");
								}
								catch (Exception ex)
								{
									Log.i(TAG, "" + ex);
								}
							}
						});
					}
					catch (Exception ex)
					{
						Log.e(TAG, "back Button " + ex);
					}
				}
			});
			rgp.addView(backButton, rprms);
		}
		
		RadioButton rb = (RadioButton) findViewById(R.id.preview_visible);
		rb.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v) 
			{
				serverPreview.setLayoutParams(new RelativeLayout.LayoutParams(mWidth, mHeight));

				infoText.setVisibility(View.INVISIBLE);
			}
		});
		
		rb = (RadioButton) findViewById(R.id.preview_invisible);
		rb.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v) 
			{
				serverPreview.setLayoutParams(new RelativeLayout.LayoutParams(1, 1));

				infoText.setVisibility(View.VISIBLE);
			}
		});

		mServer = new ThreadPooledServer(serverPort);
		new Thread(mServer).start();

		Timer saveTimer = new Timer();
		saveTimer.scheduleAtFixedRate(new SaveTask(), 10, saveSec);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, "NetCamLock");
	}

	@Override
	protected void onPause()
	{
		try
		{
			if (mCamera != null)
				mCamera.stopPreview();
		}
		catch (Exception ex)
		{
			Log.e(TAG, "" + ex);
		}
		wakeLock.release();

		super.onPause();
	}

	@Override
	protected void onResume()
	{
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
							if (!inProgress)
								//mData = data;
								//mData = Arrays.copyOf(data, data.length);
								mData = data.clone();
							//else
							//Log.i(TAG, "Data dropped!");
						}
						catch (Exception ex)
						{
							Log.i(TAG, "" + ex);
						}
					}
				});

				mPreview = new ServerPreview(this);

				serverPreview.setLayoutParams(new RelativeLayout.LayoutParams(mWidth, mHeight));
				serverPreview.addView(mPreview);

				mServer = new ThreadPooledServer(serverPort);
				new Thread(mServer).start();
			}
		}
		catch (Exception ex)
		{
			Log.e(TAG, "" + ex);
		}
		wakeLock.acquire();

		super.onResume();
	}
	
	@Override
	protected void onStop() 
	{
		if(mServer != null)
		{
			mServer.stop();
		}
		
		if(mPreview != null)
		{
			stopPreviewAndFreeCamera();
		}

		super.onStop();
	}
	
	/**
	 * one server thread
	 */
	public class WorkerRunnable implements Runnable
	{
	    protected Socket clientSocket = null;
	    protected String serverText   = null;

	    public WorkerRunnable(Socket clientSocket, String serverText) 
	    {
	        this.clientSocket = clientSocket;
	        this.serverText   = serverText;
	    }

	    public void run() 
	    {
	        try 
	        {
				InputStream input  = clientSocket.getInputStream();
				OutputStream output = clientSocket.getOutputStream();

				byte[] inBuff = new byte[1024];
				DataInputStream in = new DataInputStream(input);
				int bytesRead = in.read(inBuff);
				String header = new String(inBuff, 0, bytesRead);
				HttpRequestParser httpp = new HttpRequestParser();
				httpp.parseRequest(header);
				String page = httpp.getPage();
				
				// chrome:  User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36
				// firefox: User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64; rv:39.0) Gecko/20100101 Firefox/39.0
				// ie11:    User-Agent: Mozilla/5.0 (Windows NT 10.0; WOW64; Trident/7.0; rv:11.0) like Gecko				
				// edge:    User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/46.0.2486.0 Safari/537.36 Edge/13.10586
				boolean ie = false;
				String userAgent = httpp.getHeaderParam("User-Agent"); 
				if(userAgent.contains("Trident") || userAgent.contains("Edge"))
					ie = true;

				try
				{
					if (mData != null)
					{
						if (page.equals("/image.jpg"))
							sendJpeg(output, ie);
						else if (page.equals("/image.html"))
							sendBase64(output);
						else if(page.length() > 10 && page.substring(0, 7).equals("/image0"))
							sendSaved(output, page, ie);
						else if (page.equals("/imagelist.html"))
							sendImageList(output);
						else
							sendHtml(output);
					}
				}
				catch(Exception ex)
				{
					Log.i(TAG, "WorkerRunnable " + ex);
				}

	            output.close();
	            input.close();
	            //Log.i(TAG, "Request processed: " + this.serverText);

				sentCnt++;
				mHandler.sendEmptyMessage(0);
			}
	        catch (Exception e)
	        {
	            //report exception somewhere.
	            e.printStackTrace();
	        }
	    }
	}
	
	/**
	 * server thread
	 */
	public class ThreadPooledServer implements Runnable
	{
	    protected int serverPort = 8040;
	    protected ServerSocket serverSocket = null;
	    protected boolean isStopped = false;
	    protected Thread runningThread = null;
	    protected ExecutorService threadPool = Executors.newFixedThreadPool(10);

	    public ThreadPooledServer(int port)
	    {
	        this.serverPort = port;
	    }

		private int cnt = 0;

	    public void run()
	    {
	        synchronized(this)
	        {
	            this.runningThread = Thread.currentThread();
	        }
	        
	        openServerSocket();
	        
	        while(!isStopped())
	        {
	            Socket clientSocket;
	            try 
	            {
	                clientSocket = this.serverSocket.accept();
	            } 
	            catch(Exception ex)
	            {
	                if(isStopped()) 
	                {
	                    //Log.i(TAG, "Server Stopped.");
	                    break;
	                }
	                throw new RuntimeException("Error accepting client connection", ex);
	            }

				String id = "Thread Pooled Server " + cnt++;
	            
	            this.threadPool.execute(new WorkerRunnable(clientSocket, id));
	        }
	        
	        this.threadPool.shutdown();
	        //Log.i(TAG, "Server Stopped.");
	    }

	    private synchronized boolean isStopped() 
	    {
	        return this.isStopped;
	    }

	    public synchronized void stop()
	    {
	        this.isStopped = true;
	        try 
	        {
	            this.serverSocket.close();
	        } 
	        catch (Exception ex)
	        {
	            throw new RuntimeException("Error closing server", ex);
	        }
	    }

	    private void openServerSocket() 
	    {
	        try 
	        {
	            this.serverSocket = new ServerSocket(this.serverPort);
	        }
	        catch (Exception ex)
	        {
	            throw new RuntimeException("Cannot open port " + serverPort, ex);
	        }
	    }
	}

	/**
	 * camera preview surface
	 */
	class ServerPreview extends SurfaceView implements SurfaceHolder.Callback 
	{
		public ServerPreview(Context context)
		{
			super(context);
			
			mHolder = getHolder();
	        mHolder.addCallback(this);
	        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		@Override
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

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) 
		{
			try
			{
				// Now that the size is known, set up the camera parameters and begin the preview.
				Camera.Parameters parameters = mCamera.getParameters();

				supportedSizes = parameters.getSupportedPictureSizes();
				int w = Integer.MAX_VALUE;
				for(Size s:supportedSizes)
					if(s.width < w) w = s.width;
				for(Size s:supportedSizes)
				{
					if(s.width == w) break;
				}
				parameters.setPreviewSize(mWidth, mHeight);

				requestLayout();
				try
				{
					mCamera.setParameters(parameters);
				}
				catch (Exception ex)
				{
					String sMess = getResources().getString(R.string.resolution);
					Toast.makeText(CamServerActivity.this, sMess, Toast.LENGTH_LONG).show();
					if(!inProgress)
						mData = null;
					//else
						//Log.i(TAG, "Data dropped!");
				}

				// Important: Call startPreview() to start updating the preview surface.
				// Preview must be started before you can take a picture.
				mCamera.startPreview();
			}
			catch (Exception ex)
			{
				Log.e(TAG, "" + ex);
			}

		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			// Surface will be destroyed when we return, so stop the preview.
			try {
				if (mCamera != null) {
					// Call stopPreview() to stop updating the preview surface.
					mCamera.stopPreview();
				}
			}
			catch (Exception ex)
			{
				Log.i(TAG, "" + ex);
			}
		}
	}

	/**
	 * get local IP address
 	 * @return String
	 */
	private String getLocalIpAddress()
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
						res += inetAddress.getHostAddress();
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

	/**
	 * Http Request Parser
	 */
	public class HttpRequestParser
	{
		private String _requestLine;// GET /image.jpg?nocache=1450171107343 HTTP/1.1
		private String _method;
		private String _httpVersion;
		private String _httpPage;
		private Hashtable<String, String> _requestParams;
		private Hashtable<String, String> _requestHeaders;
		private StringBuffer _messagetBody;

		public class HttpFormatException extends Exception
		{
			private static final long serialVersionUID = 837910846054497673L;

			public HttpFormatException()
			{
				// TODO Auto-generated constructor stub
			}

			public HttpFormatException(String message)
			{
				super(message);
				// TODO Auto-generated constructor stub
			}

			public HttpFormatException(Throwable cause)
			{
				super(cause);
				// TODO Auto-generated constructor stub
			}

			public HttpFormatException(String message, Throwable cause)
			{
				super(message, cause);
				// TODO Auto-generated constructor stub
			}
		}

		public HttpRequestParser()
		{
			_requestHeaders = new Hashtable<String, String>();
			_requestParams = new Hashtable<String, String>();
			_messagetBody = new StringBuffer();
		}

		/**
		 * Parse and HTTP request.
		 *
		 * @param request
		 *            String holding http request.
		 * @throws Exception
		 *             If an I/O error occurs reading the input stream.
		 * @throws HttpFormatException
		 *             If HTTP Request is malformed
		 */
		public void parseRequest(String request) throws Exception, HttpFormatException
		{
			BufferedReader reader = new BufferedReader(new StringReader(request));

			setRequestLine(reader.readLine()); // Request-Line ; Section 5.1

			String header = reader.readLine();
			while (header.length() > 0)
			{
				appendHeaderParameter(header);
				header = reader.readLine();
			}

			String bodyLine = reader.readLine();
			while (bodyLine != null)
			{
				appendMessageBody(bodyLine);
				bodyLine = reader.readLine();
			}

			String[] headArr = _requestLine.split(" ");
			_method = headArr[0];
			_httpVersion = headArr[2];

			headArr = headArr[1].split("[?]");
			_httpPage = headArr[0];
			if(headArr.length > 1)
			{
				headArr = headArr[1].split("&");
				if (headArr.length > 0)
				{
					String[] pair;
					for (int i = 0; i < headArr.length; i++)
					{
						pair = headArr[i].split("=");
						if (pair.length == 1)
							_requestParams.put(pair[0], "");
						if (pair.length == 2)
							_requestParams.put(pair[0], pair[1]);
					}
				}
			}
		}

		/**
		 *
		 * 5.1 Request-Line The Request-Line begins with a method token, followed by
		 * the Request-URI and the protocol version, and ending with CRLF. The
		 * elements are separated by SP characters. No CR or LF is allowed except in
		 * the final CRLF sequence.
		 *
		 * @return String with Request-Line
		 */
		public String getRequestLine() {
			return _requestLine;
		}

		private void setRequestLine(String requestLine) throws HttpFormatException
		{
			if (requestLine == null || requestLine.length() == 0)
			{
				throw new HttpFormatException("Invalid Request-Line: " + requestLine);
			}
			_requestLine = requestLine;
		}

		private void appendHeaderParameter(String header) throws HttpFormatException
		{
			int idx = header.indexOf(":");
			if (idx == -1)
			{
				throw new HttpFormatException("Invalid Header Parameter: " + header);
			}
			_requestHeaders.put(header.substring(0, idx), header.substring(idx + 1, header.length()));
		}

		/**
		 * The message-body (if any) of an HTTP message is used to carry the
		 * entity-body associated with the request or response. The message-body
		 * differs from the entity-body only when a transfer-coding has been
		 * applied, as indicated by the Transfer-Encoding header field (section
		 * 14.41).
		 * @return String with message-body
		 */
		public String getMessageBody() {
			return _messagetBody.toString();
		}

		private void appendMessageBody(String bodyLine) {
			_messagetBody.append(bodyLine).append("\r\n");
		}

		/**
		 * For list of available headers refer to sections: 4.5, 5.3, 7.1 of RFC 2616
		 * @param headerName Name of header
		 * @return String with the value of the header or null if not found.
		 */
		public String getHeaderParam(String headerName){
			return _requestHeaders.get(headerName);
		}

		public String getPage()
		{
			return _httpPage;
		}

		public String getMethod()
		{
			return _method;
		}

		public String getVersion()
		{
			return _httpVersion;
		}
	}

	/**
	 * send jpeg file
	 * @param output OutputStream
	 * @param ie boolean
	 * @throws Exception
	 */
	private synchronized void sendJpeg(OutputStream output, boolean ie) throws Exception
	{
		if(mData == null)
			return;
		//Log.i(TAG, "start sending jpeg " + mWidth + "x" + mHeight);
		long timestamp = System.currentTimeMillis();
		inProgress = true;
		Rect rect = new Rect(0, 0, mWidth, mHeight);
		// all bytes are in YUV format therefore to use the YUV helper functions we are putting in a YUV object
		YuvImage yuv_image = new YuvImage(mData, imageFormat, mWidth, mHeight, null);
		String boundary = "netcam" + timestamp + "netcam";
		String header = "HTTP/1.0 200 OK\r\n" +
				"Server: CamServer\r\n" +
				"Connection: close\r\n" +
				"Max-Age: 0\r\n" +
				"Expires: 0\r\n" +
				"Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
				"Pragma: no-cache\r\n";
		if(!ie)
			header += "Content-Type: multipart/x-mixed-replace; ";
		else
			header += "Content-Type: multipart/form-data; ";
		header += "boundary=" + boundary + "\r\n\r\n";
		output.write(header.getBytes());
		if(!ie)
			output.write(("--" + boundary + "\r\n" +
				"Content-Type: image/jpeg\r\n" +
				"Content-Length: " + mData.length + "\r\n" +
				"X-Timestamp:" + timestamp + "\r\n" +
				"\r\n").getBytes());

		// image has now been converted to the jpg format and bytes have been written to the output_stream object
		if(saveImage && saveTime)
		{
			ByteArrayOutputStream output_stream = new ByteArrayOutputStream();
			yuv_image.compressToJpeg(rect, 30, output_stream);
			saveImage(output_stream.toByteArray());
			output.write(output_stream.toByteArray());
			
			saveTime = false;
		}
		else
		{
			yuv_image.compressToJpeg(rect, 30, output);
		}
		inProgress = false;

		output.write(("\r\n--" + boundary + "--\r\n").getBytes());
		//Log.i(TAG, "end sending jpeg " + mWidth + "x" + mHeight);
	}

	/**
	 * send base 64 encoded image in a html page
	 * @param output OutputStream
	 * @throws Exception
	 */
	private synchronized void sendBase64(OutputStream output) throws Exception
	{
		if(mData == null)
			return;

		long timestamp = System.currentTimeMillis();
		
		inProgress = true;
		ByteArrayOutputStream byteArrayBitmapStream = new ByteArrayOutputStream();
		Rect rect = new Rect(0, 0, mWidth, mHeight);
		// all bytes are in YUV format therefore to use the YUV helper functions we are putting in a YUV object
		YuvImage yuv_image = new YuvImage(mData, imageFormat, mWidth, mHeight, null);
		// image has now been converted to the jpg format and bytes have been written to the output_stream object
		yuv_image.compressToJpeg(rect, 30, byteArrayBitmapStream);
		inProgress = false;

		byte[] b = byteArrayBitmapStream.toByteArray();
		String encodedImage = Base64.encodeToString(b, Base64.NO_WRAP);
		
		output.write(("HTTP/1.0 200 OK\r\n" +
				"Server: CamServer\r\n" +
				"Connection: close\r\n" +
				"Max-Age: 0\r\n" +
				"Expires: 0\r\n" +
				"Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
				"Pragma: no-cache\r\n" +
				"Content-type: text/html\r\n" +
				"X-Timestamp:" + timestamp + "\r\n" +
				"\r\n").getBytes());

		output.write((
				"<!DOCTYPE html>\r\n" +
				"<html>\r\n" +
				"<head>\r\n" +
				"<title></title>\r\n" +
				"<meta charset=\"utf-8\" />\r\n" +
				"</head>\r\n" +
				"<body>\r\n" +
				"<img src=\"data:image/gif;base64," + encodedImage + "\"" +
				"alt=\"Base64 encoded image\" width=\"" + mWidth + "\" height=\"" + mHeight + "\"/>\r\n" +
				"</body>\r\n" +
				"</html>\r\n"+
				"\r\n").getBytes());
	}

	/**
	 * send html
	 * @param output OutputStream
	 * @throws Exception
	 */
	private void sendHtml(OutputStream output) throws Exception
	{
		long timestamp = System.currentTimeMillis();
		output.write(("HTTP/1.0 200 OK\r\n" +
				"Server: CamServer\r\n" +
				"Connection: close\r\n" +
				"Max-Age: 0\r\n" +
				"Expires: 0\r\n" +
				"Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
				"Pragma: no-cache\r\n" +
				"Content-type: text/html\r\n" +
				"X-Timestamp:" + timestamp + "\r\n" +
				"\r\n").getBytes());
		
		output.write((indexHtml).getBytes());
	}

	/**
	 * create camera
	 */
	private boolean createCamera()
	{
		try {
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
			for (Size s : supportedSizes)
			{
				if (s.width == w) break;
				i++;
			}

			while(inProgress);

			mWidth = supportedSizes.get(i).width;
			mHeight = supportedSizes.get(i).height;
			parameters.setPreviewSize(mWidth, mHeight);

			mCamera.setParameters(parameters);// setting the parameters to the camera but this line is not required
			mCamera.setPreviewCallback(new PreviewCallback()
			{
				// Called for each frame previewed
				public void onPreviewFrame(byte[] data, Camera camera)
				{
					try
					{
						if (!inProgress)
							//mData = data;
							//mData = Arrays.copyOf(data, data.length);
							mData = data.clone();
						//else
						//Log.i(TAG, "Data dropped!");
					}
					catch (Exception ex)
					{
						Log.i(TAG, "" + ex);
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

	/**
	 * switch front and back camera
	 */
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

	/**
	 * stop Preview and Free Camera
	 */
	private void stopPreviewAndFreeCamera()
	{
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

	/**
	 * load asset file
	 * @param fName String
	 * @return String
	 */
	private String loadAsset(String fName)
	{
		StringBuffer sb = new StringBuffer();
		BufferedReader reader = null;
		try 
		{
		    reader = new BufferedReader(new InputStreamReader(getAssets().open(fName), "UTF-8"));

		    // do reading, usually loop until end of file reading  
		    String line;
		    while ((line = reader.readLine()) != null) 
		    {
		    	//process line
		    	sb.append(line);
		    	sb.append("\n");
		    }
		} 
		catch (Exception e)
		{
		    Log.e(TAG, "" + e);
		} 
		finally 
		{
		    if (reader != null) 
		    {
		         try 
		         {
		             reader.close();
		         } 
		         catch (Exception e)
		         {
		        	 Log.e(TAG, "" + e);
		         }
		    }
		}

		return sb.toString();
	}

	/**
	 * send a saved image
	 * @param output OutputStream
	 * @param imageName String
	 * @param ie boolean
	 * @throws Exception
	 */
	private void sendSaved(OutputStream output, String imageName, boolean ie) throws Exception
	{
		String fileName = getCacheDir().getPath() + imageName;
		File file = new File(fileName);
		if(file.exists())
		{
			long timestamp = System.currentTimeMillis();
			String boundary = "netcam" + timestamp + "netcam";
			output.write(("HTTP/1.0 200 OK\r\n" +
					"Server: CamServer\r\n" +
					"Connection: close\r\n" +
					"Max-Age: 0\r\n" +
					"Expires: 0\r\n" +
					"Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
					"Pragma: no-cache\r\n" +
					"Content-Type: multipart/x-mixed-replace; " +
					"boundary=" + boundary + "\r\n" +
					"\r\n").getBytes());
			if(!ie)
				output.write((
					"--" + boundary + "\r\n" +
					"Content-Type: image/jpeg\r\n" +
					"Content-Length: " + mData.length + "\r\n" +
					"X-Timestamp:" + timestamp + "\r\n" +
					"\r\n").getBytes());
			
			BufferedInputStream is = new BufferedInputStream(new FileInputStream(fileName));
			int totalBytes = is.available();
			for (int j = 0; j < totalBytes; j++)
			{
				// Write the data to the output stream
				output.write(is.read());
			}
			is.close();
	
			output.write(("\r\n--" + boundary + "\r\n").getBytes());
		}
	}

	/**
	 * save image to the cache directory
	 * @param buffer byte[]
	 */
	private void saveImage(byte[] buffer)
	{
		String fileName = getCacheDir().getPath() + "/image0" + fileCnt + ".jpg";
		if(fileCnt < 10)
			fileName = getCacheDir().getPath() + "/image00" + fileCnt + ".jpg";
		fileCnt++;
		if(fileCnt > fileMax)
			fileCnt = 0;
		
		FileOutputStream  fop = null;
		File file = new File(fileName);
		try
		{
			if (file.exists()) 
			{
				file.delete();
			}
			
			fop = new FileOutputStream(file);
			
			fop.write(buffer);
			fop.flush();
			fop.close();
		} 
		catch (Exception e)
		{
			Log.e(TAG, "" + e);
		} 
		finally 
		{
			try 
			{
				if (fop != null) 
				{
					fop.close();
				}
			} 
			catch (Exception e)
			{
				Log.e(TAG, "" + e);
			}
		}		
	}

	/**
	 * create image list html page
	 * @param output OutputStream
	 */
	private void sendImageList(OutputStream output)
	{
		StringBuilder html = new StringBuilder(); 
		
		html.append("<!DOCTYPE html>\n");
		html.append("<html>\n");
		html.append("<head>\n");
		html.append("<meta charset=\"UTF-8\">\n");
		html.append("<title>CamServer</title>\n");
		html.append("</head>\n");
		html.append("<body>\n");
		html.append("<table>\n");
		
		for(int i = 0; i < fileMax; i += 2)
		{
			html.append("<tr>\n");
			html.append("<td>");
			html.append("<img name=\"webcam\" src=\"");
			html.append("image0");
			if(i < 10)
				html.append(0);
			html.append(i);
			html.append(".jpg\" border=1 >");
			html.append("</td>\n");

			html.append("<td>");
			html.append("<img name=\"webcam\" src=\"");
			html.append("image0");
			if(i + 1 < 10)
				html.append(0);
			html.append(i + 1);
			html.append(".jpg\" border=1 >");
			html.append("</td>\n");
			
			html.append("</tr>\n");
		}
		
		html.append("</table>\n");
		html.append("</body>\n");
		html.append("</html>\n");
		
		try
		{
			long timestamp = System.currentTimeMillis();
			output.write(("HTTP/1.0 200 OK\r\n" +
					"Server: CamServer\r\n" +
					"Connection: close\r\n" +
					"Max-Age: 0\r\n" +
					"Expires: 0\r\n" +
					"Cache-Control: no-store, no-cache, must-revalidate, pre-check=0, post-check=0, max-age=0\r\n" +
					"Pragma: no-cache\r\n" +
					"Content-type: text/html\r\n" +
					"X-Timestamp:" + timestamp + "\r\n" +
					"\r\n").getBytes());

			output.write(html.toString().getBytes());
		} 
		catch (Exception e)
		{
			Log.e(TAG, "" + e);
		} 
	}

	/**
	 * enable save photo
	 */
	class SaveTask extends TimerTask
	{
		public void run()
		{
			saveTime = true;
		}
	}

	/**
	 * read preference data
	 */
	private void getInfo()
	{
		SharedPreferences settings = getSharedPreferences("org.landroo.netcam_preferences", Context.MODE_PRIVATE);
		String txt = settings.getString("default_port", "8040");
		serverPort = Integer.parseInt(txt);
	}

}
