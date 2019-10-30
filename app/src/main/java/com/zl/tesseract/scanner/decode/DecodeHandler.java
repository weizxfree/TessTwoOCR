/*
 * Copyright (C) 2010 ZXing authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.zl.tesseract.scanner.decode;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.zl.tesseract.R;
import com.zl.tesseract.scanner.ScannerActivity;
import com.zl.tesseract.scanner.tess.TessEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DecodeHandler extends Handler {

    private final ScannerActivity mActivity;
    private final MultiFormatReader mMultiFormatReader;
    private final Map<DecodeHintType, Object> mHints;
    private byte[] mRotatedData;
    
    DecodeHandler(ScannerActivity activity) {
        this.mActivity = activity;
        mMultiFormatReader = new MultiFormatReader();
        mHints = new Hashtable<>();
        mHints.put(DecodeHintType.CHARACTER_SET, "utf-8");
        mHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        Collection<BarcodeFormat> barcodeFormats = new ArrayList<>();
        barcodeFormats.add(BarcodeFormat.CODE_39);
        barcodeFormats.add(BarcodeFormat.CODE_128); // 快递单常用格式39,128
        barcodeFormats.add(BarcodeFormat.QR_CODE); //扫描格式自行添加
        mHints.put(DecodeHintType.POSSIBLE_FORMATS, barcodeFormats);
    }

    @Override
    public void handleMessage(Message message) {
        switch (message.what) {
            case R.id.decode:
                decode((byte[]) message.obj, message.arg1, message.arg2);
                break;
            case R.id.quit:
                Looper looper = Looper.myLooper();
                if (null != looper) {
                    looper.quit();
                }
                break;
        }
    }

    /**
     * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency, reuse the same reader
     * objects from one decode to the next.
     *
     * @param data The YUV preview frame.
     * @param width The width of the preview frame.
     * @param height The height of the preview frame.
     */
    private void decode(byte[] data, int width, int height) {
        if (null == mRotatedData) {
            mRotatedData = new byte[width * height];
        } else {
            if (mRotatedData.length < width * height) {
                mRotatedData = new byte[width * height];
            }
        }
        Arrays.fill(mRotatedData, (byte) 0);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (x + y * width >= data.length) {
                    break;
                }
                mRotatedData[x * height + height - y - 1] = data[x + y * width];
            }
        }
        int tmp = width; // Here we are swapping, that's the difference to #11
        width = height;
        height = tmp;

        Result rawResult = null;
        try {
            Rect rect = mActivity.getCropRect();
            if (rect == null) {
                return;
            }

            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(mRotatedData, width, height, rect.left, rect.top, rect.width(), rect.height(), false);
            TessEngine tessEngine = TessEngine.Generate();
            Bitmap bitmap = source.renderCroppedGreyscaleBitmap();
            String result = tessEngine.detectText(bitmap);
            String telePhoneNum = getTelnum(result);
            if(!TextUtils.isEmpty(result) && !TextUtils.isEmpty(telePhoneNum)){
                rawResult = new Result(telePhoneNum, null, null, null);
                rawResult.setBitmap(bitmap);
                rawResult.setType(1);
            }else{
                rawResult = mMultiFormatReader.decodeWithState(new BinaryBitmap(new HybridBinarizer(source)));
                if (rawResult == null) {
                    rawResult = mMultiFormatReader.decodeWithState(new BinaryBitmap(new GlobalHistogramBinarizer(source)));
                }

                rawResult.setType(2);
            }


        } catch (Exception ignored) {
        } finally {
            mMultiFormatReader.reset();
        }

        if (rawResult != null) {
            Message message = Message.obtain(mActivity.getCaptureActivityHandler(), R.id.decode_succeeded, rawResult);
            message.sendToTarget();
        } else {
            Message message = Message.obtain(mActivity.getCaptureActivityHandler(), R.id.decode_failed);
            message.sendToTarget();
        }
    }


    /**
     * 获取字符串中的手机号
     */
    public String getTelnum(String sParam) {
        if (sParam.length() <= 0)
            return "";
        Pattern pattern = Pattern.compile("(1|861)(3|5|8)\\d{9}$*");
        Matcher matcher = pattern.matcher(sParam);
        StringBuffer bf = new StringBuffer();
        while (matcher.find()) {
            bf.append(matcher.group()).append(",");
        }
        int len = bf.length();
        if (len > 0) {
            bf.deleteCharAt(len - 1);
        }
        return bf.toString();
    }

}
