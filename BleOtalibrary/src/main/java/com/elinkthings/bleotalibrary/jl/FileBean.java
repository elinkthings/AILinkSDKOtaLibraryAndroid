//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.elinkthings.bleotalibrary.jl;

import android.os.Parcel;
import android.os.Parcelable;

import com.jieli.jl_fatfs.model.FatFile;

public class FileBean implements Parcelable {
    private long size;
    private String name;
    private boolean isDir;
    private long modifyTime;
    private String path;
    public static final Creator<FileBean> CREATOR = new Creator<FileBean>() {
        @Override
        public FileBean createFromParcel(Parcel in) {
            return new FileBean(in);
        }

        @Override
        public FileBean[] newArray(int size) {
            return new FileBean[size];
        }
    };


    public FileBean() {
    }

    public FileBean(FatFile fatFile) {
        setSize(fatFile.getSize());
        setDir(fatFile.isDir());
        setModifyTime(fatFile.getModifyTime());
        setName(fatFile.getName());
        setPath(fatFile.getPath());
    }

    protected FileBean(Parcel in) {
        this.size = in.readLong();
        this.name = in.readString();
        this.isDir = in.readByte() != 0;
        this.modifyTime = in.readLong();
        this.path = in.readString();
    }

    public long getSize() {
        return this.size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDir() {
        return this.isDir;
    }

    public void setDir(boolean dir) {
        this.isDir = dir;
    }

    public long getModifyTime() {
        return this.modifyTime;
    }

    public void setModifyTime(long modifyTime) {
        this.modifyTime = modifyTime;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "FatFile{size=" + this.size + ", name='" + this.name + '\'' + ", isDir=" + this.isDir + ", modifyTime=" + this.modifyTime + ", path='" + this.path + '\'' + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.size);
        dest.writeString(this.name);
        dest.writeByte((byte)(this.isDir ? 1 : 0));
        dest.writeLong(this.modifyTime);
        dest.writeString(this.path);
    }
}
