package ca.pkay.rcloneexplorer.Services;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import ca.pkay.rcloneexplorer.BroadcastReceivers.UploadCancelAction;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;


public class UploadService extends IntentService {

    public static final String UPLOAD_PATH_ARG = "ca.pkay.rcexplorer.upload_service.arg1";
    public static final String LOCAL_PATH_ARG = "ca.pkay.rcexplorer.upload_service.arg2";
    public static final String REMOTE_ARG = "ca.pkay.rcexplorer.upload_service.arg3";
    private final String CHANNEL_ID = "ca.pkay.rcexplorer.UPLOAD_CHANNEL";
    private final String UPLOAD_FINISHED_GROUP = "ca.pkay.rcexplorer.UPLOAD_FINISHED_GROUP";
    private final String UPLOAD_FAILED_GROUP = "ca.pkay.rcexplorer.UPLOAD_FAILED_GROUP";
    private final String CHANNEL_NAME = "Uploads";
    private final int PERSISTENT_NOTIFICATION_ID = 90;
    private final int UPLOAD_FINISHED_NOTIFICATION_ID = 41;
    private final int UPLOAD_FAILED_NOTIFICATION_ID = 14;
    private Rclone rclone;
    private int numOfProcessesRunning;
    private int numOfFinishedUploads;
    private int numOfFailedUploads;
    private List<Process> runningProcesses;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.*
     */
    public UploadService() {
        super("ca.pkay.rcexplorer.uploadservice");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setNotificationChannel();
        rclone = new Rclone(this);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Intent foregroundIntent = new Intent(this, UploadService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, foregroundIntent, 0);

        Intent cancelIntent = new Intent(this, UploadCancelAction.class);
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(getString(R.string.upload_service_notification_title))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_cancel_download, getString(R.string.cancel), cancelPendingIntent);

        startForeground(PERSISTENT_NOTIFICATION_ID, builder.build());

        if (intent == null) {
            return;
        }
        final String uploadPath = intent.getStringExtra(UPLOAD_PATH_ARG);
        final ArrayList<String> uploadList = intent.getStringArrayListExtra(LOCAL_PATH_ARG);
        final String remote = intent.getStringExtra(REMOTE_ARG);

        runningProcesses = rclone.uploadFiles(remote, uploadPath, uploadList);
        numOfProcessesRunning = runningProcesses.size();
        numOfFinishedUploads = 0;
        numOfFailedUploads = 0;
        AsyncTask[] asyncTasks = new AsyncTask[numOfProcessesRunning];
        for (int i = 0; i < numOfProcessesRunning; i++) {
            asyncTasks[i] = new MonitorUpload(uploadList.get(0), runningProcesses.get(0)).execute();
        }
        
        for (AsyncTask asyncTask : asyncTasks) {
            try {
                asyncTask.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        stopForeground(true);
    }

    private void showUploadFinishedNotification(int notificationID, String contentText) {
        createSummaryNotificationForFinished();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle(getString(R.string.upload_complete))
                .setContentText(contentText)
                .setGroup(UPLOAD_FINISHED_GROUP)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(notificationID, builder.build());
    }

    private void createSummaryNotificationForFinished() {
        Notification summaryNotification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(getString(R.string.upload_complete))
                        //set content text to support devices running API level < 24
                        .setContentText(getString(R.string.upload_complete))
                        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                        .setGroup(UPLOAD_FINISHED_GROUP)
                        .setGroupSummary(true)
                        .setAutoCancel(true)
                        .build();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(UPLOAD_FINISHED_NOTIFICATION_ID, summaryNotification);
    }

    private void showUploadFailedNotification(int notificationID, String contentText) {
        createSummaryNotificationForFailed();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(getString(R.string.upload_failed))
                .setContentText(contentText)
                .setGroup(UPLOAD_FAILED_GROUP)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(notificationID, builder.build());
    }

    private void createSummaryNotificationForFailed() {
        Notification summaryNotification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(getString(R.string.upload_failed))
                        //set content text to support devices running API level < 24
                        .setContentText(getString(R.string.upload_failed))
                        .setSmallIcon(android.R.drawable.stat_sys_warning)
                        .setGroup(UPLOAD_FAILED_GROUP)
                        .setGroupSummary(true)
                        .setAutoCancel(true)
                        .build();

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(UPLOAD_FAILED_NOTIFICATION_ID, summaryNotification);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        for (Process process : runningProcesses) {
            process.destroy();
        }
    }

    private void setNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.upload_service_notification_channel_description));
            // Register the channel with the system
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private class MonitorUpload extends AsyncTask<Void, Void, Boolean> {

        private String file;
        private Process process;

        MonitorUpload(String file, Process process) {
            this.file = file;
            this.process = process;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return process.exitValue() == 0;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            int notificationId = (int)System.currentTimeMillis();
            int startIndex = file.lastIndexOf("/");
            String fileName;
            if (startIndex >= 0 && startIndex < file.length()) {
                fileName = file.substring(startIndex + 1);
            } else {
                fileName = file;
            }

            if (result) {
                showUploadFinishedNotification(notificationId, fileName);
            } else {
                showUploadFailedNotification(notificationId, fileName);
            }
        }
    }
}
