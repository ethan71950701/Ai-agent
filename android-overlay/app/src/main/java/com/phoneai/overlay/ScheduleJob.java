package com.phoneai.overlay;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** 系統每 15 分鐘叫一次：跑該跑的定時查詢（見 Schedule）。 */
public class ScheduleJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try { Schedule.runDue(this); } catch (Exception ignored) { }
            jobFinished(params, false);
        }, "schedule-run").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return true; }
}
