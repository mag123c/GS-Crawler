package com.test.crawler.googlescholar;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import com.test.crawler.settings.DB;
import com.test.crawler.settings.Log;

import io.github.bonigarcia.wdm.WebDriverManager;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class GoogleScholarCrawler {

    private static String current_url = "";
    private static String path = "LOCAL_LOG_PATH";
    private static String label = "REFDB";
    private static Connection mysql;
    private static List<String> gateway = new ArrayList<>();
    private static Map<String, String> gateway_map = new HashMap<>();
    private static int gateway_idx = 0; 
    
    public static void main(String[] args) throws Exception {    	
    	
        String base_url = "https://scholar.google.com";
        String param = "/scholar?hl=ko&sl=en&sourceid=chrome&ie=UTF-8&";
        String search_text = "";

        int filter_id = 0;
        int max_page_num = 5;

        String programName = "updateGoogleScholar";
        if (filter_id == 0) {
            programName = "allUpdateGoogleScholar";
        }

        int load_db = DB.loadDBConfiguration();
        
        if(load_db == -1) {        
        	Log.writeLogMsg("[loadDBConfiguration] key name should not be used for database connection name. Please check global.conf file.", path);
        	return;        	
        }
        
        connectDB();
        mysql = DB.EDBCon.get(label);
        if(mysql==null) {
        	Log.writeLogMsg("* Label is not found Error! break", path);
        	return;
        }
                
        if (mysql.isClosed()) {
        	Log.writeLogMsg("* Class Driver connection Error! break", path);
            throw new java.net.ConnectException("DB Connection Error");
        }
        
        PreparedStatement pst = mysql.prepareStatement("INSERT INTO GoogleScholarCrawlerHistoryInfo" +
                "(PROGRAM_ID, NAME, FILTER_ID, GOOGLE_SCHOLAR_PUBLICATION_CNT, MAX_PAGE_NUM, STATUS, " +
                "ERROR_MESSAGE, START_DATE, END_DATE, ELAPSED_TIME, COMMENT, CREATE_DATE, LASTUPDATE_DATE, " +
                "CREATE_USER_ID) VALUES(0, ?, ?, null, ?, 'G', null, NOW(6), null, 0, null, NOW(6), null, 1)", PreparedStatement.RETURN_GENERATED_KEYS);

        pst.setString(1, programName);
        pst.setInt(2, filter_id);
        pst.setInt(3, max_page_num);
        pst.executeUpdate();

        ResultSet rs = pst.getGeneratedKeys();

        int lastHistoryId = 0;
        Integer count = 0;        
       
        gateway.add("192.168.8.1");
        gateway.add("192.168.7.1");

        for(String gate : gateway) gateway_map.put(gate, null);        
        
        int first_switch = switchingNIC(gateway, gateway_map, gateway_idx++);
        if(first_switch == -2){
        	Exception exception = new Exception("[NIC NULL] CHECK NIC");                        	
        	updateHistoryWhenError(mysql, exception, lastHistoryId, count);
        	closeDB();
        	return;
        }
        
        int range1 = 5;
        int range2 = 15;
        boolean[] bl = new boolean[range2-range1];
        
        while (rs.next()) {
            lastHistoryId = rs.getInt(1);
        }

        if (!rs.isClosed()) rs.close();        
        if (!pst.isClosed()) pst.close();

        Log.writeLogMsg(programName + "\n\n", path);
        Log.writeLogMsg(programName + " 프로그램 크롤링 시작", path);
        long start = System.currentTimeMillis();
        try {
            List<String> keywords = findFilterKeywords(filter_id, mysql);

            int start_index = 0;
            for (String keyword : keywords) {
            	if(mysql.isClosed()) {
            		connectDB();
        		}

            	int idx = 1;
                String text = keyword.replaceAll(" +", " ").replaceAll("^\\s+", "").replaceAll("\\s+$", "").replaceAll(" ", "+");
                search_text = "q=" + text + "&op=" + text;
                param = "/scholar?hl=ko&sl=en&sourceid=chrome&ie=UTF-8&";

                start_index++;
                Log.writeLogMsg(start_index + "번째 키워드 크롤링 중", path);
                while (idx <= max_page_num) {
                    if (mysql.isClosed()) {
                    	connectDB();
                    }
                    int http_status = 0;

                    Document doc = null;                    

                    while(http_status == 0){
                    	try{
                    		doc = Jsoup.connect(base_url + param + search_text)
    	                            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.88 Safari/537.36")
    	                            .referrer("https://www.google.com/search?q=google+scholar&oq=google+sch&sourceid=chrome&ie=UTF-8")
    	                            .timeout(5000)
    	                            .get();
                    		
                    		http_status = 1;
                    	}catch(Exception e){
                    		System.out.println("ERROR");
                    		e.printStackTrace();
                    		e.getMessage();
                    		
                    		Log.writeLogMsg("[429 ERROR] TOO MANY REQUEST, 1HOUR STOP", path);
                    		Thread.sleep(36000000);
                    	}      
                        
                    }
                    
                    String cur_url = base_url + param + search_text;
                    current_url = cur_url;
                    
            		int reCAPTCHA = 3;
                    while (!doc.select("h1:contains(사용자가 로봇이 아니라는 확인이 필요합니다.)").isEmpty()) {           			
                    	Log.writeLogMsg("[JSOUP reCAPTCHA]reCAPTCHA 발생, 30분 대기", path);
                    	Thread.sleep(1800000);           	            	
                    	
                    	reCAPTCHA--;
                    	
                    	if(reCAPTCHA == -1) {
                    		Log.writeLogMsg("[JSOUP Parsing Fail]", path);
                    		continue;
                    	}            	
                    	
                            
                        if(reCAPTCHA == 0) {                        	
                            int switching = switchingNIC(gateway, gateway_map, gateway_idx++);
                            
                            if(switching == -1) {                        	
                            	Exception exception = new Exception("NIC already used up");                        	
                            	updateHistoryWhenError(mysql, exception, lastHistoryId, count);
                            	Log.writeLogMsg("[Nic already used up]Crawler is closed", path);
                            	return;
                            }
                        }
                        reCAPTCHA = 3;
                    }
                    
                    Log.writeLogMsg("로봇 인증 걸리지 않음", path);

                    if (mysql.isClosed()) {
                    	connectDB();
                    }
                    Elements els = doc.select("div.gs_ri");
                    ArrayList<String[]> data_arrays = new ArrayList<String[]>();

                    for (Element el : els) {
                    	if(el.select("h3 > a").size() == 0) continue;
                    	
                    	String CNUM = null;
                    	for(Element cnum : el.select("div.gs_fl > a")) {
                    		if(cnum.attr("href").indexOf("?cluster=") > -1) {
                    			CNUM = cnum.attr("href").substring(cnum.attr("href").indexOf("?cluster=") + "?cluster=".length(),
                    												cnum.attr("href").indexOf("&", cnum.attr("href").indexOf("?cluster=")));
                    			break;
                    		}
                    		
                    		else if(cnum.attr("href").indexOf("?cites=") > -1) {
                    			CNUM = cnum.attr("href").substring(cnum.attr("href").indexOf("?cites=") + "?cites=".length(),
                    												cnum.attr("href").indexOf("&", cnum.attr("href").indexOf("?cites=")));
                    			break;
                    		}
                    	}
                    	
                    	String AUTHOR = el.select("div.gs_a").text();
                		if(AUTHOR.contains(" - ") && !AUTHOR.contains("…")) AUTHOR = AUTHOR.split(" - ")[0];
            			else if(!AUTHOR.contains(" - ") && AUTHOR.contains("…")) AUTHOR = AUTHOR.split("…")[0];
        				else if(AUTHOR.contains(" - ") && AUTHOR.contains("…") && (AUTHOR.indexOf(" - ") > AUTHOR.indexOf("…"))) AUTHOR = AUTHOR.split("…")[0];
            			else if(AUTHOR.contains(" - ") && AUTHOR.contains("…") && (AUTHOR.indexOf(" - ") < AUTHOR.indexOf("…"))) AUTHOR = AUTHOR.split("-")[0];                    	
            			else AUTHOR += "***검증 필요***";
                		
                    	AUTHOR.trim();

                    	//URL, TITLE, AUTHOR, CID, CNUM, PUBLICATION_YEAR
                        data_arrays.add(new String[]{el.select("h3 > a").attr("href"), null, AUTHOR, el.select("h3 > a").attr("id"), CNUM, null});
                    }

                    if (data_arrays.size() > 0) {
                    	int select_and_insert_result = select_and_insert(data_arrays, mysql, count); 
                        if(select_and_insert_result == -1){
                        	Exception exception = new Exception("NIC already used up");                        	
                        	updateHistoryWhenError(mysql, exception, lastHistoryId, count);
                        	Log.writeLogMsg("[Nic already used up]Crawler is closed", path);
                        	return;
                        }
                        if(select_and_insert_result == 0){
                        	Exception exception = new Exception("[Selenium Error]Cookie Folder does not delete. Please Check");                        	
                        	updateHistoryWhenError(mysql, exception, lastHistoryId, count);
                        	Log.writeLogMsg("[Nic already used up]Crawler is closed", path);
                        	return;
                        }
                    }

                    if (!doc.select("b:contains(다음)").isEmpty()) {
                        param = doc.select("b:contains(다음)").get(0).parent().attr("href");
                        search_text = "";
                    }
                    
                    Log.writeLogMsg(start_index + "번째 키워드의 " + idx + "번째 페이지 크롤링 완료", path);
                    idx++;                    
                    
                    int sleeptime = getRandomTime(range1, range2, bl);
                    closeDB();
                    
                    ProcessBuilder processkill = new ProcessBuilder("cmd.exe", "/C", "taskkill", "/f", "/im", "chrome.exe");
                    Process kill = processkill.start();
                    kill.waitFor();

                    Log.writeLogMsg("[Waiting] 다음 Crawling을 위해 대기중... 대기시작시간 : " + LocalDateTime.now() + " // 총 대기시간(ms) : " + sleeptime, path);
                    Thread.sleep(sleeptime); 
                }
                Log.writeLogMsg(start_index + "번째 키워드 크롤링 완료  ", path);
            }
            
        } catch (Exception exception) {
            exception.printStackTrace();
            Log.writeLogMsg(exception.getMessage(), path);
            updateHistoryWhenError(mysql, exception, lastHistoryId, count);
            if (!pst.isClosed()) pst.close();
            if (!mysql.isClosed()) closeDB();
            return;
        }

        if (mysql.isClosed()) {
        	connectDB();
        }

        double end = ((System.currentTimeMillis() - start) / 1000.0);
        pst = mysql.prepareStatement("UPDATE GoogleScholarCrawlerHistoryInfo SET " +
                "GOOGLE_SCHOLAR_PUBLICATION_CNT = ?, STATUS = 'Y', END_DATE = NOW(6), ELAPSED_TIME = ?, LASTUPDATE_DATE = NOW(6) WHERE HISTORY_ID = ?");
        pst.setInt(1, count);
        pst.setDouble(2, end);
        pst.setInt(3, lastHistoryId);
        pst.executeUpdate();

        if (pst != null) pst.close();
        if (!mysql.isClosed()) closeDB();
    }
    
    private static int switchingNIC(List<String> gateway, Map<String, String> gateway_map, int gateway_idx) throws InterruptedException {
    	String msg = gateway_idx == 0 ? "[NIC 초기화]" : "[NIC Switchinig] 교체중.......";
    	
    	Log.writeLogMsg(msg, path);

    	if(gateway.size() == 0) {
    		return -2;
    	}
    	
    	if(gateway_idx%gateway.size() == 0 && gateway_idx >= gateway.size()) {
    		Log.writeLogMsg("[NIC ALL BLOCK]ALL NIC IS BLOCKED, WAIT 6HOURS", path);
    		Thread.sleep(21600000);
    	}
    	
    	gateway_idx %= gateway.size();
    	
    	if(gateway_idx >= gateway.size()){
	    	gateway_idx %= gateway.size();
    		if(Long.parseLong(gateway_map.get(gateway.get(gateway_idx))) - Long.parseLong(LocalDateTime.now().format(DateTimeFormatter.ofPattern("YYMMddHHmmss"))) < -1000000){
    			System.out.println("NIC 교체");
    		}
    		else return -1;
    	}
    	
    	
        try {        	
            ProcessBuilder routeDelBuilder = new ProcessBuilder("cmd.exe", "/C", "route", "delete", "0.0.0.0");
            Process routeDelProcess = routeDelBuilder.start();
            routeDelProcess.waitFor();
            
            ProcessBuilder routeAddBuilder = new ProcessBuilder("cmd.exe", "/C", "route", "add", "0.0.0.0", "mask", "0.0.0.0", gateway.get(gateway_idx));
            Process routeAddProcess = routeAddBuilder.start();
            routeAddProcess.waitFor();
            
            gateway_map.put(gateway.get(gateway_idx), LocalDateTime.now().format(DateTimeFormatter.ofPattern("YYMMddHHmmss")));
            
            Log.writeLogMsg("[NIC Switching] Switching Success, Waiting for 5sec", path);
            Thread.sleep(5000);      
        } catch (InterruptedException e) {
            e.printStackTrace();            
        } catch (IOException e) {
			e.printStackTrace();
		}
        return 0;
	}

	private static int connectDB() {
		int connect = 0;
		try {
			connect = DB.connectDB(label);
			switch(connect) {
			case -1 : Log.writeLogMsg("[connectDB error] label is null", path);
				break;
			case -2 : Log.writeLogMsg("[connectDB error] label is not Found", path);
				break;
			case -3 : Log.writeLogMsg("[connectDB error] dbType is not MySQL", path);
				break;
			}
		} catch (SQLException e) {
			Log.writeLogMsg("[connectDB method fail]", path);
			e.printStackTrace();
		}
		
		return connect;		
	}

	private static int closeDB() {
		int close = 0;
		try {
			close = DB.closeDB(label);
			switch(close) {
			case -1 : Log.writeLogMsg("[closeDB error] label is null", path);
				break;
			case -2 : Log.writeLogMsg("[closeDB error] DB Name is incorrect", path);
				break;
			}
		} catch (SQLException e) {
			Log.writeLogMsg("[closeDB method fail]", path);
			e.printStackTrace();
		}
		
		return close;
	}

    private static int getRandomTime(int range1, int range2, boolean[] bl) {
    	int chk = 0;
    	for(boolean b : bl) {
    		if(b) chk++;
    	}
    	if(chk==bl.length) bl = new boolean[chk];
    	
    	int ran_num = 0;
    		
		while(true) {
			ran_num = (int)(((Math.random()*(range2-range1))+range1)*60000);
			
			int idx = (ran_num/60000)-range1;
			
			if(!bl[idx]) {
				bl[idx] = true;
				return ran_num;
			}
		}    	
	}

	private static List<String> findFilterKeywords(int filterId, Connection mysql) {
        List<String> keywords = new ArrayList<>();
        PreparedStatement pst = null;
        ResultSet rs = null;

        try {
            if (filterId == 0) {
                pst = mysql.prepareStatement("SELECT FILTER_ID FROM FilterInfo");
                rs = pst.executeQuery();

                List<Integer> filterIds = new ArrayList<>();
                while (rs.next()) {
                    int id = rs.getInt("FILTER_ID");
                    filterIds.add(id);
                }
                if (!rs.isClosed()) rs.close();
                if (!pst.isClosed()) pst.close();

                for (int id : filterIds) {
                    pst = mysql.prepareStatement("SELECT NAME FROM FilterKeywordInfo WHERE FILTER_ID = ?");
                    pst.setInt(1, id);
                    rs = pst.executeQuery();

                    while (rs.next()) {
                        String keyword = rs.getString("NAME");
                        keywords.add(keyword);
                    }

                    if (!rs.isClosed()) rs.close();
                    if (!pst.isClosed()) pst.close();
                }
            } else {            	
                pst = mysql.prepareStatement("SELECT NAME FROM FilterKeywordInfo WHERE FILTER_ID = ?");                
                pst.setInt(1, filterId);
                rs = pst.executeQuery();

                while (rs.next()) {
                    String keyword = rs.getString("NAME");
                    keywords.add(keyword);
                }

                if (!rs.isClosed()) rs.close();
                if (!pst.isClosed()) pst.close();
            }
            return keywords;
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new RuntimeException("키워드 찾는 중 오류가 발생했습니다.");
        }
    }
    
    private static int select_and_insert(ArrayList<String[]> dataset, Connection mysql, Integer count) throws Exception {
    	for(int i=0; i<dataset.size(); i++) {
    		int selenium_result = selenium(dataset.get(i), i, 0);
    		
    		if(selenium_result == -1){
            	return -1;
    		}
    		else if(selenium_result == 0) return 0;
    			
    		if(dataset.get(i)[1] == null) continue;

            if (mysql.isClosed()) {            	
            	connectDB();
            	mysql = DB.EDBCon.get(label);
            }
            
            PreparedStatement pst = mysql.prepareStatement("SELECT IFNULL(COUNT(PAPER_ID),0) as count FROM GoogleScholarPaperTestInfo4 WHERE CID=?");
            ResultSet rs = null;
            String[] element = dataset.get(i);
            try {
                pst.setString(1, element[3]);
                rs = pst.executeQuery();
                if (rs.next()) {
                    if (rs.getInt("count") > 0) {              
                    	Log.writeLogMsg("[Distinct_list_add]" + element[0] + "\t" + element[1] + "\t" + element[2] + "\t" + element[3] + "\t" + element[4] + "\t" + element[5], path);                      
                    	chk_and_update(element, mysql, i);                    	
                    } else {
                    	Log.writeLogMsg("[insert_list_add]" + element[0] + "\t" + element[1] + "\t" + element[2] + "\t" + element[3] + "\t" + element[4] + "\t" + element[5], path);
                        insert(element, mysql, i);                        
                        count++;
                    }
                }
            } catch (Exception e) {
            	Log.writeLogMsg(e.getMessage(), path);
            	Log.writeLogMsg("[Select Fail]" + element[0] + "\t" + element[1] + "\t" + element[2] + "\t" + element[3] + "\t" + element[4] + "\t" + element[5], path);            	
            } finally {            	
                if (rs != null && !rs.isClosed()) {
                    rs.close();                                        
                }
                if (!pst.isClosed()) {
                    pst.close();
                }

                int thread_sleep = 60000 + (int)(Math.random()*(120000-60000+1));
                Log.writeLogMsg("[selenium wait]" + thread_sleep + "ms만큼 대기", path);
        		if(i < dataset.size()-1) Thread.sleep(thread_sleep);        		
            }
        }
    	return 1;
    }
    
	private static void insert(String[] dataset, Connection mysql, int idx) throws Exception {

        if (mysql.isClosed()) {
        	connectDB();
        }
        
        PreparedStatement pst = null;
        try {
            dataset[1] = (dataset[1] == null) ? "" : dataset[1];
            dataset[1] = dataset[1].replace("\'", "\\'");
            
            pst = mysql.prepareStatement("INSERT INTO GoogleScholarPaperTestInfo4 (URL,TITLE,AUTHOR,CID,CNUM,PUBLICATION_YEAR,CREATE_DATE,CREATE_USER_ID) VALUES(?,?,?,?,?,?,NOW(6),7)");
        	pst.setString(1, dataset[0]);
            pst.setString(2, dataset[1]);
            pst.setString(3, dataset[2]);
            pst.setString(4, dataset[3]);
            pst.setString(5, dataset[4]);
            pst.setString(6, dataset[5]);
            pst.executeUpdate();            
            if (!pst.isClosed()) pst.close();
            Log.writeLogMsg("[Insert Success]" + dataset[0] + "\t" + dataset[1] + "\t" + dataset[2] + "\t" + dataset[3] + "\t" + dataset[4] + "\t" + dataset[5], path);
            
        } catch (Exception e) {
        	Log.writeLogMsg("[Insert Fail]" + dataset[0] + "\t" + dataset[1] + "\t" + dataset[2] + "\t" + dataset[3] + "\t" + dataset[4] + "\t" + dataset[5], path);
            throw new Exception();            
        } finally {
            if (!pst.isClosed()) pst.close();
        }
    }

	private static void updateHistoryWhenError(Connection mysql, Exception exception, int lastHistoryId, Integer count) throws SQLException {
		if(mysql.isClosed()){
			connectDB();
			mysql = DB.EDBCon.get(label);
		}
		
        PreparedStatement pst = mysql.prepareStatement("UPDATE GoogleScholarCrawlerHistoryInfo SET " +
                "GOOGLE_SCHOLAR_PUBLICATION_CNT = ?, STATUS = 'E', ERROR_MESSAGE = ?, END_DATE = NOW(6), LASTUPDATE_DATE = NOW(6) WHERE HISTORY_ID = ?");
        pst.setInt(1, count);
        pst.setString(2, exception.getMessage());
        pst.setInt(3, lastHistoryId);
        pst.execute();
        if (!pst.isClosed()) pst.close();
    }
	
	
	/*
	 * ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
	 * ★ 각 경로에 맞는 chromePath와, 원하는 cookieDir 경로 지정 ★
	 * ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
	 */
	private static ChromeDriver driverGet() throws IOException, InterruptedException{
        String chromePath = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";
        String userDataDir = "C:\\chromeCookie";
        
        Path userDataDirPath = Paths.get(userDataDir);
        if (Files.exists(userDataDirPath)) {
            try {
                Files.walk(userDataDirPath)
                     .map(Path::toFile)
                     .sorted((o1, o2) -> -o1.compareTo(o2))
                     .forEach(File::delete);
            } catch (IOException e) {
                e.printStackTrace();
                Log.writeLogMsg("[Selenium Error]Cookie Folder does not delete. Please Check", path);
            }
        }
        
        Thread.sleep(2000);
        
        ProcessBuilder processBuilder = new ProcessBuilder(chromePath, "--remote-debugging-port=9222", "--user-data-dir=" + userDataDir);
        processBuilder.start();
        
		WebDriverManager.chromedriver().setup();
		
        ChromeDriverService chromeService = new ChromeDriverService.Builder().usingAnyFreePort().build();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.setExperimentalOption("debuggerAddress", "127.0.0.1:9222");
        
    	ChromeDriver driver = new ChromeDriver(chromeService, options);
    	driver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
		return driver;  
	}

    private static int selenium(String[] dataset, int idx, int tmp) throws Exception {
    	if(tmp == 3) return -1;
    	Log.writeLogMsg("[selenium start]", path);
    	
    	ChromeDriver driver = driverGet();
    	if(driver == null) return 0;

    	Thread.sleep(2000);
    	
    	String pre = "";    	
    	
    	try {
    		driver.get(current_url);
    		
    		List<WebElement> elem = driver.findElements(By.className("gs_ri"));    		
    		while(tmp < elem.size()) {
    			if(elem.get(idx).findElement(By.cssSelector("h3.gs_rt > a")).getSize().equals(new Dimension(0,0))){
    				tmp++;
    			}
    			else break;
    		}
    		
    		Thread.sleep(2000);
    		
    		WebElement quoto_btn = elem.get(idx).findElement(By.cssSelector("a.gs_or_cit"));			    		
    		quoto_btn.click();
    		
    		Thread.sleep(2000);
    		    		
    		WebElement BibTeX = driver.findElement(By.cssSelector("a.gs_citi"));
    		BibTeX.click();
    		
    		Thread.sleep(2000);
    		
    		pre = driver.findElement(By.cssSelector("pre")).getText();
    		
    		if(pre.contains("title={")) dataset[1] = pre.substring(pre.indexOf("title={")+"title={".length(), pre.indexOf("},", pre.indexOf("title={")));
    		if(pre.contains("year={")) dataset[5] = pre.substring(pre.indexOf("year={")+"year={".length(), pre.indexOf("}", pre.indexOf("year={")));
    		
    		driver.close();
    		driver.quit();
    	} catch(NoSuchElementException e){
            Log.writeLogMsg("[seleium NoSuchElementException] reCAPTCHA" + dataset[0] + "\t" + dataset[1] + "\t" + dataset[2] + "\t" + dataset[3] + "\t" + dataset[4] + "\t" + dataset[5], path);
            if(tmp < 3) {
            	driver.close();
            	driver.quit();
            	selenium(dataset, idx, tmp++);
            }
            else return -2;
            
    	}catch(Exception e){
            Log.writeLogMsg("[seleium NoSuchElementException] reCAPTCHA" + dataset[0] + "\t" + dataset[1] + "\t" + dataset[2] + "\t" + dataset[3] + "\t" + dataset[4] + "\t" + dataset[5], path);
            if(tmp < 3) {
            	driver.close();
            	driver.quit();
            	selenium(dataset, idx, tmp++);
            }
            return -3;    		
    	}
    	return 1;
    }

    private static void chk_and_update(String[] element, Connection mysql, int idx) throws Exception {		
		if (mysql.isClosed()) {
			connectDB();
        }

        PreparedStatement pst = mysql.prepareStatement("SELECT URL, TITLE, AUTHOR, CNUM, PUBLICATION_YEAR FROM GoogleScholarPaperTestInfo4 WHERE CID=?");
        ResultSet rs = null;
        pst.setString(1, element[3]);        
        try {
        	rs = pst.executeQuery();        		
        		if(rs.next()) {
            		if(rs.getString("PUBLICATION_YEAR") == null || !rs.getString("URL").equals(element[0]) || !rs.getString("TITLE").equals(element[1]) || !rs.getString("AUTHOR").equals(element[2])
            				|| !rs.getString("CNUM").equals(element[4]) || !rs.getString("PUBLICATION_YEAR").equals(element[5])) {
            			pst = mysql.prepareStatement("UPDATE GoogleScholarPaperTestInfo4 SET URL=?, TITLE=?, AUTHOR=?, CNUM=?, PUBLICATION_YEAR=?, LASTUPDATE_DATE = NOW(6) WHERE CID=?");
            			pst.setString(1, element[0]);
            			pst.setString(2, element[1]);
            			pst.setString(3, element[2]);
            			pst.setString(4, element[4]);
            			pst.setString(5, element[5]);            			
            			pst.setString(6, element[3]);            			
            			pst.executeUpdate();
            			Log.writeLogMsg("[Distinct_list_Update]" + element[0] + "\t" + element[1] + "\t" + element[2] + "\t" + element[3] + "\t" + element[4] + "\t" + element[5], path); 
            		}
            		
        		else Log.writeLogMsg("[Distinct_list_NoUpdate]", path);
        	}
        } catch(Exception e) {
        	e.printStackTrace();
        	Log.writeLogMsg(e.getMessage(), path);
        	Log.writeLogMsg("[Update Fail]" + element[0] + "\t" + element[1] + "\t" + element[2] + "\t" + element[3] + "\t" + element[4] + "\t" + element[5], path);        	
        } finally {
            if (!rs.isClosed()) {
                rs.close();                
            }
            if (!pst.isClosed()) {
                pst.close();                
            }       
        }
	}   
	
}
