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
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Joe Portner
 */
public class DatabaseHandler extends SQLiteOpenHelper {
    private static final String TAG = DatabaseHandler.class.getName();

    public static final String DB_NAME = "org.mitre.svmp.db";
    public static final int DB_VERSION = 4;

    public static final int TABLE_CONNECTIONS = 0;
    public static final String[] Tables = new String[]{
        "Connections"
    };

    // this is used to generate queries to create new tables with appropriate constraints
    // foreign keys constraints are automatically added for matching table names
    // see SQLite Datatypes: http://www.sqlite.org/datatype3.html
    public static final String[][][] TableColumns = new String[][][] {
        // column, type, constraints
        {
            {"ConnectionID", "INTEGER", "PRIMARY KEY"},
            {"Description", "TEXT"},
            {"Username", "TEXT"},
            {"Host", "TEXT"},
            {"Port", "INTEGER"},
            {"EncryptionType", "INTEGER"},
            {"Domain", "TEXT"},
            {"AuthType", "INTEGER DEFAULT 1"}
        }
    };

    private SQLiteDatabase db;
    private Context context;

    public DatabaseHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
    }

    private SQLiteDatabase getDb() {
        if( db == null )
            db = this.getWritableDatabase();
        return db;
    }

    public void close() {
        // cleanup
        if (db != null && db.isOpen())
            db.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // loop through the tables and create them from the TableColumns jagged array
        for (int i = 0; i < Tables.length; i++)
            createTable(i, db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        switch(oldVersion) {
            case 1:
                // changed Connections table, recreate it
                recreateTable(TABLE_CONNECTIONS, db);
            case 2:
                addTableColumn(TABLE_CONNECTIONS, 6, "''", db);
                addTableColumn(TABLE_CONNECTIONS, 7, "0", db);
            case 3:
                // changed auth types, now the IDs begin with 1, not 0
                db.execSQL("UPDATE Connections SET AuthType=1 WHERE AuthType=0;");
            default:
                break;
        }
    }

    // This is needed to enable cascade operations for foreign keys (delete and update)
    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            // Enable foreign key constraints
            db.execSQL("PRAGMA foreign_keys=ON;");
        }
    }

    private void addTableColumn(int tableID, int colNum, String defaultVal, SQLiteDatabase db) {
        String query = String.format("ALTER TABLE %s ADD COLUMN %s %s DEFAULT %s",
                Tables[TABLE_CONNECTIONS], // table name
                TableColumns[TABLE_CONNECTIONS][colNum][0], // column name
                TableColumns[TABLE_CONNECTIONS][colNum][1], // column type
                defaultVal);
        // try to create the table with the constructed query
        try {
            db.execSQL(query);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // if the table exists, drop it; then, create the table again
    private void recreateTable(int tableID, SQLiteDatabase db) {
        // try to drop the table
        try {
            db.execSQL(String.format("DROP TABLE IF EXISTS %s", Tables[tableID]));
        } catch (Exception e) {
            e.printStackTrace();
        }
        // create the table again
        createTable(tableID, db);
    }

    // creates a table, along with constraints, based on the TableColumns jagged array
    private void createTable(int tableID, SQLiteDatabase db) {
        StringBuilder query = new StringBuilder();
        StringBuilder primaryKeys = new StringBuilder();
        StringBuilder foreignKeys = new StringBuilder();

        query.append(String.format("CREATE TABLE %s (", Tables[tableID]));

        for (int i = 0; i < TableColumns[tableID].length; i++) {
            if (i > 0)
                query.append(", ");

            // append column name, type, and constraints
            for (int j = 0; j < TableColumns[tableID][i].length; j++) {
                // if this is a primary key option, add it to the string and save it for the end
                if (j == 2 && TableColumns[tableID][i][j].equals("PRIMARY KEY")) {
                    if (primaryKeys.length() > 0)
                        primaryKeys.append(", ");
                    primaryKeys.append(TableColumns[tableID][i][0]);
                }
                // if this is another option (UNIQUE, NOT NULL, etc) add it now
                else {
                    if (j > 0)
                        query.append(" ");
                    query.append(TableColumns[tableID][i][j]);
                }
            }

            // loop through tables to construct foreign key constraints (looks at 1st column/primary key of each table)
            String foreignTable = "";
            for (int k = 0; k < Tables.length; k++) {

                if (k < tableID // skip the same table, skip tables that haven't been added yet
                        && TableColumns[k][0][0].equals(TableColumns[tableID][i][0])
                        && TableColumns[k][0].length > 2
                        && TableColumns[k][0][2].equals("PRIMARY KEY")) {
                    foreignTable = Tables[k];
                    break;
                }
            }
            if (foreignTable.length() > 0) {
                String constraint = String.format(", FOREIGN KEY (%s) REFERENCES %s (%s) ON DELETE CASCADE ON UPDATE CASCADE",
                        TableColumns[tableID][i][0],
                        foreignTable,
                        TableColumns[tableID][i][0]);
                foreignKeys.append(constraint);
            }
        }

        // append primary key constraint(s)
        if( primaryKeys.length() > 0 ) {
            query.append(String.format(", PRIMARY KEY (%s)", primaryKeys.toString()));
        }

        query.append(foreignKeys.toString());
        query.append(");");

        Log.d(TAG, String.format("Creating table: %s", query.toString()));

        // try to create the table with the constructed query
        try {
            db.execSQL(query.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*private void recreateTables(SQLiteDatabase db) {
        // drop older table(s) if they exist
        for (String table : Tables)
            db.execSQL("DROP TABLE IF EXISTS " + table);

        // create tables again
        onCreate(db);
    }*/

    public List<ConnectionInfo> getConnectionInfoList() {
        // run the query
        Cursor cursor = _getConnectionInfoCursor(null);

        // try to get results and add ConnectionInfo objects to the list
        List<ConnectionInfo> connectionInfoList = new ArrayList<ConnectionInfo>();
        while (cursor.moveToNext()) {
            // construct a new ConnectionInfo from the cursor
            ConnectionInfo connectionInfo = makeConnectionInfo(cursor);

                // add the ConnectionInfo to the list
            if (connectionInfo != null)
                connectionInfoList.add(connectionInfo);
        }

        // cleanup
        try {
            cursor.close();
        } catch (Exception e) {
            // don't care
        }

        return connectionInfoList;
    }

    // returns a ConnectionInfo that matches the given ConnectionID (null if none found)
    public ConnectionInfo getConnectionInfo(int id) {
        return _getConnectionInfo("ConnectionID=?", String.valueOf(id));
    }

    // returns a ConnectionInfo that does NOT match the given ConnectionID, but matches the given description (null if none found)
    public ConnectionInfo getConnectionInfo(int id, String description) {
        return _getConnectionInfo("ConnectionID!=? AND LOWER(Description)=TRIM(LOWER(?))",
                String.valueOf(id), description);
    }

    private ConnectionInfo _getConnectionInfo(String selection, String... selectionArgs) {
        // run the query
        Cursor cursor = _getConnectionInfoCursor(selection, selectionArgs);

        // try to get results and make a ConnectionInfo object to return
        ConnectionInfo connectionInfo = null;
        if (cursor.moveToFirst())
            connectionInfo = makeConnectionInfo(cursor);

        // cleanup
        try {
            cursor.close();
        } catch (Exception e) {
            // don't care
        }

        return connectionInfo;
    }

    // selection and selectionArgs are optional
    private Cursor _getConnectionInfoCursor(String selection, String... selectionArgs) {
        // prepared statement for speed and security
        Cursor cursor = getDb().query(
                Tables[TABLE_CONNECTIONS], // table
                null, // columns (null == "*")
                selection, // selection ('where' clause)
                selectionArgs, // selection args
                null, // group by
                null, // having
                null // order by
        );

        return cursor;
    }

    private ConnectionInfo makeConnectionInfo(Cursor cursor) {
        try {
            // get values from query
            int connectionID = cursor.getInt(0);
            String description = cursor.getString(1);
            String username = cursor.getString(2);
            String host = cursor.getString(3);
            int port = cursor.getInt(4);
            int encryptionType = cursor.getInt(5);
            String domain = cursor.getString(6);
            int authType = cursor.getInt(7);

            return new ConnectionInfo(connectionID, description, username, host, port, encryptionType, domain,
                    authType);
        } catch( Exception e ) {
            e.printStackTrace();
            return null;
        }
    }

    protected long insertConnectionInfo(ConnectionInfo connectionInfo) {
        // attempt insert
        return insertRecord(TABLE_CONNECTIONS, makeContentValues(connectionInfo));
    }

    private long insertRecord(int tableID, ContentValues contentValues) {
        long result = -1;

        // attempt insert
        try {
            result = getDb().insert(Tables[tableID], null, contentValues);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // return result
        return result;
    }

    protected long updateConnectionInfo(ConnectionInfo connectionInfo) {
        // attempt insert
        return updateRecord(
                TABLE_CONNECTIONS,
                makeContentValues(connectionInfo),
                "ConnectionID=?",
                String.valueOf(connectionInfo.getConnectionID())
        );
    }

    private long updateRecord(int tableID, ContentValues contentValues, String whereClause, String... whereArgs) {
        long result = -1;

        // attempt update
        try {
            result = getDb().update(Tables[tableID], contentValues, whereClause, whereArgs);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // return result
        return result;
    }

    private ContentValues makeContentValues(ConnectionInfo connectionInfo) {
        ContentValues contentValues = new ContentValues();

        if (connectionInfo != null) {
            contentValues.put("Description", connectionInfo.getDescription());
            contentValues.put("Username", connectionInfo.getUsername());
            contentValues.put("Host", connectionInfo.getHost());
            contentValues.put("Port", connectionInfo.getPort());
            contentValues.put("EncryptionType", connectionInfo.getEncryptionType());
            contentValues.put("Domain", connectionInfo.getDomain());
            contentValues.put("AuthType", connectionInfo.getAuthType());
        }

        return contentValues;
    }

    protected long deleteConnectionInfo(int connectionID) {
        // attempt delete
        return deleteRecord(TABLE_CONNECTIONS, "ConnectionID=?", String.valueOf(connectionID));
    }

    private long deleteRecord(int tableID, String whereClause, String... whereArgs) {
        long result = -1;

        // attempt delete
        try {
            result = getDb().delete(Tables[tableID], whereClause, whereArgs);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // return result
        return result;
    }
}