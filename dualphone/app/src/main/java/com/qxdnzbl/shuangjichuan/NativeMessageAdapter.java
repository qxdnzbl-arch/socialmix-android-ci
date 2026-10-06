package com.qxdnzbl.shuangjichuan;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import java.util.*;

public class NativeMessageAdapter extends RecyclerView.Adapter<NativeMessageAdapter.Holder>{
  public interface Callbacks{
    void onLongPress(View anchor,TransferDb.Msg msg);
    void onFileClick(TransferDb.Msg msg);
  }

  private final Context c;
  private final Callbacks cb;
  private final AsyncListDiffer<TransferDb.Msg> differ;
  private String query="";
  private String activeId=null;
  private final ArrayList<Integer> matches=new ArrayList<>();
  private final HashSet<Integer> matchSet=new HashSet<>();
  private final int maxWidth;
  private int latestMinePosition=-1;

  public NativeMessageAdapter(Context c,Callbacks cb){
    this.c=c;this.cb=cb;
    maxWidth=Math.round(c.getResources().getDisplayMetrics().widthPixels*.76f);
    setHasStableIds(true);
    differ=new AsyncListDiffer<>(this,new DiffUtil.ItemCallback<TransferDb.Msg>(){
      @Override public boolean areItemsTheSame(TransferDb.Msg a,TransferDb.Msg b){
        return Objects.equals(a.id,b.id);
      }
      @Override public boolean areContentsTheSame(TransferDb.Msg a,TransferDb.Msg b){
        return a.mine==b.mine
          && a.fileSize==b.fileSize
          && a.createdAt==b.createdAt
          && Objects.equals(a.kind,b.kind)
          && Objects.equals(a.text,b.text)
          && Objects.equals(a.fileName,b.fileName)
          && Objects.equals(a.filePath,b.filePath)
          && Objects.equals(a.status,b.status);
      }
    });
  }

  public void setItems(List<TransferDb.Msg> list){
    ArrayList<TransferDb.Msg> copy=new ArrayList<>(list);
    differ.submitList(copy,()->{
      latestMinePosition=-1;
      for(int i=differ.getCurrentList().size()-1;i>=0;i--){
        if(differ.getCurrentList().get(i).mine){latestMinePosition=i;break;}
      }
      rebuildMatches();
      if(!query.isEmpty())notifyDataSetChanged();
    });
  }

  public void setSearch(String q,String active){
    query=q==null?"":q.trim();activeId=active;rebuildMatches();notifyDataSetChanged();
  }

  public List<Integer> getMatchPositions(){return new ArrayList<>(matches);}
  public String getItemIdAt(int position){List<TransferDb.Msg> items=differ.getCurrentList();return position>=0&&position<items.size()?items.get(position).id:null;}

  @Override public long getItemId(int position){
    String id=getItemIdAt(position);
    return id==null?RecyclerView.NO_ID:((long)id.hashCode()<<32)^(id.length()*2654435761L);
  }

  private void rebuildMatches(){
    matches.clear();matchSet.clear();if(query.isEmpty())return;
    List<TransferDb.Msg> items=differ.getCurrentList();
    String q=query.toLowerCase(Locale.ROOT);
    for(int i=0;i<items.size();i++){
      TransferDb.Msg m=items.get(i);
      String hay="file".equals(m.kind)?String.valueOf(m.fileName):String.valueOf(m.text);
      if(hay.toLowerCase(Locale.ROOT).contains(q)){matches.add(i);matchSet.add(i);}
    }
  }

  @Override public Holder onCreateViewHolder(ViewGroup parent,int viewType){
    FrameLayout root=new FrameLayout(c);
    root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
    root.setPadding(dp(12),dp(3),dp(12),dp(3));

    LinearLayout bubble=new LinearLayout(c);
    bubble.setOrientation(LinearLayout.VERTICAL);
    bubble.setPadding(dp(12),dp(9),dp(12),dp(8));
    root.addView(bubble,new FrameLayout.LayoutParams(-2,-2));

    return new Holder(root,bubble);
  }

  @Override public void onBindViewHolder(Holder h,int position){
    TransferDb.Msg m=differ.getCurrentList().get(position);
    h.bubble.removeAllViews();
    h.bubble.setOnClickListener(null);
    h.bubble.setOnLongClickListener(v->{cb.onLongPress(v,m);return true;});

    FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)h.bubble.getLayoutParams();
    lp.gravity=m.mine?Gravity.END:Gravity.START;
    h.bubble.setLayoutParams(lp);

    boolean matched=!query.isEmpty()&&matchSet.contains(position);
    boolean active=matched&&m.id!=null&&m.id.equals(activeId);

    h.bubble.setBackground(bubbleBackground(m.mine,matched,active));

    if("text".equals(m.kind)){
      TextView body=textView(15.5f,m.mine?Color.WHITE:Color.rgb(29,29,31));
      body.setMaxWidth(maxWidth);
      body.setLineSpacing(0,1.05f);
      body.setText(highlight(m.text==null?"":m.text,query,m.mine));
      h.bubble.addView(body,new LinearLayout.LayoutParams(-2,-2));
    }else{
      LinearLayout title=new LinearLayout(c);title.setGravity(Gravity.CENTER_VERTICAL);
      FrameLayout iconBox=new FrameLayout(c);
      iconBox.setBackground(makeColor(m.mine?0x1FFFFFFF:0x12687EE0,dp(11)));
      ImageView clip=new ImageView(c);
      clip.setImageResource(R.drawable.ic_attachment);
      clip.setColorFilter(m.mine?0xE6FFFFFF:Color.rgb(104,126,224));
      clip.setPadding(dp(8),dp(8),dp(8),dp(8));
      iconBox.addView(clip,new FrameLayout.LayoutParams(-1,-1));
      title.addView(iconBox,new LinearLayout.LayoutParams(dp(34),dp(34)));

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

    if(m.mine&&position==latestMinePosition){
      TextView state=textView(10.5f,0xAFFFFFFF);
      state.setText("sent".equals(m.status)?"已送达":"正在发送");
      state.setGravity(Gravity.END);
      LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(5);
      h.bubble.addView(state,sp);
    }
  }

  @Override public int getItemCount(){return differ.getCurrentList().size();}

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
    GradientDrawable g=new GradientDrawable();
    if(mine){
      g.setColor(Color.rgb(45,124,246));
      float r=dp(19),tight=dp(6);
      g.setCornerRadii(new float[]{r,r,r,r,tight,tight,r,r});
    }else{
      g.setColor(0xD9FFFFFF);
      float r=dp(19),tight=dp(6);
      g.setCornerRadii(new float[]{r,r,r,r,r,r,tight,tight});
      g.setStroke(1,0x66FFFFFF);
    }
    if(active)g.setStroke(dp(3),0xDDE09A30);
    else if(matched)g.setStroke(dp(2),0xA8ECB758);
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
