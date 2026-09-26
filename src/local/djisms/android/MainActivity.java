package local.djisms.android;
import android.app.*;import android.os.*;import android.content.*;import android.content.res.Configuration;import android.hardware.usb.*;import android.widget.*;import android.view.*;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.*;import android.database.Cursor;import android.text.*;import java.util.*;
public class MainActivity extends Activity {
 final int ink=0xff172b43,muted=0xff65758b,blue=0xff187bff;
 TextView inventory,state,total;LinearLayout messages,detail;EditText search;Button start,stop;
 Handler handler=new Handler();String action,lastRows="",selected="";BroadcastReceiver receiver;boolean paused,wide;
 AlertDialog detailDialog;ArrayList<String[]> current=new ArrayList<>();
 final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,2000);}};
 public void onCreate(Bundle saved){super.onCreate(saved);action=getPackageName()+".USB_PERMISSION";paused=getPreferences(0).getBoolean("paused",false);if(saved!=null)selected=saved.getString("selected","");
 wide=getResources().getConfiguration().screenWidthDp>=600;
 getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
 LinearLayout shell=new LinearLayout(this);shell.setOrientation(0);shell.setBackgroundColor(0xfff4f7fb);setContentView(shell);
 shell.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();});shell.requestApplyInsets();
 LinearLayout left=column();left.setPadding(dp(20),dp(20),dp(20),dp(12));shell.addView(left,new LinearLayout.LayoutParams(0,-1,wide?0.43f:1));
 TextView brand=label("DJISMS",15,ink,true);left.addView(brand);TextView title=label("收件箱",30,ink,true);add(left,title,18);
 LinearLayout card=column();card.setPadding(dp(16),dp(16),dp(16),dp(16));GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xffdcf5ef,0xffe8f2ff});gradient.setCornerRadius(dp(20));card.setBackground(gradient);add(left,card,18);
 LinearLayout statusline=new LinearLayout(this);statusline.setGravity(Gravity.CENTER_VERTICAL);ImageView icon=new ImageView(this);icon.setImageResource(getResources().getIdentifier("app_icon","drawable",getPackageName()));icon.setContentDescription("DJISMS 短信");statusline.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));LinearLayout statustext=column();statustext.setPadding(dp(10),0,0,0);inventory=label("检查模块连接",16,ink,true);statustext.addView(inventory);TextView interval=label("模块通知接收 · 本机保存",12,muted,false);add(statustext,interval,3);statusline.addView(statustext,new LinearLayout.LayoutParams(0,-2,1));card.addView(statusline);
 state=label("",12,muted,false);add(card,state,8);
 LinearLayout actions=new LinearLayout(this);start=button("连接接收",blue,Color.WHITE);stop=button("暂停",0xffeff6ff,blue);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(48),1);bp.rightMargin=dp(8);actions.addView(start,bp);actions.addView(stop,new LinearLayout.LayoutParams(0,dp(48),0.8f));add(card,actions,12);
 start.setOnClickListener(v->{paused=false;getPreferences(0).edit().putBoolean("paused",false).apply();connect();});stop.setOnClickListener(v->{paused=true;getPreferences(0).edit().putBoolean("paused",true).apply();stopService(new Intent(this,ReceiverService.class));ReceiverService.status="已请求暂停";refresh();});
 Button check=button("立即补查",0xffeaf0f7,blue);check.setOnClickListener(v->{if(paused){Toast.makeText(this,"请先继续接收，再补查",Toast.LENGTH_SHORT).show();return;}UsbManager um=(UsbManager)getSystemService(USB_SERVICE);boolean permitted=false;for(UsbDevice d:um.getDeviceList().values())if(matches(d)&&um.hasPermission(d))permitted=true;if(permitted)startForegroundService(new Intent(this,ReceiverService.class).setAction("CHECK"));else connect();});add(left,check,10);
 search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setTextColor(ink);search.setHintTextColor(muted);search.setHint("搜索号码或短信内容");search.setPadding(dp(14),0,dp(14),0);search.setBackground(bg(0xffeaf0f7,14));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(48));sp.topMargin=dp(16);left.addView(search,sp);
 total=label("全部记录",13,muted,false);add(left,total,16);ScrollView list=new ScrollView(this);list.setFillViewport(true);messages=column();list.addView(messages);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);lp.topMargin=dp(8);left.addView(list,lp);
 TextView foot=label("模块与 SIM 原件保留",11,muted,false);foot.setGravity(Gravity.CENTER);add(left,foot,10);
 if(wide){View line=new View(this);line.setBackgroundColor(0xffe5eaf1);shell.addView(line,new LinearLayout.LayoutParams(dp(1),-1));ScrollView ds=new ScrollView(this);ds.setFillViewport(true);ds.setBackgroundColor(Color.WHITE);detail=column();detail.setPadding(dp(28),dp(36),dp(28),dp(24));ds.addView(detail);shell.addView(ds,new LinearLayout.LayoutParams(0,-1,0.57f));}
 search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){lastRows="";refresh();}});
 if(saved!=null)search.setText(saved.getString("query",""));
 receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){if(!action.equals(i.getAction()))return;UsbManager m=(UsbManager)getSystemService(USB_SERVICE);boolean granted=false;for(UsbDevice d:m.getDeviceList().values())if(matches(d)&&m.hasPermission(d))granted=true;if(granted&&!paused)startReceiver();else ReceiverService.status="USB 使用权限未授予或接收已暂停";refresh();}};
 if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,new IntentFilter(action),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,new IntentFilter(action));
 if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=0)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1);
 }
 void connect(){diagnose();UsbManager m=(UsbManager)getSystemService(USB_SERVICE);UsbDevice found=null;int n=0;for(UsbDevice d:m.getDeviceList().values())if(d.getVendorId()==0x2ca3&&d.getProductId()==0x4006){found=d;n++;}
 if(n!=1){ReceiverService.status="请仅连接一块 DJI/Baiwang 模块；当前识别到 "+n+" 块。";state.setText(ReceiverService.status);return;}
 if(m.hasPermission(found))startReceiver();else {ReceiverService.status="已识别模块，等待 USB 访问授权";m.requestPermission(found,PendingIntent.getBroadcast(this,0,new Intent(action).setPackage(getPackageName()),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT));}}
 boolean matches(UsbDevice d){return d.getVendorId()==0x2ca3&&d.getProductId()==0x4006;}
 void startReceiver(){ReceiverService.status="正在连接短信接口…";android.util.Log.i("DJISMS_DIAG","Starting SMS receiver");startForegroundService(new Intent(this,ReceiverService.class));}
 void diagnose(){try{UsbManager m=(UsbManager)getSystemService(USB_SERVICE);StringBuilder d=new StringBuilder("USB devices="+m.getDeviceList().size());for(UsbDevice v:m.getDeviceList().values()){d.append(" VID=").append(v.getVendorId()).append(" PID=").append(v.getProductId());for(int i=0;i<v.getInterfaceCount();i++){UsbInterface f=v.getInterface(i);d.append(" IF").append(f.getId()).append(" class=").append(f.getInterfaceClass()).append(" subclass=").append(f.getInterfaceSubclass()).append(" protocol=").append(f.getInterfaceProtocol());}}android.util.Log.i("DJISMS_DIAG",d.toString());}catch(Exception e){android.util.Log.i("DJISMS_DIAG","USB inventory unavailable");}}

 int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 TextView label(String text,int size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1);if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
 Button button(String text,int color,int foreground){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(foreground);b.setPadding(dp(8),0,dp(8),0);b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x223168ef),bg(color,12),null));return b;}
 void add(LinearLayout parent,View child,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(child,p);}
 String sender(String[] r){int n=r[2].indexOf('\n');return n>0?r[2].substring(0,n):"模块短信";}
 String body(String[] r){int n=r[2].indexOf('\n');return n>0?r[2].substring(n+1):r[2];}
 String stamp(String[] r){return new java.text.SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(new Date(Long.parseLong(r[3])));}
 void showDetail(String[] row){LinearLayout content=wide?detail:column();content.removeAllViews();if(!wide)content.setPadding(dp(24),dp(20),dp(24),dp(24));
 if(row==null){content.addView(label("选择一条短信",24,ink,true));add(content,label("短信内容会显示在这里。",14,muted,false),12);return;}
 content.addView(label(sender(row),26,ink,true));add(content,label(stamp(row)+" 保存 · "+("SM".equals(row[1])?"SIM 卡":"模块存储"),12,muted,false),12);
 TextView text=label(body(row),18,ink,false);text.setTextIsSelectable(true);text.setPadding(dp(18),dp(20),dp(18),dp(20));text.setBackground(bg(0xfff4f7fb,18));add(content,text,28);
 Button copy=button("复制内容",0xffedf4ff,blue);copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("短信",body(row)));Toast.makeText(this,"已复制",Toast.LENGTH_SHORT).show();});add(content,copy,20);
 Button delete=button("删除本机短信",0xffffeeee,0xffb3261e);delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("删除这条本机短信？").setMessage("仅删除手机中的内容，模块和 SIM 原件不受影响。这条原件以后不会重复导入本机。此操作不可撤销。").setNegativeButton("取消",null).setPositiveButton("删除",(dialog,which)->{try(Archive a=new Archive(this)){a.deleteLocal(row[0]);if(detailDialog!=null){detailDialog.dismiss();detailDialog=null;}selected="";lastRows="";refresh();Toast.makeText(this,"已删除本机短信",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"删除失败，请重试",Toast.LENGTH_LONG).show();}}).show());add(content,delete,12);
 if(!wide){ScrollView scroll=new ScrollView(this);scroll.addView(content);detailDialog=new AlertDialog.Builder(this).setView(scroll).setPositiveButton("完成",null).create();detailDialog.show();}}
 void refresh(){if(messages==null)return;UsbManager m=(UsbManager)getSystemService(USB_SERVICE);int count=0;boolean permission=false;for(UsbDevice d:m.getDeviceList().values())if(matches(d)){count++;permission|=m.hasPermission(d);}
 inventory.setText(count==0?"等待连接模块":count==1?(permission?"模块已连接":"模块等待授权"):"连接了 "+count+" 块模块");
 state.setText(paused?"接收已暂停":count==0?"插入模块后开始接收短信":ReceiverService.status+"\n最近补查："+ReceiverService.lastCheck);
 start.setText(ReceiverService.running&&!paused?"接收中":"连接接收");start.setEnabled(!ReceiverService.running);stop.setEnabled(ReceiverService.running&&!paused);stop.setAlpha(stop.isEnabled()?1:0.45f);
 String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);ArrayList<String[]> rows=new ArrayList<>();StringBuilder key=new StringBuilder(query);int all=0;
 try(Archive a=new Archive(this);Cursor c=a.getReadableDatabase().rawQuery("SELECT id,storage,body,created FROM messages ORDER BY created DESC,id DESC",null)){while(c.moveToNext()){all++;if(!c.getString(2).toLowerCase(Locale.ROOT).contains(query))continue;String[] r={c.getString(0),c.getString(1),c.getString(2),c.getString(3)};rows.add(r);key.append(Arrays.toString(r));}}catch(Exception e){total.setText("暂时无法读取本机短信");return;}
 total.setText((query.isEmpty()?"全部记录":"搜索结果")+" · "+(query.isEmpty()?all:rows.size()));if(lastRows.equals(key.toString())&&messages.getChildCount()>0)return;lastRows=key.toString();current=rows;renderRows();
 }
 void renderRows(){messages.removeAllViews();String[] active=null;for(String[] r:current)if(r[0].equals(selected))active=r;if(active==null&&!current.isEmpty()){active=current.get(0);selected=active[0];}
 if(current.isEmpty()){TextView empty=label(search.length()==0?"还没有短信\n连接模块后，新短信会显示在这里。":"没有匹配的短信",15,muted,false);empty.setPadding(dp(12),dp(30),dp(12),dp(30));messages.addView(empty);}
 for(String[] row:current){LinearLayout item=column();item.setPadding(dp(16),dp(16),dp(16),dp(16));item.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x223168ef),bg(wide&&row[0].equals(selected)?0xffe2efff:Color.WHITE,18),null));
 item.addView(label(sender(row),16,ink,true));TextView preview=label(body(row),14,muted,false);preview.setMaxLines(2);preview.setEllipsize(TextUtils.TruncateAt.END);add(item,preview,8);add(item,label(stamp(row)+" 保存",11,muted,false),8);add(messages,item,8);item.setOnClickListener(v->{selected=row[0];if(wide)renderRows();else showDetail(row);});}
 if(wide)showDetail(active);
 }
 public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("selected",selected);b.putString("query",search.getText().toString());}
 public void onResume(){super.onResume();UsbManager m=(UsbManager)getSystemService(USB_SERVICE);for(UsbDevice d:m.getDeviceList().values())if(matches(d)&&m.hasPermission(d)&&!ReceiverService.running&&!paused){startReceiver();break;}handler.post(tick);}
 public void onPause(){handler.removeCallbacks(tick);super.onPause();}
 public void onDestroy(){unregisterReceiver(receiver);super.onDestroy();}
}
