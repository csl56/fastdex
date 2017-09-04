/*
 * Copyright (C) 2016 The Android Open Source Project
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

package fastdex.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import fastdex.runtime.fd.Logging;
import fastdex.runtime.fd.Server;

/**
 * Service which starts the Instant Run server; started by the IDE via
 * adb shell am startservice pkg/service
 */
public class InstantRunService extends Service {
    private static final String TAG = InstantRunService.class.getSimpleName();
    private Server server;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Don't allow anyone to bind to this service.
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(Logging.LOG_TAG, "Starting Fastdex Instant Run Server for " + getPackageName());

        // Start server, unless we're in a multi-process scenario and this isn't the
        // primary process
        try {
            Log.d(Logging.LOG_TAG, "server starting...");
            server = Server.create(this);
        } catch (Throwable t) {
            Log.d(Logging.LOG_TAG, "Failed during multi process check", t);
            server = Server.create(this);
        }

        super.onCreate();
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            Log.i(Logging.LOG_TAG, "Stopping Instant Run Server for " + getPackageName());
            server.shutdown();
        }
        super.onDestroy();
    }
}
