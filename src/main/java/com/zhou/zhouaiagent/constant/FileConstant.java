package com.zhou.zhouaiagent.constant;

import java.io.File;

/**
 * 文件常量
 */
public interface FileConstant {

    /**
     * 文件保存目录
     * user.dir = JVM 启动时的「当前工作目录」（可变、随启动位置变）, 项目根目录, 即tmp和src/target同级(生产不建议, 需要使用绝对路径)
     */
    String FILE_SAVE_DIR = System.getProperty("user.dir")+ File.separator + "/tmp";
}