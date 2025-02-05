package com.codecrush.mymeeting;

public class NV21Converter {
    static {
        System.loadLibrary("nv21_converter");
    }

    public static native void convertRgbaToNv21(
            byte[] rgbaInput, int width, int height, byte[] nv21Output
    );



}
