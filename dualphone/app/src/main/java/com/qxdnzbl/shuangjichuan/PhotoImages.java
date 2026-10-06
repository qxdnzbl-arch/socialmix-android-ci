package com.qxdnzbl.shuangjichuan;

import android.graphics.*;
import android.media.ExifInterface;
import java.io.File;
import java.util.Locale;

final class PhotoImages {
  static boolean isPhoto(String name){
    return name!=null&&name.toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|gif|webp|bmp|heic|heif)$");
  }
  static Bitmap decode(String path,int limit){
    if(path==null||!new File(path).isFile())return null;
    try{
      BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
      BitmapFactory.decodeFile(path,bounds);
      if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
      int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>limit)sample*=2;
      BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=sample;
      Bitmap bitmap=BitmapFactory.decodeFile(path,opts);if(bitmap==null)return null;
      Matrix transform=new Matrix();
      try{
        int o=new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        switch(o){
          case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:transform.setScale(-1,1);break;
          case ExifInterface.ORIENTATION_ROTATE_180:transform.setRotate(180);break;
          case ExifInterface.ORIENTATION_FLIP_VERTICAL:transform.setScale(1,-1);break;
          case ExifInterface.ORIENTATION_TRANSPOSE:transform.setRotate(90);transform.postScale(-1,1);break;
          case ExifInterface.ORIENTATION_ROTATE_90:transform.setRotate(90);break;
          case ExifInterface.ORIENTATION_TRANSVERSE:transform.setRotate(270);transform.postScale(-1,1);break;
          case ExifInterface.ORIENTATION_ROTATE_270:transform.setRotate(270);break;
        }
      }catch(Exception ignored){}
      if(!transform.isIdentity()){
        Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),transform,true);
        if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;
      }
      return bitmap;
    }catch(Exception|OutOfMemoryError ignored){return null;}
  }
}
