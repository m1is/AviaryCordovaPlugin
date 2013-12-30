package com.aviary.android.feather.widget;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory.Options;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.aviary.android.feather.AviaryMainController;
import com.aviary.android.feather.FeatherActivity;
import com.aviary.android.feather.R;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.content.FeatherIntent;
import com.aviary.android.feather.library.log.LoggerFactory;
import com.aviary.android.feather.library.log.LoggerFactory.Logger;
import com.aviary.android.feather.library.log.LoggerFactory.LoggerType;
import com.aviary.android.feather.library.plugins.ExternalPacksTask;
import com.aviary.android.feather.library.plugins.ExternalType;
import com.aviary.android.feather.library.plugins.FeatherExternalPack;
import com.aviary.android.feather.library.plugins.PluginFactory;
import com.aviary.android.feather.library.plugins.PluginFactory.ExternalPlugin;
import com.aviary.android.feather.library.services.FutureListener;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.ImageCacheService;
import com.aviary.android.feather.library.services.ImageCacheService.SimpleCachedRemoteBitmap;
import com.aviary.android.feather.library.services.PluginService;
import com.aviary.android.feather.library.services.ThreadPoolService;
import com.aviary.android.feather.library.services.ThreadPoolService.BackgroundCallable;
import com.aviary.android.feather.library.tracking.Tracker;
import com.aviary.android.feather.library.utils.SystemUtils;
import com.aviary.android.feather.widget.AviaryWorkspace.OnPageChangeListener;
import com.aviary.android.feather.widget.CellLayout.CellInfo;

public class IAPDialog implements OnPageChangeListener, OnClickListener {

	public interface OnCloseListener {
		void onClose();
	}

	public static class IAPUpdater {

		private ExternalPlugin plugin;
		private String packagename;
		private int type;
		
		public String getPackageName() {
			if( null != packagename ) {
				return packagename;
			}
			
			if( null != plugin ) {
				return plugin.getPackageName();
			}
			
			return null;
		}

		public static class Builder {

			IAPUpdater result;

			public Builder () {
				result = new IAPUpdater();
			}

			public Builder setPlugin( ExternalPlugin plugin ) {
				result.plugin = plugin;
				return this;
			}

			public Builder setPlugin( String packagename, int type ) {
				result.packagename = packagename;
				result.type = type;
				return this;
			}

			public IAPUpdater build() {
				return result;
			}
		}
	}

	private int mMainLayoutResId = R.layout.aviary_iap_workspace_screen_stickers;
	private int mCellResId = R.layout.aviary_iap_cell_item_stickers;

	private View mErrorView;
	private Button mRetryButton;

	private View mLoader;

	private View mBackground;
	private AviaryTextView mTitle, mDescription;
	private Button mButton;
	private AviaryWorkspace mWorkspace;
	private AviaryWorkspaceIndicator mWorkspaceIndicator;
	private ImageView mIcon;
	private IAPUpdater mCurrentData;

	private ThreadPoolService mThreadService;
	private ImageCacheService mCacheService;
	private AviaryMainController mController;

	private boolean mDownloadOnDemand = true;

	private OnCloseListener mCloseListener;

	int mRows;
	int mCols;
	int mItemsPerPage;

	private ViewGroup mView;
	private static Logger logger = LoggerFactory.getLogger( "iap-dialog", LoggerType.ConsoleLoggerType );

	public static IAPDialog create( ViewGroup container, IAPUpdater data ) {
		
		logger.info( "create" );
		
		ViewGroup dialog = (ViewGroup) container.findViewById( R.id.aviary_main_iap_dialog_container );
		IAPDialog instance = null;

		if ( dialog == null ) {
			dialog = (ViewGroup) LayoutInflater.from( container.getContext() ).inflate( R.layout.aviary_iap_dialog_container, container, false );
			dialog.setFocusable( true );
			instance = new IAPDialog( dialog );
			container.addView( dialog );
			
			instance.update( data );
		} else {
			instance = (IAPDialog) dialog.getTag();
			instance.update( data );
		}
		return instance;
	}

	public IAPDialog ( ViewGroup view ) {
		mView = view;
		mView.setTag( this );
		onAttachedToWindow();
	}

	public void onConfigurationChanged( Configuration newConfig ) {
		logger.info( "onConfigurationChanged" );
		
		if( !valid() ) return;
		
		ViewGroup parent = (ViewGroup) mView.getParent();
		if( null != parent ) {
			int index = parent.indexOfChild( mView );
			parent.removeView( mView );
			mView = null;
			
			mView = (ViewGroup) LayoutInflater.from( parent.getContext() ).inflate( R.layout.aviary_iap_dialog_container, parent, false );
			
			parent.addView( mView, index );
			
			mView.setFocusable( true );
			
			ViewGroup animator = (ViewGroup) mView.findViewById( R.id.aviary_main_iap_dialog );
			if( null != animator ) {
				animator.setLayoutAnimation( null );
			}
			
			onAttachedToWindow();
			update( mCurrentData );
		} else {
			logger.error( "parent is null" );
		}
	}

	public IAPUpdater getData() {
		return mCurrentData;
	}

	protected void onAttachedToWindow() {
		logger.info( "onAttachedFromWindow" );

		ExternalPlugin plugin = getPlugin();
		computeLayoutItems( mView.getResources(), plugin != null ? plugin.getType() : 0 );

		mDownloadOnDemand = SystemUtils.getApplicationTotalMemory() < Constants.APP_MEMORY_MEDIUM;

		mIcon = (ImageView) mView.findViewById( R.id.aviary_icon );
		mBackground = mView.findViewById( R.id.aviary_main_iap_dialog );
		mButton = (Button) mView.findViewById( R.id.aviary_button );
		mTitle = (AviaryTextView) mView.findViewById( R.id.aviary_title );
		mDescription = (AviaryTextView) mView.findViewById( R.id.aviary_description );
		mWorkspace = (AviaryWorkspace) mView.findViewById( R.id.aviary_workspace );
		mWorkspaceIndicator = (AviaryWorkspaceIndicator) mView.findViewById( R.id.aviary_workspace_indicator );
		mLoader = mView.findViewById( R.id.aviary_progress );

		mRetryButton = (Button) mView.findViewById( R.id.aviary_retry_button );
		mRetryButton.setEnabled( true );
		mErrorView = mView.findViewById( R.id.aviary_error_message );

		mBackground.setOnClickListener( this );
		mRetryButton.setOnClickListener( this );
	}

	private void computeLayoutItems( Resources res, int pluginType ) {
		if ( pluginType == FeatherIntent.PluginType.TYPE_FILTER || pluginType == FeatherIntent.PluginType.TYPE_BORDER ) {
			mMainLayoutResId = R.layout.aviary_iap_workspace_screen_effects;
			mCellResId = R.layout.aviary_iap_cell_item_effects;
			mCols = res.getInteger( R.integer.aviary_iap_dialog_cols_effects );
			mRows = res.getInteger( R.integer.aviary_iap_dialog_rows_effects );
		} else {
			mMainLayoutResId = R.layout.aviary_iap_workspace_screen_stickers;
			mCellResId = R.layout.aviary_iap_cell_item_stickers;
			mCols = res.getInteger( R.integer.aviary_iap_dialog_cols_stickers );
			mRows = res.getInteger( R.integer.aviary_iap_dialog_rows_stickers );
		}
		mItemsPerPage = mRows * mCols;
	}

	protected void onDetachedFromWindow() {
		logger.info( "onDetachedFromWindow" );
		setOnCloseListener( null );
		mButton.setOnClickListener( null );
		mRetryButton.setOnClickListener( null );
		mWorkspace.setAdapter( null );
		mWorkspace.setOnPageChangeListener( null );
		mBackground.setOnClickListener( null );
		mCloseListener = null;
		mController = null;
		mThreadService = null;
		mCacheService = null;
		mCurrentData = null;
		mView = null;
	}

	@Override
	public void onClick( View v ) {
		
		if ( v.equals( mBackground ) ) {
			if ( mCloseListener != null ) {
				mCloseListener.onClose();
			}
		} else if ( v.equals( mRetryButton ) ) {
			update( mCurrentData );
		}
	}

	private void initWorkspace( ExternalPlugin plugin ) {
		
		logger.info( "initWorkspace" );
		
		if ( null != plugin && null != plugin.getItems() && valid() ) {

			String[] items = plugin.getItems();
			String folder = getRemoteFolder( plugin );
			mWorkspace.setAdapter( new WorkspaceAdapter( mView.getContext(), folder, mMainLayoutResId, -1, items ) );
			mWorkspace.setOnPageChangeListener( this );

			if ( plugin.getItems().length <= mItemsPerPage ) {
				mWorkspaceIndicator.setVisibility( View.INVISIBLE );
			} else {
				mWorkspaceIndicator.setVisibility( View.VISIBLE );
			}
		} else {
			logger.error( "invalid plugin" );
			mWorkspace.setAdapter( null );
			mWorkspace.setOnPageChangeListener( null );
		}
	}

	private String getRemoteFolder( ExternalPlugin plugin ) {

		if ( null != plugin && null != plugin.getPackageName() ) {
			String[] pkg = plugin.getPackageName().split( "\\." );
			String folder = null;
			if ( pkg.length >= 2 ) {
				folder = PluginService.CONTENT_DEFAULT_URL + "/" + pkg[pkg.length - 2] + "/" + pkg[pkg.length - 1];
				return folder;
			}
		}
		return "";
	}

	public void setOnCloseListener( OnCloseListener listener ) {
		mCloseListener = listener;
	}

	public ExternalPlugin getPlugin() {
		if ( null != mCurrentData ) return mCurrentData.plugin;
		return null;
	}

	public int getPluginType() {
		if ( mCurrentData != null && mCurrentData.plugin != null ) {
			return mCurrentData.plugin.getType();
		}
		return 0;
	}

	public void update( IAPUpdater updater ) {
		if ( null == updater || !valid() ) return;
		
		logger.info( "update" );
		
		String pname = updater.getPackageName();
		if( null != pname ) {
			Tracker.recordTag( "Unpurchased(" + pname + ") : Opened" );
		}
		
		mCurrentData = updater;

		if ( null == mController ) {
			
			if ( mView.getContext() instanceof FeatherActivity ) {
				mController = ( (FeatherActivity) mView.getContext() ).getMainController();
				logger.log( "controller: " + mController );

				if ( null != mController ) {
					mThreadService = mController.getService( ThreadPoolService.class );
					mCacheService = mController.getService( ImageCacheService.class );
				}
			}
		} else {
			logger.log( "controller: " + mController );
		}

		if ( updater.plugin != null ) {
			processPlugin();
		} else {
			if ( updater.packagename != null && updater.type != 0 ) {
				downloadPlugin( updater.packagename, updater.type );
			}
		}
	}

	private void downloadPlugin( final String name, final int type ) {
		logger.info( "downloadPlugin: " + name + ", type: " + type );
		
		if( !valid() ) return;

		mButton.setVisibility( View.INVISIBLE );

		// show loader
		mLoader.setVisibility( View.VISIBLE );
		mErrorView.setVisibility( View.GONE );

		mTitle.setText("");

		if ( null != mThreadService ) {

			Bundle params = new Bundle();
			params.putBoolean( ExternalPacksTask.OPTION_IN_USE_CACHE, false );
			FutureListener<Bundle> listener = new FutureListener<Bundle>() {

				@SuppressWarnings ( "unchecked" )
				@Override
				public void onFutureDone( Future<Bundle> future ) {

					if ( !valid() ) return;
					
					Bundle result = null;
					try {
						result = future.get();
					} catch ( Exception e ) {
						result = null;
						logger.error( e.getMessage() );
					}

					if ( null != result ) {
						if ( result.containsKey( ExternalPacksTask.BUNDLE_RESULT_LIST ) ) {
							final List<ExternalType> allplugins = (List<ExternalType>) result
									.get( ExternalPacksTask.BUNDLE_RESULT_LIST );

							mView.post( new Runnable() {

								@Override
								public void run() {
									processPlugins( allplugins, name, type );
								}
							} );
						}
					} else {
						mView.post( new Runnable() {
							@Override
							public void run() {
								onDownloadError();
							}
						} );
					}

				}
			};
			mThreadService.submit( new ExternalPacksTask(), listener, params );
		}
	}

	private void processPlugins( List<ExternalType> list, String pkgname, int type ) {
		logger.info( "processPlugins" );
		
		if( !valid() ) return;

		Iterator<ExternalType> iterator = list.iterator();
		while ( iterator.hasNext() ) {
			ExternalType current = iterator.next();
			if ( null != current ) {
				if ( pkgname.equals( current.getPackageName() ) ) {
					FeatherExternalPack pack = new FeatherExternalPack( current );
					ExternalPlugin plugin = (ExternalPlugin) PluginFactory.create( mView.getContext(), pack, type );
					update( new IAPUpdater.Builder().setPlugin( plugin ).build() );
					return;
				}
			}
		}
		onDownloadError();
	}

	/**
	 * Error downloading plugin informations
	 */
	private void onDownloadError() {
		mErrorView.setVisibility( View.VISIBLE );
		mLoader.setVisibility( View.GONE );
		
		mTitle.setText( "" );
		mRetryButton.setEnabled( true );
	}

	private void processPlugin() {
		logger.info( "processPlugin" );
		
		if( !valid() ) return;
		if ( null == mCurrentData || mCurrentData.plugin == null ) return;

		final ExternalPlugin plugin = mCurrentData.plugin;

		mButton.setVisibility( View.VISIBLE );
		mLoader.setVisibility( View.GONE );
		
		mErrorView.setVisibility( View.GONE );

		computeLayoutItems( mView.getResources(), plugin.getType() );

		logger.log( "cols: " + mCols + ", rows: " + mRows );

		mTitle.setText( plugin.getPackageLabel() + " (" + plugin.size() + " effects)" );
		mTitle.setSelected( true );
		
		mDescription.setText( plugin.getDescription() != null ? plugin.getDescription() : "" );

		if ( null != plugin.getPackageName() ) {

			mWorkspace.setIndicator( mWorkspaceIndicator );
			initWorkspace( plugin );
			downloadPackIcon( plugin );

			mButton.setOnClickListener( new OnClickListener() {

				@Override
				public void onClick( View v ) {
					if ( null != mController ) {
						Tracker.recordTag( "Unpurchased(" + plugin.getPackageName() + ") : StoreButtonClicked" );
						mController.downloadPlugin( plugin.getPackageName(), plugin.getType() );

						if ( !valid() ) return;

						mView.postDelayed( new Runnable() {

							@Override
							public void run() {
								if ( mCloseListener != null ) {
									mCloseListener.onClose();
								}
							}
						}, 500 );

					}
				}
			} );
		}
	}

	/**
	 * Fetch the current pack icon
	 * 
	 * @param plugin
	 */
	private void downloadPackIcon( ExternalPlugin plugin ) {
		
		logger.info( "downloadPackIcon" );
		
		if ( null != plugin && valid() ) {
			if ( null != mThreadService && null != mIcon ) {
				final String url = PluginService.CONTENT_DEFAULT_URL + "/" + plugin.getIconUrl();
				BackgroundImageLoader callable = new BackgroundImageLoader( mCacheService, false );
				BackgroundImageLoaderListener listener = new BackgroundImageLoaderListener( mIcon, url );
				mThreadService.submit( callable, listener, url );
			}
		}
	}

	class WorkspaceAdapter extends ArrayAdapter<String> {

		LayoutInflater mLayoutInflater;
		int mResId;
		String mUrlPrefix;

		public WorkspaceAdapter ( Context context, String urlPrefix, int resource, int textResourceId, String[] objects ) {
			super( context, resource, textResourceId, objects );
			mUrlPrefix = urlPrefix;
			mResId = resource;
			mLayoutInflater = LayoutInflater.from( getContext() );
		}

		public String getUrlPrefix() {
			return mUrlPrefix;
		}

		@Override
		public int getCount() {
			return (int) Math.ceil( (double) super.getCount() / mItemsPerPage );
		}

		/**
		 * Gets the real num of items.
		 * 
		 * @return the real count
		 */
		public int getRealCount() {
			return super.getCount();
		}

		@Override
		public View getView( int position, View convertView, ViewGroup parent ) {
			
			logger.info( "getView: " + position + ", convertView: " + convertView );

			if ( convertView == null ) {
				convertView = mLayoutInflater.inflate( mResId, mWorkspace, false );
			}

			CellLayout cell = (CellLayout) convertView;
			cell.setNumCols( mCols );
			cell.setNumRows( mRows );

			for ( int i = 0; i < mItemsPerPage; i++ ) {
				View toolView;
				CellInfo cellInfo = cell.findVacantCell();

				if ( cellInfo == null ) {
					toolView = cell.getChildAt( i );
				} else {
					toolView = mLayoutInflater.inflate( mCellResId, parent, false );
					CellLayout.LayoutParams lp = new CellLayout.LayoutParams( cellInfo.cellX, cellInfo.cellY, cellInfo.spanH,
							cellInfo.spanV );
					cell.addView( toolView, -1, lp );
				}

				final int index = ( position * mItemsPerPage ) + i;
				final ImageView imageView = (ImageView) toolView.findViewById( R.id.aviary_image );
				final View progress = toolView.findViewById( R.id.aviary_progress );

				if ( index < getRealCount() ) {
					final String url = getUrlPrefix() + "/" + getItem( index ) + ".png";
					final String tag = (String) imageView.getTag();

					if ( mDownloadOnDemand ) {
						// if on demand we can clean up the bitmap
						if ( tag == null || !url.equals( tag ) ) {
							imageView.setImageBitmap( null );
							imageView.setTag( null );
						}
					} else {
						// download the image immediately
						if ( null != mThreadService ) {

							if ( null != progress ) {
								progress.setVisibility( View.VISIBLE );
							}

							imageView.setImageBitmap( null );
							imageView.setTag( null );
							BackgroundImageLoader callable = new BackgroundImageLoader( mCacheService, true );
							BackgroundImageLoaderListener listener = new BackgroundImageLoaderListener( imageView, url );
							mThreadService.submit( callable, listener, url );
						}
					}
				} else {
					if ( null != progress ) {
						progress.setVisibility( View.GONE );
					}
					imageView.setImageBitmap( null );
					imageView.setTag( null );
				}
			}

			convertView.requestLayout();
			return convertView;
		}
	}

	@Override
	public void onPageChanged( int which, int old ) {

		if ( !mDownloadOnDemand ) return;
		if ( !valid() ) return;

		logger.info( "onPageChanged: " + which + " from " + old );

		if ( null != mWorkspace ) {
			WorkspaceAdapter adapter = (WorkspaceAdapter) mWorkspace.getAdapter();

			int index = which * mItemsPerPage;
			int endIndex = index + mItemsPerPage;
			int total = adapter.getRealCount();

			for ( int i = index; i < endIndex; i++ ) {
				CellLayout cellLayout = (CellLayout) mWorkspace.getScreenAt( which );
				View toolView = cellLayout.getChildAt( i - index );
				if ( i < total ) {
					final String url = adapter.getUrlPrefix() + "/" + adapter.getItem( i ) + ".png";
					final ImageView imageView = (ImageView) toolView.findViewById( R.id.aviary_image );
					final String tag = (String) imageView.getTag();

					if ( tag == null || !url.equals( tag ) ) {
						if ( null != mThreadService ) {

							logger.log( "fetching image: " + url );
							BackgroundImageLoader callable = new BackgroundImageLoader( mCacheService, true );
							BackgroundImageLoaderListener listener = new BackgroundImageLoaderListener( imageView, url );
							mThreadService.submit( callable, listener, url );

						}
					} else {
						logger.warning( "image already loaded?" );
					}
				}
			}
		}
	}

	public boolean valid() {
		return mView != null && mView.getWindowToken() != null;
	}

	protected void hide() {
		if ( !valid() ) return;
		logger.info( "hide" );
		if ( null != getPlugin() ) {
			Tracker.recordTag( "Unpurchased(" + getPlugin().getPackageName() + ") : Cancelled" );
		}
		mView.post( mHide );
	}

	@Override
	protected void finalize() throws Throwable {
		logger.info( "finalize" );
		super.finalize();
	}

	private Runnable mHide = new Runnable() {
		@Override
		public void run() {
			handleHide();
		}
	};

	/**
	 * Dismiss the current dialog
	 * @param animate
	 */
	public void dismiss( boolean animate ) {
		logger.info( "dismiss, animate: " + animate );
		if ( animate ) {
			hide();
		} else {
			removeFromParent();
		}
	}

	private void removeFromParent() {
		logger.info( "removeFromParent" );
		
		if ( null != mView ) {
			ViewGroup parent = (ViewGroup) mView.getParent();
			if ( null != parent ) {
				parent.removeView( mView );
				onDetachedFromWindow();
			}
		}
	}

	private void handleHide() {
		logger.info( "handleHide" );
		
		if ( !valid() ) return;

		Animation animation = AnimationUtils.loadAnimation( mView.getContext(), R.anim.aviary_iap_close_animation );
		AnimationListener listener = new AnimationListener() {

			@Override
			public void onAnimationStart( Animation animation ) {}

			@Override
			public void onAnimationRepeat( Animation animation ) {}

			@Override
			public void onAnimationEnd( Animation animation ) {
				removeFromParent();
			}
		};
		animation.setAnimationListener( listener );
		mView.startAnimation( animation );
	}

	class BackgroundImageLoaderListener implements FutureListener<Bitmap> {

		WeakReference<ImageView> mImageView;
		String mUrl;

		public BackgroundImageLoaderListener ( final ImageView view, final String url ) {
			mImageView = new WeakReference<ImageView>( view );
			mUrl = url;
		}

		@Override
		public void onFutureDone( Future<Bitmap> future ) {

			final ImageView image = mImageView.get();

			if ( null != image ) {
				try {
					final Bitmap bitmap = future.get();
					if ( valid() ) {
						mView.post( new Runnable() {

							@SuppressWarnings ( "deprecation" )
							@Override
							public void run() {
								
								if( !valid() ) return;
								if( mView.getContext() == null ) return;

								if ( null == bitmap ) {
									image.setScaleType( ScaleType.CENTER );
									image.setImageResource( R.drawable.aviary_ic_na );
								} else {
									image.setScaleType( ScaleType.FIT_CENTER );
									image.setImageDrawable( new BitmapDrawable( bitmap ) );
								}

								View parent = (View) image.getParent();
								if ( null != parent ) {
									View progress = parent.findViewById( R.id.aviary_progress );
									if ( null != progress ) {
										progress.setVisibility( View.INVISIBLE );
									}
								}

								try {
									Animation anim = AnimationUtils.loadAnimation( mView.getContext(), android.R.anim.fade_in );
									image.startAnimation( anim );
									image.setTag( mUrl );
								} catch( Exception e ){
									e.printStackTrace();
								}
							}
						} );
					}
				} catch ( Throwable e ) {
					e.printStackTrace();
				}
			} else {
				logger.warning( "imageView is null" );
			}
		}
	}

	/**
	 * Background loader for the remote images
	 * 
	 * @author alessandro
	 * 
	 */
	static class BackgroundImageLoader extends BackgroundCallable<String, Bitmap> {

		WeakReference<ImageCacheService> mImageCache;
		boolean mSaveToCache;

		public BackgroundImageLoader ( ImageCacheService service, boolean saveToCache ) {
			mImageCache = new WeakReference<ImageCacheService>( service );
			mSaveToCache = saveToCache;
		}

		@Override
		public Bitmap call( IAviaryController context, String url ) {

			if ( null != url ) {

				Options options = new Options();
				options.inPreferredConfig = Bitmap.Config.RGB_565;
				options.inInputShareable = true;
				options.inPurgeable = true;

				ImageCacheService service = mImageCache.get();
				if ( null != service ) {

					SimpleCachedRemoteBitmap remoteRequest;

					try {
						remoteRequest = service.requestRemoteBitmap( url );
					} catch ( MalformedURLException e ) {
						e.printStackTrace();
						return null;
					}

					try {
						return remoteRequest.getBitmap( options );
					} catch ( IOException e ) {
						e.printStackTrace();
						return null;
					}
				}
			}
			return null;
		}
	}

}
