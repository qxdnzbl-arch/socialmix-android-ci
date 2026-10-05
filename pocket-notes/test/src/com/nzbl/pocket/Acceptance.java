package com.nzbl.pocket;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;

public class Acceptance extends Instrumentation {
    MainActivity a;
    File dir;
    final ArrayList<String> checks=new ArrayList<>();
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();
        try{
            getUiAutomation().executeShellCommand("settings put secure show_ime_with_hard_keyboard 1").close();
            dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();
            Intent i=new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(),"com.nzbl.pocket.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a=(MainActivity)startActivitySync(i);idle();
            check(a.store.ready,"cold launch and data readiness");check(a.store.notes.size()==0,"no fake sample records shipped");check(a.store.categories.isEmpty(),"fresh install has no preset categories");check(find(a.root,"理发")==null&&find(a.root,"提示词")==null&&find(a.root,"日常")==null,"no example category shortcuts on home");shot("01-empty");
            tap("记一条");check(Store.UNFILED.equals(a.editing.category),"new record has no mandatory category");check("写下想留住的内容…".contentEquals(a.bodyInput.getHint()),"generic content hint");main(()->{a.titleInput.setText("下次理发 · 先沟通");a.bodyInput.setText("先按参考图片沟通，确认长度再剪。\n请保留我标出的地方。\n"+repeat("这是较长的沟通记录，逐条确认。\n",16));});
            tapDescription("选择记录分类");shot("01-category-picker");dump("initial-category-picker");tapDialog("＋ 新建分类");setDialogEdit("灵感");tapDialog("保存");check("edit".equals(a.screen)&&a.bodyInput.getText().toString().startsWith("先按参考图片"),"create category inside editor preserves input");final String firstCategory=a.store.categories.get(0).id;check(firstCategory.equals(a.editing.category),"created category selected immediately");
            main(()->{a.bodyInput.requestFocus();((InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(a.bodyInput,InputMethodManager.SHOW_IMPLICIT);});Thread.sleep(1800);idle();
            final Rect frame=new Rect();final int[] heights=new int[2];main(()->{a.root.getWindowVisibleDisplayFrame(frame);heights[0]=a.root.getHeight();heights[1]=a.getResources().getDisplayMetrics().heightPixels;});
            check(heights[0]<heights[1]-180,"real Android IME shrinks editor viewport");
            assertInFrame(find(a.root,"取消"),frame,"cancel visible above keyboard");assertInFrame(find(a.root,"保存"),frame,"save visible above keyboard");
            final int[] loc=new int[2];main(()->a.root.getLocationOnScreen(loc));check(loc[1]<150,"editor pinned at viewport top");shot("02-keyboard");
            tap("保存");idle();check(a.store.notes.size()==1,"save hair note");Store.Note hair=a.store.notes.get(0);String hairBody=hair.body;
            tap("下次理发 · 先沟通");check("detail".equals(a.screen),"open saved note");shot("03-hair-detail");tap("大字展示");check(a.showing,"large-text view available in any category");check((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0,"large-text view keeps screen awake");shot("04-large-text-mode");main(()->a.onBackPressed());check(!a.showing,"back exits large-text view");tap("复制");final String[] copied=new String[1];main(()->{android.content.ClipboardManager c=(android.content.ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE);copied[0]=c.getPrimaryClip().getItemAt(0).getText().toString();});check(hairBody.equals(copied[0]),"copy full text without truncation");main(()->a.onBackPressed());
            main(()->{a.filter="all";a.home();a.newNote(null);});main(()->{a.titleInput.setText("代码修改提示词");a.bodyInput.setText("只改指定问题。保留现有布局、文案和数据字段。\n完成后实际检查点击、返回、输入和滚动。\n保留换行与中文。" );});tapDescription("选择记录分类");tapDialog("＋ 新建分类");setDialogEdit("资料");tapDialog("保存");final String secondCategory=a.store.categories.get(1).id;tap("保存");Store.Note prompt=a.store.notes.get(1);check(secondCategory.equals(prompt.category),"user-created category saved");
            main(()->{a.filter="all";a.home();a.searchInput.setText("数据字段");});check(find(a.root,"代码修改提示词")!=null&&find(a.root,"下次理发 · 先沟通")==null,"search uses body and category filters");shot("05-search");main(()->{a.searchInput.setText("");a.hideKeyboard();});
            main(()->{a.filter=secondCategory;a.home();});check(find(a.root,"下次理发 · 先沟通")==null,"category isolation");
            main(()->a.detail(prompt,false));tap("编辑");main(()->a.bodyInput.setText("不应覆盖原记录的临时编辑"));tap("取消");tapDialog("放弃这次编辑");check(prompt.body.startsWith("只改指定问题"),"cancel edit leaves original unchanged");
            main(()->a.newNote(Store.UNFILED));main(()->{a.titleInput.setText("待完成的记录");a.bodyInput.setText("这段草稿必须在返回和重开后恢复。");});tap("取消");tapDialog("保留草稿");check(a.store.draft!=null,"explicit draft retention");Store reread=new Store(getTargetContext());check(reread.draft!=null&&reread.draft.body.contains("重开"),"draft persisted on disk");tap("继续上次没写完的记录");check(a.bodyInput.getText().toString().contains("重开"),"resume draft");tap("保存");check(a.store.draft==null&&a.store.notes.size()==3,"saved draft removed from resume card");check(Store.UNFILED.equals(a.store.notes.get(2).category),"uncategorized record saves and reloads");
            main(()->{a.filter="all";a.home();});tap("下次理发 · 先沟通");tapDescription("记录操作");tapDialog("设为常用");check(hair.pinned,"pin record");main(()->{a.home();a.filter="pinned";a.home();});check(find(a.root,"下次理发 · 先沟通")!=null&&find(a.root,"代码修改提示词")==null,"pinned filter");
            // Real system picker import: a reference fixture is placed in Downloads by the runner.
            main(()->{a.editor(hair.copy());a.photoPicker();});Thread.sleep(1200);dump("picker");
            AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();check(root!=null&&!"com.nzbl.pocket".contentEquals(root.getPackageName()),"system image picker opens");
            selectFixture();Thread.sleep(1600);idle();check(a.editing.images.size()==1,"image imported through document picker");String image=a.editing.images.get(0);check(new File(a.store.photos,image).length()>0,"original image bytes copied privately");shot("06-image-editor");tap("保存");final Store.Note savedHair=a.store.find(hair.id);
            main(()->a.detail(savedHair,false));tapDescription("参考图片 1");check("photo".equals(a.screen),"full image viewer opens");shot("07-image-viewer");main(()->a.onBackPressed());check("detail".equals(a.screen),"system back returns from image viewer");
            main(()->a.noteMore(savedHair));tapDialog("删除记录");tapDialog("删除");check(savedHair.deleted,"delete moves record to trash");main(()->a.trash());tap("恢复");check(!savedHair.deleted,"restore deleted note");
            // Same persisted source is exercised for backup; verify the picker separately.
            File zip=new File(dir,"backup-test.zip");a.store.exportZip(new FileOutputStream(zip));long zipSize=zip.length();check(zipSize>0,"export complete zip with images");int before=a.store.notes.size();int duplicates=a.store.importZip(new FileInputStream(zip));check(duplicates==0&&a.store.notes.size()==before,"reimport does not duplicate records");
            byte[] content=Store.read(new FileInputStream(a.store.file.getBaseFile()),16*1024*1024);main(()->{a.store.notes.clear();a.store.draft=null;});int restored=a.store.importZip(new FileInputStream(zip));check(restored==before,"backup restores all records");Store.Note restoredHair=a.store.find(savedHair.id);check(restoredHair!=null&&restoredHair.images.size()==1,"backup restores attached image");check(new File(a.store.photos,restoredHair.images.get(0)).isFile(),"restored image resolves");
            int count=a.store.notes.size();try{a.store.importZip(new ByteArrayInputStream("broken backup".getBytes("UTF-8")));throw new AssertionError("invalid backup accepted");}catch(IOException expected){}check(a.store.notes.size()==count,"invalid import does not erase original data");
            main(()->{a.home();a.exportPicker();});Thread.sleep(800);dump("backup-picker");check(!"com.nzbl.pocket".contentEquals(getUiAutomation().getRootInActiveWindow().getPackageName()),"backup document save picker opens");back();idle();
            main(()->a.categoriesDialog());tapDialog("＋ 新建分类");setDialogEdit("就医");tapDialog("保存");check(a.store.categories.size()==3,"custom category creation");main(()->a.categoriesDialog());tapDialog("就医");tapDialog("重命名");setDialogEdit("生活资料");tapDialog("保存");check("生活资料".equals(a.store.categories.get(2).name),"category rename");
            main(()->a.newNote(a.store.categories.get(2).id));main(()->{a.titleInput.setText("图片也可以独立保存");a.bodyInput.setText("");a.editing.images.add(restoredHair.images.get(0));a.renderEditorPhotos();});tap("保存");check(a.store.notes.size()==4,"image-only record saves");
            main(()->{a.categoriesDialog();});tapDialog("生活资料");tapDialog("删除分类");tapDialog("删除分类");check(a.store.categories.size()==2&&Store.UNFILED.equals(a.store.notes.get(3).category),"category deletion moves content without loss");
            main(()->{a.filter="all";a.home();});shot("08-home-populated");Store restart=new Store(getTargetContext());check(restart.ready&&restart.notes.size()==4,"restart reload retains all notes");check(restart.find(prompt.id).body.startsWith("只改指定问题"),"Chinese and newline persistence");
            main(()->a.detail(a.store.find(savedHair.id),false));main(()->((ScrollView)((ViewGroup)a.root).getChildAt(1)).smoothScrollTo(0,100000));Thread.sleep(300);check(find(a.root,"复制")!=null,"long detail scroll retains bottom actions");
            main(()->{a.home();a.searchInput.setText("完全没有的文字");});check(find(a.root,"没有找到这条记录")!=null,"empty search is recoverable");tap("清空搜索");check(a.query.isEmpty(),"clear search works");
            main(()->a.newNote(firstCategory));main(()->a.bodyInput.setText("删除分类后也要留住的草稿"));tap("取消");tapDialog("保留草稿");
            main(()->a.categoriesDialog());tapDialog("灵感");tapDialog("删除分类");tapDialog("删除分类");check(Store.UNFILED.equals(a.store.find(savedHair.id).category)&&a.store.find(savedHair.id).images.size()==1,"deleting occupied category preserves photo record");check(a.store.draft!=null&&Store.UNFILED.equals(a.store.draft.category),"deleting category preserves draft");
            main(()->a.categoriesDialog());tapDialog("资料");tapDialog("删除分类");tapDialog("删除分类");check(a.store.categories.isEmpty()&&a.store.notes.size()==4,"last category can be deleted without losing records");
            Store emptyCategories=new Store(getTargetContext());check(emptyCategories.ready&&emptyCategories.categories.isEmpty()&&emptyCategories.notes.size()==4,"restart with no categories is valid");
            File noCats=new File(dir,"no-categories-backup.zip");a.store.exportZip(new FileOutputStream(noCats));check(a.store.importZip(new FileInputStream(noCats))==1,"empty-category backup imports saved records and draft");check(a.store.categories.isEmpty(),"backup import never recreates presets");
            tap("继续上次没写完的记录");tapDescription("选择记录分类");tapDialog("＋ 新建分类");setDialogEdit("理发");tapDialog("保存");check("edit".equals(a.screen)&&"理发".equals(a.store.categoryName(a.editing.category)),"example name works only when user creates it");tap("取消");tapDialog("放弃这次编辑");main(()->a.categoriesDialog());tapDialog("理发");tapDialog("删除分类");tapDialog("删除分类");check(a.store.categories.isEmpty(),"user-created example name is freely deletable");main(()->{a.filter="all";a.home();});shot("09-no-fixed-categories");
            // Save evidence from an actual second small-screen density setting in the runner as well.
            File report=new File(dir,"acceptance.json");JSONObject j=new JSONObject().put("passed",true).put("checks",new JSONArray(checks));try(FileOutputStream f=new FileOutputStream(report)){f.write(j.toString(2).getBytes("UTF-8"));}
            result.putString("stream","\nPASS: "+checks.size()+" real Android acceptance checks\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable e){try{shot("failure");File f=new File(dir,"failure.txt");try(PrintWriter out=new PrintWriter(f)){e.printStackTrace(out);}}catch(Exception ignored){}result.putString("stream","\nFAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    void main(Runnable action){runOnMainSync(action);waitForIdleSync();}
    void idle()throws Exception{waitForIdleSync();Thread.sleep(200);}
    void check(boolean value,String name){if(!value)throw new AssertionError(name);checks.add(name);Bundle b=new Bundle();b.putString("stream","PASS "+name+"\n");sendStatus(0,b);}
    String repeat(String s,int n){StringBuilder b=new StringBuilder();while(n-->0)b.append(s);return b.toString();}
    View find(View view,String value){if(view instanceof TextView&&value.equals(((TextView)view).getText().toString()))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),value);if(found!=null)return found;}}return null;}
    View desc(View view,String value){if(value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=desc(g.getChildAt(i),value);if(found!=null)return found;}}return null;}
    void tap(String label)throws Exception{final View[] view=new View[1];main(()->view[0]=find(a.root,label));if(view[0]==null&&"home".equals(a.screen)){for(int i=0;i<15&&view[0]==null;i++){main(()->a.homeList.smoothScrollBy(a.dp(300),0));idle();main(()->view[0]=find(a.root,label));}}if(view[0]==null)throw new AssertionError("Button missing: "+label);touch(view[0]);}
    void tapDescription(String label)throws Exception{final View[] view=new View[1];main(()->view[0]=desc(a.root,label));if(view[0]==null)throw new AssertionError("Action missing: "+label);touch(view[0]);}
    void touch(View v)throws Exception{final int[] p=new int[2];final int[] size=new int[2];main(()->{v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);});idle();main(()->{v.getLocationOnScreen(p);size[0]=v.getWidth();size[1]=v.getHeight();});float x=p[0]+size[0]/2f,y=p[1]+size[1]/2f;long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,0,x,y,0);MotionEvent up=MotionEvent.obtain(time,time+100,1,x,y,0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);down.recycle();up.recycle();idle();}
    void tapDialog(String label)throws Exception{Thread.sleep(180);AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)throw new AssertionError("No active dialog");List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(label);for(AccessibilityNodeInfo n:nodes){if(label.contentEquals(n.getText()==null?"":n.getText())||label.contentEquals(n.getContentDescription()==null?"":n.getContentDescription())){AccessibilityNodeInfo p=n;while(p!=null&&!p.isClickable())p=p.getParent();if(p!=null&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK)){idle();return;}}}throw new AssertionError("Dialog action missing: "+label);}
    void setDialogEdit(String value)throws Exception{AccessibilityNodeInfo n=findClass(getUiAutomation().getRootInActiveWindow(),"android.widget.EditText");if(n==null)throw new AssertionError("Dialog editor missing");Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);check(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b),"dialog text input");}
    AccessibilityNodeInfo findClass(AccessibilityNodeInfo n,String klass){if(n==null)return null;if(klass.contentEquals(n.getClassName()))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo found=findClass(n.getChild(i),klass);if(found!=null)return found;}return null;}
    void assertInFrame(View v,Rect frame,String message){final Rect box=new Rect();main(()->v.getGlobalVisibleRect(box));check(box.width()>0&&box.top>=frame.top&&box.bottom<=frame.bottom,message);}
    void shot(String name)throws Exception{Thread.sleep(180);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new IOException("Screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    void back(){long time=SystemClock.uptimeMillis();getUiAutomation().injectInputEvent(new KeyEvent(time,time,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0),true);getUiAutomation().injectInputEvent(new KeyEvent(time,time+50,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0),true);}
    void selectFixture()throws Exception{
        for(int attempt=0;attempt<8;attempt++){
            AccessibilityNodeInfo active=getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo node=findDocument(active,"reference-fixture.png");
            if(node!=null){
                Rect rect=new Rect();node.getBoundsInScreen(rect);
                Bundle info=new Bundle();info.putString("stream","Picker observed file bounds "+rect+" description="+node.getContentDescription()+"\n");sendStatus(0,info);
                if(rect.width()<=0||rect.height()<=0)throw new AssertionError("Empty picker file bounds");
                android.os.ParcelFileDescriptor command=getUiAutomation().executeShellCommand("input tap "+rect.centerX()+" "+rect.centerY());
                try(InputStream output=new android.os.ParcelFileDescriptor.AutoCloseInputStream(command)){while(output.read()!=-1){}}
                Thread.sleep(800);active=getUiAutomation().getRootInActiveWindow();
                if(active!=null&&"com.nzbl.pocket".contentEquals(active.getPackageName())){dump("picker-after-selection");return;}
                if(active!=null){
                    for(String label:new String[]{"Open","Select","SELECT","OPEN"}){
                        List<AccessibilityNodeInfo> actions=active.findAccessibilityNodeInfosByText(label);
                        for(AccessibilityNodeInfo action:actions)if(action.isClickable())action.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                }
                Thread.sleep(300);
                continue;
            }
            List<AccessibilityNodeInfo> roots=active.findAccessibilityNodeInfosByText("Show roots");
            if(!roots.isEmpty())roots.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            List<AccessibilityNodeInfo> downloads=active.findAccessibilityNodeInfosByText("Downloads");
            if(!downloads.isEmpty())tapDialog("Downloads");Thread.sleep(300);
        }
        dump("picker-failed");throw new AssertionError("Reference image not selected in real picker");
    }
    AccessibilityNodeInfo findDocument(AccessibilityNodeInfo node,String name){if(node==null)return null;String desc=node.getContentDescription()==null?"":node.getContentDescription().toString();String text=node.getText()==null?"":node.getText().toString();if(desc.startsWith(name)||text.equals(name))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findDocument(node.getChild(i),name);if(found!=null)return found;}return null;}
    void dump(String name)throws Exception{AccessibilityNodeInfo n=getUiAutomation().getRootInActiveWindow();try(PrintWriter out=new PrintWriter(new File(dir,name+".txt"))){walk(n,out,0);}}
    void walk(AccessibilityNodeInfo n,PrintWriter out,int depth){if(n==null)return;Rect box=new Rect();n.getBoundsInScreen(box);out.println(depth+" "+n.getClassName()+" text="+n.getText()+" description="+n.getContentDescription()+" bounds="+box);for(int i=0;i<n.getChildCount();i++)walk(n.getChild(i),out,depth+1);}
}
