package com.qxdnzbl.shuangjichuan;

import android.content.*;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

final class PairCrypto {
  private static final String ALPHABET="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final String PREFIX="SJC58:";
  private static final byte[] AAD="shuangjichuan:e2e:v58".getBytes(StandardCharsets.UTF_8);
  private final SharedPreferences preferences;
  private final SecureRandom secureRandom=new SecureRandom();

  PairCrypto(Context context){preferences=context.getSharedPreferences("dual",Context.MODE_PRIVATE);}

  synchronized String self(){
    String id=preferences.getString("device","");
    if(id.isEmpty()){
      id=UUID.randomUUID().toString();
      preferences.edit().putString("device",id).commit();
    }
    return id;
  }
  synchronized String code(){return preferences.getString("pair_secret_v58","");}
  synchronized String peer(){return preferences.getString("pair_peer_v58","");}
  boolean configured(){return !code().isEmpty();}
  boolean paired(){return configured()&&!peer().isEmpty();}

  synchronized String createCode(){
    StringBuilder result=new StringBuilder(20);
    for(int i=0;i<20;i++)result.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
    setCodeInternal(result.toString());
    return format(result.toString());
  }
  synchronized boolean joinCode(String raw){
    String normalized=normalize(raw);
    if(normalized.length()!=20)return false;
    for(int i=0;i<normalized.length();i++)if(ALPHABET.indexOf(normalized.charAt(i))<0)return false;
    setCodeInternal(normalized);
    return true;
  }
  private void setCodeInternal(String normalized){
    if(!preferences.edit().putString("pair_secret_v58",normalized)
      .remove("pair_peer_v58").commit())throw new IllegalStateException("Cannot persist pairing");
  }
  static String normalize(String code){return code==null?"":code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");}
  static String format(String normalized){
    String s=normalize(normalized);StringBuilder b=new StringBuilder();
    for(int i=0;i<s.length();i++){if(i>0&&i%5==0)b.append('-');b.append(s.charAt(i));}
    return b.toString();
  }

  synchronized boolean acceptPeer(String candidate){
    if(candidate==null||!candidate.matches("[0-9a-fA-F-]{36}")||candidate.equals(self())||!configured())return false;
    String existing=peer();
    if(existing.isEmpty()){
      if(!preferences.edit().putString("pair_peer_v58",candidate).commit())return false;
      return true;
    }
    return existing.equals(candidate);
  }
  boolean fromPeer(String sender){return paired()&&sender!=null&&sender.equals(peer());}

  String room(){
    try{
      byte[] digest=MessageDigest.getInstance("SHA-256")
        .digest(relayToken().getBytes(StandardCharsets.UTF_8));
      return hex(digest).substring(0,32);
    }catch(Exception e){throw new IllegalStateException(e);}
  }
  String relayToken(){return hex(derive("relay-auth"));}
  private byte[] derive(String purpose){
    String secret=code();
    if(secret.isEmpty())throw new IllegalStateException("Pairing required");
    try{
      MessageDigest digest=MessageDigest.getInstance("SHA-256");
      digest.update(("SJC-V58-"+purpose+"|").getBytes(StandardCharsets.UTF_8));
      return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
    }catch(Exception e){throw new IllegalStateException(e);}
  }
  private SecretKeySpec key(){return new SecretKeySpec(derive("aes-gcm"),"AES");}
  private static String hex(byte[] bytes){
    StringBuilder b=new StringBuilder();
    for(byte a:bytes)b.append(String.format(Locale.US,"%02x",a&255));
    return b.toString();
  }
  private Cipher cipher(int mode,byte[] nonce)throws Exception{
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
    c.init(mode,key(),new GCMParameterSpec(128,nonce));
    c.updateAAD(AAD);
    return c;
  }
  String encrypt(JSONObject payload)throws Exception{
    byte[] nonce=new byte[12];secureRandom.nextBytes(nonce);
    byte[] bytes=cipher(Cipher.ENCRYPT_MODE,nonce).doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
    byte[] raw=new byte[12+bytes.length];
    System.arraycopy(nonce,0,raw,0,12);System.arraycopy(bytes,0,raw,12,bytes.length);
    return PREFIX+Base64.encodeToString(raw,Base64.NO_WRAP);
  }
  JSONObject decrypt(String frame)throws Exception{
    if(frame==null||!frame.startsWith(PREFIX))throw new SecurityException("Not a paired encrypted message");
    byte[] raw=Base64.decode(frame.substring(PREFIX.length()),Base64.DEFAULT);
    if(raw.length<29)throw new SecurityException("Invalid encrypted frame");
    byte[] nonce=Arrays.copyOfRange(raw,0,12);
    byte[] clear=cipher(Cipher.DECRYPT_MODE,nonce).doFinal(raw,12,raw.length-12);
    if(clear.length>1024*1024)throw new SecurityException("Oversized frame");
    return new JSONObject(new String(clear,StandardCharsets.UTF_8));
  }

  File encryptFile(Context c,File input,JSONObject metadata)throws Exception{
    if(!input.isFile()||input.length()>500L*1024*1024)throw new IOException("Invalid source file");
    File folder=new File(c.getCacheDir(),"outbound-secure");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("cache dir");
    File tmp=File.createTempFile("secure-", ".sjc",folder);
    byte[] nonce=new byte[12];secureRandom.nextBytes(nonce);
    try(OutputStream raw=new FileOutputStream(tmp)){
      raw.write(nonce);
      try(CipherOutputStream out=new CipherOutputStream(raw,cipher(Cipher.ENCRYPT_MODE,nonce));
          DataOutputStream data=new DataOutputStream(out);
          InputStream in=new FileInputStream(input)){
        byte[] meta=metadata.toString().getBytes(StandardCharsets.UTF_8);
        if(meta.length>8192)throw new IOException("metadata too long");
        data.writeInt(meta.length);data.write(meta);copy(in,data);
      }
      return tmp;
    }catch(Exception ex){tmp.delete();throw ex;}
  }

  static final class ReceivedFile {
    final JSONObject metadata;
    final File file;
    ReceivedFile(JSONObject meta,File file){this.metadata=meta;this.file=file;}
  }
  ReceivedFile decryptFile(Context context,InputStream encrypted)throws Exception{
    File folder=new File(context.getFilesDir(),"incoming");
    if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("incoming folder");
    File tmp=File.createTempFile("secure-in-", ".receiving",folder);
    try{
      byte[] nonce=new byte[12];
      DataInputStream raw=new DataInputStream(encrypted);
      raw.readFully(nonce);
      JSONObject metadata;
      long copied=0;
      MessageDigest digest=MessageDigest.getInstance("SHA-256");
      try(CipherInputStream stream=new CipherInputStream(raw,cipher(Cipher.DECRYPT_MODE,nonce));
          DataInputStream data=new DataInputStream(stream);
          OutputStream out=new FileOutputStream(tmp)){
        int length=data.readInt();
        if(length<=0||length>8192)throw new SecurityException("Invalid metadata length");
        byte[] meta=new byte[length];data.readFully(meta);
        metadata=new JSONObject(new String(meta,StandardCharsets.UTF_8));
        byte[] buffer=new byte[65536];int n;
        while((n=data.read(buffer))!=-1){
          copied+=n;
          if(copied>500L*1024*1024)throw new IOException("File exceeds limit");
          digest.update(buffer,0,n);out.write(buffer,0,n);
        }
      }
      long expected=metadata.optLong("size",-1);
      if(expected<0||copied!=expected)throw new SecurityException("Truncated file");
      String checksum=metadata.optString("sha","");
      if(!checksum.equals(hex(digest.digest())))throw new SecurityException("Checksum mismatch");
      return new ReceivedFile(metadata,tmp);
    }catch(Exception e){tmp.delete();throw e;}
  }
  static String shaFile(File f)throws Exception{
    MessageDigest d=MessageDigest.getInstance("SHA-256");
    try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
    return hex(d.digest());
  }
  static void copy(InputStream in,OutputStream out)throws IOException{
    byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
  }
}
