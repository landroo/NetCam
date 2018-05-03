package org.landroo.netcam;
/*
NetCam
 
This is a simple phone camera view transfer application.
- The receiver view display one or more phone camera view (max four). First you must start receiver view then the senders.
- The sender view send the camera preview to the receiver view on a different phone. You must set the ip address of the receiver and connect.
- The IPCam server wait for a connection from an Internet browser and send the camera preview.   
If you would like your old mobile as a IPCam, just connect your phone to the power and you may trn on developer options, and stay awake.
If you want to see anywhere, you must enable the portforward in your router the phone address and port (8040).

v 1.0

Ez egy egszerű videókép tivábbító alkalmazás.
- A fogadó nézet megjelení a küldő készülék videó képét, maximum 4-et. Először a fogadó nézetet kell elindítani és csak aztán a küldő modult.
- A küldő modul küldi a készülék képét a fogdó nézetnek. Meg kell adni a fogadó készülék ip címét a kép küldésének megkezdése előtt a kapcsolódáshoz.
- Az IPCam kiszolgáló elküldi a csatlakozott böngészőknek a kamera képét.
Ha egy régi készülékét szeretné használni IPCam-ként csatlakoztassa a készüléket a töltőhöz és bekpcsolhatja a fejlesztői beállításokon belül a nem kapcsol ki opciót.
Ha máshonnan is látni akarja a kamera képét akkor a routeren engedélyezni kell a készülék címének és portjának továbbítását (8040).

tasks:
last images time	__	always save last	__	save mjpeg video	__

bugs:
more line ip edit	OK	nexus server hangs	__ 

 */
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class NetCamMainActivity extends Activity 
{
	private static final String TAG = "NetCamMainActivity";
	
	private EditText editText;
	private int port = 8040;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_cam_main);

		Button btn = (Button) findViewById(R.id.receiverButton);
		btn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v) 
			{
				if(isConnected(NetCamMainActivity.this) == 1)
				{
					Bundle bundle = new Bundle();
					bundle.putString("caller", "main");

					Intent intent = new Intent(NetCamMainActivity.this, CamReceiverActivity.class);
					intent.putExtra("camparam", bundle);
					startActivity(intent);
				}
				else
				{
					String sMess = getResources().getString(R.string.no_wifi);
					Toast.makeText(NetCamMainActivity.this, sMess, Toast.LENGTH_LONG).show();
				}
			}
		});

		btn = (Button) findViewById(R.id.senderButton);
		btn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v) 
			{
				if(isConnected(NetCamMainActivity.this) == 1)
				{
					String text = editText.getText().toString();
				
					Bundle bundle = new Bundle();
					bundle.putString("caller", "main");
					bundle.putString("ip", text);

					Intent intent = new Intent(NetCamMainActivity.this, CamSenderActivity.class);
					intent.putExtra("camparam", bundle);
					startActivity(intent);

					putInfo();
				}
				else
				{
					String sMess = getResources().getString(R.string.no_wifi);
					Toast.makeText(NetCamMainActivity.this, sMess, Toast.LENGTH_LONG).show();
				}
			}
		});
		
		btn = (Button) findViewById(R.id.serverButton);
		btn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v) 
			{
				if(isConnected(NetCamMainActivity.this) == 1)
				{
					Bundle bundle = new Bundle();
					bundle.putString("caller", "main");

					Intent intent = new Intent(NetCamMainActivity.this, CamServerActivity.class);
					intent.putExtra("camparam", bundle);
					startActivity(intent);

					putInfo();
				}
				else
				{
					String sMess = getResources().getString(R.string.no_wifi);
					Toast.makeText(NetCamMainActivity.this, sMess, Toast.LENGTH_LONG).show();
				}
			}
		});

		btn = (Button) findViewById(R.id.tab1Button);
		btn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				LinearLayout ll = (LinearLayout) findViewById(R.id.receiverTab);
				ll.setVisibility(View.VISIBLE);
				ll = (LinearLayout) findViewById(R.id.senderTab);
				ll.setVisibility(View.GONE);
				ll = (LinearLayout) findViewById(R.id.serverTab);
				ll.setVisibility(View.GONE);
			}
		});

		btn = (Button) findViewById(R.id.tab2Button);
		btn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				LinearLayout ll = (LinearLayout) findViewById(R.id.receiverTab);
				ll.setVisibility(View.GONE);
				ll = (LinearLayout) findViewById(R.id.senderTab);
				ll.setVisibility(View.VISIBLE);
				ll = (LinearLayout) findViewById(R.id.serverTab);
				ll.setVisibility(View.GONE);
			}
		});

		btn = (Button) findViewById(R.id.tab3Button);
		btn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				LinearLayout ll = (LinearLayout) findViewById(R.id.receiverTab);
				ll.setVisibility(View.GONE);
				ll = (LinearLayout) findViewById(R.id.senderTab);
				ll.setVisibility(View.GONE);
				ll = (LinearLayout) findViewById(R.id.serverTab);
				ll.setVisibility(View.VISIBLE);
			}
		});

		editText = (EditText) findViewById(R.id.ipEditText);
		editText.setText("192.168.0.1");
		
		getInfo();
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.net_cam_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) 
		{
			Intent PreferenceScreen = new Intent(this, Preferences.class);
			startActivity(PreferenceScreen);
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}
	
	//
	private void putInfo()
	{
		String text = editText.getText().toString();
		SharedPreferences settings = getSharedPreferences("org.landroo.netcam_preferences", MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("ip", text);
		editor.putString("default_port", "" + port);
		editor.commit();
	}
	
	private void getInfo()
	{
		SharedPreferences settings = getSharedPreferences("org.landroo.netcam_preferences", Context.MODE_PRIVATE);
		String ip = settings.getString("ip", "192.168.0.1");
		editText.setText(ip);
	}

	private int isConnected(Context context)
	{
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = null;
		int bOK = 0;
		if(connectivityManager != null)
		{
			try
			{
				networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
				if(networkInfo != null && networkInfo.isConnectedOrConnecting())
				{
					bOK = 1;
				}
			}
			catch(Exception ex)
			{
				Log.i(TAG, "getNetworkInfo " + ex);
			}
		}

		if(networkInfo == null)
		{
			bOK = 0;
		}

		return bOK;
	}
	
}
