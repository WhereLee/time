package com.reason.modules.job.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "定时任务查询对象")
@Data
public class ScheduleJobForm extends CommonForm {
    @Schema(description = "Spring Bean名称")
    private String jobBean;     //Bean名称
    @Schema(description = "任务名称")
    private String jobName;     //任务名称
    @Schema(description = "任务状态 0-正常 1-暂停")
    private Integer jobState;   //任务状态 0：正常  1：暂停
}
