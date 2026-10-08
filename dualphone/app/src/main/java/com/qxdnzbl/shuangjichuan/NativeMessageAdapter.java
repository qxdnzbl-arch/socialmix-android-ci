package com.qxdnzbl.shuangjichuan;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.text.*;
import android.text.style.BackgroundColorSpan;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import java.util.concurrent.*;

public class NativeMessageAdapter extends RecyclerView.Adapter<NativeMessageAdapter.Holder>{
  public interface Callbacks{
    void onLongPress(View anchor,TransferDb.Msg msg);
    void onFileClick(TransferDb.Msg msg);
  }
  private static final int TYPE_TEXT=0,TYPE_FILE=1,TYPE_IMAGE=2;
  private final Context c;
  private final Callbacks cb;
  private final ArrayList<TransferDb.Msg> items=new ArrayList<>();
  private final ArrayList<Integer> matches=new ArrayList<>();
  private final HashSet<Integer> matchedPositions=new HashSet<>();
  private final ExecutorService images=Executors.newFixedThreadPool(2);
  private final Handler main=new Handler(Looper.getMainLooper());
  private final LruCache<String,Bitmap> thumbnails=new LruCache<String,Bitmap>(12*1024*1024){
    @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
  };
  private final Set<String> decoding=new HashSet<>();
  private String query="",activeId=null,link="searching";
  private final int maxWidth;
  private int lastMine=-1;
  private boolean closed;

  public NativeMessageAdapter(Context c,Callbacks cb){
    this.c=c;this.cb=cb;
    maxWidth=Math.round(c.getResources().getDisplayMetrics().widthPixels*.76f);
    setHasStableIds(true);
  }
  public void close(){closed=true;images.shutdownNow();main.removeCallbacksAndMessages(null);thumbnails.evictAll();}
  public void setLinkState(String next){
    if(Objects.equals(link,next))return;link=next;
    if(lastMine>=0&&lastMine<items.size())notifyItemChanged(lastMine);
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
    }else{items.clear();items.addAll(n);notifyDataSetChanged();}
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
  public void setSearch(String q,String active){String next=q==null?"":q.trim();if(Objects.equals(query,next)&&Objects.equals(activeId,active))return;query=next;activeId=active;rebuildMatches();notifyDataSetChanged();}
  public List<Integer> getMatchPositions(){return new ArrayList<>(matches);}
  public String getItemIdAt(int p){return p>=0&&p<items.size()?items.get(p).id:null;}
  public List<TransferDb.Msg> getPhotos(){
    ArrayList<TransferDb.Msg> photos=new ArrayList<>();
    for(TransferDb.Msg m:items)if("file".equals(m.kind)&&PhotoImages.isPhoto(m.fileName))photos.add(m);
    return photos;
  }
  private void rebuildMatches(){
    matches.clear();matchedPositions.clear();if(query.isEmpty())return;
    String q=query.toLowerCase(Locale.ROOT);
    for(int i=0;i<items.size();i++){
      TransferDb.Msg m=items.get(i);
      String hay="file".equals(m.kind)?String.valueOf(m.fileName):String.valueOf(m.text);
      if(hay.toLowerCase(Locale.ROOT).contains(q)){matches.add(i);matchedPositions.add(i);}
    }
  }
  @Override public long getItemId(int p){String id=items.get(p).id;return id==null?p:(((long)id.hashCode())<<32)^id.length();}
  @Override public int getItemViewType(int p){TransferDb.Msg m=items.get(p);return !"file".equals(m.kind)?TYPE_TEXT:PhotoImages.isPhoto(m.fileName)?TYPE_IMAGE:TYPE_FILE;}
  @Override public int getItemCount(){return items.size();}

  @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
    FrameLayout root=new FrameLayout(c);root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
    root.setPadding(dp(12),dp(4),dp(12),dp(4));
    LinearLayout column=new LinearLayout(c);column.setOrientation(LinearLayout.VERTICAL);
    root.addView(column,new FrameLayout.LayoutParams(-2,-2));
    LinearLayout bubble=new LinearLayout(c);bubble.setOrientation(LinearLayout.VERTICAL);
    bubble.setPadding(dp(12),dp(9),dp(12),dp(9));
    column.addView(bubble,new LinearLayout.LayoutParams(-2,-2));
    TextView state=textView(10.5f,Color.rgb(89,96,107));state.setGravity(Gravity.END);
    state.setPadding(dp(6),dp(2),dp(6),dp(2));state.setBackground(makeColor(Color.rgb(231,233,238),dp(5)));
    LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.gravity=Gravity.END;sp.topMargin=dp(4);column.addView(state,sp);
    if(type==TYPE_TEXT){
      TextView body=textView(15.5f,Color.BLACK);body.setMaxWidth(maxWidth-dp(24));body.setLineSpacing(0,1.06f);
      bubble.addView(body,new LinearLayout.LayoutParams(-2,-2));
      return new TextHolder(root,column,bubble,state,body);
    }
    if(type==TYPE_IMAGE){
      bubble.setPadding(0,0,0,0);bubble.setClipToOutline(true);
      ImageView photo=new ImageView(c);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
      int width=Math.min(maxWidth,dp(216));bubble.addView(photo,new LinearLayout.LayoutParams(width,width*3/4));
      return new ImageHolder(root,column,bubble,state,photo);
    }
    LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
    ImageView icon=new ImageView(c);icon.setImageResource(R.drawable.ic_attachment);icon.setPadding(dp(8),dp(8),dp(8),dp(8));
    row.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(42)));
    LinearLayout details=new LinearLayout(c);details.setOrientation(LinearLayout.VERTICAL);
    TextView label=textView(14f,Color.rgb(34,36,40));label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
    label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);
    details.addView(label,new LinearLayout.LayoutParams(-1,-2));
    TextView meta=textView(11.5f,Color.rgb(101,107,116));
    LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(4);details.addView(meta,mp);
    LinearLayout.LayoutParams detailsLp=new LinearLayout.LayoutParams(0,-2,1);detailsLp.leftMargin=dp(7);row.addView(details,detailsLp);
    bubble.addView(row,new LinearLayout.LayoutParams(Math.min(maxWidth-dp(24),dp(212)),-2));
    return new FileHolder(root,column,bubble,state,icon,label,meta);
  }

  @Override public void onBindViewHolder(Holder holder,int position){
    TransferDb.Msg m=items.get(position);
    FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)holder.column.getLayoutParams();lp.gravity=m.mine?Gravity.END:Gravity.START;holder.column.setLayoutParams(lp);
    boolean matched=!query.isEmpty()&&matchedPositions.contains(position),active=matched&&Objects.equals(m.id,activeId);
    boolean card=holder instanceof FileHolder;
    holder.bubble.setBackground(bubbleBackground(m.mine,card,matched,active));
    holder.bubble.setOnLongClickListener(v->{cb.onLongPress(v,m);return true;});
    holder.bubble.setOnClickListener(null);
    bindState(holder.state,m,position);
    if(holder instanceof TextHolder){
      TextHolder h=(TextHolder)holder;h.body.setTextColor(m.mine?Color.WHITE:Color.rgb(35,38,43));
      h.body.setText(highlight(m.text==null?"":m.text,query,m.mine));
    }else if(holder instanceof ImageHolder){
      ImageHolder h=(ImageHolder)holder;h.photo.setTag(m.filePath);h.photo.setContentDescription("图片："+m.fileName);
      h.photo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);h.photo.setImageResource(android.R.drawable.ic_menu_gallery);holder.bubble.setOnClickListener(v->cb.onFileClick(m));
      Bitmap cached=m.filePath==null?null:thumbnails.get(m.filePath);
      if(cached!=null){h.photo.setScaleType(ImageView.ScaleType.CENTER_CROP);h.photo.setImageBitmap(cached);}else loadThumbnail(m.filePath);
    }else{
      FileHolder h=(FileHolder)holder;h.icon.setColorFilter(Color.rgb(62,68,77));
      h.label.setText(highlight(friendlyFileLabel(m.fileName),query,false));
      h.meta.setText(size(m.fileSize)+(m.mine?"":"  ·  保存到下载"));
      holder.bubble.setOnClickListener(v->cb.onFileClick(m));
    }
  }
  private void loadThumbnail(String path){
    if(path==null||closed||decoding.contains(path))return;
    decoding.add(path);
    images.execute(()->{
      Bitmap decoded=PhotoImages.decode(path,dp(320));
      main.post(()->{
        decoding.remove(path);if(closed){if(decoded!=null)decoded.recycle();return;}
        if(decoded!=null)thumbnails.put(path,decoded);
        for(int i=0;i<items.size();i++)if(Objects.equals(items.get(i).filePath,path)){
          if(decoded!=null)notifyItemChanged(i);break;
        }
      });
    });
  }
  @Override public void onViewRecycled(Holder h){if(h instanceof ImageHolder){((ImageHolder)h).photo.setTag(null);((ImageHolder)h).photo.setImageDrawable(null);}super.onViewRecycled(h);}
  private void bindState(TextView state,TransferDb.Msg m,int position){
    boolean show=m.mine&&position==lastMine;state.setVisibility(show?View.VISIBLE:View.GONE);
    if(show)state.setText("sent".equals(m.status)?"已送达":("nearby".equals(link)||"relay".equals(link))?"待发送":"等待连接");
  }
  private CharSequence highlight(String text,String q,boolean mine){
    if(q==null||q.isEmpty())return text;
    String low=text.toLowerCase(Locale.ROOT),needle=q.toLowerCase(Locale.ROOT);SpannableString s=new SpannableString(text);int from=0,i;
    while((i=low.indexOf(needle,from))>=0){s.setSpan(new BackgroundColorSpan(mine?0xFF94712E:0xFFF0D9AB),i,i+q.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);from=i+q.length();}
    return s;
  }
  private GradientDrawable bubbleBackground(boolean mine,boolean card,boolean matched,boolean active){
    GradientDrawable g=makeColor(card?Color.rgb(238,240,243):mine?Color.rgb(48,51,58):Color.rgb(240,242,245),dp(13));
    if(active)g.setStroke(dp(3),0xFFE09A30);else if(matched)g.setStroke(dp(2),0xFFBD8C35);else if(card||!mine)g.setStroke(dp(1),0xFFD0D4DB);
    return g;
  }
  private TextView textView(float sp,int color){TextView v=new TextView(c);v.setTextSize(sp);v.setTextColor(color);v.setIncludeFontPadding(false);return v;}
  private GradientDrawable makeColor(int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(radius);return g;}
  private int dp(float v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
  private String size(long n){if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.US,"%.1f KB",n/1024f);return String.format(Locale.US,"%.1f MB",n/1048576f);}
  public static String friendlyFileLabel(String name){return name==null||name.trim().isEmpty()?"文件":name.trim();}

  static abstract class Holder extends RecyclerView.ViewHolder{
    final LinearLayout column,bubble;final TextView state;
    Holder(View v,LinearLayout col,LinearLayout b,TextView st){super(v);column=col;bubble=b;state=st;}
  }
  static class TextHolder extends Holder{final TextView body;TextHolder(View v,LinearLayout col,LinearLayout b,TextView s,TextView body){super(v,col,b,s);this.body=body;}}
  static class FileHolder extends Holder{final ImageView icon;final TextView label,meta;FileHolder(View v,LinearLayout col,LinearLayout b,TextView s,ImageView i,TextView l,TextView m){super(v,col,b,s);icon=i;label=l;meta=m;}}
  static class ImageHolder extends Holder{final ImageView photo;ImageHolder(View v,LinearLayout col,LinearLayout b,TextView s,ImageView p){super(v,col,b,s);photo=p;}}
}
