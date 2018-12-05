/*
 * Copyright (C) 2014 Murray Cumming
 *
 * This file is part of android-galaxyzoo.
 *
 * android-galaxyzoo is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * android-galaxyzoo is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with android-galaxyzoo.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.murrayc.galaxyzoo.app;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.murrayc.galaxyzoo.app.provider.Item;
import com.murrayc.galaxyzoo.app.provider.ItemsContentProvider;

/**
 * A fragment representing a single subject.
 * This fragment is either contained in a {@link ListActivity}
 * in two-pane mode (on tablets) or a {@link SubjectViewerActivity}
 * on handsets.
 */
public class SubjectViewerFragment extends ItemFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int URL_LOADER = 0;
    // We have to hard-code the indices - we can't use getColumnIndex because the Cursor
    // (actually a SQliteDatabase cursor returned
    // from our ContentProvider) only knows about the underlying SQLite database column names,
    // not our ContentProvider's column names. That seems like a design error in the Android API.
    //TODO: Use org.apache.commons.lang.ArrayUtils.indexOf() instead?
    private static final int COLUMN_INDEX_ID = 0;
    private final String[] mColumns = {Item.Columns._ID};
    private Cursor mCursor = null;
    private View mLoadingView = null;
    private View mRootView = null;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public SubjectViewerFragment() {
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_subject_viewer, container, false);
        assert mRootView != null;

        //Show the progress spinner while we are waiting for the subject to load,
        //particularly during first start when we are waiting to get the first data in our cache.
        showLoadingView(true);

        initializeSingleton();

        return mRootView;
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        createCommonOptionsMenu(menu, inflater);
    }

    @Override
    protected void onSingletonInitialized() {
        super.onSingletonInitialized();

        //Now we are ready to do more:
        update();
    }

    private void showLoadingView(final boolean show) {
        if (mLoadingView == null) {
            mLoadingView = mRootView.findViewById(R.id.loading_spinner);
        }

        mLoadingView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void addOrUpdateChildFragments() {
        showLoadingView(false);

        final Bundle arguments = new Bundle();
        //TODO? arguments.putString(ARG_USER_ID,
        //        getUserId()); //Obtained in the super class.
        arguments.putString(ItemFragment.ARG_ITEM_ID,
                getItemId());

        //Add, or update, the nested child fragments.
        //This can only be done programmatically, not in the layout XML.
        //See http://developer.android.com/about/versions/android-4.2.html#NestedFragments

        final FragmentManager fragmentManager = getChildFragmentManager();
        SubjectFragment fragmentSubject = (SubjectFragment) fragmentManager.findFragmentById(R.id.child_fragment_subject);
        if (fragmentSubject == null) {
            fragmentSubject = new SubjectFragment();
            fragmentSubject.setArguments(arguments);
            fragmentManager.beginTransaction().replace(R.id.child_fragment_subject, fragmentSubject).commit();
        } else {
            //TODO: Is there some more standard method to do this,
            //to trigger the Fragments' onCreate()?
            fragmentSubject.setItemId(getItemId());
            fragmentSubject.update();
        }


        // Add extra details or links.
        SubjectExtrasFragment fragmentQuestion = (SubjectExtrasFragment) fragmentManager.findFragmentById(R.id.child_fragment_subject_extras);
        if (fragmentQuestion == null) {
            fragmentQuestion = new SubjectExtrasFragment();
            fragmentQuestion.setArguments(arguments);
            fragmentManager.beginTransaction().replace(R.id.child_fragment_subject_extras, fragmentQuestion).commit();
        } else {
            //TODO: Is there some more standard method to do this,
            //to trigger the Fragments' onCreate()?
            fragmentQuestion.setItemId(getItemId());
            fragmentQuestion.update();
        }
    }

    public void update() {
        final Activity activity = getActivity();
        if (activity == null)
            return;


        if (TextUtils.equals(getItemId(), ItemsContentProvider.URI_PART_ITEM_ID_NEXT)) {
            /*
             * Initializes the CursorLoader. The URL_LOADER value is eventually passed
             * to onCreateLoader().
             * We use restartLoader(), instead of initLoader(),
             * so we can refresh this fragment to show a different subject,
             * even when using the same query ("next") to do that.
             */
            getLoaderManager().restartLoader(URL_LOADER, null, this);
        } else {
            //Add, or update, the child fragments already, because we know the Item IDs:
            addOrUpdateChildFragments();
        }
    }

    private void updateFromCursor() {
        if (mCursor == null) {
            Log.error("mCursor is null.");
            return;
        }

        final Activity activity = getActivity();
        if (activity == null)
            return;

        if (mCursor.getCount() <= 0) { //In case the query returned no rows.
            Log.error("SubjectViewerFragment.updateFromCursor(): The ContentProvider query returned no rows.");
            UiUtils.warnAboutMissingNetwork(activity, mRootView);

            return;
        }

        mCursor.moveToFirst(); //There should only be one anyway.

        if (mRootView == null) {
            Log.error("SubjectViewerFragment.updateFromCursor(): mRootView is null.");
            return;
        }

        //This will return the actual ID if we asked for the NEXT id.
        if (mCursor.getCount() > 0) {
            final String itemId = mCursor.getString(COLUMN_INDEX_ID);
            setItemId(itemId);
        }

        //TODO: Just update them.
        addOrUpdateChildFragments();
    }

    //We only bother using this when we have asked for the "next" item,
    //because we want to know its ID.
    @Override
    public Loader<Cursor> onCreateLoader(final int loaderId, final Bundle bundle) {
        if (loaderId != URL_LOADER) {
            return null;
        }
        final String itemId = getItemId();
        if (TextUtils.isEmpty(itemId)) {
            return null;
        }

        //Asynchronously get the actual ID,
        //because we have just asked for the "next" item.
        final Activity activity = getActivity();

        final Uri.Builder builder = Item.ITEMS_URI.buildUpon();
        builder.appendPath(itemId);

        showLoadingView(true);

        return new CursorLoader(
                activity,
                builder.build(),
                mColumns,
                null, // No where clause, return all records. We already specify just one via the itemId in the URI
                null, // No where clause, therefore no where column values.
                null // Use the default sort order.
        );
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> cursorLoader, final Cursor cursor) {
        mCursor = cursor;

        showLoadingView(false);

        updateFromCursor();

        // Avoid this being called twice, which seems to be an Android bug,
        // and which could cause us to get a different item ID if our virtual "next" item changes to
        // another item:
        // See http://stackoverflow.com/questions/14719814/onloadfinished-called-twice
        // and https://code.google.com/p/android/issues/detail?id=63179
        getLoaderManager().destroyLoader(URL_LOADER);
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> cursorLoader) {
        /*
         * Clears out our reference to the Cursor.
         * This prevents memory leaks.
         */
        mCursor = null;
    }
}
