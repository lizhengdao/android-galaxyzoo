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

package com.murrayc.galaxyzoo.app.provider;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.test.mock.MockContentResolver;
import android.text.TextUtils;

import com.murrayc.galaxyzoo.app.Log;
import com.murrayc.galaxyzoo.app.Utils;
import com.murrayc.galaxyzoo.app.provider.client.ZooniverseClient;
import com.murrayc.galaxyzoo.app.syncadapter.SubjectAdder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemsContentProvider extends ContentProvider {

    public static final String URI_PART_ITEM = "item";
    public static final String URI_PART_ITEM_ID_NEXT = "next"; //Use in place of the item ID to get the next unclassified item.
    public static final String URI_PART_FILE = "file";
    public static final String URI_PART_CLASSIFICATION_ANSWER = "classification-answer";
    public static final String URI_PART_CLASSIFICATION_CHECKBOX = "classification-checkbox";
    private static final String URI_PART_CLASSIFICATION = "classification";

    /** The standard _data field used by the ContentProvider/ContentResolver for
     * the local URI corresponding to the row (identified by a Content URI) in the table.
     */
    public static final String URI_PART_DATA = "_data";

    /**
     * The MIME type of {@link Item#ITEMS_URI} providing a directory of items.
     */
    private static final String CONTENT_TYPE_ITEMS =
            "vnd.android.cursor.dir/vnd.android-galaxyzoo.item";

    /**
     * The MIME type of a {@link Item#ITEMS_URI} sub-directory of a single
     * item.
     */
    private static final String CONTENT_TYPE_ITEM =
            "vnd.android.cursor.item/vnd.android-galaxyzoo.item";

    /**
     * The MIME type of {@link Item#ITEMS_URI} providing a directory of classifications.
     */
    private static final String CONTENT_TYPE_CLASSIFICATIONS =
            "vnd.android.cursor.dir/vnd.android-galaxyzoo.classification";

    /**
     * The MIME type of a {@link Item#ITEMS_URI} sub-directory of a single
     * classification.
     */
    private static final String CONTENT_TYPE_CLASSIFICATION =
            "vnd.android.cursor.item/vnd.android-galaxyzoo.classification";

    /**
     * The MIME type of {@link Item#ITEMS_URI} providing a directory of classifications.
     */
    private static final String CONTENT_TYPE_CLASSIFICATION_ANSWERS =
            "vnd.android.cursor.dir/vnd.android-galaxyzoo.classification-answer";

    /**
     * The MIME type of a {@link Item#ITEMS_URI} sub-directory of a single
     * classification answer.
     */
    private static final String CONTENT_TYPE_CLASSIFICATION_ANSWER =
            "vnd.android.cursor.item/vnd.android-galaxyzoo.classification-answer";

    /**
     * The MIME type of {@link Item#ITEMS_URI} providing a directory of classifications.
     */
    private static final String CONTENT_TYPE_CLASSIFICATION_CHECKBOXES =
            "vnd.android.cursor.dir/vnd.android-galaxyzoo.classification-checkboxes";

    /**
     * The MIME type of a {@link Item#ITEMS_URI} sub-directory of a single
     * classification checkbox.
     */
    private static final String CONTENT_TYPE_CLASSIFICATION_CHECKBOX =
            "vnd.android.cursor.item/vnd.android-galaxyzoo.classification-checkbox";

    //TODO: Use an enum?
    private static final int MATCHER_ID_ITEMS = 1;
    private static final int MATCHER_ID_ITEM = 2;
    private static final int MATCHER_ID_ITEM_NEXT = 3;
    private static final int MATCHER_ID_FILE = 4;
    private static final int MATCHER_ID_CLASSIFICATIONS = 5;
    private static final int MATCHER_ID_CLASSIFICATION = 6;
    private static final int MATCHER_ID_CLASSIFICATION_ANSWERS = 7;
    private static final int MATCHER_ID_CLASSIFICATION_ANSWER = 8;
    private static final int MATCHER_ID_CLASSIFICATION_CHECKBOXES = 9;
    private static final int MATCHER_ID_CLASSIFICATION_CHECKBOX = 10;
    private static final UriMatcher sUriMatcher;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // A URI for the list of all items:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_ITEM, MATCHER_ID_ITEMS);

        // A URI for the next item:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_ITEM + "/" + URI_PART_ITEM_ID_NEXT, MATCHER_ID_ITEM_NEXT);

        // A URI for a single item:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_ITEM + "/#", MATCHER_ID_ITEM);

        // A URI for a single file:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_FILE + "/#", MATCHER_ID_FILE);

        // A URI for the list of all classifications:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION, MATCHER_ID_CLASSIFICATIONS);

        // A URI for a single classification:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION + "/#", MATCHER_ID_CLASSIFICATION);

        // A URI for the list of all classifications:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION_ANSWER, MATCHER_ID_CLASSIFICATION_ANSWERS);

        // A URI for a single classification:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION_ANSWER + "/#", MATCHER_ID_CLASSIFICATION_ANSWER);

        // A URI for the list of all classifications:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION_CHECKBOX, MATCHER_ID_CLASSIFICATION_CHECKBOXES);

        // A URI for a single classification:
        sUriMatcher.addURI(Item.AUTHORITY, URI_PART_CLASSIFICATION_CHECKBOX + "/#", MATCHER_ID_CLASSIFICATION_CHECKBOX);
    }

    private static final String[] FILE_MIME_TYPES = new String[]{"application/x-glom"};

    /**
     * A map of GlomContentProvider projection column names to underlying Sqlite column names
     * for /item/ URIs, mapping to the items tables.
     */
    private static final Map<String, String> sItemsProjectionMap;
    private static final Map<String, String> sClassificationAnswersProjectionMap;
    private static final Map<String, String> sClassificationCheckboxesProjectionMap;

    static {
        sItemsProjectionMap = new HashMap<>();
        sItemsProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        sItemsProjectionMap.put(Item.Columns.DONE, DatabaseHelper.ItemsDbColumns.DONE);
        sItemsProjectionMap.put(Item.Columns.UPLOADED, DatabaseHelper.ItemsDbColumns.UPLOADED);
        sItemsProjectionMap.put(Item.Columns.SUBJECT_ID, DatabaseHelper.ItemsDbColumns.SUBJECT_ID);
        sItemsProjectionMap.put(Item.Columns.ZOONIVERSE_ID, DatabaseHelper.ItemsDbColumns.ZOONIVERSE_ID);
        sItemsProjectionMap.put(Item.Columns.GROUP_ID, DatabaseHelper.ItemsDbColumns.GROUP_ID);
        sItemsProjectionMap.put(Item.Columns.LOCATION_STANDARD_URI_REMOTE, DatabaseHelper.ItemsDbColumns.LOCATION_STANDARD_URI_REMOTE);
        sItemsProjectionMap.put(Item.Columns.LOCATION_STANDARD_URI, DatabaseHelper.ItemsDbColumns.LOCATION_STANDARD_URI);
        sItemsProjectionMap.put(Item.Columns.LOCATION_STANDARD_DOWNLOADED, DatabaseHelper.ItemsDbColumns.LOCATION_STANDARD_DOWNLOADED);
        sItemsProjectionMap.put(Item.Columns.LOCATION_THUMBNAIL_URI_REMOTE, DatabaseHelper.ItemsDbColumns.LOCATION_THUMBNAIL_URI_REMOTE);
        sItemsProjectionMap.put(Item.Columns.LOCATION_THUMBNAIL_URI, DatabaseHelper.ItemsDbColumns.LOCATION_THUMBNAIL_URI);
        sItemsProjectionMap.put(Item.Columns.LOCATION_THUMBNAIL_DOWNLOADED, DatabaseHelper.ItemsDbColumns.LOCATION_THUMBNAIL_DOWNLOADED);
        sItemsProjectionMap.put(Item.Columns.LOCATION_INVERTED_URI_REMOTE, DatabaseHelper.ItemsDbColumns.LOCATION_INVERTED_URI_REMOTE);
        sItemsProjectionMap.put(Item.Columns.LOCATION_INVERTED_URI, DatabaseHelper.ItemsDbColumns.LOCATION_INVERTED_URI);
        sItemsProjectionMap.put(Item.Columns.LOCATION_INVERTED_DOWNLOADED, DatabaseHelper.ItemsDbColumns.LOCATION_INVERTED_DOWNLOADED);
        sItemsProjectionMap.put(Item.Columns.FAVORITE, DatabaseHelper.ItemsDbColumns.FAVORITE);
        sItemsProjectionMap.put(Item.Columns.DATETIME_DONE, DatabaseHelper.ItemsDbColumns.DATETIME_DONE);


        sClassificationAnswersProjectionMap = new HashMap<>();
        sClassificationAnswersProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        sClassificationAnswersProjectionMap.put(ClassificationAnswer.Columns.ITEM_ID, DatabaseHelper.ClassificationAnswersDbColumns.ITEM_ID);
        sClassificationAnswersProjectionMap.put(ClassificationAnswer.Columns.SEQUENCE, DatabaseHelper.ClassificationAnswersDbColumns.SEQUENCE);
        sClassificationAnswersProjectionMap.put(ClassificationAnswer.Columns.QUESTION_ID, DatabaseHelper.ClassificationAnswersDbColumns.QUESTION_ID);
        sClassificationAnswersProjectionMap.put(ClassificationAnswer.Columns.ANSWER_ID, DatabaseHelper.ClassificationAnswersDbColumns.ANSWER_ID);

        sClassificationCheckboxesProjectionMap = new HashMap<>();
        sClassificationCheckboxesProjectionMap.put(BaseColumns._ID, BaseColumns._ID);
        sClassificationCheckboxesProjectionMap.put(ClassificationCheckbox.Columns.ITEM_ID, DatabaseHelper.ClassificationCheckboxesDbColumns.ITEM_ID);
        sClassificationCheckboxesProjectionMap.put(ClassificationCheckbox.Columns.SEQUENCE, DatabaseHelper.ClassificationCheckboxesDbColumns.SEQUENCE);
        sClassificationCheckboxesProjectionMap.put(ClassificationCheckbox.Columns.QUESTION_ID, DatabaseHelper.ClassificationCheckboxesDbColumns.QUESTION_ID);
        sClassificationCheckboxesProjectionMap.put(ClassificationCheckbox.Columns.CHECKBOX_ID, DatabaseHelper.ClassificationCheckboxesDbColumns.CHECKBOX_ID);

    }


    private DatabaseHelper mOpenDbHelper = null;

    //These are only used in the rare case that we need to explicitly get a "next" item,
    //and block on the result, if the SyncAdapter hasn't done that for us.
    private ZooniverseClient mZooniverseClient = null;
    private SubjectAdder mSubjectAdder = null;
    private static final String[] PROJECTION_REMOVE_ITEM = {
            DatabaseHelper.ItemsDbColumns.LOCATION_STANDARD_URI,
            DatabaseHelper.ItemsDbColumns.LOCATION_THUMBNAIL_URI,
            DatabaseHelper.ItemsDbColumns.LOCATION_INVERTED_URI
    };
    private static final String[] PROJECTION_FILES_FILE_DATA = {DatabaseHelper.FilesDbColumns.FILE_DATA};


    /** A where clause to find all the subjects that have not yet been classified,
     * and which are ready to be classified.
     */
    private static final String WHERE_CLAUSE_NOT_DONE = "(" +
            DatabaseHelper.ItemsDbColumns.DONE + " != 1" +
            ") AND (" +
            DatabaseHelper.ItemsDbColumns.LOCATION_STANDARD_DOWNLOADED + " == 1" +
            ") AND (" +
            DatabaseHelper.ItemsDbColumns.LOCATION_THUMBNAIL_DOWNLOADED + " == 1" +
            ") AND (" +
            DatabaseHelper.ItemsDbColumns.LOCATION_INVERTED_DOWNLOADED + " == 1" +
            ")";

    public ItemsContentProvider() {
    }

    private static ContentValues getMappedContentValues(final ContentValues values, final Map<String, String> projectionMap) {
        final ContentValues result = new ContentValues();

        for (final String keyExternal : values.keySet()) {
            final String keyInternal = projectionMap.get(keyExternal);
            if (!TextUtils.isEmpty(keyInternal)) {
                final Object value = values.get(keyExternal);
                putValueInContentValues(result, keyInternal, value);
            }
        }

        return result;
    }

    /**
     * There is no ContentValues.put(key, object),
     * only put(key, String), put(key, Boolean), etc.
     * so we use this tedious implementation instead,
     * so our code can be more generic.
     *
     * @param values
     * @param key
     * @param value
     */
    private static void putValueInContentValues(final ContentValues values, final String key, final Object value) {
        if (value instanceof String) {
            values.put(key, (String) value);
        } else if (value instanceof Boolean) {
            values.put(key, (Boolean) value);
        } else if (value instanceof Integer) {
            values.put(key, (Integer) value);
        } else if (value instanceof Long) {
            values.put(key, (Long) value);
        } else if (value instanceof Double) {
            values.put(key, (Double) value);
        }
    }

    @Override
    public int delete(@NonNull final Uri uri, final String selection, final String[] selectionArgs) {
        final int match = sUriMatcher.match(uri);
        final int affected;

        switch (match) {
            //TODO: Do not support this because it would delete everything in one go?
            case MATCHER_ID_ITEMS:
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_ITEMS,
                        (!TextUtils.isEmpty(selection) ?
                                " AND (" + selection + ')' : ""),
                        selectionArgs
                );
                //TODO: Delete all associated files too.
                break;
            case MATCHER_ID_ITEM: {
                //TODO: Use selection.
                final UriParts uriParts = parseContentUri(uri);
                removeItem(uriParts.itemId);
                affected = 1; //TODO: Check the removeItem() result.
                break;
            }

            //TODO: Do not support this because it would delete everything in one go?
            case MATCHER_ID_CLASSIFICATION_ANSWERS:
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                        (!TextUtils.isEmpty(selection) ?
                                " AND (" + selection + ')' : ""),
                        selectionArgs
                );
                //TODO: Delete all associated files too.
                break;
            case MATCHER_ID_CLASSIFICATION_ANSWER: {
                final UriParts uriParts = parseContentUri(uri);
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId)
                );
                break;
            }

            //TODO: Do not support this because it would delete everything in one go?
            case MATCHER_ID_CLASSIFICATION_CHECKBOXES:
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                        (!TextUtils.isEmpty(selection) ?
                                " AND (" + selection + ')' : ""),
                        selectionArgs
                );
                //TODO: Delete all associated files too.
                break;
            case MATCHER_ID_CLASSIFICATION_CHECKBOX:
                final UriParts uriParts = parseContentUri(uri);
                affected = getDb().delete(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId)
                );
                break;

            //TODO?: case MATCHER_ID_FILE:
            default:
                throw new IllegalArgumentException("unknown item: " +
                        uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return affected;
    }

    @Override
    public String getType(@NonNull final Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case MATCHER_ID_ITEMS:
                return CONTENT_TYPE_ITEMS;
            case MATCHER_ID_ITEM:
            case MATCHER_ID_ITEM_NEXT:
                return CONTENT_TYPE_ITEM;
            case MATCHER_ID_CLASSIFICATION_ANSWERS:
                return CONTENT_TYPE_CLASSIFICATION_ANSWERS;
            case MATCHER_ID_CLASSIFICATION_ANSWER:
                return CONTENT_TYPE_CLASSIFICATION_ANSWER;
            case MATCHER_ID_CLASSIFICATION_CHECKBOXES:
                return CONTENT_TYPE_CLASSIFICATION_CHECKBOXES;
            case MATCHER_ID_CLASSIFICATION_CHECKBOX:
                return CONTENT_TYPE_CLASSIFICATION_CHECKBOX;
            default:
                throw new IllegalArgumentException("Unknown item type: " +
                        uri);
        }
    }

    public String[] getStreamTypes(@NonNull final Uri uri, @NonNull final String mimeTypeFilter) {

        switch (sUriMatcher.match(uri)) {
            case MATCHER_ID_FILE:
                if (mimeTypeFilter != null) {
                    // We use ClipDescription just so we can use its filterMimeTypes()
                    // though we are not intested in ClipData here.
                    // TODO: Find a more suitable utility function?
                    final ClipDescription clip = new ClipDescription(null, FILE_MIME_TYPES);
                    return clip.filterMimeTypes(mimeTypeFilter);
                } else {
                    //We return a clone rather than the array itself,
                    //because that would theoretically allow the caller to
                    //modify the items, which is theoretically a
                    //security vulnerability.
                    return FILE_MIME_TYPES.clone();
                }
            default:
                throw new IllegalArgumentException("Unknown type: " +
                        uri);
        }
    }

    @Override
    public ParcelFileDescriptor openFile(@NonNull final Uri uri, @NonNull final String mode)
            throws FileNotFoundException {
        return super.openFileHelper(uri, mode);
    }

    //TODO: Is this actually used by anything?
    @Override
    public Uri insert(@NonNull final Uri uri, final ContentValues values) {

        // Note: We map the values' columns names to the internal database columns names.
        // Strangely, I can't find any example code, or open source code, that bothers to do this,
        // though examples for query() generally do.
        // Maybe they don't do it because it's so awkward. murrayc.
        // But if we don't do this then we are leaking the internal database structure out as our API.

        final Uri uriInserted;

        switch (sUriMatcher.match(uri)) {
            case MATCHER_ID_ITEMS:
            case MATCHER_ID_ITEM: {
                //Refuse to insert without a Subject ID:
                final String subjectId = values.getAsString(Item.Columns.SUBJECT_ID);
                if (TextUtils.isEmpty(subjectId)) {
                    throw new IllegalArgumentException("Refusing to insert without a SubjectID: " + uri);
                }

                // Get (our) local content URIs for the local caches of any (or any future) remote URIs for the images:
                // Notice that we allow the client to provide a remote URI for each but we then change
                // it to our local URI of our local cache of that remote file.
                // Even if no URI is provided by the client, we still create the local URI and put
                // it in the table row for later use.
                final ContentValues valuesComplete = new ContentValues(values);

                //This doesn't actually get any data from the locations.
                boolean fileUrisCreated = false;
                try {
                    fileUrisCreated = createFileUrisForImages(valuesComplete);
                } catch (final IOException e) {
                    Log.error("insert(): createFileUrisForImages() failed", e);
                }

                if (!fileUrisCreated) {
                    //Abandon the item.
                    //We cannot add an item without its file URIs.
                    return null;
                }

                uriInserted = insertMappedValues(DatabaseHelper.TABLE_NAME_ITEMS, valuesComplete,
                        sItemsProjectionMap, Item.ITEMS_URI);

                //The caller (SyncAdapter) will do this: cacheUrisToFiles(subjectId, listFiles, true /* async */);
                requestSync();

                break;
            }
            case MATCHER_ID_CLASSIFICATION_ANSWERS:
            case MATCHER_ID_CLASSIFICATION_ANSWER:
                uriInserted = insertMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                        values, sClassificationAnswersProjectionMap,
                        ClassificationAnswer.CLASSIFICATION_ANSWERS_URI);
                break;
            case MATCHER_ID_CLASSIFICATION_CHECKBOXES:
            case MATCHER_ID_CLASSIFICATION_CHECKBOX:
                uriInserted = insertMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                        values, sClassificationCheckboxesProjectionMap,
                        ClassificationCheckbox.CLASSIFICATION_CHECKBOXES_URI);
                break;
            default:
                //This could be because of an invalid -1 ID in the # position.
                throw new IllegalArgumentException("unsupported uri: " + uri);
        }

        return uriInserted;
    }

    private int updateMappedValues(final String tableName, final ContentValues values, final Map<String, String> projectionMap, final String selection,
                                   final String[] selectionArgs) {
        final ContentValues valuesToUse = getMappedContentValues(values, projectionMap);

        // insert the initialValues into a new database row
        final SQLiteDatabase db = getDb();

        return db.update(tableName, valuesToUse,
                selection, selectionArgs);
    }

    @Nullable
    private Uri insertMappedValues(final String tableName, final ContentValues values, final Map<String, String> projectionMap, final Uri uriPrefix) {
        final ContentValues valuesToUse = getMappedContentValues(values, projectionMap);

        // insert the initialValues into a new database row
        final SQLiteDatabase db = getDb();

        try {
            final long rowId = db.insertOrThrow(tableName,
                    DatabaseHelper.ItemsDbColumns._ID, valuesToUse);
            if (rowId >= 0) {
                final Uri itemUri =
                        ContentUris.withAppendedId(
                                uriPrefix, rowId);
                getContext().getContentResolver().notifyChange(itemUri, null);
                return itemUri; //The URI of the newly-added Item.
            } else {
                throw new IllegalStateException("could not insert " +
                        "content values: " + values);
            }
        } catch (final SQLException e) {
            //TODO: Let the caller catch this?
            Log.error("insert failed", e);
        }

        return null;
    }

    /** Get a the content URI of a new file, whose data will actually be on the local system.
     */
    private Uri createFileUri() throws IOException {
        //Log.info("createFileUri(): subject id=" + subjectId + ", imageType=" + imageType);

        final SQLiteDatabase db = getDb();
        final long fileId = db.insertOrThrow(DatabaseHelper.TABLE_NAME_FILES,
                DatabaseHelper.FilesDbColumns.FILE_DATA, null);

        //Build a value for the _data column, using the autogenerated file _id:
        final String realFileUri = createCacheFile(Long.toString(fileId)); //TODO: Is toString() affected by the locale?)
        if (TextUtils.isEmpty(realFileUri)) {
            Log.error("createFileUri(): createCacheFile() returned null.");
            return null;
        }

        //Put the value for the _data column in the files table:
        //This will be used implicitly by openOutputStream() and openInputStream():
        final ContentValues valuesUpdate = new ContentValues();
        valuesUpdate.put(DatabaseHelper.FilesDbColumns.FILE_DATA, realFileUri);
        db.update(DatabaseHelper.TABLE_NAME_FILES, valuesUpdate,
                BaseColumns._ID + " = ?", new String[]{Double.toString(fileId)});

        //Build the content: URI for the file to put in the Item's table:
        Uri fileUri = null;
        if (fileId >= 0) {
            fileUri = ContentUris.withAppendedId(Item.FILE_URI, fileId);
            //TODO? getContext().getContentResolver().notifyChange(fileId, null);
        }

        return fileUri;
    }

    /**
     * Actually create the file on disk in the cache directory,
     * and return the absolute path of the new file.
     *
     * @param filename
     * @return
     */
    @Nullable
    private String createCacheFile(final String filename) throws IOException {
        final Context context = getContext();
        if (context == null) {
            return null;
        }

        final File cacheDir = Utils.getExternalCacheDir(context);
        if (cacheDir == null) {
            Log.error("createFileUri(): getExternalCacheDir returned null.");
            return null;
        }

        final File file = new File(cacheDir, filename);

        //Actually create an empty file there -
        //otherwise when we try to write to it via openOutputStream()
        //we will get a FileNotFoundException.
        try {
            if (!file.createNewFile()) {
                //This can happen while debugging, if we wipe the database but don't wipe the cached files.
                //You can do that by uninstalling the app.
                //When this happens we just reuse the file.
                Log.error("createCacheFile(): The file already exists: " + file.getAbsolutePath());
            }
            /*
            else {
                Log.info("createFileUri(): subject id=" + subjectId +", file created: " + realFile.getAbsolutePath());
            }
            */
        } catch (final SecurityException e) {
            // This can theoretically happen (according to the createNewFile() docs.
            Log.fatal("createcacheFile(): SecurityException during testing: for filename=" + file.getAbsolutePath(), e);
            throw e;
        } catch (final IOException|UnsupportedOperationException e) {
            Log.error("createCacheFile(): exception during testing: for filename=" + file.getAbsolutePath(), e);

            //This happens while running under ProviderTestCase2.
            //so we just catch it and provide a useful value,
            //so at least the other functionality can be tested.
            //TODO: Find a way to let it succeed.
            if (context.getContentResolver() instanceof MockContentResolver) {
                Log.error("createCacheFile(): exception expected during testing: for filename=" + file.getAbsolutePath(), e);
                return "testuri";
            } else {
                throw e;
            }
        }

        return file.getAbsolutePath();
    }

    @Override
    public boolean onCreate() {
        final Context context = getContext();

        mOpenDbHelper = new DatabaseHelper(context);

        //This is useful to wipe the database when testing.
        //Note that the cached image files in files/ will not be deleted
        //so you will see "the file already exists" errors in the log,
        //but we will then just reuse the files.
        //mOpenDbHelper.onUpgrade(mOpenDbHelper.getWritableDatabase(), 0, 1);
        mZooniverseClient = new ZooniverseClient(context, Config.SERVER);
        mSubjectAdder = new SubjectAdder(context);

        //This isn't necessary when using the private getExternalCacheDir():
        //Make sure that the .nomedia file exists,
        //to prevent the media indexer from checking or listing our files.
        //createCacheFile(".nomedia");

        return true;
    }


    @Override
    public Cursor query(@NonNull final Uri uri, final String[] projection, final String selection,
                        final String[] selectionArgs, final String sortOrder) {
        //TODO: Avoid a direct implicit mapping between the Cursor column names in "selection" and the
        //underlying SQL database names.

        // If no sort order is specified use the default
        final String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = DatabaseHelper.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        final int match = sUriMatcher.match(uri);

        Cursor c;
        switch (match) {
            case MATCHER_ID_ITEMS: {
                // query the database for all items:
                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_ITEMS);
                builder.setProjectionMap(sItemsProjectionMap);
                c = builder.query(getDb(), projection,
                        selection, selectionArgs,
                        null, null, orderBy);

                c.setNotificationUri(getContext().getContentResolver(),
                        Item.ITEMS_URI);

                //The client must call(TODO) sometime to actually fill the database with items,
                //and the client will then be notified via the cursor that there are new items.

                break;
            }
            case MATCHER_ID_ITEM: {
                // query the database for a specific item:
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection

                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_ITEMS);
                builder.setProjectionMap(sItemsProjectionMap);
                builder.appendWhere(BaseColumns._ID + " = ?"); //We use ? to avoid SQL Injection.
                c = builder.query(getDb(), projection,
                        selection, prependToArray(selectionArgs, uriParts.itemId),
                        null, null, orderBy);
                c.setNotificationUri(getContext().getContentResolver(),
                        Item.ITEMS_URI); //TODO: More precise?
                break;
            }

            case MATCHER_ID_ITEM_NEXT:
                c = queryItemNext(projection, selection, selectionArgs, orderBy);

                if (c == null) {
                    Log.error("ItemsContentProvider.query(): c is null.");
                } else {
                    final int count = c.getCount();
                    if (count < 1) {
                        //Immediately get some more from the REST server and then try again.
                        //Get one synchronously, for now.
                        //This is a (small) duplicate of what SyncProvider does.
                        //TODO: Find a way to ask the SyncProvider to do exactly this and no more,
                        //and block until it has finished?
                        // Try this more than once, in case we are using multiple groups
                        // and some of the groups are no longer available from the server.
                        // This doesn't guarantee that we will try all groups, but it makes
                        // it much more likely that we will hit one that works.
                        boolean found = false;
                        for(int i = 0; i < 3; i++) {
                            List<ZooniverseClient.Subject> subjects = null;
                            try {
                                subjects = mZooniverseClient.requestMoreItemsSync(1);
                            } catch (final HttpUtils.NoNetworkException e) {
                                //Return the empty cursor,
                                //and let the caller guess at the cause.
                                //If we let the exception be thrown by this query() method then
                                //it will causes an app crash in AsyncTask.done(), as used by CursorLoader.
                                //TODO: Find a better way to respond to errors when using CursorLoader?
                                Log.error("ItemsContentProvider.query(): next: requestMoreItemsSync threw NoNetworkException.");
                            } catch (final ZooniverseClient.RequestMoreItemsException e) {
                                //Return the empty cursor,
                                //and let the caller guess at the cause.
                                //If we let the exception be thrown by this query() method then
                                //it will causes an app crash in AsyncTask.done(), as used by CursorLoader.
                                //TODO: Find a better way to respond to errors when using CursorLoader?
                                Log.error("ItemsContentProvider.query(): next: requestMoreItemsSync threw RequestMoreItemsException.");
                            }

                            if ((subjects == null) || (subjects.isEmpty())) {
                                Log.error("ItemsContentProvider.query(): next: requestMoreItemsSync returned no items.");
                            } else {
                                found = true;
                                if(!mSubjectAdder.addSubjects(subjects, false /* not async - we need it immediately. */)) {
                                    found = false; //Something went wrong when getting the first item.
                                }
                                break;
                            }
                        }

                        if (!found) {
                            //Return the empty cursor,
                            //and let the caller guess at the cause.
                            Log.error("ItemsContentProvider.query(): next: requestMoreItemsSync returned no items even after multiple attempts");
                            return c;
                        }

                        //Close the cursor and try again, now that we expect to succeed:
                        c.close();
                        c = queryItemNext(projection, selection, selectionArgs, orderBy);
                    }


                    c.setNotificationUri(getContext().getContentResolver(),
                            Item.ITEMS_URI); //TODO: More precise?
                }

                //Make sure we have enough soon enough
                //by getting the rest asynchronously:
                requestSync();

                break;

            case MATCHER_ID_FILE:
                // query the database for a specific file:
                // The caller will then use the _data value (the normal filesystem URI of a file).
                final long fileId = ContentUris.parseId(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection
                c = getDb().query(DatabaseHelper.TABLE_NAME_FILES, projection,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, fileId), null, null, orderBy
                );

                c.setNotificationUri(getContext().getContentResolver(),
                        Item.FILE_URI); //TODO: More precise?
                break;

            case MATCHER_ID_CLASSIFICATION_ANSWERS: {
                // query the database for all items:
                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS);
                builder.setProjectionMap(sClassificationAnswersProjectionMap);
                c = builder.query(getDb(), projection,
                        selection, selectionArgs,
                        null, null, orderBy);

                c.setNotificationUri(getContext().getContentResolver(),
                        ClassificationAnswer.CONTENT_URI);

                break;
            }
            case MATCHER_ID_CLASSIFICATION_ANSWER: {
                // query the database for a specific item:
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection

                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS);
                builder.setProjectionMap(sClassificationAnswersProjectionMap);
                builder.appendWhere(BaseColumns._ID + " = ?"); //We use ? to avoid SQL Injection.
                c = builder.query(getDb(), projection,
                        selection, prependToArray(selectionArgs, uriParts.itemId),
                        null, null, orderBy);
                c.setNotificationUri(getContext().getContentResolver(),
                        ClassificationAnswer.CONTENT_URI); //TODO: More precise?
                break;
            }

            case MATCHER_ID_CLASSIFICATION_CHECKBOXES: {
                // query the database for all items:
                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES);
                builder.setProjectionMap(sClassificationCheckboxesProjectionMap);
                c = builder.query(getDb(), projection,
                        selection, selectionArgs,
                        null, null, orderBy);

                c.setNotificationUri(getContext().getContentResolver(),
                        ClassificationCheckbox.CONTENT_URI);

                break;
            }
            case MATCHER_ID_CLASSIFICATION_CHECKBOX:
                // query the database for a specific item:
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection

                final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES);
                builder.setProjectionMap(sClassificationCheckboxesProjectionMap);
                builder.appendWhere(BaseColumns._ID + " = ?"); //We use ? to avoid SQL Injection.
                c = builder.query(getDb(), projection,
                        selection, prependToArray(selectionArgs, uriParts.itemId),
                        null, null, orderBy);
                c.setNotificationUri(getContext().getContentResolver(),
                        ClassificationCheckbox.CONTENT_URI); //TODO: More precise?
                break;

            default:
                //This could be because of an invalid -1 ID in the # position.
                throw new IllegalArgumentException("unsupported uri: " + uri);
        }

        //TODO: Can we avoid passing a Sqlite cursor up as a ContentResolver cursor?
        return c;
    }

    private Cursor queryItemNext(final String[] projection, final String selection, final String[] selectionArgs, final String orderBy) {
        // query the database for a single  item that is not yet done:

        //Prepend our ID=? argument to the selection arguments.
        //This lets us use the ? syntax to avoid SQL injection

        final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(DatabaseHelper.TABLE_NAME_ITEMS);
        builder.setProjectionMap(sItemsProjectionMap);
        builder.appendWhere(WHERE_CLAUSE_NOT_DONE);

        //Default to the order of creation,
        //so we are more likely to get the first record that was created synchronously
        //so we could be sure that it was fully loaded.
        String orderByToUse = orderBy;
        if (orderBy == null || orderBy.isEmpty()) {
            orderByToUse = DatabaseHelper.ItemsDbColumns._ID + " ASC";
        }

        return builder.query(getDb(), projection,
                selection, selectionArgs,
                null, null, orderByToUse, "1");
    }

    private static String[] prependToArray(final String[] selectionArgs, final long value) {
        return prependToArray(selectionArgs, Double.toString(value));
    }

    private static String[] prependToArray(final String[] array, final String value) {
        //Handle array being null:
        if (array == null) {
            final String[] result = new String[1];
            result[0] = value;
            return result;
        }

        final int arrayLength = array.length;
        final String[] result = new String[arrayLength + 1];
        result[0] = value;

        if (arrayLength > 0) {
            System.arraycopy(array, 0, result, 1, result.length);
        }

        return result;
    }

    private static UriParts parseContentUri(final Uri uri) {
        final UriParts result = new UriParts();
        //ContentUris.parseId(uri) gets the first ID, not the last.
        //final long userId = ContentUris.parseId(uri);
        final List<String> uriParts = uri.getPathSegments();
        final int size = uriParts.size();

        if (size < 2) {
            Log.error("The URI did not have the expected number of parts.");
        }

        //Note: The UriMatcher will not even match the URI if this id (#) is -1
        //so we will never reach this code then:
        result.itemId = uriParts.get(1);

        return result;
    }

    @Override
    public int update(@NonNull final Uri uri, final ContentValues values, final String selection,
                      final String[] selectionArgs) {
        final int affected;

        // Note: We map the values' columns names to the internal database columns names.
        // Strangely, I can't find any example code, or open source code, that bothers to do this,
        // though examples for query() generally do.
        // Maybe they don't do it because it's so awkward. murrayc.
        // But if we don't do this then we are leaking the internal database structure out as our API.

        switch (sUriMatcher.match(uri)) {
            case MATCHER_ID_ITEMS:
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_ITEMS, values, sItemsProjectionMap,
                        selection, selectionArgs);
                requestSync();
                break;

            case MATCHER_ID_ITEM: {
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_ITEMS, values, sItemsProjectionMap,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId));
                requestSync();
                break;
            }

            case MATCHER_ID_CLASSIFICATION_ANSWERS:
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                        values, sClassificationAnswersProjectionMap,
                        selection, selectionArgs);
                break;

            case MATCHER_ID_CLASSIFICATION_ANSWER: {
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                        values, sClassificationAnswersProjectionMap,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId)
                );
                break;
            }

            case MATCHER_ID_CLASSIFICATION_CHECKBOXES:
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                        values, sClassificationCheckboxesProjectionMap,
                        selection, selectionArgs);
                break;

            case MATCHER_ID_CLASSIFICATION_CHECKBOX:
                final UriParts uriParts = parseContentUri(uri);

                //Prepend our ID=? argument to the selection arguments.
                //This lets us use the ? syntax to avoid SQL injection
                affected = updateMappedValues(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                        values, sClassificationCheckboxesProjectionMap,
                        prependIdToSelection(selection),
                        prependToArray(selectionArgs, uriParts.itemId)
                );
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);

        return affected;
    }

    private static String prependIdToSelection(final String selection) {
        return BaseColumns._ID + " = ?"
                + (!TextUtils.isEmpty(selection) ?
                " AND (" + selection + ')' : "");
    }

    /**
     * Get the database.
     *
     * We don't need to close() this SQLiteDatabase.
     *   See http://stackoverflow.com/a/12715032/1123654
     *
     * @return
     */
    private SQLiteDatabase getDb() {
        return mOpenDbHelper.getWritableDatabase();
    }

    private void removeItem(final String itemId) {
        final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(DatabaseHelper.TABLE_NAME_ITEMS);
        builder.appendWhere(Item.Columns._ID + " = ?"); //We use ? to avoid SQL Injection.
        final String[] selectionArgs = {itemId}; //TODO: locale-independent?
        final Cursor c = builder.query(getDb(), PROJECTION_REMOVE_ITEM,
                null, selectionArgs,
                null, null, null);

        final String[] imageUris = new String[3];
        if (c.moveToFirst()) {
            imageUris[0] = c.getString(0);
            imageUris[1] = c.getString(1);
            imageUris[2] = c.getString(2);
        }

        c.close();

        removeItem(itemId, imageUris);
    }

    private void removeItem(final String itemId, final String[] imageUris) {
        final SQLiteDatabase db = getDb();

        final SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(DatabaseHelper.TABLE_NAME_FILES);
        builder.appendWhere(DatabaseHelper.FilesDbColumns._ID + " = ?");

        //Get the cached image files, delete them, and forget them:
        for (final String contentUri : imageUris) {
            if (contentUri == null) {
                continue;
            }

            //Get the real local URI for the file:
            final Uri uri = Uri.parse(contentUri);
            final long fileId = ContentUris.parseId(uri);
            final String strFileId = Double.toString(fileId); //TODO: Is this locale-independent?

            final String[] selectionArgs = {strFileId};
            final Cursor c = builder.query(db, PROJECTION_FILES_FILE_DATA,
                    null, selectionArgs,
                    null, null, null);

            if (c.moveToFirst()) {
                final String realFileUri = c.getString(0);
                final File realFile = new File(realFileUri);
                if(!realFile.delete()) {
                    Log.error("removeItem(): File.delete() failed.");
                }
            }

            c.close();

            final String[] whereArgs = {strFileId};
            if (db.delete(DatabaseHelper.TABLE_NAME_FILES,
                    DatabaseHelper.FilesDbColumns._ID + " = ?",
                    whereArgs) <= 0) {
                Log.error("removeItem(): Could not remove the file row.");
            }
        }

        // Remove the related classification answers:
        final String[] whereArgs = {itemId};
        if (db.delete(DatabaseHelper.TABLE_NAME_CLASSIFICATION_ANSWERS,
                DatabaseHelper.ClassificationAnswersDbColumns.ITEM_ID + " = ?",
                whereArgs) <= 0) {
            //This would only be an error worth reporting if we know that this item
            //has a classification already.
            //Log.error("removeItem(): Could not remove the classification answers rows.");
        }

        // Remove the related classification checkboxes:
        // We don't check that at least 1 row was deleted,
        // because there are not always answers with checkboxes.
        if (db.delete(DatabaseHelper.TABLE_NAME_CLASSIFICATION_CHECKBOXES,
                DatabaseHelper.ClassificationCheckboxesDbColumns.ITEM_ID + " = ?",
                whereArgs) <= 0) {
            //This would only be an error worth reporting if we know that this item
            //has a classification already.
            //Log.error("removeItem(): Could not remove the classification answers rows.");
        }

        //Delete the item:
        if (db.delete(DatabaseHelper.TABLE_NAME_ITEMS,
                Item.Columns._ID + " = ?",
                whereArgs) <= 0) {
            Log.error("removeItem(): No item rows were removed.");
        }
    }

    /**
     * Create Content URIs that point to local files, so we can download the remote files to those
     * files as a cache.
     *
     * @param values
     * @return
     */
    private boolean createFileUrisForImages(final ContentValues values) throws IOException {
        Uri fileUri = createFileUri();
        if (fileUri == null) {
            return false;
        }

        values.put(DatabaseHelper.ItemsDbColumns.LOCATION_STANDARD_URI, fileUri.toString());

        fileUri = createFileUri();
        if (fileUri == null) {
            return false;
        }

        values.put(DatabaseHelper.ItemsDbColumns.LOCATION_THUMBNAIL_URI, fileUri.toString());

        fileUri = createFileUri();
        if (fileUri == null) {
            return false;
        }

        values.put(DatabaseHelper.ItemsDbColumns.LOCATION_INVERTED_URI, fileUri.toString());

        return true;
    }

    /**
     * There are 2 tables: items and files.
     * The items table has a uri field that specifies a record in the files tables.
     * The files table has a (standard for openInput/OutputStream()) _data field that
     * contains the URI of the file for the item.
     * <p/>
     * The location and creation of the SQLite database is left entirely up to the SQLiteOpenHelper
     * class. We just store its name in the Document.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        //After the first official release, try to preserve data when changing this. See onUpgrade()
        private static final int DATABASE_VERSION = 21;

        private static final String DATABASE_NAME = "items.db";

        private static final String TABLE_NAME_ITEMS = "items";
        private static final String TABLE_NAME_FILES = "files";
        //Each item row has many classification_answers rows.
        private static final String TABLE_NAME_CLASSIFICATION_ANSWERS = "classification_answers";
        //Each item row has some classification_checkboxes rows.
        private static final String TABLE_NAME_CLASSIFICATION_CHECKBOXES = "classification_checkboxes";
        private static final String DEFAULT_SORT_ORDER = Item.Columns._ID + " ASC";

        DatabaseHelper(final Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(final SQLiteDatabase sqLiteDatabase) {
            createTable(sqLiteDatabase);
        }

        @Override
        public void onUpgrade(final SQLiteDatabase sqLiteDatabase,
                              final int oldv, final int newv) {
            if (oldv != newv) {

                switch(oldv) {
                    case 20: {
                        //Add the groupId field to the items:
                        try {
                            sqLiteDatabase.execSQL("ALTER TABLE " + TABLE_NAME_ITEMS + " ADD COLUMN "
                                    + ItemsDbColumns.GROUP_ID + " TEXT;");
                        } catch( final SQLiteException ex) {
                            Log.error("onUpgrade: ALTER TABLE ADD COLUMN failed", ex);
                            //Fall through to the default case to recreate the tables completely.
                        }
                        break;
                    }

                    default: {
                        dropTable(sqLiteDatabase, TABLE_NAME_ITEMS);
                        dropTable(sqLiteDatabase, TABLE_NAME_FILES);
                        dropTable(sqLiteDatabase, TABLE_NAME_CLASSIFICATION_ANSWERS);
                        dropTable(sqLiteDatabase, TABLE_NAME_CLASSIFICATION_CHECKBOXES);

                        createTable(sqLiteDatabase);
                        break;
                    }
                }
            }
        }

        private static void dropTable(final SQLiteDatabase sqLiteDatabase, final String tableName) {
            sqLiteDatabase.execSQL("DROP TABLE IF EXISTS " +
                    tableName + ";");
        }

        private static void createTable(final SQLiteDatabase sqLiteDatabase) {
            String qs = "CREATE TABLE " + TABLE_NAME_ITEMS + " (" +
                    BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ItemsDbColumns.DONE + " INTEGER DEFAULT 0, " +
                    ItemsDbColumns.UPLOADED + " INTEGER DEFAULT 0, " +
                    ItemsDbColumns.SUBJECT_ID + " TEXT, " +
                    ItemsDbColumns.ZOONIVERSE_ID + " TEXT, " +
                    ItemsDbColumns.GROUP_ID + " TEXT, " +
                    ItemsDbColumns.LOCATION_STANDARD_URI_REMOTE + " TEXT, " +
                    ItemsDbColumns.LOCATION_STANDARD_URI + " TEXT, " +
                    ItemsDbColumns.LOCATION_STANDARD_DOWNLOADED + " INTEGER DEFAULT 0, " +
                    ItemsDbColumns.LOCATION_THUMBNAIL_URI_REMOTE + " TEXT, " +
                    ItemsDbColumns.LOCATION_THUMBNAIL_URI + " TEXT, " +
                    ItemsDbColumns.LOCATION_THUMBNAIL_DOWNLOADED + " INTEGER DEFAULT 0, " +
                    ItemsDbColumns.LOCATION_INVERTED_URI_REMOTE + " TEXT, " +
                    ItemsDbColumns.LOCATION_INVERTED_URI + " TEXT, " +
                    ItemsDbColumns.LOCATION_INVERTED_DOWNLOADED + " INTEGER DEFAULT 0, " +
                    ItemsDbColumns.FAVORITE + " INTEGER DEFAULT 0, " +
                    ItemsDbColumns.DATETIME_DONE + " TEXT)";
            sqLiteDatabase.execSQL(qs);
            createIndex(sqLiteDatabase, TABLE_NAME_ITEMS, ItemsDbColumns.SUBJECT_ID);
            createIndex(sqLiteDatabase, TABLE_NAME_ITEMS, ItemsDbColumns.UPLOADED);
            createIndex(sqLiteDatabase, TABLE_NAME_ITEMS, ItemsDbColumns.DONE);
            createIndex(sqLiteDatabase, TABLE_NAME_ITEMS, ItemsDbColumns.DATETIME_DONE);
            createIndex(sqLiteDatabase, TABLE_NAME_ITEMS, ItemsDbColumns.LOCATION_STANDARD_DOWNLOADED);
            createIndex(sqLiteDatabase, TABLE_NAME_ITEMS, ItemsDbColumns.LOCATION_THUMBNAIL_DOWNLOADED);
            createIndex(sqLiteDatabase, TABLE_NAME_ITEMS, ItemsDbColumns.LOCATION_INVERTED_DOWNLOADED);


            qs = "CREATE TABLE " + TABLE_NAME_FILES + " (" +
                    BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    FilesDbColumns.FILE_DATA + " TEXT);";
            sqLiteDatabase.execSQL(qs);


            qs = "CREATE TABLE " + TABLE_NAME_CLASSIFICATION_ANSWERS + " (" +
                    BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ClassificationAnswersDbColumns.SEQUENCE + " INTEGER DEFAULT 0, " +
                    ClassificationAnswersDbColumns.ITEM_ID + " INTEGER, " +
                    ClassificationAnswersDbColumns.QUESTION_ID + " TEXT, " +
                    ClassificationAnswersDbColumns.ANSWER_ID + " TEXT)";
            sqLiteDatabase.execSQL(qs);
            createIndex(sqLiteDatabase, TABLE_NAME_CLASSIFICATION_ANSWERS, ClassificationAnswersDbColumns.ITEM_ID);

            qs = "CREATE TABLE " + TABLE_NAME_CLASSIFICATION_CHECKBOXES + " (" +
                    BaseColumns._ID +
                    " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    ClassificationCheckboxesDbColumns.SEQUENCE + " INTEGER DEFAULT 0, " +
                    ClassificationCheckboxesDbColumns.ITEM_ID + " INTEGER, " +
                    ClassificationCheckboxesDbColumns.QUESTION_ID + " TEXT, " +
                    ClassificationCheckboxesDbColumns.CHECKBOX_ID + " TEXT)";
            sqLiteDatabase.execSQL(qs);
            createIndex(sqLiteDatabase, TABLE_NAME_CLASSIFICATION_CHECKBOXES, ClassificationCheckboxesDbColumns.ITEM_ID);
            createIndex(sqLiteDatabase, TABLE_NAME_CLASSIFICATION_CHECKBOXES, ClassificationCheckboxesDbColumns.QUESTION_ID);
        }

        private static void createIndex(final SQLiteDatabase sqLiteDatabase, final String tableName, final String fieldName) {
            final String qs = "CREATE INDEX " + tableName + "_" + fieldName + "_index" +
                    " ON " + tableName +
                    " ( " + fieldName + " )";
            sqLiteDatabase.execSQL(qs);
        }

        private static class ItemsDbColumns implements BaseColumns {
            //Specific to our app:
            static final String DONE = "done"; //1 or 0. Whether the user has classified it already.
            static final String UPLOADED = "uploaded"; //1 or 0. Whether its classification has been submitted.

            //From the REST API:
            static final String SUBJECT_ID = "subjectId";
            static final String ZOONIVERSE_ID = "zooniverseId";
            static final String GROUP_ID = "groupId";
            static final String LOCATION_STANDARD_URI_REMOTE = "locationStandardUriRemote"; //The original file on the remote server.
            static final String LOCATION_STANDARD_URI = "locationStandardUri"; //The content URI for a file in the files table.
            static final String LOCATION_STANDARD_DOWNLOADED = "locationStandardDownloaded"; //1 or 0. Whether the file has finished downloading.
            static final String LOCATION_THUMBNAIL_URI_REMOTE = "locationThumbnailUriRemote"; //The original file on the remote server.
            static final String LOCATION_THUMBNAIL_URI = "locationThumbnailUri"; //The content URI for a file in the files table.
            static final String LOCATION_THUMBNAIL_DOWNLOADED = "locationThumbnailDownloaded"; //1 or 0. Whether the file has finished downloading.
            static final String LOCATION_INVERTED_URI_REMOTE = "locationInvertedUriRemote"; //The original file on the remote server.
            static final String LOCATION_INVERTED_URI = "locationInvertedUri"; //The content URI for a file in the files table.
            static final String LOCATION_INVERTED_DOWNLOADED = "locationInvertedDownloaded"; //1 or 0. Whether the file has finished downloading.
            //            static final String LOCATIONS_REQUESTED_DATETIME = "locationsRequestedDateTime"; //When we last tried to download the images. An ISO8601 string ("YYYY-MM-DD HH:MM:SS.SSS")
            static final String FAVORITE = "favorite"; //1 or 0. Whether the user has marked this as a favorite.
            static final String DATETIME_DONE = "dateTimeDone"; //An ISO8601 string ("YYYY-MM-DD HH:MM:SS.SSS").
        }

        private static class FilesDbColumns implements BaseColumns {
            private static final String FILE_DATA = URI_PART_DATA; //The real URI
        }

        private static class ClassificationAnswersDbColumns implements BaseColumns {
            private static final String ITEM_ID = "itemId";
            private static final String SEQUENCE = "sequence";
            private static final String QUESTION_ID = "questionId";
            private static final String ANSWER_ID = "answerId";
        }

        private static class ClassificationCheckboxesDbColumns implements BaseColumns {

            private static final String ITEM_ID = "itemId";
            private static final String SEQUENCE = "sequence";
            private static final String QUESTION_ID = "questionId";
            private static final String CHECKBOX_ID = "checkboxId";
        }
    }

    /** Ask the SyncAdapter to do its work.
     * We call this when we think it's likely that some work is necessary.
     */
    private static void requestSync() {
        final Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);

        //Ask the framework to run our SyncAdapter.
        //We call this far too often,
        //but we trust the SyncAdapter system to not actually do the sync too often.
        //That seems to work fine as long as the SyncAdapter is in its own process.
        //See android:process=":sync" in AndroidManifest.xml
        ContentResolver.requestSync(null, Item.AUTHORITY, extras);
    }

    private static class UriParts {
        public String itemId = null;
    }
}
