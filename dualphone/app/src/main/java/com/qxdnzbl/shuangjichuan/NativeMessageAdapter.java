package com.qxdnzbl.shuangjichuan;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;

public class NativeMessageAdapter extends RecyclerView.Adapter<NativeMessageAdapter.Holder>{
  public interface Callbacks{
    void onLongPress(View anchor,TransferDb.Msg msg);
    void onFileClick(TransferDb.Msg msg);
  }

  private final Context c;
  private final Callbacks cb;
  private final ArrayList<TransferDb.Msg> items=new ArrayList<>();
  private String query="";
  private String activeId=null;
  private final ArrayList<Integer> matches=new ArrayList<>();
  private final int maxWidth;

  public NativeMessageAdapter(Context c,Callbacks cb){
    this.c=c;this.cb=cb;
    maxWidth=Math.round(c.getResources().getDisplayMetrics().widthPixels*.78f);
  }

  public void setItems(List<TransferDb.Msg> list){
    items.clear();items.addAll(list);rebuildMatches();notifyDataSetChanged();
  }

  public void setSearch(String q,String active){
    query=q==null?"":q.trim();activeId=active;rebuildMatches();notifyDataSetChanged();
  }

  public List<Integer> getMatchPositions(){return new ArrayList<>(matches);}
  public String getItemIdAt(int position){return position>=0&&position<items.size()?items.get(position).id:null;}

  private void rebuildMatches(){
    matches.clear();if(query.isEmpty())return;
    String q=query.toLowerCase(Locale.ROOT);
    for(int i=0;i<items.size();i++){
      TransferDb.Msg m=items.get(i);
      String hay="file".equals(m.kind)?String.valueOf(m.fileName):String.valueOf(m.text);
      if(hay.toLowerCase(Locale.ROOT).contains(q))matches.add(i);
    }
  }

  @Override public Holder onCreateViewHolder(ViewGroup parent,int viewType){
    FrameLayout root=new FrameLayout(c);
    root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
    root.setPadding(dp(14),dp(4),dp(14),dp(4));

    LinearLayout bubble=new LinearLayout(c);
    bubble.setOrientation(LinearLayout.VERTICAL);
    bubble.setPadding(dp(13),dp(10),dp(13),dp(9));
    root.addView(bubble,new FrameLayout.LayoutParams(-2,-2));

    return new Holder(root,bubble);
  }

  @Override public void onBindViewHolder(Holder h,int position){
    TransferDb.Msg m=items.get(position);
    h.bubble.removeAllViews();
    h.bubble.setOnClickListener(null);
    h.bubble.setOnLongClickListener(v->{cb.onLongPress(v,m);return true;});

    FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)h.bubble.getLayoutParams();
    lp.gravity=m.mine?Gravity.END:Gravity.START;
    h.bubble.setLayoutParams(lp);

    boolean matched=!query.isEmpty()&&matches.contains(position);
    boolean active=matched&&m.id!=null&&m.id.equals(activeId);

    h.bubble.setBackground(bubbleBackground(m.mine,matched,active));

    if("text".equals(m.kind)){
      TextView body=textView(15.5f,m.mine?Color.WHITE:Color.rgb(32,34,38));
      body.setMaxWidth(maxWidth);
      body.setLineSpacing(0,1.05f);
      body.setText(highlight(m.text==null?"":m.text,query,m.mine));
      h.bubble.addView(body,new LinearLayout.LayoutParams(-2,-2));
    }else{
      LinearLayout title=new LinearLayout(c);title.setGravity(Gravity.CENTER_VERTICAL);
      TextView clip=new TextView(c);clip.setText("⌁");clip.setTextSize(21);clip.setGravity(Gravity.CENTER);
      clip.setTextColor(m.mine?0xE6FFFFFF:Color.rgb(104,126,224));
      clip.setBackground(makeColor(m.mine?0x1FFFFFFF:0x12687EE0,dp(11)));
      title.addView(clip,new LinearLayout.LayoutParams(dp(34),dp(34)));

      TextView label=textView(14.5f,m.mine?Color.WHITE:Color.rgb(34,36,40));
      label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
      label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);label.setMaxWidth(maxWidth-dp(54));
      label.setText(highlight(friendlyFileLabel(m.fileName),query,m.mine));
      LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,-2,1f);llp.leftMargin=dp(9);
      title.addView(label,llp);
      h.bubble.addView(title,new LinearLayout.LayoutParams(Math.min(maxWidth,dp(250)),-2));

      TextView meta=textView(11.5f,m.mine?0xC8FFFFFF:Color.rgb(128,132,138));
      meta.setText(size(m.fileSize));
      LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(-2,-2);mlp.topMargin=dp(5);
      h.bubble.addView(meta,mlp);

      if(!m.mine){
        TextView save=textView(11.5f,Color.rgb(99,124,222));
        save.setText("保存到下载");
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-2,-2);slp.topMargin=dp(5);
        h.bubble.addView(save,slp);
        h.bubble.setOnClickListener(v->cb.onFileClick(m));
      }
    }

    if(m.mine){
      TextView state=textView(10.5f,0xB8FFFFFF);
      state.setText("sent".equals(m.status)?"已送达":"等待发送");
      state.setGravity(Gravity.END);
      LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(5);
      h.bubble.addView(state,sp);
    }
  }

  @Override public int getItemCount(){return items.size();}

  private CharSequence highlight(String text,String q,boolean mine){
    if(q==null||q.isEmpty())return text;
    String low=text.toLowerCase(Locale.ROOT),needle=q.toLowerCase(Locale.ROOT);
    SpannableString s=new SpannableString(text);int from=0,i;
    while((i=low.indexOf(needle,from))>=0){
      s.setSpan(new BackgroundColorSpan(mine?0xAAFFE5A8:0xFFFFE5A6),i,i+q.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      from=i+q.length();
    }
    return s;
  }

  private GradientDrawable bubbleBackground(boolean mine,boolean matched,boolean active){
    GradientDrawable g;
    if(mine){
      g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(120,138,242),Color.rgb(132,119,233)});
    }else{
      g=makeColor(0xE9FFFFFF,dp(18));
    }
    g.setCornerRadius(dp(18));
    if(active)g.setStroke(dp(3),0xDDE09A30);
    else if(matched)g.setStroke(dp(2),0xA8ECB758);
    else if(!mine)g.setStroke(1,0x55FFFFFF);
    return g;
  }

  private TextView textView(float sp,int color){
    TextView v=new TextView(c);v.setTextSize(sp);v.setTextColor(color);v.setIncludeFontPadding(false);return v;
  }

  private GradientDrawable makeColor(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
  private int dp(float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}

  private String size(long n){
    if(n<1024)return n+" B";
    if(n<1048576)return String.format(Locale.US,"%.1f KB",n/1024f);
    return String.format(Locale.US,"%.1f MB",n/1048576f);
  }

  public static String friendlyFileLabel(String name){
    String n=name==null||name.trim().isEmpty()?"文件":name.trim();
    String low=n.toLowerCase(Locale.ROOT);
    if(low.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|heic|heif)$")||n.matches("(?i)^(camera|img|screenshot|xhs)[_\\-].*"))return "图片";
    if(low.matches(".*\\.(mp4|mov|m4v|avi|mkv|webm)$"))return "视频";
    if(low.matches(".*\\.(mp3|m4a|wav|aac|flac|ogg)$"))return "音频";
    if(low.endsWith(".pdf"))return "PDF 文件";
    if(n.length()<=34)return n;
    return n.substring(0,18)+"…"+n.substring(n.length()-10);
  }

  static class Holder extends RecyclerView.ViewHolder{
    final LinearLayout bubble;
    Holder(View item,LinearLayout bubble){super(item);this.bubble=bubble;}
  }
}
