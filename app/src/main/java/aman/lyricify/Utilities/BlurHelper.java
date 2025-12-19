package aman.lyricify;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

/**
 * Helper class for blurring bitmaps
 */
public class BlurHelper {
    
    /**
     * Blur a bitmap using RenderScript
     * @param context Context
     * @param image Bitmap to blur
     * @param radius Blur radius (1-25)
     * @return Blurred bitmap
     */
    public static Bitmap blur(Context context, Bitmap image, float radius) {
        if (radius < 1 || radius > 25) {
            radius = 25;
        }
        
        try {
            // Create a scaled down version for better performance
            int width = image.getWidth();
            int height = image.getHeight();
            float scale = 0.4f; // Scale down to 40%
            
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
            // Fallback: return original image if blur fails
            return image;
        }
    }
}