package com.juanpi.bi.merge;

import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Properties;

import com.juanpi.bi.merge.utils.DateUtil;
import com.juanpi.bi.merge.utils.LoadConfigFile;

/**
 * 
 * @author yunduan
 * rewrite by gongzi
 * @date 2016年7月25日 下午1:20:38
 * 任务管理
 */
public class TaskManager {

//	private String baseDir = "hdfs://nameservice1/user/hadoop/dw_realtime/dw_real_for_path_list";
    private static String timeFlag = "01";

    static String AM0_FMT = "yyyy-MM-dd 00:00:00";

    private String dataBaseDir = "";
    private String sourceDir = "";
    private String targetDir = "";
    private String kafkaTopicIds = "";

	public TaskManager(String timeFlag, String dataBaseDir, String sourceDir, String targetDir, String kafkaTopicIds) {
	    this.timeFlag = timeFlag;
        this.dataBaseDir = dataBaseDir;
        this.sourceDir = sourceDir;
        this.targetDir = targetDir;
        this.kafkaTopicIds = kafkaTopicIds;
    }

    // 路径正则
	private String getDirRegex(String dateStr) {
		return this.dataBaseDir + this.sourceDir + "/{" + this.kafkaTopicIds + "}/date=" + dateStr + "/gu_hash=*/logs/";
	}

    /**
     * 返回interval个小时前的日期的毫秒值
     *
     * @return
     */
    private long getHoursAgoMillis(String timeFlag)
    {
        Calendar cal = Calendar.getInstance();

        String fmt = "yyyy-MM-dd HH:00:00";
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        // 取当前的日期，并格式化为：yyyy-MM-dd HH:00:00
        String dt = "";

        if(timeFlag.equals(hour+""))
        {
            // 如果是凌晨1点，取当天0点
            dt = DateUtil.getDateIntervalDate(cal, AM0_FMT);
            fmt = "yyyy-MM-dd HH:mm:ss";
        } else {
            // 转化为特定的日期
            dt = DateUtil.getHourIntervalDate(cal,0, fmt);
        }

        long milis = 0;

        try {
            // 将给定的日期转为毫秒
            milis = DateUtil.dateToMillis(dt, fmt);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return milis;
    }
	
	/**
	 * 开始任务
	 * @throws IOException
	 */
	public void start(String dateStr) throws IOException {
        System.out.println("开始任务======>>....");
        String srcDirRegex = getDirRegex(dateStr);
        long oneHourAgoMillis = getHoursAgoMillis(timeFlag);
		MergeTask mergeTask = new MergeTask(srcDirRegex, this.sourceDir, this.targetDir, true, oneHourAgoMillis);
		mergeTask.doMerge();
	}
	
	public static void main(String[] args) {

		// 传入 date 格式 yyyy-MM-dd

        String dateStr = "";

        // 现在的文件合并逻辑是凌晨1点的时候合并前一天的小文件。5点~22点处理当天的文件
        String intervalStr = "01";

        System.out.println("args 参数个数：" + args.length);

		if(args.length == 1)
        {
            dateStr = args[0];
        }

        if(args.length == 2)
        {
            dateStr = args[0];
            intervalStr = args[1];
        }

        String dataBaseDir= "";
        String sourceDir= "";
        String targetDir= "";
        String kafkaTopicIds= "";

        try {
            Properties pro = LoadConfigFile.loadFileByClassLoader("config.properties");
            dataBaseDir= (String) pro.get("dataBaseDir");
            sourceDir= (String) pro.get("sourceDir");
            targetDir= (String) pro.get("targetDir");
            kafkaTopicIds= (String) pro.get("kafkaTopicIds");

        } catch (IOException e) {
		    System.exit(1);
            e.printStackTrace();
        }

        System.out.println("======>>main_date:" + dateStr);
        System.out.println("======>>timeFlag :" + intervalStr);

        // , Integer.parseInt(intervalStr)
		TaskManager manager = new TaskManager(intervalStr, dataBaseDir, sourceDir, targetDir, kafkaTopicIds);

		try {
			manager.start(dateStr);
		} catch (Exception e) {
            e.printStackTrace();
			System.exit(-1);
		}

        System.out.println("Small Files Merge Complete...");
    }
}
