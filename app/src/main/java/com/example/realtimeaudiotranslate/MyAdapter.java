package com.example.realtimeaudiotranslate;

import android.content.Context;
import android.text.SpannableString;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

public class MyAdapter extends ArrayAdapter<SpannableString> {
    private final Context mContext;
    private final int mResource;
    public MyAdapter(Context context, int resource, List<SpannableString> objects){
        super(context, resource, objects);
        mContext = context;
        mResource = resource;
    }

    public View getView(int pos, View convertView, ViewGroup parent){
        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(mResource, parent, false);
        SpannableString item = getItem(pos);
        TextView textView = view.findViewById(android.R.id.text1);
        textView.setIncludeFontPadding(false);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        textView.setText(item);
        return view;
    }
}
