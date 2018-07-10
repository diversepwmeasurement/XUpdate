/*
 * Copyright (C) 2018 xuexiangjys(xuexiangjys@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xuexiang.xupdate;

import android.content.Context;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;

import com.xuexiang.xupdate.entity.UpdateEntity;
import com.xuexiang.xupdate.proxy.IUpdateChecker;
import com.xuexiang.xupdate.proxy.IUpdateDownloader;
import com.xuexiang.xupdate.proxy.IUpdateHttpService;
import com.xuexiang.xupdate.proxy.IUpdateParser;
import com.xuexiang.xupdate.proxy.IUpdatePrompter;
import com.xuexiang.xupdate.proxy.IUpdateProxy;
import com.xuexiang.xupdate.proxy.impl.DefaultUpdatePrompter;
import com.xuexiang.xupdate.service.OnFileDownloadListener;
import com.xuexiang.xupdate.utils.UpdateUtils;

import java.util.Map;
import java.util.TreeMap;

import static com.xuexiang.xupdate.entity.UpdateError.ERROR.CHECK_NO_NETWORK;
import static com.xuexiang.xupdate.entity.UpdateError.ERROR.CHECK_NO_NEW_VERSION;
import static com.xuexiang.xupdate.entity.UpdateError.ERROR.CHECK_NO_WIFI;
import static com.xuexiang.xupdate.entity.UpdateError.ERROR.PROMPT_ACTIVITY_DESTROY;

/**
 * 版本更新管理者
 *
 * @author xuexiang
 * @since 2018/7/1 下午9:49
 */
public class UpdateManager implements IUpdateProxy {
    /**
     * 版本更新代理
     */
    private IUpdateProxy mIUpdateProxy;
    /**
     * 更新信息
     */
    private UpdateEntity mUpdateEntity;

    private Context mContext;
    //============请求参数==============//
    /**
     * 版本更新的url地址
     */
    private String mUpdateUrl;
    /**
     * 请求参数
     */
    private Map<String, Object> mParams;

    /**
     * apk缓存的目录
     */
    private String mApkCacheDir;

    //===========更新模式================//
    /**
     * 是否只在wifi下进行版本更新检查
     */
    private boolean mIsWifiOnly;
    /**
     * 是否是Get请求
     */
    private boolean mIsGet;
    /**
     * 是否是自动版本更新模式【无人干预,自动下载，自动更新】
     */
    private boolean mIsAutoMode;
    //===========更新组件===============//
    /**
     * 版本更新网络请求服务API
     */
    private IUpdateHttpService mIUpdateHttpService;
    /**
     * 版本更新检查器
     */
    private IUpdateChecker mIUpdateChecker;
    /**
     * 版本更新解析器
     */
    private IUpdateParser mIUpdateParser;
    /**
     * 版本更新下载器
     */
    private IUpdateDownloader mIUpdateDownloader;
    /**
     * 文件下载监听
     */
    private OnFileDownloadListener mOnFileDownloadListener;
    /**
     * 版本更新提示器
     */
    private IUpdatePrompter mIUpdatePrompter;


    /**
     * 构造函数
     *
     * @param builder
     */
    private UpdateManager(Builder builder) {
        mContext = builder.context;
        mUpdateUrl = builder.updateUrl;
        mParams = builder.params;
        mApkCacheDir = builder.apkCacheDir;

        mIsWifiOnly = builder.isWifiOnly;
        mIsGet = builder.isGet;
        mIsAutoMode = builder.isAutoMode;

        mIUpdateHttpService = builder.updateHttpService;

        mIUpdateChecker = builder.updateChecker;
        mIUpdateParser = builder.updateParser;
        mIUpdateDownloader = builder.updateDownLoader;
        mOnFileDownloadListener = builder.onFileDownloadListener;

        mIUpdatePrompter = builder.updatePrompter;
    }

    /**
     * 设置版本更新的代理，可自定义版本更新
     *
     * @param updateProxy
     * @return
     */
    public UpdateManager setIUpdateProxy(IUpdateProxy updateProxy) {
        mIUpdateProxy = updateProxy;
        return this;
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public IUpdateHttpService getIUpdateHttpService() {
        return mIUpdateHttpService;
    }

    /**
     * 开始版本更新
     */
    @Override
    public void update() {
        if (mIUpdateProxy != null) {
            mIUpdateProxy.update();
        } else {
            doUpdate();
        }
    }

    /**
     * 执行版本更新操作
     */
    private void doUpdate() {
        onBeforeCheck();

        if (mIsWifiOnly) {
            if (UpdateUtils.checkWifi(mContext)) {
                checkVersion();
            } else {
                onAfterCheck();
                _XUpdate.onUpdateError(CHECK_NO_WIFI);
            }
        } else {
            if (UpdateUtils.checkNetwork(mContext)) {
                checkVersion();
            } else {
                onAfterCheck();
                _XUpdate.onUpdateError(CHECK_NO_NETWORK);
            }
        }
    }

    /**
     * 版本检查之前
     */
    @Override
    public void onBeforeCheck() {
        if (mIUpdateProxy != null) {
            mIUpdateProxy.onBeforeCheck();
        } else {
            mIUpdateChecker.onBeforeCheck();
        }
    }

    /**
     * 执行网络请求，检查应用的版本信息
     */
    @Override
    public void checkVersion() {
        if (mIUpdateProxy != null) {
            mIUpdateProxy.checkVersion();
        } else {
            mIUpdateChecker.checkVersion(mIsGet, mUpdateUrl, mParams, this);
        }
    }

    /**
     * 版本检查之后
     */
    @Override
    public void onAfterCheck() {
        if (mIUpdateProxy != null) {
            mIUpdateProxy.onAfterCheck();
        } else {
            mIUpdateChecker.onAfterCheck();
        }
    }

    /**
     * 将请求的json结果解析为版本更新信息实体
     *
     * @param json
     * @return
     */
    @Override
    public UpdateEntity parseJson(@NonNull String json) throws Exception {
        if (mIUpdateProxy != null) {
            mUpdateEntity = mIUpdateProxy.parseJson(json);
        } else {
            mUpdateEntity = mIUpdateParser.parseJson(json);
        }
        if (mUpdateEntity != null) {  //更新信息（本地信息）
            mUpdateEntity.setApkCacheDir(mApkCacheDir);
            mUpdateEntity.setIsAutoMode(mIsAutoMode);
            mUpdateEntity.setIUpdateHttpService(mIUpdateHttpService);
        }
        return mUpdateEntity;
    }

    /**
     * 发现新版本
     *
     * @param updateEntity 版本更新信息
     * @param updateProxy  版本更新代理
     */
    @Override
    public void findNewVersion(@NonNull UpdateEntity updateEntity, @NonNull IUpdateProxy updateProxy) {
        if (updateEntity.isSilent()) { //静默下载，发现新版本后，直接下载更新
            startDownload(updateEntity, mOnFileDownloadListener);
        } else {
            if (mIUpdateProxy != null) { //否则显示版本更新提示
                mIUpdateProxy.findNewVersion(updateEntity, updateProxy);
            } else {
                if (mIUpdatePrompter instanceof DefaultUpdatePrompter) {
                    if (mContext != null && !((FragmentActivity) mContext).isFinishing()) {
                        mIUpdatePrompter.showPrompt(updateEntity, updateProxy);
                    } else {
                        _XUpdate.onUpdateError(PROMPT_ACTIVITY_DESTROY);
                    }
                } else {
                    mIUpdatePrompter.showPrompt(updateEntity, updateProxy);
                }
            }
        }
    }

    /**
     * 未发现新版本
     *
     * @param throwable 未发现的原因
     */
    @Override
    public void noNewVersion(@NonNull Throwable throwable) {
        if (mIUpdateProxy != null) {
            mIUpdateProxy.noNewVersion(throwable);
        } else {
            _XUpdate.onUpdateError(CHECK_NO_NEW_VERSION, throwable.getMessage());
        }
    }

    @Override
    public void startDownload(@NonNull UpdateEntity updateEntity, @Nullable OnFileDownloadListener downloadListener) {
        if (mIUpdateProxy != null) {
            mIUpdateProxy.startDownload(updateEntity, downloadListener);
        } else {
            mIUpdateDownloader.startDownload(updateEntity, downloadListener);
        }
    }

    @Override
    public void cancelDownload() {
        if (mIUpdateProxy != null) {
            mIUpdateProxy.cancelDownload();
        } else {
            mIUpdateDownloader.cancelDownload();
        }
    }

    //============================构建者===============================//

    /**
     * 版本更新管理构建者
     */
    public static class Builder {
        //=======必填项========//
        Context context;
        /**
         * 版本更新的url地址
         */
        String updateUrl;
        /**
         * 请求参数
         */
        Map<String, Object> params;
        /**
         * 版本更新网络请求服务API
         */
        IUpdateHttpService updateHttpService;
        /**
         * 版本更新解析器
         */
        IUpdateParser updateParser;
        //===========更新模式================//
        /**
         * 是否使用的是Get请求
         */
        boolean isGet;
        /**
         * 是否只在wifi下进行版本更新检查
         */
        boolean isWifiOnly;
        /**
         * 是否是自动版本更新模式【无人干预,有版本更新直接下载、安装】
         */
        boolean isAutoMode;

        //===========更新行为================//
        /**
         * 版本更新检查器
         */
        IUpdateChecker updateChecker;
        /**
         * 版本更新提示器
         */
        IUpdatePrompter updatePrompter;
        /**
         * 下载器
         */
        IUpdateDownloader updateDownLoader;
        /**
         * 下载监听
         */
        OnFileDownloadListener onFileDownloadListener;
        /**
         * 主题颜色
         */
        int themeColor;
        /**
         * 顶部背景图片
         */
        int topResId;
        /**
         * apk缓存的目录
         */
        String apkCacheDir;

        /**
         * 构建者
         *
         * @param context
         */
        public Builder(@NonNull Context context) {
            this.context = context;

            params = new TreeMap<>();
            if (_XUpdate.getParams() != null) {
                params.putAll(_XUpdate.getParams());
            }

            updateHttpService = _XUpdate.getIUpdateHttpService();

            updateChecker = _XUpdate.getIUpdateChecker();
            updateParser = _XUpdate.getIUpdateParser();
            updateDownLoader = _XUpdate.getIUpdateDownLoader();

            isGet = _XUpdate.isGet();
            isWifiOnly = _XUpdate.isWifiOnly();
            isAutoMode = _XUpdate.isAutoMode();
            apkCacheDir = _XUpdate.getApkCacheDir();
        }

        /**
         * 设置版本更新检查的url
         *
         * @param updateUrl
         * @return
         */
        public Builder updateUrl(@NonNull String updateUrl) {
            this.updateUrl = updateUrl;
            return this;
        }

        /**
         * 设置请求参数
         *
         * @param params
         * @return
         */
        public Builder params(@NonNull Map<String, Object> params) {
            this.params.putAll(params);
            return this;
        }

        /**
         * 设置请求参数
         *
         * @param key
         * @param value
         * @return
         */
        public Builder param(@NonNull String key, @NonNull Object value) {
            this.params.put(key, value);
            return this;
        }

        /**
         * 设置网络请求的请求服务API
         *
         * @param updateHttpService
         * @return
         */
        public Builder updateHttpService(@NonNull IUpdateHttpService updateHttpService) {
            this.updateHttpService = updateHttpService;
            return this;
        }

        /**
         * 设置apk下载的缓存目录
         *
         * @param apkCacheDir
         * @return
         */
        public Builder apkCacheDir(@NonNull String apkCacheDir) {
            this.apkCacheDir = apkCacheDir;
            return this;
        }

        /**
         * 是否使用Get请求
         *
         * @param isGet
         * @return
         */
        public Builder isGet(boolean isGet) {
            this.isGet = isGet;
            return this;
        }

        /**
         * 是否是自动版本更新模式【无人干预,有版本更新直接下载、安装，需要root权限】
         *
         * @param isAutoMode
         * @return
         */
        public Builder isAutoMode(boolean isAutoMode) {
            this.isAutoMode = isAutoMode;
            return this;
        }

        /**
         * 是否只在wifi下进行版本更新检查
         *
         * @param isWifiOnly
         * @return
         */
        public Builder isWifiOnly(boolean isWifiOnly) {
            this.isWifiOnly = isWifiOnly;
            return this;
        }

        /**
         * 设置版本更新检查器
         *
         * @param updateChecker
         * @return
         */
        public Builder updateChecker(@NonNull IUpdateChecker updateChecker) {
            this.updateChecker = updateChecker;
            return this;
        }

        /**
         * 设置版本更新的解析器
         *
         * @param updateParser
         * @return
         */
        public Builder updateParser(@NonNull IUpdateParser updateParser) {
            this.updateParser = updateParser;
            return this;
        }

        /**
         * 设置版本更新提示器
         *
         * @param updatePrompter
         * @return
         */
        public Builder updatePrompter(@NonNull IUpdatePrompter updatePrompter) {
            this.updatePrompter = updatePrompter;
            return this;
        }

        /**
         * 设置文件的下载监听
         *
         * @param onFileDownloadListener
         * @return
         */
        public Builder setOnFileDownloadListener(OnFileDownloadListener onFileDownloadListener) {
            this.onFileDownloadListener = onFileDownloadListener;
            return this;
        }

        /**
         * 设置主题颜色
         *
         * @param themeColor
         * @return
         */
        public Builder themeColor(@ColorInt int themeColor) {
            this.themeColor = themeColor;
            return this;
        }

        /**
         * 设置顶部背景图片
         *
         * @param topResId
         * @return
         */
        public Builder topResId(@DrawableRes int topResId) {
            this.topResId = topResId;
            return this;
        }

        /**
         * 设备版本更新下载器
         *
         * @param updateDownLoader
         * @return
         */
        public Builder updateDownLoader(@NonNull IUpdateDownloader updateDownLoader) {
            this.updateDownLoader = updateDownLoader;
            return this;
        }

        /**
         * 构建版本更新管理者
         *
         * @return 版本更新管理者
         */
        public UpdateManager build() {
            UpdateUtils.requireNonNull(this.context, "[UpdateManager.Builder] : context == null");
            UpdateUtils.requireNonNull(this.updateHttpService, "[UpdateManager.Builder] : updateHttpService == null");
            if (TextUtils.isEmpty(this.updateUrl)) {
                throw new NullPointerException("[UpdateManager.Builder] : updateUrl 不能为空");
            }

            if (this.updatePrompter == null) {
                if (context instanceof FragmentActivity) {
                    updatePrompter = new DefaultUpdatePrompter(((FragmentActivity) context).getSupportFragmentManager(), themeColor, topResId);
                } else {
                    throw new NullPointerException("[UpdateManager.Builder] : 使用默认的版本更新提示器，context必须传FragmentActivity！");
                }
            }

            if (TextUtils.isEmpty(apkCacheDir)) {
                apkCacheDir = UpdateUtils.getDiskCacheDir(this.context, "xupdate");
            }
            return new UpdateManager(this);
        }

        /**
         * 进行版本更新
         */
        public void update() {
            build().update();
        }

        /**
         * 进行版本更新
         *
         * @param updateProxy 版本更新代理
         */
        public void update(IUpdateProxy updateProxy) {
            build().setIUpdateProxy(updateProxy)
                    .update();
        }
    }
}