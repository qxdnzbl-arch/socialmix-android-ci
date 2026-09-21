package com.kehua.revival;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import android.util.Base64;

public class MainActivity extends Activity {
  final ExecutorService io=Executors.newFixedThreadPool(3);
  LinearLayout root, body, bottomNav;
  String token="", uid="", api=BuildConfig.API_BASE_URL;
  Uri selectedPostMedia, selectedChatMedia;
  String selectedChatConversation="";
  boolean dark=false;
  int bg, card, ink, muted, line, purple, pink;
  final int MATCH_REQ=7, CHAT_MEDIA_REQ=8;

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    token=getPreferences(MODE_PRIVATE).getString("token","");
    uid=getPreferences(MODE_PRIVATE).getString("uid","");
    dark=getPreferences(MODE_PRIVATE).getBoolean("dark",false);
    palette();
    shell();
    if(token.isEmpty()) showLogin(); else { loadMeSilently(); showNow(); }
  }

  int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
  void palette(){
    bg=dark?Color.rgb(16,16,18):Color.rgb(247,247,249);
    card=dark?Color.rgb(31,31,34):Color.WHITE;
    ink=dark?Color.rgb(246,246,248):Color.rgb(32,32,36);
    muted=dark?Color.rgb(155,155,163):Color.rgb(135,135,144);
    line=dark?Color.rgb(55,55,60):Color.rgb(232,232,237);
    purple=Color.rgb(92,93,232);
    pink=Color.rgb(255,39,94);
  }
  GradientDrawable round(int color,float r){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(r));return g;}
  GradientDrawable roundStroke(int color,float r,int strokeColor){GradientDrawable g=round(color,r);g.setStroke(dp(1),strokeColor);return g;}
  GradientDrawable gradient(){
    GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
      dark?new int[]{Color.rgb(48,46,58),Color.rgb(35,30,31)}:new int[]{Color.rgb(224,232,255),Color.rgb(255,234,224)});
    g.setCornerRadius(dp(28)); return g;
  }
  TextView text(String s,float sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setIncludeFontPadding(false);return v;}
  Space gap(int h){Space s=new Space(this);s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(h)));return s;}
  Button pill(String s,boolean primary){
    Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setMinHeight(dp(44));
    b.setPadding(dp(18),0,dp(18),0);b.setTextColor(primary?Color.WHITE:ink);
    b.setBackground(primary?round(purple,23):roundStroke(card,23,line));return b;
  }
  EditText field(String hint){
    EditText e=new EditText(this);e.setHint(hint);e.setTextColor(ink);e.setHintTextColor(muted);e.setTextSize(16);
    e.setSingleLine(false);e.setPadding(dp(16),dp(14),dp(16),dp(14));e.setBackground(round(card,18));return e;
  }
  LinearLayout col(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;}
  LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);x.setGravity(Gravity.CENTER_VERTICAL);return x;}
  void shell(){
    getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
    getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    root=col();root.setBackgroundColor(bg);
    body=col();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
    bottomNav=row();bottomNav.setPadding(dp(26),dp(7),dp(26),dp(9));bottomNav.setBackgroundColor(dark?Color.rgb(22,22,24):Color.WHITE);
    root.addView(bottomNav,new LinearLayout.LayoutParams(-1,dp(66)));
    setContentView(root);
  }
  void clear(){body.removeAllViews();}
  void noNav(){bottomNav.removeAllViews();bottomNav.setVisibility(View.GONE);}
  void nav(int active){
    bottomNav.setVisibility(View.VISIBLE);bottomNav.removeAllViews();
    addNavIcon(0,active,"home",this::showNow);
    addNavIcon(1,active,"chat",this::showConversations);
    addNavIcon(2,active,"person",this::showMe);
  }
  void addNavIcon(int index,int active,String type,Runnable action){
    IconView v=new IconView(this,type,index==active?ink:muted);
    v.setOnClickListener(x->action.run());
    bottomNav.addView(v,new LinearLayout.LayoutParams(0,-1,1));
  }
  class IconView extends View {
    Paint p=new Paint(1);String type;int c;
    IconView(Context cxt,String t,int color){super(cxt);type=t;c=color;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.8f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
    @Override protected void onDraw(Canvas x){super.onDraw(x);p.setColor(c);float cx=getWidth()/2f,cy=getHeight()/2f;float s=dp(11);
      if(type.equals("home")){Path q=new Path();q.moveTo(cx-s,cy);q.lineTo(cx,cy-s*.85f);q.lineTo(cx+s,cy);q.moveTo(cx-s*.72f,cy-dp(1));q.lineTo(cx-s*.72f,cy+s*.8f);q.lineTo(cx+s*.72f,cy+s*.8f);q.lineTo(cx+s*.72f,cy-dp(1));x.drawPath(q,p);}
      else if(type.equals("chat")){RectF r=new RectF(cx-s,cy-s*.72f,cx+s,cy+s*.55f);x.drawRoundRect(r,dp(7),dp(7),p);Path q=new Path();q.moveTo(cx-dp(4),cy+s*.55f);q.lineTo(cx-dp(7),cy+s*.9f);q.lineTo(cx,cy+s*.55f);x.drawPath(q,p);}
      else {x.drawCircle(cx,cy-dp(6),dp(4.5f),p);RectF r=new RectF(cx-dp(8),cy+dp(1),cx+dp(8),cy+dp(13));x.drawArc(r,205,130,false,p);}
    }
  }

  void showLogin(){
    noNav();clear();
    LinearLayout wrap=col();wrap.setPadding(dp(28),dp(74),dp(28),dp(28));body.addView(wrap,new LinearLayout.LayoutParams(-1,-1));
    TextView logo=text("可话",34,ink);logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);wrap.addView(logo);
    wrap.addView(gap(10));wrap.addView(text("想说的话，都聊得来。",17,muted));wrap.addView(gap(54));
    EditText phone=field("手机号");phone.setSingleLine(true);phone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);wrap.addView(phone,new LinearLayout.LayoutParams(-1,dp(56)));
    wrap.addView(gap(12));EditText code=field("验证码");code.setSingleLine(true);code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);wrap.addView(code,new LinearLayout.LayoutParams(-1,dp(56)));
    wrap.addView(gap(18));LinearLayout buttons=row();Button send=pill("获取验证码",false), login=pill("进入可话",true);
    buttons.addView(send,new LinearLayout.LayoutParams(0,dp(48),1));Space sp=new Space(this);buttons.addView(sp,new LinearLayout.LayoutParams(dp(10),1));buttons.addView(login,new LinearLayout.LayoutParams(0,dp(48),1));wrap.addView(buttons);
    TextView note=text("",13,muted);note.setPadding(0,dp(16),0,0);wrap.addView(note);
    send.setOnClickListener(v->{String ph=phone.getText().toString().trim();if(ph.isEmpty()){note.setText("请先输入手机号");return;}send.setEnabled(false);post("/v1/auth/request-code",obj("phone",ph),"",(j)->runOnUiThread(()->{send.setEnabled(true);note.setText("验证码已发送");}),e->runOnUiThread(()->{send.setEnabled(true);note.setText(errorText(e));}));});
    login.setOnClickListener(v->{String ph=phone.getText().toString().trim(),co=code.getText().toString().trim();if(ph.isEmpty()||co.isEmpty()){note.setText("请输入手机号和验证码");return;}login.setEnabled(false);post("/v1/auth/verify-code",obj("phone",ph,"code",co),"",(j)->{token=j.optString("token");JSONObject u=j.optJSONObject("user");uid=u==null?"":u.optString("id");getPreferences(MODE_PRIVATE).edit().putString("token",token).putString("uid",uid).apply();runOnUiThread(()->{login.setEnabled(true);showNow();});},e->runOnUiThread(()->{login.setEnabled(true);note.setText(errorText(e));}));});
  }

  void showNow(){
    clear();nav(0);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
    LinearLayout page=col();page.setPadding(dp(20),dp(20),dp(20),dp(18));scroll.addView(page,new ScrollView.LayoutParams(-1,-1));
    TextView date=text(new SimpleDateFormat("M月d日 EEEE",Locale.CHINA).format(new Date()),13,muted);page.addView(date);
    page.addView(gap(10));TextView h=text("此刻，说你想说的话～",23,ink);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);page.addView(h);
    page.addView(gap(18));
    LinearLayout canvas=col();canvas.setPadding(dp(14),dp(14),dp(14),dp(12));canvas.setBackground(gradient());page.addView(canvas,new LinearLayout.LayoutParams(-1,dp(430)));
    EditText e=field("在这满足口里的世界，写下你此刻真实的想法或感受…");e.setGravity(Gravity.TOP);e.setBackground(round(dark?Color.rgb(34,34,38):0xF7FFFFFF,22));canvas.addView(e,new LinearLayout.LayoutParams(-1,0,1));
    TextView mediaNote=text("",13,muted);mediaNote.setPadding(dp(6),dp(8),dp(6),0);canvas.addView(mediaNote);
    LinearLayout controls=row();controls.setPadding(0,dp(8),0,0);Button media=pill("＋ 图片 / 视频",false), publish=pill("说出去",true);
    controls.addView(media,new LinearLayout.LayoutParams(0,dp(46),1));Space gs=new Space(this);controls.addView(gs,new LinearLayout.LayoutParams(dp(10),1));controls.addView(publish,new LinearLayout.LayoutParams(0,dp(46),1));canvas.addView(controls);
    media.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,MATCH_REQ);});
    mediaNote.setOnClickListener(v->{selectedPostMedia=null;mediaNote.setText("");});
    publish.setOnClickListener(v->{String t=e.getText().toString().trim();if(t.isEmpty()&&selectedPostMedia==null){toast("写点什么再说出去吧");return;}publish.setEnabled(false);if(selectedPostMedia!=null)uploadAndPost(t,publish);else createPost(t,null,publish);});
    page.addView(gap(16));TextView tip=text("你说的话不会进入公开广场，只会去寻找可能懂你的人。",13,muted);tip.setGravity(Gravity.CENTER);page.addView(tip);
    page.setTag(mediaNote);
  }

  @Override protected void onActivityResult(int request,int result,Intent d){
    super.onActivityResult(request,result,d);if(result!=RESULT_OK||d==null)return;
    if(request==MATCH_REQ){selectedPostMedia=d.getData();View t=findTaggedText(body);if(t instanceof TextView)((TextView)t).setText("已选素材 · 点这里取消");}
    if(request==CHAT_MEDIA_REQ){selectedChatMedia=d.getData();if(selectedChatConversation!=null&&!selectedChatConversation.isEmpty())uploadChatMedia(selectedChatConversation,selectedChatMedia);}
  }
  View findTaggedText(ViewGroup g){
    for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(v.getTag() instanceof TextView)return (View)v.getTag();if(v instanceof ViewGroup){View f=findTaggedText((ViewGroup)v);if(f!=null)return f;}}return null;
  }

  void uploadAndPost(String text,Button b){
    Uri uri=selectedPostMedia;
    io.execute(()->{try{String mime=getContentResolver().getType(uri);byte[] data=read(getContentResolver().openInputStream(uri));String kind=mime!=null&&mime.startsWith("video")?"video":"image";JSONObject up=sync("POST","/v1/media",obj("kind",kind,"mime",mime,"base64",Base64.encodeToString(data,Base64.NO_WRAP)),token);String id=up.getJSONObject("media").getString("id");runOnUiThread(()->createPost(text,id,b));}catch(Exception x){runOnUiThread(()->{b.setEnabled(true);toast("素材上传失败");});}});
  }
  void createPost(String text,String mediaId,Button b){
    JSONObject p=obj("body",text,"media_ids",mediaId!=null?arr(mediaId):JSONObject.NULL);if(mediaId==null)p.remove("media_ids");
    post("/v1/posts",p,token,j->{JSONObject post=j.optJSONObject("post");String id=post==null?"":post.optString("id");post("/v1/match",obj("source","post","post_id",id),token,x->runOnUiThread(()->{selectedPostMedia=null;b.setEnabled(true);showResonanceFrom(x);}),e->runOnUiThread(()->{b.setEnabled(true);toast(errorText(e));}));},e->runOnUiThread(()->{b.setEnabled(true);toast(errorText(e));}));
  }

  void showResonanceFrom(JSONObject match){
    JSONArray a=match.optJSONArray("candidates");if(a==null||a.length()==0){showNoResonance();return;}showResonanceCards(a,0);
  }
  void showNoResonance(){
    clear();noNav();LinearLayout p=col();p.setPadding(dp(24),dp(20),dp(24),dp(24));body.addView(p,new LinearLayout.LayoutParams(-1,-1));
    TextView x=text("×",34,ink);x.setPadding(0,0,0,0);x.setOnClickListener(v->showNow());p.addView(x,new LinearLayout.LayoutParams(dp(48),dp(48)));
    Space s=new Space(this);p.addView(s,new LinearLayout.LayoutParams(1,0,1));
    TextView h=text("这次还没遇见共鸣",22,ink);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);h.setGravity(Gravity.CENTER);p.addView(h);
    p.addView(gap(12));TextView m=text("过一会儿再来看看。你的话已经留在这里。",15,muted);m.setGravity(Gravity.CENTER);p.addView(m);
    p.addView(gap(28));Button back=pill("回到此刻",true);back.setOnClickListener(v->showNow());p.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));Space s2=new Space(this);p.addView(s2,new LinearLayout.LayoutParams(1,0,1));
  }
  void showResonanceCards(JSONArray items,int index){
    clear();noNav();
    JSONObject item=items.optJSONObject(index),post=item==null?null:item.optJSONObject("post");if(post==null){showNoResonance();return;}
    String postId=post.optString("id"),authorId=post.optString("author_id"),content=post.optString("body","（图片或视频）");
    LinearLayout page=col();page.setPadding(dp(18),dp(14),dp(18),dp(14));body.addView(page,new LinearLayout.LayoutParams(-1,-1));
    LinearLayout top=row();TextView close=text("×",32,ink);close.setGravity(Gravity.CENTER);close.setOnClickListener(v->showNow());top.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
    Space grow=new Space(this);top.addView(grow,new LinearLayout.LayoutParams(0,1,1));TextView count=text((index+1)+"/"+items.length(),13,muted);count.setGravity(Gravity.CENTER);top.addView(count,new LinearLayout.LayoutParams(dp(56),dp(48)));
    TextView more=text("•••",18,muted);more.setGravity(Gravity.CENTER);top.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(top);
    LinearLayout cardBox=col();cardBox.setPadding(dp(20),dp(24),dp(20),dp(16));cardBox.setBackground(round(card,24));page.addView(cardBox,new LinearLayout.LayoutParams(-1,0,1));
    ScrollView sv=new ScrollView(this);LinearLayout inner=col();inner.setPadding(0,dp(6),0,dp(12));sv.addView(inner);cardBox.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    TextView postText=text(content,19,ink);postText.setLineSpacing(dp(5),1f);inner.addView(postText);
    JSONArray media=post.optJSONArray("media");if(media!=null&&media.length()>0){inner.addView(gap(18));JSONObject m=media.optJSONObject(0);if(m!=null&&"image".equals(m.optString("kind"))){ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setBackground(round(line,16));inner.addView(iv,new LinearLayout.LayoutParams(-1,dp(210)));loadImage(m.optString("url"),iv); } else {TextView mv=text("▶  视频",15,muted);mv.setGravity(Gravity.CENTER);mv.setBackground(round(dark?0xFF28282C:0xFFF2F2F5,16));inner.addView(mv,new LinearLayout.LayoutParams(-1,dp(120)));}}
    Button light=pill("☀  点亮",false);light.setOnClickListener(v->{light.setEnabled(false);lightAndOpen(postId,authorId,"聊天",null);});cardBox.addView(light,new LinearLayout.LayoutParams(-1,dp(46)));
    LinearLayout reply=row();reply.setPadding(0,dp(10),0,0);EditText input=field("回复这条共鸣…");input.setSingleLine(true);Button send=pill("发送",true);reply.addView(input,new LinearLayout.LayoutParams(0,dp(48),1));Space rs=new Space(this);reply.addView(rs,new LinearLayout.LayoutParams(dp(8),1));reply.addView(send,new LinearLayout.LayoutParams(dp(76),dp(48)));page.addView(reply);
    send.setOnClickListener(v->{String s=input.getText().toString().trim();if(s.isEmpty())return;send.setEnabled(false);lightAndOpen(postId,authorId,"聊天",s);});
    final float[] down={0};cardBox.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN){down[0]=e.getX();return true;}if(e.getAction()==MotionEvent.ACTION_UP){float d=e.getX()-down[0];if(Math.abs(d)>dp(70)){int next=d<0?index+1:index-1;if(next>=0&&next<items.length())showResonanceCards(items,next);}return true;}return true;});
  }
  void lightAndOpen(String postId,String authorId,String name,String firstMessage){
    post("/v1/posts/"+postId+"/light",new JSONObject(),token,z->post("/v1/conversations",obj("other_user_id",authorId),token,c->{JSONObject co=c.optJSONObject("conversation");String cid=co==null?"":co.optString("id");if(firstMessage!=null&&!firstMessage.isEmpty())post("/v1/conversations/"+cid+"/messages",obj("kind","text","body",firstMessage,"origin_post_id",postId),token,m->runOnUiThread(()->showChat(cid,name)),e->runOnUiThread(()->toast(errorText(e))));else runOnUiThread(()->showChat(cid,name));},e->runOnUiThread(()->toast(errorText(e)))),e->runOnUiThread(()->toast(errorText(e))));
  }

  void showConversations(){
    clear();nav(1);
    LinearLayout page=col();page.setPadding(dp(20),dp(18),dp(20),0);body.addView(page,new LinearLayout.LayoutParams(-1,-1));
    LinearLayout top=row();TextView h=text("消息",26,ink);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);top.addView(h,new LinearLayout.LayoutParams(0,dp(48),1));TextView friends=text("♙",25,ink);friends.setGravity(Gravity.CENTER);friends.setOnClickListener(v->showFriends());top.addView(friends,new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(top);
    ScrollView sv=new ScrollView(this);LinearLayout list=col();sv.addView(list);page.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    get("/v1/conversations",token,j->{JSONArray a=j.optJSONArray("conversations");runOnUiThread(()->renderConversations(list,a));},e->runOnUiThread(()->emptyState(list,"消息暂时没加载出来")));
  }
  void renderConversations(LinearLayout list,JSONArray a){
    list.removeAllViews();if(a==null||a.length()==0){emptyState(list,"还没有聊天。\n点亮一条共鸣后，聊天会出现在这里。");return;}
    for(int i=0;i<a.length();i++){JSONObject c=a.optJSONObject(i),u=c==null?null:c.optJSONObject("other"),last=c==null?null:c.optJSONObject("last_message");String nick=u==null?"一个人":u.optString("nickname","一个人"),cid=c.optString("id");int unread=c.optInt("unread",0);
      LinearLayout item=row();item.setPadding(dp(2),dp(10),dp(2),dp(10));TextView avatar=avatar(nick);item.addView(avatar,new LinearLayout.LayoutParams(dp(50),dp(50)));
      LinearLayout meta=col();meta.setPadding(dp(12),0,dp(8),0);TextView n=text(nick,16,ink);n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);meta.addView(n);String preview=last==null?"开始聊天":(!last.isNull("recalled_at")?"消息已撤回":last.optString("body","[媒体]"));TextView pv=text(preview,14,muted);pv.setMaxLines(1);meta.addView(pv);item.addView(meta,new LinearLayout.LayoutParams(0,dp(52),1));
      if(unread>0){TextView badge=text(String.valueOf(Math.min(unread,99)),11,Color.WHITE);badge.setGravity(Gravity.CENTER);badge.setBackground(round(pink,11));item.addView(badge,new LinearLayout.LayoutParams(dp(22),dp(22)));}
      item.setBackground(round(card,16));item.setOnClickListener(v->showChat(cid,nick));list.addView(item,new LinearLayout.LayoutParams(-1,dp(70)));list.addView(gap(7));
    }
  }
  TextView avatar(String nick){String s=(nick==null||nick.isEmpty())?"可":nick.substring(0,1);TextView a=text(s,18,ink);a.setGravity(Gravity.CENTER);a.setTypeface(Typeface.DEFAULT,Typeface.BOLD);a.setBackground(round(dark?0xFF44444A:0xFFF0EDF8,25));return a;}

  void showFriends(){
    clear();noNav();LinearLayout p=col();p.setPadding(dp(20),dp(16),dp(20),0);body.addView(p,new LinearLayout.LayoutParams(-1,-1));header(p,"好友",this::showConversations);
    ScrollView sv=new ScrollView(this);LinearLayout list=col();sv.addView(list);p.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    get("/v1/friends",token,j->{JSONArray a=j.optJSONArray("friends");runOnUiThread(()->{if(a==null||a.length()==0){emptyState(list,"还没有好友");return;}for(int i=0;i<a.length();i++){JSONObject f=a.optJSONObject(i),u=f==null?null:f.optJSONObject("profile");if(u==null)continue;String nick=u.optString("nickname","一个人");LinearLayout it=row();it.setPadding(dp(10),dp(10),dp(10),dp(10));it.setBackground(round(card,16));it.addView(avatar(nick),new LinearLayout.LayoutParams(dp(48),dp(48)));TextView t=text(nick,16,ink);t.setPadding(dp(12),0,0,0);it.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));list.addView(it);list.addView(gap(8));}});},e->runOnUiThread(()->emptyState(list,"好友暂时没加载出来")));
  }

  void showChat(String cid,String name){
    clear();noNav();selectedChatConversation=cid;
    LinearLayout p=col();p.setPadding(dp(14),dp(10),dp(14),dp(10));body.addView(p,new LinearLayout.LayoutParams(-1,-1));header(p,name,this::showConversations);
    ScrollView sv=new ScrollView(this);sv.setFillViewport(true);LinearLayout list=col();list.setPadding(dp(4),dp(12),dp(4),dp(12));sv.addView(list);p.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout composer=row();EditText e=field("回复 "+name+"…");e.setSingleLine(true);TextView pic=text("▧",24,ink);pic.setGravity(Gravity.CENTER);Button send=pill("发送",true);composer.addView(e,new LinearLayout.LayoutParams(0,dp(48),1));composer.addView(pic,new LinearLayout.LayoutParams(dp(48),dp(48)));composer.addView(send,new LinearLayout.LayoutParams(dp(76),dp(48)));p.addView(composer);
    Runnable refresh=()->loadMessages(cid,list,sv);
    refresh.run();
    send.setOnClickListener(v->{String s=e.getText().toString().trim();if(s.isEmpty())return;send.setEnabled(false);post("/v1/conversations/"+cid+"/messages",obj("kind","text","body",s),token,j->runOnUiThread(()->{e.setText("");send.setEnabled(true);refresh.run();}),x->runOnUiThread(()->{send.setEnabled(true);toast(errorText(x));}));});
    pic.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*","image/gif"});i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,CHAT_MEDIA_REQ);});
  }
  void loadMessages(String cid,LinearLayout list,ScrollView sv){
    get("/v1/conversations/"+cid+"/messages",token,j->{JSONArray a=j.optJSONArray("messages");runOnUiThread(()->{list.removeAllViews();if(a!=null)for(int i=0;i<a.length();i++){JSONObject m=a.optJSONObject(i);if(m==null)continue;boolean mine=uid.equals(m.optString("sender_id")),recalled=!m.isNull("recalled_at");LinearLayout lineRow=row();lineRow.setGravity(mine?Gravity.RIGHT:Gravity.LEFT);LinearLayout bubble=col();bubble.setPadding(dp(14),dp(10),dp(14),dp(10));bubble.setBackground(round(mine?purple:card,17));String bodyText=recalled?"消息已撤回":m.optString("body","");TextView t=text(bodyText.isEmpty()?"[媒体]":bodyText,16,mine?Color.WHITE:ink);t.setMaxWidth(dp(280));bubble.addView(t);
      String mediaUrl=m.optString("media_url","");String kind=m.optString("kind","text");if(!recalled&&!mediaUrl.isEmpty()&&("image".equals(kind)||"gif".equals(kind))){ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);bubble.addView(iv,new LinearLayout.LayoutParams(dp(190),dp(190)));loadImage(mediaUrl,iv);}else if(!recalled&&!mediaUrl.isEmpty()&&"video".equals(kind)){TextView vv=text("▶  视频",14,mine?Color.WHITE:muted);vv.setPadding(0,dp(6),0,0);bubble.addView(vv);}
      String mid=m.optString("id");if(mine&&!recalled)bubble.setOnLongClickListener(v->{new AlertDialog.Builder(this).setItems(new String[]{"撤回"},(d,w)->post("/v1/messages/"+mid+"/recall",new JSONObject(),token,z->runOnUiThread(()->loadMessages(cid,list,sv)),e->runOnUiThread(()->toast(errorText(e))))).show();return true;});
      lineRow.addView(bubble);list.addView(lineRow,new LinearLayout.LayoutParams(-1,-2));list.addView(gap(8));}sv.post(()->sv.fullScroll(View.FOCUS_DOWN));});},e->runOnUiThread(()->toast(errorText(e))));
  }
  void uploadChatMedia(String cid,Uri uri){
    io.execute(()->{try{String mime=getContentResolver().getType(uri);byte[] data=read(getContentResolver().openInputStream(uri));String kind=mime!=null&&mime.contains("gif")?"gif":(mime!=null&&mime.startsWith("video")?"video":"image");JSONObject up=sync("POST","/v1/media",obj("kind",kind,"mime",mime,"base64",Base64.encodeToString(data,Base64.NO_WRAP)),token);String id=up.getJSONObject("media").getString("id");sync("POST","/v1/conversations/"+cid+"/messages",obj("kind",kind,"media_id",id),token);runOnUiThread(()->showChat(cid,"聊天"));}catch(Exception e){runOnUiThread(()->toast("发送素材失败"));}});
  }

  void showMe(){
    clear();nav(2);ScrollView sv=new ScrollView(this);LinearLayout p=col();p.setPadding(dp(20),dp(20),dp(20),dp(22));sv.addView(p);body.addView(sv,new LinearLayout.LayoutParams(-1,-1));
    get("/v1/me",token,j->{JSONObject u=j.optJSONObject("user"),ent=j.optJSONObject("entitlements");if(u!=null){uid=u.optString("id",uid);getPreferences(MODE_PRIVATE).edit().putString("uid",uid).apply();}runOnUiThread(()->renderMe(p,u,ent));},e->runOnUiThread(()->emptyState(p,"个人页暂时没加载出来")));
  }
  void renderMe(LinearLayout p,JSONObject u,JSONObject ent){
    p.removeAllViews();String nick=u==null?"可话用户":u.optString("nickname","可话用户");p.addView(avatar(nick),new LinearLayout.LayoutParams(dp(72),dp(72)));p.addView(gap(14));TextView n=text(nick,24,ink);n.setTypeface(Typeface.DEFAULT,Typeface.BOLD);p.addView(n);p.addView(gap(6));p.addView(text(u==null?"说你想说的话":u.optString("bio","说你想说的话"),14,muted));p.addView(gap(26));
    LinearLayout member=col();member.setPadding(dp(18),dp(16),dp(18),dp(16));member.setBackground(gradient());TextView mh=text(ent!=null&&ent.optBoolean("nearby_priority")?"可话会员 · 已生效":"可话会员",18,ink);mh.setTypeface(Typeface.DEFAULT,Typeface.BOLD);member.addView(mh);member.addView(gap(6));member.addView(text("附近优先 · 共鸣性别筛选 · 遇见次数 · 置顶 · 历史自见",12,muted));p.addView(member);p.addView(gap(12));
    addSetting(p,"我的记录",this::showMyPosts);addSetting(p,"搜索",this::showSearch);addSetting(p,dark?"深色模式：开":"深色模式：关",()->{dark=!dark;getPreferences(MODE_PRIVATE).edit().putBoolean("dark",dark).apply();palette();shell();showMe();});
    addSetting(p,"退出登录",()->{token="";uid="";getPreferences(MODE_PRIVATE).edit().clear().apply();shell();showLogin();});
  }
  void addSetting(LinearLayout p,String s,Runnable r){TextView v=text(s,16,ink);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(16),0,dp(16),0);v.setBackground(round(card,15));v.setOnClickListener(x->r.run());p.addView(v,new LinearLayout.LayoutParams(-1,dp(56)));p.addView(gap(8));}

  void showMyPosts(){
    clear();noNav();LinearLayout p=col();p.setPadding(dp(20),dp(16),dp(20),0);body.addView(p,new LinearLayout.LayoutParams(-1,-1));header(p,"我的记录",this::showMe);ScrollView sv=new ScrollView(this);LinearLayout list=col();sv.addView(list);p.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    get("/v1/posts/mine",token,j->{JSONArray a=j.optJSONArray("posts");runOnUiThread(()->{if(a==null||a.length()==0){emptyState(list,"还没有记录");return;}for(int i=0;i<a.length();i++){JSONObject po=a.optJSONObject(i);LinearLayout c=col();c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(round(card,16));c.addView(text(po.optString("body","[媒体]"),16,ink));String state=(po.optBoolean("pinned")?"已置顶 · ":"")+("self".equals(po.optString("visibility"))?"仅自己可见":"寻找共鸣");TextView st=text(state,12,muted);st.setPadding(0,dp(8),0,0);c.addView(st);list.addView(c);list.addView(gap(8));}});},e->runOnUiThread(()->emptyState(list,"记录暂时没加载出来")));
  }

  void showSearch(){
    clear();noNav();LinearLayout p=col();p.setPadding(dp(20),dp(16),dp(20),0);body.addView(p,new LinearLayout.LayoutParams(-1,-1));header(p,"搜索",this::showMe);
    EditText q=field("搜索有联系的人或自己的记录");q.setSingleLine(true);p.addView(q,new LinearLayout.LayoutParams(-1,dp(52)));p.addView(gap(10));Button go=pill("搜索",true);p.addView(go,new LinearLayout.LayoutParams(-1,dp(46)));p.addView(gap(14));ScrollView sv=new ScrollView(this);LinearLayout results=col();sv.addView(results);p.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
    go.setOnClickListener(v->{String s=q.getText().toString().trim();if(s.isEmpty())return;get("/v1/search?q="+Uri.encode(s),token,j->runOnUiThread(()->renderSearch(results,j)),e->runOnUiThread(()->emptyState(results,"搜索失败")));});
  }
  void renderSearch(LinearLayout r,JSONObject j){r.removeAllViews();JSONArray us=j.optJSONArray("users"),ps=j.optJSONArray("posts");if((us==null||us.length()==0)&&(ps==null||ps.length()==0)){emptyState(r,"没有找到");return;}if(us!=null&&us.length()>0){r.addView(text("有联系的人",13,muted));r.addView(gap(8));for(int i=0;i<us.length();i++){JSONObject u=us.optJSONObject(i);String n=u.optString("nickname","一个人");LinearLayout x=row();x.setPadding(dp(12),dp(10),dp(12),dp(10));x.setBackground(round(card,15));x.addView(avatar(n),new LinearLayout.LayoutParams(dp(44),dp(44)));TextView t=text(n,16,ink);t.setPadding(dp(12),0,0,0);x.addView(t);r.addView(x);r.addView(gap(7));}}if(ps!=null&&ps.length()>0){r.addView(gap(12));r.addView(text("我的记录",13,muted));r.addView(gap(8));for(int i=0;i<ps.length();i++){JSONObject p=ps.optJSONObject(i);TextView t=text(p.optString("body","[媒体]"),15,ink);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setBackground(round(card,15));r.addView(t);r.addView(gap(7));}}}

  void header(LinearLayout p,String title,Runnable backAction){LinearLayout h=row();TextView b=text("‹",36,ink);b.setGravity(Gravity.CENTER);b.setOnClickListener(v->backAction.run());h.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView t=text(title,18,ink);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER);h.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));Space s=new Space(this);h.addView(s,new LinearLayout.LayoutParams(dp(48),1));p.addView(h);}
  void emptyState(LinearLayout p,String s){p.removeAllViews();TextView t=text(s,16,muted);t.setGravity(Gravity.CENTER);t.setPadding(dp(20),dp(72),dp(20),dp(20));p.addView(t,new LinearLayout.LayoutParams(-1,-2));}
  void loadImage(String url,ImageView v){if(url==null||url.isEmpty())return;io.execute(()->{try{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);c.setConnectTimeout(10000);c.setReadTimeout(15000);Bitmap b=BitmapFactory.decodeStream(c.getInputStream());if(b!=null)runOnUiThread(()->v.setImageBitmap(b));}catch(Exception ignored){}});}
  void loadMeSilently(){get("/v1/me",token,j->{JSONObject u=j.optJSONObject("user");if(u!=null){uid=u.optString("id",uid);getPreferences(MODE_PRIVATE).edit().putString("uid",uid).apply();}},e->{ });}
  byte[] read(InputStream in)throws Exception{if(in==null)return new byte[0];try(InputStream x=in;ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] buf=new byte[8192];for(int n;(n=x.read(buf))>0;)o.write(buf,0,n);return o.toByteArray();}}
  JSONObject obj(Object... kv){JSONObject o=new JSONObject();for(int i=0;i+1<kv.length;i+=2)try{o.put(String.valueOf(kv[i]),kv[i+1]);}catch(Exception ignored){}return o;}
  JSONArray arr(Object... xs){JSONArray a=new JSONArray();for(Object x:xs)a.put(x);return a;}
  String errorText(Exception e){String s=e==null?"请求失败":e.getMessage();if(s==null)return "请求失败";if(s.contains("sms_provider_unconfigured"))return "验证码服务暂不可用";if(s.contains("invalid_code"))return "验证码不正确或已失效";if(s.contains("unauthorized"))return "登录已失效，请重新登录";return s;}
  void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}

  interface OK{void run(JSONObject j);} interface ERR{void run(Exception e);}
  void get(String path,String tok,OK ok,ERR err){io.execute(()->{try{ok.run(sync("GET",path,null,tok));}catch(Exception e){err.run(e);}});}
  void post(String path,JSONObject data,String tok,OK ok,ERR err){io.execute(()->{try{ok.run(sync("POST",path,data,tok));}catch(Exception e){err.run(e);}});}
  JSONObject sync(String method,String path,JSONObject data,String tok)throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(api+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/json");if(tok!=null&&!tok.isEmpty())c.setRequestProperty("Authorization","Bearer "+tok);
    if(data!=null){c.setDoOutput(true);try(OutputStream o=c.getOutputStream()){o.write(data.toString().getBytes(StandardCharsets.UTF_8));}}
    int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();String s=in==null?"":new String(read(in),StandardCharsets.UTF_8);JSONObject j=s.isEmpty()?new JSONObject():new JSONObject(s);if(code>=400)throw new Exception(j.optString("error","HTTP "+code));return j;
  }
}
