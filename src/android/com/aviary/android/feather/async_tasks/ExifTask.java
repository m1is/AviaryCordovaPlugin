package com.aviary.android.feather.async_tasks;

import android.os.Bundle;

import com.aviary.android.feather.headless.media.ExifInterfaceWrapper;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.ThreadPoolService.BackgroundCallable;

public class ExifTask extends BackgroundCallable<String, Bundle> {

	@Override
	public Bundle call( IAviaryController context, String path ) {

		if ( path == null ) {
			return null;
		}

		Bundle result = new Bundle();

		try {
			ExifInterfaceWrapper exif = new ExifInterfaceWrapper( path );
			exif.copyTo( result );

		} catch ( Throwable t ) {
			t.printStackTrace();
		}
		return result;
	}

}
