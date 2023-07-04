package com.test.crawler.googlescholar;

import java.io.IOException;
import java.net.ConnectException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import com.test.crawler.settings.DB;
import com.test.crawler.settings.Log;

import io.github.bonigarcia.wdm.WebDriverManager;

public class HalfAutoGSCrawler {
	
	private static String currentURL = null;
	private static String nextURL = null;
    private static String label = "REFDB";
    private static String path = "LOCAL_LOG_PATH";
    private static Connection mysql;
    private static String chromePath = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"; 


    public static void main(String[] args) throws Exception {
    	ChromeDriver driver = null;
    	
    	try {
    		driver = driverGet();
    	} catch(IOException e) {
    		e.printStackTrace();
        	Log.writeLogMsg("[ChromeDriver Error] Please Check ChromeDriver", path);
    	}
    	   	
    	while(!driver.getCurrentUrl().contains("scholar.google.com/scholar?")) {
    		System.out.println("Google Scholar에 검색을 시도해야 동작함");
    		Thread.sleep(1000);
    	}    	
		
        int filter_id = 0;
        int load_db = DB.loadDBConfiguration();
        String programName = "SelfUpdateGoogleScholar";
        
        if(load_db == -1) {
        	Exception exception = new Exception("loadDBConfiguration error");
        	updateHistoryWhenError(mysql, exception, -1, 0, driver);
        	Log.writeLogMsg("[loadDBConfiguration] key name should not be used for database connection name. Please check global.conf file.", path);
        	return;        	
        }
        
        connectDB();
        mysql = DB.EDBCon.get(label);
        if(mysql==null) {
        	Exception exception = new Exception("Label is not found");
        	updateHistoryWhenError(mysql, exception, -1, 0, driver);
        	Log.writeLogMsg("* Label is not found Error! break", path);
        	return;
        }
        
        if (mysql.isClosed()) {
        	Log.writeLogMsg("* Class Driver connection Error! break", path);
            ConnectException exception = new java.net.ConnectException("DB Connection Error");
        	updateHistoryWhenError(mysql, exception, -1, 0, driver);            
        }
        
        PreparedStatement pst = mysql.prepareStatement("INSERT INTO GoogleScholarCrawlerHistoryInfo" +
                "(PROGRAM_ID, NAME, FILTER_ID, GOOGLE_SCHOLAR_PUBLICATION_CNT, STATUS, " +
                "ERROR_MESSAGE, START_DATE, END_DATE, ELAPSED_TIME, COMMENT, CREATE_DATE, LASTUPDATE_DATE, " +
                "CREATE_USER_ID) VALUES(0, ?, ?, null, 'G', null, NOW(6), null, 0, null, NOW(6), null, 1)", PreparedStatement.RETURN_GENERATED_KEYS);

        pst.setString(1, programName);
        pst.setInt(2, filter_id);
        pst.executeUpdate();

        ResultSet rs = pst.getGeneratedKeys();

        int lastHistoryId = 0;
        Integer count = 0;
        
        while (rs.next()) {
            lastHistoryId = rs.getInt(1);
        }

        if (!rs.isClosed()) rs.close();        
        if (!pst.isClosed()) pst.close();

        Log.writeLogMsg(programName + "\n\n", path);
        Log.writeLogMsg(programName + " 프로그램 크롤링 시작", path);
        long start = System.currentTimeMillis();   

		int page = 0;
		int cnt = 0;
		
		boolean run = true;
    	while(run) {
    		try {
    			nextURL = driver.getCurrentUrl();
    		} catch(WebDriverException e) {
            	Log.writeLogMsg("[Chrome has been closed]", path);
            	updateHistoryWhenError(mysql, e, lastHistoryId, cnt, driver);   
    		}
    		
    		
    		if(page > 0 && (!currentURL.equals(nextURL)) && nextURL.contains("scholar.google.com/scholar?")) {
    			selenium(driver, cnt);
    			page++;
    		}
    		
    		else if(page == 0) {
    			int currentCount = selenium(driver, cnt);
    			
    			if(currentCount != cnt) {
    				System.out.println("데이터 INSERT 완료, 파싱할 다른 페이지가 필요합니다.");
    				page++;
    			}
    			
    			else System.out.println("파싱 실패. 현재 페이지 파싱 불가능"); 
    		}
    		
    		if(!run) break;
    		    		    		
    	}
    	
        double end = (System.currentTimeMillis() - start) / 1000.0;
        
        pst = mysql.prepareStatement("UPDATE GoogleScholarCrawlerHistoryInfo SET " +
                "GOOGLE_SCHOLAR_PUBLICATION_CNT = ?, STATUS = 'Y', END_DATE = NOW(6), ELAPSED_TIME = ?, LASTUPDATE_DATE = NOW(6) WHERE HISTORY_ID = ?");
        pst.setInt(1, count);
        pst.setDouble(2, end);
        pst.setInt(3, lastHistoryId);
        pst.executeUpdate();

        if (pst != null) pst.close();
        if (!mysql.isClosed()) closeDB();
	
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

	private static ChromeDriver driverGet() throws IOException, InterruptedException {		
        ProcessBuilder processBuilder = new ProcessBuilder(chromePath, "https://scholar.google.com/", "--remote-debugging-port=9222");
        processBuilder.start();                
        
		WebDriverManager.chromedriver().setup();
		
        ChromeDriverService chromeService = new ChromeDriverService.Builder().usingAnyFreePort().build();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.setExperimentalOption("debuggerAddress", "127.0.0.1:9222");
        
    	ChromeDriver driver = new ChromeDriver(chromeService, options);
		return driver;  
	}

	private static int selenium(ChromeDriver driver, int count) throws Exception {		
		List<WebElement> elem = driver.findElements(By.className("gs_ri"));
		if(elem.size() == 0) {			
			System.out.println("reCAPTCHA 해제중");
			Thread.sleep(10000);
			return selenium(driver, count);
		}
		/*
		 * parsingData : URL, TITLE, AUTHOR, CID, CNUM, PUBLICATION_YEAR
		 */
		currentURL = driver.getCurrentUrl();		
				
		for(WebElement el : elem) {
			String[] parsingData = new String[6];
			
			WebElement h3 = el.findElement(By.tagName("h3"));
			WebElement anchor;
			
			try {
				anchor = h3.findElement(By.tagName("a"));
			}catch (NoSuchElementException e) {
				count--;
				continue;
			}
			
			//URL
			parsingData[0] = anchor.getAttribute("href");
			
			//TITLE
			parsingData[1] = anchor.getText();
			
			//AUTHOR
			String gs_aTtext = el.findElement(By.className("gs_a")).getText();
			parsingData[2] = gs_aTtext.split("-")[0];

			int length = parsingData[2].length();			
			for(int i = 0; i < length; i++) {				
				if(Character.isLetter(parsingData[2].charAt(i)) && !Character.isDigit(parsingData[2].charAt(i))) {
					parsingData[2] = parsingData[2].substring(i, length);
					break;
				}	
			}
			
			
			//CID
			parsingData[3] = anchor.getAttribute("id");
			
			//CNUM
			WebElement gsFl = el.findElement(By.className("gs_fl"));
			List<WebElement> gsFl_a = gsFl.findElements(By.tagName("a"));
			
			parsingData[4] = findCNUM(gsFl_a);
			
			//PUBLICATION_YEAR
			parsingData[5] = findPubYear(el, gs_aTtext);
			
			insertCheck(parsingData);
		}
		
		return count + elem.size();				
	}

	private static String findCNUM(List<WebElement> gsFl_a) {
		for(WebElement cnum : gsFl_a) {
    		if(cnum.getAttribute("href").indexOf("?cluster=") > -1) {
    			return cnum.getAttribute("href").substring(cnum.getAttribute("href").indexOf("?cluster=") + "?cluster=".length(),
    												cnum.getAttribute("href").indexOf("&", cnum.getAttribute("href").indexOf("?cluster=")));
    		}
    		
    		else if(cnum.getAttribute("href").indexOf("?cites=") > -1) {
    			return cnum.getAttribute("href").substring(cnum.getAttribute("href").indexOf("?cites=") + "?cites=".length(),
    												cnum.getAttribute("href").indexOf("&", cnum.getAttribute("href").indexOf("?cites=")));    			
    		}
		}
		
		return "";
	}

	private static String findPubYear(WebElement el, String gs_aText) {	
        Pattern pattern = Pattern.compile("\\d{4}");
        Matcher matcher = pattern.matcher(gs_aText);

        while (matcher.find()) {
            int startIndex = matcher.start();
            int endIndex = matcher.end();
            return gs_aText.substring(startIndex, endIndex);	            
        }	      
        
        return "";
        
	}
	
	private static void insertCheck(String[] parsingData) throws Exception {
        if (mysql.isClosed()) {            	
        	connectDB();
        	mysql = DB.EDBCon.get(label);
        }
        
        PreparedStatement pst = mysql.prepareStatement("SELECT IFNULL(COUNT(PAPER_ID),0) as count FROM GoogleScholarPaperTestInfo5 WHERE CID=?");
        ResultSet rs = null;
        
        try {
            pst.setString(1, parsingData[3]);
            rs = pst.executeQuery();
            if (rs.next()) {
                if (rs.getInt("count") > 0) {              
                	Log.writeLogMsg("[Distinct_list_add]" + parsingData[0] + "\t" + parsingData[1] + "\t" + parsingData[2] + "\t" + parsingData[3] + "\t" + parsingData[4] + "\t" + parsingData[5], path);
                	update(parsingData);
                } else {
                	Log.writeLogMsg("[Insert_list_add]" + parsingData[0] + "\t" + parsingData[1] + "\t" + parsingData[2] + "\t" + parsingData[3] + "\t" + parsingData[4] + "\t" + parsingData[5], path);
                	insert(parsingData);
                }
            }
        } catch (Exception e) {
        	Log.writeLogMsg(e.getMessage(), path);
        	Log.writeLogMsg("[Select Fail]" + parsingData[0] + "\t" + parsingData[1] + "\t" + parsingData[2] + "\t" + parsingData[3] + "\t" + parsingData[4] + "\t" + parsingData[5], path);  		
        }
	}

	private static void insert(String[] parsingData) throws Exception {
		
		 if (mysql.isClosed()) {
	        	connectDB();
	        }
	        
	        PreparedStatement pst = null;
	        try {
	            parsingData[1] = (parsingData[1] == null) ? "" : parsingData[1];
	            parsingData[1] = parsingData[1].replace("\'", "\\'");
	            
	            pst = mysql.prepareStatement("INSERT INTO GoogleScholarPaperTestInfo5 (URL,TITLE,AUTHOR,CID,CNUM,PUBLICATION_YEAR,CREATE_DATE,CREATE_USER_ID) VALUES(?,?,?,?,?,?,NOW(6),7)");
	        	pst.setString(1, parsingData[0]);
	            pst.setString(2, parsingData[1]);
	            pst.setString(3, parsingData[2]);
	            pst.setString(4, parsingData[3]);
	            pst.setString(5, parsingData[4]);
	            pst.setString(6, parsingData[5]);
	            pst.executeUpdate();            
	            if (!pst.isClosed()) pst.close();
	            Log.writeLogMsg("[Insert Success]", path);
	            
	        } catch (Exception e) {
	        	Log.writeLogMsg("[Insert Fail]", path);
	            throw new Exception();            
	        } finally {
	            if (!pst.isClosed()) pst.close();
	        }
		
	}

	private static void update(String[] parsingData) throws SQLException {
		if (mysql.isClosed()) {
			connectDB();
        }

        PreparedStatement pst = mysql.prepareStatement("SELECT URL, TITLE, AUTHOR, CNUM, PUBLICATION_YEAR FROM GoogleScholarPaperTestInfo5 WHERE CID=?");
        ResultSet rs = null;
        pst.setString(1, parsingData[3]);        
        try {
        	rs = pst.executeQuery();        		
        		if(rs.next()) {
            		if(rs.getString("PUBLICATION_YEAR") == null || !rs.getString("URL").equals(parsingData[0]) || !rs.getString("TITLE").equals(parsingData[1]) || !rs.getString("AUTHOR").equals(parsingData[2])
            				|| !rs.getString("CNUM").equals(parsingData[4]) || !rs.getString("PUBLICATION_YEAR").equals(parsingData[5])) {
            			pst = mysql.prepareStatement("UPDATE GoogleScholarPaperTestInfo5 SET URL=?, TITLE=?, AUTHOR=?, CNUM=?, PUBLICATION_YEAR=?, LASTUPDATE_DATE = NOW(6) WHERE CID=?");
            			pst.setString(1, parsingData[0]);
            			pst.setString(2, parsingData[1]);
            			pst.setString(3, parsingData[2]);
            			pst.setString(4, parsingData[4]);
            			pst.setString(5, parsingData[5]);            			
            			pst.setString(6, parsingData[3]);            			
            			pst.executeUpdate();
            			Log.writeLogMsg("[Distinct_list_Update]", path); 
            		}
            		
        		else Log.writeLogMsg("[Distinct_list_NoUpdate]", path);
        	}
        } catch(Exception e) {
        	e.printStackTrace();
        	Log.writeLogMsg("[Update Error]", path);        	
        } finally {
            if (!rs.isClosed()) {
                rs.close();                
            }
            if (!pst.isClosed()) {
                pst.close();                
            }       
        }
	}
	
	private static void updateHistoryWhenError(Connection mysql, Exception exception, int lastHistoryId, Integer count, ChromeDriver driver) throws SQLException {
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
        
        driver.close();
        driver.quit();
    }
}
