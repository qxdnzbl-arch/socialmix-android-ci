package com.nzbl.pocket;

import android.content.*;
import android.database.*;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

public class UpdateProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    File updateFile(){return new File(getContext().getCacheDir(),"update.apk");}
    @Override public String getType(Uri uri){return "application/vnd.android.package-archive";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"/update.apk".equals(uri.getPath())||!"r".equals(mode))throw new FileNotFoundException();
        File file=updateFile();if(!file.isFile())throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sortOrder){
        if(!"/update.apk".equals(uri.getPath()))return null;
        File file=updateFile();String[] cols=projection!=null?projection:new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};MatrixCursor c=new MatrixCursor(cols);MatrixCursor.RowBuilder row=c.newRow();
        for(String col:cols){if(OpenableColumns.DISPLAY_NAME.equals(col))row.add("Suishoucun.apk");else if(OpenableColumns.SIZE.equals(col))row.add(file.length());else row.add(null);}return c;
    }
    @Override public int delete(Uri uri,String selection,String[] args){return 0;}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){return 0;}
    @Override public Uri insert(Uri uri,ContentValues values){return null;}
}