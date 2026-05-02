package aman.lyricify;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

public class BlurHelper {
    
    public static Bitmap blur(Context context, Bitmap image, float radius) {
        if (radius < 1 || radius > 25) {
            radius = 25;
        }
        
        try {
            int width = image.getWidth();
            int height = image.getHeight();
            float scale = 0.4f; 
            
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(
                image, 
                (int)(width * scale), 
                (int)(height * scale), 
                false
            );
            
            Bitmap outputBitmap = Bitmap.createBitmap(scaledBitmap);
            
            RenderScript rs = RenderScript.create(context);
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            
            Allocation tmpIn = Allocation.createFromBitmap(rs, scaledBitmap);
            Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
            
            blurScript.setRadius(radius);
            blurScript.setInput(tmpIn);
            blurScript.forEach(tmpOut);
            tmpOut.copyTo(outputBitmap);
            
            rs.destroy();
            
            return outputBitmap;
        } catch (Exception e) {
            return image;
        }
    }
}
