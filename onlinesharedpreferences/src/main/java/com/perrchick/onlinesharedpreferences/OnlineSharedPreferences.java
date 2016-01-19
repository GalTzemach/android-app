package com.perrchick.onlinesharedpreferences;

import android.content.Context;
import android.util.Log;

import com.parse.DeleteCallback;
import com.parse.FindCallback;
import com.parse.Parse;
import com.parse.ParseException;
import com.parse.ParseQuery;
import com.parse.SaveCallback;
import com.perrchick.onlinesharedpreferences.parse.ParseSyncedObject;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by perrchick on 1/17/16.
 */
public class OnlineSharedPreferences {
    private static final String TAG = OnlineSharedPreferences.class.getSimpleName();

    private static OnlineSharedPreferences _onlineSharedPreferences;
    private static OnlineSharedPreferences getInstance(Context context) {
        synchronized (context) {
            if (_onlineSharedPreferences == null) {
                _onlineSharedPreferences = new OnlineSharedPreferences(context);
            }
        }

        return _onlineSharedPreferences;
    }

    private OnlineSharedPreferences(Context context) {
        Log.v(TAG, "Initializing integration with Parse");
        // [Optional] Power your app with Local Datastore. For more info, go to
        // https://parse.com/docs/android/guide#local-datastore
        Parse.enableLocalDatastore(context);

        // Replace this if you want the data to be managed in your on account
        Parse.initialize(context, "6uvLKEmnnQtdRpdThttAnDneX1RxyGUjyHwpI462", "TaVVVo6EP2dufExRhznnVSHYl5YHwM9gPhvxwP00");
        ParseSyncedObject.registerSubclass(ParseSyncedObject.class);

        this.keyValueObjectContainer = new ParseObjectWrapper(context.getPackageName());
    }

    public interface GetAllObjectsCallback {
        void done(HashMap<String, String> objects, ParseException e);
    }
    public interface GetObjectCallback {
        void done(String value, ParseException e);
    }
    public interface CommitCallback {
        void done(ParseException e);
    }
    public interface RemoveCallback {
        void done(ParseException e);
    }

    public static OnlineSharedPreferences getParseSharedPreferences(Context context) {
        return getInstance(context);
    }

    // This object will contain all the <key,value> combinations
    private final ParseObjectWrapper keyValueObjectContainer;
    // To prevent overriding by similar keys, there's another foreign key that will make this combination unique
    public static final String PACKAGE_NAME_KEY = "packageName";

    public OnlineSharedPreferences putObject(String key, String value) {
        keyValueObjectContainer.putObject(key, value);

        return this;
    }

    public void remove(final String key, final RemoveCallback removeCallback) {
        Log.v(TAG, "Removing '" + key + "'...");
        keyValueObjectContainer.remove(key, new DeleteCallback() {
            @Override
            public void done(ParseException e) {
                if (removeCallback != null) {
                    removeCallback.done(e);
                }
                if (e == null) {
                    Log.v(TAG, "... Removed '" + key + "'");
                } else {
                    Log.e(TAG, "... Failed to remove '" + key + "'");
                    e.printStackTrace();
                }
            }
        });
    }

    public void getObject(final String key, final GetObjectCallback callback) {
        ParseQuery<ParseSyncedObject> parseQuery = ParseQuery.getQuery(ParseSyncedObject.class);
        // Has two keys (package name + key)
        parseQuery.whereEqualTo(PACKAGE_NAME_KEY, keyValueObjectContainer.getPackageName());
        parseQuery.whereEqualTo(ParseSyncedObject.SAVED_OBJECT_KEY, key);
        Log.v(TAG, "Getting object for key '" + key + "'...");
        parseQuery.findInBackground(new FindCallback<ParseSyncedObject>() {
            @Override
            public void done(List<ParseSyncedObject> objects, ParseException e) {
                String value = null;
                if (objects.size() > 0) {
                    value = objects.get(0).getValue();
                }
                if (callback != null) {
                    // Should be unique
                    callback.done(value, e);
                }

                if (e == null) {
                    Log.v(TAG, "... Got object for key '" + key + "'");
                } else {
                    Log.e(TAG, "... Failed to get object for key '" + key + "'");
                    e.printStackTrace();
                }
            }
        });
    }

    public void getAllObjects(final GetAllObjectsCallback callback) {
        final ParseQuery<ParseSyncedObject> parseQuery = ParseQuery.getQuery(ParseSyncedObject.class);
        parseQuery.whereEqualTo(PACKAGE_NAME_KEY, keyValueObjectContainer.getPackageName());
        Log.v(TAG, "Getting all objects...");
        parseQuery.findInBackground(new FindCallback<ParseSyncedObject>() {
            @Override
            public void done(List<ParseSyncedObject> objects, ParseException e) {
                HashMap<String, String> savedObjects = new HashMap<String, String>(objects.size());
                for (ParseSyncedObject syncedObject : objects) {
                    savedObjects.put(syncedObject.getKey(), syncedObject.getValue());
                }
                callback.done(savedObjects, e);

                if (e == null) {
                    Log.v(TAG, "... Got all (" + objects.size() + ") objects");
                } else {
                    Log.e(TAG, "... Failed to get all objects");
                    e.printStackTrace();
                }
            }
        });
    }

    public void commitInBackground() {
        commitInBackground(null);
    }

    public void commitInBackground(final CommitCallback commitCallback) {
        Log.v(TAG, "Committing in background...");
        keyValueObjectContainer.saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if (commitCallback != null) {
                    commitCallback.done(e);
                }

                if (e == null) {
                    Log.v(TAG, "... Committed in background");
                } else {
                    Log.e(TAG, "... Failed to commit in background");
                    e.printStackTrace();
                }
            }
        });
    }

    // Adapter pattern (Object Adapter)
    private static class ParseObjectWrapper {
        private final ParseSyncedObject innerObject;

        protected ParseObjectWrapper(String packageName) {
            innerObject = new ParseSyncedObject();
            innerObject.put(OnlineSharedPreferences.PACKAGE_NAME_KEY, packageName);
        }

        protected void putObject(String key, Object value) {
            innerObject.put(ParseSyncedObject.SAVED_OBJECT_KEY, key);
            innerObject.put(ParseSyncedObject.SAVED_OBJECT_VALUE, value);
        }

        protected void remove(String key, final DeleteCallback deleteCallback) {
            ParseQuery<ParseSyncedObject> parseQuery = ParseQuery.getQuery(ParseSyncedObject.class);
            parseQuery.whereEqualTo(OnlineSharedPreferences.PACKAGE_NAME_KEY, innerObject.getPackageName());
            parseQuery.whereEqualTo(ParseSyncedObject.SAVED_OBJECT_KEY, key);
            // Find the object to delete
            parseQuery.findInBackground(new FindCallback<ParseSyncedObject>() {
                @Override
                public void done(final List<ParseSyncedObject> objects, ParseException e) {
                    // Found?
                    if (objects.size() == 1) {
                        if (deleteCallback == null) {
                            objects.get(0).deleteInBackground();
                        } else {
                            objects.get(0).deleteInBackground(deleteCallback);
                        }
                    }
                }
            });
        }

        /**
         * Saves the shared object
         * I'm sure there's an efficient way, I might solve it with Parse by configuring the object to have two unique keys from 'packageName" + 'key'.
         * But I wanted to have the challenge.
         *
         * Will check duplications, delete if any, and save.
         *
         * @param saveCallback The callback that holds the method to run in completion
         */
        protected void saveInBackground(final SaveCallback saveCallback) {
            ParseQuery<ParseSyncedObject> parseQuery = ParseQuery.getQuery(ParseSyncedObject.class);
            parseQuery.whereEqualTo(OnlineSharedPreferences.PACKAGE_NAME_KEY, innerObject.getPackageName());
            parseQuery.whereEqualTo(ParseSyncedObject.SAVED_OBJECT_KEY, this.innerObject.getKey());
            // (1) Find duplications
            parseQuery.findInBackground(new FindCallback<ParseSyncedObject>() {
                @Override
                public void done(final List<ParseSyncedObject> objects, ParseException e) {
                    // (2) Duplications found?
                    if (objects.size() > 0) {
                        // (3) Delete duplications (disallowed) before add new object
                        boolean finished = false;
                        ExecutorService es = Executors.newCachedThreadPool();
                        for (int i = 0; i < objects.size(); i++) {
                            final int index = i; // must be final because another thread "would like" to use that resource
                            es.execute(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        objects.get(index).deleteInBackground().waitForCompletion();
                                    } catch (InterruptedException deletionException) {
                                        deletionException.printStackTrace();
                                    }
                                }
                            });
                        }
                        es.shutdown();
                        try {
                            finished = es.awaitTermination(1, TimeUnit.MINUTES);
                        } catch (InterruptedException waitingException) {
                            waitingException.printStackTrace();
                        }
                        /* all tasks have finished or the time has been reached */

                        if (finished) {
                            // (3.1) Save...
                            new ParseSyncedObject(innerObject.getPackageName(), innerObject.getKey(), innerObject.getValue()).saveInBackground(new SaveCallback() {
                                @Override
                                public void done(ParseException e) {
                                    // (4) Notify for completion with callback
                                    if (saveCallback != null) {
                                        saveCallback.done(e);
                                    }
                                }
                            });
                        } else {
                            if (saveCallback != null) {
                                saveCallback.done(new ParseException(-1, "Couldn't delete (duplications) and save"));
                            }
                        }
                    } else { // Already unique, brand new
                        // (3) Save...
                        innerObject.saveInBackground(new SaveCallback() {
                            @Override
                            public void done(ParseException e) {
                                // (4) Notify for completion with callback
                                if (saveCallback != null) {
                                    saveCallback.done(e);
                                }
                            }
                        });
                    }
                }
            });
        }

        public String getPackageName() {
            return innerObject.getPackageName();
        }
    }
}