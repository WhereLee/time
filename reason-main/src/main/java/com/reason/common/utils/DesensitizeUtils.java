package com.reason.common.utils;

/**
 * 敏感信息脱敏
 */
public class DesensitizeUtils {

    /**
     * 姓名|昵称脱敏
     * 脱敏规则: 隐藏第一个字,比如李某某置换为*某某, 李某置换为*某
     * @param name
     * @return
     */
    public static String name(String name) {
        if (StringUtils.isBlank(name))
            return "";

        return "*".concat(name.substring(1));
    }

    /**
     * 昵称脱敏
     * 脱敏规则: 只显示第一个字,比如user13置换为u****
     * @param nickname
     * @return
     */
    public static String nickname(String nickname) {
        if (StringUtils.isBlank(nickname))
            return "";

        int length = nickname.length();

        if (length <= 1)
            return "*";

        return StringUtils.rightPad(StringUtils.left(nickname, 1), length, "*");
    }

    /**
     * 电话号码脱敏（固定电话、手机号码）
     * 脱敏规则: 保留前三后四, 比如15638296218置换为156****6218 0574****3242
     * @param phone
     * @return
     */
    public static String phone(String phone){
        if (StringUtils.isBlank(phone))
            return "";

        int length = phone.length();

        //手机
        if (length == 11)
            return phone.replaceAll("(\\w{3})\\w*(\\w{4})", "$1****$2");

        //固定电话 或 其他
        int prelength = length/3;
        return StringUtils.rightPad(StringUtils.left(phone, prelength), length-prelength, "*").concat(phone.substring(length-prelength));
    }

    /**
     * 身份证脱敏
     * 脱敏规则: 保留前1位、后1位, 中间全部隐藏 比如：3****************7
     * 如果小于等于2位（正常不会） 全部隐藏
     * @param idcard
     * @return
     */
    public static String idcard(String idcard){
        if (StringUtils.isBlank(idcard))
            return "";

        int length = idcard.length();

        if (length <= 2)
            return StringUtils.rightPad(StringUtils.left(idcard, 0), length, "*");

        return StringUtils.rightPad(StringUtils.left(idcard, 1), length-1, "*").concat(idcard.substring(length-1));
    }

    /**
     * 地址脱敏
     * 脱敏规则: 缺省脱敏：显示前1/3、后 1/3，其他显示，比如：河南省****XX号
     * @param address
     * @return
     */
    public static String address(String address){
        if (StringUtils.isBlank(address))
            return "";

        int length = address.length();

        int prelength = length/3;
        return StringUtils.rightPad(StringUtils.left(address, prelength), length-prelength, "*").concat(address.substring(length-prelength));
    }

    /**
     * 电子邮件
     * 脱敏规则：@前显示3位(如果少于等于3位，则全部显示)、加3个*，@及@后完整显示 比如：user**@example.com
     * @param email
     * @return
     */
    public static String email(String email) {
        if (StringUtils.isBlank(email))
            return "";

        int length = email.length();

        int index = email.indexOf("@");
        //正常不会
        if (index == -1) {
            int prelength = length/3;
            return StringUtils.rightPad(StringUtils.left(email, prelength), length-prelength, "*").concat(email.substring(length-prelength));
        }

        String prefix = "";
        if (index <= 2)
            prefix = StringUtils.left(email, index);
        else
            prefix = StringUtils.left(email, 3);

        return prefix.concat("***").concat(email.substring(index));
    }

    /**
     * 银行卡号 （16位、17位、19位）
     * 脱敏规则：只显示前六位、后四位，中间部分隐藏 比如：622260**********1234
     * 如果小于等于10位（正常不会） 显示前1/3、后1/3，中间部分隐藏
     * @param bankCard
     * @return
     */
    public static String bankCard(String bankCard) {
        if (StringUtils.isBlank(bankCard))
            return "";

        int length = bankCard.length();

        if (length <= 10) {
            int prelength = length/3;
            return StringUtils.rightPad(StringUtils.left(bankCard, prelength), length-prelength, "*").concat(bankCard.substring(length-prelength));
        }

        return StringUtils.rightPad(StringUtils.left(bankCard, 6), length-4, "*").concat(bankCard.substring(length-4));
    }
}
