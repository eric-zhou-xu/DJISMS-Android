package local.djisms.android;
import android.app.*;import android.os.*;import android.content.*;import android.hardware.usb.*;import android.telephony.SmsMessage;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.regex.*;
public class ReceiverService extends Service {
 public static volatile boolean running=false;public static volatile String status="未连接模块";public static volatile String lastCheck="尚未补查";volatile boolean manual=false;boolean listening=false;String notificationError="";AtStream stream=new AtStream();PowerManager.WakeLock wake;volatile boolean stop=false;Thread worker;UsbDeviceConnection conn;UsbInterface intf;UsbEndpoint in,out;String deviceId;Archive archive;
 public IBinder onBind(Intent i){return null;}
 public int onStartCommand(Intent i,int f,int id){if(i!=null&&"CHECK".equals(i.getAction()))manual=true;if(worker!=null)return START_NOT_STICKY;
 NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("usb","模块短信接收",NotificationManager.IMPORTANCE_LOW));
 startForeground(1,new Notification.Builder(this,"usb").setSmallIcon(android.R.drawable.stat_notify_chat).setContentTitle("DJISMS 正在接收模块短信").setContentText("通知触发接收，支持手动补查；原件保留").setOngoing(true).build());
 wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"DJISMS:UsbListen");wake.acquire();running=true;stop=false;worker=new Thread(()->runSession(),"module-receiver");worker.start();return START_NOT_STICKY;}
 public void onDestroy(){stop=true;super.onDestroy();}
 void runSession(){String originalStore=null;Integer originalFormat=null;String originalCnmi=null;
 try{archive=new Archive(this);
 UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);UsbDevice dev=null;int count=0;
 for(UsbDevice d:manager.getDeviceList().values())if(d.getVendorId()==0x2ca3&&d.getProductId()==0x4006){dev=d;count++;}
 if(count!=1||!manager.hasPermission(dev))throw new Exception("模块未连接或 USB 未授权");
 for(int k=0;k<dev.getInterfaceCount();k++){UsbInterface t=dev.getInterface(k);if(t.getId()==2&&t.getInterfaceClass()==255)intf=t;}
 if(intf==null)throw new Exception("USB 接口布局不匹配，未发送命令");
 for(int k=0;k<intf.getEndpointCount();k++){UsbEndpoint e=intf.getEndpoint(k);if(e.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){if(e.getAddress()==0x84)in=e;if(e.getAddress()==3)out=e;}}
 if(in==null||out==null)throw new Exception("短信端点不匹配，未发送命令");
 conn=manager.openDevice(dev);if(conn==null||!conn.claimInterface(intf,false))throw new Exception("短信接口被占用或无法打开");
 ok("AT");if(!ok("AT+CGMM").contains("QDC507"))throw new Exception("尚未支持此模块型号");
 String sn=ok("AT+CGSN");Matcher sm=Pattern.compile("(?m)^([0-9]{15})$").matcher(sn);if(!sm.find())throw new Exception("无法核验模块身份");deviceId=Archive.hash(sm.group(1));
 String stores=ok("AT+CPMS=?");Matcher st=Pattern.compile("\\+CPMS:\\s*\\(([^)]*)\\)").matcher(stores);if(!st.find())throw new Exception("无法读取短信存储支持列表");String supported=st.group(1);
 Matcher current=Pattern.compile("\\+CPMS:\\s*\"(ME|SM|MT)\"").matcher(ok("AT+CPMS?"));if(!current.find())throw new Exception("无法备份读取存储设置");originalStore=current.group(1);
 Matcher format=Pattern.compile("\\+CMGF:\\s*([01])").matcher(ok("AT+CMGF?"));if(!format.find())throw new Exception("无法备份短信格式");originalFormat=Integer.valueOf(format.group(1));ok("AT+CMGF=0");
 List<String> active=new ArrayList<>();for(String s:new String[]{"ME","SM"})if(supported.contains("\""+s+"\""))active.add(s);if(active.isEmpty())throw new Exception("没有可读取的 ME/SM 存储");
 try{
 String route=ok("AT+QURCCFG=\"urcport\"");
 if(!route.contains("\"usbat\"")&&!route.contains("\"all\""))throw new IOException("模块通知未路由到 USB AT 口");
 Matcher cn=Pattern.compile("\\+CNMI:\\s*([0-9]+\\s*,\\s*[0-9]+\\s*,\\s*[0-9]+\\s*,\\s*[0-9]+\\s*,\\s*[0-9]+)").matcher(ok("AT+CNMI?"));
 if(!cn.find())throw new IOException("无法备份短信通知设置");originalCnmi=cn.group(1).replaceAll("\\s","");
 ok("AT+CNMI=2,1,0,0,0");
 if(!ok("AT+CNMI?").matches("(?s).*\\+CNMI:\\s*2,\\s*1,\\s*0,\\s*0,\\s*0.*"))throw new IOException("通知设置回读不一致");listening=true;
 }catch(IOException e){notificationError="自动通知不可用，请手动补查";android.util.Log.i("DJISMS_DIAG","Notification configuration unavailable");}
 scan(active);manual=false;
 while(!stop){
 if(!manager.getDeviceList().containsKey(dev.getDeviceName()))throw new IOException("模块已断开");
 if(manual){manual=false;scan(active);}
 String notice=stream.pollNotice();
 if(notice!=null&&listening){Matcher nt=Pattern.compile("\\+CMTI:\\s*\"(ME|SM)\",\\s*([0-9]+)").matcher(notice);if(nt.matches()&&active.contains(nt.group(1))){
 int slot=Integer.parseInt(nt.group(2));if(slot>65535)throw new IOException("短信通知索引异常");
 ok("AT+CPMS=\""+nt.group(1)+"\"");String response=at("AT+CMGR="+slot);
 if(!Protocol.emptySlot(response)){if(!response.contains("\nOK\n"))throw new IOException("通知短信读取失败，请手动补查");String pdu=Protocol.read(response);archive.save(deviceId,nt.group(1),pdu,decode(pdu));android.util.Log.i("DJISMS_DIAG","Notification SMS saved");}
 }}else if(notice==null){receive();}
 status=listening?"正在监听模块通知":notificationError;
 }

 status="已停止接收";
 }catch(Exception e){status="已停止："+(e.getMessage()==null?"USB 或档案操作失败":e.getMessage());}
 finally{listening=false;running=false;android.util.Log.i("DJISMS_DIAG",status);if(conn!=null){try{if(originalCnmi!=null)ok("AT+CNMI="+originalCnmi);}catch(Exception ignored){}try{if(originalStore!=null)ok("AT+CPMS=\""+originalStore+"\"");if(originalFormat!=null)ok("AT+CMGF="+originalFormat);}catch(Exception ignored){}try{conn.releaseInterface(intf);}catch(Exception ignored){}conn.close();}if(archive!=null)archive.close();if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
 }
 void scan(List<String> stores)throws Exception{int count=0;status="正在补查模块与 SIM…";for(String storage:stores){if(stop)return;ok("AT+CPMS=\""+storage+"\"");for(Protocol.Entry e:Protocol.list(ok("AT+CMGL=4"))){archive.save(deviceId,storage,e.pdu,decode(e.pdu));count++;}}lastCheck=new java.text.SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date())+" · 已核对 "+count+" 条原始记录";android.util.Log.i("DJISMS_DIAG","Manual/connection scan complete count="+count);}
 void receive()throws IOException{byte[] b=new byte[4096];int n=conn.bulkTransfer(in,b,b.length,1000);if(n>0)stream.feed(new String(b,0,n,StandardCharsets.US_ASCII));}
 synchronized String at(String command)throws Exception{
 if(conn==null)throw new IOException("USB 未连接");stream.clearReplies();byte[] bytes=(command+"\r\n").getBytes(StandardCharsets.US_ASCII);if(conn.bulkTransfer(out,bytes,bytes.length,2500)!=bytes.length)throw new IOException("USB 命令传输失败");
 StringBuilder result=new StringBuilder("\n");long end=SystemClock.elapsedRealtime()+12000;
 while(SystemClock.elapsedRealtime()<end){String line;while((line=stream.pollReply())!=null){result.append(line).append('\n');if(result.length()>262144)throw new IOException("模块响应超限");if(line.equals("OK")||line.equals("ERROR")||line.startsWith("+CMS ERROR:")||line.startsWith("+CME ERROR:"))return result.toString();}receive();}
 throw new IOException("模块响应超时，请重新连接");}

 String ok(String c)throws Exception{String r=at(c);if(!Pattern.compile("(?m)^OK$").matcher(r).find()||r.contains("ERROR"))throw new IOException("模块拒绝操作，已停止（"+c.split("[=?]")[0]+"）");return r;}
 String readPdu(int slot)throws Exception{return Protocol.read(ok("AT+CMGR="+slot));}
 static String decode(String p){try{byte[] b=new byte[p.length()/2];for(int k=0;k<b.length;k++)b[k]=(byte)Integer.parseInt(p.substring(k*2,k*2+2),16);SmsMessage s=SmsMessage.createFromPdu(b,"3gpp");if(s!=null)return String.valueOf(s.getOriginatingAddress())+"\n"+String.valueOf(s.getMessageBody());}catch(Exception ignored){}return "短信原始数据已保存，暂时无法解码";}
}
