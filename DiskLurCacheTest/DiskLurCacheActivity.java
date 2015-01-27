package com.example.commonuidemo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import com.example.common.Utils;
import com.example.common.network.HttpDownloader;
import com.example.common.network.IoUtils;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;


public class DiskLurCacheActivity extends Activity {

    private static String TAG = DiskLurCacheActivity.class.getSimpleName();
    private HttpDownloader httpDownloader;
    
    private ImageView cacheImageView;
    private TextView cacheTextView;
    
    private final String imageUrl = "http://img3.cache.netease.com/photo/0003/2014-12-12/AD9DN5E500AJ0003.jpg";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disk_lru_cache);
        init();
    }
    
    private void init() {
        if (httpDownloader == null) {
            httpDownloader = new HttpDownloader(getApplicationContext());
        }
        
        cacheImageView = (ImageView) findViewById(R.id.disk_lru_cache_imageview);
        cacheTextView = (TextView) findViewById(R.id.disk_lru_cache_text);
        
        
        new DownloadImageTask().execute(imageUrl);
    }
    
    
    private Bitmap getImageFromNetWork(final String imageUrl) {
        InputStream inputStream = null;
        Bitmap decodedBitmap = null;
        try {
            inputStream = httpDownloader.getStreamFromNetwork(imageUrl, null);
            decodedBitmap = BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                IoUtils.closeSilently(inputStream);
            }
        }
        
        return decodedBitmap;
    }
    
    private class DownloadImageTask extends AsyncTask<String, Integer, Bitmap> {

        @Override
        protected Bitmap doInBackground(String...params) {
            String imageUrl = params[0].toString();
            Bitmap imageBitmap = null;
            try {
                imageBitmap = getImageFromNetWork(imageUrl);
            } catch (Exception e) {
                
            }
            return imageBitmap;
        }
        
        protected void onPreExecute() {
            Log.d(TAG, "DownloadImageTask you can do something in onPreExecute, work on UI thread");
        }
        
        protected void onPostExecute(Bitmap result) {
            if (result == null) {
                return;
            }
            if (cacheImageView != null) {
                BitmapDrawable drawable = new BitmapDrawable(result);
                cacheImageView.setImageDrawable(drawable);
            }
        }

        protected void onProgressUpdate(Integer... values) {
        }
    }
}
