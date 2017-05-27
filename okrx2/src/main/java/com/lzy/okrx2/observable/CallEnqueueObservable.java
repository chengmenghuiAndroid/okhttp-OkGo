/*
 * Copyright (C) 2016 Jake Wharton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lzy.okrx2.observable;

import com.lzy.okgo.adapter.Call;
import com.lzy.okgo.callback.Callback;
import com.lzy.okgo.model.Response;
import com.lzy.okgo.request.HttpRequest;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.CompositeException;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.plugins.RxJavaPlugins;

public class CallEnqueueObservable<T> extends Observable<Response<T>> {
    private final Call<T> originalCall;

    public CallEnqueueObservable(Call<T> originalCall) {
        this.originalCall = originalCall;
    }

    @Override
    protected void subscribeActual(Observer<? super Response<T>> observer) {
        // Since Call is a one-shot type, clone it for each new observer.
        Call<T> call = originalCall.clone();
        CallCallback<T> callback = new CallCallback<>(call, observer);
        observer.onSubscribe(callback);
        call.execute(callback);
    }

    private static final class CallCallback<T> implements Disposable, Callback<T> {
        private final Call<T> call;
        private final Observer<? super Response<T>> observer;
        boolean terminated = false;

        CallCallback(Call<T> call, Observer<? super Response<T>> observer) {
            this.call = call;
            this.observer = observer;
        }

        @Override
        public void dispose() {
            call.cancel();
        }

        @Override
        public boolean isDisposed() {
            return call.isCanceled();
        }

        @Override
        public T convertResponse(okhttp3.Response response) throws Exception {
            // okrx 使用converter转换，不需要这个解析方法
            return null;
        }

        @Override
        public void onStart(HttpRequest<T, ? extends HttpRequest> request) {
        }

        @Override
        public void onSuccess(T t, Response<T> response) {
            if (call.isCanceled()) return;

            try {
                observer.onNext(response);
            } catch (Exception e) {
                if (terminated) {
                    RxJavaPlugins.onError(e);
                } else {
                    onError(e, response);
                }
            }
        }

        @Override
        public void onCacheSuccess(T t, Response<T> response) {
            onSuccess(t, response);
        }

        @Override
        public void onError(Exception e, Response<T> response) {
            if (call.isCanceled()) return;

            try {
                terminated = true;
                observer.onError(e);
            } catch (Throwable inner) {
                Exceptions.throwIfFatal(inner);
                RxJavaPlugins.onError(new CompositeException(e, inner));
            }
        }

        @Override
        public void onFinish(Response<T> response) {
            if (call.isCanceled()) return;

            try {
                terminated = true;
                observer.onComplete();
            } catch (Throwable inner) {
                Exceptions.throwIfFatal(inner);
                RxJavaPlugins.onError(inner);
            }

        }

        @Override
        public void upProgress(long currentSize, long totalSize, float progress, long networkSpeed) {
        }

        @Override
        public void downloadProgress(long currentSize, long totalSize, float progress, long networkSpeed) {
        }
    }
}