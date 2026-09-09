package com.reason.modules.device.form;

import com.reason.modules.sys.form.CommonForm;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设备台账表单（分页查询条件 & 登记入参共用）
 *
 * <p>登记（save）：deviceNo 必填，其余可空；查询（list）：deviceNo/deviceName
 * 模糊、deviceType/deviceState 精确。登记时不受 deviceState 入参（建档状态恒为 0-未接入，
 * 状态只能由设备事件驱动——状态不由人写，由设备说）。</p>
 */
@Schema(description = "设备台账表单")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeviceRecordForm extends CommonForm {

    @Schema(description = "设备编号（登记必填，唯一）")
    private String deviceNo;

    @Schema(description = "设备名称（模糊查询/登记）")
    private String deviceName;

    @Schema(description = "设备类型：1-道闸/升降杆")
    private Integer deviceType;

    @Schema(description = "安装位置（查询精确/登记）")
    private String location;

    @Schema(description = "状态筛选：0-未接入 1-升起 2-降下 3-动作中 4-故障")
    private Integer deviceState;

    @Schema(description = "备注（登记用）")
    private String deviceRemark;

    @Schema(description = "设备 HMAC 密钥（登记可选；批次2 批量联调剧本使用——登记脚本产出的 32hex 强随机值，"
            + "仓库零明文；空=平台随机生成（日常登记不传），非空=校验 ^[0-9a-f]{32}$ 后采用）")
    private String deviceSecret;
}
