package balti.androidclient.javaserver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;

import static balti.androidclient.javaserver.Constants.ACTION_CANCEL_CONNECT;
import static balti.androidclient.javaserver.Constants.INFO_CONNECTION_ERROR;
import static balti.androidclient.javaserver.Constants.INFO_SERVER_MESSAGE;
import static balti.androidclient.javaserver.Constants.INFO_USER_MESSAGE;
import static balti.androidclient.javaserver.Constants.STATUS_CONNECTION_CLOSED;
import static balti.androidclient.javaserver.Constants.STATUS_CONNECTION_ESTABLISHED;
import static balti.androidclient.javaserver.Constants.STATUS_CONNECTION_FAILED;
import static balti.androidclient.javaserver.Constants.STATUS_MESSAGE_RECEIVED;
import static balti.androidclient.javaserver.Constants.STATUS_MESSAGE_SENT;

public class MainActivity extends AppCompatActivity {

    // variables to store IP and port
    String ipAddress;
    int port;

    // conection dialog to take IP and port as input
    AlertDialog connectionEstablishDialog = null;

    // various broadcast receivers for different purposes
    BroadcastReceiver connectionEstablishedReceiver;
    BroadcastReceiver connectionFailedReceiver;
    BroadcastReceiver disconnectReceiver;
    BroadcastReceiver messageReceiver;
    BroadcastReceiver messageSentReceiver;

    // Asynctask to connect to server and listen to messages
    ChatThread chatThread;

    // views
    Button startChat;
    LinearLayout chatBody;
    EditText userMessage;
    ImageView send;
    ScrollView chatScrollView;

    // these (arbitary valued) strings denote how messages will be shown on chat thread
    String STATUS_MESSAGE = "2.66.999.8.44.444.66.4";
    String RECEIVED_MESSAGE = "333.666.777";
    String SENDING_MESSAGE = "999.666.88";

    // SharedPreferences used to store last entered IP and port
    // just used for convenience. Maybe omitted.
    SharedPreferences main;
    SharedPreferences.Editor editor;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        main = getSharedPreferences("main", MODE_PRIVATE);
        editor = main.edit();

        // inflate view for connection dialog
        View connectionEstablishView = View.inflate(this, R.layout.connection_establish_dialog, null);

        startChat = findViewById(R.id.start_chat);
        chatBody = findViewById(R.id.chat_body);
        userMessage = findViewById(R.id.user_message);
        send = findViewById(R.id.send_message);
        chatScrollView = findViewById(R.id.chat_scrollView);

        connectionEstablishDialog= new AlertDialog.Builder(this)
                .setView(connectionEstablishView)
                .setCancelable(false)
                .create();

        // these are views of the inflated connectionEstablishView
        Button cancelConnect = connectionEstablishView.findViewById(R.id.cancel_connection);
        final Button startConnect = connectionEstablishView.findViewById(R.id.connect_button);
        final EditText ipAddressInput = connectionEstablishView.findViewById(R.id.ip_address_input);
        final EditText portInput = connectionEstablishView.findViewById(R.id.port_input);
        final TextView errorText = connectionEstablishView.findViewById(R.id.connection_error_display);

        // setting values from previous entry of IP and port
        ipAddressInput.setText(main.getString("ip", "localhost"));
        portInput.setText(main.getInt("port", 5300) + "");

        cancelConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (ChatThread.isConnecting){

                    // this means that chatThread is still connecting.
                    // send broadcast to cancel that and enable button startConnect to enable new connection
                    sendBroadcast(new Intent(ACTION_CANCEL_CONNECT));
                    startConnect.setText(R.string.connect);
                    startConnect.setEnabled(true);
                }
                else {

                    // this means chatThread is not yet initialised
                    // dismiss the dialog.
                    connectionEstablishDialog.cancel();
                    startChat.setVisibility(View.VISIBLE);
                }
            }
        });

        startConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // remove errors
                errorText.setText("");

                // disable this button to prevent duplicate connection requests
                startConnect.setEnabled(false);
                startConnect.setText(R.string.connecting);

                // if no port or IP address is provided, put a default value.
                if (portInput.getText().toString().equals(""))
                    port = 5300;
                else port = Integer.parseInt(portInput.getText().toString());

                if (ipAddressInput.getText().toString().equals(""))
                    ipAddress = "localhost";
                else ipAddress = ipAddressInput.getText().toString();

                // store the entered IP addressed and port for next use
                editor.putString("ip", ipAddress);
                editor.putInt("port", port);
                editor.commit();

                // start the chatThread to start connection and listen to incoming messages
                chatThread = new ChatThread(MainActivity.this);
                chatThread.setPortAndIP(ipAddress, port);
                chatThread.execute();
            }
        });

        startChat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // show the connection dialog to get connection parameters
                connectionEstablishDialog.show();
                startChat.setVisibility(View.GONE);
            }
        });

        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // send message to server
                sendMessage(userMessage.getText().toString().trim());
            }
        });

        connectionEstablishedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                // reset elements connection dialog and dismiss it
                errorText.setText("");
                startConnect.setText(R.string.connect);
                startConnect.setEnabled(true);
                connectionEstablishDialog.dismiss();

                // make the start chat button disappear
                startChat.setVisibility(View.GONE);

                // set a status message
                addMessage("IP: " + ipAddress + "\nPort: " + port, STATUS_MESSAGE);
            }
        };
        registerReceiver(connectionEstablishedReceiver, new IntentFilter(STATUS_CONNECTION_ESTABLISHED));

        connectionFailedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                startConnect.setText(R.string.connect);
                startConnect.setEnabled(true);

                // show the error on connection failure
                errorText.setText(intent.getStringExtra(INFO_CONNECTION_ERROR));
            }
        };
        registerReceiver(connectionFailedReceiver, new IntentFilter(STATUS_CONNECTION_FAILED));

        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(INFO_SERVER_MESSAGE)){

                    // show the from server when broadcasted by chatThread
                    addMessage(intent.getStringExtra(INFO_SERVER_MESSAGE), RECEIVED_MESSAGE);
                }
            }
        };
        registerReceiver(messageReceiver, new IntentFilter(STATUS_MESSAGE_RECEIVED));

        disconnectReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                // show the startChat button to start a new chat
                startChat.setVisibility(View.VISIBLE);

                // show the "Disconnected" status
                addMessage(getString(R.string.disconnected), STATUS_MESSAGE);
            }
        };
        registerReceiver(disconnectReceiver, new IntentFilter(STATUS_CONNECTION_CLOSED));

        messageSentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                // this receiver runs on acknowledgement that a message has been successfully sent to the server
                userMessage.setText("");
                // show the sent message
                addMessage(intent.getStringExtra(INFO_USER_MESSAGE), SENDING_MESSAGE);
            }
        };
        registerReceiver(messageSentReceiver, new IntentFilter(STATUS_MESSAGE_SENT));

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(connectionEstablishedReceiver);
        unregisterReceiver(connectionFailedReceiver);
        unregisterReceiver(messageReceiver);
        unregisterReceiver(disconnectReceiver);
        unregisterReceiver(messageSentReceiver);

        // send an exit message to server on closing the app
        sendMessage("<exit>");
    }

    void addMessage(String text, String type){

        // this method is used to show different types of messages to chat body
        // this includes received messages, sent messages and status messages

        // layout to hold message textView
        LinearLayout messageHolder = new LinearLayout(MainActivity.this);
        messageHolder.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // actual message textView
        TextView aMessage = new TextView(MainActivity.this);
        aMessage.setText(text);
        aMessage.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int padding = 10;

        aMessage.setPadding(padding,padding,padding,padding);

        messageHolder.addView(aMessage);
        messageHolder.setPadding(padding,padding,padding,padding);

        if (type.equals(STATUS_MESSAGE)) {

            // status message textViews are to be center aligned in the messageHolder
            aMessage.setTextAppearance(android.R.style.TextAppearance_Small);
            aMessage.setBackgroundColor(getResources().getColor(R.color.statusMessageColor));
            messageHolder.setGravity(Gravity.CENTER_HORIZONTAL);
        }
        else if (type.equals(RECEIVED_MESSAGE)){

            // received message textViews are to be left aligned in the messageHolder
            aMessage.setTextAppearance(android.R.style.TextAppearance_Medium);
            aMessage.setBackgroundColor(getResources().getColor(R.color.receivedMessageColor));
            messageHolder.setGravity(Gravity.START);
        }
        else if (type.equals(SENDING_MESSAGE)) {

            // sent message textViews are to be right aligned in the messageHolder
            aMessage.setTextAppearance(android.R.style.TextAppearance_Medium);
            aMessage.setBackgroundColor(getResources().getColor(R.color.sentMessageColor));
            messageHolder.setGravity(Gravity.END);
        }

        chatBody.addView(messageHolder);

        // this method allows to auto scroll at bottom
        chatScrollView.post(new Runnable(){
            @Override
            public void run() {
                chatScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    void sendMessage(final String message){

        // method to send a message to server

        // check if connection is actually established.
        if (ChatThread.isConnected && ChatThread.socket != null) {

            // without this thread approach, the program just crashed
            Thread senderThread = new Thread(new Runnable() {
                @Override
                public void run() {


                    // send the message if not blank
                    if (!message.equals("")) {
                        try {
                            DataOutputStream dos = new DataOutputStream(ChatThread.socket.getOutputStream());
                            dos.writeUTF(message);
                            dos.flush();

                            Intent sentIntent = new Intent(STATUS_MESSAGE_SENT);
                            sentIntent.putExtra(INFO_USER_MESSAGE, message);

                            sendBroadcast(sentIntent);

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                }
            });
            senderThread.start();
        }
        else Toast.makeText(MainActivity.this, getString(R.string.server_not_connected), Toast.LENGTH_SHORT).show();

    }
}
