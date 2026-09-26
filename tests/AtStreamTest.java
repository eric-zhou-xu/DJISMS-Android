package local.djisms.android;
public class AtStreamTest {
 public static void main(String[] args)throws Exception{
 String input="\r\n+CMGL: 1,0,,3\r\n+CMTI: \"ME\",2\r\n00010203\r\nOK\r\n";
 for(int split=0;split<=input.length();split++){
 AtStream s=new AtStream();s.feed(input.substring(0,split));s.feed(input.substring(split));
 if(!"+CMTI: \"ME\",2".equals(s.pollNotice()))throw new AssertionError("notice boundary "+split);
 StringBuilder reply=new StringBuilder();String line;while((line=s.pollReply())!=null)reply.append(line).append('\n');
 if(Protocol.list(reply.toString()).size()!=1)throw new AssertionError("interleaved listing");
 }
 AtStream s=new AtStream();for(char c:input.toCharArray())s.feed(String.valueOf(c));s.clearReplies();if(s.pollNotice()==null)throw new AssertionError("lost queued notice");
 s.feed("+CMTI: \"SM\",5\r\n+CMTI: \"ME\",6\r\n");if(!s.pollNotice().contains("SM")||!s.pollNotice().contains("ME"))throw new AssertionError("ordering");
 try{s.feed(new String(new char[4097]).replace('\0','X'));throw new AssertionError("unbounded");}catch(java.io.IOException expected){}
 System.out.println("Passed all split boundaries, bytewise/interleaved notices, queue preservation, ordering and bounds");
 }
}
