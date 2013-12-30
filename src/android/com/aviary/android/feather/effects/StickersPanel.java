package com.aviary.android.feather.effects;

import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;
import it.sephiroth.android.library.widget.BaseAdapterExtended;
import it.sephiroth.android.library.widget.HorizontalListView.OnItemDragListener;
import it.sephiroth.android.library.widget.HorizontalVariableListView;
import it.sephiroth.android.library.widget.HorizontalVariableListView.OnItemClickedListener;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.aviary.android.feather.AviaryMainController.FeatherContext;
import com.aviary.android.feather.R;
import com.aviary.android.feather.async_tasks.AsyncImageManager;
import com.aviary.android.feather.async_tasks.AsyncImageManager.OnImageLoadListener;
import com.aviary.android.feather.async_tasks.AsyncImageManager.Priority;
import com.aviary.android.feather.effects.BordersPanel.ViewHolder;
import com.aviary.android.feather.effects.SimpleStatusMachine.OnStatusChangeListener;
import com.aviary.android.feather.effects.StickersPanel.StickerEffectPack.StickerEffectPackType;
import com.aviary.android.feather.headless.moa.MoaActionFactory;
import com.aviary.android.feather.headless.moa.MoaActionList;
import com.aviary.android.feather.headless.utils.IOUtils;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.content.FeatherIntent;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.StickerFilter;
import com.aviary.android.feather.library.graphics.drawable.FeatherDrawable;
import com.aviary.android.feather.library.graphics.drawable.StickerDrawable;
import com.aviary.android.feather.library.plugins.FeatherExternalPack;
import com.aviary.android.feather.library.plugins.FeatherInternalPack;
import com.aviary.android.feather.library.plugins.FeatherPack;
import com.aviary.android.feather.library.plugins.PluginFactory;
import com.aviary.android.feather.library.plugins.PluginFactory.ExternalPlugin;
import com.aviary.android.feather.library.plugins.PluginFactory.IPlugin;
import com.aviary.android.feather.library.plugins.PluginFactory.InternalPlugin;
import com.aviary.android.feather.library.plugins.PluginFactory.StickerPlugin;
import com.aviary.android.feather.library.plugins.UpdateType;
import com.aviary.android.feather.library.services.ConfigService;
import com.aviary.android.feather.library.services.DragControllerService;
import com.aviary.android.feather.library.services.DragControllerService.DragListener;
import com.aviary.android.feather.library.services.DragControllerService.DragSource;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.ImageCacheService;
import com.aviary.android.feather.library.services.ImageCacheService.SimpleCachedRemoteBitmap;
import com.aviary.android.feather.library.services.LocalDataService;
import com.aviary.android.feather.library.services.PluginService;
import com.aviary.android.feather.library.services.PluginService.OnUpdateListener;
import com.aviary.android.feather.library.services.PluginService.StickerType;
import com.aviary.android.feather.library.services.PreferenceService;
import com.aviary.android.feather.library.services.drag.DragView;
import com.aviary.android.feather.library.services.drag.DropTarget;
import com.aviary.android.feather.library.services.drag.DropTarget.DropTargetListener;
import com.aviary.android.feather.library.tracking.Tracker;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.utils.ImageLoader;
import com.aviary.android.feather.library.utils.MatrixUtils;
import com.aviary.android.feather.library.utils.PackageManagerUtils;
import com.aviary.android.feather.library.utils.SystemUtils;
import com.aviary.android.feather.library.utils.UIConfiguration;
import com.aviary.android.feather.widget.DrawableHighlightView;
import com.aviary.android.feather.widget.DrawableHighlightView.OnDeleteClickListener;
import com.aviary.android.feather.widget.EffectThumbLayout;
import com.aviary.android.feather.widget.IAPDialog;
import com.aviary.android.feather.widget.IAPDialog.IAPUpdater;
import com.aviary.android.feather.widget.IAPDialog.OnCloseListener;
import com.aviary.android.feather.widget.ImageViewDrawableOverlay;

public class StickersPanel extends AbstractContentPanel implements OnUpdateListener, OnStatusChangeListener, OnItemClickedListener,
		DragListener, DragSource, DropTargetListener, OnItemSelectedListener, OnImageLoadListener {

	private static final int STATUS_NULL = SimpleStatusMachine.INVALID_STATUS;
	private static final int STATUS_PACKS = 1;
	private static final int STATUS_STICKERS = 2;
	private static final int STATUS_IAP = 3;

	/** panel's status */
	private SimpleStatusMachine mStatus;

	/** This panel is executing some animations */
	private volatile boolean mIsAnimating;

	/** horizontal listview for stickers packs */
	private HorizontalVariableListView mListPacks;

	/** horizontal listview for stickers items */
	private HorizontalVariableListView mListStickers;

	/** view flipper for switching between lists */
	private ViewFlipper mViewFlipper;

	/** external packs availability */
	private boolean mExternalPacksEnabled;

	/** dialog used to alert the user about changes in the installed plugins */
	private AlertDialog mUpdateDialog;

	/** thumbnail cache manager */
	private AsyncImageManager mImageManager;

	/** canvas used to draw stickers */
	private Canvas mCanvas;

	private int mPackCellWidth;
	private int mStickerCellWidth;

	/** installed plugins */
	private List<String> mInstalledPackages;

	/** required services */
	private PluginService mPluginService;
	private ConfigService mConfigService;
	private PreferenceService mPreferenceService;
	private ImageCacheService mCacheService;
	private DragControllerService mDragControllerService;

	/** max number of featured elements to display */
	private int mFeaturedCount;

	/** iap dialog for inline previews */
	private IAPDialog mIapDialog;

	/** the current selected sticker pack */
	private IPlugin mPlugin;

	private MoaActionList mActionList;
	private StickerFilter mCurrentFilter;

	private int mPackThumbSize;
	private int mStickerThumbSize;

	private boolean mFirstTimeRenderer;

	public StickersPanel ( IAviaryController context, ToolEntry entry ) {
		super( context, entry );
	}

	@Override
	public void onCreate( Bitmap bitmap, Bundle options ) {
		super.onCreate( bitmap, options );

		mStatus = new SimpleStatusMachine();


		// init layout components
		mListPacks = (HorizontalVariableListView) getOptionView().findViewById( R.id.aviary_list_packs );
		mListStickers = (HorizontalVariableListView) getOptionView().findViewById( R.id.aviary_list_stickers );
		mViewFlipper = (ViewFlipper) getOptionView().findViewById( R.id.aviary_flipper );
		mImageView = (ImageViewDrawableOverlay) getContentView().findViewById( R.id.aviary_overlay );

		// init services
		mPluginService = getContext().getService( PluginService.class );
		mConfigService = getContext().getService( ConfigService.class );
		mPreferenceService = getContext().getService( PreferenceService.class );
		mCacheService = getContext().getService( ImageCacheService.class );
		
		LocalDataService dataService = getContext().getService( LocalDataService.class );
		// determine if the external packs are enabled
		mExternalPacksEnabled = dataService.getExternalPacksEnabled( FeatherIntent.PluginType.TYPE_STICKER );

		// TODO: only for testing
		// mCacheService.deleteCache();

		// setup the main horizontal listview
		mListPacks.setOverScrollMode( HorizontalVariableListView.OVER_SCROLL_ALWAYS );

		// setup the stickers listview
		mListStickers.setOverScrollMode( HorizontalVariableListView.OVER_SCROLL_ALWAYS );

		// setup the main imageview
		( (ImageViewDrawableOverlay) mImageView ).setDisplayType( DisplayType.FIT_IF_BIGGER );
		( (ImageViewDrawableOverlay) mImageView ).setForceSingleSelection( false );
		( (ImageViewDrawableOverlay) mImageView ).setDropTargetListener( this );
		( (ImageViewDrawableOverlay) mImageView ).setScaleWithContent( true );

		// create the default action list
		mActionList = MoaActionFactory.actionList();

		mFeaturedCount = mConfigService.getInteger( R.integer.aviary_featured_packs_count );

		mImageManager = new AsyncImageManager();

		// create the preview for the main imageview
		createAndConfigurePreview();

		DragControllerService dragger = getContext().getService( DragControllerService.class );
		dragger.addDropTarget( (DropTarget) mImageView );
		dragger.setMoveTarget( mImageView );
		dragger.setDragListener( this );

		setDragController( dragger );
	}

	@Override
	public void onActivate() {
		super.onActivate();

		@SuppressWarnings ( "unused" )
		Matrix current = getContext().getCurrentImageViewMatrix();
		mImageView.setImageBitmap( mPreview, null, -1, UIConfiguration.IMAGE_VIEW_MAX_ZOOM );

		mPackCellWidth = mConfigService.getDimensionPixelSize( R.dimen.aviary_sticker_pack_width );
		mPackThumbSize = mConfigService.getDimensionPixelSize( R.dimen.aviary_sticker_pack_image_width );
		mStickerCellWidth = mConfigService.getDimensionPixelSize( R.dimen.aviary_sticker_single_item_width );
		mStickerThumbSize = mConfigService.getDimensionPixelSize( R.dimen.aviary_sticker_single_item_image_width );

		mImageManager.setOnLoadCompleteListener( this );

		mInstalledPackages = Collections.synchronizedList( new ArrayList<String>() );

		mListPacks.setOnItemClickedListener( this );
		mListPacks.setOnItemSelectedListener( this );

		// register to status change
		mStatus.setOnStatusChangeListener( this );

		if ( mExternalPacksEnabled ) {
			mPluginService.registerOnUpdateListener( this );
			mStatus.setStatus( STATUS_PACKS );
		} else {
			updateInstalledPacks( true );
		}

		getContentView().setVisibility( View.VISIBLE );
		contentReady();
	}

	@Override
	public boolean onBackPressed() {

		mLogger.info( "onBackPressed" );

		if ( mIsAnimating ) return true;

		if ( mStatus.getCurrentStatus() == STATUS_IAP ) {
			mStatus.setStatus( STATUS_PACKS );
			mListPacks.setSelectedPosition( HorizontalVariableListView.INVALID_POSITION, true );
			return true;
		}

		// we're in the packs status
		if ( mStatus.getCurrentStatus() == STATUS_PACKS ) {
			if ( stickersOnScreen() ) {
				askToLeaveWithoutApply();
				return true;
			}
			return false;
		}

		// we're in the stickers status
		if ( mStatus.getCurrentStatus() == STATUS_STICKERS ) {
			if ( mExternalPacksEnabled ) {
				mStatus.setStatus( STATUS_PACKS );
				if ( null != mPlugin ) {
					Tracker.recordTag( mPlugin.getPackageLabel() + ": Cancelled" );
				}
				return true;
			} else {
				// ok we still have a sticker in there
				if ( stickersOnScreen() ) {
					askToLeaveWithoutApply();
					return true;
				}
				return false;
			}
		}

		return super.onBackPressed();
	}

	@Override
	public boolean onCancel() {

		mLogger.info( "onCancel" );

		// if there's an active sticker on screen
		// then ask if we really want to exit this panel
		// and discard changes
		if ( stickersOnScreen() ) {
			askToLeaveWithoutApply();
			return true;
		}

		return super.onCancel();
	}

	@Override
	public void onDeactivate() {
		super.onDeactivate();

		mImageManager.setOnLoadCompleteListener( null );

		// disable the drag controller
		if ( null != getDragController() ) {
			getDragController().deactivate();
			getDragController().removeDropTarget( (DropTarget) mImageView );
			getDragController().setDragListener( null );
		}
		setDragController( null );

		mPluginService.removeOnUpdateListener( this );
		mStatus.setOnStatusChangeListener( null );
		mListPacks.setOnItemClickedListener( null );
		mListPacks.setOnItemSelectedListener( null );
		mListStickers.setOnItemClickedListener( null );
		mListStickers.setOnItemDragListener( null );
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		( (ImageViewDrawableOverlay) mImageView ).clearOverlays();
		mCurrentFilter = null;
		mActionList = null;
	}

	@Override
	protected void onDispose() {
		super.onDispose();

		if ( null != mImageManager ) {
			mImageManager.clearCache();
			mImageManager.shutDownNow();
		}

		if ( null != mInstalledPackages ) {
			mInstalledPackages.clear();
		}

		mPlugin = null;
		mCacheService = null;
		mCanvas = null;
	}

	@Override
	protected void onGenerateResult() {
		onApplyCurrent();
		super.onGenerateResult( mActionList );
	}

	@Override
	public void onConfigurationChanged( Configuration newConfig, Configuration oldConfig ) {

		// TODO: To be verified
		mLogger.info( "onConfigurationChanged: " + newConfig );

		super.onConfigurationChanged( newConfig, oldConfig );

		mImageManager.clearCache();

		if ( mStatus.getCurrentStatus() == STATUS_NULL || mStatus.getCurrentStatus() == STATUS_PACKS ) {
			// updateInstalledPacks( false );
		} else if ( mStatus.getCurrentStatus() == STATUS_STICKERS ) {
			// loadStickers();
		} else if ( mStatus.getCurrentStatus() == STATUS_IAP ) {
			if ( mIapDialog != null ) {
				mIapDialog.onConfigurationChanged( newConfig );
			}
			// updateInstalledPacks( false );
		}
	}

	@Override
	protected View generateContentView( LayoutInflater inflater ) {
		return inflater.inflate( R.layout.aviary_content_stickers, null );
	}

	@Override
	protected ViewGroup generateOptionView( LayoutInflater inflater, ViewGroup parent ) {
		return (ViewGroup) inflater.inflate( R.layout.aviary_panel_stickers, null );
	}

	@Override
	public void onLoadComplete( ImageView view, Bitmap bitmap, int tag ) {

		if ( !isActive() ) return;

		if ( null != bitmap ) {
			view.setImageBitmap( bitmap );
		} else {
			view.setImageResource( R.drawable.aviary_ic_na );
		}
		view.setVisibility( View.VISIBLE );
	}

	// /////////////////////////
	// OnStatusChangeListener //
	// /////////////////////////
	@Override
	public void OnStatusChanged( int oldStatus, int newStatus ) {
		mLogger.info( "OnStatusChange: " + oldStatus + " >> " + newStatus );

		switch ( newStatus ) {
			case STATUS_PACKS:

				// deactivate listeners for the stickers list
				mListStickers.setOnItemClickedListener( null );
				mListStickers.setOnItemDragListener( null );

				if ( oldStatus == STATUS_NULL ) {
					updateInstalledPacks( true );
				} else if ( oldStatus == STATUS_STICKERS ) {
					mViewFlipper.setDisplayedChild( 1 );
					restoreToolbarTitle();

					if ( getDragController() != null ) {
						getDragController().deactivate();
					}

				} else if ( oldStatus == STATUS_IAP ) {
					// only using back button
					mPlugin = null;
					removeIAPDialog();
				}
				break;

			case STATUS_STICKERS:
				if ( oldStatus == STATUS_PACKS ) {
					loadStickers();
				} else if ( oldStatus == STATUS_IAP ) {
					removeIAPDialog();
					loadStickers();
				} else if ( oldStatus == STATUS_NULL ) {
					loadStickers();
				}

				setToolbarTitle( mPlugin.getPackageLabel() );

				if ( getDragController() != null ) {
					getDragController().activate();
				}
				break;

			case STATUS_IAP:
				showIAPDialog();
				break;

			default:
				mLogger.error( "unmanaged status change: " + oldStatus + " >> " + newStatus );
				break;
		}
	}

	@Override
	public void OnStatusUpdated( int status ) {
		mLogger.info( "OnStatusUpdated: " + status );
		switch ( status ) {
			case STATUS_IAP:
				showIAPDialog();
				break;
		}
	}

	// ///////////////////////////
	// OnUpdateListener methods //
	// ///////////////////////////

	@Override
	public void onUpdate( PluginService service, Bundle delta ) {
		mLogger.info( "onUpdate" );

		if ( !isActive() || !mExternalPacksEnabled ) return;

		if ( !validDelta( delta ) ) {
			mLogger.log( "Suppress the alert, no stickers in the delta bundle" );
			return;
		}

		if ( mUpdateDialog != null && mUpdateDialog.isShowing() ) {
			mLogger.log( "dialog is already there, skip new alerts" );
			return;
		}

		final int status = mStatus.getCurrentStatus();
		AlertDialog dialog = null;

		if ( status == STATUS_NULL || status == STATUS_PACKS ) {
			// PACKS
			dialog = new AlertDialog.Builder( getContext().getBaseContext() ).setMessage( R.string.feather_sticker_pack_updated_1 )
					.setPositiveButton( android.R.string.ok, new DialogInterface.OnClickListener() {

						@Override
						public void onClick( DialogInterface dialog, int which ) {
							updateInstalledPacks( false );
						}
					} ).create();

		} else if ( status == STATUS_STICKERS ) {
			// STICKERS

			if ( stickersOnScreen() ) {

				dialog = new AlertDialog.Builder( getContext().getBaseContext() ).setMessage( R.string.feather_sticker_pack_updated_3 )
						.setPositiveButton( android.R.string.yes, new DialogInterface.OnClickListener() {

							@Override
							public void onClick( DialogInterface dialog, int which ) {
								onApplyCurrent();
								mStatus.setStatus( STATUS_PACKS );
								updateInstalledPacks( false );
							}
						} ).setNegativeButton( android.R.string.no, new DialogInterface.OnClickListener() {

							@Override
							public void onClick( DialogInterface dialog, int which ) {
								onClearCurrent( true );
								mStatus.setStatus( STATUS_PACKS );
								updateInstalledPacks( false );
							}
						} ).create();

			} else {

				dialog = new AlertDialog.Builder( getContext().getBaseContext() ).setMessage( R.string.feather_sticker_pack_updated_2 )
						.setPositiveButton( android.R.string.ok, new DialogInterface.OnClickListener() {

							@Override
							public void onClick( DialogInterface dialog, int which ) {
								mStatus.setStatus( STATUS_PACKS );
								updateInstalledPacks( false );
							}
						} ).create();
			}

		} else if ( status == STATUS_IAP ) {
			// IAP
			dialog = new AlertDialog.Builder( getContext().getBaseContext() ).setMessage( R.string.feather_sticker_pack_updated_2 )
					.setPositiveButton( android.R.string.ok, new DialogInterface.OnClickListener() {

						@Override
						public void onClick( DialogInterface dialog, int which ) {
							mStatus.setStatus( STATUS_PACKS );
							mListPacks.setSelectedPosition( HorizontalVariableListView.INVALID_POSITION, true );
							updateInstalledPacks( false );
						}
					} ).create();
		}

		if ( dialog != null ) {
			mUpdateDialog = dialog;
			mUpdateDialog.setCancelable( false );
			mUpdateDialog.show();
		}
	}

	// ///////////////////////////
	// Iap Notification methods //
	// ///////////////////////////

	// //////////////////////
	// OnItemClickListener //
	// //////////////////////

	@Override
	public boolean onItemClick( AdapterView<?> parent, View view, int position, long id ) {

		Log.i( "stickers", "onItemClick: " + position );

		if ( !isActive() ) return false;

		if ( mStatus.getCurrentStatus() == STATUS_PACKS || mStatus.getCurrentStatus() == STATUS_IAP ) {

			StickerEffectPack item = (StickerEffectPack) mListPacks.getAdapter().getItem( position );

			// "get more" button
			if ( item.mType == StickerEffectPackType.GET_MORE_FIRST || item.mType == StickerEffectPackType.GET_MORE_LAST ) {

				if ( position == 0 ) {
					Tracker.recordTag( "LeftGetMoreStickers : Selected" );
				} else {
					Tracker.recordTag( "RightGetMoreStickers : Selected" );
				}

				getContext().searchPlugin( FeatherIntent.PluginType.TYPE_STICKER );
				return false;
			}

			if ( null != item ) {

				if ( item.mType == StickerEffectPackType.EXTERNAL ) {

					// open the IAP Dialog only if current build is > froyo and app memory
					// is >= 32
					if ( android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.FROYO
							&& SystemUtils.getApplicationTotalMemory() >= Constants.APP_MEMORY_SMALL ) {
						mPlugin = (ExternalPlugin) item.mPluginRef;
						mStatus.setStatus( STATUS_IAP );
						return true;

					} else {
						// external plugin - download from the play store
						Tracker.recordTag( "Unpurchased(" + item.mPackageName + ") : StoreButtonClicked" );
						getContext().downloadPlugin( item.mPackageName.toString(), FeatherIntent.PluginType.TYPE_STICKER );
						return false;
					}
				} else {
					// internal plugin
					mPlugin = (InternalPlugin) item.mPluginRef;
					if ( null != mPlugin ) {
						mStatus.setStatus( STATUS_STICKERS );
						Tracker.recordTag( mPlugin.getPackageLabel() + ": Opened" );
					}
					return true;
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
	}

	@Override
	public void onNothingSelected( AdapterView<?> parent ) {
		mLogger.info( "onNothingSelected" );

		if ( mStatus.getCurrentStatus() == STATUS_IAP ) {
			mStatus.setStatus( STATUS_PACKS );
		}
	}

	// ////////////////////////
	// Drag and Drop methods //
	// ////////////////////////

	/**
	 * Starts the drag and drop operation
	 * 
	 * @param parent
	 *            - the parent list
	 * @param view
	 *            - the current view clicked
	 * @param position
	 *            - the position in the list
	 * @param id
	 *            - the item id
	 * @param nativeClick
	 *            - it's a native click
	 * @return
	 */
	private boolean startDrag( AdapterView<?> parent, View view, int position, long id, boolean animate ) {

		mLogger.info( "startDrag" );

		if ( android.os.Build.VERSION.SDK_INT < 9 ) return false;

		if ( parent == null || view == null || parent.getAdapter() == null ) {
			return false;
		}

		if ( mStatus.getCurrentStatus() != STATUS_STICKERS ) return false;
		if ( mPlugin == null || !( mPlugin instanceof InternalPlugin ) ) return false;

		if ( null != view ) {
			View image = view.findViewById( R.id.image );
			if ( null != image ) {
				final String dragInfo = (String) parent.getAdapter().getItem( position );

				int size = mStickerThumbSize;
				Bitmap bitmap;
				try {
					bitmap = ImageLoader.getPluginItemBitmap( (InternalPlugin) mPlugin, dragInfo, StickerType.Small, size, size );
					int offsetx = Math.abs( image.getWidth() - bitmap.getWidth() ) / 2;
					int offsety = Math.abs( image.getHeight() - bitmap.getHeight() ) / 2;
					
					mLogger.error( "bitmap: " + bitmap + ", is recycled? " + bitmap.isRecycled() );
					
					return getDragController().startDrag( image, bitmap, offsetx, offsety, StickersPanel.this, dragInfo,
							DragControllerService.DRAG_ACTION_MOVE, animate );
				} catch ( Exception e ) {
					e.printStackTrace();
				}

				return getDragController().startDrag( image, StickersPanel.this, dragInfo, DragControllerService.DRAG_ACTION_MOVE,
						animate );
			}
		}
		return false;
	}

	@Override
	public void setDragController( DragControllerService controller ) {
		mDragControllerService = controller;
	}

	@Override
	public DragControllerService getDragController() {
		return mDragControllerService;
	}

	@Override
	public void onDropCompleted( View arg0, boolean arg1 ) {
		mLogger.info( "onDropCompleted" );
		mListStickers.setIsDragging( false );
	}

	@Override
	public boolean onDragEnd() {
		mLogger.info( "onDragEnd" );
		mListStickers.setIsDragging( false );
		return false;
	}

	@Override
	public void onDragStart( DragSource arg0, Object arg1, int arg2 ) {
		mLogger.info( "onDragStart" );
		mListStickers.setIsDragging( true );
	}

	@Override
	public boolean acceptDrop( DragSource source, int x, int y, int xOffset, int yOffset, DragView dragView, Object dragInfo ) {
		return source == this;
	}

	@Override
	public void onDrop( DragSource source, int x, int y, int xOffset, int yOffset, DragView dragView, Object dragInfo ) {

		mLogger.info( "onDrop. source=" + source + ", dragInfo=" + dragInfo );

		if ( dragInfo != null && dragInfo instanceof String ) {
			String sticker = (String) dragInfo;
			onApplyCurrent();

			float scaleFactor = dragView.getScaleFactor();

			float w = dragView.getWidth();
			float h = dragView.getHeight();

			int width = (int) ( w / scaleFactor );
			int height = (int) ( h / scaleFactor );

			int targetX = (int) ( x - xOffset );
			int targetY = (int) ( y - yOffset );

			RectF rect = new RectF( targetX, targetY, targetX + width, targetY + height );
			addSticker( sticker, rect );
		}
	}

	// /////////////////////////
	// Stickers panel methods //
	// /////////////////////////

	/**
	 * bundle contains a list of all updates applications. if one meets the criteria ( is
	 * a sticker apk ) then return true
	 * 
	 * @param bundle
	 *            - the bundle delta
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

							if ( FeatherIntent.PluginType.isSticker( update.getPluginType() ) ) {
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

	/**
	 * Ask to leave without apply changes.
	 */
	void askToLeaveWithoutApply() {
		new AlertDialog.Builder( getContext().getBaseContext() ).setTitle( R.string.feather_attention )
				.setMessage( R.string.feather_tool_leave_question )
				.setPositiveButton( android.R.string.yes, new DialogInterface.OnClickListener() {

					@Override
					public void onClick( DialogInterface dialog, int which ) {
						getContext().cancel();
					}
				} ).setNegativeButton( android.R.string.no, null ).show();
	}

	/**
	 * Initialize the preview bitmap and canvas.
	 */
	private void createAndConfigurePreview() {

		if ( mPreview != null && !mPreview.isRecycled() ) {
			mPreview.recycle();
			mPreview = null;
		}

		mPreview = BitmapUtils.copy( mBitmap, Bitmap.Config.ARGB_8888 );
		mCanvas = new Canvas( mPreview );
	}

	/**
	 * Update the installed plugins
	 */
	protected void updateInstalledPacks( boolean firstTime ) {
		mIsAnimating = true;

		if ( mViewFlipper.getDisplayedChild() != 0 ) {
			mViewFlipper.setDisplayedChild( 0 );
		}
		new PluginInstallTask().execute();
	}

	protected final void showIAPDialog() {
		if ( mPlugin != null && mPlugin instanceof ExternalPlugin ) {
			final ExternalPlugin plugin = (ExternalPlugin) mPlugin;
			createIAPDialog( new IAPUpdater.Builder().setPlugin( plugin ).build() );
		}
	}

	private final void createIAPDialog( IAPUpdater data ) {
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
					mStatus.setStatus( STATUS_PACKS );
					mListPacks.setSelectedPosition( HorizontalVariableListView.INVALID_POSITION, true );
				}
			} );
		}
		mIapDialog = dialog;
		setApplyEnabled( false );
	}

	private boolean removeIAPDialog() {
		setApplyEnabled( true );
		if ( null != mIapDialog ) {
			mIapDialog.dismiss( true );
			mIapDialog = null;
			return true;
		}
		return false;
	}

	/**
	 * Loads the list of available stickers for the current selected pack
	 */
	protected void loadStickers() {

		mLogger.info( "loadStickers" );

		if ( mViewFlipper.getDisplayedChild() != 2 ) {
			mViewFlipper.setDisplayedChild( 2 );
		}

		if ( mPlugin != null || !( mPlugin instanceof StickerPlugin ) ) {

			String[] list = ( (StickerPlugin) mPlugin ).listStickers();
			getOptionView().post( new LoadStickersRunner( list ) );

		} else {
			onGenericError( "Sorry, there was an error opening the pack", android.R.string.ok, null );
		}
	}

	/**
	 * Add a new sticker to the canvas.
	 * 
	 * @param drawable
	 *            - the drawable name
	 */
	private void addSticker( String drawable, RectF position ) {

		if ( mPlugin == null || !( mPlugin instanceof StickerPlugin ) ) {
			return;
		}

		final StickerPlugin plugin = (StickerPlugin) mPlugin;

		onApplyCurrent();

		InputStream stream = null;

		try {
			stream = plugin.getStickerStream( drawable, StickerType.Small );
		} catch ( Exception e ) {
			e.printStackTrace();
			onGenericError( "Failed to load the selected sticker", android.R.string.ok, null );
			return;
		}

		if ( stream != null ) {
			StickerDrawable d = new StickerDrawable( plugin.getResources(), stream, drawable, plugin.getPackageLabel().toString() );
			d.setAntiAlias( true );

			IOUtils.closeSilently( stream );

			// adding the required action
			ApplicationInfo info = PackageManagerUtils.getApplicationInfo( getContext().getBaseContext(), mPlugin.getPackageName() );
			if ( info != null ) {
				String sourceDir = plugin.getSourceDir();

				if ( null == sourceDir ) {
					sourceDir = "";
					mLogger.error( "Cannot find the source dir" );
				}

				mCurrentFilter = new StickerFilter( sourceDir, drawable );
				mCurrentFilter.setSize( d.getBitmapWidth(), d.getBitmapHeight() );
				mCurrentFilter.setExternal( 0 );

				Tracker.recordTag( drawable + ": Selected" );

				addSticker( d, position );

			} else {
				onGenericError( "Sorry I'm not able to load the selected sticker", android.R.string.ok, null );
			}
		}
	}

	/**
	 * Adds the sticker.
	 * 
	 * @param drawable
	 *            - the drawable
	 * @param rotateAndResize
	 *            - allow rotate and resize
	 */
	private void addSticker( FeatherDrawable drawable, RectF positionRect ) {

		mLogger.info( "addSticker: " + drawable + ", position: " + positionRect );

		setIsChanged( true );

		DrawableHighlightView hv = new DrawableHighlightView( mImageView,
				( (ImageViewDrawableOverlay) mImageView ).getOverlayStyleId(), drawable );

		hv.setOnDeleteClickListener( new OnDeleteClickListener() {

			@Override
			public void onDeleteClick() {
				onClearCurrent( true );
			}
		} );

		Matrix mImageMatrix = mImageView.getImageViewMatrix();

		int cropWidth, cropHeight;
		int x, y;

		final int width = mImageView.getWidth();
		final int height = mImageView.getHeight();

		// width/height of the sticker
		if ( positionRect != null ) {
			cropWidth = (int) positionRect.width();
			cropHeight = (int) positionRect.height();
		} else {
			cropWidth = (int) drawable.getCurrentWidth();
			cropHeight = (int) drawable.getCurrentHeight();
		}

		final int cropSize = Math.max( cropWidth, cropHeight );
		final int screenSize = Math.min( mImageView.getWidth(), mImageView.getHeight() );

		if ( cropSize > screenSize ) {
			float ratio;
			float widthRatio = (float) mImageView.getWidth() / cropWidth;
			float heightRatio = (float) mImageView.getHeight() / cropHeight;

			if ( widthRatio < heightRatio ) {
				ratio = widthRatio;
			} else {
				ratio = heightRatio;
			}

			cropWidth = (int) ( (float) cropWidth * ( ratio / 2 ) );
			cropHeight = (int) ( (float) cropHeight * ( ratio / 2 ) );

			if ( positionRect == null ) {
				int w = mImageView.getWidth();
				int h = mImageView.getHeight();
				positionRect = new RectF( w / 2 - cropWidth / 2, h / 2 - cropHeight / 2, w / 2 + cropWidth / 2, h / 2 + cropHeight
						/ 2 );
			}

			positionRect.inset( ( positionRect.width() - cropWidth ) / 2, ( positionRect.height() - cropHeight ) / 2 );
		}

		if ( positionRect != null ) {
			x = (int) positionRect.left;
			y = (int) positionRect.top;
		} else {
			x = ( width - cropWidth ) / 2;
			y = ( height - cropHeight ) / 2;
		}

		Matrix matrix = new Matrix( mImageMatrix );
		matrix.invert( matrix );

		float[] pts = new float[] { x, y, x + cropWidth, y + cropHeight };
		MatrixUtils.mapPoints( matrix, pts );

		RectF cropRect = new RectF( pts[0], pts[1], pts[2], pts[3] );
		Rect imageRect = new Rect( 0, 0, width, height );

		// hv.setRotateAndScale( rotateAndResize );
		hv.setup( getContext().getBaseContext(), mImageMatrix, imageRect, cropRect, false );

		( (ImageViewDrawableOverlay) mImageView ).addHighlightView( hv );
		( (ImageViewDrawableOverlay) mImageView ).setSelectedHighlightView( hv );
	}

	/**
	 * Flatten the current sticker within the preview bitmap. No more changes will be
	 * possible on this sticker.
	 */
	private void onApplyCurrent() {

		mLogger.info( "onApplyCurrent" );

		if ( !stickersOnScreen() ) return;

		final DrawableHighlightView hv = ( (ImageViewDrawableOverlay) mImageView ).getHighlightViewAt( 0 );

		if ( hv != null ) {

			final StickerDrawable stickerDrawable = ( (StickerDrawable) hv.getContent() );

			RectF cropRect = hv.getCropRectF();
			Rect rect = new Rect( (int) cropRect.left, (int) cropRect.top, (int) cropRect.right, (int) cropRect.bottom );

			Matrix rotateMatrix = hv.getCropRotationMatrix();
			Matrix matrix = new Matrix( mImageView.getImageMatrix() );
			if ( !matrix.invert( matrix ) ) {
			}

			int saveCount = mCanvas.save( Canvas.MATRIX_SAVE_FLAG );
			mCanvas.concat( rotateMatrix );

			stickerDrawable.setDropShadow( false );
			hv.getContent().setBounds( rect );
			hv.getContent().draw( mCanvas );
			mCanvas.restoreToCount( saveCount );
			mImageView.invalidate();

			if ( mCurrentFilter != null ) {
				final int w = mBitmap.getWidth();
				final int h = mBitmap.getHeight();

				mCurrentFilter.setTopLeft( cropRect.left / w, cropRect.top / h );
				mCurrentFilter.setBottomRight( cropRect.right / w, cropRect.bottom / h );
				mCurrentFilter.setRotation( Math.toRadians( hv.getRotation() ) );

				int dw = stickerDrawable.getBitmapWidth();
				int dh = stickerDrawable.getBitmapHeight();
				float scalew = cropRect.width() / dw;
				float scaleh = cropRect.height() / dh;

				mCurrentFilter.setCenter( cropRect.centerX() / w, cropRect.centerY() / h );
				mCurrentFilter.setScale( scalew, scaleh );

				mActionList.add( mCurrentFilter.getActions().get( 0 ) );

				Tracker.recordTag( stickerDrawable.getPackLabel() + ": Applied" );

				mCurrentFilter = null;
			}
		}

		onClearCurrent( false );
		onPreviewChanged( mPreview, false, false );
	}

	/**
	 * Remove the current sticker.
	 * 
	 * @param removed
	 *            - true if the current sticker is being removed, otherwise it was
	 *            flattened
	 */
	private void onClearCurrent( boolean removed ) {
		mLogger.info( "onClearCurrent. removed=" + removed );

		if ( stickersOnScreen() ) {
			final ImageViewDrawableOverlay image = (ImageViewDrawableOverlay) mImageView;
			final DrawableHighlightView hv = image.getHighlightViewAt( 0 );
			onClearCurrent( hv, removed );
		}
	}

	/**
	 * Removes the current active sticker.
	 * 
	 * @param hv
	 *            - the {@link DrawableHighlightView} of the active sticker
	 * @param removed
	 *            - current sticker is removed
	 */
	private void onClearCurrent( DrawableHighlightView hv, boolean removed ) {

		mLogger.info( "onClearCurrent. hv=" + hv + ", removed=" + removed );

		if ( mCurrentFilter != null ) {
			mCurrentFilter = null;
		}

		if ( null != hv ) {
			FeatherDrawable content = hv.getContent();

			if ( removed ) {
				if ( content instanceof StickerDrawable ) {
					String name = ( (StickerDrawable) content ).getStickerName();
					String packname = ( (StickerDrawable) content ).getPackLabel();

					Tracker.recordTag( name + ": Cancelled" );
					Tracker.recordTag( packname + ": Cancelled" );

				}
			}
		}

		hv.setOnDeleteClickListener( null );
		( (ImageViewDrawableOverlay) mImageView ).removeHightlightView( hv );
		( (ImageViewDrawableOverlay) mImageView ).invalidate();
	}

	/**
	 * Return true if there's at least one active sticker on screen.
	 * 
	 * @return true, if successful
	 */
	private boolean stickersOnScreen() {
		final ImageViewDrawableOverlay image = (ImageViewDrawableOverlay) mImageView;
		return image.getHighlightCount() > 0;
	}

	/**
	 * The PluginInstallTask is completed
	 * 
	 * @param result
	 */
	private void onStickersPackListUpdated( List<StickerEffectPack> result, int installedCount, int externalCount, int firstIndex ) {
		mLogger.info( "onStickersPackListUpdated: " + result.size() );

		if ( mExternalPacksEnabled ) {

			StickerPacksAdapter adapter = new StickerPacksAdapter( getContext().getBaseContext(), R.layout.aviary_sticker_item,
					R.layout.aviary_frame_item_external, R.layout.aviary_sticker_item_more, result );
			mListPacks.setAdapter( adapter );

			// scroll the list to 'n' position
			if ( mExternalPacksEnabled && externalCount > 0 && !mFirstTimeRenderer && firstIndex > 2 ) {
				
				final int delta = (int) ( mPackCellWidth * ( firstIndex - 2.5 ) );
				mListPacks.post( new Runnable() {
					
					@Override
					public void run() {
						int clamped = mListPacks.computeScroll( delta );
						if( clamped != 0 ) {
							mListPacks.smoothScrollBy( delta, 500 );
						} else {
							mListPacks.scrollTo( delta );
						}
					}
				} );				
				
			}
			mFirstTimeRenderer = true;

			if ( mViewFlipper.getDisplayedChild() != 1 ) {
				mViewFlipper.setDisplayedChild( 1 );
			}

			if ( mInstalledPackages.size() < 1 && mExternalPacksEnabled ) {
				// show the dialog popup

				if ( !mPreferenceService.containsValue( this.getClass().getSimpleName() + "-install-first-time" ) ) {

					OnClickListener listener = new OnClickListener() {

						@Override
						public void onClick( DialogInterface dialog, int which ) {
							Tracker.recordTag( "Unpurchased("+PluginService.FREE_STICKERS_PACKAGENAME+") : StoreButtonClicked" );
							getContext().downloadPlugin( PluginService.FREE_STICKERS_PACKAGENAME,
									FeatherIntent.PluginType.TYPE_STICKER );
							dialog.dismiss();
						}
					};

					AlertDialog dialog = new AlertDialog.Builder( getContext().getBaseContext() )
							.setMessage( R.string.feather_stickers_dialog_first_time )
							.setPositiveButton( android.R.string.ok, listener ).setNegativeButton( android.R.string.cancel, null )
							.create();

					mPreferenceService.putBoolean( this.getClass().getSimpleName() + "-install-first-time", true );

					dialog.show();
				}
			}
		} else {
			if ( result.size() > 0 ) {
				mPlugin = (InternalPlugin) result.get( 0 ).mPluginRef;
				mStatus.setStatus( STATUS_STICKERS );
			}
		}
	}

	class PluginInstallTask extends AsyncTask<Void, Void, List<StickerEffectPack>> {

		private int mInstalledCount = 0;
		private int mExternalCount = 0;
		private int mFirstValidIndex = -1;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			mImageManager.clearCache();
		}

		@Override
		protected List<StickerEffectPack> doInBackground( Void... params ) {

			FeatherInternalPack installedPacks[] = null;
			FeatherPack availablePacks[] = null;

			if ( getContext() == null ) {
				return null;
			}

			final Context context = getContext().getBaseContext();
			List<StickerEffectPack> result = Collections.synchronizedList( new ArrayList<StickerEffectPack>() );

			if ( null != context ) {

				if ( mExternalPacksEnabled ) {
					while ( !mPluginService.isUpdated() ) {
						try {
							Thread.sleep( 50 );
						} catch ( InterruptedException e ) {
							e.printStackTrace();
						}
						mLogger.log( "waiting for plugin service..." );
					}

					installedPacks = mPluginService.getInstalled( context, FeatherIntent.PluginType.TYPE_STICKER );
					availablePacks = mPluginService.getAvailable( FeatherIntent.PluginType.TYPE_STICKER );
				} else {
					installedPacks = new FeatherInternalPack[] { FeatherInternalPack.getDefault( getContext().getBaseContext() ) };
					availablePacks = new FeatherExternalPack[] {};
				}
			}

			// List of the available plugins online
			mInstalledPackages.clear();

			mInstalledCount = 0;
			mExternalCount = 0;
			mFirstValidIndex = -1;

			int index;

			if ( mExternalPacksEnabled ) {
				mExternalCount++;
				result.add( new StickerEffectPack( StickerEffectPackType.GET_MORE_FIRST ) );
			}

			// cycle the available "external" packs
			if ( mExternalPacksEnabled && context != null ) {

				if ( availablePacks != null ) {
					index = 0;
					for ( FeatherPack pack : availablePacks ) {
						if ( index >= mFeaturedCount ) break;
						ExternalPlugin plugin = (ExternalPlugin) PluginFactory.create( context, pack,
								FeatherIntent.PluginType.TYPE_STICKER );
						final CharSequence packagename = plugin.getPackageName();
						final CharSequence label = plugin.getPackageLabel();

						final StickerEffectPack effectPack = new StickerEffectPack( StickerEffectPackType.EXTERNAL, packagename, label, PluginService.ERROR_NONE, plugin );
						result.add( effectPack );
						mExternalCount++;
						index++;
					}
				}
			}
			
			if( mExternalCount > 0 ) {
				result.add( new StickerEffectPack( StickerEffectPackType.RIGHT_DIVIDER ) );
			}

			if ( !isActive() ) return result;

			index = 0;

			// cycle the installed "internal" packages
			if ( null != context && installedPacks != null ) {
				for ( FeatherPack pack : installedPacks ) {
					if ( pack instanceof FeatherInternalPack ) {
						StickerPlugin plugin = (StickerPlugin) PluginFactory.create( getContext().getBaseContext(), pack,
								FeatherIntent.PluginType.TYPE_STICKER );
						final CharSequence packagename = plugin.getPackageName();
						final CharSequence label = plugin.getPackageLabel();

						final StickerEffectPack effectPack = new StickerEffectPack( StickerEffectPackType.INTERNAL, packagename, label, PluginService.ERROR_NONE, plugin );

						mInstalledPackages.add( packagename.toString() );
						result.add( effectPack );

						if ( mFirstValidIndex == -1 ) {
							mFirstValidIndex = result.size() - 1;
						}

						mInstalledCount++;
					}
					index++;
				}
			}

			if ( !isActive() ) return result;

			// add ending "get more" if necessary
			if ( mInstalledPackages != null && mInstalledPackages.size() > 0 && mExternalPacksEnabled && mInstalledCount > 3 ) {
				result.add( new StickerEffectPack( StickerEffectPackType.LEFT_DIVIDER ) );
				result.add( new StickerEffectPack( StickerEffectPackType.GET_MORE_LAST ) );
			}

			return result;
		}

		@Override
		protected void onPostExecute( List<StickerEffectPack> result ) {
			super.onPostExecute( result );
			mIsAnimating = false;
			onStickersPackListUpdated( result, mInstalledCount, mExternalCount, mFirstValidIndex );
		}
	}

	/**
	 * Sticker pack listview adapter class
	 * 
	 * @author alessandro
	 */
	class StickerPacksAdapter extends BaseAdapterExtended<StickerEffectPack> {

		static final int TYPE_INVALID = -1;
		static final int TYPE_GETMORE_FIRST = 0;
		static final int TYPE_GETMORE_LAST = 1;
		static final int TYPE_NORMAL = 2;
		static final int TYPE_EXTERNAL = 3;
		static final int TYPE_LEFT_DIVIDER = 4;
		static final int TYPE_RIGHT_DIVIDER = 5;

		private int mLayoutResId;
		private int mExternalLayoutResId;
		private int mMoreResId;
		private LayoutInflater mLayoutInflater;
		private BitmapDrawable mFolderIcon;
		private BitmapDrawable mExternalFolderIcon;
		private List<StickerEffectPack> mObjects;

		public StickerPacksAdapter ( Context context, int mainResId, int externalResId, int moreResId,
				List<StickerEffectPack> objects ) {
			super();
			mObjects = objects;
			mLayoutResId = mainResId;
			mExternalLayoutResId = externalResId;
			mMoreResId = moreResId;

			mLayoutInflater = LayoutInflater.from( context );
			mFolderIcon = (BitmapDrawable) context.getResources().getDrawable( R.drawable.aviary_sticker_pack_background );
			mExternalFolderIcon = (BitmapDrawable) context.getResources().getDrawable( R.drawable.aviary_sticker_pack_background );
		}

		@Override
		public int getCount() {
			return mObjects.size();
		}

		@Override
		public int getViewTypeCount() {
			return 6;
		}

		@Override
		public StickerEffectPack getItem( int position ) {
			return mObjects.get( position );
		}

		@Override
		public long getItemId( int position ) {
			return position;
		}

		@Override
		public boolean hasStableIds() {
			return false;
		}

		@Override
		public int getItemViewType( int position ) {

			if ( !mExternalPacksEnabled ) return TYPE_NORMAL;

			if ( position < 0 || position >= getCount() ) {
				return TYPE_INVALID;
			}
			
			StickerEffectPack item = getItem( position );

			switch( item.mType ) {
				case EXTERNAL: return TYPE_EXTERNAL;
				case LEFT_DIVIDER: return TYPE_LEFT_DIVIDER;
				case RIGHT_DIVIDER: return TYPE_RIGHT_DIVIDER;
				case GET_MORE_FIRST: return TYPE_GETMORE_FIRST;
				case GET_MORE_LAST: return TYPE_GETMORE_LAST;
				case INTERNAL:
				default:
					return TYPE_NORMAL;
			}
		}

		@Override
		public View getView( final int position, View convertView, final ViewGroup parent ) {

			ViewHolder holder = null;

			int type = getItemViewType( position );
			int layoutWidth = mPackCellWidth;
			int layoutHeight = LayoutParams.MATCH_PARENT;

			if ( convertView == null ) {

				holder = new ViewHolder();

				if ( type == TYPE_GETMORE_FIRST || type == TYPE_GETMORE_LAST ) {
					convertView = mLayoutInflater.inflate( mMoreResId, parent, false );
					layoutWidth = mPackCellWidth;
					
					holder.image = (ImageView) convertView.findViewById( R.id.aviary_image );
					
					LayoutParams params = holder.image.getLayoutParams();
					params.width = params.height = mPackThumbSize;
					holder.image.setLayoutParams( params );		
					
					if ( type == TYPE_GETMORE_LAST ) {
						// hide the last "get more" button if there's no need
						
						View lastChild = parent.getChildAt( parent.getChildCount() - 1 );
						if ( null != lastChild ) {
							
							if ( lastChild.getRight() < parent.getRight() ) {
								layoutWidth = 0;
							}
						}
					}


				} else if ( type == TYPE_NORMAL || type == TYPE_EXTERNAL ) {
					convertView = mLayoutInflater.inflate( type == TYPE_NORMAL ? mLayoutResId : mExternalLayoutResId, parent, false );

					holder.text = (TextView) convertView.findViewById( R.id.aviary_text );
					holder.image = (ImageView) convertView.findViewById( R.id.aviary_image );
					holder.image.setImageResource( R.drawable.aviary_sticker_pack_background );

					LayoutParams params = holder.image.getLayoutParams();
					params.width = params.height = mPackThumbSize;
					holder.image.setLayoutParams( params );

					layoutWidth = mPackCellWidth;
					
				} else if( type == TYPE_LEFT_DIVIDER ) {
					convertView = mLayoutInflater.inflate( R.layout.aviary_thumb_divider_left, parent, false );
					layoutWidth = LayoutParams.WRAP_CONTENT;
				} else if( type == TYPE_RIGHT_DIVIDER ) {
					convertView = mLayoutInflater.inflate( R.layout.aviary_thumb_divider_right, parent, false );
					layoutWidth = LayoutParams.WRAP_CONTENT;
				}

				convertView.setTag( holder );
				convertView.setLayoutParams( new EffectThumbLayout.LayoutParams( layoutWidth, layoutHeight ) );
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			if ( type == TYPE_NORMAL ) {
				StickerEffectPack item = getItem( position );

				holder.text.setText( item.mTitle );

				InternalPlugin plugin = (InternalPlugin) item.mPluginRef;
				StickerPackThumbnailCallable executor = new StickerPackThumbnailCallable( plugin, mFolderIcon );
				mImageManager.execute( executor, plugin.getPackageName(), holder.image, STATUS_PACKS, Priority.HIGH );

			} else if ( type == TYPE_EXTERNAL ) {

				StickerEffectPack item = getItem( position );

				ExternalPlugin plugin = (ExternalPlugin) item.mPluginRef;

				holder.image.setImageDrawable( mExternalFolderIcon );
				holder.text.setText( item.mTitle );

				if ( null != plugin ) {
					ExternalThumbnailCallable executor = new ExternalThumbnailCallable( plugin.getIconUrl(), mCacheService,
							mExternalFolderIcon, getContext().getBaseContext().getResources(), R.drawable.aviary_ic_na );
					
					mImageManager.execute( executor, plugin.getPackageName(), holder.image, STATUS_PACKS, Priority.LOW );
				}
			}

			return convertView;
		}
	}

	/**
	 * Retrieve and draw the internal plugin Icon
	 * 
	 * @author alessandro
	 */
	static class StickerPackThumbnailCallable implements Callable<Bitmap> {

		InternalPlugin mPlugin;
		BitmapDrawable mFolder;

		public StickerPackThumbnailCallable ( InternalPlugin plugin, BitmapDrawable drawable ) {
			mPlugin = plugin;
			mFolder = drawable;
		}

		@Override
		public Bitmap call() throws Exception {
			Drawable icon = mPlugin.getPackageIcon();
			if ( null != icon ) {
				return BitmapUtils.flattenDrawables( mFolder, icon, 1.7f, 0.05f );
			} else {
				return mFolder.getBitmap();
			}
		}
	}

	/**
	 * Download the remote icon or re-use the one from the current cache
	 * 
	 * @author alessandro
	 */
	static class ExternalThumbnailCallable implements Callable<Bitmap> {

		String mUri;
		BitmapDrawable mFolder;
		SoftReference<ImageCacheService> cacheServiceRef;
		SoftReference<Resources> resourcesRef;
		int mDefaultIconResId;
		
		static final String LOG_TAG = "external-thumbnail-callable";

		public ExternalThumbnailCallable ( final String uri, ImageCacheService cacheService, final BitmapDrawable folderBackground,
				Resources resources, int defaultIconResId ) {
			mUri = uri;
			mFolder = folderBackground;
			cacheServiceRef = new SoftReference<ImageCacheService>( cacheService );
			resourcesRef = new SoftReference<Resources>( resources );
			mDefaultIconResId = defaultIconResId;
		}

		@SuppressWarnings ( "deprecation" )
		@Override
		public Bitmap call() throws Exception {

			if ( null == mUri || mUri.length() < 1 ) {
				return mFolder.getBitmap();
			}
			
			Log.d( LOG_TAG, "download: " + mUri );

			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inPreferredConfig = Config.RGB_565;

			Bitmap bitmap = null;
			ImageCacheService cache = cacheServiceRef.get();

			if ( null == cache ) {
				return mFolder.getBitmap();
			}

			SimpleCachedRemoteBitmap request;

			try {
				request = cache.requestRemoteBitmap( PluginService.CONTENT_DEFAULT_URL + "/" + mUri );
				bitmap = request.getBitmap( options );
			} catch ( Exception e ) {
			}

			// fallback icon
			if ( null == bitmap ) {
				if ( null != resourcesRef.get() ) {
					try {
						bitmap = BitmapFactory.decodeResource( resourcesRef.get(), mDefaultIconResId );
					} catch ( Throwable t ) {
					}
				}
			}

			if ( null != bitmap ) {
				try {
					Bitmap result = BitmapUtils.flattenDrawables( mFolder, new BitmapDrawable( bitmap ), 1.7f, 0.05f );
					bitmap.recycle();
					bitmap = null;
					return result;
				} catch ( Throwable e ) {
					return mFolder.getBitmap();
				}
			} else {
				return mFolder.getBitmap();
			}
		}
	}

	/**
	 * Sticker pack element
	 * 
	 * @author alessandro
	 */
	static class StickerEffectPack {
		
		static enum StickerEffectPackType {
			GET_MORE_FIRST, GET_MORE_LAST, EXTERNAL, INTERNAL, LEFT_DIVIDER, RIGHT_DIVIDER
		}

		CharSequence mPackageName;
		CharSequence mTitle;
		int mPluginStatus;
		IPlugin mPluginRef;
		StickerEffectPackType mType;
		
		public StickerEffectPack ( StickerEffectPackType type ) {
			mType = type;
		}

		public StickerEffectPack ( StickerEffectPackType type, CharSequence packageName, CharSequence title, int status, IPlugin plugin ) {
			this( type );
			mPackageName = packageName;
			mPluginStatus = status;
			mPluginRef = plugin;
			mTitle = title;
		}

		@Override
		protected void finalize() throws Throwable {
			mPluginRef = null;
			super.finalize();
		}
	}

	//
	// Stickers list adapter
	//

	class StickersAdapter extends ArrayAdapter<String> {

		private LayoutInflater mLayoutInflater;
		private int mStickerResourceId;

		public StickersAdapter ( Context context, int textViewResourceId, String[] objects ) {
			super( context, textViewResourceId, objects );

			mLogger.info( "StickersAdapter. size: " + objects.length );

			mStickerResourceId = textViewResourceId;
			mLayoutInflater = LayoutInflater.from( context );
		}

		@Override
		public View getView( int position, View convertView, ViewGroup parent ) {

			View view;

			if ( null == convertView ) {
				view = mLayoutInflater.inflate( mStickerResourceId, null );
				LayoutParams params = new LayoutParams( mStickerCellWidth, LayoutParams.MATCH_PARENT );

				// LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(
				// mThumbSize, mThumbSize );
				// view.findViewById( R.id.image ).setLayoutParams( params2 );

				view.setLayoutParams( params );
			} else {
				view = convertView;
			}

			ImageView image = (ImageView) view.findViewById( R.id.image );

			final String sticker = getItem( position );

			StickerThumbnailCallable executor = new StickerThumbnailCallable( (InternalPlugin) mPlugin, sticker, mStickerThumbSize );
			mImageManager.execute( executor, sticker, image, STATUS_STICKERS, Priority.HIGH );

			return view;
		}
	}

	/**
	 * Downloads and renders the sticker thumbnail
	 * 
	 * @author alessandro
	 */
	static class StickerThumbnailCallable implements Callable<Bitmap> {

		InternalPlugin mPlugin;
		int mFinalSize;
		String mUrl;

		public StickerThumbnailCallable ( final InternalPlugin plugin, final String srcUrl, final int size ) {
			mPlugin = plugin;
			mFinalSize = size;
			mUrl = srcUrl;
		}

		@Override
		public Bitmap call() throws Exception {
			try {
				return ImageLoader.getPluginItemBitmap( mPlugin, mUrl, StickerType.Preview, mFinalSize, mFinalSize );
			} catch ( NameNotFoundException e ) {
				return ImageLoader.getPluginItemBitmap( mPlugin, mUrl, StickerType.Small, mFinalSize, mFinalSize );
			} catch ( Exception e ) {
				e.printStackTrace();
				return null;
			}
		}

	}

	//
	// Runnable for loading all the stickers from a pack
	//

	private class LoadStickersRunner implements Runnable {

		String[] mlist;

		LoadStickersRunner ( String[] list ) {
			mlist = list;
		}

		@Override
		public void run() {

			mIsAnimating = true;

			if ( mListStickers.getHeight() == 0 ) {
				mOptionView.post( this );
				return;
			}

			StickersAdapter adapter = new StickersAdapter( getContext().getBaseContext(), R.layout.aviary_sticker_item_single, mlist );
			mListStickers.setAdapter( adapter );
			mListStickers.setDragTolerance( mStickerThumbSize / 2 );

			mListStickers.setDragScrollEnabled( true );
			mListStickers.setOnItemDragListener( new OnItemDragListener() {

				@Override
				public boolean onItemStartDrag( AdapterView<?> parent, View view, int position, long id ) {
					return startDrag( parent, view, position, id, false );
				}
			} );
			mListStickers.setLongClickable( false );

			mListStickers.setOnItemClickedListener( new OnItemClickedListener() {

				@Override
				public boolean onItemClick( AdapterView<?> parent, View view, int position, long id ) {
					final Object obj = parent.getAdapter().getItem( position );
					final String sticker = (String) obj;
					addSticker( sticker, null );
					return true;
				}
			} );

			mIsAnimating = false;
			mlist = null;
		}
	}

}
