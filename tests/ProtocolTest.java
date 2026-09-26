package local.djisms.android;
public class ProtocolTest{
 interface Attempt{void run()throws Exception;}
 static int n=0;static void yes(boolean b){if(!b)throw new AssertionError();n++;}
 static void reject(Attempt a)throws Exception{try{a.run();throw new AssertionError("accepted malformed response");}catch(java.io.IOException expected){n++;}}
 public static void main(String[] a)throws Exception{
 String p="00010203";
 yes(Protocol.list("AT+CMGL=4\n+CMGL: 1,0,,3\n"+p+"\nOK\n").get(0).pdu.equals(p));
 yes(Protocol.list("\r\nOK\r\n").isEmpty());
 yes(Protocol.read("+CMGR: 1,,3\n"+p+"\nOK\n").equals(p));
 reject(()->Protocol.list("+CMGL: 1,0,,4\n"+p+"\nOK\n"));
 reject(()->Protocol.list("+CMGL: 1,0,,3\n"+p+"\n+CMGL: 1,0,,3\n"+p+"\n"));
 reject(()->Protocol.list("+CMGL: 1,0,,3\n"));
 reject(()->Protocol.read("+CMGR: 1,,3\n001XY203\n"));
 reject(()->Protocol.read("+CMGR: 1,,3\n02010203\n"));
 yes(Protocol.emptySlot("\r\n+CMS ERROR: 321\r\n"));
 yes(!Protocol.emptySlot("+CMS ERROR: 3210\n"));
 yes(!Protocol.emptySlot("ERROR\n"));
 yes(!Protocol.emptySlot("+CMGR: 1,,3\n00010203\n+CMS ERROR: 321\n"));
 System.out.println("Passed "+n+" protocol cases");
 }
}
