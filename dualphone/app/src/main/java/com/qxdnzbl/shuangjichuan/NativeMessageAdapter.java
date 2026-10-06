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

  private static final int TYPE_TEXT=0,TYPE_FILE=1;
  private final Context c;
  private final Callbacks cb;
  private final ArrayList<TransferDb.Msg> items=new ArrayList<>();
  private final ArrayList<Integer> matches=new ArrayList<>();
  private String query="",activeId=null;
  private final int maxWidth;
  private int lastMine=-1;

  public NativeMessageAdapter(Context c,Callbacks cb){
    this.c=c;this.cb=cb;
    maxWidth=Math.round(c.getResources().getDisplayMetrics().widthPixels*.76f);
    setHasStableIds(true);
  }

  public void setItems(List<TransferDb.Msg> next){
    ArrayList<TransferDb.Msg> n=new ArrayList<>(next);
    int oldLast=lastMine,oldSize=items.size();
    boolean prefix=n.size()>=oldSize;
    if(prefix)for(int i=0;i<oldSize;i++)if(!Objects.equals(items.get(i).id,n.get(i).id)){prefix=false;break;}
    if(prefix){
      for(int i=0;i<oldSize;i++){
        TransferDb.Msg old=items.get(i),now=n.get(i);
        if(!same(old,now)){items.set(i,now);notifyItemChanged(i);}
      }
      if(n.size()>oldSize){
        for(int i=oldSize;i<n.size();i++)items.add(n.get(i));
        notifyItemRangeInserted(oldSize,n.size()-oldSize);
      }
    }else{
      items.clear();items.addAll(n);notifyDataSetChanged();
    }
    lastMine=findLastMine();
    if(oldLast!=lastMine){
      if(oldLast>=0&&oldLast<items.size())notifyItemChanged(oldLast);
      if(lastMine>=0&&lastMine<items.size())notifyItemChanged(lastMine);
    }
    rebuildMatches();
  }

  private boolean same(TransferDb.Msg a,TransferDb.Msg b){
    return a.mine==b.mine&&a.fileSize==b.fileSize&&a.createdAt==b.createdAt
      &&Objects.equals(a.id,b.id)&&Objects.equals(a.kind,b.kind)&&Objects.equals(a.text,b.text)
      &&Objects.equals(a.fileName,b.fileName)&&Objects.equals(a.filePath,b.filePath)&&Objects.equals(a.status,b.status);
  }
  private int findLastMine(){for(int i=items.size()-1;i>=0;i--)if(items.get(i).mine)return i;return -1;}

  public void setSearch(String q,String active){query=q==null?"":q.trim();activeId=active;rebuildMatches();notifyDataSetChanged();}
  public List<Integer> getMatchPositions(){return new ArrayList<>(matches);}
  public String getItemIdAt(int p){return p>=0&&p<items.size()?items.get(p).id:null;}
  private void rebuildMatches(){
    matches.clear();if(query.isEmpty())return;
    String q=query.toLowerCase(Locale.ROOT);
    for(int i=0;i<items.size();i++){
      TransferDb.Msg m=items.get(i);
      String hay="file".equals(m.kind)?String.valueOf(m.fileName):String.valueOf(m.text);
      if(hay.toLowerCase(Locale.ROOT).contains(q))matches.add(i);
    }
  }

  @Override public long getItemId(int p){String id=items.get(p).id;return id==null?p:(((long)id.hashCode())<<32)^id.length();}
  @Override public int getItemViewType(int p){return "file".equals(items.get(p).kind)?TYPE_FILE:TYPE_TEXT;}
  @Override public int getItemCount(){return items.size();}

  private FrameLayout root(){
    FrameLayout r=new FrameLayout(c);
    r.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
    r.setPadding(dp(12),dp(3),dp(12),dp(3));
    return r;
  }
  private LinearLayout bubble(FrameLayout root){
    LinearLayout b=new LinearLayout(c);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(12),dp(8),dp(12),dp(8));
    root.addView(b,new FrameLayout.LayoutParams(-2,-2));return b;
  }

  @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
    FrameLayout root=root();LinearLayout bubble=bubble(root);
    if(type==TYPE_TEXT){
      TextView body=textView(15.5f,Color.BLACK);body.setMaxWidth(maxWidth);body.setLineSpacing(0,1.04f);
      bubble.addView(body,new LinearLayout.LayoutParams(-2,-2));
      TextView state=textView(10.5f,0xB8FFFFFF);state.setGravity(Gravity.END);
      LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(4);bubble.addView(state,sp);
      return new TextHolder(root,bubble,body,state);
    }
    LinearLayout title=new LinearLayout(c);title.setGravity(Gravity.CENTER_VERTICAL);
    FrameLayout iconBox=new FrameLayout(c);ImageView icon=new ImageView(c);icon.setImageResource(R.drawable.ic_attachment);icon.setPadding(dp(7),dp(7),dp(7),dp(7));
    iconBox.addView(icon,new FrameLayout.LayoutParams(-1,-1));title.addView(iconBox,new LinearLayout.LayoutParams(dp(32),dp(32)));
    TextView label=textView(14.5f,Color.rgb(34,36,40));label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);label.setMaxWidth(maxWidth-dp(52));
    LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(0,-2,1f);llp.leftMargin=dp(8);title.addView(label,llp);
    bubble.addView(title,new LinearLayout.LayoutParams(Math.min(maxWidth,dp(246)),-2));
    TextView meta=textView(11.5f,Color.rgb(128,132,138));LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-2,-2);mp.topMargin=dp(4);bubble.addView(meta,mp);
    TextView save=textView(11.5f,Color.rgb(52,120,246));save.setText("保存到下载");LinearLayout.LayoutParams svp=new LinearLayout.LayoutParams(-2,-2);svp.topMargin=dp(4);bubble.addView(save,svp);
    TextView state=textView(10.5f,0xB8FFFFFF);state.setGravity(Gravity.END);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(4);bubble.addView(state,sp);
    return new FileHolder(root,bubble,iconBox,icon,label,meta,save,state);
  }

  @Override public void onBindViewHolder(Holder holder,int position){
    TransferDb.Msg m=items.get(position);
    FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)holder.bubble.getLayoutParams();lp.gravity=m.mine?Gravity.END:Gravity.START;holder.bubble.setLayoutParams(lp);
    boolean matched=!query.isEmpty()&&matches.contains(position),active=matched&&m.id!=null&&m.id.equals(activeId);
    holder.bubble.setBackground(bubbleBackground(m.mine,matched,active));
    holder.bubble.setOnLongClickListener(v->{cb.onLongPress(v,m);return true;});holder.bubble.setOnClickListener(null);

    if(holder instanceof TextHolder){
      TextHolder h=(TextHolder)holder;h.body.setTextColor(m.mine?Color.WHITE:Color.rgb(28,28,30));h.body.setText(highlight(m.text==null?"":m.text,query,m.mine));bindState(h.state,m,position);
    }else{
      FileHolder h=(FileHolder)holder;
      h.icon.setColorFilter(m.mine?0xE6FFFFFF:Color.rgb(52,120,246));h.iconBox.setBackground(makeColor(m.mine?0x20FFFFFF:0x123478F6,dp(10)));
      h.label.setTextColor(m.mine?Color.WHITE:Color.rgb(28,28,30));h.label.setText(highlight(friendlyFileLabel(m.fileName),query,m.mine));
      h.meta.setTextColor(m.mine?0xC8FFFFFF:Color.rgb(128,128,134));h.meta.setText(size(m.fileSize));
      h.save.setVisibility(m.mine?View.GONE:View.VISIBLE);if(!m.mine)holder.bubble.setOnClickListener(v->cb.onFileClick(m));bindState(h.state,m,position);
    }
  }

  private void bindState(TextView state,TransferDb.Msg m,int position){
    boolean show=m.mine&&position==lastMine;state.setVisibility(show?View.VISIBLE:View.GONE);
    if(show)state.setText("sent".equals(m.status)?"已送达":"发送中");
  }

  private CharSequence highlight(String text,String q,boolean mine){
    if(q==null||q.isEmpty())return text;
    String low=text.toLowerCase(Locale.ROOT),needle=q.toLowerCase(Locale.ROOT);SpannableString s=new SpannableString(text);int from=0,i;
    while((i=low.indexOf(needle,from))>=0){s.setSpan(new BackgroundColorSpan(mine?0xAAFFE5A8:0xFFFFE5A6),i,i+q.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);from=i+q.length();}
    return s;
  }

  private GradientDrawable bubbleBackground(boolean mine,boolean matched,boolean active){
    GradientDrawable g=makeColor(mine?Color.rgb(52,120,246):0xE8F2F2F7,dp(20));
    float r=dp(20),small=dp(7);
    if(mine)g.setCornerRadii(new float[]{r,r,r,r,small,small,r,r}); else g.setCornerRadii(new float[]{r,r,r,r,r,r,small,small});
    if(active)g.setStroke(dp(3),0xDDE09A30);else if(matched)g.setStroke(dp(2),0xA8ECB758);else if(!mine)g.setStroke(1,0x44FFFFFF);
    return g;
  }

  private TextView textView(float sp,int color){TextView v=new TextView(c);v.setTextSize(sp);v.setTextColor(color);v.setIncludeFontPadding(false);return v;}
  private GradientDrawable makeColor(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
  private int dp(float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
  private String size(long n){if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.US,"%.1f KB",n/1024f);return String.format(Locale.US,"%.1f MB",n/1048576f);}

  public static String friendlyFileLabel(String name){
    String n=name==null||name.trim().isEmpty()?"文件":name.trim(),low=n.toLowerCase(Locale.ROOT);
    if(low.matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|heic|heif)$")||n.matches("(?i)^(camera|img|screenshot|xhs)[_\\-].*"))return "图片";
    if(low.matches(".*\\.(mp4|mov|m4v|avi|mkv|webm)$"))return "视频";
    if(low.matches(".*\\.(mp3|m4a|wav|aac|flac|ogg)$"))return "音频";
    if(low.endsWith(".pdf"))return "PDF 文件";
    return n.length()<=34?n:n.substring(0,18)+"…"+n.substring(n.length()-10);
  }

  static abstract class Holder extends RecyclerView.ViewHolder{final LinearLayout bubble;Holder(View v,LinearLayout b){super(v);bubble=b;}}
  static class TextHolder extends Holder{final TextView body,state;TextHolder(View v,LinearLayout b,TextView body,TextView state){super(v,b);this.body=body;this.state=state;}}
  static class FileHolder extends Holder{final FrameLayout iconBox;final ImageView icon;final TextView label,meta,save,state;FileHolder(View v,LinearLayout b,FrameLayout ib,ImageView i,TextView l,TextView m,TextView s,TextView st){super(v,b);iconBox=ib;icon=i;label=l;meta=m;save=s;state=st;}}
}
