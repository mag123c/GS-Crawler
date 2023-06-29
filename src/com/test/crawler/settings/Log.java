package com.test.crawler.settings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class Log {
	
	FileWriter fw;
	
//	운영서버로 넘어갈 때, Path값 EConf에서 받아올 수 있게. param : REFDB
	private static final Map<String, String> EConf = Conf.getEConf();
    
    public static int writeLogMsg(String msg, String path){
    	if(path == null) {
    		path = EConf.get("LOCAL_WEBLOG_PATH");            
    	}
    	path = EConf.get(path);
//    	path = "./home/refdb/log/";
    	
        LocalDateTime temp = LocalDateTime.now();
        String filename = String.format("Google_Scholar_log.%s.log", temp.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        
        boolean validation = filename.matches("\\s+$");
        if(!validation){
            msg += "\n";
        }
    	
        
        String scriptName = "";
        
        //Handle ClassName Get
        try {
            throw new Exception();
        } catch (Exception e) {
            StackTraceElement[] stackTrace = e.getStackTrace();
            if (stackTrace.length > 1) {
                scriptName = stackTrace[1].getClassName();
            }
        }
                
        String newMsg = "[" + temp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "][" + scriptName + "]" + msg;

        try {
        	File file = new File("." + path + filename);
            FileWriter fw = new FileWriter(file, true);

			fw.write(newMsg);			
	        fw.flush();	        
            fw.close();
            
    		System.out.println("로그 작성 완료 : " + newMsg);
    		return 1;
    		
		} catch (IOException e) {			
			System.out.println(e.getMessage());
			System.out.println(newMsg);
			return -1;
		}
        
        
    }
}
