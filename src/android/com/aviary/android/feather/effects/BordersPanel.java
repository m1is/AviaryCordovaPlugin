package com.aviary.android.feather.effects;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;
import it.sephiroth.android.library.widget.BaseAdapterExtended;
import it.sephiroth.android.library.widget.HorizontalVariableListView;
import it.sephiroth.android.library.widget.HorizontalVariableListView.OnItemClickedListener;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import org.json.JSONException;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewSwitcher.ViewFactory;

import com.aviary.android.feather.AviaryMainController.FeatherContext;
import com.aviary.android.feather.R;
import com.aviary.android.feather.async_tasks.AsyncImageManager;
import com.aviary.android.feather.async_tasks.AsyncImageManager.OnImageLoadListener;
import com.aviary.android.feather.async_tasks.AsyncImageManager.Priority;
import com.aviary.android.feather.graphics.PluginDividerDrawable;
import com.aviary.android.feather.headless.filters.INativeFilter;
import com.aviary.android.feather.headless.filters.NativeFilterProxy;
import com.aviary.android.feather.headless.moa.Moa;
import com.aviary.android.feather.headless.moa.MoaAction;
import com.aviary.android.feather.headless.moa.MoaActionFactory;
import com.aviary.android.feather.headless.moa.MoaActionList;
import com.aviary.android.feather.headless.moa.MoaResult;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.content.FeatherIntent;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.BorderFilter;
import com.aviary.android.feather.library.filters.FilterLoaderFactory;
import com.aviary.android.feather.library.filters.FilterLoaderFactory.Filters;
import com.aviary.android.feather.library.graphics.drawable.FakeBitmapDrawable;
import com.aviary.android.feather.library.plugins.FeatherExternalPack;
import com.aviary.android.feather.library.plugins.FeatherInternalPack;
import com.aviary.android.feather.library.plugins.FeatherPack;
import com.aviary.android.feather.library.plugins.PluginFactory;
import com.aviary.android.feather.library.plugins.PluginFactory.ExternalPlugin;
import com.aviary.android.feather.library.plugins.PluginFactory.FramePlugin;
import com.aviary.android.feather.library.plugins.PluginFactory.ICDSPlugin;
import com.aviary.android.feather.library.plugins.PluginFactory.IPlugin;
import com.aviary.android.feather.library.plugins.PluginFactory.InternalPlugin;
import com.aviary.android.feather.library.plugins.UpdateType;
import com.aviary.android.feather.library.services.CDSService;
import com.aviary.android.feather.library.services.ConfigService;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.ImageCacheService;
import com.aviary.android.feather.library.services.ImageCacheService.SimpleCachedRemoteBitmap;
import com.aviary.android.feather.library.services.LocalDataService;
import com.aviary.android.feather.library.services.PluginService;
import com.aviary.android.feather.library.services.PluginService.OnExternalUpdateListener;
import com.aviary.android.feather.library.services.PluginService.OnUpdateListener;
import com.aviary.android.feather.library.services.PluginService.PluginException;
import com.aviary.android.feather.library.services.PreferenceService;
import com.aviary.android.feather.library.tracking.Tracker;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.utils.ImageLoader;
import com.aviary.android.feather.library.utils.SystemUtils;
import com.aviary.android.feather.library.utils.UserTask;
import com.aviary.android.feather.widget.AviaryImageSwitcher;
import com.aviary.android.feather.widget.IAPDialog;
import com.aviary.android.feather.widget.IAPDialog.IAPUpdater;
import com.aviary.android.feather.widget.IAPDialog.OnCloseListener;

public class BordersPanel extends AbstractContentPanel implements ViewFactory, OnUpdateListener, OnImageLoadListener,
		OnItemSelectedListener, OnExternalUpdateListener, OnItemClickedListener {

	private static final int TAG_EXTERNAL_VIEW = 4000;
	private static final int TAG_INTERNAL_VIEW = 1000;

	private final int mPluginType;

	protected HorizontalVariableListView mHList;

	protected View mLoader;

	protected volatile Boolean mIsRendering = false;

	private volatile boolean mIsAnimating;

	private RenderTask mCurrentTask;

	protected PluginService mPluginService;
	
	protected ConfigService mConfigService;

	protected CDSService mCDSService;

	protected ImageCacheService mCacheService;

	private PreferenceService mPreferenceService;

	protected AviaryImageSwitcher mImageSwitcher;

	private boolean mExternalPacksEnabled = true;

	protected MoaActionList mActions = null;

	protected String mRenderedEffect;

	protected String mRenderedPackName;

	/**
	 * create a reference to the update alert dialog. This to prevent multiple alert
	 * messages
	 */
	private AlertDialog mUpdateDialog;

	/** default width of each effect thumbnail */
	private int mCellWidth = 80;

	private int mThumbSize;

	private List<String> mInstalledPackages;

	/** thumbnail cache manager */
	private AsyncImageManager mImageManager;

	/** thumbnail for effects */
	protected Bitmap mThumbBitmap;

	/** current selected position */
	protected int mSelectedPosition = -1;
	
	/* the first valid position of the list */
	protected int mListFirstValidPosition = 0;

	// display the "get more" view
	private boolean mShowGetMoreView = true;

	protected Bitmap updateArrowBitmap;

	/** max number of featured elements to display */
	private int mFeaturedCount;

	// don't display the error dialog more than once
	private static boolean mUpdateErrorHandled = false;

	private boolean mFirstTimeRenderer;

	/** options used to decode cached images */
	private static BitmapFactory.Options mThumbnailOptions;
	
	protected boolean mEnableFastPreview = false;

	public BordersPanel ( IAviaryController context, ToolEntry entry ) {
		this( context, entry, FeatherIntent.PluginType.TYPE_BORDER );
	}

	protected BordersPanel ( IAviaryController context, ToolEntry entry, int type ) {
		super( context, entry );
		mPluginType = type;
	}

	@Override
	public void onCreate( Bitmap bitmap, Bundle options ) {
		super.onCreate( bitmap, options );

		mImageManager = new AsyncImageManager();

		mThumbnailOptions = new Options();
		mThumbnailOptions.inPreferredConfig = Config.RGB_565;

		mConfigService = getContext().getService( ConfigService.class );
		mPluginService = getContext().getService( PluginService.class );
		mCDSService = getContext().getService( CDSService.class );
		mCacheService = getContext().getService( ImageCacheService.class );
		mPreferenceService = getContext().getService( PreferenceService.class );
		
		LocalDataService dataService = getContext().getService( LocalDataService.class );
		
		mEnableFastPreview = dataService.getFastPreviewEnabled();

		mExternalPacksEnabled = dataService.getExternalPacksEnabled( mPluginType );

		mHList = (HorizontalVariableListView) getOptionView().findViewById( R.id.aviary_list );
		mLoader = getOptionView().findViewById( R.id.aviary_loader );

		mPreview = BitmapUtils.copy( mBitmap, Bitmap.Config.ARGB_8888 );

		// ImageView Switcher setup
		mImageSwitcher = (AviaryImageSwitcher) getContentView().findViewById( R.id.aviary_switcher );
		initContentImage( mImageSwitcher );

		try {
			updateArrowBitmap = BitmapFactory.decodeResource( getContext().getBaseContext().getResources(), R.drawable.aviary_update_arrow );
		} catch ( Throwable t ) {}
	}

	@SuppressLint ( "NewApi" )
	@Override
	public void onActivate() {
		super.onActivate();

		// new method, using the panel height dinamically
		// mCellWidth = (int) ( getOptionView().findViewById( R.id.aviary_panel ).getHeight() * 0.68 );
		// mThumbSize = (int) ( mCellWidth * 0.859 );
		
		mCellWidth = mConfigService.getDimensionPixelSize( R.dimen.aviary_frame_item_width );
		mThumbSize = mConfigService.getDimensionPixelSize( R.dimen.aviary_frame_item_image_width );
		
		mLogger.log( "cell width: " + mCellWidth );
		mLogger.log( "thumb size: " + mThumbSize );

		mThumbBitmap = generateThumbnail( mBitmap, mThumbSize, mThumbSize );

		mInstalledPackages = Collections.synchronizedList( new ArrayList<String>() );

		mFeaturedCount = mConfigService.getInteger( R.integer.aviary_featured_packs_count );

		mHList.setGravity( Gravity.BOTTOM );
		mHList.setOverScrollMode( HorizontalVariableListView.OVER_SCROLL_ALWAYS );
		mHList.setOnItemSelectedListener( this );
		mHList.setOnItemClickedListener( this );

		mImageManager.setOnLoadCompleteListener( this );

		getContentView().setVisibility( View.VISIBLE );
		onPostActivate();
	}

	protected void initContentImage( AviaryImageSwitcher imageView ) {
		if ( null != imageView ) {
			imageView.setFactory( this );

			@SuppressWarnings ( "unused" )
			Matrix matrix = getContext().getCurrentImageViewMatrix();
			imageView.setImageBitmap( mBitmap, null );
			imageView.setAnimateFirstView( false );
		}
	}

	protected final int getPluginType() {
		return mPluginType;
	}

	private void showUpdateAlert( final CharSequence packageName, final CharSequence label, final int error,
			String exceptionMessage, boolean fromUseClick ) {
		if ( error != PluginService.ERROR_NONE ) {

			String errorString = getError( error, exceptionMessage );

			if ( error == PluginService.ERROR_PLUGIN_TOO_OLD || error == PluginService.ERROR_PLUGIN_CORRUPTED
					|| error == PluginService.ERROR_DOWNLOAD ) {

				OnClickListener yesListener = new OnClickListener() {

					@Override
					public void onClick( DialogInterface dialog, int which ) {
						Tracker.recordTag( "PluginNeedsUpdate: " + packageName );
						getContext().downloadPlugin( (String) packageName, mPluginType );
					}
				};

				onGenericMessage( label, errorString, R.string.feather_update, yesListener, android.R.string.cancel, null );

			} else if ( error == PluginService.ERROR_PLUGIN_TOO_NEW ) {
				OnClickListener yesListener = new OnClickListener() {

					@Override
					public void onClick( DialogInterface dialog, int which ) {
						String pname = getContext().getBaseContext().getPackageName();
						Tracker.recordTag( "EditorNeedsUpdate: " + packageName );
						getContext().downloadPlugin( pname, mPluginType );
					}
				};
				onGenericMessage( label, errorString, R.string.feather_update, yesListener, android.R.string.cancel, null );
			} else {
				onGenericMessage( label, errorString, android.R.string.ok, null );
			}
			return;
		}
	}

	/**
	 * Create a popup alert dialog when multiple plugins need to be updated
	 * 
	 * @param error
	 */
	private void showUpdateAlertMultiplePlugins( final int error, boolean fromUserClick ) {

		if ( error != PluginService.ERROR_NONE ) {
			final String errorString = getError( error, null );

			if ( error == PluginService.ERROR_PLUGIN_TOO_OLD || error == PluginService.ERROR_PLUGIN_CORRUPTED
					|| error == PluginService.ERROR_DOWNLOAD ) {
				OnClickListener yesListener = new OnClickListener() {

					@Override
					public void onClick( DialogInterface dialog, int which ) {
						getContext().searchPlugin( mPluginType );
					}
				};
				onGenericError( errorString, R.string.feather_update, yesListener, android.R.string.cancel, null );

			} else if ( error == PluginService.ERROR_PLUGIN_TOO_NEW ) {
				OnClickListener yesListener = new OnClickListener() {

					@Override
					public void onClick( DialogInterface dialog, int which ) {
						String pname = getContext().getBaseContext().getPackageName();
						getContext().downloadPlugin( pname, mPluginType );
					}
				};
				onGenericError( errorString, R.string.feather_update, yesListener, android.R.string.cancel, null );
			} else {
				onGenericError( errorString, android.R.string.ok, null );
			}
		}
	}

	/**
	 * Multiple items and different errors
	 * 
	 * @param pkgname
	 * @param set
	 */
	private void showUpdateAlertMultipleItems( final String pkgname, Set<Integer> set ) {
		if ( null != set ) {
			final String errorString = getContext().getBaseContext().getResources()
					.getString( R.string.feather_effects_error_update_multiple );

			OnClickListener yesListener = new OnClickListener() {

				@Override
				public void onClick( DialogInterface dialog, int which ) {
					getContext().searchPlugin( mPluginType );
				}
			};
			onGenericError( errorString, R.string.feather_update, yesListener, android.R.string.cancel, null );
		}
	}

	protected String getError( int error, String message ) {

		mLogger.info( "getError for " + error );

		int resId = R.string.feather_effects_error_loading_packs;

		switch ( error ) {
			case PluginService.ERROR_UNKNOWN:
				resId = R.string.feather_effects_unknown_errors;
				break;

			case PluginService.ERROR_PLUGIN_TOO_OLD:
				resId = R.string.feather_effects_error_update_packs;
				break;

			case PluginService.ERROR_PLUGIN_TOO_NEW:
				resId = R.string.feather_effects_error_update_editors;
				break;

			case PluginService.ERROR_DOWNLOAD:
				resId = R.string.feather_plugin_error_download;
				break;

			case PluginService.ERROR_PLUGIN_CORRUPTED:
				resId = R.string.feather_plugin_error_corrupted;
				break;

			case PluginService.ERROR_STORAGE_NOT_AVAILABLE:
				resId = R.string.feather_plugin_error_storage_not_available;
				break;

			default:
				if ( null != message ) {
					return message;
				}
				resId = R.string.feather_effects_unknown_errors;
				break;
		}

		return getContext().getBaseContext().getString( resId );
	}

	protected void onPostActivate() {
		// register for plugins updates
		mPluginService.registerOnUpdateListener( this );
		mPluginService.registerOnExternalUpdateListener( this );
		updateInstalledPacks( true );
		contentReady();
	}

	@Override
	public void onDestroy() {
		mPluginService = null;
		mCDSService = null;
		mCacheService = null;
		mPreferenceService = null;
		mConfigService = null;
		super.onDestroy();
	}

	@Override
	public void onDeactivate() {
		onProgressEnd();
		mPluginService.removeOnUpdateListener( this );
		mPluginService.removeOnExternalUpdateListener( this );
		mImageManager.setOnLoadCompleteListener( null );

		mHList.setOnItemSelectedListener( null );
		mHList.setOnItemClickedListener( null );

		if ( null != mIapDialog ) {
			mIapDialog.dismiss( false );
			mIapDialog = null;
		}

		super.onDeactivate();
	}

	@Override
	public void onConfigurationChanged( Configuration newConfig, Configuration oldConfig ) {

		// TODO: we really need this?
		// mImageManager.clearCache();

		if ( mIapDialog != null ) {
			mIapDialog.onConfigurationChanged( newConfig );
		}

		// TODO: we don't really need to update everything...
		// updateInstalledPacks( false );

		super.onConfigurationChanged( newConfig, oldConfig );
	}

	@Override
	protected void onDispose() {

		mHList.setAdapter( null );

		if ( null != mImageManager ) {
			mImageManager.clearCache();
			mImageManager.shutDownNow();
		}

		if ( mThumbBitmap != null && !mThumbBitmap.isRecycled() ) {
			mThumbBitmap.recycle();
		}
		mThumbBitmap = null;

		if ( null != updateArrowBitmap && !updateArrowBitmap.isRecycled() ) {
			updateArrowBitmap.recycle();
		}
		updateArrowBitmap = null;

		super.onDispose();
	}

	@Override
	protected void onGenerateResult() {
		mLogger.info( "onGenerateResult. isRendering: " + mIsRendering );
		if ( mIsRendering ) {
			GenerateResultTask task = new GenerateResultTask();
			task.execute();
		} else {
			onComplete( mPreview, mActions );
		}
	}

	@Override
	protected void onComplete( Bitmap bitmap, MoaActionList actions ) {

		if ( null != mRenderedEffect ) {
			Tracker.recordTag( mRenderedEffect + ": applied" );
			
			mTrackingAttributes.put( "Effect", mRenderedEffect );
		}

		if ( null != mRenderedPackName ) {
			try {
				if ( mRenderedPackName.equals( getContext().getBaseContext().getPackageName() ) ) {
					mRenderedPackName = "com.aviary.android.feather";
				}
			} catch ( Throwable t ) {
			}
			
			
			HashMap<String, String> attrs = new HashMap<String, String>();
			attrs.put( "Effects", mRenderedEffect );
			Tracker.recordTag( mRenderedPackName + ": applied", attrs );
			

			mTrackingAttributes.put( "Pack", mRenderedPackName );
		}

		super.onComplete( bitmap, actions );
	}

	@Override
	public boolean onBackPressed() {
		if ( backHandled() ) return true;
		return super.onBackPressed();
	}

	@Override
	public void onCancelled() {
		killCurrentTask();
		mIsRendering = false;
		super.onCancelled();
	}

	@Override
	public boolean getIsChanged() {
		return super.getIsChanged() || mIsRendering == true;
	}

	@Override
	public void onUpdate( PluginService service, Bundle delta ) {
		mLogger.info( "onUpdate" );

		if ( isActive() && mExternalPacksEnabled ) {
			if ( mUpdateDialog != null && mUpdateDialog.isShowing() ) {
				// another update alert is showing, skip new alerts
				return;
			}

			if ( validDelta( delta ) ) {

				DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

					@Override
					public void onClick( DialogInterface dialog, int which ) {
						updateInstalledPacks( true );
					}
				};

				mUpdateDialog = new AlertDialog.Builder( getContext().getBaseContext() ).setMessage( R.string.feather_filter_pack_updated )
						.setNeutralButton( android.R.string.ok, listener ).setCancelable( false ).create();

				mUpdateDialog.show();

			}
		}
	}

	@Override
	public void onExternalUpdate( PluginService service ) {
		mLogger.info( "onExternalUpdated" );

		// TODO: update the list adapter without resetting the view completely

		/*
		 * FramesListAdapter adapter = (FramesListAdapter) mHList.getAdapter();
		 * 
		 * FeatherExternalPack[] available = mPluginService.getAvailable( mPluginType );
		 * IPlugin plugin = PluginFactory.create( getContext().getBaseContext(),
		 * available[0], mPluginType );
		 * 
		 * final EffectPack effectPack = new EffectPack( "test", "Test", null, null,
		 * PluginService.ERROR_NONE, null, plugin, true );
		 * adapter.add( effectPack );
		 */
	}

	@Override
	public void onLoadComplete( final ImageView view, final Bitmap bitmap, int tag ) {

		if ( !isActive() ) return;

		if ( null != bitmap ) {
			// view.setImageBitmap( bitmap );
			view.post( new Runnable() {
				
				@Override
				public void run() {
					view.setImageBitmap( bitmap );
				}
			} );
		} else {
			view.setImageResource( R.drawable.aviary_ic_na );
		}
		//view.setVisibility( View.VISIBLE );
	}

	/**
	 * bundle contains a list of all updates applications. if one meets the criteria ( is
	 * a filter apk ) then return true
	 * 
	 * @param bundle
	 *            the bundle
	 * @return true if bundle contains a valid filter package
	 */
	private boolean validDelta( Bundle bundle ) {
		if ( null != bundle ) {
			if ( bundle.containsKey( "delta" ) ) {
				try {
					@SuppressWarnings ( "unchecked" )
					ArrayList<UpdateType> updates = (ArrayList<UpdateType>) bundle.getSerializable( "delta" );
					if ( null != updates ) {
						for ( UpdateType update : updates ) {
							if ( FeatherIntent.PluginType.isTypeOf( update.getPluginType(), mPluginType ) ) {
								return true;
							}

							if ( FeatherIntent.ACTION_PLUGIN_REMOVED.equals( update.getAction() ) ) {
								// if it's removed check against current listed packs
								if ( mInstalledPackages.contains( update.getPackageName() ) ) {
									return true;
								}
							}
						}
						return false;
					}
				} catch ( ClassCastException e ) {
					return true;
				}
			}
		}
		return true;
	}

	@Override
	public View makeView() {
		ImageViewTouch view = new ImageViewTouch( getContext().getBaseContext(), null );
		view.setBackgroundColor( 0x00000000 );
		view.setDoubleTapEnabled( false );
		view.setScaleEnabled( false );
		view.setScrollEnabled( false );
		view.setDisplayType( DisplayType.FIT_IF_BIGGER );
		view.setLayoutParams( new AviaryImageSwitcher.LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT ) );
		return view;
	}

	@Override
	protected View generateContentView( LayoutInflater inflater ) {
		return inflater.inflate( R.layout.aviary_content_effects, null );
	}

	@Override
	protected ViewGroup generateOptionView( LayoutInflater inflater, ViewGroup parent ) {
		return (ViewGroup) inflater.inflate( R.layout.aviary_panel_frames, parent, false );
	}

	protected Bitmap generateThumbnail( Bitmap input, final int width, final int height ) {
		return ThumbnailUtils.extractThumbnail( input, width, height );
	}

	/**
	 * Update the installed plugins
	 */
	protected void updateInstalledPacks( boolean firstTime ) {

		mIsAnimating = true;

		mLoader.setVisibility( View.VISIBLE );
		mHList.setVisibility( View.INVISIBLE );

		// now try to install every plugin...
		if ( firstTime ) {
			mHList.setAdapter( null );
		}
		new PluginInstallTask().execute( mPluginType );
	}

	/**
	 * Creates and returns the default adapter for the frames listview
	 * 
	 * @param context
	 * @param result
	 * @return
	 */
	protected BaseAdapter createListAdapter( Context context, List<EffectPack> result ) {
		
		return new ListAdapter( context, 
				R.layout.aviary_frame_item, 
				R.layout.aviary_frame_item_more, 
				R.layout.aviary_frame_item_external, 
				R.layout.aviary_frame_item_divider, result );
	}

	/**
	 * @param result		containing all the {@link EffectPack} items ( external, internal, dividers... )
	 * @param errors		contains all the error items
	 * @param totalCount	installed count
	 * @param externalCount	external items count ( get more and featured )
	 * @param firstValidIndex	the index of the first valid element
	 */
	private void onEffectListUpdated( List<EffectPack> result, List<EffectPackError> errors, int totalCount, int externalCount, int firstValidIndex ) {

		String iapPackageName = null;

		// check if the incoming options bundle has some instructions
		if( hasOptions() ) {
			Bundle options = getOptions();
			if( options.containsKey( FeatherIntent.OptionBundle.SHOW_IAP_DIALOG ) ) {
				iapPackageName = options.getString( FeatherIntent.OptionBundle.SHOW_IAP_DIALOG );
			}
			// ok, we display the IAP dialog only the first time
			options.remove( FeatherIntent.OptionBundle.SHOW_IAP_DIALOG );
		}
		
		final boolean willShowIapDialog = null != iapPackageName;

		// we had errors during installation
		if ( null != errors && errors.size() > 0 && !willShowIapDialog ) {
			mLogger.error( "errors: " + errors.size() );
			if ( !mUpdateErrorHandled ) {
				handleErrors( errors );
			}
		}

		mListFirstValidPosition = firstValidIndex > 0 ? firstValidIndex : 0;
		
		BaseAdapter adapter = createListAdapter( getContext().getBaseContext(), result );
		mHList.setAdapter( adapter );

		mLoader.setVisibility( View.INVISIBLE );
		
		Animation animation = new AlphaAnimation( 0, 1 );
		animation.setFillAfter( true );
		animation.setDuration( getContext().getBaseContext().getResources().getInteger( android.R.integer.config_longAnimTime ) );
		mHList.startAnimation( animation );
		mHList.setVisibility( View.VISIBLE );
		
		if ( mFirstTimeRenderer ) {
			mHList.setSelectedPosition( mSelectedPosition, false );
		} else if ( mSelectedPosition != mListFirstValidPosition && mListFirstValidPosition >= 0 ) {
			mHList.setSelectedPosition( mListFirstValidPosition, false );
		}

		// panel already rendered once, select the already selected item
		if ( mFirstTimeRenderer ) {
			onItemSelected( mHList, null, mSelectedPosition, -1 );
		}
		
		// scroll the list to 'n' position
		
		if( mExternalPacksEnabled && !mFirstTimeRenderer && externalCount > 0 ) {

			if( firstValidIndex > 2 ) {
				final int delta = (int) ( mCellWidth * ( firstValidIndex - 2.5 ) );
				mHList.post( new Runnable() {
					
					@Override
					public void run() {
						int clamped = mHList.computeScroll( delta );
						if( clamped != 0 ) {
							mHList.smoothScrollBy( delta, 500 );
						} else {
							mHList.scrollTo( delta );
						}
					}
				} );
			}
		}
		
		mFirstTimeRenderer = true;

		// show the alert only the first time!!
		if ( totalCount < 1 && mExternalPacksEnabled && mPluginType == FeatherIntent.PluginType.TYPE_BORDER ) {

			if ( !mPreferenceService.containsValue( this.getClass().getSimpleName() + "-install-first-time" ) ) {

				OnClickListener listener = new OnClickListener() {

					@Override
					public void onClick( DialogInterface dialog, int which ) {
						getContext().downloadPlugin( PluginService.FREE_BORDERS_PACKAGENAME, FeatherIntent.PluginType.TYPE_BORDER );
						dialog.dismiss();
					}
				};

				AlertDialog dialog = new AlertDialog.Builder( getContext().getBaseContext() )
						.setMessage( R.string.feather_borders_dialog_first_time ).setPositiveButton( android.R.string.ok, listener )
						.setNegativeButton( android.R.string.cancel, null ).create();

				mPreferenceService.putBoolean( this.getClass().getSimpleName() + "-install-first-time", true );

				dialog.show();
			}
		}

		// show the iap-dialog by default if the passed option Bundle
		// contains a valid package name to be shown and the current list
		// of errors is empty
		if( null != iapPackageName ) {
			displayIAPDialog( new IAPUpdater.Builder().setPlugin( iapPackageName, mPluginType ).build() );
		}
	}

	// ///////////////
	// IAP - Dialog //
	// ///////////////

	protected IAPDialog mIapDialog;

	private final void displayIAPDialog( IAPUpdater data ) {
		if ( null != mIapDialog ) {
			if ( mIapDialog.valid() ) {
				mIapDialog.update( data );
				setApplyEnabled( false );
				return;
			} else {
				mIapDialog.dismiss( false );
				mIapDialog = null;
			}
		}

		ViewGroup container = ( (FeatherContext) getContext().getBaseContext() ).activatePopupContainer();
		IAPDialog dialog = IAPDialog.create( container, data );
		if ( dialog != null ) {
			dialog.setOnCloseListener( new OnCloseListener() {
				@Override
				public void onClose() {
					removeIapDialog();
				}
			} );
		}
		mIapDialog = dialog;
		setApplyEnabled( false );
	}

	private boolean removeIapDialog() {
		setApplyEnabled( true );
		if ( null != mIapDialog ) {
			mIapDialog.dismiss( true );
			mIapDialog = null;
			return true;
		}
		return false;
	}

	@SuppressLint ( "UseSparseArrays" )
	private void handleErrors( List<EffectPackError> mErrors ) {

		if ( mErrors == null || mErrors.size() < 1 ) return;

		// first get the total number of errors
		HashMap<Integer, String> hash = new HashMap<Integer, String>();

		for ( EffectPackError item : mErrors ) {
			int error = item.mError;
			hash.put( error, (String) item.mPackageName );
		}

		// now manage the different cases
		// 1. just one type of error
		if ( hash.size() == 1 ) {

			// get the first error
			EffectPackError item = mErrors.get( 0 );

			if ( mErrors.size() == 1 ) {
				showUpdateAlert( item.mPackageName, item.mLabel, item.mError, item.mErrorMessage, false );
			} else {
				showUpdateAlertMultiplePlugins( item.mError, false );
			}
		} else {
			// 2. here we must handle different errors type
			showUpdateAlertMultipleItems( getContext().getBaseContext().getPackageName(), hash.keySet() );
		}

		mUpdateErrorHandled = true;
	}

	private void renderEffect( EffectPack item, int position ) {

		String label = (String) item.getItemAt( position );
		mLogger.log( "renderEffect: " + label );

		killCurrentTask();
		mCurrentTask = createRenderTask( position );
		mCurrentTask.execute( item );
	}

	protected RenderTask createRenderTask( int position ) {
		return new RenderTask( position );
	}

	boolean killCurrentTask() {
		if ( mCurrentTask != null ) {
			onProgressEnd();
			return mCurrentTask.cancel( true );
		}
		return false;
	}

	protected INativeFilter loadNativeFilter( final EffectPack pack, int position, final CharSequence label, boolean hires ) {
		BorderFilter filter = (BorderFilter) FilterLoaderFactory.get( Filters.BORDERS );
		filter.setBorderName( label );
		filter.setHiRes( hires );

		IPlugin plugin = pack.mPluginRef;
		if ( null != plugin ) {
			if ( plugin instanceof FramePlugin ) {
				filter.setSourceApp( ( (FramePlugin) plugin ).getSourceDir() );

				// border size
				int[] sizes = ( (FramePlugin) plugin ).listBordersWidths();
				position -= pack.getIndex();

				if ( null != sizes && sizes.length > ( position - 1 ) && position > 0 ) {
					int borderSize = sizes[position - 1];
					filter.setSize( (double) borderSize / 100.0 );
				}
			}
		}
		return filter;
	}

	boolean backHandled() {
		if ( mIsAnimating ) return true;
		if ( null != mIapDialog ) {
			removeIapDialog();
			return true;
		}
		killCurrentTask();
		return false;
	}

	static class ViewHolder {
		TextView text;
		ImageView image;
	}
	
	class ListAdapter extends BaseAdapterExtended<EffectPack> {
		
		static final int TYPE_INVALID = -1;
		static final int TYPE_NORMAL = 0;
		static final int TYPE_GETMORE = 1;
		static final int TYPE_EXTERNAL = 2;
		static final int TYPE_DIVIDER = 3;
		static final int TYPE_LEFT_DIVIDER = 4;
		static final int TYPE_RIGHT_DIVIDER = 5;
		
		Object mLock = new Object();
		LayoutInflater mInflater;
		List<EffectPack> mObjects;
		int mDefaultResId;
		int mMoreResId;
		int mExternalResId;
		int mDividerResId;
		int mCount = -1;
		BitmapDrawable mExternalFolderIcon;
		
		public ListAdapter ( Context context, int defaultResId, int moreResId, int externalResId, int dividerResId, List<EffectPack> items ) {
			super();
			mInflater = LayoutInflater.from( context );
			mObjects = items;
			mDefaultResId = defaultResId;
			mMoreResId = moreResId;
			mExternalResId = externalResId;
			mDividerResId = dividerResId;
			mExternalFolderIcon = getExternalBackgroundDrawable( context );
		}
		
		protected BitmapDrawable getExternalBackgroundDrawable( Context context ) {
			return (BitmapDrawable) context.getResources().getDrawable( R.drawable.aviary_frames_pack_background );
		}
		
		@Override
		public EffectPack getItem( int position ) {
			for ( int i = 0; i < mObjects.size(); i++ ) {
				EffectPack pack = mObjects.get( i );
				if ( null == pack ) continue;

				if ( position >= pack.index && position < pack.index + pack.size ) {
					return pack;
				}
			}
			return null;
		}
		
		@Override
		public int getCount() {
			if ( mCount == -1 ) {
				int total = 0; // first get more
				for ( EffectPack pack : mObjects ) {
					if ( null == pack ) {
						total++;
						continue;
					}
					pack.setIndex( total );
					total += pack.size;
				}
				// return total;
				mCount = total;
			}
			
			return mCount;
		}
		
		@Override
		public long getItemId( int position ) {
			return position;
		}
		
		@Override
		public int getViewTypeCount() {
			return 6;
		}
		
		@Override
		public int getItemViewType( int position ) {

			if ( !mExternalPacksEnabled ) return TYPE_NORMAL;
			
			if( position < 0 || position >= getCount() ) {
				return TYPE_INVALID;
			}

			EffectPack item = getItem( position );
			
			switch( item.mType ) {
				case EXTERNAL: return TYPE_EXTERNAL;
				case GET_MORE: return TYPE_GETMORE;
				case LEFT_DIVIDER: return TYPE_LEFT_DIVIDER;
				case RIGHT_DIVIDER: return TYPE_RIGHT_DIVIDER;
				case PACK_DIVIDER: return TYPE_DIVIDER;
				case INTERNAL:
				default:
					return TYPE_NORMAL;
			}
		}
		
		@Override
		public View getView( int position, View convertView, ViewGroup parent ) {
			
			ViewHolder holder;
			EffectPack item = getItem( position );
			final int type = getItemViewType( position );
			
			int layoutWidth = LayoutParams.WRAP_CONTENT;
			
			if( null == convertView ) {
				
				if( type == TYPE_NORMAL ) {
					convertView = mInflater.inflate( mDefaultResId, parent, false );
					layoutWidth = mCellWidth;
				} else if( type == TYPE_GETMORE ) {
					convertView = mInflater.inflate( mMoreResId, parent, false );
					layoutWidth = mCellWidth;
				} else if( type == TYPE_EXTERNAL ) {
					convertView = mInflater.inflate( mExternalResId, parent, false );
					layoutWidth = mCellWidth;					
				} else if( type == TYPE_DIVIDER ) {
					convertView = mInflater.inflate( mDividerResId, parent, false );
					layoutWidth = LayoutParams.WRAP_CONTENT;
				} else if( type == TYPE_LEFT_DIVIDER ) {
					convertView = mInflater.inflate( R.layout.aviary_thumb_divider_left, parent, false );
					layoutWidth = LayoutParams.WRAP_CONTENT;
				} else if( type == TYPE_RIGHT_DIVIDER ) {
					convertView = mInflater.inflate( R.layout.aviary_thumb_divider_right, parent, false );
					layoutWidth = LayoutParams.WRAP_CONTENT;
				}
				
				convertView.setLayoutParams( new LayoutParams( layoutWidth, LayoutParams.MATCH_PARENT ) );
				holder = new ViewHolder();
				
				holder.image = (ImageView) convertView.findViewById( R.id.aviary_image );
				holder.text = (TextView) convertView.findViewById( R.id.aviary_text );
				
				if( type != TYPE_DIVIDER && holder.image != null ) {
					LayoutParams params = holder.image.getLayoutParams();
					params.height = mThumbSize;
					params.width = mThumbSize;
					holder.image.setLayoutParams( params );
				}
				
				convertView.setTag( holder );
			} else {
				holder = (ViewHolder) convertView.getTag();
			}
			
			Callable<Bitmap> executor;

			if ( type == TYPE_NORMAL && null != item ) {
				holder.text.setText( item.getLabelAt( position ) );

				final String effectName = (String) item.getItemAt( position );
				executor = createContentCallable( item, position, effectName );

				mImageManager.execute( executor, position + "/" + effectName, holder.image, TAG_INTERNAL_VIEW, Priority.HIGH );
				
			} else if( type == TYPE_GETMORE || type == TYPE_EXTERNAL ) {
				
				if( type == TYPE_EXTERNAL ) {
					ExternalPlugin plugin = (ExternalPlugin) item.mPluginRef;
					
					holder.image.setImageDrawable( mExternalFolderIcon );
					holder.text.setText( item.mTitle );
					
					if ( null != plugin ) {
						executor = createExternalContentCallable( plugin.getIconUrl() );
						mImageManager.execute( executor, plugin.getIconUrl(), holder.image, TAG_EXTERNAL_VIEW, Priority.LOW );
					}				
				}
				
			} else if( type == TYPE_DIVIDER ) {
				Drawable drawable = holder.image.getDrawable();

				if ( drawable instanceof PluginDividerDrawable ) {
					( (PluginDividerDrawable) drawable ).setTitle( item.mTitle.toString() );
				} else {
					PluginDividerDrawable d = new PluginDividerDrawable( getContext().getBaseContext(), R.attr.aviaryEffectThumbDividerTextStyle, item.mTitle.toString() );
					holder.image.setImageDrawable( d );
				}				
			}
			
			return convertView;
		}
		
		protected Callable<Bitmap> createContentCallable( final EffectPack item, int position, final String effectName ) {
			return new BorderThumbnailCallable( mCacheService, (InternalPlugin) item.mPluginRef, effectName, mThumbBitmap, mThumbSize );
		}
		
		protected Callable<Bitmap> createExternalContentCallable( final String iconUrl ) {
			return new ExternalFramesThumbnailCallable( iconUrl, mCacheService, mExternalFolderIcon, getContext().getBaseContext().getResources(), R.drawable.aviary_ic_na );
		}		
	}


	// ////////////////////////
	// OnItemClickedListener //
	// ////////////////////////

	@Override
	public boolean onItemClick( AdapterView<?> parent, View view, int position, long id ) {

		mLogger.info( "onItemClick: " + position );

		if ( isActive() ) {
			if ( mHList.getAdapter() == null ) return false;
			int viewType = mHList.getAdapter().getItemViewType( position );

			if ( viewType == ListAdapter.TYPE_NORMAL ) {

				EffectPack item = (EffectPack) mHList.getAdapter().getItem( position );

				if ( item != null && item.mStatus == PluginService.ERROR_NONE ) {
					return true;
				} else {
					showUpdateAlert( item.mPackageName, item.mTitle, item.mStatus, item.mError, true );
					return false;
				}

			} else if ( viewType == ListAdapter.TYPE_GETMORE ) {

				if ( position == 0 ) {
					Tracker.recordTag( "LeftGetMoreEffects : Selected" );
				} else {
					Tracker.recordTag( "RightGetMoreEffects : Selected" );
				}
				getContext().searchPlugin( mPluginType );
				return false;

			} else if ( viewType == ListAdapter.TYPE_EXTERNAL ) {
				EffectPack item = (EffectPack) mHList.getAdapter().getItem( position );
				if ( null != item ) {

					if ( android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.FROYO
							&& SystemUtils.getApplicationTotalMemory() >= Constants.APP_MEMORY_SMALL ) {
						ExternalPlugin externalPlugin = (ExternalPlugin) item.mPluginRef;

						if ( externalPlugin == null ) return false;
						displayIAPDialog( new IAPUpdater.Builder().setPlugin( externalPlugin ).build() );

					} else {
						Tracker.recordTag( "Unpurchased(" + item.mPackageName + ") : StoreButtonClicked" );
						getContext().downloadPlugin( item.mPackageName.toString(), mPluginType );
					}
				}
			}
		}
		return false;
	}

	// /////////////////////////
	// OnItemSelectedListener //
	// /////////////////////////

	@Override
	public void onItemSelected( AdapterView<?> parent, View view, int position, long id ) {
		mLogger.info( "onItemSelected: " + position );

		mSelectedPosition = position;

		if ( isActive() ) {

			if ( mHList.getAdapter() == null ) return;
			int viewType = mHList.getAdapter().getItemViewType( position );

			if ( viewType == ListAdapter.TYPE_NORMAL ) {

				EffectPack item = (EffectPack) mHList.getAdapter().getItem( position );

				if ( item == null ) return;

				if ( item.mStatus == PluginService.ERROR_NONE ) {
					// so we assume the view is already selected and so let's selected the
					// "original" effect by default
					if ( item.mType == EffectPack.EffectPackType.INTERNAL ) {
						renderEffect( item, position );
					}
				}
			}
		}
	}

	@Override
	public void onNothingSelected( AdapterView<?> parent ) {
		mLogger.info( "onNothingSelected" );

		if ( parent.getAdapter() != null ) {
			EffectPack item = (EffectPack) parent.getAdapter().getItem( mListFirstValidPosition );
			if ( null != item && item.size > 0 ) {
				renderEffect( item, mListFirstValidPosition );
			} else {
				return;
			}

			if ( null != getHandler() ) {
				getHandler().postDelayed( new Runnable() {

					@Override
					public void run() {
						mHList.setSelectedPosition( mListFirstValidPosition, false );
					}
				}, 100 );
			}
		}
	}

	static class ExternalFramesThumbnailCallable implements Callable<Bitmap> {

		String mUri;
		int mFallbackResId;
		BitmapDrawable mFolder;
		SoftReference<ImageCacheService> cacheServiceRef;
		SoftReference<Resources> resourcesRef;

		public ExternalFramesThumbnailCallable ( final String uri, ImageCacheService cacheService,
				final BitmapDrawable folderBackground, Resources resources, final int fallbackResId ) {
			mUri = uri;
			mFallbackResId = fallbackResId;
			cacheServiceRef = new SoftReference<ImageCacheService>( cacheService );
			resourcesRef = new SoftReference<Resources>( resources );
			mFolder = folderBackground;
		}

		@Override
		public Bitmap call() throws Exception {

			if ( mUri == null || mUri.length() < 1 ) {
				return mFolder.getBitmap();
			}

			Bitmap bitmap = null;
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inPreferredConfig = Config.ARGB_8888;

			ImageCacheService cache = cacheServiceRef.get();
			if ( null == cache ) {
				return mFolder.getBitmap();
			}

			SimpleCachedRemoteBitmap request;

			try {
				request = cache.requestRemoteBitmap( PluginService.CONTENT_DEFAULT_URL + "/" + mUri );
				bitmap = request.getBitmap( options );
			} catch ( Exception e ) {
				e.printStackTrace();
			}

			// fallback icon
			if ( null == bitmap ) {
				if ( null != resourcesRef.get() ) {
					try {
						bitmap = BitmapFactory.decodeResource( resourcesRef.get(), mFallbackResId );
					} catch ( Throwable t ) {
					}
				}
			}

			if ( null != bitmap ) {
				Bitmap result = generateBitmap( bitmap );
				if ( result != bitmap ) {
					bitmap.recycle();
					bitmap = null;
					bitmap = result;
				}
				return bitmap;
			} else {
				return mFolder.getBitmap();
			}
		}

		Bitmap generateBitmap( Bitmap icon ) {
			return icon;
		}
	}

	static class BorderThumbnailCallable implements Callable<Bitmap> {

		InternalPlugin mPlugin;
		Bitmap mBitmap;
		int mFinalSize;
		String mUrl;
		SoftReference<ImageCacheService> cacheRef;

		public BorderThumbnailCallable ( ImageCacheService cacheService, final InternalPlugin plugin, final String srcUrl, final Bitmap bitmap,
				final int size ) {
			mFinalSize = size;
			mUrl = srcUrl;
			mPlugin = plugin;
			cacheRef = new SoftReference<ImageCacheService>( cacheService );
			mBitmap = bitmap;
		}

		@Override
		public Bitmap call() throws Exception {

			ImageCacheService cache = cacheRef.get();
			Bitmap bitmap = mBitmap;

			if ( null != cache ) {
				bitmap = cache.getBitmap( mPlugin.getType() + "-" + mUrl, mThumbnailOptions );
				if ( null != bitmap ) return bitmap;
			}
			
			try {
				bitmap = ImageLoader.getPluginItemBitmap( mPlugin, mUrl, null, mFinalSize, mFinalSize );
			} catch ( Exception e ) {
				return null;
			}

			if ( null != bitmap ) {
				MoaActionList actions = actionsForRoundedThumbnail( true, null );
				
				MoaResult mResult = NativeFilterProxy.prepareActions( actions, bitmap, null, 1, 1 );
				mResult.execute();
				bitmap = mResult.outputBitmap;
				
				//bitmap.recycle();

				if ( null != bitmap && null != cache ) {
					cache.putBitmap( FeatherIntent.PluginType.TYPE_BORDER + "-" + mUrl, bitmap );
				}
				return bitmap;
			}

			return null;
		}
		
		MoaActionList actionsForRoundedThumbnail( final boolean isValid, INativeFilter filter ) {
			
			MoaActionList actions = MoaActionFactory.actionList();
			if ( null != filter ) {
				actions.addAll( filter.getActions() );
			}

			MoaAction action = MoaActionFactory.action( "ext-roundedborders" );
			action.setValue( "padding", 0 );
			action.setValue( "roundPx", 0 );
			action.setValue( "strokeColor", 0xff000000 );
			action.setValue( "strokeWeight", 1 );

			if ( !isValid ) {
				action.setValue( "overlaycolor", 0x99000000 );
			}
			
			actions.add( action );
			return actions;
		}		
	}

	protected CharSequence[] getOptionalEffectsValues() {
		return new CharSequence[] { "original" };
	}

	protected CharSequence[] getOptionalEffectsLabels() {
		return new CharSequence[] { mConfigService.getString( R.string.feather_original ) };
	}

	/**
	 * Install all the
	 * 
	 * @author alessandro
	 */
	class PluginInstallTask extends AsyncTask<Integer, Void, List<EffectPack>> {

		List<EffectPackError> mErrors;
		private int mInstalledCount = 0;
		private int mExternalCount = 0;
		private int mFirstValidIndex = -1;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			mErrors = Collections.synchronizedList( new ArrayList<EffectPackError>() );
			mImageManager.clearCache();
		}

		@Override
		protected List<EffectPack> doInBackground( Integer... params ) {

			// List of installed plugins available on the device
			final int pluginType = params[0];
			FeatherInternalPack installedPacks[];
			FeatherPack availablePacks[];

			if ( mExternalPacksEnabled ) {
				int count = 0;

				while ( !mPluginService.isUpdated() ) {
					try {
						Thread.sleep( 50 );
					} catch ( InterruptedException e ) {
						e.printStackTrace();
					}
					mLogger.log( "waiting for plugin service" );
				}

				// if the external packs are not available
				// then wait for maximum 500ms
				count = 0;
				while ( !mPluginService.isExternalUpdated() ) {
					count++;
					SystemUtils.trySleep( 10 );
					if ( count > 50 ) {
						break;
					}
				}
				installedPacks = mPluginService.getInstalled( getContext().getBaseContext(), pluginType );
				availablePacks = mPluginService.getAvailable( pluginType );
			} else {
				// external packs are not enabled, so just return the default ones
				if ( pluginType == FeatherIntent.PluginType.TYPE_FILTER ) {
					installedPacks = new FeatherInternalPack[] { FeatherInternalPack.getDefault( getContext().getBaseContext() ) };
				} else {
					installedPacks = new FeatherInternalPack[] {};
				}
				availablePacks = new FeatherExternalPack[] {};
			}

			// number of installed packs
			mInstalledCount = 0;
			
			// number of external available items ( get more included )
			mExternalCount = 0;

			List<EffectPack> result = Collections.synchronizedList( new ArrayList<EffectPack>() );
			mInstalledPackages.clear();

			// left "get more"
			if ( mExternalPacksEnabled ) {
				if ( mPluginType == FeatherIntent.PluginType.TYPE_BORDER ) {
					mShowGetMoreView = !( ( installedPacks.length == 0 && availablePacks.length == 1 ) || ( installedPacks.length == 1 && availablePacks.length == 0 ) );
				}
				
				if ( mShowGetMoreView ) {
					mExternalCount++;
					result.add( new EffectPack( EffectPack.EffectPackType.GET_MORE ) );
				}
			}
			
			int index = 0;
			int pack_index = 0;
			
			// featured external packs
			if ( mExternalPacksEnabled ) {
				int size = Math.min( mFeaturedCount, availablePacks.length );
				if( size > 0 ) {
					for( int i = size - 1; i >= 0; i-- ) {
						FeatherPack pack = availablePacks[i];
						ExternalPlugin plugin = (ExternalPlugin) PluginFactory.create( getContext().getBaseContext(), pack, mPluginType );
						final CharSequence packagename = plugin.getPackageName();
						final CharSequence label = plugin.getPackageLabel();
						final EffectPack effectPack = new EffectPack( EffectPack.EffectPackType.EXTERNAL, packagename, label, null, null, PluginService.ERROR_NONE, null, plugin );
						result.add( effectPack );
						mExternalCount++;
						index++;
						
					}
				}
			}
			
			if( mExternalCount > 0 ) {
				result.add( new EffectPack( EffectPack.EffectPackType.RIGHT_DIVIDER ) );
			}
			
			if( !isActive() ) return result;

			index = 0;
			
			// device installed packs
			for ( FeatherPack pack : installedPacks ) {
				if ( pack instanceof FeatherInternalPack ) {
					InternalPlugin plugin = (InternalPlugin) PluginFactory.create( getContext().getBaseContext(), pack, mPluginType );
					final CharSequence packagename = plugin.getPackageName();
					final CharSequence label = plugin.getPackageLabel();

					int status = PluginService.ERROR_NONE;
					String errorMessage = null;

					List<Pair<String, String>> pluginItems = null;
					List<Long> pluginIds = null;

					// install process
					if ( plugin.installed() ) { // ok, the plugin is on the device
						mLogger.info( "**** " + packagename + " ****" );
						if ( plugin instanceof ICDSPlugin ) {
							try {
								if ( !( (ICDSPlugin) plugin ).installAndLoad( getContext().getBaseContext(), mPluginService ) ) {
									status = PluginService.ERROR_INSTALL;
								} else {
									status = PluginService.ERROR_NONE;
								}
							} catch ( PluginException e ) {
								e.printStackTrace();
								status = e.getErrorCode();
								errorMessage = e.getMessage();
							} catch ( Throwable t ) {
								t.printStackTrace();
								status = PluginService.ERROR_UNKNOWN;
								errorMessage = t.getMessage();
							}
						}
					} else {
						status = PluginService.ERROR_PLUGIN_NOT_FOUND;
						errorMessage = "Plugin not installed";
					}

					// Some errors loading the plugin!
					if ( status != PluginService.ERROR_NONE ) {

						// if the plugin is not the default one then display the error
						if ( !getContext().getBaseContext().getPackageName().equals( plugin.getPackageName() ) ) {
							pluginItems = new ArrayList<Pair<String, String>>();
							pluginItems.add( Pair.create( "-1", (String) plugin.getPackageLabel() ) );
							pluginIds = new ArrayList<Long>();
							pluginIds.add( -1L );

							EffectPackError error = new EffectPackError( packagename, label, status, errorMessage );
							mErrors.add( error );
						}
					} else {
						pluginItems = loadPluginItems( plugin );
						pluginIds = loadPluginIds( plugin );
						
						if ( index == 0 ) {
							CharSequence[] f = getOptionalEffectsValues();
							CharSequence[] n = getOptionalEffectsLabels();
							if ( null != f && null != n && f.length == n.length ) {

								for ( int i = 0; i < f.length; i++ ) {
									pluginItems.add( 0, Pair.create( (String) f[i], (String) n[i] ) );
									if ( null != pluginIds ) pluginIds.add( 0, -1L );
								}
							}
						}
						mInstalledCount++;
					}

					if ( null != pluginItems && null != pluginIds ) {
						final EffectPack effectPack = new EffectPack( EffectPack.EffectPackType.INTERNAL, packagename, label, pluginItems, pluginIds, status, errorMessage, plugin );
						mInstalledPackages.add( packagename.toString() );

						if ( pack_index > 0 ) {
							// first add the label item
							result.add( new EffectPack( EffectPack.EffectPackType.PACK_DIVIDER, label.toString() ) );
						}

						// then add the item pack
						result.add( effectPack );
						
						if( status == PluginService.ERROR_NONE && mFirstValidIndex == -1 ) {
							mFirstValidIndex = result.size() - 1;
						}
						pack_index++;
					}
					index++;
				}
				
				// just a check...
				if( !isActive() ) break;
			}


			// right "get more"
			if ( mInstalledPackages != null && mInstalledCount > 0 ) {
				if ( mExternalPacksEnabled && mShowGetMoreView ) {
					result.add( new EffectPack( EffectPack.EffectPackType.LEFT_DIVIDER ) );
					result.add( new EffectPack( EffectPack.EffectPackType.GET_MORE ) );
				}
			}

			return result;
		}

		@Override
		protected void onPostExecute( List<EffectPack> result ) {
			super.onPostExecute( result );

			onEffectListUpdated( result, mErrors, mInstalledCount, mExternalCount, mFirstValidIndex );
			mIsAnimating = false;
		}
	}

	protected List<Pair<String, String>> loadPluginItems( InternalPlugin plugin ) {
		List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();
		if ( plugin instanceof FramePlugin ) {
			String[] items = ( (FramePlugin) plugin ).listBorders();
			for ( int i = 0; i < items.length; i++ ) {
				String label = (String) plugin.getResourceLabel( items[i] );
				result.add( Pair.create( items[i], label ) );
			}
		}
		return result;
	}

	protected List<Long> loadPluginIds( InternalPlugin plugin ) {
		if ( plugin instanceof FramePlugin ) {
			int size = ( (FramePlugin) plugin ).size();
			List<Long> result = new ArrayList<Long>( size );
			for( int i = 0; i < size; i++ ) {
				result.add( (long) i );
			}
		}
		return new ArrayList<Long>();
	}

	class EffectPackError {

		CharSequence mPackageName;
		CharSequence mLabel;
		int mError;
		String mErrorMessage;

		public EffectPackError ( CharSequence packagename, CharSequence label, int error, String errorString ) {
			mPackageName = packagename;
			mLabel = label;
			mError = error;
			mErrorMessage = errorString;
		}
	}

	static class EffectPack {
		
		static enum EffectPackType {
			INTERNAL, EXTERNAL, PACK_DIVIDER, LEFT_DIVIDER, RIGHT_DIVIDER, GET_MORE
		};

		CharSequence mPackageName;
		List<Pair<String, String>> mValues;
		List<Long> mIds;
		CharSequence mTitle;
		int mStatus;
		String mError;
		IPlugin mPluginRef;
		int size = 0;
		int index = 0;
		EffectPackType mType;
		
		public EffectPack( EffectPackType type ) {
			mType = type;
			size = 1;
			mStatus = PluginService.ERROR_NONE;
		}

		public EffectPack ( EffectPackType type, final String label ) {
			this( type );
			mTitle = label;
		}

		public EffectPack ( EffectPackType type, CharSequence packageName, CharSequence pakageTitle, List<Pair<String, String>> values, List<Long> ids,
				int status, String errorMsg, IPlugin plugin ) {
			this( type );
			mPackageName = packageName;
			mStatus = status;
			mTitle = pakageTitle;
			mPluginRef = plugin;
			mValues = values;
			mIds = ids;
			mError = errorMsg;

			if ( null != values ) {
				size = values.size();
			} else {
				size = 1;
			}
		}

		public int getCount() {
			return size;
		}

		public int getIndex() {
			return index;
		}

		public void setIndex( int value ) {
			index = value;
		}

		public CharSequence getItemAt( int position ) {
			return mValues.get( position - index ).first;
		}

		public long getItemIdAt( int position ) {
			if ( null != mIds ) return mIds.get( position - index );
			return -1;
		}

		public CharSequence getLabelAt( int position ) {
			return mValues.get( position - index ).second;
		}

		public int getItemIndex( int position ) {
			return position - index;
		}

		@Override
		protected void finalize() throws Throwable {
			mPluginRef = null;
			super.finalize();
		}
	}

	/**
	 * Render the selected effect
	 */
	protected class RenderTask extends UserTask<EffectPack, Bitmap, Bitmap> implements OnCancelListener {

		int mPosition;
		String mError;
		MoaResult mMoaMainExecutor;
		MoaResult mMoaPreviewExecutor;

		/**
		 * Instantiates a new render task.
		 * 
		 * @param tag
		 */
		public RenderTask ( final int position ) {
			mPosition = position;
		}

		@Override
		public void onPreExecute() {
			super.onPreExecute();
			onProgressStart();
		}

		private INativeFilter initFilter( EffectPack pack, int position, String label ) {
			final INativeFilter filter;

			try {
				filter = loadNativeFilter( pack, position, label, true );
			} catch ( Throwable t ) {
				t.printStackTrace();
				return null;
			}

			mActions = (MoaActionList) filter.getActions().clone();

			if ( filter instanceof BorderFilter ) ( (BorderFilter) filter ).setHiRes( false );

			try {
				mMoaMainExecutor = filter.prepare( mBitmap, mPreview, 1, 1 );
			} catch ( JSONException e ) {
				e.printStackTrace();
				mMoaMainExecutor = null;
				return null;
			}
			return filter;
		}

		protected MoaResult initPreview( INativeFilter filter ) {
			return null;
		}

		/**
		 * Process the preview bitmap while executing in background the full image
		 */
		public void doSmallPreviewInBackground() {
			// rendering the small preview
			if ( mMoaPreviewExecutor != null ) {

				mMoaPreviewExecutor.execute();
				if ( mMoaPreviewExecutor.active > 0 ) {
					publishProgress( mMoaPreviewExecutor.outputBitmap );
				}
			}
		}

		public void doFullPreviewInBackground( final String effectName ) {
			// rendering the full preview
			mMoaMainExecutor.execute();
		}

		@Override
		public Bitmap doInBackground( final EffectPack... params ) {

			if ( isCancelled() ) return null;

			final EffectPack pack = params[0];
			// mRenderedEffect = (String) pack.getLabelAt( mPosition );
			mRenderedEffect = (String) pack.getItemAt( mPosition );
			mRenderedPackName = (String) pack.mPackageName;

			final String mEffect = (String) pack.getItemAt( mPosition );
			INativeFilter filter = initFilter( pack, mPosition, mEffect );
			if ( null != filter ) {
				mMoaPreviewExecutor = initPreview( filter );
			} else {
				return null;
			}

			mIsRendering = true;

			// render small preview if required
			doSmallPreviewInBackground();

			if ( isCancelled() ) return null;

			// rendering the full preview
			try {
				doFullPreviewInBackground( mEffect );
			} catch ( Exception exception ) {
				mError = exception.getMessage();
				exception.printStackTrace();
				return null;
			}

			if ( !isCancelled() ) {
				return mMoaMainExecutor.outputBitmap;
			} else {
				return null;
			}
		}

		@Override
		public void onProgressUpdate( Bitmap... values ) {
			super.onProgressUpdate( values );

			// we're using a FakeBitmapDrawable just to upscale the small bitmap
			// to be rendered the same way as the full image
			final Bitmap preview = values[0];
			if ( null != preview ) {
				final FakeBitmapDrawable drawable = new FakeBitmapDrawable( preview, mBitmap.getWidth(), mBitmap.getHeight() );
				mImageSwitcher.setImageDrawable( drawable, null );
			}
		}

		@Override
		public void onPostExecute( final Bitmap result ) {
			super.onPostExecute( result );

			if ( !isActive() ) return;

			mPreview = result;

			if ( result == null || mMoaMainExecutor == null || mMoaMainExecutor.active == 0 ) {

				onRestoreOriginalBitmap();

				if ( mError != null ) {
					onGenericError( mError, android.R.string.ok, null );
				}

				setIsChanged( false );
				mActions = null;

			} else {
				onApplyNewBitmap( result );
				setIsChanged( true );
				
				if( null != mRenderedEffect && null != mRenderedPackName ) {
					HashMap<String, String> attrs = new HashMap<String, String>();
					attrs.put( "Pack", mRenderedPackName );
					attrs.put( "Effect", mRenderedEffect );
					Tracker.recordTag( "EffectPreview: selected", attrs );
				}
			}

			onProgressEnd();

			mIsRendering = false;
			mCurrentTask = null;
		}

		protected void onApplyNewBitmap( final Bitmap result ) {
			if ( SystemUtils.isHoneyComb() ) {
				Moa.notifyPixelsChanged( result );
			}
			mImageSwitcher.setImageBitmap( result, null );
		}

		protected void onRestoreOriginalBitmap() {
			// restore the original bitmap...
			mImageSwitcher.setImageBitmap( mBitmap, null );
		}

		@Override
		public void onCancelled() {
			super.onCancelled();

			if ( mMoaMainExecutor != null ) {
				mMoaMainExecutor.cancel();
			}

			if ( mMoaPreviewExecutor != null ) {
				mMoaPreviewExecutor.cancel();
			}

			mIsRendering = false;
		}

		@Override
		public void onCancel( DialogInterface dialog ) {
			cancel( true );
		}
	}

	/**
	 * Used to generate the Bitmap result. If user clicks on the "Apply" button when an
	 * effect is still rendering, then starts this
	 * task.
	 */
	class GenerateResultTask extends AsyncTask<Void, Void, Void> {

		ProgressDialog mProgress = new ProgressDialog( getContext().getBaseContext() );

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			mProgress.setTitle( getContext().getBaseContext().getString( R.string.feather_loading_title ) );
			mProgress.setMessage( getContext().getBaseContext().getString( R.string.feather_effect_loading_message ) );
			mProgress.setIndeterminate( true );
			mProgress.setCancelable( false );
			mProgress.show();
		}

		@Override
		protected Void doInBackground( Void... params ) {

			mLogger.info( "GenerateResultTask::doInBackground", mIsRendering );

			while ( mIsRendering ) {
				mLogger.log( "waiting...." );
			}

			return null;
		}

		@Override
		protected void onPostExecute( Void result ) {
			super.onPostExecute( result );

			mLogger.info( "GenerateResultTask::onPostExecute" );

			if ( getContext().getBaseActivity().isFinishing() ) return;
			if ( mProgress.isShowing() ) mProgress.dismiss();

			onComplete( mPreview, mActions );
		}
	}

}
