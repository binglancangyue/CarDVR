/*
package com.bx.carDVR.bylym.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.bx.carDVR.R;
import com.bx.carDVR.bylym.activity.DVRVideoListActivity;

import java.util.ArrayList;

*/
/**
 * @author Altair
 * @date :2019.12.31 下午 02:35
 * @description:
 *//*

public class DirListAdapter extends BaseExpandableListAdapter implements DVRVideoListActivity.MyListAdapter {
    private int mVideoItemCnt;
    private int mPicItemCnt;
    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private ArrayList<String> mVideoListArray;
    private ArrayList<String> mPicListArray;

    public DirListAdapter(Context context, ArrayList<String> videoListArray,
                          ArrayList<String> picListArray) {
        this.mVideoListArray = videoListArray;
        this.mPicListArray = picListArray;
        mVideoItemCnt = videoListArray.size();
        mPicItemCnt = picListArray.size();
        this.mContext = context;
        this.mLayoutInflater = LayoutInflater.from(mContext);
    }

    @Override
    public int getGroupCount() {
        return 2;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        if (groupPosition == 0) {
            return mVideoItemCnt;
        } else if (groupPosition == 1) {
            return mPicItemCnt;
        }

        return 0;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return null;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return null;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return 0;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                             ViewGroup parent) {
        View view;
        TextView text;
        ImageView expandIcon;

        if (convertView == null) {
            view = mLayoutInflater.inflate(R.layout.dir_group_item, null);
        } else {
            view = convertView;
        }

        text = (TextView) view.findViewById(R.id.item_text);
        expandIcon = (ImageView) view.findViewById(R.id.expand_icon);

        if (groupPosition == 0) {
            text.setText("video");
        } else if (groupPosition == 1) {
            text.setText("picture");
        }

        if (isExpanded) {
            expandIcon.setImageResource(R.mipmap.arrow_dn);
        } else {
            expandIcon.setImageResource(R.mipmap.arrow_left);
        }

        return view;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View convertView, ViewGroup parent) {
        View view;
        TextView text;
        ImageView selBtn;

        if (convertView == null) {
            view = mLayoutInflater.inflate(R.layout.dir_list_item, null);
        } else {
            view = convertView;
        }

        text = (TextView) view.findViewById(R.id.item_text);
        selBtn = (ImageView) view.findViewById(R.id.sel_btn);

        if (groupPosition == 0) {
            text.setText(mVideoListArray.get(childPosition));
        } else if (groupPosition == 1) {
            text.setText(mPicListArray.get(childPosition));
        }

        if (groupPosition == 0) {
            if (mVideoListSelection != null) {
                selBtn.setVisibility(View.VISIBLE);
                if (mVideoListSelection[childPosition]) {
                    selBtn.setImageResource(R.mipmap.file_select_ic);
                } else {
                    selBtn.setImageResource(R.mipmap.file_no_select_ic);
                }
            } else {
                selBtn.setVisibility(View.GONE);
            }
        } else if (groupPosition == 1) {
            if (mPicListSelection != null) {
                selBtn.setVisibility(View.VISIBLE);
                if (mPicListSelection[childPosition]) {
                    selBtn.setImageResource(R.mipmap.file_select_ic);
                } else {
                    selBtn.setImageResource(R.mipmap.file_no_select_ic);
                }
            } else {
                selBtn.setVisibility(View.GONE);
            }
        }

        return view;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }

    @Override
    public void notifyChanged() {
        mVideoItemCnt = mVideoListArray.size();
        mPicItemCnt = mPicListArray.size();
        notifyDataSetChanged();
    }
}
*/
