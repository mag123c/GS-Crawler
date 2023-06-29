package com.test.crawler.settings;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DB {

    public static final Map<String, Connection> EDBCon = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> EDBConf = new ConcurrentHashMap<>();
    private static final List<String> EDBConStopword = new CopyOnWriteArrayList<>();
    private static final Map<String, String> EConf = Conf.getEConf();
    private static int rc = 0;

    static {
        EDBConStopword.add("ALL");
    }

    public static int loadDBConfiguration() {
        for (String key : EConf.keySet()) {
            if (key.contains("EDC")) {
                String[] validation = Arrays.stream(key.split("_"))
                        .filter(item -> !"EDC".equals(item))
                        .toArray(String[]::new);
                
                //EDBConf에 DBname : DBinfo(dbtype, host, port, db, user, pw중 있는 값 K : V) put
                if (validation.length == 2) {  	
                    if (EDBConStopword.contains(validation[0])) {
                        System.out.println("[loadDBConfiguration] " + key + " name should not be used for database connection name. Please check global.conf file.");
                        return -1;
                    }
                    Map<String, String> map = EDBConf.getOrDefault(validation[0], new ConcurrentHashMap<>());
                    map.put(validation[1], EConf.get(key));
                    EDBConf.put(validation[0], map);
                }
            }
        }
        
        int flag = 1;
        for (String key : EConf.keySet()) {
            if( !key.contains("DBTYPE") && !key.contains("HOST") && !key.contains("PORT") && !key.contains("DB") && !key.contains("USER") && !key.contains("PASSWORD")) {
                flag = -2;
            }
        }
        return flag;
    }

    public static int connectDB(String label) throws SQLException {
    	if(label == null) {
    		return -1;
    	}
    	
        if(!EDBCon.containsKey(label)){
            if(!EDBConf.containsKey(label)){
                System.out.println("[connectDB] label is not Found");
                return -2;
            }

            String dbType = EDBConf.get(label).get("DBTYPE");
            if ("MySQL".equals(dbType)) {
                return connectDBMySQL(label);                
            }else{
                System.out.println("[connectDB] dbType is not MySQL");
                return -3;
            }
        }
        
        else {
        	if(EDBCon.get(label) == null) {
                rc = connectDB(label);
            }
        	else {
                String pingSQL = "SELECT 1 from dual";
                boolean result = false;
                try {
                    PreparedStatement ping = EDBCon.get(label).prepareStatement(pingSQL);
                    result = ping.execute();
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                if (!result) {
                	System.out.println("[connectDB] test for dummy is fail. reconnect....");
                    rc = closeDB(label);
                    rc = connectDB(label);
                    return 2;
                } else {
                	System.out.println("[connectDB] db Connected!!");;
                	return 3;
                }
            }
        }
		return 0;
    }

    public static int closeDB(String label) throws SQLException {
        if(label == null){
            System.out.println("[close Fail] DB Name is not found");
            return -1;
        }
        if(label.equals("ALL")){
            for(String key : EDBCon.keySet()){
                closeDB(key);
            }
            System.out.println("[ALL DB closed]");
            return 1;
        }else {
            if(EDBCon.containsKey(label)){
                try {
                    EDBCon.get(label).close();
                    EDBCon.remove(label);                    
                } catch (SQLException e) {
                    e.printStackTrace();
                }                
                System.out.println("[" + label + " DB closed]");
                return 1;
            }else{
                System.out.println("[close Fail] DB Name is incorrect");
                return -2;
            }
        }        
    }

    private static int connectDBMySQL(String label) {
    	//개발서버 ip -> conf의 value로 (운영 서버 이전 시)
        String jdbc_url = "jdbc:mysql://" + "192.168.8.217" + ":"
                            + EDBConf.get(label).get("PORT") + "/"
                            + EDBConf.get(label).get("DB") + "?useSSL=false"
                            + "&serverTimezone=Asia/Seoul"
                            + "&useUnicode=true"
                            + "&characterEncoding=UTF-8";


        try {           
            Connection con = DriverManager.getConnection(jdbc_url, EDBConf.get(label).get("USER"), EDBConf.get(label).get("PASSWORD"));
            EDBCon.put(label, con);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return 1;
    }

}
