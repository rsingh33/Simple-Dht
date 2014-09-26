
package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {
    volatile int querycounter = 0;
    static final String TAG = "dht";
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final String MYPORT_0 = "5554";
    static final String MYPORT_1 = "5556";
    static final String MYPORT_2 = "5558";
    static final String MYPORT_3 = "5560";
    static final String MYPORT_4 = "5562";
    static final int SERVER_PORT = 10000;
    public static final Uri CONTENT_URI = Uri
            .parse("content://edu.buffalo.cse.cse486586.simpledht.provider");

    private SQLiteDatabase sqlDb;
    public static String PREDECESSOR = "";
    public static String SUCCESSOR = "";
    public static String NODE_ID = "";
    int messageCount;
    ArrayList<String> hold = new ArrayList<String>();
    public static String portNo;
    private static final String REPLY = "response";
    private static final String QUERY_COMP = "querycom";
    private static final String REQUEST = "requests";
    private static final String INSERT = "insertin";
    private static final String QUERY = "querymsg";
    private static final String QUERY_RESPONSE = "queryrsp";

    private SQLiteOpenHelper dhtHelper;
    private String DELETE = "deleteit";

    @Override
    public boolean onCreate() {

        dhtHelper = new DhtHelper(getContext());
        SimpleDhtProvider.portNo = getMyPort();
        // change 1
        try {
            SimpleDhtProvider.NODE_ID = genHash(SimpleDhtProvider.portNo);
        } catch (NoSuchAlgorithmException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        if (SimpleDhtProvider.portNo.equals(MYPORT_0)) {
            SimpleDhtProvider.PREDECESSOR = MYPORT_0;
            SimpleDhtProvider.SUCCESSOR = MYPORT_0;

            (new Thread(new ServerTaskRunnable(null))).start();

        } else {
            SimpleDhtProvider.PREDECESSOR = SimpleDhtProvider.portNo;
            SimpleDhtProvider.SUCCESSOR = SimpleDhtProvider.portNo;

            (new Thread(new ServerTaskRunnable(null))).start();

            String message = SimpleDhtProvider.portNo + MYPORT_0 + REQUEST;

            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, message);

        }

        return true;
    }

    public String getMyPort() {
        TelephonyManager tel = (TelephonyManager)this.getContext().getSystemService(
                Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        return portStr;

    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        SQLiteDatabase sqlDb = dhtHelper.getReadableDatabase();
        if (selection.equals("@")) {
            count = sqlDb.delete(DhtHelper.TABLE_NAME, null, null);
        } else if (selection.equals("*")) {
            count = sqlDb.delete(DhtHelper.TABLE_NAME, null, null);
            if (!(SimpleDhtProvider.portNo.equals(SimpleDhtProvider.SUCCESSOR))) {
                String message = SimpleDhtProvider.portNo + SimpleDhtProvider.SUCCESSOR + DELETE
                        + "*";
                new Thread(new ClientTaskRunnable(message)).start();

            }

        } else {
            String[] records = { DhtHelper.KEY, DhtHelper.VALUE };

            selectionArgs = new String[] { selection };

            count = sqlDb.delete(DhtHelper.TABLE_NAME, records[0] + "=?", selectionArgs);
            if (count == 0) {
                String message = SimpleDhtProvider.portNo + SimpleDhtProvider.SUCCESSOR + DELETE
                        + selection;
                new Thread(new ClientTaskRunnable(message)).start();
            }
        }
        return count;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        messageCount = 0;
        String key = (String)values.get("key");
        String value = (String)values.get("value");

        String insertMessage = SimpleDhtProvider.portNo + SimpleDhtProvider.SUCCESSOR + INSERT
                + key + ":" + value;
        // Log.d(TAG, "this is insert message " + " " + insertMessage);
        actualInsertOp(insertMessage);

        return uri;

    }

    public boolean querying(String string) {

        try {
            String hashedKey1 = genHash(string);
            String currentNode1 = SimpleDhtProvider.NODE_ID;
            String currentPredecessor1 = genHash(SimpleDhtProvider.PREDECESSOR);
            Log.d(TAG, "I AM in node =  " + SimpleDhtProvider.portNo + " " + currentNode1);
            Log.d(TAG, "I AM in predecessor =  " + SimpleDhtProvider.PREDECESSOR + " "
                    + currentPredecessor1);
            Log.d(TAG, "I AM in Querying key = " + string + " " + hashedKey1);

            boolean caseInsert1 = ((SimpleDhtProvider.portNo
                    .equalsIgnoreCase(SimpleDhtProvider.PREDECESSOR)) && (SimpleDhtProvider.portNo
                            .equalsIgnoreCase(SimpleDhtProvider.SUCCESSOR)));

            boolean caseInsert2 = ((hashedKey1.compareTo(currentNode1) <= 0) && (hashedKey1
                    .compareTo(currentPredecessor1) > 0));

            boolean caseInsert3 = ((currentNode1.compareTo(currentPredecessor1) < 0) && (hashedKey1
                    .compareTo(currentPredecessor1) > 0));

            boolean caseInsert4 = ((currentNode1.compareTo(currentPredecessor1) < 0) && (hashedKey1
                    .compareTo(currentNode1) <= 0));

            if (caseInsert1 || caseInsert2 || caseInsert3 || caseInsert4) {
                return true;
            }
        } catch (NoSuchAlgorithmException e) {

            e.printStackTrace();
        }
        return false;

    }

    public void actualInsertOp(String message) {

        String key = message.substring(16, message.indexOf(":"));

        String value = message.substring(message.indexOf(":") + 1);

        try {
            String hashedKey = genHash(key);
            String currentNode = SimpleDhtProvider.NODE_ID;// already hashed
            // above
            String currentPredecessor = genHash(SimpleDhtProvider.PREDECESSOR);

            boolean caseInsert1 = ((SimpleDhtProvider.portNo
                    .equalsIgnoreCase(SimpleDhtProvider.PREDECESSOR)) && (SimpleDhtProvider.portNo
                            .equalsIgnoreCase(SimpleDhtProvider.SUCCESSOR)));

            boolean caseInsert2 = ((hashedKey.compareTo(currentNode) <= 0) && (hashedKey
                    .compareTo(currentPredecessor) > 0));

            boolean caseInsert3 = ((currentNode.compareTo(currentPredecessor) < 0) && (hashedKey
                    .compareTo(currentPredecessor) > 0));
            boolean caseInsert4 = ((currentNode.compareTo(currentPredecessor) < 0) && (hashedKey
                    .compareTo(currentNode) <= 0));

            if ((caseInsert1) || (caseInsert2) || (caseInsert3) || (caseInsert4)) {
                Log.d(TAG, "I am in if part " + currentNode + " " + hashedKey);

                ContentValues cv = new ContentValues();
                cv.put("key", key);
                cv.put("value", value);
                System.out.println(key);
                sqlDb = dhtHelper.getWritableDatabase();
                // Doubt full
                sqlDb.insertWithOnConflict(DhtHelper.TABLE_NAME, null, cv,
                        SQLiteDatabase.CONFLICT_REPLACE);

            } else {
                // messageCount++;
                String insertMessage = SimpleDhtProvider.portNo + SimpleDhtProvider.SUCCESSOR
                        + INSERT + key + ":" + value;

                (new Thread(new ClientTaskRunnable(insertMessage))).start();
            }
        } catch (NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // Log.d(TAG, "Succ = " + SUCCESSOR + "pred = " + PREDECESSOR);
        MatrixCursor matrixCursor = new MatrixCursor(
                new String[] { DhtHelper.KEY, DhtHelper.VALUE });
        Log.d(TAG, "Selection pparameters " + selection);
        SQLiteDatabase sqlDb = dhtHelper.getReadableDatabase();
        Cursor cursor = null;
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(DhtHelper.TABLE_NAME);
        String[] keyCol = { DhtHelper.KEY, DhtHelper.VALUE };
        selectionArgs = new String[] { selection };

        if (selection.equals("@")) {
            Log.d(TAG, "Inside the @ block");
            cursor = sqlDb.query(DhtHelper.TABLE_NAME, keyCol, null, null, null, null, null);
            Log.d(TAG, "cursor is " + cursor.getCount());
            return cursor;
        }

        else if (selection.equals("*")) {
            Log.d(TAG, "Inside the * block");

            cursor = sqlDb.query(DhtHelper.TABLE_NAME, keyCol, null, null, null, null, null);
            Log.d(TAG, "Cursor * count is " + cursor.getCount());
            Log.d(TAG, "" + " PRed " + PREDECESSOR + " Succ " + SUCCESSOR);
            if (!SimpleDhtProvider.portNo.equals(PREDECESSOR)) {
                querycounter = 1;
                Log.d(TAG, "In current != pred");
                hold.clear();

                String message = SimpleDhtProvider.portNo + SimpleDhtProvider.SUCCESSOR + QUERY
                        + "*";
                new Thread(new ClientTaskRunnable(message)).start();
                Log.d(TAG, "message in * is " + message);

                Log.d(TAG, "new matrix counter is " + cursor.getCount());
                cursor.moveToFirst();
                for (int i = 0; i < cursor.getCount(); i++, cursor.moveToNext()) {
                    Log.d(TAG, "I am in the matrix cursor for loop");
                    matrixCursor.addRow(new String[] {
                            cursor.getString(cursor.getColumnIndex(DhtHelper.KEY)),
                            cursor.getString(cursor.getColumnIndex(DhtHelper.VALUE)) });
                }

                Log.d(TAG, "new matrix counter is " + matrixCursor.getCount());
                querycounter = 1;
                while (querycounter == 1) {
                    // Log.d(TAG, "In while");

                }
                Log.d(TAG, "hold size is " + hold.size());
                for (int i = 0; i < hold.size(); i++) {
                    String[] arr = hold.get(i).split(":");
                    matrixCursor.addRow(arr);
                }
                return matrixCursor;
            }// change it

            return cursor;

        } else {
            Log.v(TAG, "part key");
            if (querying(selection)) {
                Log.v(TAG, "found in querying");
                Log.d(TAG, "" + keyCol);

                selectionArgs = new String[] { selection };
                cursor = sqlDb.query(DhtHelper.TABLE_NAME, keyCol, keyCol[0] + "=?", selectionArgs,
                        null, null, null);
                Log.d(TAG, "val of cursor is " + cursor.getCount());

                return cursor;

            } else {
                Log.v(TAG, "not found in querying222");

                String message = SimpleDhtProvider.portNo + SimpleDhtProvider.SUCCESSOR
                        + SimpleDhtProvider.QUERY + selection;
                Log.d(TAG, message);
                new Thread(new ClientTaskRunnable(message)).start();
                Log.d(TAG, "sent to successor");

                hold.clear();
                querycounter = 1;
                while (querycounter == 1) {
                    // Log.d(TAG,"IN while" );

                }
                // Log.d(TAG,"After while");
                for (int i = 0; i < hold.size(); i++) {
                    String[] arr = hold.get(i).split(":");
                    Log.d(TAG, "value of array" + arr[0] + arr[1]);
                    matrixCursor.addRow(arr);
                }
                Log.d(TAG, "" + matrixCursor.getCount());
                // change it

                return matrixCursor;
            }
        }
    }

    // return cursor;

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    class DhtHelper extends SQLiteOpenHelper {
        public static final String DATABASE = "myDb.db";
        public static final String TABLE_NAME = "myTable";
        public static final int DB_VERSION = 8;
        public static final String KEY = "key";
        public static final String VALUE = "value";
        private String CREATE_TABLE = String.format("CREATE TABLE %s "
                + "(%s text primary key, %s text)", DhtHelper.TABLE_NAME, DhtHelper.KEY,
                DhtHelper.VALUE);
        private Context context;

        static final String TAG = "DBHelper";
        private static final String DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

        public DhtHelper(Context context) {
            super(context, DhtHelper.DATABASE, null, DhtHelper.DB_VERSION);
            this.context = context;

        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // TODO Auto-generated method stub

            db.execSQL(CREATE_TABLE);

        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO Auto-generated method stub
            db.execSQL(DROP_TABLE);
            onCreate(db);
        }

    }

    private class ServerTaskRunnable implements Runnable {
        String messageReceived;
        String firstMessage;
        String secondMessage;

        public ServerTaskRunnable(String message) {
            messageReceived = message;
        }

        @Override
        public void run() {
            // TODO Auto-generated method stub

            ServerSocket serverSocket = null;

            try {
                serverSocket = new ServerSocket(10000);

                Socket clientSocket;
                while (true) {

                    clientSocket = serverSocket.accept();

                    BufferedReader buffer = new BufferedReader(new InputStreamReader(
                            clientSocket.getInputStream()));
                    messageReceived = buffer.readLine();
                    Log.d(TAG, "Message in server task " + messageReceived);
                    String node = genHash(messageReceived.substring(0, 4));

                    node = genHash(messageReceived.substring(0, 4));

                    if (messageReceived.substring(8, 16).equals(REQUEST)) {
                        if (SimpleDhtProvider.SUCCESSOR.equalsIgnoreCase(SimpleDhtProvider.portNo)
                                && SimpleDhtProvider.PREDECESSOR
                                .equalsIgnoreCase(SimpleDhtProvider.portNo)) {
                            firstMessage = SimpleDhtProvider.portNo
                                    + messageReceived.substring(0, 4) + REPLY
                                    + SimpleDhtProvider.portNo + ":" + SimpleDhtProvider.portNo;

                            // Log.d(TAG, "Successor := " +
                            // SimpleDhtProvider.portNo);

                            new ClientTask().executeOnExecutor(ClientTask.THREAD_POOL_EXECUTOR,
                                    firstMessage);

                            SimpleDhtProvider.PREDECESSOR = messageReceived.substring(0, 4);
                            SimpleDhtProvider.SUCCESSOR = messageReceived.substring(0, 4);

                        } else {
                            if (SimpleDhtProvider.NODE_ID
                                    .compareTo(genHash(SimpleDhtProvider.SUCCESSOR)) < 0) {
                                if (node.compareTo(SimpleDhtProvider.NODE_ID) > 0
                                        && node.compareTo(genHash(SimpleDhtProvider.SUCCESSOR)) < 0) {

                                    firstMessage = SimpleDhtProvider.portNo
                                            + messageReceived.substring(0, 4) + REPLY
                                            + SimpleDhtProvider.SUCCESSOR + ":"
                                            + SimpleDhtProvider.portNo;
                                    // Log.d(TAG, "Successor := " +
                                    // SimpleDhtProvider.SUCCESSOR);
                                    new ClientTask().executeOnExecutor(
                                            ClientTask.THREAD_POOL_EXECUTOR, firstMessage);

                                    secondMessage = SimpleDhtProvider.portNo
                                            + SimpleDhtProvider.SUCCESSOR + REPLY + "0" + ":"
                                            + messageReceived.substring(0, 4);

                                    new ClientTask().executeOnExecutor(
                                            ClientTask.THREAD_POOL_EXECUTOR, secondMessage);

                                    SimpleDhtProvider.SUCCESSOR = messageReceived.substring(0, 4);

                                } else {
                                    firstMessage = messageReceived.substring(0, 4)
                                            + SimpleDhtProvider.SUCCESSOR + REQUEST;

                                    new ClientTask().executeOnExecutor(
                                            ClientTask.THREAD_POOL_EXECUTOR, firstMessage);

                                }

                            } else {
                                if (SimpleDhtProvider.NODE_ID.compareTo(node) < 0
                                        || genHash(SimpleDhtProvider.SUCCESSOR).compareTo(node) > 0) {

                                    firstMessage = SimpleDhtProvider.portNo
                                            + messageReceived.substring(0, 4) + REPLY
                                            + SimpleDhtProvider.SUCCESSOR + ":"
                                            + SimpleDhtProvider.portNo;
                                    new ClientTask().executeOnExecutor(
                                            ClientTask.THREAD_POOL_EXECUTOR, firstMessage);

                                    secondMessage = SimpleDhtProvider.portNo
                                            + SimpleDhtProvider.SUCCESSOR + REPLY + "0" + ":"
                                            + messageReceived.substring(0, 4);
                                    new ClientTask().executeOnExecutor(
                                            ClientTask.THREAD_POOL_EXECUTOR, secondMessage);

                                    SimpleDhtProvider.SUCCESSOR = messageReceived.substring(0, 4);

                                } else {
                                    firstMessage = messageReceived.substring(0, 4)
                                            + SimpleDhtProvider.SUCCESSOR + REQUEST;

                                    new ClientTask().executeOnExecutor(
                                            ClientTask.THREAD_POOL_EXECUTOR, firstMessage);
                                }

                            }
                        }
                    }

                    else if (messageReceived.substring(8, 16).equalsIgnoreCase(REPLY)) {
                        if (!(messageReceived.substring(16, 17).equals("0"))) {
                            SimpleDhtProvider.SUCCESSOR = messageReceived.substring(16, 20);
                        }

                        SimpleDhtProvider.PREDECESSOR = messageReceived.substring(messageReceived
                                .indexOf(":") + 1);
                    }

                    else if (messageReceived.substring(8, 16).equalsIgnoreCase(INSERT)) {
                        actualInsertOp(messageReceived);

                    } else if (messageReceived.substring(8, 16).equalsIgnoreCase(
                            SimpleDhtProvider.QUERY)) {
                        Log.d(TAG, "msg type is QUERY" + messageReceived);
                        if (messageReceived.substring(0, 4).equals(SimpleDhtProvider.portNo)) {
                            System.out.println("I am the origin");
                        } else {
                            Log.d(TAG, "I am in else part of Query messsage");
                            if (messageReceived.substring(16).equals("*")) {
                                SQLiteDatabase sqlDb = dhtHelper.getReadableDatabase();
                                Cursor cursor = sqlDb.query(DhtHelper.TABLE_NAME, null, null, null,
                                        null, null, null);
                                cursor.moveToFirst();
                                Log.d(TAG, "" + cursor.getCount());
                                for (int i = 1; i <= cursor.getCount(); i++, cursor.moveToNext()) {
                                    String message = messageReceived.substring(0, 4)
                                            + messageReceived.substring(0, 4)
                                            + QUERY_RESPONSE
                                            + cursor.getString(cursor.getColumnIndex(DhtHelper.KEY))
                                            + ":"
                                            + cursor.getString(cursor
                                                    .getColumnIndex(DhtHelper.VALUE));
                                    Log.d(TAG, message);
                                    new Thread(new ClientTaskRunnable(message)).start();
                                }
                                Log.d(TAG, "succ " + SimpleDhtProvider.SUCCESSOR);
                                if (messageReceived.substring(0, 4).equals(
                                        SimpleDhtProvider.SUCCESSOR)) {

                                    String message = SimpleDhtProvider.portNo
                                            + messageReceived.substring(0, 4) + QUERY_COMP;
                                    Log.d(TAG, message);
                                    new Thread(new ClientTaskRunnable(message)).start();
                                } else {
                                    String message = messageReceived.substring(0, 4)
                                            + SimpleDhtProvider.SUCCESSOR + QUERY
                                            + messageReceived.substring(16);
                                    new Thread(new ClientTaskRunnable(message)).start();
                                }
                            } else {

                                if (querying(messageReceived.substring(16))) {

                                    Log.d(TAG,
                                            "message selection is " + messageReceived.substring(16));
                                    SQLiteDatabase sqlDb = dhtHelper.getReadableDatabase();
                                    String[] columns = new String[] { DhtHelper.KEY,
                                            DhtHelper.VALUE };

                                    Cursor cursor = sqlDb.query(DhtHelper.TABLE_NAME, columns,
                                            columns[0] + "=?",
                                            new String[] { messageReceived.substring(16) }, null,
                                            null, null);
                                    Log.d(TAG, "HERE Lies the problem");

                                    cursor.moveToNext();

                                    String message = messageReceived.substring(0, 4)
                                            + messageReceived.substring(0, 4) + QUERY_RESPONSE
                                            + cursor.getString(0) + ":" + cursor.getString(1);

                                    Log.d(TAG, "Message to be pr" + message);
                                    new Thread(new ClientTaskRunnable(message)).start();
                                    String message1 = SimpleDhtProvider.portNo
                                            + messageReceived.substring(0, 4) + QUERY_COMP;
                                    new Thread(new ClientTaskRunnable(message1)).start();

                                } else {
                                    Log.d(TAG, "If not there then i m here");
                                    String message = messageReceived.substring(0, 4)
                                            + SimpleDhtProvider.SUCCESSOR
                                            + messageReceived.substring(8);
                                    new Thread(new ClientTaskRunnable(message)).start();

                                }

                            }
                        }
                    } else if (messageReceived.substring(8, 16).equals(QUERY_COMP)) {
                        Log.d(TAG, "msg type is QUERY_COMPLETE" + messageReceived);
                        querycounter = 0;

                    } else if (messageReceived.substring(8, 16).equals(QUERY_RESPONSE)) {
                        Log.d(TAG, "msg type is " + messageReceived);
                        hold.add(messageReceived.substring(16));
                    }

                    clientSocket.close();
                    buffer.close();

                }
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            try {
                serverSocket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
    }

    private class ClientTaskRunnable implements Runnable {
        int receiver;
        String msgToSend;

        public ClientTaskRunnable(String message) {
            // Log.d(TAG, "ClientTaskRunnable()::" + message);
            msgToSend = message;
        }

        @Override
        public void run() {
            // Log.d(TAG, "Client task is running::" + msgToSend);
            try {
                if (msgToSend.substring(4, 8).equals(MYPORT_0)) {
                    receiver = Integer.parseInt(REMOTE_PORT0);

                }

                else if (msgToSend.substring(4, 8).equals(MYPORT_1)) {
                    receiver = Integer.parseInt(REMOTE_PORT1);

                }

                else if (msgToSend.substring(4, 8).equals(MYPORT_2)) {
                    receiver = Integer.parseInt(REMOTE_PORT2);

                }

                else if (msgToSend.substring(4, 8).equals(MYPORT_3)) {
                    receiver = Integer.parseInt(REMOTE_PORT3);

                } else if (msgToSend.substring(4, 8).equals(MYPORT_4)) {
                    receiver = Integer.parseInt(REMOTE_PORT4);

                }
                // Log.d(TAG, "Rceiver Port = " + receiver);
                Socket sock = new Socket("10.0.2.2", receiver);

                OutputStreamWriter out = new OutputStreamWriter(sock.getOutputStream());
                BufferedWriter buffer = new BufferedWriter(out);
                buffer.write(msgToSend);
                buffer.flush();
                sock.close();

            } catch (IOException e) {

                e.printStackTrace();
            }
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {
        int receiver;

        @Override
        synchronized protected Void doInBackground(String... msgs) {
            // Log.d(TAG, "Client asynctask is running");
            String msgToSend = msgs[0];

            try {
                if (msgToSend.substring(4, 8).equals(MYPORT_0)) {
                    receiver = Integer.parseInt(REMOTE_PORT0);

                }

                else if (msgToSend.substring(4, 8).equals(MYPORT_1)) {
                    receiver = Integer.parseInt(REMOTE_PORT1);

                }

                else if (msgToSend.substring(4, 8).equals(MYPORT_2)) {
                    receiver = Integer.parseInt(REMOTE_PORT2);

                }

                else if (msgToSend.substring(4, 8).equals(MYPORT_3)) {
                    receiver = Integer.parseInt(REMOTE_PORT3);

                } else if (msgToSend.substring(4, 8).equals(MYPORT_4)) {
                    receiver = Integer.parseInt(REMOTE_PORT4);

                }
                Socket sock = new Socket("10.0.2.2", receiver);

                OutputStreamWriter out = new OutputStreamWriter(sock.getOutputStream());
                BufferedWriter buffer = new BufferedWriter(out);
                buffer.write(msgToSend);
                buffer.flush();
                sock.close();

            } catch (IOException e) {
                SimpleDhtProvider.SUCCESSOR = SimpleDhtProvider.portNo;
                SimpleDhtProvider.PREDECESSOR = SimpleDhtProvider.portNo;
            }

            return null;

        }
    }

}
