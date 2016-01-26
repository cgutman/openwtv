package com.github.cgutman.openwtv;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.github.cgutman.openwtv.adapter.ChannelListAdapter;
import com.github.cgutman.openwtv.protocol.ExtendConnection;
import com.github.cgutman.openwtv.utils.Dialog;
import com.github.cgutman.openwtv.utils.SpinnerDialog;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class ChannelListActivity extends AppCompatActivity {

    public static final String ADDRESS_EXTRA = "com.github.cgutman.openwtv.ChannelListActivity.ADDRESS";
    public static final String PORT_EXTRA = "com.github.cgutman.openwtv.ChannelListActivity.PORT";
    public static final String PASSWD_EXTRA = "com.github.cgutman.openwtv.ChannelListActivity.PASSWD";

    private String addressString;
    private int portNumber;
    private String passwdString;
    private ListView listView;
    private ChannelListAdapter channelListAdapter;
    private Thread channelListLoaderThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_list);

        addressString = getIntent().getStringExtra(ADDRESS_EXTRA);
        portNumber = getIntent().getIntExtra(PORT_EXTRA, 0);
        passwdString = getIntent().getStringExtra(PASSWD_EXTRA);

        channelListAdapter = new ChannelListAdapter(this, R.layout.channel_list_row);

        listView = (ListView) findViewById(R.id.channelListView);
        listView.setAdapter(channelListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                ExtendConnection.ChannelEntry channel = (ExtendConnection.ChannelEntry) listView.getAdapter().getItem(position);

                Intent intent = new Intent(ChannelListActivity.this, PlayerActivity.class);
                intent.putExtra(PlayerActivity.ADDRESS_EXTRA, addressString);
                intent.putExtra(PlayerActivity.PORT_EXTRA, portNumber);
                intent.putExtra(PlayerActivity.PASSWD_EXTRA, passwdString);
                intent.putExtra(PlayerActivity.CHANNELID_EXTRA, channel.channelId);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (channelListLoaderThread != null) {
            channelListLoaderThread.interrupt();
        }

        Dialog.closeDialogs();
        SpinnerDialog.closeDialogs(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (channelListLoaderThread != null) {
            channelListLoaderThread.interrupt();
        }

        // Reload the channel list
        channelListLoaderThread = new Thread() {
            public void run() {
                try {
                    InetAddress address;

                    try {
                        address = InetAddress.getByName(addressString);
                    } catch (UnknownHostException e) {
                        Dialog.displayDialog(ChannelListActivity.this, "Invalid Address", "The address could not be found.", false);
                        return;
                    }

                    ExtendConnection connection = ExtendConnection.establishConnection(address, portNumber, passwdString);

                    // TODO: Support more than 1 group
                    final List<ExtendConnection.ChannelEntry> channelList = connection.requestChannelListForGroup(0);

                    ChannelListActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            channelListAdapter.updateChannelList(channelList);
                        }
                    });
                } catch (IOException e) {
                    Dialog.displayDialog(ChannelListActivity.this, "Connection Error", e.getMessage(), true);
                }
            }
        };
        channelListLoaderThread.start();
    }
}
