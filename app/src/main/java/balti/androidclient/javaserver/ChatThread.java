package balti.androidclient.javaserver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;

import static balti.androidclient.javaserver.Constants.ACTION_CANCEL_CONNECT;
import static balti.androidclient.javaserver.Constants.INFO_CONNECTION_ERROR;
import static balti.androidclient.javaserver.Constants.INFO_SERVER_MESSAGE;
import static balti.androidclient.javaserver.Constants.STATUS_CONNECTION_CLOSED;
import static balti.androidclient.javaserver.Constants.STATUS_CONNECTION_ESTABLISHED;
import static balti.androidclient.javaserver.Constants.STATUS_CONNECTION_FAILED;
import static balti.androidclient.javaserver.Constants.STATUS_MESSAGE_RECEIVED;

public class ChatThread extends AsyncTask {

    // static booleans storing connection status
    static boolean isConnecting = false;
    static boolean isConnected = false;

    String ipAddress = "localhost";
    int port = 5300;

    private Context context;
    private DataInputStream din = null;
    static Socket socket = null;

    String message;

    // broadcast receiver to fire on
    private BroadcastReceiver cancelConnection;

    ChatThread(Context context){

        this.context = context;

        cancelConnection = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                // reset connection status and cancel the asynctask
                isConnected = isConnecting = false;
                cancel(true);
            }
        };
        context.registerReceiver(cancelConnection, new IntentFilter(ACTION_CANCEL_CONNECT));
    }

    void setPortAndIP(String ipAddress, int port){
        this.ipAddress = ipAddress;
        this.port = port;
    }

    @Override
    protected Object doInBackground(Object[] objects) {

        try {

            // trying to connect but not yet connected
            isConnecting = true;
            isConnected = false;

            socket = new Socket(ipAddress, port);

            din = new DataInputStream(socket.getInputStream());

            // connection successful
            isConnecting = false;
            isConnected = true;
            context.sendBroadcast(new Intent(STATUS_CONNECTION_ESTABLISHED));

            // intent to broadcast messages from server to MainActivity
            Intent messageBroadcast = new Intent(STATUS_MESSAGE_RECEIVED);

            // run loop as long as connection is established
            while (isConnected) {

                try {
                    message = din.readUTF();

                    if (message.equals("<exit>")) {

                        // <exit> message received. Close the connection.
                        context.sendBroadcast(new Intent(STATUS_CONNECTION_CLOSED));
                        break;
                    } else {

                        // show message if not blank
                        if (!message.trim().equals("")) {
                            messageBroadcast.putExtra(INFO_SERVER_MESSAGE, message.trim());
                            context.sendBroadcast(messageBroadcast);
                        }
                    }
                }
                catch (Exception e){
                    e.printStackTrace();

                    // error occurs if server unexpectedly closes. Hence send broadcast to MainActivity that connection is closed.
                    context.sendBroadcast(new Intent(STATUS_CONNECTION_CLOSED));
                    break;
                }
            }

        } catch (IOException e) {

            // could not open connection to server
            isConnected = isConnecting = false;

            // send broadcast to MainActivity (connection dialog to be specific) saying the reason for error
            Intent errorIntent = new Intent(STATUS_CONNECTION_FAILED);
            errorIntent.putExtra(INFO_CONNECTION_ERROR, e.getMessage());

            context.sendBroadcast(errorIntent);
            e.printStackTrace();
        }

        return null;
    }

    @Override
    protected void onPostExecute(Object o) {
        super.onPostExecute(o);

        // session over. clean up.
        isConnected = isConnecting = false;

        try {
            if (din != null) din.close();
        } catch (Exception ignored) {}

        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}

        context.unregisterReceiver(cancelConnection);
    }


}
