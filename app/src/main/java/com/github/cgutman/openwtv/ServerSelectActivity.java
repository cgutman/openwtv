package com.github.cgutman.openwtv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.github.cgutman.openwtv.utils.Dialog;

public class ServerSelectActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "default";
    private static final String PREF_ADDRESS = "address";
    private static final String PREF_PORT = "port";
    private static final String PREF_PASSWD = "passwd";

    private EditText addressText, portText, passwdText;
    private Button connectButton;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_select);

        addressText = (EditText) findViewById(R.id.addressText);
        portText = (EditText) findViewById(R.id.portText);
        passwdText = (EditText) findViewById(R.id.passwdText);
        connectButton = (Button) findViewById(R.id.connectButton);

        prefs = getSharedPreferences(PREFS_NAME, 0);

        addressText.setText(prefs.getString(PREF_ADDRESS, ""));
        portText.setText(prefs.getString(PREF_PORT, "7799"));
        passwdText.setText(prefs.getString(PREF_PASSWD, ""));

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (addressText.getText().toString().isEmpty()) {
                    Dialog.displayDialog(ServerSelectActivity.this, "Invalid Address", "The address cannot be blank.", false);
                    return;
                }
                if (portText.getText().toString().isEmpty()) {
                    Dialog.displayDialog(ServerSelectActivity.this, "Invalid Port", "The port cannot be blank.", false);
                    return;
                }
                if (passwdText.getText().toString().isEmpty()) {
                    Dialog.displayDialog(ServerSelectActivity.this, "Invalid Password", "The password cannot be blank.", false);
                    return;
                }

                int port;
                try {
                    port = Integer.parseInt(portText.getText().toString());
                } catch (NumberFormatException e) {
                    Dialog.displayDialog(ServerSelectActivity.this, "Invalid Port", "The port must consist of numbers only.", false);
                    return;
                }

                // Start the channel list activity
                Intent intent = new Intent(ServerSelectActivity.this, ChannelListActivity.class);
                intent.putExtra(ChannelListActivity.ADDRESS_EXTRA, addressText.getText().toString().trim());
                intent.putExtra(ChannelListActivity.PORT_EXTRA, port);
                intent.putExtra(ChannelListActivity.PASSWD_EXTRA, passwdText.getText().toString());
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(PREF_ADDRESS, addressText.getText().toString());
        edit.putString(PREF_PORT, portText.getText().toString());
        edit.putString(PREF_PASSWD, passwdText.getText().toString());
        edit.apply();
    }
}
