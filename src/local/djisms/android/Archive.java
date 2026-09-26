package local.djisms.android;
import android.content.*;import android.database.*;import android.database.sqlite.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
public class Archive extends SQLiteOpenHelper{
 public Archive(Context c){super(c,"sms.db",null,2);}
 public void onConfigure(SQLiteDatabase d){d.execSQL("PRAGMA synchronous=FULL");}
 public void onCreate(SQLiteDatabase d){createLocalDeleted(d);d.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,storage TEXT NOT NULL,pdu TEXT NOT NULL,body TEXT NOT NULL,created INTEGER NOT NULL)");d.execSQL("CREATE TABLE deletions(id INTEGER PRIMARY KEY AUTOINCREMENT,message TEXT NOT NULL,slot INTEGER NOT NULL,state TEXT NOT NULL)");}
 static void createLocalDeleted(SQLiteDatabase d){d.execSQL("CREATE TABLE IF NOT EXISTS local_deleted(id TEXT PRIMARY KEY)");}
 public void onUpgrade(SQLiteDatabase d,int a,int b){if(a==1&&b==2){createLocalDeleted(d);return;}throw new IllegalStateException("Unsupported archive upgrade");}
 public void deleteLocal(String id){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{ContentValues v=new ContentValues();v.put("id",id);d.insertWithOnConflict("local_deleted",null,v,SQLiteDatabase.CONFLICT_IGNORE);d.delete("messages","id=?",new String[]{id});d.setTransactionSuccessful();}finally{d.endTransaction();}}

 static String hash(String value)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
 public String save(String device,String storage,String pdu,String body)throws Exception{String id=hash(device+"|"+storage+"|"+pdu);SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{try(Cursor c=d.rawQuery("SELECT 1 FROM local_deleted WHERE id=?",new String[]{id})){if(c.moveToFirst()){d.setTransactionSuccessful();return id;}}ContentValues v=new ContentValues();v.put("id",id);v.put("storage",storage);v.put("pdu",pdu);v.put("body",body);v.put("created",System.currentTimeMillis());d.insertWithOnConflict("messages",null,v,SQLiteDatabase.CONFLICT_IGNORE);try(Cursor c=d.rawQuery("SELECT pdu FROM messages WHERE id=?",new String[]{id})){if(!c.moveToFirst()||!pdu.equals(c.getString(0)))throw new Exception("Archive verification failed");}d.setTransactionSuccessful();}finally{d.endTransaction();}return id;}
 public boolean unresolved(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM deletions WHERE state='intent'",null)){c.moveToFirst();return c.getInt(0)>0;}}
 public long intent(String id,int slot){ContentValues v=new ContentValues();v.put("message",id);v.put("slot",slot);v.put("state","intent");return getWritableDatabase().insertOrThrow("deletions",null,v);}
 public void confirmed(long id){ContentValues v=new ContentValues();v.put("state","confirmed");if(getWritableDatabase().update("deletions",v,"id=?",new String[]{""+id})!=1)throw new IllegalStateException("Missing intent");}
}
