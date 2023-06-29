package com.test.crawler.settings;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Conf {

    /*
     * EConf : global.text K,V
     * CLIENT_ID : x
     * SERVER_ID : x 
     * 
     * */
    private static final Map<String, String> EConf = new ConcurrentHashMap<>();

    static {
        loadConfiguration(".//home/global.conf");
        loadConfiguration("./home/db.conf");
    }

    public static void loadConfiguration(String filepath) {
    	System.out.println(filepath);
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line;
            while((line = br.readLine()) != null) {
                if (line.indexOf("#") == 0) {               	
                    continue;
                }
                String[] inputs = line.split("\t");
                EConf.put(inputs[0], inputs[1]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, String> getEConf() {
        return new HashMap<>(EConf);
    }

}