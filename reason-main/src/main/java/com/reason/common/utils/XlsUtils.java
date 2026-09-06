package com.reason.common.utils;

import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class XlsUtils {

    /**
     * 导出Excel 2003
     * @param path
     * @param name
     * @param headList
     * @param bodyList
     * @return
     */
    public static void PoiExport2003(String path, String name, List<XlsHead> headList,List<List<String>> bodyList) {
        //列数
        Integer colNum = headList.size();

        HSSFWorkbook wb = new HSSFWorkbook();//工作簿
        HSSFSheet sheet = wb.createSheet();//工作表
        HSSFRow row = null;//行
        HSSFCellStyle style = wb.createCellStyle();//单元格样式
        HSSFFont font = wb.createFont();//字体样式
        //设置单元格样式 中间对齐、垂直中间对齐、四周边框、12号宋体、自动换行
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setWrapText(true);

        //设置工作表名称
        wb.setSheetName(0, "name");

        row = sheet.createRow(0);
        //单元格
        HSSFCell[] cell = new HSSFCell[colNum+1];

        //1.生成标题栏
        //1.1 序号
        //设置列宽
        sheet.setColumnWidth(0, 2000);
        //创建单元格
        cell[0] = row.createCell(0);
        cell[0].setCellStyle(style);
        cell[0].setCellValue("序号");
        //1.2 其他标题
        for (int i=0;i<colNum;i++) {
            //标题数据(标题名称和列宽)
            XlsHead head = headList.get(i);
            String title = head.getTitle();
            Integer width = head.getWidth();

            //设置列宽
            sheet.setColumnWidth(i+1, width);
            //创建单元格
            cell[i+1] = row.createCell(i+1);
            cell[i+1].setCellStyle(style);
            if(title != null){
                cell[i+1].setCellValue(title);
            }
        }

        //2.生成内容 bodyList
        for (int i=0;i<bodyList.size();i++) {
            List<String> rowData = bodyList.get(i);
            //创建行
            row = sheet.createRow(i + 1);
            //1.1 序号
            //创建单元格
            cell[0] = row.createCell(0);
            cell[0].setCellStyle(style);
            cell[0].setCellValue(i+1);
            //2.2
            for (int j=0;j<colNum;j++) {
                //创建单元格
                String data = rowData.get(j);
                cell[j+1] = row.createCell(j+1);
                cell[j+1].setCellStyle(style);
                if(data != null){
                    cell[j+1].setCellValue(data);
                }
            }
        }

        //3.写文件
        BufferedOutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(new File(path, name)));
            wb.write(out);
        } catch (IOException e) {
            throw new RuntimeException("Excel写流失败:"+e.getMessage());
        } finally {
            try {
                if(null != wb)
                    wb.close();
                if(null != out)
                    out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
