package com.reason.modules.job.vo;

import com.reason.common.utils.StringUtils;
import com.reason.common.validator.group.AddGroup;
import com.reason.common.validator.group.UpdateGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "定时任务VO")
@Data
public class ScheduleJobVO {
    @Schema(description = "定时任务ID")
    @NotNull(message = "定时任务主键不能为空",groups = {UpdateGroup.class})
    private Long jobId;

    @Schema(description = "Bean名称")
    @Size(max = 128,message = "Bean名称过长",groups = {AddGroup.class,UpdateGroup.class})
    @NotBlank(message="Bean名称不能为空",groups = {AddGroup.class})
    private String jobBean;

    public void setJobBean(String jobBean) {
        this.jobBean = StringUtils.replaceBlank(jobBean);
    }

    @Schema(description = "定时任务名称")
    @Size(max = 128,message = "定时任务名称过长",groups = {AddGroup.class,UpdateGroup.class})
    @NotBlank(message="定时任务名称不能为空",groups = {AddGroup.class})
    private String jobName;

    public void setJobName(String jobName) {
        this.jobName = StringUtils.replaceBlank(jobName);
    }

    @Schema(description = "定时任务参数")
    @Size(max = 2000,message = "定时任务参数过长",groups = {AddGroup.class,UpdateGroup.class})
    private String jobParams;

    public void setJobParams(String jobParams) {
        this.jobParams = StringUtils.replaceBlank(jobParams);
    }

    @Schema(description = "Cron表达式")
    @Size(max = 128,message = "Cron表达式过长",groups = {AddGroup.class,UpdateGroup.class})
    @NotBlank(message="Cron表达式不能为空",groups = {AddGroup.class})
    private String jobCron;

    /* Cron表达式不能去空格
    public void setJobCron(String jobCron) {
        this.jobCron = StringUtils.replaceBlank(jobCron);
    }*/

    @Schema(description = "定时任务说明")
    @Size(max = 256,message = "定时任务说明过长",groups = {AddGroup.class,UpdateGroup.class})
    private String jobComment;

    public void setJobComment(String jobComment) {
        this.jobComment = StringUtils.replaceBlank(jobComment);
    }
}
