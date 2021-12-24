package io.jmix.quartz.service;

import io.jmix.core.UnconstrainedDataManager;
import io.jmix.quartz.model.JobDataParameterModel;
import io.jmix.quartz.model.JobModel;
import io.jmix.quartz.model.ScheduleType;
import io.jmix.quartz.model.TriggerModel;
import org.apache.commons.collections4.CollectionUtils;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Provides information from the Quartz engine.
 */
@Component("quartz_QuartzDataProvider")
public class QuartzDataProvider {

    private static final Logger log = LoggerFactory.getLogger(QuartzDataProvider.class);

    @Autowired
    private Scheduler scheduler;

    @Autowired
    private UnconstrainedDataManager dataManager;

    /**
     * Provides information about all configured quartz jobs with related triggers
     */
    public List<JobModel> getAllJobs() {
        List<JobModel> result = new ArrayList<>();

        try {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.anyJobGroup())) {
                JobModel jobModel = dataManager.create(JobModel.class);
                jobModel.setJobName(jobKey.getName());
                jobModel.setJobGroup(jobKey.getGroup());

                JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                jobModel.setJobClass(jobDetail.getJobClass().getName());
                jobModel.setIsDisallowConcurrentExecution(jobDetail.isConcurrentExectionDisallowed());

                List<? extends Trigger> jobTriggers = scheduler.getTriggersOfJob(jobKey);
                if (CollectionUtils.isEmpty(jobTriggers)) {
                    jobModel.setIsActive(false);
                } else {
                    Date previousFireTime = null;
                    Date nextFireTime = null;
                    boolean isActive = false;
                    for (Trigger jobTrigger : jobTriggers) {
                        Date triggerPreviousFireTime = jobTrigger.getPreviousFireTime();
                        if (previousFireTime == null
                                || (triggerPreviousFireTime != null && previousFireTime.before(triggerPreviousFireTime))) {
                            previousFireTime = triggerPreviousFireTime;
                        }

                        Date triggerNextFireTime = jobTrigger.getNextFireTime();
                        if (nextFireTime == null
                                || (triggerNextFireTime != null && nextFireTime.after(triggerNextFireTime))) {
                            nextFireTime = triggerNextFireTime;
                        }

                        Trigger.TriggerState triggerState = scheduler.getTriggerState(jobTrigger.getKey());
                        if (Trigger.TriggerState.NORMAL == triggerState) {
                            isActive = true;
                        }
                    }

                    jobModel.setLastFireDate(previousFireTime);
                    jobModel.setNextFireDate(nextFireTime);
                    jobModel.setIsActive(isActive);
                }

                result.add(jobModel);
            }
        } catch (SchedulerException e) {
            log.warn("Unable to fetch information about active jobs", e);
        }

        return result;
    }

    /**
     * Provides information about parameters for given job
     *
     * @param jobName  name of job
     * @param jobGroup group of job
     * @return job parameters
     */
    public List<JobDataParameterModel> getDataParamsOfJob(String jobName, String jobGroup) {
        List<JobDataParameterModel> result = new ArrayList<>();

        try {
            scheduler.getJobDetail(JobKey.jobKey(jobName, jobGroup))
                    .getJobDataMap()
                    .getWrappedMap()
                    .forEach((k, v) -> {
                        JobDataParameterModel dataParameterModel = dataManager.create(JobDataParameterModel.class);
                        dataParameterModel.setKey(k);
                        dataParameterModel.setValue(v == null ? "" : v.toString());
                        result.add(dataParameterModel);
                    });
        } catch (SchedulerException e) {
            log.warn("Unable to fetch information about parameters for job {}", JobKey.jobKey(jobName, jobGroup), e);
        }

        return result;
    }

    /**
     * Returns information about active or paused triggers for given job
     *
     * @param jobName  name of the job
     * @param jobGroup group of the job
     * @return all configured triggers for the job
     */
    public List<TriggerModel> getTriggersOfJob(String jobName, String jobGroup) {
        List<TriggerModel> result = new ArrayList<>();

        try {
            for (Trigger trigger : scheduler.getTriggersOfJob(JobKey.jobKey(jobName, jobGroup))) {
                TriggerModel triggerModel = dataManager.create(TriggerModel.class);
                triggerModel.setTriggerName(trigger.getKey().getName());
                triggerModel.setTriggerGroup(trigger.getKey().getGroup());
                triggerModel.setScheduleType(trigger instanceof SimpleTrigger ? ScheduleType.SIMPLE : ScheduleType.CRON_EXPRESSION);
                triggerModel.setStartDate(trigger.getStartTime());
                triggerModel.setLastFireDate(trigger.getPreviousFireTime());
                triggerModel.setNextFireDate(trigger.getNextFireTime());

                if (trigger instanceof CronTrigger) {
                    triggerModel.setCronExpression(((CronTrigger) trigger).getCronExpression());
                } else if (trigger instanceof SimpleTrigger) {
                    SimpleTrigger simpleTrigger = (SimpleTrigger) trigger;
                    //it is necessary because org.quartz.SimpleScheduleBuilder.withRepeatCount
                    triggerModel.setRepeatCount(simpleTrigger.getRepeatCount() + 1);
                    triggerModel.setRepeatInterval(simpleTrigger.getRepeatInterval());
                }

                result.add(triggerModel);
            }
        } catch (SchedulerException e) {
            log.warn("Unable to fetch information about triggers for job {}", JobKey.jobKey(jobName, jobGroup), e);
        }

        return result;
    }

    /**
     * Returns names of all known JobDetail groups
     */
    public List<String> getJobGroupNames() {
        List<String> result = new ArrayList<>();

        try {
            result = scheduler.getJobGroupNames();
        } catch (SchedulerException e) {
            log.warn("Unable to fetch information about job group names", e);
        }

        return result;
    }

    /**
     * Returns names of all known Trigger groups
     */
    public List<String> getTriggerGroupNames() {
        List<String> result = new ArrayList<>();

        try {
            result = scheduler.getTriggerGroupNames();
        } catch (SchedulerException e) {
            log.warn("Unable to fetch information about trigger group names", e);
        }

        return result;
    }

}
