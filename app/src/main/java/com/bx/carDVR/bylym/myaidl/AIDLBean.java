package com.bx.carDVR.bylym.myaidl;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.util.ArrayList;

/**
 * @author Altair
 * @date :2019.12.27 上午 11:37
 * @description:
 */
public class AIDLBean implements Parcelable {
    private ArrayList<String> mVideoListArray;

    protected AIDLBean(Parcel in) {
        if (mVideoListArray != null) {
            mVideoListArray = new ArrayList<>();
        }
//        in.readArrayList(String.class.getClassLoader());
        mVideoListArray = in.createStringArrayList();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringList(mVideoListArray);
    }

    public static final Creator<AIDLBean> CREATOR = new Creator<AIDLBean>() {
        @Override
        public AIDLBean createFromParcel(Parcel in) {
            return new AIDLBean(in);
        }

        @Override
        public AIDLBean[] newArray(int size) {
            return new AIDLBean[size];
        }
    };
}
