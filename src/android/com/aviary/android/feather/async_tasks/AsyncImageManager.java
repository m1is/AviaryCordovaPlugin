package com.aviary.android.feather.async_tasks;

import java.lang.ref.WeakReference;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;

import com.aviary.android.feather.library.log.LoggerFactory;
import com.aviary.android.feather.library.log.LoggerFactory.Logger;
import com.aviary.android.feather.library.log.LoggerFactory.LoggerType;
import com.aviary.android.feather.library.threading.PriorityThreadFactory;
import com.aviary.android.feather.utils.SimpleBitmapCache;

/**
 * Load an internal asset asynchronous.
 * 
 * @author alessandro
 */
public class AsyncImageManager {

	public static enum Priority {
		HIGH, LOW
	};
	
	private static final int THUMBNAIL_LOADED = 1;
	
	abstract static class MyRunnable implements Runnable {

		public WeakReference<ImageView> mView;

		public MyRunnable ( ImageView image ) {
			mView = new WeakReference<ImageView>( image );
		}
	}
	
	public static interface OnImageLoadListener {
		public void onLoadComplete( ImageView view, Bitmap bitmap, int tag );
	}
	
	
	ExecutorService mExecutor1;
	ExecutorService mExecutor2;
	
	private volatile Boolean mStopped = false;

	private SimpleBitmapCache mBitmapCache;
	private OnImageLoadListener mListener;
	private Handler mHandler;

	private Logger logger = LoggerFactory.getLogger( "AsyncImageManager", LoggerType.ConsoleLoggerType );


	public AsyncImageManager() {
		
		mExecutor1 = Executors.newCachedThreadPool( new PriorityThreadFactory( "async-image-high", android.os.Process.THREAD_PRIORITY_BACKGROUND ) );
		mExecutor2 = Executors.newFixedThreadPool( 1, new PriorityThreadFactory( "async-image-low", android.os.Process.THREAD_PRIORITY_LOWEST ) );
		
		mBitmapCache = new SimpleBitmapCache();
		mHandler = new MyHandler( this );
		mListener = null;
	}

	public void setOnLoadCompleteListener( OnImageLoadListener listener ) {
		mListener = listener;
	}

	private static class MyHandler extends Handler {

		WeakReference<AsyncImageManager> mParent;

		public MyHandler ( AsyncImageManager parent ) {
			mParent = new WeakReference<AsyncImageManager>( parent );
		}

		@Override
		public void handleMessage( Message msg ) {

			switch ( msg.what ) {
				case AsyncImageManager.THUMBNAIL_LOADED:

					Thumb thumb = (Thumb) msg.obj;

					AsyncImageManager parent = mParent.get();
					ImageView view = thumb.image.get();
					Bitmap bitmap = thumb.bitmap != null ? thumb.bitmap.get() : null;

					if ( null != parent ) {
						if ( parent.mListener != null ) {
							if ( null != view ) {
								parent.mListener.onLoadComplete( view, bitmap, thumb.tag );
							}
							return;
						}
					}

					if ( view != null && bitmap != null ) {
						view.setImageBitmap( bitmap );
					}

					break;
			}
		}
	}

	public void shutDownNow() {
		// logger.info( "shutDownNow" );
		
		mStopped = true;
		
		mExecutor1.shutdownNow();
		mExecutor2.shutdownNow();

		mHandler = null;
		clearCache();
	}

	public void executeDelayed( final Callable<Bitmap> executor, final String hash, final ImageView view, final int tag, final Priority priority, long delayMillis ) {
		// logger.info( "executeDelayed: " + tag );
		if( null != mHandler ) {
			mHandler.postDelayed( new Runnable() {
				
				@Override
				public void run() {
					if( !mStopped ) {
						execute( executor, hash, view, tag, priority );
					}
				}
			}, delayMillis );
		}
	}
	
	/**
	 * Retrive the bitmap either using the internal cache or executing the passed
	 * {@link Callable} instance.
	 * 
	 * @param executor	the executor
	 * @param hash	the unique hash used to store/retrieve the bitmap from the cache
	 * @param view	the final {@link ImageView} where the bitmap will be shown
	 * @param tag	a custom tag
	 * @param priority	the process priority
	 */
	public void execute( final Callable<Bitmap> executor, final String hash, final ImageView view, final int tag, final Priority priority ) {

		if ( mStopped ) return;

		mBitmapCache.resetPurgeTimer();

		// logger.info( "Adding executor with tag: " + tag + " and priority: " + priority );
		
		MyRunnable task = new MyRunnable( view ) {
			
			@Override
			public void run() {
				
				// logger.log( "running task with priority: " + getPriority() + " and tag: " + tag );
				
				 if ( mStopped ) return;
				 
				 MyRunnable bitmapTask = getBitmapTask( mView.get() );
				 if( !this.equals( bitmapTask ) ) {
					 logger.warning( "not the same task: " + this + " != " + bitmapTask );
					 return;
				 }
				  
				 Message message = Message.obtain();
				 Bitmap bitmap = mBitmapCache.getBitmapFromCache( hash );
				 
				 if ( bitmap != null ) {
					 // logger.log( "bitmap from cache: " + hash );
					 message.what = THUMBNAIL_LOADED;
					 message.obj = new Thumb( bitmap, mView.get(), tag );
				 } else {
					 try {
						 // logger.log( "render bitmap: " + hash );
						 bitmap = executor.call();
					 } catch ( Exception e ) {
						 e.printStackTrace();
						 return;
					 }
				 
					 if ( bitmap != null ) {
						 // logger.log( "adding bitmap to cache: " + hash );
						 mBitmapCache.addBitmapToCache( hash, bitmap );
					 }
				 
					 ImageView imageView = mView.get();
				 
					 if ( imageView != null ) {
						 if ( this.equals( bitmapTask ) ) {
							 imageView.setTag( null );
							 message.what = THUMBNAIL_LOADED;
							 message.obj = new Thumb( bitmap, imageView, tag );
						 } else {
							 logger.error( "image tag is different than current task!" );
						 }
				 	}
				 }
				 
				 if ( message.what == THUMBNAIL_LOADED && null != mHandler ) {
					 mHandler.sendMessage( message );
				 }
			}
		};
			
		view.setTag( new CustomTag( task ) );
		
		if( priority == Priority.HIGH ) {
			mExecutor1.execute( task );
		} else {
			mExecutor2.execute( task );
		}
		
	}

	static class CustomTag {

		private final WeakReference<MyRunnable> taskReference;

		public CustomTag ( MyRunnable task ) {
			super();
			taskReference = new WeakReference<MyRunnable>( task );
		}

		public MyRunnable getDownloaderTask() {
			return taskReference.get();
		}
	}

	private static MyRunnable getBitmapTask( ImageView imageView ) {
		if ( imageView != null ) {
			Object tag = imageView.getTag();
			if ( tag instanceof CustomTag ) {
				CustomTag runnableTag = (CustomTag) tag;
				return runnableTag.getDownloaderTask();
			}
		}
		return null;
	}

	/**
	 * Clears the image cache used internally to improve performance. Note that for memory
	 * efficiency reasons, the cache will
	 * automatically be cleared after a certain inactivity delay.
	 */
	public void clearCache() {
		mBitmapCache.clearCache();
	}

	/**
	 * The Class Thumb.
	 */
	static class Thumb {

		public WeakReference<Bitmap> bitmap;
		public WeakReference<ImageView> image;
		public final int tag;

		public Thumb ( Bitmap bmp, ImageView img ) {
			this( bmp, img, -1 );
		}

		public Thumb ( Bitmap bmp, ImageView img, int ntag ) {
			image = new WeakReference<ImageView>( img );
			bitmap = new WeakReference<Bitmap>( bmp );
			tag = ntag;
		}
	}
}
