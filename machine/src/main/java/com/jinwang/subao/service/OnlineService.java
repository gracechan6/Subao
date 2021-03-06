package com.jinwang.subao.service;

import java.nio.ByteBuffer;

import org.ddpush.im.util.DateTimeUtil;
import org.ddpush.im.v1.client.appuser.Message;
import org.ddpush.im.v1.client.appuser.TCPClientBase;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.widget.Toast;

import com.jinwang.subao.config.PushConfig;
import com.jinwang.subao.receiver.TickAlarmReceiver;
import com.jinwang.subao.util.Util;

/**
 * created by michael, 7/28/15
 *
 * 保证设备在线服务
 */
public class OnlineService extends Service {
	
	protected PendingIntent tickPendIntent;
	protected TickAlarmReceiver tickAlarmReceiver = new TickAlarmReceiver();
	WakeLock wakeLock;

	MyTcpClient tcpClient;
	
	public class MyTcpClient extends TCPClientBase {

		public MyTcpClient(byte[] uuid, int appid, String serverAddr, int serverPort)
				throws Exception {
			super(uuid, appid, serverAddr, serverPort, 10);

		}

		@Override
		public boolean hasNetworkConnection() {
			return true;
		}
		

		@Override
		public void trySystemSleep() {
			tryReleaseWakeLock();
		}

		@Override
		public void onPushMessage(Message message) {
			if(message == null){
				return;
			}
			if(message.getData() == null || message.getData().length == 0){
				return;
			}
			if(message.getCmd() == 16){// 0x10 通用推送信息
			}
			if(message.getCmd() == 17){// 0x11 分组推送信息
				long msg = ByteBuffer.wrap(message.getData(), 5, 8).getLong();
			}
			if(message.getCmd() == 32){// 0x20 自定义推送信息
				//自定义消息有系统升级 、远程开箱，具体值待定，数据为JSON {type：int, data: {}}
				//具体信息待宝
				String str = null;
				try{
					str = new String(message.getData(),5,message.getContentLength(), "UTF-8");
				}catch(Exception e){
					str = Util.convert(message.getData(), 5, message.getContentLength());
				}
			}
		}

	}

	public OnlineService() {
	}

	@Override
	public void onCreate() {
		super.onCreate();
		this.setTickAlarm();

		resetClient();

		PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OnlineService");
	}

	@Override
	public int onStartCommand(Intent param, int flags, int startId) {
		if(param == null){
			return START_STICKY;
		}
		String cmd = param.getStringExtra("CMD");
		if(cmd == null){
			cmd = "";
		}
		if(cmd.equals("TICK")){
			if(wakeLock != null && wakeLock.isHeld() == false){
				wakeLock.acquire();
			}
		}
		if(cmd.equals("RESET")){
			if(wakeLock != null && wakeLock.isHeld() == false){
				wakeLock.acquire();

				resetClient();
			}
		}
		if(cmd.equals("TOAST")){
			String text = param.getStringExtra("TEXT");
			if(text != null && text.trim().length() != 0){
				Toast.makeText(this, text, Toast.LENGTH_LONG).show();
			}
		}

		return START_STICKY;
	}
	
	protected void tryReleaseWakeLock(){
		if(wakeLock != null && wakeLock.isHeld() == true){
			wakeLock.release();
		}
	}

	/**
	 * 终端重置
	 */
	protected void resetClient(){
		SharedPreferences account = this.getSharedPreferences(PushConfig.DEFAULT_PRE_NAME,Context.MODE_PRIVATE);
		String serverIp = PushConfig.SERVER_IP;
		int serverPort = PushConfig.SERVER_PORT;
		int pushPort = PushConfig.PUSH_PORT;

		String userName = account.getString(PushConfig.USER_NAME, "");


		if(this.tcpClient != null){
			try{tcpClient.stop();}catch(Exception e){}
		}
		try{
			tcpClient = new MyTcpClient(Util.md5Byte(userName), 1, serverIp, serverPort);
			tcpClient.setHeartbeatInterval(50);
			tcpClient.start();

		}catch(Exception e){
			Toast.makeText(this.getApplicationContext(), "操作失败："+e.getMessage(), Toast.LENGTH_LONG).show();
		}
		Toast.makeText(this.getApplicationContext(), "ddpush：终端重置", Toast.LENGTH_LONG).show();
	}
	
	protected void setTickAlarm(){
		AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);  
		Intent intent = new Intent(this,TickAlarmReceiver.class);
		int requestCode = 0;  
		tickPendIntent = PendingIntent.getBroadcast(this,  
		requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);  
		//小米2s的MIUI操作系统，目前最短广播间隔为5分钟，少于5分钟的alarm会等到5分钟再触发！2014-04-28
		long triggerAtTime = System.currentTimeMillis();
		int interval = 300 * 1000;  
		alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, interval, tickPendIntent);
	}
	
	protected void cancelTickAlarm(){
		AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		alarmMgr.cancel(tickPendIntent);  
	}

	
	protected void cancelNotifyRunning(){
		NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(0);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		//this.cancelTickAlarm();
		cancelNotifyRunning();
		this.tryReleaseWakeLock();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


}
