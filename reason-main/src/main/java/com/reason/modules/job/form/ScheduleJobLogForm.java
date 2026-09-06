package com.reason.modules.job.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "定时任务日志查询条件")
@Data
public class ScheduleJobLogForm extends CommonForm {
    @Schema(description = "任务bean")
    private String jobBean;     //任务bean

    @Schema(description = "任务名称")
    private String jobName;     //任务名称

    @Schema(description = "执行状态 0：成功    1：失败")
    private Integer logState;   //执行状态 0：成功    1：失败

    @Schema(description = "起始时间戳，单位秒")
    private Long starttime;     //操作起始时间戳 单位秒

    @Schema(description = "截止时间戳，单位秒")
    private Long endtime;       //操作截止时间戳 单位秒
}
