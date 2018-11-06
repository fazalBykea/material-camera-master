package com.afollestad.materialcamera.util;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.SyncStateContract;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.afollestad.materialcamera.ICallback;
import com.afollestad.materialcamera.R;
import com.bumptech.glide.Glide;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.afollestad.materialcamera.util.Degrees.DEGREES_270;
import static com.afollestad.materialcamera.util.Degrees.DEGREES_90;

/**
 * Created by tomiurankar on 06/03/16.
 */
public class ImageUtil {
    private static final String IMAGE_FILTER_IMPLICIT_INTENT = "image/*";
    public static final String FILE_PROVIDER_TAG = ".fileprovider";
    public static final int REQUEST_IMAGE_FROM_GALLERY = 1011;

    /**
     * Saves byte[] array to disk
     *
     * @param input    byte array
     * @param output   path to output file
     * @param callback will always return in originating thread
     */
    public static void saveToDiskAsync(final byte[] input, final File output, final ICallback callback) {
        final Handler handler = new Handler();
        new Thread() {
            @Override
            public void run() {
                try {
                    FileOutputStream outputStream = new FileOutputStream(output);
                    outputStream.write(input);
                    outputStream.flush();
                    outputStream.close();

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.done(null);
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.done(e);
                        }
                    });
                }
            }
        }.start();
    }

    /**
     * Rotates the bitmap per their EXIF flag. This is a recursive function that will
     * be called again if the image needs to be downsized more.
     *
     * @param inputFile Expects an JPEG file if corrected orientation wants to be set.
     * @return rotated bitmap or null
     */
    @Nullable
    public static Bitmap getRotatedBitmap(String inputFile, int reqWidth, int reqHeight) {
        final int rotationInDegrees = getExifDegreesFromJpeg(inputFile);

        final BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(inputFile, opts);
        opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight, rotationInDegrees);
        opts.inJustDecodeBounds = false;

        final Bitmap origBitmap = BitmapFactory.decodeFile(inputFile, opts);

        if (origBitmap == null)
            return null;

        Matrix matrix = new Matrix();
        matrix.preRotate(rotationInDegrees);
        // we need not check if the rotation is not needed, since the below function will then return the same bitmap. Thus no memory loss occurs.

        return Bitmap.createBitmap(origBitmap, 0, 0, origBitmap.getWidth(), origBitmap.getHeight(), matrix, true);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight, int rotationInDegrees) {

        // Raw height and width of image
        final int height;
        final int width;
        int inSampleSize = 1;

        // Check for rotation
        if(rotationInDegrees == DEGREES_90 || rotationInDegrees == DEGREES_270){
            width = options.outHeight;
            height = options.outWidth;
        } else {
            height = options.outHeight;
            width = options.outWidth;
        }

        if (height > reqHeight || width > reqWidth) {
            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        return inSampleSize;
    }

    private static int getExifDegreesFromJpeg(String inputFile) {
        try {
            final ExifInterface exif = new ExifInterface(inputFile);
            final int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
                return 90;
            } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
                return 180;
            } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
                return 270;
            }
        } catch (IOException e) {
            Log.e("exif", "Error when trying to get exif data from : " + inputFile, e);
        }
        return 0;
    }

    /**
     * Getting All Images Path from Gallery using Content Providers
     * {@link MediaStore.MediaColumns#DATA}
     *
     * @param activity an activity reference.
     *
     * @return the collection of image path from gallery.
     */
    public static ArrayList<String> getAllShownImagesPath(Activity activity) {
        Uri uri;
        Cursor cursor;
        int column_index_data, column_index_folder_name;
        ArrayList<String> listOfAllImages = new ArrayList<String>();
        String absolutePathOfImage = null;
        uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = { MediaStore.MediaColumns.DATA,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME };

        cursor = activity.getContentResolver().query(uri, projection, null,
                null, null);

        column_index_data = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
        column_index_folder_name = cursor
                .getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
        while (cursor.moveToNext()) {
            absolutePathOfImage = cursor.getString(column_index_data);

            listOfAllImages.add(absolutePathOfImage);
        }
        return listOfAllImages;
    }

    /**
     * Set Gallery Images in Horizontal View {@link ImageUtil#getAllShownImagesPath(Activity)}
     *
     * @see Glide#with(Activity)
     *
     * @param activity
     * @param layout
     */
    public static void setImageHorizontalList(Activity activity, LinearLayout layout){
        List<String> imagePathList = getAllShownImagesPath(activity);
        for (int i = 0; i < imagePathList.size(); i++) {
            ImageView imageView = new ImageView(activity);
            imageView.setId(i);
            imageView.setPadding(2, 2, 2, 2);
            Glide.with(activity).load(imagePathList.get(i)).into(imageView);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            layout.addView(imageView);
            imageView.getLayoutParams().width = (int) activity.getResources().
                    getDimension(R.dimen.gallery_layout_width);
            imageView.getLayoutParams().height = (int) activity.getResources().
                    getDimension(R.dimen.gallery_layout_height);
        }
    }

    /**
     * Initiate image gallery intent for the system.
     *
     * @param activity an activity reference.
     * @param file an output file object.
     *
     */
    public static void openGalleryIntent(Activity activity, File file) {
        Intent galleryIntent = new Intent();
        galleryIntent.setType(IMAGE_FILTER_IMPLICIT_INTENT);
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
        galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
        galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        galleryIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        galleryIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                FileProvider.getUriForFile(activity,
                        activity.getApplicationContext().getPackageName()
                                + FILE_PROVIDER_TAG, file
                        ));
        if (galleryIntent.resolveActivity(activity.getPackageManager()) != null) {
            activity.startActivityForResult(galleryIntent, REQUEST_IMAGE_FROM_GALLERY);
        }
    }


}