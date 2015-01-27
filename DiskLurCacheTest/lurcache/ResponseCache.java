package com.example.lurcache;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.http.impl.conn.Wire;

import com.example.lurcache.DiskLruCache.Editor;
import com.example.lurcache.DiskLruCache.Snapshot;

import android.Manifest.permission;
import android.R.integer;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;
import android.text.TextUtils;

/**
 * 网络获取的content进行disk cache
 * 缓存entry：title、image stream
 */
public final class ResponseCache {

    private static ResponseCache sInstance = null;
    
    private static int INDEX_COUNT = 2;
    private static int INDEX_CACHE_TIME = 0;
    private static int INDEX_CACHE_CONTENT = 1;
    static final long DEFAULT_MAX_CACHE_SIZE = 10 * 1024 * 1024;
    private Context context;
    
    private DiskLruCache cache = null;
    private ResponseCache(Context context) {
        this.context = context.getApplicationContext();
        newDefaultCache();
    }
    
    public static ResponseCache getInstance(Context context) {
       if (sInstance == null) {
           synchronized(ResponseCache.class){
               if (sInstance == null) {
                   sInstance = new ResponseCache(context);
               }
           }
       }
       return sInstance;
    }
    
    
    private void newDefaultCache() {
        final String cacheDir = getCacheDir(context);
        try {
            setupCache(context, cacheDir, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void setupCache(Context context, String cacheDir, long maxCacheSize) throws IOException {
        try {
            File cacheFileDir = new File(cacheDir);
            if (!cacheFileDir.exists()) {
                cacheFileDir.mkdirs();
            }
            
            if (maxCacheSize <= 0) {
                maxCacheSize = DEFAULT_MAX_CACHE_SIZE;
            }
            cache = DiskLruCache.open(cacheFileDir, getAppVersin(context), INDEX_COUNT, maxCacheSize);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public CacheWriter writeCache(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        
        final String urlKey = hashKeyForDisk(url);
        if (TextUtils.isEmpty(urlKey)) {
            return null;
        }
        
        if (cache == null) {
            return null;
        }
        
        OutputStream cacheTimeStream = null;
        try {
            cache.remove(urlKey);
            Editor editor = cache.edit(urlKey);
            cacheTimeStream = editor.newOutputStream(INDEX_CACHE_TIME);
            long cacheTime = System.currentTimeMillis();
            cacheTimeStream.write(ByteBuffer.allocate(8).putLong(cacheTime).array());
            cacheTimeStream.flush();
            return new CacheWriter(editor);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (cacheTimeStream != null) {
                Util.closeQuietly(cacheTimeStream);
            }
        }
        
    }
    
    public void endWriteCache(CacheWriter writer) {
        if (writer == null) {
            return;
        }
        
        Util.closeQuietly(writer);
    }
    
    public CacheReader readCache(String url, long ttl) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        
        final String urlKey = hashKeyForDisk(url);
        if (TextUtils.isEmpty(urlKey)) {
            return null;
        }
        
        if (cache == null) {
            return null;
        }
        
        try {
            CacheReader reader = new CacheReader(cache.get(urlKey));
            long cacheSaveTime = reader.getCacheTime();
            long currentTime = System.currentTimeMillis();
            if (currentTime > (cacheSaveTime + ttl) || currentTime < cacheSaveTime) {
                cache.remove(urlKey);
                return null;
            }
            return reader;
        } catch (Exception e) {
        }
        
        return null;
    }
    
    
    // write cache
    public static class CacheWriter implements Closeable {
        private OutputStream outputStream;
        private Throwable error;
        private Editor editor;
        
        public CacheWriter(Editor editor) {
            this.editor = editor;
            try {
                outputStream = editor.newOutputStream(INDEX_CACHE_CONTENT);
            } catch (IOException e) {
                error = e;
                e.printStackTrace();
            }
        }
        
        public void write(byte[] buf, int offset, int count) {
            if (error != null) {
                return;
            }
            
            try {
                outputStream.write(buf, offset, count);
            } catch (Exception e) {
                error = e;
                Util.closeQuietly(outputStream);
            }
        }
        
        public OutputStream getOutputStream() {
            return outputStream;
        }
        @Override
        public void close() throws IOException {
            if (outputStream != null) {
                try {
                    outputStream.flush();
                } catch (Exception e) {
                }
                Util.closeQuietly(outputStream);
            }
            
            if (editor != null) {
                if (error == null) {
                    editor.commit();
                } else {
                    editor.abort();
                }
            }
        }
    }
    
    
    public static class CacheReader implements Closeable {
        private InputStream inputStream;
        private Throwable error;
        private Snapshot snapshot;
        
        private long cacheTime;
        
        public long getCacheTime() {
            return cacheTime;
        }
        
        public CacheReader(Snapshot snapshot) {
            this.snapshot = snapshot;
            
            InputStream cacheTimeStream  = null;
            try {
                cacheTimeStream = snapshot.getInputStream(INDEX_CACHE_TIME);
                byte[] byteTime = new byte[8];
                cacheTimeStream.read(byteTime);
                cacheTime = ByteBuffer.wrap(byteTime).getLong();
            } catch (Exception e) {
                error = e;
                return;
            } finally {
                if (cacheTimeStream != null) {
                    Util.closeQuietly(cacheTimeStream);
                }
            }
            
            // read content
            try {
                inputStream = snapshot.getInputStream(INDEX_CACHE_CONTENT);
            } catch (Exception e) {
                error = e;
                e.printStackTrace();
            }
        }
        
        public int read(byte[] buf) throws Throwable {
            if (error != null) {
                throw error;
            }
            
            try {
                return inputStream.read(buf);
            } catch (Exception e) {
                error = e;
                Util.closeQuietly(inputStream);
                throw error;
            }
        }

        public InputStream readInputStream() {
            if (inputStream != null) {
                return inputStream;
            }
            
            return null;
        }
        @Override
        public void close() throws IOException {
            if (snapshot != null) {
                snapshot.close();
            }
        }
    }
    
    
    /**
     *  获取缓存
     * @param context
     * @return
     */
    private String getCacheDir(Context context) {
        if (context == null) {
            return "";
        }
        
        String cacheDir;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageDirectory())
            || !Environment.isExternalStorageRemovable()) {
                cacheDir = context.getExternalCacheDir().getPath();
            } else {
                cacheDir = context.getCacheDir().getPath();
            }
        
        return cacheDir;
    }
    
    private int getAppVersin(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        }catch(NameNotFoundException e) {
            e.printStackTrace();
        }
        
        return 1;
    }
    
    public String hashKeyForDisk(String key) {  
        String cacheKey;  
        try {  
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());  
            cacheKey = bytesToHexString(mDigest.digest());  
        } catch (NoSuchAlgorithmException e) {  
            cacheKey = String.valueOf(key.hashCode());  
        }  
        return cacheKey;  
    }  
      
    private String bytesToHexString(byte[] bytes) {  
        StringBuilder sb = new StringBuilder();  
        for (int i = 0; i < bytes.length; i++) {  
            String hex = Integer.toHexString(0xFF & bytes[i]);  
            if (hex.length() == 1) {  
                sb.append('0');  
            }  
            sb.append(hex);  
        }  
        return sb.toString();  
    }  
}
