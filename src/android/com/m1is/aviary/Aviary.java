package com.m1is.aviary;

import java.io.File;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.FeatherActivity;

public class Aviary extends CordovaPlugin {

	private static final int ACTION_REQUEST_FEATHER = 1;	
	private static final String LOG_TAG = "Aviary";

	private String mFolderName = "Aviary"; // Folder name on the sdcard where the images will be saved
	private File mGalleryFolder;
    private CallbackContext callbackContext;
	
	@Override
	public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
		mFolderName = args.isNull(10) ? mFolderName : args.getString(10);
		mGalleryFolder = createFolders();
		
		if (action.equals("show")) {
			try {
				Log.i(LOG_TAG, action);
				this.callbackContext = callbackContext;
				
				// make sure an image URI has been provided
				if (args.isNull(0)){
					callbackContext.error("Cannot start aviary, an image URI is required.");
					return true;	
				}
				
				// parameters
				String source = args.getString(0); // 0 - image URI
				String outputFormat = args.isNull(1) ? Bitmap.CompressFormat.JPEG.name() : args.getString(1); // 1 - EXTRA_OUTPUT_FORMAT
				int quality = args.isNull(2) ? 95 : args.getInt(2); // 2 - EXTRA_OUTPUT_QUALITY
				
				String[] toolList = new String[]{}; // 3 - EXTRA_TOOLS_LIST
				if (!args.isNull(3)){
					JSONArray toolArray = args.getJSONArray(3);
					toolList = new String[toolArray.length()];
					for(int i = 0; i < toolArray.length(); i++) {
					    toolList[i] = toolArray.getString(i);
					}
				}
				
				Boolean hideExitUnsaveConfirmation = args.isNull(4) ? false : args.getBoolean(4); // 4 - EXTRA_HIDE_EXIT_UNSAVE_CONFIRMATION
				Boolean enableEffectsPacks = args.isNull(5) ? false : args.getBoolean(5); // 5 - EXTRA_EFFECTS_ENABLE_EXTERNAL_PACKS
				Boolean enableFramesPacks = args.isNull(6) ? false : args.getBoolean(6); // 6 - EXTRA_FRAMES_ENABLE_EXTERNAL_PACKS
				Boolean enableStickersPacks = args.isNull(7) ? false : args.getBoolean(7); // 7 - EXTRA_STICKERS_ENABLE_EXTERNAL_PACKS
				Boolean disableVibration = args.isNull(8) ? false : args.getBoolean(8); // 8 - EXTRA_TOOLS_DISABLE_VIBRATION
				Boolean inSaveOnNoChanges = args.isNull(9) ? true : args.getBoolean(9); // 9 - EXTRA_IN_SAVE_ON_NO_CHANGES
				
				// get URI
				Uri uri = Uri.parse(source);
				
				// first check the external storage availability
				if ( !isExternalStorageAvilable() ) {
					callbackContext.error("Cannot start aviary, external storage unavailable.");
					return true;
				}
				String mOutputFilePath;
				
				// create a temporary file where to store the resulting image
				File file = getNextFileName();
				if ( null != file ) {
					mOutputFilePath = file.getAbsolutePath();
				} else {
					callbackContext.error("Cannot start aviary, failed to create a temp file." );
					return true;
				}
				
				// Create the intent needed to start feather
				Class<FeatherActivity> clsAviary = FeatherActivity.class;
				Intent newIntent = new Intent(this.cordova.getActivity(), clsAviary);
				
				// set the parameters
				newIntent.setData(uri);
				newIntent.putExtra(Constants.EXTRA_OUTPUT, Uri.parse( "file://" + mOutputFilePath));
				newIntent.putExtra(Constants.EXTRA_OUTPUT_FORMAT, outputFormat);
				newIntent.putExtra(Constants.EXTRA_OUTPUT_QUALITY, quality);
				
				if (toolList.length > 0){
	                newIntent.putExtra(Constants.EXTRA_TOOLS_LIST, toolList);
				}

                newIntent.putExtra(Constants.EXTRA_HIDE_EXIT_UNSAVE_CONFIRMATION, hideExitUnsaveConfirmation);
                
                newIntent.putExtra(Constants.EXTRA_EFFECTS_ENABLE_EXTERNAL_PACKS, enableEffectsPacks);
                newIntent.putExtra(Constants.EXTRA_FRAMES_ENABLE_EXTERNAL_PACKS, enableFramesPacks);
                newIntent.putExtra(Constants.EXTRA_STICKERS_ENABLE_EXTERNAL_PACKS, enableStickersPacks);
                
                // http://developers.aviary.com/docs/android/intent-parameters#EXTRA_MAX_IMAGE_SIZE
                //newIntent.putExtra(Constants.EXTRA_MAX_IMAGE_SIZE, 1000);
                
                // since a minor bug exists that when explicitly setting this to false disables vibration so only set if true
                if (disableVibration){
                	newIntent.putExtra(Constants.EXTRA_TOOLS_DISABLE_VIBRATION, disableVibration);	
                }
                
                newIntent.putExtra(Constants.EXTRA_IN_SAVE_ON_NO_CHANGES, inSaveOnNoChanges);
                
				cordova.getActivity().startActivityForResult(newIntent, ACTION_REQUEST_FEATHER);
				cordova.setActivityResultCallback(this);
				return true;
			} catch (Exception ex) {
				Log.e(LOG_TAG, ex.toString());
				callbackContext.error("Unknown error occured showing aviary.");
			}
		} else if (action.equals("prepareForShow")) {
			// nothing to do on Android
			callbackContext.success();
		}

		return false;
	}
	
	@Override
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
	    if (resultCode == Activity.RESULT_OK || resultCode == Activity.RESULT_CANCELED) {
	        switch (requestCode) {
	            case ACTION_REQUEST_FEATHER:
	                Uri mImageUri = data.getData();
	                Boolean mChanged = data.getBooleanExtra("EXTRA_OUT_BITMAP_CHANGED", false);
	                
	                try {
		                JSONObject returnVal = new JSONObject();
		                //returnVal.put("changed", mChanged); // doesn't ever seem to be anything other than false
		                
		                if (mImageUri != null){
			                returnVal.put("src", mImageUri.toString());
			                returnVal.put("name", mImageUri.getLastPathSegment());			                	
		                }
	       
		                this.callbackContext.success(returnVal);
	                } catch(JSONException ex) {
	    				Log.e(LOG_TAG, ex.toString());
	                	this.callbackContext.error(ex.getMessage());
	                }
	                break;
	       }
	    }
	}

	/**
	 * Return a new image file. Name is based on the current time. Parent folder will be the one created with createFolders
	 * 
	 * @return
	 * @see #createFolders()
	 */
	private File getNextFileName() {
		if ( mGalleryFolder != null ) {
			if ( mGalleryFolder.exists() ) {
				File file = new File( mGalleryFolder, System.currentTimeMillis() + ".jpg" );
				return file;
			}
		}
		return null;
	}

	/**
	 * Try to create the required folder on the sdcard where images will be saved to.
	 * 
	 * @return
	 */
	private File createFolders() {

		File baseDir;

		if ( android.os.Build.VERSION.SDK_INT < 8 ) {
			baseDir = Environment.getExternalStorageDirectory();
		} else {
			baseDir = Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_PICTURES );
		}

		if ( baseDir == null ) return Environment.getExternalStorageDirectory();

		Log.d( LOG_TAG, "Pictures folder: " + baseDir.getAbsolutePath() );
		File aviaryFolder = new File( baseDir, mFolderName );

		if ( aviaryFolder.exists() ) return aviaryFolder;
		if ( aviaryFolder.mkdirs() ) return aviaryFolder;

		return Environment.getExternalStorageDirectory();
	}
	
	/**
	 * Check the external storage status
	 * 
	 * @return
	 */
	private boolean isExternalStorageAvilable() {
		String state = Environment.getExternalStorageState();
		if ( Environment.MEDIA_MOUNTED.equals( state ) ) {
			return true;
		}
		return false;
	}
}