package com.github.cgutman.openwtv.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.github.cgutman.openwtv.R;
import com.github.cgutman.openwtv.protocol.ExtendConnection;

import java.util.ArrayList;
import java.util.List;

public class ChannelListAdapter extends BaseAdapter {
    private ArrayList<ExtendConnection.ChannelEntry> list = new ArrayList<>();
    private LayoutInflater inflater;
    private int layoutId;

    public ChannelListAdapter(Context context, int layoutId) {
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.layoutId = layoutId;
    }

    public void updateChannelList(List<ExtendConnection.ChannelEntry> newList) {
        int i, j;
        boolean dataChanged = false;

        // Check for new channels
        for (i = 0; i < newList.size(); i++) {
            boolean found = false;

            // Check the existing list
            for (j = 0; j < list.size(); j++) {
                if (newList.get(i).equals(list.get(j))) {
                    found = true;
                    break;
                }
            }

            // New item
            if (!found) {
                list.add(newList.get(i));
                dataChanged = true;
            }
        }

        // Check for removed channels
        for (i = 0; i < list.size(); i++) {
            boolean found = false;

            // Check the existing list
            for (j = 0; j < newList.size(); j++) {
                if (newList.get(j).equals(list.get(i))) {
                    found = true;
                    break;
                }
            }

            // New item
            if (!found) {
                list.remove(i);
                i--;
                dataChanged = true;
            }
        }

        if (dataChanged) {
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int i) {
        return list.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        if (convertView == null) {
            convertView = inflater.inflate(layoutId, viewGroup, false);
        }

        TextView channelNumberView = (TextView) convertView.findViewById(R.id.channelNumberTextView);
        TextView channelNameView = (TextView) convertView.findViewById(R.id.channelNameTextView);

        channelNumberView.setText(""+list.get(i).number);
        channelNameView.setText(list.get(i).name);

        return convertView;
    }
}
