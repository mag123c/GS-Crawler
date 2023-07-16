package com.google.scholar.app; 

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFrame;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import com.google.scholar.frame.CrawlerFrame;
import com.test.crawler.settings.*;

import io.github.bonigarcia.wdm.WebDriverManager;

public class GoogleScholarApplication {
	
	private static String currentURL = null;
	private static String nextURL = null;
    private static String label = "REFDB";
    private static String path = "LOCAL_LOG_PATH";
    private static Connection mysql;
    private static String chromePath = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";
    private static int page = 0;
    private static int count = 0;
    private static JFrame crawlerFrame;   

	public static void setCrawlerFrame(JFrame crawlerFrame) {
		GoogleScholarApplication.crawlerFrame = crawlerFrame;
	}

	public static void main(String[] args) throws Exception {
    	chromeDriverKill();
    	
    	ChromeDriver driver = null;
    	
    	try {
    		driver = driverGet();
    	} catch(IOException e) {
    		e.printStackTrace();
        	Log.writeLogMsg("[ChromeDriver Error] Please Check ChromeDriver", path);
    	}
    	   	
    	boolean searchChk = false;
    	while(!driver.getCurrentUrl().contains("scholar.google.com/scholar?")) {    		
    		if(!searchChk) {
    			System.out.println("Google Scholar에 검색을 시도해야 동작함");
    			CrawlerFrame.setLog("Google Scholar에 검색을 시도해야 동작함");
    			searchChk = true;
    		}    		
    	}    	
		
        int filter_id = 0;
        int load_db = DB.loadDBConfiguration();
        String programName = "SelfUpdateGoogleScholar";
        
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
        	return;
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
        
        while (rs.next()) {
            lastHistoryId = rs.getInt(1);
        }

        if (!rs.isClosed()) rs.close();        
        if (!pst.isClosed()) pst.close();

        Log.writeLogMsg(programName + "\n\n", path);
        Log.writeLogMsg(programName + " 프로그램 크롤링 시작", path);
        long start = System.currentTimeMillis();   
		
		boolean run = true;
		boolean chk = true;
    	while(run) {
    		try {
    			nextURL = driver.getCurrentUrl();    		
    		
	    		if(page > 0 && (!currentURL.equals(nextURL)) && nextURL.contains("scholar.google.com/scholar?")) {
	    			selenium(driver);
	    			page++;
	    		}
	    		
	    		else if(page == 0) {
	    			selenium(driver);
	    			page++;
	    		}	    		
	    			    		
	    		if(!run) break;
	    		
    		} catch(WebDriverException e) {
            	Log.writeLogMsg("[Chrome has been closed] 크롬이 종료되었습니다. 프로그램을 종료합니다.", path);
            	updateHistoryWhenFinished(mysql, e, lastHistoryId, driver, start);   
            	return;
    		}
    		    		    		
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

	private static int selenium(ChromeDriver driver) throws Exception {
		CrawlerFrame.setLog("데이터를 수집중입니다.");
		List<WebElement> elem = driver.findElements(By.className("gs_ri"));
		if(elem.size() == 0) {			
			CrawlerFrame.setLog("파싱할 데이터가 없습니다. Chrome을 확인 해주세요.");
			Thread.sleep(10000);
			return selenium(driver);
		}
		
		/*
		 * parsingData : URL, TITLE, AUTHOR, CID, CNUM, PUBLICATION_YEAR
		 */
		currentURL = driver.getCurrentUrl();
				
		for(WebElement el : elem) {
			
			try {
				String[] parsingData = new String[6];
				
				WebElement h3 = el.findElement(By.tagName("h3"));
				WebElement anchor;
				
				anchor = h3.findElement(By.tagName("a"));
				
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
				
				insertCheck(parsingData, count);
			} catch (NoSuchElementException nseException) {
				nseException.printStackTrace();
				Log.writeLogMsg("[DIV is not have any anchor] continue parsing", path);
				continue;
			} catch (StaleElementReferenceException serException) {
				serException.printStackTrace();
				Log.writeLogMsg("[abnormal behavior] do not move page when parsing", path);
			}
			
		}		 
		
		CrawlerFrame.setLog("완료... 다른 페이지 혹은 검색어를 입력해주세요. insert/update 건수 : " + count);
		
		return count;				
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
		
		return null;
	}

	private static String findPubYear(WebElement el, String gs_aText) {	
        Pattern pattern = Pattern.compile("\\d{4}");
        Matcher matcher = pattern.matcher(gs_aText);

        while (matcher.find()) {
            int startIndex = matcher.start();
            int endIndex = matcher.end();
            return gs_aText.substring(startIndex, endIndex);	            
        }	      
        
        return null;
        
	}
	
	private static void insertCheck(String[] parsingData, int count) throws Exception {
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
             count++;
             
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
            		if(rs.getString("PUBLICATION_YEAR") == null || !rs.getString("URL").equals(parsingData[0]) || !rs.getString("CNUM").equals(parsingData[1]) || !rs.getString("AUTHOR").equals(parsingData[2])
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
            			count++;
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
	
	private static void updateHistoryWhenError(Connection mysql, Exception exception, int lastHistoryId, Integer count, ChromeDriver driver) throws Exception {
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
        
        chromeDriverKill();
    }
    
	private static void updateHistoryWhenFinished(Connection mysql2, WebDriverException exception, int lastHistoryId,
			ChromeDriver driver, long start) throws Exception {
		if(mysql.isClosed()){
			connectDB();
			mysql = DB.EDBCon.get(label);
		}
		
        double end = (System.currentTimeMillis() - start) / 1000.0;
        
        PreparedStatement pst = mysql.prepareStatement("UPDATE GoogleScholarCrawlerHistoryInfo SET " +
                "GOOGLE_SCHOLAR_PUBLICATION_CNT = ?, STATUS = 'Y', END_DATE = NOW(6), ELAPSED_TIME = ?, LASTUPDATE_DATE = NOW(6) WHERE HISTORY_ID = ?");
        pst.setInt(1, count);
        pst.setDouble(2, end);
        pst.setInt(3, lastHistoryId);
        pst.executeUpdate();

        if (pst != null) pst.close();
        if (!mysql.isClosed()) closeDB();
        
        driver.quit();
        
        chromeDriverKill();
	}
	
	public static void chromeDriverKill() throws Exception {
        ProcessBuilder processkill = new ProcessBuilder("cmd.exe", "/C", "taskkill", "/f", "/im", "chrome.exe");
        Process kill = processkill.start();
        kill.waitFor();
        
        processkill = new ProcessBuilder("cmd.exe", "/C", "taskkill", "/f", "/im", "chromedriver.exe");
        kill = processkill.start();
        kill.waitFor();
	}
}
