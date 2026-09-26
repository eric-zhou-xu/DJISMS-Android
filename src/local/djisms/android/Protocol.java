package local.djisms.android;
import java.io.IOException;import java.util.*;import java.util.regex.*;
public final class Protocol {
 public static final class Entry{public final int slot;public final String pdu;Entry(int s,String p){slot=s;pdu=p;}}
 public static boolean emptySlot(String s){return Pattern.compile("(?m)^\\+CMS ERROR: 321$").matcher(s.replace("\r","")).find()&&!Pattern.compile("(?m)^\\+CMGR:").matcher(s).find();}
 public static String pdu(String header,String value)throws IOException{
 String p=value.trim().toUpperCase(Locale.ROOT);if(p.length()<4||p.length()>2048||p.length()%2!=0||!p.matches("[0-9A-F]+"))throw new IOException("短信 PDU 格式异常");
 try{int declared=Integer.parseInt(header.substring(header.lastIndexOf(',')+1).trim());int smsc=Integer.parseInt(p.substring(0,2),16);if(declared<=0||p.length()/2!=1+smsc+declared)throw new IOException("短信 PDU 长度不完整");}catch(NumberFormatException e){throw new IOException("短信长度字段异常");}return p;
 }
 public static List<Entry> list(String s)throws IOException{
 String[] lines=s.replace("\r","").split("\n");List<Entry> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
 for(int i=0;i<lines.length;i++)if(lines[i].startsWith("+CMGL:")){
 String header=lines[i];Matcher m=Pattern.compile("\\+CMGL:\\s*(\\d+),.*").matcher(header);if(!m.matches()||i+1>=lines.length)throw new IOException("短信列表不完整");
 int slot;try{slot=Integer.parseInt(m.group(1));}catch(NumberFormatException e){throw new IOException("短信索引异常");}if(slot<0||slot>65535||!seen.add(slot))throw new IOException("重复或无效的短信索引");
 result.add(new Entry(slot,pdu(header,lines[++i])));
 }return result;
 }
 public static String read(String s)throws IOException{String[] lines=s.replace("\r","").split("\n");for(int i=0;i<lines.length-1;i++)if(lines[i].startsWith("+CMGR:"))return pdu(lines[i],lines[i+1]);throw new IOException("重新核对短信失败");}
}
