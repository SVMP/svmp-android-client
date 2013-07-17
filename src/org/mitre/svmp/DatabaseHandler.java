/*
 Copyright 2013 The MITRE Corporation, All Rights Reserved.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this work except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package org.mitre.svmp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Joe Portner
 */
public class DatabaseHandler extends SQLiteOpenHelper {
    public static final String DB_NAME="org.mitre.svmp.db";
    public static final int DB_VERSION = 2;

    public static final int TABLE_CONNECTIONS = 0;
    public static final String[] Tables = new String[]{
            "Connections"
    };

    private static final String[] CreateTableQueries = new String[]{
            "CREATE TABLE " + Tables[TABLE_CONNECTIONS] + " (" +
                    "ID INTEGER PRIMARY KEY, " +
                    "Description TEXT NOT NULL, " +
                    "Username TEXT NOT NULL, " +
                    "Host TEXT NOT NULL, " +
                    "Port INTEGER, " +
                    "EncryptionType INTEGER" +
                    ");"
    };

    private SQLiteDatabase db;

    public DatabaseHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    public void close() {
        // cleanup
        try {
            if( db != null )
                db.close();
        } catch( Exception e ) {
            // don't care
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // loop through create table query strings and execute them
        for( String query : CreateTableQueries ) {
            try {
                db.execSQL(query);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        recreateTables(db);
    }

    private void recreateTables(SQLiteDatabase db) {
        // drop older table(s) if they exist
        for( String table : Tables )
            db.execSQL("DROP TABLE IF EXISTS " + table);

        // create tables again
        onCreate(db);
    }

    public List<ConnectionInfo> getConnectionInfoList() {
        db = this.getWritableDatabase();

        // prepared statement for speed and security
        Cursor cursor = db.query(
                Tables[TABLE_CONNECTIONS], // table
                null, // columns (null == "*")
                null, // selection ('where' clause)
                null, // selection args
                null, // group by
                null, // having
                "Description" // order by
        );

        // try to get results and add ConnectionInfo objects to the list
        List<ConnectionInfo> connectionInfoList = new ArrayList<ConnectionInfo>();
        while (cursor.moveToNext()) {
            try {
                // construct a new ConnectionInfo from the cursor
                ConnectionInfo connectionInfo = makeConnectionInfo(cursor);

                // add the ConnectionInfo to the list
                connectionInfoList.add( connectionInfo );
            }
            catch( Exception e ) {
                e.printStackTrace();
            }
        }

        // cleanup
        try {
            cursor.close();
        } catch( Exception e ) {
            // don't care
        }

        return connectionInfoList;
    }

    // returns a ConnectionInfo that matches the given ID (null if none found)
    public ConnectionInfo getConnectionInfo(int id) {
        return _getConnectionInfo("ID=?", new String[]{ String.valueOf(id) });
    }

    // returns a ConnectionInfo that does NOT match the given ID, but matches the given description (null if none found)
    public ConnectionInfo getConnectionInfo(int id, String description) {
        return _getConnectionInfo("ID!=? AND LOWER(Description)=TRIM(LOWER(?))",
                new String[]{ String.valueOf(id), description });
    }

    private ConnectionInfo _getConnectionInfo(String selection, String[] selectionArgs) {
        db = this.getWritableDatabase();

        // prepared statement for speed and security
        Cursor cursor = db.query(
                Tables[TABLE_CONNECTIONS], // table
                null, // columns (null == "*")
                selection, // selection ('where' clause)
                selectionArgs, // selection args
                null, // group by
                null, // having
                null // order by
        );

        // try to get results and make a ConnectionInfo object to return
        ConnectionInfo connectionInfo = null;
        if (cursor.moveToFirst()) {
            try {
                // construct a new ConnectionInfo from the cursor
                connectionInfo = makeConnectionInfo(cursor);
            }
            catch( Exception e ) {
                e.printStackTrace();
            }
        }

        // cleanup
        try {
            cursor.close();
        } catch( Exception e ) {
            // don't care
        }

        return connectionInfo;
    }

    private ConnectionInfo makeConnectionInfo(Cursor cursor) {
        // get values from query
        int id = cursor.getInt(0);
        String description = cursor.getString(1);
        String username = cursor.getString(2);
        String host = cursor.getString(3);
        int port = cursor.getInt(4);
        int encryptionType = cursor.getInt(5);

        return new ConnectionInfo(id, description, username, host, port, encryptionType);
    }

    protected long insertConnectionInfo( ConnectionInfo connectionInfo ) {
        // attempt insert
        return insertRecord( TABLE_CONNECTIONS, makeContentValues(connectionInfo) );
    }

    private long insertRecord( int tableId, ContentValues contentValues ) {
        long result = -1;
        db = this.getWritableDatabase();

        // attempt insert
        try {
            result = db.insert(Tables[tableId], null, contentValues);

        } catch( Exception e ) {
            e.printStackTrace();
        }

        // return result
        return result;
    }


    protected long updateConnectionInfo( ConnectionInfo connectionInfo ) {
        // attempt insert
        return updateRecord(
                TABLE_CONNECTIONS,
                makeContentValues(connectionInfo),
                "ID=?",
                new String[]{ String.valueOf(connectionInfo.getID()) }
        );
    }

    private long updateRecord( int tableId, ContentValues contentValues, String whereClause, String[] whereArgs ) {
        long result = -1;
        db = this.getWritableDatabase();

        // attempt update
        try {
            result = db.update(Tables[tableId], contentValues, whereClause, whereArgs);

        } catch( Exception e ) {
            e.printStackTrace();
        }

        // return result
        return result;
    }

    private ContentValues makeContentValues(ConnectionInfo connectionInfo) {
        ContentValues contentValues = new ContentValues();

        if( connectionInfo != null ) {
            contentValues.put( "Description", connectionInfo.getDescription() );
            contentValues.put( "Username", connectionInfo.getUsername() );
            contentValues.put( "Host", connectionInfo.getHost() );
            contentValues.put( "Port", connectionInfo.getPort() );
            contentValues.put( "EncryptionType", connectionInfo.getEncryptionType() );
        }

        return contentValues;
    }

    protected long deleteConnectionInfo( int id ) {
        // attempt delete
        return deleteRecord(
                Tables[TABLE_CONNECTIONS],
                "ID=?",
                new String[]{ String.valueOf(id) });
    }

    private long deleteRecord( String table, String whereClause, String[] whereArgs ) {
        long result = -1;
        db = this.getWritableDatabase();

        // attempt delete
        try {
            result = db.delete(table, whereClause, whereArgs);
        } catch( Exception e ) {
            e.printStackTrace();
        }

        // return result
        return result;
    }
}