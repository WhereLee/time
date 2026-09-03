package com.reason.common.utils;

public class RegularUtil {
  private static final String CHINESE_NUMBER = "[一二三四五六七八九〇○o零十]";
  
  private static final String CHINESE_NUMBER_UPPER = "[壹贰叁肆伍陆柒捌玖零〇○o十拾]";
  
  private static final String YEAR = "(((19|20)\\d{2})年||((19|20)\\d{2})|((一九|二〇|二○|二o|二零)[一二三四五六七八九〇○o零十][一二三四五六七八九〇○o零十])年|((一九|二〇|二○|二o|二零)[壹贰叁肆伍陆柒捌玖零〇○o十拾][壹贰叁肆伍陆柒捌玖零〇○o十拾])年)";
  
  private static final String MONTH = "(.{1,2}月|-\\d{1,2}|/\\d{1,2}|／\\d{1,2}|[一二三四五六七八九〇○o零十][一二三四五六七八九〇○o零十]月|(一十)[一二三四五六七八九〇○o零十]月|[壹贰叁肆伍陆柒捌玖零〇○o十拾][壹贰叁肆伍陆柒捌玖零〇○o十拾]月|(壹拾)[壹贰叁肆伍陆柒捌玖零〇○o十拾]月)";
  
  private static final String DAY = "(.{1,2}日|-\\d{1,2}|/\\d{1,2}|／\\d{1,2}|[一二三四五六七八九〇○o零十][一二三四五六七八九〇○o零十]日|[壹贰叁肆伍陆柒捌玖零〇○o十拾][壹贰叁肆伍陆柒捌玖零〇○o十拾]日|((一十)|(二十)|(三十))[一二三四五六七八九〇○o零十]日|((壹拾)|(贰拾)|(叁拾))[壹贰叁肆伍陆柒捌玖零〇○o十拾]日)";
  
  public static final String ORG_CODE = "[0-9A-Za-z]{9}";
  
  public static final String REG_NO = "[0-9]{10,15}|[0-9]{6}NA[0-9]{6}X|[0-9]{6}NB[0-9]{6}X";
  
  public static final String MOBILE = "(?:0|86|\\+86)?1[3456789]\\d{9}";
  
  public static final String MOBILE_CMCC = "(?:0|86|\\+86)?1((3[456789])|(47)|(5[012789])|(8[2378]))\\d{8}";
  
  public static final String MOBILE_CUCC = "(?:0|86|\\+86)?1((3[012])|(45)|(5[56])|(8[56]))\\d{8}";
  
  public static final String EMAIL = "([a-zA-Z0-9_\\-\\.]+)@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.)|(([a-zA-Z0-9\\-]+\\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\\]?)";
  
  @Deprecated
  public static final String REG_CREDIT_CODE = "9[1|2|3]([A-Za-z0-9]{16})";
  
  public static final String CREDIT_CODE = "9[1|2|3]([A-Za-z0-9]{16})";
  
  public static final String GT_CREDIT_CODE = "92([A-Za-z0-9]{16})";
  
  public static final String ENT_ID = "[q|g]([A-Za-z0-9_]{31,32})";
  
  public static final String JG_ID = "jg([_]{0,1})([A-Za-z0-9]{32})";
  
  public static final String HOSPITAL_ID = "[h]([A-Za-z0-9_]{31,32})";
  
  public static final String YEAR_MONTH_DAY = "(((19|20)\\d{2})年||((19|20)\\d{2})|((一九|二〇|二○|二o|二零)[一二三四五六七八九〇○o零十][一二三四五六七八九〇○o零十])年|((一九|二〇|二○|二o|二零)[壹贰叁肆伍陆柒捌玖零〇○o十拾][壹贰叁肆伍陆柒捌玖零〇○o十拾])年)(.{1,2}月|-\\d{1,2}|/\\d{1,2}|／\\d{1,2}|[一二三四五六七八九〇○o零十][一二三四五六七八九〇○o零十]月|(一十)[一二三四五六七八九〇○o零十]月|[壹贰叁肆伍陆柒捌玖零〇○o十拾][壹贰叁肆伍陆柒捌玖零〇○o十拾]月|(壹拾)[壹贰叁肆伍陆柒捌玖零〇○o十拾]月)(.{1,2}日|-\\d{1,2}|/\\d{1,2}|／\\d{1,2}|[一二三四五六七八九〇○o零十][一二三四五六七八九〇○o零十]日|[壹贰叁肆伍陆柒捌玖零〇○o十拾][壹贰叁肆伍陆柒捌玖零〇○o十拾]日|((一十)|(二十)|(三十))[一二三四五六七八九〇○o零十]日|((壹拾)|(贰拾)|(叁拾))[壹贰叁肆伍陆柒捌玖零〇○o十拾]日)";
  
  public static final String COURT_NOTICE_NO = "[〔（(]\\d\\s*\\d\\s*\\d\\s*\\d*\\s*[)）〕][\\s\\S]*?[号]";
  
  public static final String ID_CARD_FIRST = "[1-9]\\d{5}\\d{2}((0\\d)|(1[0-2]))(([0|1|2]\\d)|3[0-1])\\d{3}";
  
  public static final String ID_CARD_SECOND = "[1-9]\\d{5}[12]\\d{3}((0\\d)|(1[0-2]))(([0|1|2]\\d)|3[0-1])\\d{3}(\\d|X|x)";
  
  public static final String CHINESE_CHARACTERS = "[一-鿿]+";
  
  public static final String URL = "(https://|http://)?([\\w-]+\\.)+[\\w-]+(/[\\w- ./?%&=]*)?";
  
  public static final String PASSWORD = "\\w{6,18}";
  
  public static final String NUMBER = "\\d+";
  
  public static final String TIME_STAMP = "1\\d{12}";
  
  public static final String ENGLISH = "[a-zA-Z]+";
  
  public static final String NUMBER_ENGLISH = "[0-9A-Za-z]+";
  
  public static final String SCIENTIFIC_NOTATION = "\\d{1}(\\.\\d{1,})?E\\d+";
  
  public static final String NUMBER_ENGLISH_CHINESE = "[0-9a-zA-Z一-鿿]+";
  
  public static final String NUMBER_ENGLISH_CHINESE_UNDERLINE = "[0-9a-zA-Z_|-一-鿿]+";
  
  public static final String IS_INTEGER = "[+-]?\\d+";
  
  public static final String IS_DOUBLE = "([+-]?\\d+(\\.\\d+)?)|(([-+]?\\d+\\.?\\d*)[Ee]{1}([-+]?\\d+))";
  
  public static final String SPOTS = "((&#8226;)|(&#9642;)|(•)|·|⋅|∙|・|•|●)";
  
  public static final String SBC_CHAR = "^[０-９ａ-ｚＡ-Ｚ]*$";
  
  public static final String MD5 = "^([a-fA-F0-9]{32})$";
  
  public static final String PASSWAORD_LENGHTH_MIN = "^.{8,}$";
  
  public static final String PASSWAORD_LENGHTH_MAX = "^.{1,20}$";
  
  public static final String PASSWAORD_RULLE = "^(?![a-zA-Z]+$)(?![A-Z0-9]+$)(?![A-Z\\W_]+$)(?![a-z0-9]+$)(?![a-z\\W_]+$)(?![0-9\\W_]+$)[a-zA-Z0-9\\W_]{3,}$";
  
  public static boolean validate(String arg, String regex) {
    return (StringUtils.isNotEmpty(arg) && arg.matches(regex));
  }
  
  public static void main(String[] args) {
    long start = 1897804097993L;
    System.err.println(String.valueOf(start));
    System.err.println(validate(String.valueOf(start), "1\\d{12}"));
  }
}
