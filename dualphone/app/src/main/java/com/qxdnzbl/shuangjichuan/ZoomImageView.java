package com.qxdnzbl.shuangjichuan;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.widget.ImageView;

final class ZoomImageView extends ImageView {
  private final Matrix matrix=new Matrix();
  private float zoom=1f,base=1f;
  private final ScaleGestureDetector pinch;
  private final GestureDetector gestures;
  ZoomImageView(Context c){
    super(c);setScaleType(ScaleType.MATRIX);setContentDescription("图片预览");
    pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
      @Override public boolean onScale(ScaleGestureDetector d){scale(d.getScaleFactor(),d.getFocusX(),d.getFocusY());return true;}
    });
    gestures=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
      @Override public boolean onDown(MotionEvent e){return true;}
      @Override public boolean onDoubleTap(MotionEvent e){scale(zoom>1.2f?1f/zoom:2.5f,e.getX(),e.getY());return true;}
      @Override public boolean onScroll(MotionEvent a,MotionEvent b,float dx,float dy){if(!pinch.isInProgress()){matrix.postTranslate(-dx,-dy);constrain();}return true;}
      @Override public boolean onSingleTapConfirmed(MotionEvent e){performClick();return true;}
      @Override public void onLongPress(MotionEvent e){performLongClick();}
    });
  }
  @Override public void setImageBitmap(Bitmap b){super.setImageBitmap(b);post(this::fit);}
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);fit();}
  private void fit(){
    if(getDrawable()==null||getWidth()==0||getHeight()==0)return;
    float w=getDrawable().getIntrinsicWidth(),h=getDrawable().getIntrinsicHeight();
    base=Math.min(getWidth()/w,getHeight()/h);zoom=1;matrix.setScale(base,base);
    matrix.postTranslate((getWidth()-w*base)/2f,(getHeight()-h*base)/2f);setImageMatrix(matrix);
  }
  private void scale(float factor,float x,float y){
    float next=Math.max(1f,Math.min(5f,zoom*factor));matrix.postScale(next/zoom,next/zoom,x,y);zoom=next;constrain();
  }
  private void constrain(){
    if(getDrawable()==null)return;
    RectF rect=new RectF(0,0,getDrawable().getIntrinsicWidth(),getDrawable().getIntrinsicHeight());matrix.mapRect(rect);
    float dx=rect.width()<getWidth()?(getWidth()-rect.width())/2f-rect.left:rect.left>0?-rect.left:rect.right<getWidth()?getWidth()-rect.right:0;
    float dy=rect.height()<getHeight()?(getHeight()-rect.height())/2f-rect.top:rect.top>0?-rect.top:rect.bottom<getHeight()?getHeight()-rect.bottom:0;
    matrix.postTranslate(dx,dy);setImageMatrix(matrix);
  }
  @Override public boolean onTouchEvent(MotionEvent e){pinch.onTouchEvent(e);gestures.onTouchEvent(e);return true;}
  @Override public boolean performClick(){super.performClick();return true;}
}
