package local.djisms.android;
import java.util.*;import java.io.IOException;
/** Separate unsolicited SMS notifications even inside split command responses. */
public final class AtStream {
 private final StringBuilder partial=new StringBuilder();private final ArrayDeque<String> replies=new ArrayDeque<>(),notices=new ArrayDeque<>();
 public void feed(String chunk)throws IOException{for(int i=0;i<chunk.length();i++){char c=chunk.charAt(i);if(c=='\r')continue;if(c!='\n'){partial.append(c);if(partial.length()>4096)throw new IOException("USB line too long");continue;}String line=partial.toString().trim();partial.setLength(0);if(line.isEmpty())continue;if(line.startsWith("+CMTI:")){if(notices.size()>=1024)throw new IOException("通知过多，请手动补查");notices.add(line);}else{if(replies.size()>=4096)throw new IOException("USB response too large");replies.add(line);}}}
 public String pollNotice(){return notices.poll();}public String pollReply(){return replies.poll();}public void clearReplies(){replies.clear();}
}
