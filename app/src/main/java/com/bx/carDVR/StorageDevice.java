package com.bx.carDVR;


public class StorageDevice {
    public static final int TYPE_SD = 0;
    public static final int TYPE_USB = 1;
    public static final int TYPE_OTHER = 2;

    private final String mPath;
    private final String mDescription;
    private final int mDiskType;


    StorageDevice(int diskType, String description, String path) {
        mDiskType = diskType;
        mPath = path;
        mDescription = description;
    }

    public int getDiskType() {
        return mDiskType;
    }

    public String getPath() {
        return mPath;
    }

    public String getDescription() {
        return mDescription;
    }
}
